package com.nextgen.nxplayer.data.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val name: String,
    val duration: Long,
    val uri: Uri,
    val size: Long,
    val dateAdded: Long
)