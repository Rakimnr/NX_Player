@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nextgen.nxplayer.ui.screens.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.nextgen.nxplayer.data.local.AppDatabase
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.data.model.ResumeState
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerAspectMode(val label: String) {
    FIT("Fit"),
    CROP("Crop"),
    STRETCH("Stretch")
}

enum class SubtitleTextColorOption(
    val label: String,
    val androidColor: Int
) {
    WHITE("White", android.graphics.Color.WHITE),
    YELLOW("Yellow", android.graphics.Color.YELLOW),
    CYAN("Cyan", android.graphics.Color.CYAN),
    GREEN("Green", android.graphics.Color.GREEN)
}

enum class SubtitleEncodingOption(
    val label: String,
    val charsetName: String?
) {
    AUTO("Auto", null),
    UTF_8("UTF-8", "UTF-8"),
    UTF_16("UTF-16", "UTF-16"),
    WINDOWS_1252("Windows-1252", "windows-1252"),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1")
}

data class SubtitleStyleSettings(
    val fontSizeSp: Float = 20f,
    val textColor: SubtitleTextColorOption = SubtitleTextColorOption.WHITE,
    val backgroundOpacity: Float = 0.55f
)

@Suppress("unused")
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var exoPlayer: ExoPlayer? = null
    private var playerListenerAdded = false
    private val appPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val videoTitle = MutableStateFlow("")
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val speed = MutableStateFlow(1.0f)
    val errorMessage = MutableStateFlow<String?>(null)
    val playerMessage = MutableStateFlow<String?>(null)
    val showResumeDialog = MutableStateFlow(false)
    val kidsLocked = MutableStateFlow(false)

    private val _aspectMode = MutableStateFlow(loadSavedAspectMode())
    val aspectMode: StateFlow<PlayerAspectMode> = _aspectMode

    private val _hasNextVideo = MutableStateFlow(false)
    val hasNextVideo: StateFlow<Boolean> = _hasNextVideo

    private val _hasPreviousVideo = MutableStateFlow(false)
    val hasPreviousVideo: StateFlow<Boolean> = _hasPreviousVideo

    private var pendingResumePosition: Long = 0L

    data class MediaTrack(
        val id: Int,
        val name: String,
        val groupIndex: Int,
        val trackIndex: Int,
        val stableKey: String
    )

    private val _subtitleTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<MediaTrack>> = _subtitleTracks

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex

    private val _subtitlesEnabled = MutableStateFlow(loadSavedSubtitlesEnabled())
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled

    private val _subtitleStyle = MutableStateFlow(loadSavedSubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyleSettings> = _subtitleStyle

    private val _subtitleSyncOffsetMs = MutableStateFlow(loadSavedSubtitleOffsetMs())
    val subtitleSyncOffsetMs: StateFlow<Long> = _subtitleSyncOffsetMs

    private val _subtitleEncoding = MutableStateFlow(loadSavedSubtitleEncoding())
    val subtitleEncoding: StateFlow<SubtitleEncodingOption> = _subtitleEncoding

    private val _externalSubtitleName = MutableStateFlow<String?>(null)
    val externalSubtitleName: StateFlow<String?> = _externalSubtitleName

    private val _audioTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    val audioTracks: StateFlow<List<MediaTrack>> = _audioTracks

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex

    private val _audioBoostEnabled = MutableStateFlow(loadSavedAudioBoostEnabled())
    val audioBoostEnabled: StateFlow<Boolean> = _audioBoostEnabled

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var audioPreferenceAppliedForUri: String? = null

    private var positionJob: Job? = null
    private var messageJob: Job? = null
    private var subtitleReloadJob: Job? = null

    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferencesManager(application)

    private var currentVideoUri: Uri? = null
    private var initializedUri: String? = null
    private var externalSubtitleUri: Uri? = null
    private var externalSubtitleLabel: String? = null
    private var currentQueue: List<QueuedVideo> = emptyList()
    private var currentQueueIndex: Int = 0

    fun getPlayer(): ExoPlayer {
        return getOrCreatePlayer()
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val newPlayer = ExoPlayer.Builder(getApplication<Application>()).build()
        exoPlayer = newPlayer
        attachPlayerListener(newPlayer)
        return newPlayer
    }

    private fun attachPlayerListener(player: ExoPlayer) {
        if (playerListenerAdded) return
        playerListenerAdded = true

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying.value = isPlayingNow
                if (!isPlayingNow) {
                    saveResumeState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        errorMessage.value = null
                        val playerDuration = player.duration
                        duration.value = if (playerDuration != C.TIME_UNSET) playerDuration else 0L
                        updateAvailableTracks()
                        updateQueueState()
                        applyAudioBoostToCurrentSession(showFailure = false)
                    }

                    Player.STATE_ENDED -> {
                        isPlaying.value = false
                        saveResumeState()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncCurrentQueueItemFromPlayer()
                audioPreferenceAppliedForUri = null
                updateAvailableTracks()
                updateQueueState()
                applyAudioBoostToCurrentSession(showFailure = false)
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateAvailableTracks()
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage.value = error.localizedMessage ?: "Playback error"
                showMessage("Playback error")
                isPlaying.value = false
                saveResumeState()
            }
        })

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                currentAudioSessionId = audioSessionId
                applyAudioBoostToCurrentSession(showFailure = false)
            }
        })
    }

    fun initializePlayer(videoUri: Uri) {
        val requestedUriString = videoUri.toString()
        val fallbackTitle = resolveDisplayName(videoUri)
        val queue = PlayerQueueStore.getQueueFor(requestedUriString, fallbackTitle)
        val selectedIndex = queue.indexOfFirst { it.uri == requestedUriString }
            .takeIf { it >= 0 }
            ?: 0
        val selectedItem = queue.getOrNull(selectedIndex)
            ?: QueuedVideo(requestedUriString, fallbackTitle)

        currentQueue = queue
        currentQueueIndex = selectedIndex
        currentVideoUri = selectedItem.uri.toUri()
        videoTitle.value = selectedItem.title.ifBlank { fallbackTitle }

        val player = getOrCreatePlayer()

        if (initializedUri == requestedUriString && player.mediaItemCount > 0) {
            startPositionTracking()
            updateQueueState()
            return
        }

        initializedUri = requestedUriString
        externalSubtitleUri = findSidecarSubtitle(currentVideoUri ?: videoUri)
        externalSubtitleLabel = externalSubtitleUri?.let { "Sidecar SRT: ${resolveDisplayName(it)}" }
        _externalSubtitleName.value = externalSubtitleLabel
        errorMessage.value = null
        showResumeDialog.value = false
        currentPosition.value = 0L
        duration.value = 0L
        speed.value = prefs.defaultSpeed.coerceIn(0.25f, 4.0f)
        kidsLocked.value = false
        audioPreferenceAppliedForUri = null
        resetTrackState()

        val mediaItems = currentQueue.map { queuedVideo ->
            val itemUri = queuedVideo.uri.toUri()
            buildMediaItem(
                videoUri = itemUri,
                title = queuedVideo.title,
                subtitleUri = if (queuedVideo.uri == selectedItem.uri) {
                    externalSubtitleUri
                } else {
                    findSidecarSubtitle(itemUri)
                }
            )
        }

        player.apply {
            setMediaItems(mediaItems, selectedIndex, C.TIME_UNSET)
            setPlaybackSpeed(speed.value)
            prepare()
            playWhenReady = true
        }
        applySubtitleEnabledFlag(player)
        applyAudioBoostToCurrentSession(showFailure = false)
        if (shouldPrepareExternalSubtitleForPlayback()) {
            replaceCurrentMediaItemWithSubtitle()
        }

        startPositionTracking()
        updateQueueState()
        if (prefs.resumePlayback) {
            loadResumeState(selectedItem.uri)
        }

        externalSubtitleUri?.let {
            showMessage("Subtitle auto-loaded")
        }
    }

    private fun buildMediaItem(
        videoUri: Uri,
        title: String,
        subtitleUri: Uri? = externalSubtitleUri
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(videoUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifBlank { resolveDisplayName(videoUri) })
                    .build()
            )

        val mimeType = subtitleUri?.let { detectSubtitleMimeType(it) }
        if (subtitleUri != null && mimeType != null) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(mimeType)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setLabel(resolveSubtitleLabel(subtitleUri))
                .build()

            builder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        return builder.build()
    }

    private fun resolveSubtitleLabel(uri: Uri): String {
        val extension = detectSubtitleExtension(uri).uppercase()
        val name = resolveDisplayName(uri).substringBeforeLast('.').ifBlank { "Subtitle" }
        return if (extension.isNotBlank()) "$name • $extension" else name
    }

    private fun resolveDisplayName(uri: Uri): String {
        val queueTitle = currentQueue.firstOrNull { it.uri == uri.toString() }
            ?.title
            ?.takeIf { it.isNotBlank() }
        if (queueTitle != null) return queueTitle

        queryDisplayName(uri)?.let { return it }

        return Uri.decode(uri.lastPathSegment ?: "Video")
            .substringAfterLast('/')
            .ifBlank { "Video" }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun detectSubtitleMimeType(uri: Uri): String? {
        return when (detectSubtitleExtension(uri)) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            "sub", "idx" -> null
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun detectSubtitleExtension(uri: Uri): String {
        val displayName = queryDisplayName(uri)
        val source = displayName ?: uri.toString().substringBefore('?').substringAfterLast('/')
        return source.substringAfterLast('.', "").lowercase()
    }

    private fun isVobSubSubtitle(uri: Uri): Boolean {
        return detectSubtitleExtension(uri) in setOf("sub", "idx")
    }

    private fun isSrtSubtitle(uri: Uri): Boolean {
        return detectSubtitleExtension(uri) == "srt"
    }

    private fun findSidecarSubtitle(videoUri: Uri): Uri? {
        if (videoUri.scheme != "file") return null

        val path = videoUri.path ?: return null
        val videoFile = File(path)
        val folder = videoFile.parentFile ?: return null
        val baseName = videoFile.nameWithoutExtension

        val exactSrt = File(folder, "$baseName.srt")
        if (exactSrt.exists() && exactSrt.canRead()) {
            return Uri.fromFile(exactSrt)
        }

        return folder.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                        file.canRead() &&
                        file.extension.equals("srt", ignoreCase = true) &&
                        file.nameWithoutExtension.equals(baseName, ignoreCase = true)
            }
            ?.firstOrNull()
            ?.let { subtitleFile -> Uri.fromFile(subtitleFile) }
    }

    fun loadExternalSubtitle(subtitleUri: Uri) {
        if (isVobSubSubtitle(subtitleUri)) {
            showMessage("VobSub .sub/.idx is image-based and is not supported yet")
            return
        }

        val mimeType = detectSubtitleMimeType(subtitleUri)
        if (mimeType == null) {
            showMessage("Unsupported subtitle file")
            return
        }

        externalSubtitleUri = subtitleUri
        externalSubtitleLabel = "External: ${resolveDisplayName(subtitleUri)}"
        _externalSubtitleName.value = externalSubtitleLabel
        setSubtitlesEnabled(true, showToast = false)
        replaceCurrentMediaItemWithSubtitle("Subtitle loaded")
    }

    private fun shouldPrepareExternalSubtitleForPlayback(): Boolean {
        val subtitleUri = externalSubtitleUri ?: return false
        return isSrtSubtitle(subtitleUri) &&
                (_subtitleSyncOffsetMs.value != 0L || _subtitleEncoding.value != SubtitleEncodingOption.AUTO)
    }

    private fun replaceCurrentMediaItemWithSubtitle(doneMessage: String? = null) {
        val videoUri = currentVideoUri ?: return
        val player = exoPlayer ?: return
        val originalSubtitleUri = externalSubtitleUri
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying || player.playWhenReady

        subtitleReloadJob?.cancel()
        subtitleReloadJob = viewModelScope.launch {
            val playableSubtitleUri = withContext(Dispatchers.IO) {
                originalSubtitleUri?.let { prepareSubtitleForPlayback(it) }
            }

            val newItem = buildMediaItem(
                videoUri = videoUri,
                title = videoTitle.value,
                subtitleUri = playableSubtitleUri
            )

            if (currentIndex < player.mediaItemCount) {
                player.replaceMediaItem(currentIndex, newItem)
            } else {
                player.setMediaItem(newItem)
            }

            player.seekTo(
                currentIndex.coerceAtMost((player.mediaItemCount - 1).coerceAtLeast(0)),
                currentPositionMs
            )
            player.prepare()
            player.playWhenReady = wasPlaying
            applySubtitleEnabledFlag(player)
            updateAvailableTracks()

            doneMessage?.let { showMessage(it) }
        }
    }

    private fun prepareSubtitleForPlayback(originalUri: Uri): Uri {
        if (!isSrtSubtitle(originalUri)) {
            return originalUri
        }

        val offsetMs = _subtitleSyncOffsetMs.value
        val encoding = _subtitleEncoding.value

        if (offsetMs == 0L && encoding == SubtitleEncodingOption.AUTO) {
            return originalUri
        }

        val app = getApplication<Application>()
        val charset = runCatching {
            encoding.charsetName?.let { Charset.forName(it) } ?: Charsets.UTF_8
        }.getOrDefault(Charsets.UTF_8)

        val bytes = app.contentResolver.openInputStream(originalUri)?.use { input ->
            input.readBytes()
        } ?: return originalUri

        val originalText = String(bytes, charset)
        val shiftedText = if (offsetMs != 0L) {
            shiftSrtTiming(originalText, offsetMs)
        } else {
            originalText
        }

        val cacheFolder = File(app.cacheDir, "nx_subtitles").apply {
            if (!exists()) mkdirs()
        }

        val safeHash = originalUri.toString().hashCode().toString().replace("-", "m")
        val cacheFile = File(
            cacheFolder,
            "subtitle_${safeHash}_${offsetMs}_${encoding.name}.srt"
        )

        cacheFile.writeText(shiftedText, Charsets.UTF_8)
        return Uri.fromFile(cacheFile)
    }

    private fun shiftSrtTiming(srtText: String, offsetMs: Long): String {
        val timingPattern = Regex(
            """(\d{1,2}:\d{2}:\d{2},\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2},\d{3})(.*)"""
        )

        return srtText
            .lineSequence()
            .map { line ->
                val match = timingPattern.matchEntire(line)
                if (match == null) {
                    line
                } else {
                    val start = parseSrtTimeToMs(match.groupValues[1])
                    val end = parseSrtTimeToMs(match.groupValues[2])
                    if (start == null || end == null) {
                        line
                    } else {
                        val shiftedStart = (start + offsetMs).coerceAtLeast(0L)
                        val shiftedEnd = (end + offsetMs).coerceAtLeast(0L)
                        "${formatSrtTime(shiftedStart)} --> ${formatSrtTime(shiftedEnd)}${match.groupValues[3]}"
                    }
                }
            }
            .joinToString("\n")
    }

    private fun parseSrtTimeToMs(value: String): Long? {
        val mainParts = value.split(',')
        if (mainParts.size != 2) return null

        val timeParts = mainParts[0].split(':')
        if (timeParts.size != 3) return null

        return runCatching {
            val hours = timeParts[0].toLong()
            val minutes = timeParts[1].toLong()
            val seconds = timeParts[2].toLong()
            val millis = mainParts[1].toLong()
            (((hours * 60L + minutes) * 60L + seconds) * 1000L) + millis
        }.getOrNull()
    }

    private fun formatSrtTime(milliseconds: Long): String {
        val safeMs = milliseconds.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val millis = safeMs % 1000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L

        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun startPositionTracking() {
        positionJob?.cancel()

        positionJob = viewModelScope.launch {
            while (exoPlayer != null) {
                val player = exoPlayer

                if (player != null) {
                    currentPosition.value = player.currentPosition.coerceAtLeast(0L)

                    val playerDuration = player.duration
                    if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                        duration.value = playerDuration
                    }

                    updateQueueState()
                }

                delay(300)
            }
        }
    }

    fun playPause() {
        val player = exoPlayer ?: return

        if (player.isPlaying) {
            player.pause()
            saveResumeState()
        } else {
            errorMessage.value = null
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val maxDuration = duration.value.takeIf { it > 0L } ?: player.duration.takeIf {
            it != C.TIME_UNSET && it > 0L
        }

        val safePosition = if (maxDuration != null) {
            positionMs.coerceIn(0L, maxDuration)
        } else {
            positionMs.coerceAtLeast(0L)
        }

        player.seekTo(safePosition)
        currentPosition.value = safePosition
    }

    fun seekRelative(deltaMs: Long) {
        val player = exoPlayer ?: return
        val newPosition = player.currentPosition + deltaMs
        seekTo(newPosition)

        if (deltaMs > 0) {
            showMessage("+${deltaMs / 1000}s")
        } else {
            showMessage("${deltaMs / 1000}s")
        }
    }

    fun skipToNextVideo() {
        val player = exoPlayer ?: return
        if (!player.hasNextMediaItem()) {
            showMessage("No next video")
            return
        }

        saveResumeState()
        resetForQueueMove()
        player.seekToNextMediaItem()
        player.playWhenReady = true
        showMessage("Next")
    }

    fun skipToPreviousVideo() {
        val player = exoPlayer ?: return
        if (!player.hasPreviousMediaItem()) {
            showMessage("No previous video")
            return
        }

        saveResumeState()
        resetForQueueMove()
        player.seekToPreviousMediaItem()
        player.playWhenReady = true
        showMessage("Previous")
    }

    fun retryCurrent() {
        val uri = currentVideoUri ?: initializedUri?.toUri()
        if (uri == null) {
            showMessage("No video to retry")
            return
        }

        errorMessage.value = null
        initializedUri = null
        initializePlayer(uri)
    }

    fun setSpeed(newSpeed: Float) {
        val safeSpeed = newSpeed.coerceIn(0.25f, 4.0f)
        exoPlayer?.setPlaybackSpeed(safeSpeed)
        speed.value = safeSpeed
        prefs.defaultSpeed = safeSpeed
        showMessage("${formatSpeedForMessage(safeSpeed)}x")
    }

    fun cycleAspectMode() {
        val nextMode = when (_aspectMode.value) {
            PlayerAspectMode.FIT -> PlayerAspectMode.CROP
            PlayerAspectMode.CROP -> PlayerAspectMode.STRETCH
            PlayerAspectMode.STRETCH -> PlayerAspectMode.FIT
        }
        setAspectMode(nextMode)
    }

    fun setAspectMode(mode: PlayerAspectMode) {
        _aspectMode.value = mode
        appPrefs.edit().putString(KEY_ASPECT_MODE, mode.name).apply()
        showMessage("Aspect: ${mode.label}")
    }

    fun toggleKidsLock() {
        if (kidsLocked.value) {
            unlockControls()
        } else {
            lockControls()
        }
    }

    fun lockControls() {
        kidsLocked.value = true
        showMessage("Controls locked")
    }

    fun unlockControls() {
        kidsLocked.value = false
        showMessage("Controls unlocked")
    }

    fun setKidsLock(value: Boolean) {
        if (value) {
            lockControls()
        } else {
            unlockControls()
        }
    }

    fun isLocked(): Boolean = kidsLocked.value

    private fun resetForQueueMove() {
        showResumeDialog.value = false
        errorMessage.value = null
        duration.value = 0L
        currentPosition.value = 0L
        externalSubtitleUri = null
        externalSubtitleLabel = null
        _externalSubtitleName.value = null
        audioPreferenceAppliedForUri = null
        resetTrackState()
    }

    private fun resetTrackState() {
        _subtitleTracks.value = emptyList()
        _selectedSubtitleIndex.value = -1
        _audioTracks.value = emptyList()
        _selectedAudioIndex.value = -1
    }

    private fun syncCurrentQueueItemFromPlayer() {
        val player = exoPlayer ?: return
        currentQueueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val queuedVideo = currentQueue.getOrNull(currentQueueIndex)
        if (queuedVideo != null) {
            currentVideoUri = queuedVideo.uri.toUri()
            initializedUri = queuedVideo.uri
            videoTitle.value = queuedVideo.title.ifBlank { resolveDisplayName(currentVideoUri!!) }
            syncSidecarForCurrentVideo()
        } else {
            val itemUri = player.currentMediaItem?.localConfiguration?.uri
            if (itemUri != null) {
                currentVideoUri = itemUri
                initializedUri = itemUri.toString()
                videoTitle.value = resolveDisplayName(itemUri)
                syncSidecarForCurrentVideo()
            }
        }
    }

    private fun syncSidecarForCurrentVideo() {
        val sidecar = currentVideoUri?.let { findSidecarSubtitle(it) }
        externalSubtitleUri = sidecar
        externalSubtitleLabel = sidecar?.let { "Sidecar SRT: ${resolveDisplayName(it)}" }
        _externalSubtitleName.value = externalSubtitleLabel
    }

    private fun updateQueueState() {
        val player = exoPlayer
        if (player == null) {
            _hasNextVideo.value = false
            _hasPreviousVideo.value = false
            return
        }

        _hasNextVideo.value = player.hasNextMediaItem()
        _hasPreviousVideo.value = player.hasPreviousMediaItem()
    }

    private fun updateAvailableTracks() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks

        val textTracks = mutableListOf<MediaTrack>()
        val soundTracks = mutableListOf<MediaTrack>()
        var selectedTextIndex = -1
        var selectedSoundIndex = -1

        currentTracks.groups.forEachIndexed { groupIndex, group ->
            when (group.type) {
                C.TRACK_TYPE_TEXT -> {
                    for (trackIndex in 0 until group.length) {
                        if (group.isTrackSupported(trackIndex)) {
                            val format = group.getTrackFormat(trackIndex)
                            val track = MediaTrack(
                                id = textTracks.size,
                                name = buildTrackName(
                                    label = format.label,
                                    language = format.language,
                                    fallback = "Subtitle ${textTracks.size + 1}"
                                ),
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                stableKey = buildTrackStableKey(
                                    label = format.label,
                                    language = format.language,
                                    sampleMimeType = format.sampleMimeType,
                                    channelCount = format.channelCount,
                                    sampleRate = format.sampleRate,
                                    bitrate = format.bitrate,
                                    trackIndex = trackIndex
                                )
                            )

                            if (group.isTrackSelected(trackIndex)) {
                                selectedTextIndex = track.id
                            }

                            textTracks.add(track)
                        }
                    }
                }

                C.TRACK_TYPE_AUDIO -> {
                    for (trackIndex in 0 until group.length) {
                        if (group.isTrackSupported(trackIndex)) {
                            val format = group.getTrackFormat(trackIndex)
                            val channelInfo = when (format.channelCount) {
                                1 -> "Mono"
                                2 -> "Stereo"
                                C.LENGTH_UNSET -> null
                                else -> "${format.channelCount} ch"
                            }
                            val sampleRateInfo = format.sampleRate
                                .takeIf { it > 0 }
                                ?.let { "${it / 1000} kHz" }

                            val baseName = buildTrackName(
                                label = format.label,
                                language = format.language,
                                fallback = "Audio ${soundTracks.size + 1}"
                            )

                            val stableKey = buildTrackStableKey(
                                label = format.label,
                                language = format.language,
                                sampleMimeType = format.sampleMimeType,
                                channelCount = format.channelCount,
                                sampleRate = format.sampleRate,
                                bitrate = format.bitrate,
                                trackIndex = trackIndex
                            )

                            val track = MediaTrack(
                                id = soundTracks.size,
                                name = listOfNotNull(baseName, channelInfo, sampleRateInfo)
                                    .distinct()
                                    .joinToString(" • "),
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                stableKey = stableKey
                            )

                            if (group.isTrackSelected(trackIndex)) {
                                selectedSoundIndex = track.id
                            }

                            soundTracks.add(track)
                        }
                    }
                }
            }
        }

        _subtitleTracks.value = textTracks
        _selectedSubtitleIndex.value = if (_subtitlesEnabled.value) selectedTextIndex else -1
        _audioTracks.value = soundTracks

        val restoredIndex = restoreSavedAudioTrackIfNeeded(player, soundTracks)
        _selectedAudioIndex.value = restoredIndex ?: if (isCurrentAudioPreferenceAuto()) {
            -1
        } else {
            selectedSoundIndex
        }
    }

    private fun buildTrackName(
        label: String?,
        language: String?,
        fallback: String
    ): String {
        val cleanLabel = label?.trim()?.takeIf { it.isNotBlank() }
        val cleanLanguage = language
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() && it != "UND" }

        return cleanLabel ?: cleanLanguage ?: fallback
    }

    private fun buildTrackStableKey(
        label: String?,
        language: String?,
        sampleMimeType: String?,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        trackIndex: Int
    ): String {
        return listOf(
            label?.trim().orEmpty(),
            language?.trim().orEmpty(),
            sampleMimeType.orEmpty(),
            channelCount.toString(),
            sampleRate.toString(),
            bitrate.toString(),
            trackIndex.toString()
        ).joinToString("|")
    }

    private fun restoreSavedAudioTrackIfNeeded(
        player: ExoPlayer,
        tracks: List<MediaTrack>
    ): Int? {
        val uriKey = currentVideoUri?.toString() ?: return null
        if (audioPreferenceAppliedForUri == uriKey) return null

        audioPreferenceAppliedForUri = uriKey
        val savedKey = appPrefs.getString(audioPreferenceKeyFor(uriKey), null)

        if (savedKey == null || savedKey == AUDIO_TRACK_AUTO) {
            clearAudioTrackOverride(player)
            return null
        }

        val savedIndex = tracks.indexOfFirst { it.stableKey == savedKey }
        if (savedIndex < 0) {
            clearAudioTrackOverride(player)
            return null
        }

        applyAudioTrackOverride(player, tracks[savedIndex])
        return savedIndex
    }

    private fun clearAudioTrackOverride(player: ExoPlayer) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
    }

    private fun applyAudioTrackOverride(player: ExoPlayer, track: MediaTrack) {
        val trackGroup = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        val override = TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(track.trackIndex)
        )

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
    }

    private fun isCurrentAudioPreferenceAuto(): Boolean {
        val uriKey = currentVideoUri?.toString() ?: return true
        val savedKey = appPrefs.getString(audioPreferenceKeyFor(uriKey), null)
        return savedKey == null || savedKey == AUDIO_TRACK_AUTO
    }

    private fun audioPreferenceKeyFor(uri: String): String {
        return "$KEY_AUDIO_TRACK_PREFIX${uri.hashCode()}"
    }

    fun setSubtitlesEnabled(enabled: Boolean, showToast: Boolean = true) {
        _subtitlesEnabled.value = enabled
        appPrefs.edit().putBoolean(KEY_SUBTITLES_ENABLED, enabled).apply()
        applySubtitleEnabledFlag(exoPlayer)

        if (enabled) {
            updateAvailableTracks()
            if (showToast) showMessage("Subtitles on")
        } else {
            _selectedSubtitleIndex.value = -1
            if (showToast) showMessage("Subtitles off")
        }
    }

    private fun applySubtitleEnabledFlag(player: Player?) {
        val activePlayer = player ?: return
        activePlayer.trackSelectionParameters =
            activePlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_subtitlesEnabled.value)
                .build()
    }

    fun selectSubtitleTrack(index: Int) {
        val player = exoPlayer ?: return

        if (index < 0) {
            setSubtitlesEnabled(false)
            return
        }

        val selectedTrack = _subtitleTracks.value.getOrNull(index) ?: return
        val trackGroup = player.currentTracks.groups.getOrNull(selectedTrack.groupIndex) ?: return

        val override = TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(selectedTrack.trackIndex)
        )

        _subtitlesEnabled.value = true
        appPrefs.edit().putBoolean(KEY_SUBTITLES_ENABLED, true).apply()

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .addOverride(override)
                .build()

        _selectedSubtitleIndex.value = index
        showMessage("Subtitle: ${selectedTrack.name}")
    }

    fun adjustSubtitleSyncOffset(deltaMs: Long) {
        val newOffset = (_subtitleSyncOffsetMs.value + deltaMs)
            .coerceIn(MIN_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)

        _subtitleSyncOffsetMs.value = newOffset
        appPrefs.edit().putLong(KEY_SUBTITLE_OFFSET_MS, newOffset).apply()

        if (externalSubtitleUri != null && externalSubtitleUri?.let { isSrtSubtitle(it) } == true) {
            replaceCurrentMediaItemWithSubtitle("Subtitle offset ${formatOffsetForMessage(newOffset)}")
        } else {
            showMessage("Offset saved for external SRT")
        }
    }

    fun setSubtitleEncoding(encoding: SubtitleEncodingOption) {
        _subtitleEncoding.value = encoding
        appPrefs.edit().putString(KEY_SUBTITLE_ENCODING, encoding.name).apply()

        if (externalSubtitleUri != null && externalSubtitleUri?.let { isSrtSubtitle(it) } == true) {
            replaceCurrentMediaItemWithSubtitle("Encoding: ${encoding.label}")
        } else {
            showMessage("Encoding applies to external SRT")
        }
    }

    fun setSubtitleFontSize(fontSizeSp: Float) {
        val safeSize = fontSizeSp.coerceIn(14f, 34f)
        val current = _subtitleStyle.value
        saveSubtitleStyle(current.copy(fontSizeSp = safeSize))
    }

    fun setSubtitleColor(color: SubtitleTextColorOption) {
        val current = _subtitleStyle.value
        saveSubtitleStyle(current.copy(textColor = color))
    }

    fun setSubtitleBackgroundOpacity(opacity: Float) {
        val safeOpacity = opacity.coerceIn(0f, 1f)
        val current = _subtitleStyle.value
        saveSubtitleStyle(current.copy(backgroundOpacity = safeOpacity))
    }

    fun resetSubtitleStyle() {
        saveSubtitleStyle(SubtitleStyleSettings())
        showMessage("Subtitle style reset")
    }

    private fun saveSubtitleStyle(settings: SubtitleStyleSettings) {
        _subtitleStyle.value = settings
        appPrefs.edit()
            .putFloat(KEY_SUBTITLE_FONT_SIZE, settings.fontSizeSp)
            .putString(KEY_SUBTITLE_COLOR, settings.textColor.name)
            .putFloat(KEY_SUBTITLE_BG_OPACITY, settings.backgroundOpacity)
            .apply()
    }

    fun selectAudioTrack(index: Int) {
        val player = exoPlayer ?: return
        val uriKey = currentVideoUri?.toString()

        if (index < 0) {
            clearAudioTrackOverride(player)
            uriKey?.let {
                appPrefs.edit()
                    .putString(audioPreferenceKeyFor(it), AUDIO_TRACK_AUTO)
                    .apply()
            }
            audioPreferenceAppliedForUri = uriKey
            _selectedAudioIndex.value = -1
            showMessage("Audio: Auto")
            return
        }

        val selectedTrack = _audioTracks.value.getOrNull(index) ?: return
        applyAudioTrackOverride(player, selectedTrack)

        uriKey?.let {
            appPrefs.edit()
                .putString(audioPreferenceKeyFor(it), selectedTrack.stableKey)
                .apply()
        }
        audioPreferenceAppliedForUri = uriKey
        _selectedAudioIndex.value = index
        showMessage("Audio: ${selectedTrack.name}")
    }

    fun setAudioBoostEnabled(enabled: Boolean) {
        _audioBoostEnabled.value = enabled
        appPrefs.edit().putBoolean(KEY_AUDIO_BOOST_ENABLED, enabled).apply()
        val applied = applyAudioBoostToCurrentSession(showFailure = enabled)

        when {
            !enabled -> showMessage("Audio boost off")
            applied -> showMessage("Audio boost on")
        }
    }

    fun toggleAudioBoost() {
        setAudioBoostEnabled(!_audioBoostEnabled.value)
    }

    private fun applyAudioBoostToCurrentSession(showFailure: Boolean): Boolean {
        val player = exoPlayer ?: return false
        releaseAudioBoostEffect()

        if (!_audioBoostEnabled.value) {
            player.volume = 1f
            return true
        }

        val sessionId = currentAudioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            if (showFailure) showMessage("Audio boost will apply after playback starts")
            return false
        }

        val result = runCatching {
            LoudnessEnhancer(sessionId).apply {
                setTargetGain(AUDIO_BOOST_GAIN_MB)
                enabled = true
            }
        }

        result.onSuccess { enhancer ->
            loudnessEnhancer = enhancer
        }.onFailure {
            if (showFailure) showMessage("Audio boost is not supported on this device")
        }

        return result.isSuccess
    }

    private fun releaseAudioBoostEffect() {
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
    }

    fun createSystemEqualizerIntent(): Intent? {
        if (exoPlayer == null) return null
        val sessionId = currentAudioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return null

        return Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getApplication<Application>().packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
        }
    }

    private fun loadResumeState(uri: String) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                db.resumeDao().getResumeState(uri).firstOrNull()
            }

            state?.let {
                if (it.positionMs > 5000L) {
                    pendingResumePosition = it.positionMs
                    showResumeDialog.value = true
                    exoPlayer?.pause()
                }
            }
        }
    }

    fun saveResumeState() {
        val uri = currentVideoUri?.toString() ?: return
        val pos = exoPlayer?.currentPosition ?: currentPosition.value
        val maxDuration = duration.value

        if (pos <= 1000L) return

        val safePosition = if (maxDuration > 0L && pos >= maxDuration - 3000L) {
            0L
        } else {
            pos
        }

        viewModelScope.launch(Dispatchers.IO) {
            db.resumeDao().saveResumeState(
                ResumeState(
                    videoUri = uri,
                    positionMs = safePosition
                )
            )
        }
    }

    fun saveCurrentPosition() {
        saveResumeState()
    }

    fun pauseAndSaveForExit() {
        exoPlayer?.pause()
        saveResumeState()
    }

    fun showMessage(message: String) {
        messageJob?.cancel()
        playerMessage.value = message

        messageJob = viewModelScope.launch {
            delay(900)
            playerMessage.value = null
        }
    }

    fun releasePlayer() {
        saveResumeState()

        positionJob?.cancel()
        positionJob = null

        messageJob?.cancel()
        messageJob = null

        subtitleReloadJob?.cancel()
        subtitleReloadJob = null

        releaseAudioBoostEffect()

        exoPlayer?.release()
        exoPlayer = null

        playerListenerAdded = false
        initializedUri = null
        audioPreferenceAppliedForUri = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        isPlaying.value = false
        duration.value = 0L
        currentPosition.value = 0L
        _hasNextVideo.value = false
        _hasPreviousVideo.value = false
        _externalSubtitleName.value = null
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }

    fun resumePlayback() {
        exoPlayer?.seekTo(pendingResumePosition)
        exoPlayer?.playWhenReady = true
        exoPlayer?.play()
        showResumeDialog.value = false
    }

    fun startFromBeginning() {
        exoPlayer?.seekTo(0)
        exoPlayer?.playWhenReady = true
        exoPlayer?.play()
        showResumeDialog.value = false
    }

    private fun loadSavedAspectMode(): PlayerAspectMode {
        val savedValue = appPrefs.getString(KEY_ASPECT_MODE, PlayerAspectMode.FIT.name)
        return runCatching {
            PlayerAspectMode.valueOf(savedValue ?: PlayerAspectMode.FIT.name)
        }.getOrDefault(PlayerAspectMode.FIT)
    }

    private fun loadSavedSubtitlesEnabled(): Boolean {
        return appPrefs.getBoolean(KEY_SUBTITLES_ENABLED, true)
    }

    private fun loadSavedSubtitleStyle(): SubtitleStyleSettings {
        val color = runCatching {
            SubtitleTextColorOption.valueOf(
                appPrefs.getString(KEY_SUBTITLE_COLOR, SubtitleTextColorOption.WHITE.name)
                    ?: SubtitleTextColorOption.WHITE.name
            )
        }.getOrDefault(SubtitleTextColorOption.WHITE)

        return SubtitleStyleSettings(
            fontSizeSp = appPrefs.getFloat(KEY_SUBTITLE_FONT_SIZE, 20f).coerceIn(14f, 34f),
            textColor = color,
            backgroundOpacity = appPrefs.getFloat(KEY_SUBTITLE_BG_OPACITY, 0.55f).coerceIn(0f, 1f)
        )
    }

    private fun loadSavedSubtitleOffsetMs(): Long {
        return appPrefs.getLong(KEY_SUBTITLE_OFFSET_MS, 0L)
            .coerceIn(MIN_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
    }

    private fun loadSavedSubtitleEncoding(): SubtitleEncodingOption {
        return runCatching {
            SubtitleEncodingOption.valueOf(
                appPrefs.getString(KEY_SUBTITLE_ENCODING, SubtitleEncodingOption.AUTO.name)
                    ?: SubtitleEncodingOption.AUTO.name
            )
        }.getOrDefault(SubtitleEncodingOption.AUTO)
    }

    private fun loadSavedAudioBoostEnabled(): Boolean {
        return appPrefs.getBoolean(KEY_AUDIO_BOOST_ENABLED, false)
    }

    private fun formatOffsetForMessage(offsetMs: Long): String {
        if (offsetMs == 0L) return "0.0s"

        val sign = if (offsetMs > 0L) "+" else "-"
        val seconds = kotlin.math.abs(offsetMs) / 1000f
        return "$sign${"%.1f".format(seconds)}s"
    }

    private fun formatSpeedForMessage(value: Float): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    }

    companion object {
        private const val PREFS_NAME = "nxplayer_prefs"
        private const val KEY_ASPECT_MODE = "player_aspect_mode"
        private const val KEY_SUBTITLES_ENABLED = "player_subtitles_enabled"
        private const val KEY_SUBTITLE_FONT_SIZE = "player_subtitle_font_size"
        private const val KEY_SUBTITLE_COLOR = "player_subtitle_color"
        private const val KEY_SUBTITLE_BG_OPACITY = "player_subtitle_background_opacity"
        private const val KEY_SUBTITLE_OFFSET_MS = "player_subtitle_offset_ms"
        private const val KEY_SUBTITLE_ENCODING = "player_subtitle_encoding"
        private const val KEY_AUDIO_TRACK_PREFIX = "player_audio_track_"
        private const val KEY_AUDIO_BOOST_ENABLED = "player_audio_boost_enabled"
        private const val AUDIO_TRACK_AUTO = "AUTO"
        private const val AUDIO_BOOST_GAIN_MB = 800
        private const val MIN_SUBTITLE_OFFSET_MS = -60_000L
        private const val MAX_SUBTITLE_OFFSET_MS = 60_000L
    }
}
