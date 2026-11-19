package com.sedsoftware.sproutpace

import android.Manifest
import android.net.Uri
import java.io.File
import com.sedsoftware.sproutpace.platform.importIntoPrivateStorage
import com.sedsoftware.sproutpace.platform.PickedContentKind
import android.test.InstrumentationTestCase
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.sedsoftware.sproutpace.platform.AndroidNotificationPermissionService
import com.sedsoftware.sproutpace.platform.PermissionState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@Suppress("DEPRECATION")
class PlatformRegressionTest : InstrumentationTestCase() {
    override fun setUp() {
        super.setUp()
        instrumentation.waitForIdleSync()
        // Real UI actions are recovery-gated; native adapter tests must enter after that boundary too.
        runBlocking {
            (instrumentation.targetContext.applicationContext as SproutPaceApplication)
                .graph.reconcileReminderEnvironment(com.sedsoftware.sproutpace.platform.ReconciliationReason.APP_START)
        }
    }

    fun testNativeDestinationWriteFailurePreservesPriorSelection() = runBlocking {
        val context = instrumentation.targetContext
        val root = File(context.filesDir, "pending_photos").apply { mkdirs() }
        val prior = File(root, "prior.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val source = File(context.cacheDir, "source-photo.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
        try {
            assertTrue(root.setWritable(false, false))
            assertFalse(root.canWrite())
            try {
                importIntoPrivateStorage(context, Uri.fromFile(source), PickedContentKind.PHOTO)
                fail("Read-only destination must reject B")
            } catch (_: java.io.IOException) {
                assertTrue(prior.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
                assertEquals(listOf("prior.jpg"), root.listFiles()!!.map { it.name })
            }
        } finally { root.setWritable(true, true); prior.delete(); source.delete() }
    }

    fun testCancellationDuringNativeCopyPreservesPriorAndRemovesPartialFile() = runBlocking {
        val context = instrumentation.targetContext
        val root = File(context.filesDir, "pending_photos").apply { mkdirs() }
        val prior = File(root, "prior.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val copying = async {
            importIntoPrivateStorage(context, Uri.parse("content://com.sedsoftware.sproutpace.test.failing-photo/slow"),
                PickedContentKind.PHOTO)
        }
        try {
            withTimeout(5000) {
                while (root.listFiles()!!.none { it != prior && it.length() > 0 }) kotlinx.coroutines.delay(5)
            }
            copying.cancel()
            copying.join()
            assertTrue(prior.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(listOf("prior.jpg"), root.listFiles()!!.map { it.name })
        } finally { copying.cancel(); prior.delete() }
    }

    fun testRealActivityRecreationWhilePermissionDialogIsOpen() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as SproutPaceApplication
        app.getSharedPreferences("notification_permission.v1", 0).edit().clear().commit()
        val recreated = kotlinx.coroutines.CompletableDeferred<AppActivity>()
        var original: AppActivity? = null
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {
                if (original != null && activity is AppActivity && activity !== original) recreated.complete(activity)
            }
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            original = instrumentation.startActivitySync(android.content.Intent(app, AppActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as AppActivity
            val service = app.graph.notificationPermissionService
            val request = async(kotlinx.coroutines.Dispatchers.Main) { service.request() }
            withTimeout(5000) {
                while (service.state.value != PermissionState.Requesting) kotlinx.coroutines.delay(20)
            }
            instrumentation.runOnMainSync { original!!.recreate() }
            val replacement = withTimeout(5000) { recreated.await() }
            assertTrue(replacement.application === app)
            withTimeout(5000) {
                while (true) {
                    val deny = instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")
                        ?.firstOrNull()
                    if (deny?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) break
                    kotlinx.coroutines.delay(50)
                }
            }
            assertTrue(withTimeout(5000) { request.await() } is PermissionState.Denied)
            assertTrue(service.check() is PermissionState.Denied)
            instrumentation.runOnMainSync { replacement.finish() }
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    fun testPartialNativeCopyFailureCleansOnlyNewFile() = runBlocking {
        val context = instrumentation.targetContext
        val root = File(context.filesDir, "pending_photos").apply { mkdirs() }
        val prior = File(root, "prior.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            importIntoPrivateStorage(context, Uri.parse("content://com.sedsoftware.sproutpace.test.failing-photo/image"),
                PickedContentKind.PHOTO)
            fail("Pipe must report its read failure")
        } catch (_: java.io.IOException) {
            assertTrue(prior.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(listOf("prior.jpg"), root.listFiles()!!.map { it.name })
        } finally { prior.delete() }
    }

    fun testSuccessfulReplacementRetainsPriorUntilAccepted() = runBlocking {
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "source-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val root = File(context.filesDir, "pending_photos")
        try {
            val first = importIntoPrivateStorage(context, Uri.fromFile(source), PickedContentKind.PHOTO)
            val a = File(root, first.pendingToken!!)
            val next = importIntoPrivateStorage(context, Uri.fromFile(source), PickedContentKind.PHOTO)
            val b = File(root, next.pendingToken!!)
            assertFalse(a == b)
            assertTrue(a.readBytes().contentEquals(source.readBytes()))
            assertTrue(b.readBytes().contentEquals(source.readBytes()))
            assertTrue(a.delete())
            assertTrue(b.exists())
            val pending = com.sedsoftware.sproutpace.data.AndroidPendingPhotoImportStore(context)
            val stored = File(context.filesDir, "native_regression_photos")
            val photoId = com.sedsoftware.sproutpace.domain.model.PhotoId("native-regression")
            com.sedsoftware.sproutpace.data.AndroidPrivateFileStore(stored).write(photoId, pending.read(next.pendingToken!!)!!)
            pending.delete(next.pendingToken!!)
            val reopened = com.sedsoftware.sproutpace.data.AndroidPrivateFileStore(stored)
            assertTrue(reopened.read(photoId)!!.contentEquals(source.readBytes()))
            reopened.delete(photoId)
            assertNull(reopened.read(photoId))
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { source.delete() }
    }

    fun testFailedPhotoReplacementPreservesPriorPendingFile() = runBlocking {
        val context = instrumentation.targetContext
        val root = File(context.filesDir, "pending_photos").apply { mkdirs() }
        val prior = File(root, "prior.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val missing = Uri.fromFile(File(context.cacheDir, "missing-photo.jpg"))
        try {
            importIntoPrivateStorage(context, missing, PickedContentKind.PHOTO)
            fail("Unreadable source must fail")
        } catch (_: java.io.FileNotFoundException) {
            assertTrue(prior.exists())
            assertEquals(listOf("prior.jpg"), root.listFiles()!!.map { it.name })
        } finally { prior.delete() }
    }

    fun testPermissionSurvivesHostReplacementAndCancellation() = runBlocking(kotlinx.coroutines.Dispatchers.Main) {
        val context = instrumentation.targetContext
        context.getSharedPreferences("notification_permission.v1", 0).edit().clear().commit()
        val service = AndroidNotificationPermissionService(context)
        val first = RationaleActivity()
        val second = RationaleActivity()
        val launcher = RecordingLauncher()
        service.attach(first, launcher)
        val request = async(start = CoroutineStart.UNDISPATCHED) { service.request() }
        assertEquals(1, launcher.count)
        service.detach(first)
        service.attach(second, launcher)
        service.detach(first) // A stale host must not detach the new owner.
        service.onPermissionResult(false)
        assertEquals(PermissionState.Denied(true), withTimeout(1000) { request.await() })
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { service.request() }
        cancelled.cancel()
        cancelled.join()
        assertEquals(PermissionState.Requesting, service.request())
        service.onPermissionResult(false)
        val retry = async(start = CoroutineStart.UNDISPATCHED) { service.request() }
        service.onPermissionResult(false)
        assertEquals(PermissionState.Denied(true), withTimeout(1000) { retry.await() })
        assertEquals(3, launcher.count)
        service.detach(second)
    }

    private class RationaleActivity : ComponentActivity() {
        override fun shouldShowRequestPermissionRationale(permission: String) = true
    }

    private class RecordingLauncher : ActivityResultLauncher<String>() {
        var count = 0
        override fun launch(input: String, options: ActivityOptionsCompat?) {
            check(input == Manifest.permission.POST_NOTIFICATIONS)
            count++
        }
        override fun unregister() = Unit
        override val contract: ActivityResultContract<String, *> = ActivityResultContracts.RequestPermission()
    }
}
