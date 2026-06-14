package com.nextgen.nxplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resume_state")
data class ResumeState(
    @PrimaryKey val videoUri: String,
    val positionMs: Long,
    val audioTrackIndex: Int = -1,
    val subtitleTrackIndex: Int = -1
)