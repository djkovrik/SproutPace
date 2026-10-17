package com.sedsoftware.sproutpace.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Environmental invalidation only: retained screen Stores continue to own models and drafts. */
internal class ScreenTimeUpdates(private val scope: CoroutineScope, private val ticks: Flow<Unit>) {
    private val mutableRevisions = MutableStateFlow(0L)
    val revisions: StateFlow<Long> = mutableRevisions.asStateFlow()
    private var clockObservation: Job? = null

    fun refresh() { mutableRevisions.update { it + 1L } }

    fun onResume() {
        refresh()
        clockObservation?.cancel()
        clockObservation = scope.launch { ticks.collect { refresh() } }
    }

    fun onPause() {
        clockObservation?.cancel()
        clockObservation = null
    }
}

/** Cold and cancellable; graph tests can inject a finite/manual stream instead. */
internal fun minuteScreenTimeTicks(): Flow<Unit> = flow {
    while (currentCoroutineContext().isActive) {
        delay(SCREEN_TIME_REFRESH_PERIOD_MILLIS)
        emit(Unit)
    }
}

private const val SCREEN_TIME_REFRESH_PERIOD_MILLIS = 60_000L
