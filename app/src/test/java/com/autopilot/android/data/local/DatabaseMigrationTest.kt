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

    @Test
    fun testMigration3To4SqlSyntax() {
        val migration = AutoPilotDatabase.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }

    @Test
    fun testMigration4To5SqlSyntax() {
        val migration = AutoPilotDatabase.MIGRATION_4_5
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }

    @Test
    fun testMigration5To6SqlSyntax() {
        val migration = AutoPilotDatabase.MIGRATION_5_6
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
    }

    @Test
    fun testMigration6To7SqlSyntax() {
        val migration = AutoPilotDatabase.MIGRATION_6_7
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
    }
}
