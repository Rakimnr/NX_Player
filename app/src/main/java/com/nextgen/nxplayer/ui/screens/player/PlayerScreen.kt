package com.nextgen.nxplayer.ui.screens.player

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.nextgen.nxplayer.ui.screens.player.controls.PlaybackControls
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val audioTracks by viewModel.audioTracks.collectAsState()
    val selectedAudioIndex by viewModel.selectedAudioIndex.collectAsState()
    val playerMessage by viewModel.playerMessage.collectAsState()
    val showResumeDialog by viewModel.showResumeDialog.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val hapticFeedback = LocalHapticFeedback.current

    var controlsVisible by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableStateOf(1f) }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { subtitleUri: Uri? ->
        subtitleUri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not support persistable URI permissions.
            }

            viewModel.loadExternalSubtitle(it)
        }
    }

    LaunchedEffect(uri) {
        controlsVisible = true
        zoomScale = 1f
        viewModel.initializePlayer(uri)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    LaunchedEffect(isPlaying, controlsVisible, kidsLocked) {
        if (isPlaying && controlsVisible && !kidsLocked) {
            delay(3000)
            if (isPlaying && controlsVisible && !kidsLocked) {
                controlsVisible = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.getPlayer()
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                playerView.player = viewModel.getPlayer()
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
        )

        if (!kidsLocked) {
            MxGestureLayer(
                activity = activity,
                audioManager = audioManager,
                onSingleTap = {
                    controlsVisible = !controlsVisible
                },
                onDoubleTapLeft = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.seekRelative(-10_000L)
                },
                onDoubleTapRight = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.seekRelative(10_000L)
                },
                onLongPressCenter = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.lockControls()
                    controlsVisible = false
                },
                onSeek = { delta ->
                    viewModel.seekRelative(delta)
                },
                onBrightnessChanged = { percent ->
                    viewModel.showMessage("Brightness $percent%")
                },
                onVolumeChanged = { percent ->
                    viewModel.showMessage("Volume $percent%")
                },
                onZoomChanged = { zoomChange ->
                    val oldZoom = zoomScale
                    zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3f)

                    if (abs(oldZoom - zoomScale) >= 0.03f) {
                        viewModel.showMessage("Zoom ${(zoomScale * 100f).roundToInt()}%")
                    }
                }
            )
        }

        if (showResumeDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Resume Video") },
                text = { Text("Continue from where you left off?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resumePlayback() }) {
                        Text("Resume")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.startFromBeginning() }) {
                        Text("Start Over")
                    }
                }
            )
        }

        if (controlsVisible && !kidsLocked) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (zoomScale > 1.01f) {
                        TextButton(
                            onClick = {
                                zoomScale = 1f
                                viewModel.showMessage("Zoom reset")
                            }
                        ) {
                            Text("Reset zoom", color = Color.White)
                        }
                    }

                    TextButton(
                        onClick = { showAudioDialog = true }
                    ) {
                        Text("Audio", color = Color.White)
                    }

                    IconButton(
                        onClick = { showSubtitleDialog = true }
                    ) {
                        Icon(
                            Icons.Rounded.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { showSpeedDialog = true }
                    ) {
                        Text(
                            text = "${formatSpeed(currentSpeed)}x",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.lockControls()
                            controlsVisible = false
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Lock",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PlaybackControls(
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.playPause() },
                    onSkipNext = { viewModel.showMessage("Next video not added yet") },
                    onSkipPrevious = { viewModel.showMessage("Previous video not added yet") }
                )

                Slider(
                    value = if (duration > 0L) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { progress ->
                        if (duration > 0L) {
                            viewModel.seekTo((progress * duration).toLong())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlayerTime(currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Text(
                        text = formatPlayerTime(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        playerMessage?.let {
            PlayerFeedbackOverlay(message = it)
        }

        if (kidsLocked) {
            LockedPlayerOverlay(
                onUnlock = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.unlockControls()
                    controlsVisible = true
                }
            )
        }

        if (showSpeedDialog) {
            SpeedDialog(
                currentSpeed = currentSpeed,
                onSpeedSelected = { selectedSpeed ->
                    viewModel.setSpeed(selectedSpeed)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        if (showSubtitleDialog) {
            SubtitleDialog(
                tracks = subtitleTracks.map { it.name },
                selectedIndex = selectedSubIndex,
                onLoadExternalSubtitle = {
                    showSubtitleDialog = false
                    subtitlePicker.launch(
                        arrayOf(
                            "application/x-subrip",
                            "text/plain",
                            "text/vtt",
                            "application/ttml+xml",
                            "*/*"
                        )
                    )
                },
                onTrackSelected = { index ->
                    viewModel.selectSubtitleTrack(index)
                    showSubtitleDialog = false
                },
                onDisable = {
                    viewModel.selectSubtitleTrack(-1)
                    showSubtitleDialog = false
                },
                onDismiss = { showSubtitleDialog = false }
            )
        }

        if (showAudioDialog) {
            AudioTrackDialog(
                tracks = audioTracks.map { it.name },
                selectedIndex = selectedAudioIndex,
                onTrackSelected = { index ->
                    viewModel.selectAudioTrack(index)
                    showAudioDialog = false
                },
                onAuto = {
                    viewModel.selectAudioTrack(-1)
                    showAudioDialog = false
                },
                onDismiss = { showAudioDialog = false }
            )
        }
    }
}

@Composable
private fun MxGestureLayer(
    activity: Activity?,
    audioManager: AudioManager?,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onLongPressCenter: () -> Unit,
    onSeek: (Long) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onZoomChanged: (Float) -> Unit
) {
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val startX = down.position.x
                    val startY = down.position.y
                    var lastPosition = down.position
                    var dragged = false
                    var dragMode = DragMode.NONE
                    var previousPinchDistance: Float? = null
                    val startTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }

                        if (pressedChanges.size >= 2) {
                            val first = pressedChanges[0].position
                            val second = pressedChanges[1].position
                            val pinchDistance = distanceBetween(first, second)
                            val previousDistance = previousPinchDistance

                            dragged = true
                            dragMode = DragMode.ZOOM

                            if (previousDistance != null && previousDistance > 0f) {
                                val zoomChange = (pinchDistance / previousDistance).coerceIn(0.85f, 1.15f)

                                if (abs(zoomChange - 1f) > 0.01f) {
                                    onZoomChanged(zoomChange)
                                }
                            }

                            previousPinchDistance = pinchDistance
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break

                        if (!change.pressed) {
                            val releaseTime = change.uptimeMillis
                            val totalTime = releaseTime - startTime
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY

                            if (!dragged && totalTime > 450L && isCenterThird(startX, size.width)) {
                                onLongPressCenter()
                            } else if (!dragged && abs(totalDx) < 25f && abs(totalDy) < 25f) {
                                if (releaseTime - lastTapTime < 300L) {
                                    when {
                                        startX < size.width / 3f -> onDoubleTapLeft()
                                        startX > size.width * 2f / 3f -> onDoubleTapRight()
                                        else -> onSingleTap()
                                    }
                                    lastTapTime = 0L
                                } else {
                                    lastTapTime = releaseTime
                                    onSingleTap()
                                }
                            }

                            break
                        }

                        val dx = change.position.x - startX
                        val dy = change.position.y - startY

                        if (!dragged && (abs(dx) > 35f || abs(dy) > 35f)) {
                            dragged = true
                            dragMode = if (abs(dx) > abs(dy)) {
                                DragMode.SEEK
                            } else if (startX < size.width / 2f) {
                                DragMode.BRIGHTNESS
                            } else {
                                DragMode.VOLUME
                            }
                        }

                        if (dragged) {
                            when (dragMode) {
                                DragMode.SEEK -> {
                                    val deltaX = change.position.x - lastPosition.x
                                    val seekDelta =
                                        (deltaX / size.width.toFloat() * 60_000L).toLong()

                                    if (abs(seekDelta) >= 300L) {
                                        onSeek(seekDelta)
                                    }
                                }

                                DragMode.BRIGHTNESS -> {
                                    val deltaY = change.position.y - lastPosition.y
                                    val percentDelta = -deltaY / size.height.toFloat()

                                    activity?.window?.let { window ->
                                        val attrs = window.attributes
                                        val currentBrightness =
                                            if (attrs.screenBrightness >= 0f) {
                                                attrs.screenBrightness
                                            } else {
                                                0.5f
                                            }

                                        val newBrightness =
                                            (currentBrightness + percentDelta).coerceIn(0.01f, 1.0f)

                                        attrs.screenBrightness = newBrightness
                                        window.attributes = attrs

                                        onBrightnessChanged((newBrightness * 100f).roundToInt())
                                    }
                                }

                                DragMode.VOLUME -> {
                                    val maxVolume = audioManager?.getStreamMaxVolume(
                                        AudioManager.STREAM_MUSIC
                                    ) ?: 15

                                    val currentVolume = audioManager?.getStreamVolume(
                                        AudioManager.STREAM_MUSIC
                                    ) ?: 0

                                    val deltaY = change.position.y - lastPosition.y
                                    val volumeDelta =
                                        (-deltaY / size.height.toFloat() * maxVolume).roundToInt()

                                    if (volumeDelta != 0) {
                                        val newVolume =
                                            (currentVolume + volumeDelta).coerceIn(0, maxVolume)

                                        audioManager?.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolume,
                                            0
                                        )

                                        val percent = if (maxVolume > 0) {
                                            ((newVolume.toFloat() / maxVolume.toFloat()) * 100f)
                                                .roundToInt()
                                        } else {
                                            0
                                        }

                                        onVolumeChanged(percent)
                                    }
                                }

                                DragMode.ZOOM,
                                DragMode.NONE -> Unit
                            }

                            lastPosition = change.position
                            change.consume()
                        }
                    }
                }
            }
    )
}

private enum class DragMode {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
    ZOOM
}

@Composable
private fun PlayerFeedbackOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun LockedPlayerOverlay(onUnlock: () -> Unit) {
    var unlockProgress by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break

                        if (!change.pressed) {
                            unlockProgress = 0f
                            break
                        }

                        val elapsed = change.uptimeMillis - startTime
                        unlockProgress = (elapsed / 3000f).coerceIn(0f, 1f)

                        if (unlockProgress >= 1f) {
                            unlockProgress = 0f
                            onUnlock()
                            event.changes.forEach { it.consume() }
                            break
                        }

                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Controls locked",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Press and hold for 3 seconds to unlock",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = unlockProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(
        0.25f,
        0.5f,
        0.75f,
        1.0f,
        1.25f,
        1.5f,
        1.75f,
        2.0f,
        2.5f,
        3.0f,
        3.5f,
        4.0f
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    TextButton(
                        onClick = { onSpeedSelected(speed) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatSpeed(speed)}x",
                            color = if (abs(speed - currentSpeed) < 0.001f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
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
    onLoadExternalSubtitle: () -> Unit,
    onTrackSelected: (Int) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column {
                Button(
                    onClick = onLoadExternalSubtitle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Load subtitle file")
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Off",
                        color = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        }
                    )
                }

                if (tracks.isEmpty()) {
                    Text(
                        text = "No embedded subtitle tracks found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    tracks.forEachIndexed { index, track ->
                        TextButton(
                            onClick = { onTrackSelected(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = track,
                                color = if (index == selectedIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun AudioTrackDialog(
    tracks: List<String>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onAuto: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Track") },
        text = {
            Column {
                TextButton(
                    onClick = onAuto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Auto",
                        color = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        }
                    )
                }

                if (tracks.isEmpty()) {
                    Text(
                        text = "No separate audio tracks found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    tracks.forEachIndexed { index, track ->
                        TextButton(
                            onClick = { onTrackSelected(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = track,
                                color = if (index == selectedIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

fun formatPlayerTime(milliseconds: Long): String {
    val safeMilliseconds = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safeMilliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        speed.toInt().toString()
    } else {
        speed.toString().trimEnd('0').trimEnd('.')
    }
}

private fun isCenterThird(x: Float, width: Int): Boolean {
    return x >= width / 3f && x <= width * 2f / 3f
}

private fun distanceBetween(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return sqrt(dx * dx + dy * dy)
}
