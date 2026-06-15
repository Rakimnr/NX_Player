package com.nextgen.nxplayer.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.utils.DonationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var resumeEnabled by remember { mutableStateOf(prefs.resumePlayback) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Video", style = MaterialTheme.typography.titleMedium)
            Row {
                Text("Resume playback")
                Switch(checked = resumeEnabled, onCheckedChange = {
                    resumeEnabled = it
                    prefs.resumePlayback = it
                })
            }

            HorizontalDivider()
            Text("Support", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                DonationHelper.openDonationPage(context)
            }) {
                Icon(Icons.Rounded.Support, contentDescription = null)
                Text("Buy me a coffee")
            }
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.nextgen.nxplayer".toUri())
                context.startActivity(intent)
            }) {
                Text("Rate on Play Store")
            }
        }
    }
}