/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Decision Model
 * File: LocalDecisionModel.kt
 * Description: Catalog of 100% local / self-hosted decision model backends selectable in-app
 *              when standard launcher search yields no direct matches.
 */

package com.sameerasw.essentials.assistant.decision

enum class LocalDecisionModel(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String,
    val sizeBadge: String,
    val requiresDownload: Boolean,
    val isLocalEndpoint: Boolean = false,
) {
    OFF(
        id = "off",
        label = "Off (Search Only)",
        shortLabel = "Off",
        description = "Only show direct search results; never run a fallback decision model",
        sizeBadge = "Disabled",
        requiresDownload = false,
    ),
    BUILT_IN_LEXICAL(
        id = "builtin_lexical",
        label = "Built-in Fast (BM25 + Fuzzy)",
        shortLabel = "Fast Local",
        description = "Instant offline BM25, synonym & typo router with zero download (<1 ms)",
        sizeBadge = "0 MB · Built-in",
        requiresDownload = false,
    ),
    TINYDECIDE_Q4(
        id = "tinydecide_q4",
        label = "TinyDecide 10.4M Q4 (On-Device)",
        shortLabel = "TinyDecide 10.4M",
        description = "Apache-2.0 ELECTRA-small Jev decision model running locally on CPU",
        sizeBadge = "6.2 MB",
        requiresDownload = true,
    ),
    TINYDECIDE_FULL(
        id = "tinydecide_full",
        label = "TinyDecide 13.8M Full (On-Device)",
        shortLabel = "TinyDecide 13.8M",
        description = "Higher-accuracy 30K-vocab TinyDecide neural decision model running locally",
        sizeBadge = "10.0 MB",
        requiresDownload = true,
    ),
    LOCAL_SYSTEMONE(
        id = "local_systemone",
        label = "Local Server — Kev / Laya",
        shortLabel = "Local Kev/Laya",
        description = "Self-hosted local or Tailscale /v1/systemone endpoint (Kev or Laya)",
        sizeBadge = "Self-hosted",
        requiresDownload = false,
        isLocalEndpoint = true,
    ),
    ;

    companion object {
        const val DEFAULT_LOCAL_SYSTEMONE_URL = "http://127.0.0.1:9931/v1/systemone"

        fun fromId(raw: String?): LocalDecisionModel =
            entries.firstOrNull { it.id == raw } ?: BUILT_IN_LEXICAL
    }
}

sealed interface ModelDownloadState {
    data object NotRequired : ModelDownloadState
    data object NotDownloaded : ModelDownloadState
    data class Downloading(
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : ModelDownloadState
    data class Ready(val sizeBytes: Long) : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}
