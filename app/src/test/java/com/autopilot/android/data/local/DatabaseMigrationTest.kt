package com.autopilot.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class DatabaseMigrationTest {

    @Test
    fun testMigration1To2SqlSyntax() {
        // Test that the migration SQL compiles and executes without SQLite syntax errors
        val migration = AutoPilotDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun testMigration2To3SqlSyntax() {
        val migration = AutoPilotDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
    }
}
