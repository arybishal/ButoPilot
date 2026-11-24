package com.autopilot.android.feature.backup

import android.content.Context
import android.net.Uri
import java.util.Base64
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.local.dao.BackupRecordDao
import com.autopilot.android.data.local.entity.BackupRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: AutoPilotDatabase,
    private val backupRecordDao: BackupRecordDao
) {
    private val serializer = BackupSerializer()
    private val crypto = BackupCrypto()

    companion object {
        const val BACKUP_EXTENSION = ".autopilot"
        const val MANIFEST_ENTRY_NAME = "manifest.json"
        const val ENCRYPTED_PAYLOAD_ENTRY_NAME = "payload.enc"
        const val PROFILES_ENTRY_NAME = "profiles.json"
        const val SCHEDULES_ENTRY_NAME = "schedules.json"
        const val PROXIES_ENTRY_NAME = "proxies.json"
        const val SOURCES_ENTRY_NAME = "sources.json"
        const val HISTORY_ENTRY_NAME = "history.json"

        const val MAX_ZIP_ENTRIES = 50
        const val MAX_ZIP_TOTAL_BYTES = 50 * 1024 * 1024 // 50MB
    }

    suspend fun createBackup(
        categories: Set<BackupCategory> = BackupCategory.values().toSet(),
        password: String? = null,
        outputDir: File = File(context.filesDir, "backups")
    ): File = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val backupFile = File(outputDir, "autopilot_backup_$timestamp$BACKUP_EXTENSION")

        // 1. Gather entities based on categories
        val profiles = if (categories.contains(BackupCategory.PROFILES)) database.profileDao().getAll() else emptyList()
        val schedules = if (categories.contains(BackupCategory.SCHEDULES)) database.scheduleDao().getAll() else emptyList()
        val proxies = if (categories.contains(BackupCategory.PROXIES)) database.proxyDao().getAll() else emptyList()
        val sources = if (categories.contains(BackupCategory.PROXY_SOURCES)) database.proxySourceDao().getAllFlow().let {
            database.proxySourceDao().getAllFlow()
            // ProxySourceDao doesn't have suspend getAll() in earlier definition, let's query via room db or flow
            val list = mutableListOf<com.autopilot.android.data.local.entity.ProxySourceEntity>()
            val cur = database.openHelper.readableDatabase.query("SELECT * FROM proxy_sources")
            while (cur.moveToNext()) {
                list.add(
                    com.autopilot.android.data.local.entity.ProxySourceEntity(
                        id = cur.getString(cur.getColumnIndexOrThrow("id")),
                        name = cur.getString(cur.getColumnIndexOrThrow("name")),
                        sourceType = cur.getString(cur.getColumnIndexOrThrow("sourceType")),
                        urlOrPath = if (cur.isNull(cur.getColumnIndexOrThrow("urlOrPath"))) null else cur.getString(cur.getColumnIndexOrThrow("urlOrPath")),
                        lastRefreshedAt = if (cur.isNull(cur.getColumnIndexOrThrow("lastRefreshedAt"))) null else cur.getLong(cur.getColumnIndexOrThrow("lastRefreshedAt")),
                        proxyCount = cur.getInt(cur.getColumnIndexOrThrow("proxyCount")),
                        createdAt = cur.getLong(cur.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
            cur.close()
            list
        } else emptyList()
        val history = if (categories.contains(BackupCategory.EXECUTION_HISTORY)) database.executionHistoryDao().getAll() else emptyList()

        // 2. Serialize payloads and calculate checksums
        val payloadMap = mutableMapOf<String, ByteArray>()
        val checksums = mutableMapOf<String, String>()
        val recordCounts = mutableMapOf<String, Int>()

        if (categories.contains(BackupCategory.PROFILES)) {
            val json = serializer.serializeProfiles(profiles)
            val bytes = json.toByteArray(Charsets.UTF_8)
            payloadMap[PROFILES_ENTRY_NAME] = bytes
            checksums[PROFILES_ENTRY_NAME] = serializer.calculateSha256(bytes)
            recordCounts["profiles"] = profiles.size
        }

        if (categories.contains(BackupCategory.SCHEDULES)) {
            val json = serializer.serializeSchedules(schedules)
            val bytes = json.toByteArray(Charsets.UTF_8)
            payloadMap[SCHEDULES_ENTRY_NAME] = bytes
            checksums[SCHEDULES_ENTRY_NAME] = serializer.calculateSha256(bytes)
            recordCounts["schedules"] = schedules.size
        }

        if (categories.contains(BackupCategory.PROXIES)) {
            val json = serializer.serializeProxies(proxies)
            val bytes = json.toByteArray(Charsets.UTF_8)
            payloadMap[PROXIES_ENTRY_NAME] = bytes
            checksums[PROXIES_ENTRY_NAME] = serializer.calculateSha256(bytes)
            recordCounts["proxies"] = proxies.size
        }

        if (categories.contains(BackupCategory.PROXY_SOURCES)) {
            val json = serializer.serializeProxySources(sources)
            val bytes = json.toByteArray(Charsets.UTF_8)
            payloadMap[SOURCES_ENTRY_NAME] = bytes
            checksums[SOURCES_ENTRY_NAME] = serializer.calculateSha256(bytes)
            recordCounts["proxySources"] = sources.size
        }

        if (categories.contains(BackupCategory.EXECUTION_HISTORY)) {
            val json = serializer.serializeHistory(history)
            val bytes = json.toByteArray(Charsets.UTF_8)
            payloadMap[HISTORY_ENTRY_NAME] = bytes
            checksums[HISTORY_ENTRY_NAME] = serializer.calculateSha256(bytes)
            recordCounts["executionHistory"] = history.size
        }

        val isEncrypted = !password.isNullOrBlank()
        var saltBase64: String? = null
        var ivBase64: String? = null
        var encryptedBytes: ByteArray? = null
        var kdfIterations: Int? = null

        if (isEncrypted) {
            // Bundle the payloads into an unencrypted byte buffer, then encrypt
            val unencryptedZipStream = ByteArrayOutputStream()
            ZipOutputStream(unencryptedZipStream).use { zos ->
                for ((entryName, bytes) in payloadMap) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }

            val payloadBundle = unencryptedZipStream.toByteArray()
            val encResult = crypto.encrypt(payloadBundle, password!!, BackupCrypto.PBKDF2_ITERATIONS)
            saltBase64 = Base64.getEncoder().encodeToString(encResult.salt)
            ivBase64 = Base64.getEncoder().encodeToString(encResult.iv)
            encryptedBytes = encResult.cipherText
            kdfIterations = encResult.iterations
        }

        val manifest = BackupManifest(
            formatVersion = 1,
            appVersion = "1.0.0",
            schemaVersion = 4,
            createdAt = timestamp,
            isEncrypted = isEncrypted,
            encryptionSaltBase64 = saltBase64,
            encryptionIvBase64 = ivBase64,
            kdfIterations = kdfIterations,
            exportedCategories = categories.map { it.name },
            recordCounts = recordCounts,
            checksums = checksums
        )

        val manifestBytes = serializer.serializeManifest(manifest).toByteArray(Charsets.UTF_8)

        // 3. Write outer zip archive
        val fos = FileOutputStream(backupFile)
        ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
            // Write manifest.json
            zos.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zos.write(manifestBytes)
            zos.closeEntry()

            if (isEncrypted && encryptedBytes != null) {
                zos.putNextEntry(ZipEntry(ENCRYPTED_PAYLOAD_ENTRY_NAME))
                zos.write(encryptedBytes)
                zos.closeEntry()
            } else {
                for ((entryName, bytes) in payloadMap) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        // 4. Save record in database
        val totalItems = recordCounts.values.sum()
        val record = BackupRecordEntity(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            filePath = backupFile.absolutePath,
            sizeBytes = backupFile.length(),
            isEncrypted = isEncrypted,
            itemCount = totalItems,
            status = "CREATED"
        )
        try {
            backupRecordDao.insert(record)
        } catch (e: Exception) {
            // Non-fatal
        }

        backupFile
    }

    suspend fun readManifest(archiveFile: File): BackupManifest = withContext(Dispatchers.IO) {
        require(archiveFile.exists()) { "Backup file does not exist: ${archiveFile.absolutePath}" }

        ZipFile(archiveFile).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_ENTRY_NAME)
                ?: throw IllegalArgumentException("Invalid backup: missing $MANIFEST_ENTRY_NAME")

            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            serializer.deserializeManifest(manifestJson)
        }
    }

    suspend fun generatePreview(
        archiveFile: File,
        password: String? = null
    ): RestorePreview = withContext(Dispatchers.IO) {
        val manifest = readManifest(archiveFile)

        if (manifest.isEncrypted && password.isNullOrBlank()) {
            return@withContext RestorePreview(
                manifest = manifest,
                profileCount = manifest.recordCounts["profiles"] ?: 0,
                scheduleCount = manifest.recordCounts["schedules"] ?: 0,
                proxyCount = manifest.recordCounts["proxies"] ?: 0,
                sourceCount = manifest.recordCounts["proxySources"] ?: 0,
                historyCount = manifest.recordCounts["executionHistory"] ?: 0,
                warnings = listOf("This backup is password encrypted. Please enter the decryption password to restore."),
                isCompatible = true,
                requiresPassword = true
            )
        }

        val warnings = mutableListOf<String>()
        var isCompatible = true

        if (manifest.formatVersion > 1) {
            warnings.add("Backup format version ${manifest.formatVersion} is newer than current app version.")
            isCompatible = false
        }
        if (manifest.schemaVersion > 4) {
            warnings.add("Database schema version ${manifest.schemaVersion} is newer than current app schema.")
            isCompatible = false
        }

        // Test decryption and extract payload entries in-memory
        val payloadMap = try {
            extractPayloads(archiveFile, manifest, password)
        } catch (e: Exception) {
            return@withContext RestorePreview(
                manifest = manifest,
                profileCount = manifest.recordCounts["profiles"] ?: 0,
                scheduleCount = manifest.recordCounts["schedules"] ?: 0,
                proxyCount = manifest.recordCounts["proxies"] ?: 0,
                sourceCount = manifest.recordCounts["proxySources"] ?: 0,
                historyCount = manifest.recordCounts["executionHistory"] ?: 0,
                warnings = listOf("Decryption or archive extraction failed: ${e.message}"),
                isCompatible = false,
                requiresPassword = manifest.isEncrypted
            )
        }

        // Verify checksums
        for ((entryName, expectedSha) in manifest.checksums) {
            val contentBytes = payloadMap[entryName]
            if (contentBytes == null) {
                warnings.add("Missing expected payload entry: $entryName")
                isCompatible = false
            } else {
                val actualSha = serializer.calculateSha256(contentBytes)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    warnings.add("Integrity checksum mismatch for $entryName")
                    isCompatible = false
                }
            }
        }

        RestorePreview(
            manifest = manifest,
            profileCount = manifest.recordCounts["profiles"] ?: 0,
            scheduleCount = manifest.recordCounts["schedules"] ?: 0,
            proxyCount = manifest.recordCounts["proxies"] ?: 0,
            sourceCount = manifest.recordCounts["proxySources"] ?: 0,
            historyCount = manifest.recordCounts["executionHistory"] ?: 0,
            warnings = warnings,
            isCompatible = isCompatible,
            requiresPassword = manifest.isEncrypted
        )
    }

    suspend fun extractPayloads(
        archiveFile: File,
        manifest: BackupManifest,
        password: String?
    ): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val payloadMap = mutableMapOf<String, ByteArray>()

        ZipFile(archiveFile).use { zip ->
            // Path Traversal Security Verification
            var entryCount = 0
            var totalBytes = 0L

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw SecurityException("Archive exceeds maximum entry count limit ($MAX_ZIP_ENTRIES)")
                }

                // Strictly forbid Zip Slip / path traversal in entry names
                if (entry.name.contains("..") || entry.name.startsWith("/") || entry.name.startsWith("\\")) {
                    throw SecurityException("Malicious path traversal attempted in zip entry: ${entry.name}")
                }
            }

            if (manifest.isEncrypted) {
                val encEntry = zip.getEntry(ENCRYPTED_PAYLOAD_ENTRY_NAME)
                    ?: throw IllegalArgumentException("Missing encrypted payload entry in archive")

                val cipherBytes = zip.getInputStream(encEntry).use { it.readBytes() }
                val salt = Base64.getDecoder().decode(manifest.encryptionSaltBase64)
                val iv = Base64.getDecoder().decode(manifest.encryptionIvBase64)
                val iterations = manifest.kdfIterations ?: BackupCrypto.PBKDF2_ITERATIONS

                val decryptedZipBytes = crypto.decrypt(cipherBytes, password ?: "", salt, iv, iterations)

                // Read inner zip stream
                val innerZip = java.util.zip.ZipInputStream(ByteArrayInputStream(decryptedZipBytes))
                var innerEntry = innerZip.nextEntry
                while (innerEntry != null) {
                    if (innerEntry.name.contains("..") || innerEntry.name.startsWith("/") || innerEntry.name.startsWith("\\")) {
                        throw SecurityException("Malicious path traversal in encrypted zip entry: ${innerEntry.name}")
                    }
                    val bytes = innerZip.readBytes()
                    totalBytes += bytes.size
                    if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                        throw SecurityException("Extracted payload exceeds maximum size limit ($MAX_ZIP_TOTAL_BYTES bytes)")
                    }
                    payloadMap[innerEntry.name] = bytes
                    innerEntry = innerZip.nextEntry
                }
            } else {
                for (entryName in listOf(PROFILES_ENTRY_NAME, SCHEDULES_ENTRY_NAME, PROXIES_ENTRY_NAME, SOURCES_ENTRY_NAME, HISTORY_ENTRY_NAME)) {
                    val entry = zip.getEntry(entryName)
                    if (entry != null) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        totalBytes += bytes.size
                        if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                            throw SecurityException("Extracted payload exceeds maximum size limit ($MAX_ZIP_TOTAL_BYTES bytes)")
                        }
                        payloadMap[entryName] = bytes
                    }
                }
            }
        }

        payloadMap
    }

    suspend fun exportToUri(sourceFile: File, destinationUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
            FileInputStream(sourceFile).use { inStream ->
                inStream.copyTo(outStream)
            }
        } ?: throw IOException("Could not open destination URI for writing: $destinationUri")
    }

    suspend fun importFromUri(sourceUri: Uri, destinationFile: File) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
            FileOutputStream(destinationFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        } ?: throw IOException("Could not open source URI for reading: $sourceUri")
    }
}
