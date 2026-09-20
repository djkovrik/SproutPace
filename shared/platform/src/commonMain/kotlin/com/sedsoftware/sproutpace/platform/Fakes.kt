package com.sedsoftware.sproutpace.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNotificationPermissionService(
    initial: PermissionState = PermissionState.Unknown,
    private val requestResult: PermissionState = PermissionState.Granted,
) : NotificationPermissionService {
    private val mutableState = MutableStateFlow(initial)
    override val state = mutableState.asStateFlow()
    var checkCount: Int = 0
        private set
    var requestCount: Int = 0
        private set

    override suspend fun check(): PermissionState {
        checkCount += 1
        return state.value
    }

    override suspend fun request(): PermissionState {
        requestCount += 1
        mutableState.value = requestResult
        return state.value
    }

    fun setSystemState(value: PermissionState) {
        mutableState.value = value
    }
}

class FakeReminderDriver(initial: Iterable<PhysicalReminder> = emptyList()) : ReminderDriver {
    private val reminders = initial.associateByTo(linkedMapOf()) { it.id }
    val scheduledCalls = mutableListOf<PhysicalReminder>()
    val cancelledCalls = mutableListOf<PhysicalReminderId>()

    override suspend fun registeredIds(): Set<PhysicalReminderId> = reminders.keys.toSet()

    override suspend fun schedule(reminder: PhysicalReminder): Result<Unit> = runCatching {
        reminders[reminder.id] = reminder
        scheduledCalls += reminder
    }

    override suspend fun cancel(id: PhysicalReminderId): Result<Unit> = runCatching {
        reminders.remove(id)
        cancelledCalls += id
    }
}

class FakeCrashReportingDriver(override var isConfigured: Boolean = true) : CrashReportingDriver {
    val collectionChanges = mutableListOf<Boolean>()
    val recordedFailureTypes = mutableListOf<String>()

    override fun setCollectionEnabled(enabled: Boolean): Result<Unit> = runCatching {
        collectionChanges += enabled
    }

    override fun recordReviewedTechnicalFailure(failure: Throwable): Result<Unit> = runCatching {
        recordedFailureTypes += failure::class.simpleName.orEmpty()
    }
}
