package com.autopilot.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autopilot.android.data.local.converter.Converters
import com.autopilot.android.data.local.dao.ExecutionHistoryDao
import com.autopilot.android.data.local.dao.ProfileDao
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity

@Database(
    entities = [ProfileEntity::class, ExecutionHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AutoPilotDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AutoPilotDatabase? = null

        fun getInstance(context: Context): AutoPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoPilotDatabase::class.java,
                    "autopilot_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
