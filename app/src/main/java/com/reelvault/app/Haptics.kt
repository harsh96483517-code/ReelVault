package com.reelvault.app

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The two haptic cues the dock uses: a tick while a drag crosses a tab, and a click when a tab is chosen. */
enum class Haptic { Tick, Select }

class Haptics(private val view: View) {
    fun play(h: Haptic) {
        view.performHapticFeedback(
            when (h) {
                Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                Haptic.Select -> HapticFeedbackConstants.VIRTUAL_KEY
            }
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
