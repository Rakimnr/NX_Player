package com.nextgen.nxplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nextgen.nxplayer.data.local.dao.ResumeDao
import com.nextgen.nxplayer.data.model.ResumeState

@Database(entities = [ResumeState::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resumeDao(): ResumeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nxplayer_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}