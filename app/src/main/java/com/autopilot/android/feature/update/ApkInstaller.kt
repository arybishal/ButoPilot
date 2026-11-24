package com.autopilot.android.feature.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.Arrays

class ApkInstaller(private val context: Context) {

    /**
     * Checks if the app has permission to install unknown packages.
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches the system settings screen for granting install permission.
     */
    fun requestInstallPermission(activity: Activity? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        }
    }

    /**
     * Verifies the APK file integrity, package name, version, and signing certificates.
     */
    fun verifyApk(apkFile: File): ApkVerificationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return ApkVerificationResult(isValid = false, errorMessage = "Downloaded APK file does not exist or is empty.")
        }

        val pm = context.packageManager

        // 1. Parse archive info
        val archiveInfo = getArchivePackageInfo(pm, apkFile.absolutePath)
            ?: return ApkVerificationResult(isValid = false, errorMessage = "File is not a valid Android package (corrupted or malformed).")

        val downloadedPackageName = archiveInfo.packageName
        val downloadedVersionName = archiveInfo.versionName ?: ""
        val downloadedVersionCode = getVersionCode(archiveInfo)

        // 2. Package name check
        if (downloadedPackageName != context.packageName) {
            return ApkVerificationResult(
                isValid = false,
                packageName = downloadedPackageName,
                errorMessage = "Package name mismatch: downloaded '$downloadedPackageName' does not match installed '${context.packageName}'."
            )
        }

        // 3. Current installed package info
        val installedInfo = try {
            getPackageInfo(pm, context.packageName)
        } catch (e: Exception) {
            null
        }

        val currentVersionName = installedInfo?.versionName ?: "1.0.0"

        // 4. Version check: downloaded must be >= current
        if (SemVer.compare(downloadedVersionName, currentVersionName) < 0) {
            return ApkVerificationResult(
                isValid = false,
                versionName = downloadedVersionName,
                errorMessage = "Downloaded version ($downloadedVersionName) is older than currently installed ($currentVersionName)."
            )
        }

        // 5. Signature verification
        if (installedInfo != null) {
            val installedSignatures = getSignatures(installedInfo)
            val downloadedSignatures = getSignatures(archiveInfo)

            if (installedSignatures.isNotEmpty() && downloadedSignatures.isNotEmpty()) {
                val matches = matchSignatures(installedSignatures, downloadedSignatures)
                if (!matches) {
                    return ApkVerificationResult(
                        isValid = false,
                        errorMessage = "Signing certificate mismatch: The downloaded APK is signed with a different key than the installed application."
                    )
                }
            }
        }

        return ApkVerificationResult(
            isValid = true,
            packageName = downloadedPackageName,
            versionName = downloadedVersionName,
            versionCode = downloadedVersionCode
        )
    }

    /**
     * Launches the native Android package installer for the verified APK.
     */
    fun launchInstall(apkFile: File): Result<Unit> {
        return try {
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(pm: PackageManager, path: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    path,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

    @Suppress("DEPRECATION")
    private fun getSignatures(info: PackageInfo): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners?.forEach { list.add(it.toByteArray()) }
                } else {
                    signingInfo.signingCertificateHistory?.forEach { list.add(it.toByteArray()) }
                }
            }
        }
        if (list.isEmpty()) {
            info.signatures?.forEach { list.add(it.toByteArray()) }
        }
        return list
    }

    private fun matchSignatures(sig1: List<ByteArray>, sig2: List<ByteArray>): Boolean {
        // Compare SHA-256 fingerprint hashes
        val hashes1 = sig1.map { sha256(it) }.toSet()
        val hashes2 = sig2.map { sha256(it) }.toSet()
        return hashes1.intersect(hashes2).isNotEmpty()
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
