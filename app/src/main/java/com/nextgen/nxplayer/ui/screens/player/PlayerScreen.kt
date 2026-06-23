@file:Suppress("SpellCheckingInspection")
package com.nextgen.nxplayer.ui.screens.player


import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.util.TypedValue
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.nextgen.nxplayer.ui.screens.player.controls.PlaybackControls
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val selectedVideo = remember { PlayerQueueStore.getSelectedVideo() }
    val uri = remember(selectedVideo?.uri) { selectedVideo?.uri?.toUri() }

    if (uri == null) {
        MissingSelectedVideoState(onBack = onBack)
        return
    }

    val videoTitle by viewModel.videoTitle.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val kidsLocked by viewModel.kidsLocked.collectAsState()
    val currentSpeed by viewModel.speed.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val selectedSubIndex by viewModel.selectedSubtitleIndex.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val selectedAudioIndex by viewModel.selectedAudioIndex.collectAsState()
    val audioBoostEnabled by viewModel.audioBoostEnabled.collectAsState()
    val playerMessage by viewModel.playerMessage.collectAsState()
    val showResumeDialog by viewModel.showResumeDialog.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val aspectMode by viewModel.aspectMode.collectAsState()
    val hasNextVideo by viewModel.hasNextVideo.collectAsState()
    val hasPreviousVideo by viewModel.hasPreviousVideo.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val subtitleSyncOffsetMs by viewModel.subtitleSyncOffsetMs.collectAsState()
    val subtitleEncoding by viewModel.subtitleEncoding.collectAsState()
    val externalSubtitleName by viewModel.externalSubtitleName.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
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

    fun openSystemEqualizer() {
        val intent = viewModel.createSystemEqualizerIntent()
        if (intent == null) {
            viewModel.showMessage("System equalizer unavailable")
            return
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            viewModel.showMessage("No system equalizer app found")
        }
    }

    fun leavePlayer() {
        viewModel.pauseAndSaveForExit()
        onBack()
    }

    BackHandler(enabled = true) {
        when {
            showAudioDialog -> showAudioDialog = false
            showSubtitleDialog -> showSubtitleDialog = false
            showSpeedDialog -> showSpeedDialog = false
            kidsLocked -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.unlockControls()
                controlsVisible = true
            }
            else -> leavePlayer()
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.saveCurrentPosition()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveCurrentPosition()
        }
    }

    LaunchedEffect(uri) {
        controlsVisible = true
        zoomScale = 1f
        viewModel.initializePlayer(uri)
    }

    LaunchedEffect(isPlaying, controlsVisible, kidsLocked, errorMessage) {
        if (isPlaying && controlsVisible && !kidsLocked && errorMessage == null) {
            delay(3000)
            controlsVisible = false
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
                    resizeMode = aspectMode.toResizeMode()
                    applyNxSubtitleStyle(subtitleStyle)
                }
            },
            update = { playerView ->
                playerView.player = viewModel.getPlayer()
                playerView.resizeMode = aspectMode.toResizeMode()
                playerView.applyNxSubtitleStyle(subtitleStyle)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
        )

        if (!kidsLocked && errorMessage == null) {
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

        if (showResumeDialog && errorMessage == null) {
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

        if (controlsVisible && !kidsLocked && errorMessage == null) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = videoTitle.ifBlank { "Video" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

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

                    TextButton(onClick = { viewModel.cycleAspectMode() }) {
                        Text(aspectMode.label, color = Color.White)
                    }

                    TextButton(onClick = { showAudioDialog = true }) {
                        Text("Audio", color = Color.White)
                    }

                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(
                            Icons.Rounded.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { showSpeedDialog = true }) {
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
                    canSkipPrevious = hasPreviousVideo,
                    canSkipNext = hasNextVideo,
                    onPlayPause = { viewModel.playPause() },
                    onSkipNext = { viewModel.skipToNextVideo() },
                    onSkipPrevious = { viewModel.skipToPreviousVideo() }
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

        errorMessage?.let { message ->
            PlayerErrorOverlay(
                message = message,
                onRetry = { viewModel.retryCurrent() },
                onBack = { leavePlayer() }
            )
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
                subtitlesEnabled = subtitlesEnabled,
                externalSubtitleName = externalSubtitleName,
                subtitleStyle = subtitleStyle,
                subtitleSyncOffsetMs = subtitleSyncOffsetMs,
                subtitleEncoding = subtitleEncoding,
                onLoadExternalSubtitle = {
                    showSubtitleDialog = false
                    subtitlePicker.launch(
                        arrayOf(
                            "application/x-subrip",
                            "text/plain",
                            "text/vtt",
                            "text/x-ssa",
                            "application/ttml+xml",
                            "*/*"
                        )
                    )
                },
                onTrackSelected = { index ->
                    viewModel.selectSubtitleTrack(index)
                },
                onToggleSubtitles = { enabled ->
                    viewModel.setSubtitlesEnabled(enabled)
                },
                onFontSizeChanged = { sizeSp ->
                    viewModel.setSubtitleFontSize(sizeSp)
                },
                onColorSelected = { color ->
                    viewModel.setSubtitleColor(color)
                },
                onBackgroundOpacityChanged = { opacity ->
                    viewModel.setSubtitleBackgroundOpacity(opacity)
                },
                onSyncOffset = { deltaMs ->
                    viewModel.adjustSubtitleSyncOffset(deltaMs)
                },
                onEncodingSelected = { encoding ->
                    viewModel.setSubtitleEncoding(encoding)
                },
                onResetStyle = {
                    viewModel.resetSubtitleStyle()
                },
                onDismiss = { showSubtitleDialog = false }
            )
        }

        if (showAudioDialog) {
            AudioTrackDialog(
                tracks = audioTracks.map { it.name },
                selectedIndex = selectedAudioIndex,
                audioBoostEnabled = audioBoostEnabled,
                onTrackSelected = { index ->
                    viewModel.selectAudioTrack(index)
                    showAudioDialog = false
                },
                onAuto = {
                    viewModel.selectAudioTrack(-1)
                    showAudioDialog = false
                },
                onToggleAudioBoost = {
                    viewModel.toggleAudioBoost()
                },
                onOpenSystemEqualizer = {
                    openSystemEqualizer()
                },
                onDismiss = { showAudioDialog = false }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun PlayerAspectMode.toResizeMode(): Int {
    return when (this) {
        PlayerAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerAspectMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        PlayerAspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
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
                                onZoomChanged(zoomChange)
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
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY
                            val heldTime = releaseTime - startTime

                            if (!dragged && heldTime >= 550L && isCenterThird(startX, size.width)) {
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

                                else -> Unit
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
private fun MissingSelectedVideoState(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No video selected",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please open a video again from the library.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Can't play this video",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }

                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
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
                    progress = { unlockProgress },
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
    subtitlesEnabled: Boolean,
    externalSubtitleName: String?,
    subtitleStyle: SubtitleStyleSettings,
    subtitleSyncOffsetMs: Long,
    subtitleEncoding: SubtitleEncodingOption,
    onLoadExternalSubtitle: () -> Unit,
    onTrackSelected: (Int) -> Unit,
    onToggleSubtitles: (Boolean) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onColorSelected: (SubtitleTextColorOption) -> Unit,
    onBackgroundOpacityChanged: (Float) -> Unit,
    onSyncOffset: (Long) -> Unit,
    onEncodingSelected: (SubtitleEncodingOption) -> Unit,
    onResetStyle: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
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

                externalSubtitleName?.let { name ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle display",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = { onToggleSubtitles(!subtitlesEnabled) }
                    ) {
                        Text(if (subtitlesEnabled) "On" else "Off")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Embedded / external tracks",
                    style = MaterialTheme.typography.titleSmall
                )

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
                                color = if (index == selectedIndex && subtitlesEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sync offset: ${formatSubtitleOffset(subtitleSyncOffsetMs)}",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSyncOffset(-500L) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.5s")
                    }

                    OutlinedButton(
                        onClick = { onSyncOffset(500L) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.5s")
                    }
                }

                Text(
                    text = "Offset is applied to external or sidecar SRT subtitles. Embedded/image subtitles stay selectable, but cannot be retimed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Font size: ${subtitleStyle.fontSizeSp.roundToInt()}sp",
                    style = MaterialTheme.typography.titleSmall
                )

                Slider(
                    value = subtitleStyle.fontSizeSp,
                    onValueChange = onFontSizeChanged,
                    valueRange = 14f..34f
                )

                Text(
                    text = "Font color",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubtitleTextColorOption.entries.forEach { colorOption ->
                        OutlinedButton(
                            onClick = { onColorSelected(colorOption) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = colorOption.label,
                                color = if (subtitleStyle.textColor == colorOption) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Background opacity: ${(subtitleStyle.backgroundOpacity * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall
                )

                Slider(
                    value = subtitleStyle.backgroundOpacity,
                    onValueChange = onBackgroundOpacityChanged,
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Encoding override",
                    style = MaterialTheme.typography.titleSmall
                )

                SubtitleEncodingOption.entries.forEach { encoding ->
                    TextButton(
                        onClick = { onEncodingSelected(encoding) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = encoding.label,
                            color = if (encoding == subtitleEncoding) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
                    }
                }

                Text(
                    text = "Encoding override is used when preparing external or sidecar SRT subtitles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onResetStyle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset subtitle style")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun AudioTrackDialog(
    tracks: List<String>,
    selectedIndex: Int,
    audioBoostEnabled: Boolean,
    onTrackSelected: (Int) -> Unit,
    onAuto: () -> Unit,
    onToggleAudioBoost: () -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Audio tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = onOpenSystemEqualizer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open system equalizer")
                }

                TextButton(
                    onClick = onToggleAudioBoost,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (audioBoostEnabled) "Audio boost: On" else "Audio boost: Off",
                        color = if (audioBoostEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        }
                    )
                }

                Text(
                    text = "Track selection is remembered separately for each video.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Audio tracks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

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
                        text = "No selectable audio tracks found yet.",
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
                                text = if (index == selectedIndex) "✓ $track" else track,
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
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun PlayerView.applyNxSubtitleStyle(settings: SubtitleStyleSettings) {
    findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)?.apply {
        setApplyEmbeddedStyles(false)
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp)
        setStyle(
            CaptionStyleCompat(
                settings.textColor.androidColor,
                blackWithOpacity(settings.backgroundOpacity),
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                AndroidColor.BLACK,
                null
            )
        )
    }
}

private fun blackWithOpacity(opacity: Float): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
    return AndroidColor.argb(alpha, 0, 0, 0)
}

private fun formatSubtitleOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "0.0s"

    val sign = if (offsetMs > 0L) "+" else "-"
    val seconds = abs(offsetMs) / 1000f
    return "$sign${"%.1f".format(seconds)}s"
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
