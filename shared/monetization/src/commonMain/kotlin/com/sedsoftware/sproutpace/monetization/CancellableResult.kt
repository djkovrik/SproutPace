package com.sedsoftware.sproutpace.monetization

import kotlinx.coroutines.CancellationException

/** Cancellation belongs to the caller's lifecycle, never to a disabled-ad/error result. */
internal inline fun <T> cancellableResult(block: () -> T): Result<T> = runCatching(block).onFailure {
    if (it is CancellationException) throw it
}
