package com.nextgen.nxplayer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nextgen.nxplayer.ui.screens.privacy.PrivacyIntroDialog
import com.nextgen.nxplayer.ui.screens.privacy.PrivacyDashboardScreen
import com.nextgen.nxplayer.ui.screens.library.LibraryScreen
import com.nextgen.nxplayer.ui.screens.player.PlayerQueueStore
import com.nextgen.nxplayer.ui.screens.player.PlayerScreen
import com.nextgen.nxplayer.ui.screens.settings.SettingsScreen
import com.nextgen.nxplayer.ui.theme.NXPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("nxplayer_prefs", Context.MODE_PRIVATE)
        val privacyAccepted = prefs.getBoolean("privacy_accepted", false)

        setContent {
            NXPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var showPrivacyDialog by remember { mutableStateOf(!privacyAccepted) }

                    PrivacyIntroDialog(show = showPrivacyDialog) {
                        showPrivacyDialog = false
                        prefs.edit().putBoolean("privacy_accepted", true).apply()
                    }

                    NavHost(navController, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(
                                onVideoClick = { video, queue ->
                                    PlayerQueueStore.setQueue(queue, video)
                                    val encoded = Uri.encode(video.uri.toString())
                                    navController.navigate("player/$encoded")
                                },
                                onSettingsClick = { navController.navigate("settings") },
                                onPrivacyClick = { navController.navigate("privacy") }
                            )
                        }
                        composable(
                            "player/{videoUri}",
                            arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
                        ) { entry ->
                            val encoded = entry.arguments?.getString("videoUri") ?: ""
                            val uri = Uri.decode(encoded)
                            PlayerScreen(
                                videoUri = uri,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen()
                        }
                        composable("privacy") {
                            PrivacyDashboardScreen()
                        }
                    }
                }
            }
        }
    }
}