package com.nextgen.nxplayer.ui.screens.player

import com.nextgen.nxplayer.data.model.VideoItem

data class QueuedVideo(
    val uri: String,
    val title: String
)

object PlayerQueueStore {
    private var queue: List<QueuedVideo> = emptyList()

    @Synchronized
    fun setQueue(videos: List<VideoItem>, selectedVideo: VideoItem) {
        val selectedUri = selectedVideo.uri.toString()
        val cleanedQueue = videos
            .distinctBy { it.uri.toString() }
            .map { video ->
                QueuedVideo(
                    uri = video.uri.toString(),
                    title = video.name.ifBlank { "Video" }
                )
            }

        queue = if (cleanedQueue.any { it.uri == selectedUri }) {
            cleanedQueue
        } else {
            listOf(
                QueuedVideo(
                    uri = selectedUri,
                    title = selectedVideo.name.ifBlank { "Video" }
                )
            ) + cleanedQueue
        }
    }

    @Synchronized
    fun getQueueFor(uri: String, fallbackTitle: String): List<QueuedVideo> {
        return if (queue.any { it.uri == uri }) {
            queue
        } else {
            listOf(
                QueuedVideo(
                    uri = uri,
                    title = fallbackTitle.ifBlank { "Video" }
                )
            )
        }
    }
}
