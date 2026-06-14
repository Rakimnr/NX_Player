package com.nextgen.nxplayer.ui.screens.privacy

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight

@Composable
fun PrivacyIntroDialog(show: Boolean, onDismiss: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Your privacy matters") },
            text = {
                Text(
                    "NX Player never:\n" +
                            "- Connects to the internet\n" +
                            "- Shows ads\n" +
                            "- Collects any data\n\n" +
                            "It only reads your video files to play them.",
                    fontWeight = FontWeight.Normal
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Got it")
                }
            }
        )
    }
}