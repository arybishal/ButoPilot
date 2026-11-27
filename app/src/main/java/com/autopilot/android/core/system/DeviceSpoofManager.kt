package com.autopilot.android.core.system

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Temporary device identity used while an automation run is active. */
data class SpoofValues(
    val model: String = "Pixel 8 Pro",
    val manufacturer: String = "Google",
    val versionRelease: String = "14"
) {
    val props: Map<String, String> = linkedMapOf(
        "ro.product.model" to model,
        "ro.product.manufacturer" to manufacturer,
        "ro.build.version.release" to versionRelease
    )
}

data class ShellResult(val exitCode: Int, val output: String)

interface ShellRunner {
    fun run(command: String): ShellResult
}

class SuShellRunner : ShellRunner {
    override fun run(command: String): ShellResult = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            val text = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                text.append(line).append('\n')
                line = reader.readLine()
            }
            text.toString().trim()
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            ShellResult(-1, "shell command timed out")
        } else {
            ShellResult(process.exitValue(), output)
        }
    } catch (e: Exception) {
        ShellResult(-1, e.message ?: "shell command failed")
    }
}

interface SpoofSnapshotStore {
    fun load(): Map<String, String>
    fun save(values: Map<String, String>)
    fun clear()
}

/** Keeps originals across process death so props are never left spoofed. */
class SharedPrefsSpoofSnapshotStore(context: Context) : SpoofSnapshotStore {
    private val prefs = context.applicationContext.getSharedPreferences("device_spoof_snapshot", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun load(): Map<String, String> = try {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }

    override fun save(values: Map<String, String>) {
        prefs.edit().putString(KEY, gson.toJson(values)).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    private companion object {
        const val KEY = "original_props"
    }
}

data class SpoofApplyResult(val applied: Int, val failed: Int) {
    val ok: Boolean get() = applied > 0 && failed == 0
}

/** Applies root-backed build props for a run and restores the originals afterwards. */
class DeviceSpoofManager(
    private val shell: ShellRunner,
    private val snapshots: SpoofSnapshotStore,
    private val values: SpoofValues = SpoofValues()
) {
    suspend fun apply(values: SpoofValues = this.values): SpoofApplyResult = withContext(Dispatchers.IO) {
        // Never treat already-spoofed values as originals after a crashed run.
        if (snapshots.load().isNotEmpty()) {
            restore()
            return@withContext SpoofApplyResult(0, values.props.size)
        }

        val originals = linkedMapOf<String, String>()
        var failed = 0
        values.props.forEach { (key, target) ->
            val current = shell.run("getprop $key")
            if (current.exitCode != 0) {
                failed++
                return@forEach
            }
            val changed = shell.run("resetprop $key '$target'")
            if (changed.exitCode == 0) originals[key] = current.output else failed++
        }
        if (originals.isNotEmpty()) snapshots.save(originals)
        SpoofApplyResult(originals.size, failed)
    }

    suspend fun restore() = withContext(Dispatchers.IO) {
        val originals = snapshots.load()
        val remaining = linkedMapOf<String, String>()
        originals.forEach { (key, original) ->
            val command = if (original.isBlank()) {
                "resetprop --delete $key"
            } else {
                "resetprop $key '$original'"
            }
            if (shell.run(command).exitCode != 0) remaining[key] = original
        }
        if (remaining.isEmpty()) snapshots.clear() else snapshots.save(remaining)
    }
}
