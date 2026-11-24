package com.autopilot.android.feature.update

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateState(
    val currentVersion: String = "1.0.0",
    val checkStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val updateInfo: UpdateInfo? = null,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val downloadedApkFile: File? = null,
    val lastCheckTimestamp: Long = 0L,
    val isUpdateDialogVisible: Boolean = false,
    val isPermissionDialogVisible: Boolean = false,
    val errorMessage: String? = null
)

class UpdateManager(
    private val context: Context,
    val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    val downloader: ApkDownloader = ApkDownloader(context),
    val installer: ApkInstaller = ApkInstaller(context),
    val preferences: UpdatePreferences = UpdatePreferences(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {

    private val _state = MutableStateFlow(
        UpdateState(
            currentVersion = getCurrentAppVersion(),
            lastCheckTimestamp = preferences.lastCheckTimestamp
        )
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null

    /**
     * Resolves the currently installed app version string from PackageManager.
     */
    fun getCurrentAppVersion(): String {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "1.0.0"
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Initiates a check against GitHub for new releases.
     * @param isManual true if triggered by the user clicking "Check for Updates" in Settings.
     */
    fun checkForUpdates(isManual: Boolean = false) {
        if (activeCheckJob?.isActive == true) return

        activeCheckJob = scope.launch {
            _state.update {
                it.copy(
                    checkStatus = UpdateCheckStatus.CHECKING,
                    errorMessage = null
                )
            }

            val currentVer = getCurrentAppVersion()
            val isDebug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

            val result = releaseClient.fetchLatestRelease(isDebugBuild = isDebug)

            val now = System.currentTimeMillis()
            preferences.lastCheckTimestamp = now

            result.fold(
                onSuccess = { releaseInfo ->
                    val isNewer = SemVer.isNewer(releaseInfo.versionName, currentVer)
                    preferences.lastKnownReleaseTag = releaseInfo.releaseTag

                    if (isNewer) {
                        val isDismissed = !isManual && preferences.isDismissed(releaseInfo.releaseTag)

                        _state.update {
                            it.copy(
                                checkStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                                updateInfo = releaseInfo,
                                lastCheckTimestamp = now,
                                isUpdateDialogVisible = !isDismissed,
                                errorMessage = null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                checkStatus = UpdateCheckStatus.UP_TO_DATE,
                                updateInfo = releaseInfo,
                                lastCheckTimestamp = now,
                                isUpdateDialogVisible = false,
                                errorMessage = null
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            checkStatus = UpdateCheckStatus.ERROR,
                            lastCheckTimestamp = now,
                            errorMessage = error.message ?: "Failed to check for updates"
                        )
                    }
                }
            )
        }
    }

    /**
     * Starts downloading the APK for the currently available update.
     */
    fun startDownload() {
        val info = _state.value.updateInfo ?: return
        if (activeDownloadJob?.isActive == true) return

        activeDownloadJob = scope.launch {
            _state.update {
                it.copy(
                    downloadProgress = DownloadProgress(status = DownloadStatus.DOWNLOADING)
                )
            }

            val result = downloader.downloadApk(
                url = info.apkUrl,
                targetFileName = info.apkName.ifBlank { "AutoPilot-Update-${info.versionName}.apk" }
            ) { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }

            result.fold(
                onSuccess = { file ->
                    // Verify the downloaded APK before presenting installation
                    _state.update {
                        it.copy(
                            downloadProgress = it.downloadProgress.copy(status = DownloadStatus.VERIFYING)
                        )
                    }

                    val verification = installer.verifyApk(file)
                    if (verification.isValid) {
                        _state.update {
                            it.copy(
                                downloadedApkFile = file,
                                downloadProgress = it.downloadProgress.copy(
                                    status = DownloadStatus.COMPLETED,
                                    downloadedFile = file
                                )
                            )
                        }
                    } else {
                        file.delete()
                        _state.update {
                            it.copy(
                                downloadedApkFile = null,
                                downloadProgress = it.downloadProgress.copy(
                                    status = DownloadStatus.FAILED,
                                    errorMessage = verification.errorMessage ?: "APK validation failed"
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            downloadProgress = it.downloadProgress.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = error.message ?: "Download failed"
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Cancels any active APK download in progress.
     */
    fun cancelDownload() {
        activeDownloadJob?.cancel()
        downloader.cancel()
        _state.update {
            it.copy(downloadProgress = DownloadProgress(status = DownloadStatus.CANCELLED))
        }
    }

    /**
     * Installs the downloaded and verified APK.
     */
    fun installUpdate(activity: Activity? = null) {
        val file = _state.value.downloadedApkFile ?: return

        if (!installer.canRequestPackageInstalls()) {
            _state.update { it.copy(isPermissionDialogVisible = true) }
            return
        }

        installer.launchInstall(file)
    }

    /**
     * Request the install unknown packages permission.
     */
    fun requestInstallPermission(activity: Activity? = null) {
        _state.update { it.copy(isPermissionDialogVisible = false) }
        installer.requestInstallPermission(activity)
    }

    fun dismissPermissionDialog() {
        _state.update { it.copy(isPermissionDialogVisible = false) }
    }

    /**
     * Shows the update dialog (e.g. from Settings or when clicking an update card).
     */
    fun showUpdateDialog() {
        _state.update { it.copy(isUpdateDialogVisible = true) }
    }

    /**
     * Dismisses the update dialog.
     * @param rememberIfLater If true, remembers the release tag so the user is not nagged again on current launch.
     */
    fun dismissUpdateDialog(rememberIfLater: Boolean = true) {
        val currentTag = _state.value.updateInfo?.releaseTag
        if (rememberIfLater && currentTag != null) {
            preferences.dismissUpdate(currentTag)
        }
        _state.update { it.copy(isUpdateDialogVisible = false) }
    }
}
