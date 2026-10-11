package com.sedsoftware.sproutpace.platform

/** Native receiver owns finish even when reconciliation, timeout, or retry recording fails. */
suspend fun runReminderBroadcast(work: suspend () -> Unit, onFailure: () -> Unit, finish: () -> Unit) {
    try {
        try {
            work()
        } catch (_: Exception) {
            onFailure()
        }
    } finally {
        finish()
    }
}

/** Caller serializes this transaction; persistence makes duplicate broadcasts at-most-once. */
suspend fun deliverReminderOnce(
    token: String,
    evaluateAndRearm: suspend () -> Boolean,
    readClaim: () -> String?,
    writeClaim: (String?) -> Unit,
    post: () -> Unit,
) {
    if (!evaluateAndRearm() || readClaim() == token) return
    writeClaim(token)
    runCatching { post() }.onFailure { writeClaim(null) }.getOrThrow()
}
