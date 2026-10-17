package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.ScreenCommand
import com.sedsoftware.sproutpace.component.ScreenDataManager
import com.sedsoftware.sproutpace.component.ScreenId
import com.sedsoftware.sproutpace.domain.contract.domainResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Recovery outlives a loading screen; a failed attempt can be retried without exposing its data. */
internal class WorkspaceRecoveryGate(private val scope: CoroutineScope, private val recover: suspend () -> Unit) {
    private val mutex = Mutex()
    private var attempt: Deferred<Result<Unit>>? = null
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()

    suspend fun awaitReady() {
        val current = mutex.withLock {
            attempt?.takeUnless { it.isCancelled || (it.isCompleted && !mutableReady.value) } ?: scope.async {
                domainResult {
                    recover()
                    mutableReady.value = true
                }
            }.also { attempt = it }
        }
        current.await().getOrThrow()
    }
}

internal class RecoveryGatedScreenDataManager(
    private val delegate: ScreenDataManager,
    private val recovery: WorkspaceRecoveryGate,
) : ScreenDataManager {
    override fun watch(screen: ScreenId, stableId: String?) = flow {
        recovery.awaitReady()
        emitAll(delegate.watch(screen, stableId))
    }

    override suspend fun execute(screen: ScreenId, command: ScreenCommand) = domainResult {
        recovery.awaitReady()
        delegate.execute(screen, command).getOrThrow()
    }

    override suspend fun previewStage(command: ScreenCommand) = domainResult {
        recovery.awaitReady()
        delegate.previewStage(command).getOrThrow()
    }

    override suspend fun previewPhoto(value: String) = domainResult {
        recovery.awaitReady()
        delegate.previewPhoto(value).getOrThrow()
    }
}
