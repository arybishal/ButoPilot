package com.autopilot.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autopilot.android.data.local.converter.Converters
import com.autopilot.android.data.local.dao.ExecutionHistoryDao
import com.autopilot.android.data.local.dao.ProfileDao
import com.autopilot.android.data.local.dao.ScheduleDao
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ScheduleEntity

@Database(
    entities = [ProfileEntity::class, ExecutionHistoryEntity::class, ScheduleEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AutoPilotDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AutoPilotDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_schedules` (
                        `id` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `profileName` TEXT NOT NULL,
                        `scheduleName` TEXT NOT NULL,
                        `scheduleType` TEXT NOT NULL,
                        `recurrenceRule` TEXT NOT NULL,
                        `targetEpochMillis` INTEGER NOT NULL,
                        `nextExecutionAt` INTEGER,
                        `lastExecutionAt` INTEGER,
                        `timezoneId` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `maxDurationMinutes` INTEGER,
                        `status` TEXT NOT NULL,
                        `lastError` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`profileId`) REFERENCES `automation_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_schedules_profileId` ON `automation_schedules` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_schedules_enabled` ON `automation_schedules` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_schedules_nextExecutionAt` ON `automation_schedules` (`nextExecutionAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_schedules_status` ON `automation_schedules` (`status`)")
            }
        }

        fun getInstance(context: Context): AutoPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoPilotDatabase::class.java,
                    "autopilot_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
