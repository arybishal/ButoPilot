package com.autopilot.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autopilot.android.data.local.converter.Converters
import com.autopilot.android.data.local.dao.BackupRecordDao
import com.autopilot.android.data.local.dao.DiagnosticDao
import com.autopilot.android.data.local.dao.ExecutionEventDao
import com.autopilot.android.data.local.dao.ExecutionHistoryDao
import com.autopilot.android.data.local.dao.ProfileDao
import com.autopilot.android.data.local.dao.ProxyDao
import com.autopilot.android.data.local.dao.ProxySourceDao
import com.autopilot.android.data.local.dao.ScheduleDao
import com.autopilot.android.data.local.dao.TrafficDao
import com.autopilot.android.data.local.entity.BackupRecordEntity
import com.autopilot.android.data.local.entity.DiagnosticRecordEntity
import com.autopilot.android.data.local.entity.ExecutionEventEntity
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ProxyEntity
import com.autopilot.android.data.local.entity.TrafficRunEntity
import com.autopilot.android.data.local.entity.ProxySourceEntity
import com.autopilot.android.data.local.entity.ScheduleEntity

@Database(
    entities = [
        ProfileEntity::class,
        ExecutionHistoryEntity::class,
        ScheduleEntity::class,
        ProxyEntity::class,
        ProxySourceEntity::class,
        DiagnosticRecordEntity::class,
        ExecutionEventEntity::class,
        BackupRecordEntity::class,
        TrafficRunEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AutoPilotDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun proxyDao(): ProxyDao
    abstract fun proxySourceDao(): ProxySourceDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun executionEventDao(): ExecutionEventDao
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun trafficDao(): TrafficDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnostic_records` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `shortResult` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `technicalDetailsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_records_category` ON `diagnostic_records` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_records_timestamp` ON `diagnostic_records` (`timestamp`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `execution_events` (
                        `id` TEXT NOT NULL,
                        `executionId` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `errorCategory` TEXT,
                        `message` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_events_executionId` ON `execution_events` (`executionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_events_profileId` ON `execution_events` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_events_timestamp` ON `execution_events` (`timestamp`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_records` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `isEncrypted` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_records_timestamp` ON `backup_records` (`timestamp`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `traffic_runs` (
                        `id` TEXT NOT NULL,
                        `websiteUrl` TEXT NOT NULL,
                        `plannedEvents` INTEGER NOT NULL,
                        `mode` TEXT NOT NULL,
                        `speed` TEXT NOT NULL,
                        `concurrency` INTEGER NOT NULL,
                        `pagePathsJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `submitted` INTEGER NOT NULL,
                        `failed` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `startedAt` INTEGER,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_traffic_runs_createdAt` ON `traffic_runs` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_traffic_runs_status` ON `traffic_runs` (`status`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `automation_profiles` ADD COLUMN `enableSpoofing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `automation_profiles` ADD COLUMN `enableFreeProxy` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `automation_profiles` ADD COLUMN `enableHumanBehaviourScript` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `traffic_runs` ADD COLUMN `deviceCategory` TEXT NOT NULL DEFAULT 'ALL'")
                db.execSQL("ALTER TABLE `automation_profiles` ADD COLUMN `deviceCategory` TEXT NOT NULL DEFAULT 'ALL'")
            }
        }

        fun getInstance(context: Context): AutoPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoPilotDatabase::class.java,
                    "autopilot_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
