package com.nextgen.nxplayer.ui.screens.privacy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nextgen.nxplayer.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen() {
    val context = LocalContext.current

    val videoPermission = PermissionHelper.videoPermission()
    val videoPermissionName = PermissionHelper.videoPermissionDisplayName()
    val videoPermissionGranted =
        ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Privacy Dashboard") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Permission design",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            PrivacyStatusRow(
                name = videoPermissionName,
                status = if (videoPermissionGranted) "Granted" else "Not granted",
                description = "Used only for the full local video library."
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PrivacyStatusRow(
                    name = "READ_EXTERNAL_STORAGE",
                    status = "Not used on Android 13+",
                    description = "Legacy permission kept only for Android 12 and below."
                )
            } else {
                val legacyGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED

                PrivacyStatusRow(
                    name = "READ_EXTERNAL_STORAGE",
                    status = if (legacyGranted) "Granted" else "Not granted",
                    description = "Used only on Android 12 and below."
                )
            }

            PrivacyStatusRow(
                name = "READ_MEDIA_AUDIO",
                status = "Not declared",
                description = "Not needed because NX Player does not scan standalone audio files yet."
            )

            PrivacyStatusRow(
                name = "INTERNET",
                status = "Not declared",
                description = "Core app is offline. No online subtitle search, ads, or analytics."
            )

            PrivacyStatusRow(
                name = "MANAGE_EXTERNAL_STORAGE",
                status = "Not declared",
                description = "NX Player does not request all-files access."
            )

            PrivacyStatusRow(
                name = "Camera / Location / Phone / Microphone",
                status = "Not declared",
                description = "These permissions are not needed for local playback."
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Network status: Offline core build. No internet permission is declared in the manifest.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Manage app permissions")
            }
        }
    }
}

@Composable
private fun PrivacyStatusRow(
    name: String,
    status: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}