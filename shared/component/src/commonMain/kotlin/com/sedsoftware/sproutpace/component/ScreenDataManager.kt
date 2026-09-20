package com.sedsoftware.sproutpace.component

import kotlinx.coroutines.flow.Flow

/**
 * UI-independent boundary implemented by the assembly/data layer. Implementations map domain data
 * to stable IDs and resource keys and create one flat Kotlin Result boundary per operation.
 */
interface ScreenDataManager {
    fun watch(screen: ScreenId, stableId: String? = null): Flow<ScreenSnapshot>
    suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot>
}

data class ScreenSnapshot(
    val items: List<ScreenItem> = emptyList(),
    val selectedItemId: String? = null,
    val primaryActionKey: TextKey? = null,
    val permissionState: PermissionState = PermissionState.NOT_REQUIRED,
    val conflictResolutionKeys: List<TextKey> = emptyList(),
    val offline: Boolean = true,
    val showInlineAdSlot: Boolean = false,
    val destructiveConfirmationKey: TextKey? = null,
    val fields: List<ScreenField> = emptyList(),
    val facts: List<ScreenFact> = emptyList(),
    val actions: List<ScreenActionDescriptor> = emptyList(),
)

object EmptyScreenDataManager : ScreenDataManager {
    override fun watch(screen: ScreenId, stableId: String?) =
        kotlinx.coroutines.flow.flowOf(ScreenSnapshot())

    override suspend fun execute(screen: ScreenId, command: ScreenCommand): Result<ScreenSnapshot> =
        Result.success(ScreenSnapshot())
}
