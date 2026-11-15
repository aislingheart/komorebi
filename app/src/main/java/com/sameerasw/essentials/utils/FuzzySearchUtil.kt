/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: FuzzySearchUtil.kt
 * Description: Unified fuzzy, acronym, subsequence, and typo-tolerant matching & scoring utility.
 */

package com.sameerasw.essentials.utils

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

object FuzzySearchUtil {

    private val WORD_SPLIT_REGEX = Regex("[\\s\\-_./:()\\[\\],]+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    @Volatile
    private var typoToleranceEnabled = true

    fun setTypoToleranceEnabled(enabled: Boolean) {
        typoToleranceEnabled = enabled
    }

    /**
     * Returns a relevance score > 0 if [query] matches [target], or 0 if it does not match.
     * Higher scores represent stronger matches (max 1000 for exact match).
     */
    fun score(query: String, target: String): Int {
        val rawQ = query.trim()
        val rawT = target.trim()
        if (rawQ.isEmpty() || rawT.isEmpty()) return 0

        val q = rawQ.lowercase(Locale.ROOT)
        val t = rawT.lowercase(Locale.ROOT)

        if (t == q) return 1000
        if (t.startsWith(q)) {
            return (920 - (t.length - q.length).coerceAtMost(40)).coerceAtLeast(880)
        }

        val tokens = q.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val wholeScore = scoreSingle(q, t, rawT)
            var totalTokenScore = 0
            var allMatched = true
            for (token in tokens) {
                val s = scoreSingle(token, t, rawT)
                if (s <= 0) {
                    allMatched = false
                    break
                }
                totalTokenScore += s
            }
            if (allMatched) {
                val avg = (totalTokenScore / tokens.size) + 30
                return maxOf(wholeScore, avg.coerceAtMost(875))
            }
            if (wholeScore > 0) return wholeScore
        }

        return scoreSingle(q, t, rawT)
    }

    /**
     * Scores [query] across primary, secondary (aliases/keywords), and tertiary (description/category/path) fields.
     */
    fun scoreFields(
        query: String,
        primary: String,
        secondary: List<String> = emptyList(),
        tertiary: List<String> = emptyList(),
    ): Int {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return 0

        val primaryScore = score(trimmed, primary)
        var bestSecondary = 0
        for (field in secondary) {
            if (field.isBlank()) continue
            val s = (score(trimmed, field) * 0.88f).toInt()
            if (s > bestSecondary) bestSecondary = s
        }
        var bestTertiary = 0
        for (field in tertiary) {
            if (field.isBlank()) continue
            val s = (score(trimmed, field) * 0.65f).toInt()
            if (s > bestTertiary) bestTertiary = s
        }

        val best = maxOf(primaryScore, bestSecondary, bestTertiary)
        if (best <= 0) return 0

        val bonus = if (primaryScore > 0 && (bestSecondary > 0 || bestTertiary > 0)) 15 else 0
        return (best + bonus).coerceAtMost(1000)
    }

    private fun scoreSingle(q: String, t: String, rawT: String): Int {
        if (t == q) return 1000
        if (t.startsWith(q)) {
            return (920 - (t.length - q.length).coerceAtMost(40)).coerceAtLeast(880)
        }

        // 1. Word-start match (e.g. "mus" in "YouTube Music", "set" in "App Settings")
        val words = extractWords(rawT)
        var bestWordPrefix = 0
        for ((idx, word) in words.withIndex()) {
            if (word.startsWith(q)) {
                val candidate = (850 - (idx * 12).coerceAtMost(60) - (word.length - q.length).coerceAtMost(30))
                    .coerceAtLeast(760)
                if (candidate > bestWordPrefix) bestWordPrefix = candidate
            }
        }
        if (bestWordPrefix > 0) return bestWordPrefix

        // 2. Acronym / camelCase initials match (e.g. "ytm" -> "YouTube Music", "gm" -> "Google Maps")
        if (q.length >= 2) {
            val initials = extractInitials(rawT)
            if (initials.isNotEmpty()) {
                if (initials == q) return 835
                if (initials.startsWith(q)) return 810
                if (isSubsequence(q, initials)) return 755
            }
        }

        // 3. Contiguous substring match
        val subIdx = t.indexOf(q)
        if (subIdx >= 0) {
            return (720 - subIdx.coerceAtMost(90)).coerceAtLeast(630)
        }

        // 4. Ordered character subsequence (fzf-style, e.g. "spotfy" -> "Spotify", "clpb" -> "Clipboard")
        if (q.length >= 2) {
            val subseqScore = scoreSubsequence(q, t, rawT)
            if (subseqScore > 0) return subseqScore
        }

        // 5. Typo-tolerant Damerau-Levenshtein edit distance (e.g. "goolge" -> "google", "setings" -> "settings")
        if (typoToleranceEnabled && q.length >= 3) {
            val typoScore = scoreTypoTolerance(q, t, words)
            if (typoScore > 0) return typoScore
        }

        return 0
    }

