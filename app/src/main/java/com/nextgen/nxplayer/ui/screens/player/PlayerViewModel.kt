package com.nextgen.nxplayer.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import com.nextgen.nxplayer.data.local.AppDatabase
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.data.model.Bookmark
import com.nextgen.nxplayer.data.model.ResumeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var exoPlayer: ExoPlayer? = null
    private var playerListenerAdded = false

    val videoTitle = MutableStateFlow("")
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val speed = MutableStateFlow(1.0f)
    val kidsLocked = MutableStateFlow(false)
    val abRepeatActive = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val playerMessage = MutableStateFlow<String?>(null)
    val showResumeDialog = MutableStateFlow(false)
    var pendingResumePosition: Long = 0L

    data class SubtitleTrack(
        val id: Int,
        val name: String,
        val groupIndex: Int,
        val trackIndex: Int
    )

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private var repeatA: Long? = null
    private var repeatB: Long? = null

    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var messageJob: Job? = null
    private var bookmarkJob: Job? = null

    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferencesManager(application)

    private var currentVideoUri: Uri? = null
    private var initializedUri: String? = null
    private var externalSubtitleUri: Uri? = null

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
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val playerDuration = player.duration
                        duration.value =
                            if (playerDuration != C.TIME_UNSET) playerDuration else 0L

                        updateSubtitleTracks()
                    }

                    Player.STATE_ENDED -> {
                        isPlaying.value = false
                        saveResumeState()
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                updateSubtitleTracks()
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage.value = error.localizedMessage ?: "Playback error"
                showMessage("Playback error")
                isPlaying.value = false
            }
        })
    }

    fun initializePlayer(videoUri: Uri) {
        currentVideoUri = videoUri
        videoTitle.value = videoUri.lastPathSegment ?: "Video"

        val player = getOrCreatePlayer()
        val uriString = videoUri.toString()

        if (initializedUri == uriString) {
            startPositionTracking()
            loadBookmarks(uriString)
            return
        }

        initializedUri = uriString
        errorMessage.value = null
        speed.value = prefs.defaultSpeed

        val mediaItem = buildMediaItem(videoUri)

        player.apply {
            setMediaItem(mediaItem)
            setPlaybackSpeed(prefs.defaultSpeed)
            prepare()
            playWhenReady = false
        }

        startPositionTracking()
        loadResumeState(uriString)
        loadBookmarks(uriString)
    }

    private fun buildMediaItem(videoUri: Uri): MediaItem {
        val subtitleUri = externalSubtitleUri

        return if (subtitleUri == null) {
            MediaItem.fromUri(videoUri)
        } else {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(detectSubtitleMimeType(subtitleUri))
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            MediaItem.Builder()
                .setUri(videoUri)
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()
        }
    }

    private fun detectSubtitleMimeType(uri: Uri): String {
        val text = uri.toString().lowercase()

        return when {
            text.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            text.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            text.endsWith(".ass") || text.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun loadExternalSubtitle(subtitleUri: Uri) {
        val videoUri = currentVideoUri ?: return
        val player = exoPlayer ?: return

        val wasPlaying = player.isPlaying
        val current = player.currentPosition

        externalSubtitleUri = subtitleUri

        player.setMediaItem(buildMediaItem(videoUri), current)
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
                    currentPosition.value = player.currentPosition

                    val current = player.currentPosition
                    val end = repeatB

                    if (abRepeatActive.value && end != null && current >= end) {
                        repeatA?.let { player.seekTo(it) }
                    }
                }

                delay(300)
            }
        }
    }

    fun playPause() {
        val player = exoPlayer ?: return

        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val safePosition = positionMs.coerceAtLeast(0L)
        player.seekTo(safePosition)
        currentPosition.value = safePosition
    }

    fun seekRelative(deltaMs: Long) {
        val player = exoPlayer ?: return
        val newPosition = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        seekTo(newPosition)

        if (deltaMs > 0) {
            showMessage("+${deltaMs / 1000}s")
        } else {
            showMessage("${deltaMs / 1000}s")
        }
    }

    fun setSpeed(newSpeed: Float) {
        val safeSpeed = newSpeed.coerceIn(0.25f, 4.0f)
        exoPlayer?.setPlaybackSpeed(safeSpeed)
        speed.value = safeSpeed
        prefs.defaultSpeed = safeSpeed
        showMessage("${safeSpeed}x")
    }

    fun toggleKidsLock() {
        kidsLocked.value = !kidsLocked.value

        if (kidsLocked.value) {
            showMessage("Controls locked")
        } else {
            showMessage("Controls unlocked")
        }
    }

    fun toggleAbRepeat() {
        if (abRepeatActive.value) {
            clearRepeat()
            return
        }

        if (repeatA == null) {
            setRepeatA()
        } else {
            setRepeatB()
        }
    }

    fun setRepeatA() {
        repeatA = exoPlayer?.currentPosition
        repeatB = null
        abRepeatActive.value = false
        showMessage("A point set")
    }

    fun setRepeatB() {
        repeatB = exoPlayer?.currentPosition

        val a = repeatA
        val b = repeatB

        if (a == null || b == null) {
            showMessage("Set A first")
            return
        }

        if (b <= a + 1000L) {
            showMessage("B must be after A")
            repeatB = null
            return
        }

        abRepeatActive.value = true
        showMessage("A-B repeat active")
    }

    fun clearRepeat() {
        repeatA = null
        repeatB = null
        abRepeatActive.value = false
        showMessage("A-B repeat off")
    }

    private fun updateSubtitleTracks() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks

        val tracks = mutableListOf<SubtitleTrack>()
        var selectedIndex = -1

        currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)

                        val language = format.language
                            ?.uppercase()
                            ?.takeIf { it.isNotBlank() }

                        val label = format.label
                            ?.takeIf { it.isNotBlank() }

                        val name = label ?: language ?: "Subtitle ${tracks.size + 1}"

                        val subtitleTrack = SubtitleTrack(
                            id = tracks.size,
                            name = name,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex
                        )

                        if (group.isTrackSelected(trackIndex)) {
                            selectedIndex = subtitleTrack.id
                        }

                        tracks.add(subtitleTrack)
                    }
                }
            }
        }

        _subtitleTracks.value = tracks
        _selectedSubtitleIndex.value = selectedIndex
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

    private fun loadResumeState(uri: String) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                db.resumeDao().getResumeState(uri).firstOrNull()
            }

            state?.let {
                if (it.positionMs > 5000L) {
                    pendingResumePosition = it.positionMs
                    showResumeDialog.value = true
                }
            }
        }
    }

    fun saveResumeState() {
        val uri = currentVideoUri?.toString() ?: return
        val pos = exoPlayer?.currentPosition ?: 0L

        viewModelScope.launch(Dispatchers.IO) {
            db.resumeDao().saveResumeState(
                ResumeState(
                    videoUri = uri,
                    positionMs = pos
                )
            )
        }
    }

    private fun loadBookmarks(videoUri: String) {
        bookmarkJob?.cancel()

        bookmarkJob = viewModelScope.launch {
            db.bookmarkDao()
                .getBookmarksForVideo(videoUri)
                .collect { savedBookmarks ->
                    _bookmarks.value = savedBookmarks
                }
        }
    }

    fun addBookmark(note: String = "") {
        val uri = currentVideoUri?.toString() ?: return
        val pos = exoPlayer?.currentPosition ?: return

        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().insert(
                Bookmark(
                    videoUri = uri,
                    title = formatBookmarkTitle(pos),
                    positionMs = pos,
                    note = note
                )
            )
        }

        showMessage("Bookmark added")
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().delete(bookmark)
        }

        showMessage("Bookmark deleted")
    }

    fun seekToBookmark(bookmark: Bookmark) {
        seekTo(bookmark.positionMs)
        showMessage(bookmark.title)
    }

    private fun formatBookmarkTitle(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val time = if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }

        return "Bookmark $time"
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()

        if (minutes <= 0) {
            showMessage("Sleep timer off")
            return
        }

        showMessage("Sleep timer: $minutes min")

        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            exoPlayer?.pause()
            showMessage("Sleep timer finished")
        }
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

        sleepTimerJob?.cancel()
        sleepTimerJob = null

        messageJob?.cancel()
        messageJob = null

        bookmarkJob?.cancel()
        bookmarkJob = null

        exoPlayer?.release()
        exoPlayer = null

        playerListenerAdded = false
        initializedUri = null
        isPlaying.value = false
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
}