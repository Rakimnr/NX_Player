package com.nextgen.nxplayer.ui.screens.player

import android.app.Application
import android.net.Uri
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextgen.nxplayer.data.local.AppDatabase
import com.nextgen.nxplayer.data.local.PreferencesManager
import com.nextgen.nxplayer.data.model.Bookmark
import com.nextgen.nxplayer.data.model.ResumeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.io.FileOutputStream

@Suppress("unused")
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceAttached = false   // track attachment

    val videoTitle = MutableStateFlow("")
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val speed = MutableStateFlow(1.0f)
    val kidsLocked = MutableStateFlow(false)
    val abRepeatActive = MutableStateFlow(false)

    data class SubtitleTrack(val id: Int, val name: String)
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks
    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex

    private var repeatA: Long? = null
    private var repeatB: Long? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferencesManager(application)
    private var currentVideoUri: Uri? = null

    fun initializePlayer(videoUri: Uri) {
        currentVideoUri = videoUri
        val context = getApplication<Application>()

        libVlc = LibVLC(context)
        mediaPlayer = MediaPlayer(libVlc!!)

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying.value = true
                    duration.value = mediaPlayer?.length ?: 0L
                    videoTitle.value = videoUri.lastPathSegment ?: "Video"
                    updateSubtitleTracks()
                }
                MediaPlayer.Event.Paused -> isPlaying.value = false
                MediaPlayer.Event.Stopped -> isPlaying.value = false
                MediaPlayer.Event.EndReached -> {
                    isPlaying.value = false
                    saveResumeState()
                }
                MediaPlayer.Event.TimeChanged -> {
                    currentPosition.value = mediaPlayer?.time ?: 0L
                    val pos = currentPosition.value
                    if (abRepeatActive.value && repeatB != null && pos >= repeatB!!) {
                        repeatA?.let { seekTo(it) }
                    }
                }
            }
        }

        mediaPlayer?.rate = prefs.defaultSpeed
        speed.value = prefs.defaultSpeed

        loadResumeState(videoUri.toString())

        viewModelScope.launch {
            while (true) {
                if (isPlaying.value) {
                    currentPosition.value = mediaPlayer?.time ?: 0L
                }
                delay(250)
            }
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        val mp = mediaPlayer ?: return
        // Detach if already attached (prevents crash on re-attach)
        if (surfaceAttached) {
            mp.vlcVout.detachViews()
            surfaceAttached = false
        }
        mp.vlcVout.setVideoView(surfaceView)
        mp.vlcVout.attachViews()
        surfaceAttached = true

        // Resolve content URI if necessary
        val uri = currentVideoUri ?: return
        val path = resolveUri(uri) ?: return
        val media = Media(libVlc!!, path)
        mp.media = media
        media.release()
        mp.play()
    }

    /** Convert content:// URI to file path (VLC prefers file paths on some devices) */
    private fun resolveUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        // For content URIs, copy to a temp file
        try {
            val context = getApplication<Application>()
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "vlc_temp_video.mp4")
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
            input.close()
            return tempFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("NXPlayer", "Failed to resolve URI: ${e.message}")
            // Fallback: try the original URI as string
            return uri.toString()
        }
    }

    private fun updateSubtitleTracks() {
        val mp = mediaPlayer ?: return
        val descriptions = mp.spuTracks
        if (descriptions != null && descriptions.isNotEmpty()) {
            val tracks = descriptions.mapIndexed { index, desc ->
                SubtitleTrack(id = desc.id, name = desc.name ?: "Track ${index + 1}")
            }
            _subtitleTracks.value = tracks
        } else {
            _subtitleTracks.value = emptyList()
        }
    }

    fun selectSubtitleTrack(index: Int) {
        val mp = mediaPlayer ?: return
        val tracks = _subtitleTracks.value
        if (index < 0 || index >= tracks.size) {
            mp.spuTrack = -1
            _selectedSubtitleIndex.value = -1
        } else {
            mp.spuTrack = tracks[index].id
            _selectedSubtitleIndex.value = index
        }
    }

    private fun loadResumeState(videoUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = db.resumeDao().getResumeState(videoUri).firstOrNull()
            state?.let { seekTo(it.positionMs) }
        }
    }

    fun saveResumeState() {
        val uri = currentVideoUri?.toString() ?: return
        val pos = mediaPlayer?.time ?: 0
        viewModelScope.launch(Dispatchers.IO) {
            db.resumeDao().saveResumeState(ResumeState(uri, pos))
        }
    }

    fun playPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) mp.pause() else mp.play()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    fun seekRelative(deltaMs: Long) {
        val current = mediaPlayer?.time ?: 0
        seekTo(current + deltaMs)
    }

    fun setSpeed(newSpeed: Float) {
        mediaPlayer?.rate = newSpeed
        speed.value = newSpeed
        prefs.defaultSpeed = newSpeed
    }

    fun toggleKidsLock() {
        kidsLocked.value = !kidsLocked.value
    }

    fun setRepeatA() {
        repeatA = mediaPlayer?.time
        if (repeatA != null && repeatB != null) abRepeatActive.value = true
    }
    fun setRepeatB() {
        repeatB = mediaPlayer?.time
        if (repeatA != null && repeatB != null) abRepeatActive.value = true
    }
    fun clearRepeat() {
        repeatA = null; repeatB = null; abRepeatActive.value = false
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            mediaPlayer?.pause()
        }
    }

    fun addBookmark(note: String = "") {
        val uri = currentVideoUri?.toString() ?: return
        val pos = mediaPlayer?.time ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().insert(
                Bookmark(videoUri = uri, title = "Bookmark", positionMs = pos, note = note)
            )
        }
    }

    fun releasePlayer() {
        saveResumeState()
        if (surfaceAttached) {
            mediaPlayer?.vlcVout?.detachViews()
            surfaceAttached = false
        }
        mediaPlayer?.stop()
        mediaPlayer?.release()
        libVlc?.release()
        mediaPlayer = null
        libVlc = null
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}