package com.nextgen.nxplayer.data.local.dao

import androidx.room.*
import com.nextgen.nxplayer.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE videoUri = :videoUri ORDER BY positionMs ASC")
    fun getBookmarksForVideo(videoUri: String): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)
}