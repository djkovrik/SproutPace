package com.sedsoftware.sproutpace

import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sedsoftware.sproutpace.root.AppRootComponent

/** The application-scoped graph has no Activity dispatcher; route platform Back through its guard. */
internal fun ComponentActivity.bindRootBackNavigation(root: AppRootComponent) {
    val imeView = window.decorView
    val imeType = WindowInsetsCompat.Type.ime()
    val callback = RootBackPressedCallback(
        onNavigateBack = root::back,
        isImeVisible = { ViewCompat.getRootWindowInsets(imeView)?.isVisible(imeType) == true },
        hideIme = { WindowInsetsControllerCompat(window, imeView).hide(imeType) },
    )
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

/** Native callback ordering must never make a keyboard-dismiss gesture leave the current draft. */
internal class RootBackPressedCallback(
    private val onNavigateBack: () -> Unit,
    private val isImeVisible: () -> Boolean,
    private val hideIme: () -> Unit,
) : OnBackPressedCallback(false) {
    private var imeVisibleAtGestureStart = false

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        imeVisibleAtGestureStart = isImeVisible()
    }

    override fun handleOnBackCancelled() {
        imeVisibleAtGestureStart = false
    }

    override fun handleOnBackPressed() {
        val consumeForIme = imeVisibleAtGestureStart || isImeVisible()
        imeVisibleAtGestureStart = false
        if (consumeForIme) hideIme() else onNavigateBack()
    }
}
