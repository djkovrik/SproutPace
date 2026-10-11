package com.sedsoftware.sproutpace

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sedsoftware.sproutpace.root.AppRootComponent

/** The application-scoped graph has no Activity dispatcher; route platform Back through its guard. */
internal fun ComponentActivity.bindRootBackNavigation(root: AppRootComponent) {
    val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = root.back()
    }
    onBackPressedDispatcher.addCallback(this, callback)
    fun update() {
        callback.isEnabled = root.confirmation.value.child != null || root.stack.value.backStack.isNotEmpty() ||
            root.stack.value.active.instance.model.value.hasUnsavedChanges
    }
    var cancelModelSubscription: (() -> Unit)? = null
    val stackSubscription = root.stack.subscribe { stack ->
        cancelModelSubscription?.invoke()
        val modelSubscription = stack.active.instance.model.subscribe { update() }
        cancelModelSubscription = modelSubscription::cancel
        update()
    }
    val confirmationSubscription = root.confirmation.subscribe { update() }
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            cancelModelSubscription?.invoke()
            stackSubscription.cancel()
            confirmationSubscription.cancel()
            callback.remove()
        }
    })
}
