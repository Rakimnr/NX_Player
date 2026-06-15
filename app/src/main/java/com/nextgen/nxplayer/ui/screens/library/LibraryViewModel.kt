package com.nextgen.nxplayer.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextgen.nxplayer.data.model.VideoItem
import com.nextgen.nxplayer.data.repository.VideoRepository
import com.nextgen.nxplayer.data.repository.VideoSortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.nextgen.nxplayer.utils.PermissionHelper

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.contentResolver)

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos

    private val _permissionGranted = MutableStateFlow(hasVideoPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _sortType = MutableStateFlow(VideoSortType.LATEST)
    val sortType: StateFlow<VideoSortType> = _sortType

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
            _videos.value = emptyList()
        }
    }

    fun loadVideos() {
        if (!hasVideoPermission()) {
            _permissionGranted.value = false
            _videos.value = emptyList()
            return
        }

        _permissionGranted.value = true
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _videos.value = repository.getVideos(_sortType.value)
            } catch (e: SecurityException) {
                _permissionGranted.value = false
                _videos.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeSortType(newSortType: VideoSortType) {
        _sortType.value = newSortType
        loadVideos()
    }

    private fun hasVideoPermission(): Boolean {
        val context = getApplication<Application>()
        return PermissionHelper.hasVideoPermission(context)
    }
}