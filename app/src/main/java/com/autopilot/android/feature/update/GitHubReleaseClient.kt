package com.autopilot.android.feature.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubReleaseClient(
    val owner: String = DEFAULT_OWNER,
    val repo: String = DEFAULT_REPO,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val DEFAULT_OWNER = "arybishal"
        const val DEFAULT_REPO = "ButoPilot"
        const val API_BASE = "https://api.github.com"
    }

    val latestReleaseUrl: String
        get() = "$API_BASE/repos/$owner/$repo/releases/latest"

    /**
     * Queries the latest published GitHub release.
     */
    suspend fun fetchLatestRelease(isDebugBuild: Boolean = false): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "AutoPilot-Android-UpdateSystem/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                            ?: return@withContext Result.failure(IOException("Empty response body from GitHub API"))
                        val info = parseReleaseJson(body, isDebugBuild)
                            ?: return@withContext Result.failure(IllegalStateException("No valid APK installation asset found in the latest release"))
                        Result.success(info)
                    }
                    404 -> Result.failure(IllegalStateException("No published releases found for $owner/$repo"))
                    403 -> Result.failure(IllegalStateException("GitHub API rate limit exceeded. Please try again in a few minutes."))
                    else -> Result.failure(IOException("GitHub API returned error HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses the GitHub Release JSON object into [UpdateInfo].
     */
    fun parseReleaseJson(jsonStr: String, isDebugBuild: Boolean = false): UpdateInfo? {
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            return null
        }

        val tagName = root.get("tag_name")?.asString?.trim() ?: ""
        if (tagName.isBlank()) return null

        val releaseName = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { tagName } ?: tagName
        val changelog = root.get("body")?.takeIf { !it.isJsonNull }?.asString ?: "No changelog provided."
        val publishedAt = root.get("published_at")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val htmlUrl = root.get("html_url")?.takeIf { !it.isJsonNull }?.asString ?: "https://github.com/$owner/$repo/releases"

        val assetsArray = root.getAsJsonArray("assets") ?: JsonArray()
        val apkAsset = selectBestApkAsset(assetsArray, isDebugBuild) ?: return null

        val versionName = SemVer.cleanVersionString(tagName)

        return UpdateInfo(
            versionName = versionName,
            releaseTag = tagName,
            releaseName = releaseName,
            changelog = changelog,
            publishedAt = publishedAt,
            apkUrl = apkAsset.downloadUrl,
            apkName = apkAsset.name,
            apkSize = apkAsset.size,
            releasePageUrl = htmlUrl
        )
    }

    private data class ParsedAsset(
        val name: String,
        val size: Long,
        val downloadUrl: String,
        val contentType: String
    )

    /**
     * Chooses the best APK asset:
     * - Must end with ".apk".
     * - If in release mode, prefers "release" in filename and avoids "debug" if release exists.
     * - Returns the most relevant asset or null if none found.
     */
    private fun selectBestApkAsset(assets: JsonArray, isDebugBuild: Boolean): ParsedAsset? {
        val apkList = mutableListOf<ParsedAsset>()

        for (i in 0 until assets.size()) {
            val item = assets[i].asJsonObject
            val name = item.get("name")?.asString ?: ""
            val downloadUrl = item.get("browser_download_url")?.asString ?: ""
            val size = item.get("size")?.asLong ?: 0L
            val contentType = item.get("content_type")?.asString ?: ""

            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                apkList.add(ParsedAsset(name, size, downloadUrl, contentType))
            }
        }

        if (apkList.isEmpty()) return null

        if (isDebugBuild) {
            // If running a debug build, prefer debug APK if present, otherwise any APK
            return apkList.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                ?: apkList.firstOrNull()
        } else {
            // In release mode: prefer release APK and avoid debug APK
            return apkList.firstOrNull { it.name.contains("release", ignoreCase = true) && !it.name.contains("debug", ignoreCase = true) }
                ?: apkList.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                ?: apkList.firstOrNull()
        }
    }
}

