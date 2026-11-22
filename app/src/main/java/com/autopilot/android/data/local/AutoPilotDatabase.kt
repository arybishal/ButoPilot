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
import com.autopilot.android.data.local.dao.ProxyDao
import com.autopilot.android.data.local.dao.ProxySourceDao
import com.autopilot.android.data.local.dao.ScheduleDao
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ProxyEntity
import com.autopilot.android.data.local.entity.ProxySourceEntity
import com.autopilot.android.data.local.entity.ScheduleEntity

@Database(
    entities = [
        ProfileEntity::class,
        ExecutionHistoryEntity::class,
        ScheduleEntity::class,
        ProxyEntity::class,
        ProxySourceEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AutoPilotDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun proxyDao(): ProxyDao
    abstract fun proxySourceDao(): ProxySourceDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `automation_profiles` ADD COLUMN `proxyId` TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proxy_configurations` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `encryptedUsername` TEXT,
                        `encryptedPassword` TEXT,
                        `usernameIv` TEXT,
                        `passwordIv` TEXT,
                        `region` TEXT,
                        `notes` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `enabled` INTEGER NOT NULL,
                        `lastHealthStatus` TEXT NOT NULL,
                        `lastLatencyMs` INTEGER,
                        `lastTestedAt` INTEGER,
                        `lastErrorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_proxy_configurations_host_port` ON `proxy_configurations` (`host`, `port`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_proxy_configurations_enabled` ON `proxy_configurations` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_proxy_configurations_lastHealthStatus` ON `proxy_configurations` (`lastHealthStatus`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proxy_sources` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `urlOrPath` TEXT,
                        `lastRefreshedAt` INTEGER,
                        `proxyCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AutoPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoPilotDatabase::class.java,
                    "autopilot_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
