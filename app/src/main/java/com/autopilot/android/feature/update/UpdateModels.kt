package com.autopilot.android.feature.update

import java.io.File

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR
}

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class UpdateInfo(
    val versionName: String,
    val releaseTag: String,
    val releaseName: String,
    val changelog: String,
    val publishedAt: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val releasePageUrl: String
)

data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percentage: Int = 0,
    val downloadedFile: File? = null,
    val errorMessage: String? = null
)

data class UpdateCheckResult(
    val status: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val updateInfo: UpdateInfo? = null,
    val currentVersion: String = "",
    val checkedAt: Long = 0L,
    val errorMessage: String? = null
)

data class ApkVerificationResult(
    val isValid: Boolean,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val errorMessage: String? = null
)
