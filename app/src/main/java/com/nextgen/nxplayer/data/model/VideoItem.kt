package com.nextgen.nxplayer.data.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val name: String,
    val duration: Long,
    val uri: Uri,
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int
) {
    val resolutionLabel: String
        get() {
            val maxSide = maxOf(width, height)

            return when {
                maxSide >= 3840 -> "4K"
                maxSide >= 1920 -> "1080p"
                maxSide >= 1280 -> "720p"
                width > 0 && height > 0 -> "${height}p"
                else -> "HD"
            }
        }
    fun getSafeTitle(): String {
        val raw = uri.lastPathSegment ?: return "Unknown Video"

        return raw
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "Unknown Video" }
    }
}