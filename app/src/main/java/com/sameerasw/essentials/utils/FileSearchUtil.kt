/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities
 * File: FileSearchUtil.kt
 * Description: Fast indexed universal file and media search utility utilizing MediaStore.
 */

package com.sameerasw.essentials.utils

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileSearchUtil {

    data class FileSearchResults(
        val mediaItems: List<PixelSearchResultItem.FileItem> = emptyList(),
        val documentItems: List<PixelSearchResultItem.FileItem> = emptyList(),
        val topMediaScore: Int = 0,
        val topDocScore: Int = 0,
    )

    suspend fun searchFiles(
        context: Context,
        query: String,
        limit: Int = 6,
        searchMedia: Boolean = true,
        searchDocs: Boolean = true,
    ): FileSearchResults = withContext(Dispatchers.IO) {
        val trimmed = query.trim()

        val scoredMedia = mutableListOf<Pair<PixelSearchResultItem.FileItem, Int>>()
        val scoredDocs = mutableListOf<Pair<PixelSearchResultItem.FileItem, Int>>()
        val seenIds = HashSet<Long>(128)
        val resolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATA,
        )

        val baseSelection =
            "${MediaStore.Files.FileColumns.SIZE} > 0 AND " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val queryUri = MediaStore.Files.getContentUri("external")

        fun processCursor(selection: String, selectionArgs: Array<String>?, maxRows: Int) {
            try {
                resolver.query(
                    queryUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                    var scanned = 0
                    while (cursor.moveToNext() && scanned < maxRows) {
                        scanned++
                        val id = cursor.getLong(idCol)
                        if (!seenIds.add(id)) continue

                        val name = cursor.getString(nameCol) ?: continue
                        if (name.startsWith(".") || name.contains(".thumbnails", ignoreCase = true)) continue

                        val mime = cursor.getString(mimeCol) ?: getMimeTypeFromExtension(name)
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val mediaType = cursor.getInt(mediaTypeCol)
                        val path = if (dataCol != -1) cursor.getString(dataCol) else null

                        val isImage = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE || mime.startsWith("image/", ignoreCase = true)
                        val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO || mime.startsWith("video/", ignoreCase = true)
                        val isAudio = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO || mime.startsWith("audio/", ignoreCase = true)
                        val isGif = mime.equals("image/gif", ignoreCase = true) || name.endsWith(".gif", ignoreCase = true)
                        val isMedia = isImage || isVideo || isGif

                        if (isMedia && !searchMedia) continue
                        if (!isMedia && !searchDocs) continue

                        val score = if (trimmed.isEmpty()) {
                            1
                        } else {
                            val stem = name.substringBeforeLast(".")
                            val parentFolder = path?.let { java.io.File(it).parentFile?.name }
                            FuzzySearchUtil.scoreFields(
                                query = trimmed,
                                primary = stem.ifBlank { name },
                                secondary = listOf(name),
                                tertiary = listOfNotNull(parentFolder),
                            )
                        }
                        if (score <= 0) continue

                        val contentUri = if (isImage) {
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else if (isVideo) {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        } else if (isAudio) {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            ContentUris.withAppendedId(queryUri, id)
                        }

                        val iconRes = getIconForMimeType(mime, name)
                        val item = PixelSearchResultItem.FileItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            dateModified = date,
                            isMedia = isMedia,
                            isGif = isGif,
                            isVideo = isVideo,
                            isAudio = isAudio,
                            path = path,
                            iconRes = iconRes,
                        )

                        val rankedScore = score + SearchUsageTracker.boost(
                            context,
                            SearchUsageTracker.fileKey(contentUri.toString()),
                        )
                        if (isMedia) {
                            scoredMedia.add(item to rankedScore)
                        } else {
                            scoredDocs.add(item to rankedScore)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FileSearchUtil", "Error querying MediaStore", e)
            }
        }

        if (trimmed.isNotEmpty()) {
            // Fast exact/substring pass first so older exact matches are always found
            processCursor(
                selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND $baseSelection",
                selectionArgs = arrayOf("%$trimmed%"),
                maxRows = 200,
            )
            // Recent files scan for fuzzy / acronym / typo / folder matches
            processCursor(
                selection = baseSelection,
                selectionArgs = null,
                maxRows = 1500,
            )
        } else {
            processCursor(
                selection = baseSelection,
                selectionArgs = null,
                maxRows = 100,
            )
        }

        val sortedMedia = scoredMedia.sortedWith(
            compareByDescending<Pair<PixelSearchResultItem.FileItem, Int>> { it.second }
                .thenByDescending { it.first.dateModified },
        ).take(limit)

        val sortedDocs = scoredDocs.sortedWith(
            compareByDescending<Pair<PixelSearchResultItem.FileItem, Int>> { it.second }
                .thenByDescending { it.first.dateModified },
        ).take(limit)

        FileSearchResults(
            mediaItems = sortedMedia.map { it.first },
            documentItems = sortedDocs.map { it.first },
            topMediaScore = sortedMedia.firstOrNull()?.second ?: 0,
            topDocScore = sortedDocs.firstOrNull()?.second ?: 0,
        )
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val formatted = bytes / Math.pow(1024.0, index.toDouble())
        return if (index == 0) "$bytes B" else java.text.DecimalFormat("#,##0.0").format(formatted) + " " + units[index]
    }

    private fun getMimeTypeFromExtension(name: String): String {
        val ext = name.substringAfterLast(".", "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "json" -> "application/json"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
    }

    fun getIconForMimeType(mime: String, name: String): Int {
        val ext = name.substringAfterLast(".", "").lowercase()
        return when {
            ext == "apk" || mime == "application/vnd.android.package-archive" -> R.drawable.rounded_android_24
            mime.startsWith("audio/") -> R.drawable.rounded_music_note_24
            mime.startsWith("image/") -> R.drawable.rounded_image_24
            mime.startsWith("video/") -> R.drawable.rounded_music_video_24
            else -> R.drawable.rounded_description_24
        }
    }
}
