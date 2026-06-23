package com.nextgen.nxplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.ui.screens.library.LibraryScreen
import com.nextgen.nxplayer.ui.screens.player.PlayerQueueStore
import com.nextgen.nxplayer.ui.screens.player.PlayerScreen
import com.nextgen.nxplayer.ui.screens.privacy.PrivacyDashboardScreen
import com.nextgen.nxplayer.ui.screens.privacy.PrivacyIntroDialog
import com.nextgen.nxplayer.ui.screens.settings.SettingsScreen
import com.nextgen.nxplayer.ui.theme.NXPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsManager = PreferencesManager(applicationContext)
        val privacyAccepted = prefsManager.privacyAccepted

        setContent {
            NXPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    val navController = rememberNavController()
                    var showPrivacyDialog by remember { mutableStateOf(!privacyAccepted) }

                    PrivacyIntroDialog(show = showPrivacyDialog) {
                        showPrivacyDialog = false
                        prefsManager.privacyAccepted = true
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "library"
                    ) {

                        composable("library") {
                            LibraryScreen(
                                onVideoClick = { video, queue ->
                                    PlayerQueueStore.setQueue(queue, video)
                                    navController.navigate("player") {
                                        launchSingleTop = true
                                    }
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                },
                                onPrivacyClick = {
                                    navController.navigate("privacy")
                                }
                            )
                        }

                        composable("player") {
                            PlayerScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPrivacyClick = {
                                    navController.navigate("privacy")
                                }
                            )
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