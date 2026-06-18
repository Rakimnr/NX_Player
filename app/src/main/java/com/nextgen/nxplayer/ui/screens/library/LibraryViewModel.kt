package com.nextgen.nxplayer.ui.screens.library

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextgen.nxplayer.data.model.VideoItem
import com.nextgen.nxplayer.data.repository.VideoRepository
import com.nextgen.nxplayer.data.repository.VideoSortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.contentResolver)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _allVideos = MutableStateFlow<List<VideoItem>>(emptyList())

    private val _permissionGranted = MutableStateFlow(hasVideoPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _sortType = MutableStateFlow(loadSavedSortType())
    val sortType: StateFlow<VideoSortType> = _sortType

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val totalVideoCount: StateFlow<Int> = _allVideos
        .map { videos -> videos.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val videos: StateFlow<List<VideoItem>> = combine(
        _allVideos,
        _searchQuery
    ) { videos, query ->
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            videos
        } else {
            videos.filter { video ->
                video.name.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        if (_permissionGranted.value) {
            loadVideos()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted

        if (granted) {
            loadVideos()
        } else {
            _allVideos.value = emptyList()
            _errorMessage.value = "Video permission was denied. Grant video access to show your full library."
        }
    }

    fun loadVideos() {
        if (!hasVideoPermission()) {
            _permissionGranted.value = false
            _allVideos.value = emptyList()
            _isLoading.value = false
            return
        }

        _permissionGranted.value = true
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _allVideos.value = repository.getVideos(_sortType.value)
            } catch (e: SecurityException) {
                _permissionGranted.value = false
                _allVideos.value = emptyList()
                _errorMessage.value = "Video permission is missing or was revoked."
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not load videos."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshVideos() {
        loadVideos()
    }

    fun changeSortType(newSortType: VideoSortType) {
        if (_sortType.value == newSortType) return

        _sortType.value = newSortType
        prefs.edit().putString(KEY_SORT_TYPE, newSortType.name).apply()
        loadVideos()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteVideo(video: VideoItem) {
        if (!hasVideoPermission()) {
            _permissionGranted.value = false
            _errorMessage.value = "Video permission is required before deleting files."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deletedRows = getApplication<Application>()
                    .contentResolver
                    .delete(video.uri, null, null)

                if (deletedRows > 0) {
                    _allVideos.value = repository.getVideos(_sortType.value)
                } else {
                    _errorMessage.value = "Could not delete this video. It may already be removed."
                }
            } catch (e: SecurityException) {
                _errorMessage.value = "Android blocked direct delete access for this video. Try again using the system delete confirmation."
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not delete this video."
            }
        }
    }

    fun onSystemDeleteCompleted(success: Boolean) {
        if (success) {
            loadVideos()
        }
    }

    fun showError(message: String) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun loadSavedSortType(): VideoSortType {
        val savedValue = prefs.getString(KEY_SORT_TYPE, VideoSortType.LATEST.name)
        return runCatching {
            VideoSortType.valueOf(savedValue ?: VideoSortType.LATEST.name)
        }.getOrDefault(VideoSortType.LATEST)
    }

    private fun hasVideoPermission(): Boolean {
        val context = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val PREFS_NAME = "nxplayer_prefs"
        private const val KEY_SORT_TYPE = "library_sort_type"
    }
}
