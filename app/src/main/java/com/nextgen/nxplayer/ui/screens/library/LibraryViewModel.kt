package com.nextgen.nxplayer.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextgen.nxplayer.data.model.VideoItem
import com.nextgen.nxplayer.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.contentResolver)

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _videos.value = repository.getVideos()
                _permissionGranted.value = true
            } catch (e: SecurityException) {
                _permissionGranted.value = false
                _videos.value = emptyList()
            }
        }
    }
}