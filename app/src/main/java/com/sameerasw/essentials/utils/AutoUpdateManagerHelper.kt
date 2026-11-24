/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities - General
 * File: AutoUpdateManagerHelper.kt
 * Description: Utility helper for AutoUpdateManagerHelper.kt.
 */

package com.sameerasw.essentials.utils

import AutoUpdaterManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.autoupdater.UpdateFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.Locale

class AutoUpdateManagerHelper(
    private val context: Context,
) {
    private val autoUpdaterManager = AutoUpdaterManager(context)

    sealed class DownloadResult {
        data class Success(val apkFile: File) : DownloadResult()
        data class Failed(val reason: String) : DownloadResult()
    }

    suspend fun checkForUpdate(jsonUrl: String): UpdateFeatures? =
        withContext(Dispatchers.IO) {
            try {
                autoUpdaterManager.checkForUpdate(jsonUrl)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun downloadAndInstallApk(
        apkUrl: String,
        apkName: String,
        autoInstall: Boolean = true,
        onProgressUpdate: (Int) -> Unit,
        onError: (String) -> Unit = {},
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            if (apkUrl.isBlank()) {
                withContext(Dispatchers.Main) { onError("Missing download URL") }
                return@withContext DownloadResult.Failed("Missing download URL")
            }

            if (!isAllowedUpdateUrl(apkUrl)) {
                withContext(Dispatchers.Main) { onError("Untrusted download URL") }
                return@withContext DownloadResult.Failed("Untrusted download URL")
            }

            try {
                AutoBackupManager.backupBeforeUpdate(context)

                val safeName = apkName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Komorebi_update" }
                val fileName = if (safeName.endsWith(".apk", ignoreCase = true)) safeName else "$safeName.apk"
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: run {
                        withContext(Dispatchers.Main) { onError("Downloads directory unavailable") }
                        return@withContext DownloadResult.Failed("Downloads directory unavailable")
                    }
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val targetFile = File(downloadsDir, fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        ?: run {
                            withContext(Dispatchers.Main) { onError("DownloadManager unavailable") }
                            return@withContext DownloadResult.Failed("DownloadManager unavailable")
                        }

                val request =
                    DownloadManager
                        .Request(apkUrl.toUri())
                        .setTitle("Komorebi")
                        .setDescription(fileName)
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

                val downloadId = downloadManager.enqueue(request)
                val query = DownloadManager.Query().setFilterById(downloadId)

                var lastReportedProgress = -1
                val deadlineMs = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS
                while (SystemClock.elapsedRealtime() < deadlineMs) {
                    val state =
                        downloadManager.query(query)?.use { cursor ->
                            if (!cursor.moveToFirst()) {
                                return@use Triple(DownloadManager.STATUS_FAILED, 0L, 0L)
                            }
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                            val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                            val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                            Triple(status, downloaded, total)
                        } ?: Triple(DownloadManager.STATUS_FAILED, 0L, 0L)

                    val (status, downloaded, total) = state
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (!targetFile.exists() || targetFile.length() <= 0L) {
                                withContext(Dispatchers.Main) { onError("Downloaded file missing") }
                                return@withContext DownloadResult.Failed("Downloaded file missing")
                            }
                            withContext(Dispatchers.Main) { onProgressUpdate(100) }
                            if (autoInstall) {
                                withContext(Dispatchers.Main) { installApk(context, targetFile) }
                            }
                            return@withContext DownloadResult.Success(targetFile)
                        }

                        DownloadManager.STATUS_FAILED -> {
                            withContext(Dispatchers.Main) { onError("Download failed") }
                            return@withContext DownloadResult.Failed("Download failed")
                        }

                        else -> {
                            if (total > 0L) {
                                val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 99)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    withContext(Dispatchers.Main) { onProgressUpdate(progress) }
                                }
                            }
                        }
                    }

                    delay(250L)
                }

                runCatching { downloadManager.remove(downloadId) }
                withContext(Dispatchers.Main) { onError("Download timed out") }
                DownloadResult.Failed("Download timed out")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                val message = e.message ?: "Download failed"
                withContext(Dispatchers.Main) { onError(message) }
                DownloadResult.Failed(message)
            }
        }

    fun buildInstallIntent(
        context: Context,
        apkFile: File,
    ): Intent {
        val apkUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installApk(
        context: Context,
        apkFile: File,
    ): Boolean =
        runCatching {
            val installIntent = buildInstallIntent(context, apkFile)
            context.startActivity(installIntent)
            true
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(false)

    fun getCurrentAppVersionName(): String = autoUpdaterManager.getCurrentAppVersionName()

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private val ALLOWED_UPDATE_HOSTS =
            setOf(
                "github.com",
                "objects.githubusercontent.com",
                "release-assets.githubusercontent.com",
                "api.github.com",
            )

        fun isAllowedUpdateUrl(url: String): Boolean {
            if (url.isBlank()) return false
            return try {
                val uri = URI(url.trim())
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                val host = uri.host?.lowercase(Locale.ROOT)
                scheme == "https" &&
                    uri.userInfo == null &&
                    (uri.port == -1 || uri.port == 443) &&
                    host != null &&
                    host in ALLOWED_UPDATE_HOSTS
            } catch (_: Exception) {
                false
            }
        }
    }
}
