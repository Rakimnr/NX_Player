package com.nextgen.nxplayer.ui.screens.player

import android.media.AudioManager
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextgen.nxplayer.ui.screens.player.controls.KidsLockOverlay
import com.nextgen.nxplayer.ui.screens.player.controls.PlaybackControls
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUri: String,
    viewModel: PlayerViewModel = viewModel()
) {
    val uri = remember(videoUri) { videoUri.toUri() }
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val kidsLocked by viewModel.kidsLocked.collectAsState()
    val currentSpeed by viewModel.speed.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val selectedSubIndex by viewModel.selectedSubtitleIndex.collectAsState()

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    // Remember the SurfaceView so we can attach VLC to it
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    // Initialize player and attach surface when ready
    LaunchedEffect(uri) {
        viewModel.initializePlayer(uri)
    }
    LaunchedEffect(surfaceView) {
        surfaceView?.let { viewModel.attachSurface(it) }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.releasePlayer() }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Auto‑hide controls when playing
    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // VLC video surface
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture overlay (brightness, volume, seek)
        if (!kidsLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var startX = 0f
                        var startY = 0f
                        var startBrightness = 0f
                        var startVolume = 0

                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull() ?: continue
                                when {
                                    pointer.pressed && pointer.previousPressed.not() -> {
                                        startX = pointer.position.x
                                        startY = pointer.position.y
                                        startBrightness = (context as? android.app.Activity)
                                            ?.window?.attributes?.screenBrightness ?: 0.5f
                                        startVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    }
                                    pointer.pressed -> {
                                        val dx = pointer.position.x - startX
                                        val dy = pointer.position.y - startY
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()

                                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 20) {
                                            val seekDelta = (dx / width * 30000).toLong()
                                            viewModel.seekRelative(seekDelta)
                                            startX = pointer.position.x
                                        } else if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && kotlin.math.abs(dy) > 20) {
                                            if (startX < width / 2) {
                                                val brightnessDelta = -dy / height
                                                val newBrightness = (startBrightness + brightnessDelta).coerceIn(0.01f, 1.0f)
                                                (context as? android.app.Activity)?.window?.attributes?.let { attrs ->
                                                    attrs.screenBrightness = newBrightness
                                                    context.window?.attributes = attrs
                                                }
                                            } else {
                                                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                                val volumeDelta = (-dy / height * maxVolume).toInt()
                                                val newVolume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
            )

            // Tap gestures (separate)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val width = size.width.toFloat()
                                if (offset.x < width / 3) viewModel.seekRelative(-10000)
                                else if (offset.x > 2 * width / 3) viewModel.seekRelative(10000)
                            },
                            onLongPress = { viewModel.toggleKidsLock() }
                        )
                    }
            )
        }

        // Controls overlay
        if (controlsVisible && !kidsLocked) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (subtitleTracks.isNotEmpty()) {
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(Icons.Rounded.ClosedCaption, "Subtitles", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        if (viewModel.abRepeatActive.value) viewModel.clearRepeat()
                        else viewModel.setRepeatA()
                    }) {
                        Icon(
                            if (viewModel.abRepeatActive.value) Icons.Rounded.RepeatOne
                            else Icons.Rounded.Repeat,
                            "A-B Repeat",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.addBookmark() }) {
                        Icon(Icons.Rounded.BookmarkAdd, "Bookmark", tint = Color.White)
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = Color.White)
                    }
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Text("${currentSpeed}x", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { viewModel.toggleKidsLock() }) {
                        Icon(Icons.Rounded.Lock, "Lock", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PlaybackControls(
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.playPause() },
                    onSkipNext = {},
                    onSkipPrevious = {}
                )
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // Kids lock overlay
        KidsLockOverlay(locked = kidsLocked, onUnlockRequest = { viewModel.toggleKidsLock() })

        // Dialogs
        if (showSpeedDialog) {
            SpeedDialog(currentSpeed, { viewModel.setSpeed(it); showSpeedDialog = false }) { showSpeedDialog = false }
        }
        if (showSubtitleDialog && subtitleTracks.isNotEmpty()) {
            SubtitleDialog(
                tracks = subtitleTracks.map { it.name },   // show names
                selectedIndex = selectedSubIndex,
                onTrackSelected = { viewModel.selectSubtitleTrack(it); showSubtitleDialog = false },
                onDisable = { viewModel.selectSubtitleTrack(-1); showSubtitleDialog = false },
                onDismiss = { showSubtitleDialog = false }
            )
        }
        if (showSleepTimerDialog) {
            SleepTimerDialog({ viewModel.setSleepTimer(it); showSleepTimerDialog = false }) { showSleepTimerDialog = false }
        }
    }
}

// ------ Dialog composables ------
@Composable
fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    TextButton(onClick = { onSpeedSelected(speed) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${speed}x", color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun SubtitleDialog(
    tracks: List<String>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column {
                TextButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) {
                    Text("Off", color = if (selectedIndex == -1) MaterialTheme.colorScheme.primary else Color.Unspecified)
                }
                tracks.forEachIndexed { index, track ->
                    TextButton(onClick = { onTrackSelected(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(track, color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun SleepTimerDialog(onTimerSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(15, 30, 60, -1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                options.forEach { minutes ->
                    TextButton(onClick = { onTimerSelected(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (minutes == -1) "Off" else "$minutes min")
                    }
                }
            }
        },
        confirmButton = {}
    )
}