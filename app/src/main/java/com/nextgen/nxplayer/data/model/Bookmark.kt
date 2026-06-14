package com.nextgen.nxplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUri: String,
    val title: String,
    val positionMs: Long,
    val note: String = ""
)