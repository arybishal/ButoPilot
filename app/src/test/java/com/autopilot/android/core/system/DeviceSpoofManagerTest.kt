package com.autopilot.android.core.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceSpoofManagerTest {

    @Test
    fun apply_readsOriginalsThenWritesPresetAndSavesSnapshot() = runBlocking {
        val shell = FakeShellRunner { command ->
            if (command.startsWith("getprop")) ShellResult(0, "real-device") else ShellResult(0, "")
        }
        val snapshots = FakeSnapshotStore()
        val values = SpoofValues(model = "Pixel 8 Pro", manufacturer = "Google", versionRelease = "14")

        val result = DeviceSpoofManager(shell, snapshots, values).apply()

        assertTrue(result.ok)
        assertEquals(3, result.applied)
        assertEquals(3, shell.commands.count { it.startsWith("getprop ") })
        assertTrue(shell.commands.contains("resetprop ro.product.model 'Pixel 8 Pro'"))
        assertTrue(shell.commands.contains("resetprop ro.build.version.release '14'"))
        assertEquals("real-device", snapshots.load()["ro.product.model"])
    }

    @Test
    fun restore_writesOriginalValuesAndClearsSnapshot() = runBlocking {
        val shell = FakeShellRunner { ShellResult(0, "") }
        val snapshots = FakeSnapshotStore()
        snapshots.save(mapOf("ro.product.model" to "real-device"))

        DeviceSpoofManager(shell, snapshots).restore()

        assertTrue(shell.commands.contains("resetprop ro.product.model 'real-device'"))
        assertTrue(snapshots.load().isEmpty())
    }

    @Test
    fun restore_deletesPropertyWhenOriginalWasEmpty() = runBlocking {
        val shell = FakeShellRunner { ShellResult(0, "") }
        val snapshots = FakeSnapshotStore()
        snapshots.save(mapOf("ro.product.brand" to ""))

        DeviceSpoofManager(shell, snapshots).restore()

        assertTrue(shell.commands.contains("resetprop --delete ro.product.brand"))
        assertTrue(snapshots.load().isEmpty())
    }

    @Test
    fun apply_restoresStaleSnapshotAndDoesNotCaptureSpoofedValues() = runBlocking {
        val shell = FakeShellRunner { command ->
            if (command.startsWith("getprop")) ShellResult(0, "spoofed") else ShellResult(0, "")
        }
        val snapshots = FakeSnapshotStore()
        snapshots.save(mapOf("ro.product.model" to "real-device"))
        val values = SpoofValues(model = "Pixel 8 Pro", manufacturer = "Google", versionRelease = "14")

        val result = DeviceSpoofManager(shell, snapshots, values).apply()

        assertFalse(result.ok)
        assertTrue(shell.commands.contains("resetprop ro.product.model 'real-device'"))
        assertFalse(shell.commands.contains("resetprop ro.product.model 'Pixel 8 Pro'"))
        assertTrue(snapshots.load().isEmpty())
    }

    @Test
    fun apply_skipsPropertiesThatCannotBeRead() = runBlocking {
        val shell = FakeShellRunner { command ->
            if (command == "getprop ro.product.model") ShellResult(1, "") else ShellResult(0, "x")
        }
        val snapshots = FakeSnapshotStore()
        val values = SpoofValues(model = "Pixel 8 Pro", manufacturer = "Google", versionRelease = "14")

        val result = DeviceSpoofManager(shell, snapshots, values).apply()

        assertFalse(result.ok)
        assertEquals(1, result.failed)
        assertFalse(shell.commands.contains("resetprop ro.product.model 'Pixel 8 Pro'"))
        assertEquals(2, snapshots.load().size)
    }

    private class FakeShellRunner(
        private val answer: (String) -> ShellResult
    ) : ShellRunner {
        val commands = mutableListOf<String>()
        override fun run(command: String): ShellResult {
            commands.add(command)
            return answer(command)
        }
    }

    private class FakeSnapshotStore : SpoofSnapshotStore {
        private var values: Map<String, String> = emptyMap()
        override fun load(): Map<String, String> = values
        override fun save(values: Map<String, String>) {
            this.values = values.toMap()
        }
        override fun clear() {
            values = emptyMap()
        }
    }
}
