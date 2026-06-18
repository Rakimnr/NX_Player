package com.nextgen.nxplayer.ui.screens.privacy

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PrivacyIntroDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Your privacy matters")
        },
        text = {
            Text(
                "NX Player is offline by default.\n\n" +
                        "The app requests no permission on install. Video library access is requested only when you choose to open your full local video library.\n\n" +
                        "You can also open a single video using the system video picker without granting full library access.\n\n" +
                        "NX Player does not use internet, ads, analytics, camera, location, phone, or microphone access."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}