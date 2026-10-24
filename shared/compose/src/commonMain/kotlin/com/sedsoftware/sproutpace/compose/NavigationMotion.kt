package com.sedsoftware.sproutpace.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

private const val NAVIGATION_DURATION_MILLIS = 250
private const val WINDOW_SCALE = 0.94f
private val tabOrder = listOf(UiScreen.TODAY, UiScreen.TRAYS, UiScreen.PLAN, UiScreen.CATALOG)

private fun navigationSlideDirection(from: SproutPaceUiState, to: SproutPaceUiState): Int {
    if (from.screen == UiScreen.TRAYS && to.screen == UiScreen.TRAYS) {
        return (to.trayCategory.ordinal - from.trayCategory.ordinal).compareTo(0)
    }
    val previous = tabOrder.indexOf(from.screen)
    val next = tabOrder.indexOf(to.screen)
    return if (previous >= 0 && next >= 0) (next - previous).compareTo(0) else 0
}

private data class NavigationFrame(
    val key: Any,
    val state: SproutPaceUiState,
    val onAction: (UiAction) -> Unit,
)

@Composable
internal fun NavigationContent(
    state: SproutPaceUiState,
    navigationKey: Any,
    onAction: (UiAction) -> Unit,
    content: @Composable (SproutPaceUiState, (UiAction) -> Unit) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val contentKey = navigationKey to state.trayCategory.takeIf { state.screen == UiScreen.TRAYS }
    AnimatedContent(
        targetState = NavigationFrame(contentKey, state, onAction),
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentKey = { it.key },
        transitionSpec = {
            val direction = navigationSlideDirection(initialState.state, targetState.state) *
                if (layoutDirection == LayoutDirection.Rtl) -1 else 1
            navigationTransform(direction).using(null)
        },
        label = "navigation",
    ) { frame ->
        val active = frame.key == contentKey
        // Outgoing snapshots remain visible during exit, but cannot receive input or TalkBack focus.
        Box(if (active) Modifier else Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
            val visibleState = if (active) frame.state else frame.state.copy(showInlineAd = false, inlineAdUnitId = null)
            content(visibleState) { action -> if (active) frame.onAction(action) }
        }
    }
}

private fun navigationTransform(direction: Int): ContentTransform {
    val enter = tween<Float>(NAVIGATION_DURATION_MILLIS, easing = FastOutSlowInEasing)
    val motion = if (direction == 0) {
        (fadeIn(enter) + scaleIn(enter, initialScale = WINDOW_SCALE)) togetherWith
            (fadeOut(enter) + scaleOut(enter, targetScale = WINDOW_SCALE))
    } else {
        slideInHorizontally(tween(NAVIGATION_DURATION_MILLIS, easing = FastOutSlowInEasing)) { it * direction } togetherWith
            slideOutHorizontally(tween(NAVIGATION_DURATION_MILLIS, easing = FastOutSlowInEasing)) { -it * direction }
    }
    return motion
}

/** Keep the native dialog window mounted until its exit motion has finished. */
@Composable
internal fun <T : Any> AnimatedNavigationDialog(item: T?, content: @Composable (T, Modifier) -> Unit) {
    var retained by remember { mutableStateOf(item) }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = item != null
    SideEffect {
        if (item != null) retained = item
        else if (visibility.isIdle && !visibility.currentState) retained = null
    }
    val transition = rememberTransition(visibility, label = "navigation-dialog")
    val alpha by transition.animateFloat(
        transitionSpec = { tween(NAVIGATION_DURATION_MILLIS) }, label = "alpha",
    ) { if (it) 1f else 0f }
    val scale by transition.animateFloat(
        transitionSpec = { tween(NAVIGATION_DURATION_MILLIS, easing = FastOutSlowInEasing) }, label = "scale",
    ) { if (it) 1f else WINDOW_SCALE }
    if (visibility.currentState || visibility.targetState) {
        (item ?: retained)?.let { dialog ->
            content(dialog, Modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }.then(if (item == null) Modifier.clearAndSetSemantics {} else Modifier))
        }
    }
}
