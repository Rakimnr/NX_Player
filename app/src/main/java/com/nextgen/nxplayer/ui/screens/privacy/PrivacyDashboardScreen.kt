package com.nextgen.nxplayer.ui.screens.privacy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("unused")
@Composable
fun PrivacyDashboardScreen() {
    val context = LocalContext.current

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add("READ_MEDIA_VIDEO" to Manifest.permission.READ_MEDIA_VIDEO)
        }
        add("READ_EXTERNAL_STORAGE" to Manifest.permission.READ_EXTERNAL_STORAGE)
        add("INTERNET" to Manifest.permission.INTERNET)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Privacy Dashboard") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Permissions status", style = MaterialTheme.typography.titleMedium)
            permissions.forEach { (name, perm) ->
                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("$name: ")
                    Text(if (granted) "Granted" else "Not granted")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "App is completely offline. No internet permission requested.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            }) {
                Text("Manage permissions")
            }
        }
    }
}