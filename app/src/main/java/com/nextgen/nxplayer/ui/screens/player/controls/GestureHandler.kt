package com.nextgen.nxplayer.ui.screens.player.controls

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.nextgen.nxplayer.ui.screens.player.PlayerViewModel
import kotlin.math.abs

@Composable
fun GestureHandler(
    playerViewModel: PlayerViewModel,
    content: @Composable (Modifier) -> Unit
) {
    content(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat()
                        if (offset.x < width / 3) playerViewModel.seekRelative(-10000)
                        else if (offset.x > 2 * width / 3) playerViewModel.seekRelative(10000)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val width = size.width.toFloat()
                    // Horizontal drag → seek
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        // Convert horizontal drag to seek delta (e.g., 30s per screen width)
                        playerViewModel.seekRelative(
                            (dragAmount.x / width * 30000).toLong()
                        )
                    }
                    // Vertical drag → brightness (left) / volume (right)
                    // Implementation would use system APIs (WindowManager.LayoutParams / AudioManager)
                    // Left as a future enhancement.
                }
            }
    )
}