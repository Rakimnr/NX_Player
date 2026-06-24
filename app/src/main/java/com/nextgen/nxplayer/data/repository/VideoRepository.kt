package com.nextgen.nxplayer.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.nextgen.nxplayer.data.model.VideoItem

enum class VideoSortType {
    LATEST,
    NAME,
    SIZE,
    DURATION
}

class VideoRepository(
    private val contentResolver: ContentResolver
) {

    fun getVideos(sortType: VideoSortType = VideoSortType.LATEST): List<VideoItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = when (sortType) {
            VideoSortType.LATEST -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            VideoSortType.NAME -> "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
            VideoSortType.SIZE -> "${MediaStore.Video.Media.SIZE} DESC"
            VideoSortType.DURATION -> "${MediaStore.Video.Media.DURATION} DESC"
        }

        return runCatching {
            val videos = mutableListOf<VideoItem>()

            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() }
                        ?: "Unknown Video"
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val dateAdded = cursor.getLong(dateCol).coerceAtLeast(0L)
                    val width = cursor.getInt(widthCol).coerceAtLeast(0)
                    val height = cursor.getInt(heightCol).coerceAtLeast(0)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videos.add(
                        VideoItem(
                            id = id,
                            name = name,
                            duration = duration,
                            uri = uri,
                            size = size,
                            dateAdded = dateAdded,
                            width = width,
                            height = height
                        )
                    )
                }
            }

            videos
        }.getOrElse {
            emptyList()
        }
    }
}
