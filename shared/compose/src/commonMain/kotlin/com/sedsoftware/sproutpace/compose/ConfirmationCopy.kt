package com.sedsoftware.sproutpace.compose

import com.sedsoftware.sproutpace.component.DestructiveOperation

internal data class ConfirmationCopy(val title: String, val body: String, val confirm: String)

internal fun confirmationCopy(operation: DestructiveOperation): ConfirmationCopy = when (operation) {
    DestructiveOperation.SKIP_ACTION -> ConfirmationCopy(
        "confirmation_skip_task_title", "confirmation_skip_task_body", "action_skip_action",
    )
    DestructiveOperation.DISCARD_CHANGES -> ConfirmationCopy(
        "confirmation_discard_title", "confirmation_discard_body", "action_discard_changes",
    )
    DestructiveOperation.REPLACE_RESTORE -> ConfirmationCopy(
        "confirmation_replace_title", "confirmation_replace_body", "confirmation_replace_action",
    )
    DestructiveOperation.DELETE_ALL_DATA -> ConfirmationCopy(
        "confirmation_reset_title", "confirmation_reset_body", "action_delete_all_data",
    )
    DestructiveOperation.SAVE_BATCH_CORRECTION -> ConfirmationCopy(
        "confirmation_correction_title", "confirmation_correction_body", "confirmation_correction_action",
    )
    DestructiveOperation.UNDO_LAST_TRANSITION -> ConfirmationCopy(
        "confirmation_undo_title", "confirmation_undo_body", "confirmation_undo_action",
    )
    else -> ConfirmationCopy(
        "confirmation_${operation.name.lowercase()}", "confirmation_delete_body", "action_confirm_delete",
    )
}
