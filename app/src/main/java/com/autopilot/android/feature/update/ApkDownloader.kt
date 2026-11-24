package com.autopilot.android.feature.update

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    private val currentCall = AtomicReference<Call?>(null)

    /**
     * Directory where downloaded update APKs are temporarily stored.
     */
    fun getUpdateDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "autopilot_updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Cleans up previously downloaded update files.
     */
    fun cleanupPreviousDownloads() {
        try {
            val dir = getUpdateDirectory()
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup failure
        }
    }

    /**
     * Checks if the device has enough free space for the download.
     */
    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        return try {
            val dir = getUpdateDirectory()
            val stat = StatFs(dir.absolutePath)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available > (requiredBytes + (20 * 1024 * 1024)) // Extra 20MB buffer
        } catch (e: Exception) {
            true // Fallback to allowing if check fails
        }
    }

    /**
     * Cancels any active download in progress.
     */
    fun cancel() {
        currentCall.getAndSet(null)?.cancel()
    }

    /**
     * Downloads an APK from the specified URL, reporting progress.
     */
    suspend fun downloadApk(
        url: String,
        targetFileName: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        cleanupPreviousDownloads()

        val targetDir = getUpdateDirectory()
        val finalFile = File(targetDir, targetFileName)
        val tempFile = File(targetDir, "$targetFileName.tmp")

        if (tempFile.exists()) tempFile.delete()
        if (finalFile.exists()) finalFile.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AutoPilot-Android-UpdateSystem/1.0")
            .build()

        val call = client.newCall(request)
        currentCall.set(call)

        onProgress(DownloadProgress(status = DownloadStatus.DOWNLOADING, bytesDownloaded = 0, totalBytes = 0, percentage = 0))

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Download failed with HTTP ${response.code}: ${response.message}"
                    onProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = err))
                    return@withContext Result.failure(IOException(err))
                }

                val body = response.body ?: run {
                    val err = "Response body is empty"
                    onProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = err))
                    return@withContext Result.failure(IOException(err))
                }

                val contentLength = body.contentLength()
                if (contentLength > 0 && !hasEnoughSpace(contentLength)) {
                    val err = "Insufficient storage space to download update (${contentLength / (1024 * 1024)} MB required)"
                    onProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = err))
                    return@withContext Result.failure(IOException(err))
                }

                var totalBytesRead = 0L
                val buffer = ByteArray(8 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var lastReportTime = System.currentTimeMillis()

                        while (coroutineContext.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalBytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 150 || (contentLength > 0 && totalBytesRead == contentLength)) {
                                lastReportTime = now
                                val pct = if (contentLength > 0) ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100) else 0
                                onProgress(
                                    DownloadProgress(
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesDownloaded = totalBytesRead,
                                        totalBytes = contentLength,
                                        percentage = pct
                                    )
                                )
                            }
                        }
                    }
                }

                if (!coroutineContext.isActive || call.isCanceled()) {
                    tempFile.delete()
                    onProgress(DownloadProgress(status = DownloadStatus.CANCELLED))
                    return@withContext Result.failure(IOException("Download cancelled by user"))
                }

                if (contentLength > 0 && totalBytesRead < contentLength) {
                    tempFile.delete()
                    val err = "Download incomplete: read $totalBytesRead of $contentLength bytes"
                    onProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = err))
                    return@withContext Result.failure(IOException(err))
                }

                // Rename temp to final file
                if (tempFile.renameTo(finalFile)) {
                    onProgress(
                        DownloadProgress(
                            status = DownloadStatus.COMPLETED,
                            bytesDownloaded = totalBytesRead,
                            totalBytes = totalBytesRead,
                            percentage = 100,
                            downloadedFile = finalFile
                        )
                    )
                    Result.success(finalFile)
                } else {
                    tempFile.delete()
                    val err = "Failed to finalize downloaded APK file"
                    onProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = err))
                    Result.failure(IOException(err))
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            finalFile.delete()
            val status = if (call.isCanceled()) DownloadStatus.CANCELLED else DownloadStatus.FAILED
            onProgress(DownloadProgress(status = status, errorMessage = e.message))
            Result.failure(e)
        } finally {
            currentCall.set(null)
        }
    }
}
