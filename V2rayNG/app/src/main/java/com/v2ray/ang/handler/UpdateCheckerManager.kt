package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        // Saila: always the full list, so a Windows-only release (e.g. the current "latest") can be skipped.
        val url = AppConfig.APP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val releases = JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
            ?: throw IllegalStateException("No release found")
        val latestRelease = newestAndroidRelease(releases.toList(), includePreRelease)
            ?: return@withContext CheckUpdateResult(hasUpdate = false)

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = androidNotes(latestRelease.body),
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    /**
     * Saila: the highest version among the listed releases. GitHub's /releases order is not by
     * version (a b3 pre-release came after b2 and b1), so the first entry can be an older build.
     */
    internal fun newestRelease(releases: List<GitHubRelease>): GitHubRelease? =
        releases.maxWithOrNull { a, b -> compareVersions(a.tagName.removePrefix("v"), b.tagName.removePrefix("v")) }

    /** The newest release that changes the Android app (pre-releases only when asked for). */
    internal fun newestAndroidRelease(releases: List<GitHubRelease>, includePreRelease: Boolean): GitHubRelease? =
        newestRelease(releases.filter { (includePreRelease || !it.prerelease) && androidNotes(it.body) != null })

    private val androidSection = Regex("<!--\\s*android\\s*-->(.*?)<!--\\s*/android\\s*-->", RegexOption.DOT_MATCHES_ALL)
    private val windowsMarker = Regex("<!--\\s*windows\\s*-->")

    /**
     * Saila: one release carries both apps, its notes mark each part with <!-- android --> … <!-- /android -->
     * and <!-- windows --> … <!-- /windows --> (invisible on GitHub). Returns the Android parts only; null when
     * the release is Windows-only. Older releases without marks: the whole text.
     */
    internal fun androidNotes(body: String?): String? {
        val text = body.orEmpty()
        val parts = androidSection.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        return when {
            parts.isNotEmpty() -> parts.joinToString("\n\n")
            windowsMarker.containsMatchIn(text) -> null
            else -> text
        }
    }

    /** Saila versions look like "0.1.2-b3": compares 0, 1, 2, then the build number 3. */
    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = Regex("\\d+").findAll(version1).map { it.value.toInt() }.toList()
        val v2 = Regex("\\d+").findAll(version2).map { it.value.toInt() }.toList()

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i] else 0
            val num2 = if (i < v2.size) v2[i] else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}
