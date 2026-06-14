package com.nextgen.nxplayer.data.local.dao

import androidx.room.*
import com.nextgen.nxplayer.data.model.ResumeState
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume_state WHERE videoUri = :videoUri")
    fun getResumeState(videoUri: String): Flow<ResumeState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveResumeState(state: ResumeState)

    @Query("DELETE FROM resume_state WHERE videoUri = :videoUri")
    suspend fun deleteResumeState(videoUri: String)
}