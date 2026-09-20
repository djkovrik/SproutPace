package com.sedsoftware.sproutpace.platform

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.sedsoftware.sproutpace.data.requireSafePendingPhotoToken

class AndroidSystemPickerHost(
    private val activity: ComponentActivity,
) : SystemFilePicker, DocumentDestinationPicker {
    private var pendingPhoto: Continuation<Uri?>? = null
    private var pendingDocument: Continuation<Uri?>? = null
    private var pendingDestination: Continuation<Uri?>? = null

    private val photoLauncher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        pendingPhoto?.resume(it)
        pendingPhoto = null
    }
    private val documentLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingDocument?.resume(it)
        pendingDocument = null
    }
    private val destinationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) {
        pendingDestination?.resume(it)
        pendingDestination = null
    }

    override suspend fun pickAndImport(kind: PickedContentKind): Result<ImportedFile?> = runCatching {
        val uri = when (kind) {
            PickedContentKind.PHOTO -> awaitPhoto()
            PickedContentKind.BACKUP_ARCHIVE -> awaitDocument()
        } ?: return@runCatching null
        importIntoPrivateStorage(activity, uri, kind)
    }

    override suspend fun discardPendingPhoto(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            pendingPhotoFile(activity, token).let { target ->
                if (target.exists()) check(target.delete()) { "pending_photo_delete_failed" }
            }
        }
    }

    override suspend fun createDocument(
        suggestedName: String,
        mediaType: String,
    ): Result<ExternalDocumentHandle?> = runCatching {
        require(suggestedName.isNotBlank())
        require(mediaType.isNotBlank())
        val uri = suspendCancellableCoroutine { continuation ->
            check(pendingDestination == null) { "A document destination request is already active" }
            pendingDestination = continuation
            continuation.invokeOnCancellation { pendingDestination = null }
            destinationLauncher.launch(suggestedName)
        }
        uri?.let { ExternalDocumentHandle(it.toString()) }
    }

    private suspend fun awaitPhoto(): Uri? = suspendCancellableCoroutine { continuation ->
        check(pendingPhoto == null) { "A photo picker request is already active" }
        pendingPhoto = continuation
        continuation.invokeOnCancellation { pendingPhoto = null }
        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private suspend fun awaitDocument(): Uri? = suspendCancellableCoroutine { continuation ->
        check(pendingDocument == null) { "A document picker request is already active" }
        pendingDocument = continuation
        continuation.invokeOnCancellation { pendingDocument = null }
        documentLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }
}

private suspend fun importIntoPrivateStorage(
    context: Context,
    source: Uri,
    kind: PickedContentKind,
): ImportedFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val sourceName = resolver.displayName(source) ?: "import"
    val safeExtension = sourceName.substringAfterLast('.', "")
        .filter { it.isLetterOrDigit() }
        .take(12)
    val directoryName = if (kind == PickedContentKind.PHOTO) "pending_photos" else "imports"
    val privateDirectory = File(context.filesDir, directoryName).canonicalFile.apply {
        check(mkdirs() || isDirectory) { "private_import_directory_unavailable" }
    }
    if (kind == PickedContentKind.PHOTO) {
        privateDirectory.listFiles().orEmpty()
            .filter { candidate -> candidate.isFile && candidate.parentFile == privateDirectory }
            .forEach { candidate -> check(candidate.delete()) { "pending_photo_cleanup_failed" } }
    }
    val target = File(privateDirectory, buildString {
        append(UUID.randomUUID())
        if (safeExtension.isNotEmpty()) append('.').append(safeExtension)
    })
    runCatching {
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "selected_content_unreadable" }
            target.outputStream().use(input::copyTo)
        }
    }.getOrElse { failure ->
        target.delete()
        throw failure
    }
    ImportedFile(
        appOwnedPath = target.absolutePath.takeIf { kind == PickedContentKind.BACKUP_ARCHIVE },
        pendingToken = target.name.takeIf { kind == PickedContentKind.PHOTO },
        displayName = sourceName,
        mediaType = resolver.getType(source),
        sizeBytes = target.length(),
    )
}

private fun pendingPhotoFile(context: Context, token: String): File {
    val root = File(context.filesDir, "pending_photos").canonicalFile
    return File(root, requireSafePendingPhotoToken(token)).canonicalFile.also { target ->
        require(target.parentFile == root) { "pending_photo_must_be_app_owned" }
    }
}

private fun ContentResolver.displayName(uri: Uri): String? {
    val cursor: Cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
    return cursor.use {
        if (!it.moveToFirst()) null else it.getString(0)
    }
}

class AndroidAppOwnedFileStore(context: Context) : AppOwnedFileStore {
    private val root = File(context.filesDir, "imports").canonicalFile

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(path).canonicalFile
            require(target.parentFile == root) { "app_owned_path_required" }
            if (target.exists()) check(target.delete()) { "app_owned_delete_failed" }
        }
    }

    override suspend fun deleteAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            root.listFiles().orEmpty().forEach { file ->
                if (file.isFile) check(file.delete()) { "app_owned_delete_failed" }
            }
        }
    }
}