    private fun extractWords(raw: String): List<String> {
        val result = ArrayList<String>(8)
        val tokens = raw.split(WORD_SPLIT_REGEX).filter { it.isNotEmpty() }
        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            result.add(lower)
            // Also split camelCase sub-words if present (e.g. "YouTube" -> "you", "tube")
            var start = 0
            for (i in 1 until token.length) {
                if (token[i].isUpperCase() && token[i - 1].isLowerCase()) {
                    if (i > start) {
                        val sub = token.substring(start, i).lowercase(Locale.ROOT)
                        if (sub != lower) result.add(sub)
                    }
                    start = i
                }
            }
            if (start > 0 && start < token.length) {
                val tail = token.substring(start).lowercase(Locale.ROOT)
                if (tail != lower) result.add(tail)
            }
        }
        return result
    }

    private fun extractInitials(raw: String): String {
        val sb = StringBuilder()
        var prevWasSep = true
        var prevChar = '\u0000'
        for (ch in raw) {
            if (!ch.isLetterOrDigit()) {
                prevWasSep = true
                prevChar = ch
                continue
            }
            val isWordStart = prevWasSep || (ch.isUpperCase() && prevChar.isLowerCase())
            if (isWordStart) {
                sb.append(ch.lowercaseChar())
            }
            prevWasSep = false
            prevChar = ch
        }
        return sb.toString()
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.length > haystack.length) return false
        var ni = 0
        var hi = 0
        while (ni < needle.length && hi < haystack.length) {
            if (needle[ni] == haystack[hi]) ni++
            hi++
        }
        return ni == needle.length
    }

    private fun scoreSubsequence(q: String, t: String, rawT: String): Int {
        // Greedy forward pass preferring word-start matches when ambiguous
        var qi = 0
        var firstMatch = -1
        var lastMatch = -1
        var prevMatch = -2
        var consecutive = 0
        var wordBoundaryHits = 0

        for (ti in t.indices) {
            if (qi >= q.length) break
            if (t[ti] == q[qi]) {
                val isBoundary = ti == 0 || !rawT[ti - 1].isLetterOrDigit() ||
                    (rawT[ti].isUpperCase() && rawT[ti - 1].isLowerCase())
                if (firstMatch == -1) firstMatch = ti
                if (ti == prevMatch + 1) consecutive++
                if (isBoundary) wordBoundaryHits++
                prevMatch = ti
                lastMatch = ti
                qi++
            }
        }

        if (qi < q.length) return 0

        val span = (lastMatch - firstMatch) + 1
        // Reject overly sparse noise unless nearly every matched character lands on a word boundary
        val maxAllowedSpan = q.length * 3 + 3
        if (span > maxAllowedSpan && wordBoundaryHits < q.length - 1) {
            return 0
        }

        // For 2-char queries, require either adjacent chars or both on word boundaries or starting at 0
        if (q.length == 2 && consecutive == 0 && wordBoundaryHits < 2 && firstMatch > 0) {
            return 0
        }

        val compactnessPenalty = (span - q.length) * 14
        val startPenalty = firstMatch.coerceAtMost(20) * 3
        val score = 460 + (consecutive * 28) + (wordBoundaryHits * 24) - compactnessPenalty - startPenalty
        return score.coerceIn(320, 625)
    }

    private fun scoreTypoTolerance(q: String, t: String, words: List<String>): Int {
        val maxDist = when {
            q.length <= 4 -> 1
            else -> 2
        }

        var bestScore = 0

        // Check against full target (ignoring spaces if query is compact like "youtubemusic")
        val compactT = t.filter { it.isLetterOrDigit() }
        val candidates = ArrayList<Pair<String, Int>>(words.size + 2)
        candidates.add(t to 0)
        if (compactT != t && compactT.isNotEmpty()) {
            candidates.add(compactT to 0)
        }
        words.forEachIndexed { idx, w -> candidates.add(w to idx) }

        for ((cand, wordIdx) in candidates) {
            if (cand.isEmpty()) continue
            if (abs(cand.length - q.length) > maxDist) continue

            val allowedForPair = when {
                q.length == 3 -> 1
                q.length <= 5 -> if (q[0] == cand[0] && cand.length >= 5) 2 else 1
                else -> maxDist
            }

            val dist = damerauLevenshtein(q, cand, allowedForPair)
            if (dist > allowedForPair) continue

            // Guard short 3-char queries against noisy unrelated 1-char swaps
            if (q.length == 3 && q[0] != cand[0] && !isAdjacentTransposition(q, cand)) {
                continue
            }

            val firstCharBonus = if (q[0] == cand[0]) 45 else 0
            val wordPosBonus = if (wordIdx == 0) 20 else 0
            val score = (410 - (dist * 100) + firstCharBonus + wordPosBonus).coerceIn(200, 460)
            if (score > bestScore) bestScore = score
        }

        return bestScore
    }

    private fun isAdjacentTransposition(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diffIdx = -1
        for (i in a.indices) {
            if (a[i] != b[i]) {
                if (diffIdx == -1) {
                    diffIdx = i
                } else {
                    return i == diffIdx + 1 && a[diffIdx] == b[i] && a[i] == b[diffIdx] &&
                        a.substring(i + 1) == b.substring(i + 1)
                }
            }
        }
        return false
    }

    private fun damerauLevenshtein(a: String, b: String, maxDist: Int): Int {
        val n = a.length
        val m = b.length
        if (abs(n - m) > maxDist) return maxDist + 1

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, dp[i - 2][j - 2] + cost)
                }
                dp[i][j] = v
                if (v < rowMin) rowMin = v
            }
            // Note: do not early-abort rowMin blindly across transpositions without +1 margin,
            // only abort if strictly > maxDist + 1
            if (rowMin > maxDist + 1) return maxDist + 1
        }
        return dp[n][m]
    }
}
