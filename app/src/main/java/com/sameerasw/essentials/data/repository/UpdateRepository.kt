/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Data & Repository Layer
 * File: UpdateRepository.kt
 * Description: Data repository and storage component for UpdateRepository.kt.
 */

package com.sameerasw.essentials.data.repository

import android.content.Context
import com.google.gson.Gson
import com.sameerasw.essentials.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class UpdateRepository {
    companion object {
        const val REPO_OWNER = "aislingheart"
        const val REPO_NAME = "komorebi"
        private const val RELEASES_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    /**
     * Human-readable detail for the most recent failed check (HTTP status and/or GitHub message).
     * Reset at the start of each check.
     */
    @Volatile
    var lastError: String? = null
        private set

    suspend fun checkForUpdates(
        context: Context,
        isPreReleaseCheckEnabled: Boolean,
        currentVersion: String,
    ): UpdateInfo? = checkForUpdatesWithStatus(context, isPreReleaseCheckEnabled, currentVersion).getOrNull()

    suspend fun checkForUpdatesWithStatus(
        @Suppress("UNUSED_PARAMETER") context: Context,
        isPreReleaseCheckEnabled: Boolean,
        currentVersion: String,
    ): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            lastError = null
            try {
                Result.success(checkForUpdatesFromGitHub(isPreReleaseCheckEnabled, currentVersion))
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Result.failure(e)
            }
        }

    private suspend fun checkForUpdatesFromGitHub(
        isPreReleaseCheckEnabled: Boolean,
        currentVersion: String,
    ): UpdateInfo? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val urlString = if (isPreReleaseCheckEnabled) RELEASES_URL else LATEST_RELEASE_URL
                val url = URL(urlString)
                connection =
                    (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "Komorebi/$currentVersion-Updater")
                    }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody =
                        try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() }
                        } catch (_: Exception) {
                            null
                        }
                    lastError = buildHttpErrorMessage(responseCode, errorBody)
                    // 403/429 (rate limit) and other non-200 responses fail fast; no retry loop.
                    return@withContext null
                }

                val releaseData = connection.inputStream.bufferedReader().use { it.readText() }
                parseUpdateInfoFromJson(
                    releaseData = releaseData,
                    isPreReleaseCheckEnabled = isPreReleaseCheckEnabled,
                    currentVersion = currentVersion,
                )
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                e.printStackTrace()
                null
            } finally {
                connection?.disconnect()
            }
        }

    internal fun buildHttpErrorMessage(
        responseCode: Int,
        errorBody: String?,
    ): String {
        val gitHubMessage =
            try {
                errorBody
                    ?.let { Gson().fromJson(it, Map::class.java) as? Map<*, *> }
                    ?.get("message")
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        return if (gitHubMessage != null) {
            "HTTP $responseCode: $gitHubMessage"
        } else {
            "HTTP $responseCode"
        }
    }

    internal fun parseUpdateInfoFromJson(
        releaseData: String,
        isPreReleaseCheckEnabled: Boolean,
        currentVersion: String,
    ): UpdateInfo? {
        val gson = Gson()
        val release: Map<String, Any>? =
            if (isPreReleaseCheckEnabled) {
                val releases =
                    gson
                        .fromJson(releaseData, Array<Any>::class.java)
                        ?.filterIsInstance<Map<String, Any>>()
                        .orEmpty()
                        .filter { (it["draft"] as? Boolean) != true }

                releases.maxByOrNull { rel ->
                    val tagName = normalizeVersionTag(rel["tag_name"] as? String ?: "0.0.0")
                    SemanticVersion.parse(tagName)
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                (gson.fromJson(releaseData, Map::class.java) as? Map<String, Any>)
                    ?.takeIf { (it["draft"] as? Boolean) != true && (it["prerelease"] as? Boolean) != true }
            }

        if (release == null) return null

        val rawTag = release["tag_name"] as? String ?: "0.0"
        val latestVersion = normalizeVersionTag(rawTag)
        val body = release["body"] as? String ?: ""
        val releaseUrl = release["html_url"] as? String ?: ""
        val assets = (release["assets"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
        val downloadUrl = selectApkDownloadUrl(assets)
        val hasUpdate = isNewerVersion(currentVersion, latestVersion)

        return UpdateInfo(
            versionName = latestVersion,
            releaseNotes = body,
            downloadUrl = downloadUrl,
            releaseUrl = releaseUrl,
            isUpdateAvailable = hasUpdate,
        )
    }

    internal fun selectApkDownloadUrl(assets: List<Map<String, Any>>?): String {
        if (assets.isNullOrEmpty()) return ""
        val apkAssets =
            assets.filter { asset ->
                val name = asset["name"]?.toString()?.lowercase().orEmpty()
                val url = asset["browser_download_url"] as? String
                name.endsWith(".apk") && !url.isNullOrBlank()
            }
        if (apkAssets.isEmpty()) return ""

        val selected =
            apkAssets.firstOrNull { it["name"]?.toString()?.equals("app-release.apk", ignoreCase = true) == true }
                ?: apkAssets.firstOrNull { asset ->
                    val name = asset["name"]?.toString()?.lowercase().orEmpty()
                    !name.contains("unsigned") && !name.contains("debug") &&
                        (name.contains("komorebi") || name.contains("release"))
                }
                ?: apkAssets.firstOrNull { asset ->
                    val name = asset["name"]?.toString()?.lowercase().orEmpty()
                    !name.contains("unsigned") && !name.contains("debug")
                }
                ?: apkAssets.firstOrNull { asset ->
                    val name = asset["name"]?.toString()?.lowercase().orEmpty()
                    !name.contains("unsigned")
                }
                ?: apkAssets.first()

        return selected["browser_download_url"] as? String ?: ""
    }

    internal fun normalizeVersionTag(rawTag: String): String {
        var normalized = rawTag.trim()
        if (normalized.startsWith("komorebi-", ignoreCase = true)) {
            normalized = normalized.substring("komorebi-".length)
        }
        if (normalized.startsWith("v", ignoreCase = true)) {
            normalized = normalized.substring(1)
        }
        return normalized.ifEmpty { "0.0" }
    }

    internal fun isNewerVersion(
        current: String,
        latest: String,
    ): Boolean =
        try {
            val currentVer = SemanticVersion.parse(current)
            val latestVer = SemanticVersion.parse(latest)
            latestVer > currentVer
        } catch (e: Exception) {
            latest != current
        }

    internal data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            if (patch != other.patch) return patch - other.patch

            // Version with pre-release is LOWER than version without (e.g. 1.0.0-beta < 1.0.0)
            if (preRelease == null && other.preRelease != null) return 1
            if (preRelease != null && other.preRelease == null) return -1

            if (preRelease != null && other.preRelease != null) {
                return comparePreRelease(preRelease, other.preRelease)
            }

            return 0
        }

        private fun comparePreRelease(
            a: String,
            b: String,
        ): Int {
            val aParts = a.split('.', '-')
            val bParts = b.split('.', '-')
            val maxLen = maxOf(aParts.size, bParts.size)
            for (i in 0 until maxLen) {
                val left = aParts.getOrNull(i) ?: return -1
                val right = bParts.getOrNull(i) ?: return 1
                val leftInt = left.toIntOrNull()
                val rightInt = right.toIntOrNull()
                val cmp =
                    when {
                        leftInt != null && rightInt != null -> leftInt.compareTo(rightInt)
                        leftInt != null -> -1
                        rightInt != null -> 1
                        else -> left.compareTo(right, ignoreCase = true)
                    }
                if (cmp != 0) return cmp
            }
            return 0
        }

        companion object {
            /**
             * Parses version tags like "21.3.4-komorebi", "v21.3.5-komorebi-beta.1", or "v21.3-beta.2".
             * The "-komorebi" distribution flavor suffix is stripped so stable Komorebi builds are
             * not treated as pre-releases.
             */
            fun parse(versionInfo: String): SemanticVersion {
                return try {
                    var version = versionInfo.trim()
                    if (version.startsWith("komorebi-", ignoreCase = true)) {
                        version = version.substring("komorebi-".length)
                    }
                    if (version.startsWith("v", ignoreCase = true)) {
                        version = version.substring(1)
                    }
                    // Strip SemVer build metadata (+...)
                    version = version.substringBefore('+')

                    // Split into main parts and qualifier parts (e.g. "21.3.4-komorebi" or "21.3.5-komorebi-beta.1")
                    val parts = version.split("-", limit = 2)
                    val baseParts = parts[0].split(".")

                    val major = baseParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val minor = baseParts.getOrNull(1)?.toIntOrNull() ?: 0
                    val patch = baseParts.getOrNull(2)?.toIntOrNull() ?: 0

                    val preRelease =
                        if (parts.size > 1) {
                            parts[1]
                                .split("-")
                                .flatMap { token ->
                                    if (token.startsWith("komorebi.", ignoreCase = true)) {
                                        listOf(token.substring("komorebi.".length))
                                    } else if (token.endsWith(".komorebi", ignoreCase = true)) {
                                        listOf(token.substring(0, token.length - ".komorebi".length))
                                    } else {
                                        listOf(token)
                                    }
                                }.filter { it.isNotBlank() && !it.equals("komorebi", ignoreCase = true) }
                                .joinToString("-")
                                .takeIf { it.isNotBlank() }
                        } else {
                            null
                        }

                    SemanticVersion(major, minor, patch, preRelease)
                } catch (_: Exception) {
                    SemanticVersion(0, 0, 0)
                }
            }
        }
    }
}
