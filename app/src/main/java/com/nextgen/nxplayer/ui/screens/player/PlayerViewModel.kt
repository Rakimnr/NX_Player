package com.nextgen.nxplayer.ui.screens.player

import android.app.Application
import android.content.Context
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
import com.nextgen.nxplayer.data.local.AppDatabase
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.data.model.ResumeState
import java.io.File
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
        val trackIndex: Int
    )

    private val _subtitleTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<MediaTrack>> = _subtitleTracks

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex

    private val _audioTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    val audioTracks: StateFlow<List<MediaTrack>> = _audioTracks

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex

    private var positionJob: Job? = null
    private var messageJob: Job? = null

    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferencesManager(application)

    private var currentVideoUri: Uri? = null
    private var initializedUri: String? = null
    private var externalSubtitleUri: Uri? = null
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
                    }

                    Player.STATE_ENDED -> {
                        isPlaying.value = false
                        saveResumeState()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncCurrentQueueItemFromPlayer()
                updateAvailableTracks()
                updateQueueState()
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
        errorMessage.value = null
        showResumeDialog.value = false
        currentPosition.value = 0L
        duration.value = 0L
        speed.value = prefs.defaultSpeed.coerceIn(0.25f, 4.0f)
        kidsLocked.value = false
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

        startPositionTracking()
        updateQueueState()
        loadResumeState(selectedItem.uri)

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

        if (subtitleUri != null) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(detectSubtitleMimeType(subtitleUri))
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            builder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        return builder.build()
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

    private fun detectSubtitleMimeType(uri: Uri): String {
        val text = uri.toString().substringBefore('?').lowercase()

        return when {
            text.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            text.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            text.endsWith(".ass") || text.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            text.endsWith(".ttml") || text.endsWith(".dfxp") || text.endsWith(".xml") -> {
                MimeTypes.APPLICATION_TTML
            }
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun findSidecarSubtitle(videoUri: Uri): Uri? {
        if (videoUri.scheme != "file") return null

        val path = videoUri.path ?: return null
        val videoFile = File(path)
        val folder = videoFile.parentFile ?: return null
        val baseName = videoFile.nameWithoutExtension

        val supportedExtensions = listOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp", "xml")

        return supportedExtensions
            .asSequence()
            .map { extension -> File(folder, "$baseName.$extension") }
            .firstOrNull { subtitleFile -> subtitleFile.exists() && subtitleFile.canRead() }
            ?.let { subtitleFile -> Uri.fromFile(subtitleFile) }
    }

    fun loadExternalSubtitle(subtitleUri: Uri) {
        val videoUri = currentVideoUri ?: return
        val player = exoPlayer ?: return

        val wasPlaying = player.isPlaying
        val current = player.currentPosition
        val mediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0)

        externalSubtitleUri = subtitleUri

        val newItem = buildMediaItem(
            videoUri = videoUri,
            title = videoTitle.value,
            subtitleUri = subtitleUri
        )

        if (mediaItemIndex < player.mediaItemCount) {
            player.replaceMediaItem(mediaItemIndex, newItem)
        } else {
            player.setMediaItem(newItem)
        }

        player.seekTo(mediaItemIndex.coerceAtMost((player.mediaItemCount - 1).coerceAtLeast(0)), current)
        player.prepare()
        player.playWhenReady = wasPlaying

        showMessage("Subtitle loaded")
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
        } else {
            val itemUri = player.currentMediaItem?.localConfiguration?.uri
            if (itemUri != null) {
                currentVideoUri = itemUri
                initializedUri = itemUri.toString()
                videoTitle.value = resolveDisplayName(itemUri)
            }
        }
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
                                trackIndex = trackIndex
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

                            val name = buildTrackName(
                                label = format.label,
                                language = format.language,
                                fallback = "Audio ${soundTracks.size + 1}"
                            )

                            val track = MediaTrack(
                                id = soundTracks.size,
                                name = listOfNotNull(name, channelInfo).joinToString(" • "),
                                groupIndex = groupIndex,
                                trackIndex = trackIndex
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
        _selectedSubtitleIndex.value = selectedTextIndex
        _audioTracks.value = soundTracks
        _selectedAudioIndex.value = selectedSoundIndex
    }

    private fun buildTrackName(
        label: String?,
        language: String?,
        fallback: String
    ): String {
        val cleanLabel = label?.takeIf { it.isNotBlank() }
        val cleanLanguage = language
            ?.uppercase()
            ?.takeIf { it.isNotBlank() && it != "UND" }

        return cleanLabel ?: cleanLanguage ?: fallback
    }

    fun selectSubtitleTrack(index: Int) {
        val player = exoPlayer ?: return

        if (index < 0) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()

            _selectedSubtitleIndex.value = -1
            showMessage("Subtitles off")
            return
        }

        val selectedTrack = _subtitleTracks.value.getOrNull(index) ?: return
        val trackGroup = player.currentTracks.groups.getOrNull(selectedTrack.groupIndex) ?: return

        val override = TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(selectedTrack.trackIndex)
        )

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

    fun selectAudioTrack(index: Int) {
        val player = exoPlayer ?: return

        if (index < 0) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()

            _selectedAudioIndex.value = -1
            showMessage("Audio: Auto")
            return
        }

        val selectedTrack = _audioTracks.value.getOrNull(index) ?: return
        val trackGroup = player.currentTracks.groups.getOrNull(selectedTrack.groupIndex) ?: return

        val override = TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(selectedTrack.trackIndex)
        )

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()

        _selectedAudioIndex.value = index
        showMessage("Audio: ${selectedTrack.name}")
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

        exoPlayer?.release()
        exoPlayer = null

        playerListenerAdded = false
        initializedUri = null
        isPlaying.value = false
        duration.value = 0L
        currentPosition.value = 0L
        _hasNextVideo.value = false
        _hasPreviousVideo.value = false
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
    }
}
