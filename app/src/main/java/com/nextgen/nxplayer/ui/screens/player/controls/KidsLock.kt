package com.nextgen.nxplayer.ui.screens.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun KidsLockOverlay(locked: Boolean, onUnlockRequest: () -> Unit) {
    AnimatedVisibility(visible = locked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onUnlockRequest() },
                        onTap = { /* block accidental taps */ }
                    )
                }
        ) {
            Text(
                "Press and hold to unlock",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}