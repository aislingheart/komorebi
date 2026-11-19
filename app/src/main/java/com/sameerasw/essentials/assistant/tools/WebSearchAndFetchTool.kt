/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: WebSearchAndFetchTool.kt
 * Description: Web search and page fetch tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object AssistantWebSettings {
    private const val PREFS_NAME = "komorebi_assistant_web"
    private const val KEY_SEARXNG_URL = "searxng_url"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSearxngUrl(context: Context): String =
        prefs(context).getString(KEY_SEARXNG_URL, null)?.trim().orEmpty()

    fun setSearxngUrl(context: Context, url: String) {
        val value = url.trim()
        if (value.isEmpty()) {
            prefs(context).edit().remove(KEY_SEARXNG_URL).apply()
        } else {
            prefs(context).edit().putString(KEY_SEARXNG_URL, value).apply()
        }
    }
}

object WebSearchAndFetchTool {
    private const val USER_AGENT = "Komorebi-Assistant/1.0"
    private const val MAX_SEARCH_CHARS = 4000
    private const val MAX_FETCH_CHARS = 6000

    private const val WEB_SEARCH_SCHEMA =
        """{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}"""

    private const val FETCH_URL_SCHEMA =
        """{"type":"object","properties":{"url":{"type":"string","description":"HTTP or HTTPS URL to fetch"}},"required":["url"]}"""

    private const val DDG_ENDPOINT = "https://api.duckduckgo.com/"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val COMMENT_REGEX = Regex("(?s)<!--.*?-->")
    private val SCRIPT_STYLE_REGEX = Regex(
        "(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>|<noscript[^>]*>.*?</noscript>",
    )
    private val TAG_REGEX = Regex("(?s)<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "web_search",
            title = "Web Search",
            description = "Search the web using a configured SearXNG instance or DuckDuckGo",
            keywords = listOf("web search", "search the web", "look up", "find online", "internet search", "google"),
            parametersJson = WEB_SEARCH_SCHEMA,
            execute = { context, args -> webSearch(context, args["query"].orEmpty()) },
        ),
        AssistantTool(
            id = "fetch_url",
            title = "Fetch URL",
            description = "Download a web page and return its readable text",
            keywords = listOf("fetch url", "open url", "read page", "website", "web page", "browse"),
            parametersJson = FETCH_URL_SCHEMA,
            execute = { _, args -> fetchUrl(args["url"].orEmpty()) },
        ),
    )

    private suspend fun webSearch(context: Context, query: String): ToolResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ToolResult(false, "A search query is required.")
        }
        return withContext(Dispatchers.IO) {
            val searxngUrl = AssistantWebSettings.getSearxngUrl(context)
            if (searxngUrl.isNotBlank()) {
                searchSearxng(searxngUrl, trimmed)
            } else {
                searchDuckDuckGo(trimmed)
            }
        }
    }

    private fun searchSearxng(baseUrl: String, query: String): ToolResult {
        val base = baseUrl.trim().trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint = if (base.endsWith("/search", ignoreCase = true)) {
            "$base?q=$encodedQuery&format=json"
        } else {
            "$base/search?q=$encodedQuery&format=json"
        }
        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ToolResult(false, "Search request failed with HTTP ${response.code}.")
                }
                val root = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    ?: return ToolResult(false, "The search response was not valid JSON.")
                val results = root.optJSONArray("results")
                    ?: return ToolResult(false, "No web results found for \"$query\".")

                val builder = StringBuilder()
                var count = 0
                for (index in 0 until results.length()) {
                    if (builder.length >= MAX_SEARCH_CHARS) break
                    val item = results.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    val content = item.optString("content").trim()
                    if (title.isBlank() && url.isBlank()) continue
                    count++
                    builder.append(count).append(". ").append(title.ifBlank { url }).append('\n')
                    if (url.isNotBlank()) builder.append(url).append('\n')
                    if (content.isNotBlank()) builder.append(content).append('\n')
                    builder.append('\n')
                }

                val text = builder.toString().trim().take(MAX_SEARCH_CHARS)
                if (text.isBlank()) {
                    ToolResult(false, "No web results found for \"$query\".")
                } else {
                    ToolResult(true, "Found $count result(s) for \"$query\".", text)
                }
            }
        }.getOrElse { error ->
            ToolResult(false, "Search failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun searchDuckDuckGo(query: String): ToolResult {
        val endpoint = DDG_ENDPOINT +
            "?q=" + URLEncoder.encode(query, "UTF-8") +
            "&format=json&no_html=1&skip_disambig=1"
        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ToolResult(false, "Search request failed with HTTP ${response.code}.")
                }
                val root = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    ?: return ToolResult(false, "The search response was not valid JSON.")

                val builder = StringBuilder()
                val abstract = root.optString("AbstractText").trim()
                if (abstract.isNotBlank()) {
                    builder.append(abstract).append("\n\n")
                }

                val related = root.optJSONArray("RelatedTopics")
                if (related != null) {
                    for (index in 0 until related.length()) {
                        if (builder.length >= MAX_SEARCH_CHARS) break
                        val entry = related.optJSONObject(index) ?: continue
                        val text = entry.optString("Text").trim()
                        val firstUrl = entry.optString("FirstURL").trim()
                        if (text.isBlank() && firstUrl.isBlank()) continue
                        if (text.isNotBlank()) builder.append("- ").append(text).append('\n')
                        if (firstUrl.isNotBlank()) builder.append(firstUrl).append('\n')
                    }
                }

                val text = builder.toString().trim().take(MAX_SEARCH_CHARS)
                if (text.isBlank()) {
                    ToolResult(
                        false,
                        "No instant answer for \"$query\". Configure a SearXNG URL for full web results.",
                    )
                } else {
                    ToolResult(true, "DuckDuckGo results for \"$query\".", text)
                }
            }
        }.getOrElse { error ->
            ToolResult(false, "Search failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private suspend fun fetchUrl(rawUrl: String): ToolResult = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext ToolResult(false, "A URL is required.")
        }
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.US)
        val host = uri?.host
        if (uri == null || (scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
            return@withContext ToolResult(false, "Only valid http and https URLs are supported.")
        }
        if (isPrivateOrLocalHost(host)) {
            return@withContext ToolResult(false, "Fetching local or private network URLs is not allowed.")
        }

        runCatching {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ToolResult(false, "Fetch failed with HTTP ${response.code}.")
                }
                val finalHost = response.request.url.host
                if (isPrivateOrLocalHost(finalHost)) {
                    return@withContext ToolResult(false, "Redirect to a local or private network address was blocked.")
                }
                val contentType = response.header("Content-Type")?.lowercase(Locale.US).orEmpty()
                if (contentType.isNotBlank() &&
                    !contentType.contains("text/") &&
                    !contentType.contains("html") &&
                    !contentType.contains("json") &&
                    !contentType.contains("xml")
                ) {
                    return@withContext ToolResult(false, "Unsupported content type: $contentType")
                }
                val rawBody = readCappedUtf8(response.body.byteStream(), maxBytes = 512 * 1024)
                val text = htmlToText(rawBody).take(MAX_FETCH_CHARS)
                if (text.isBlank()) {
                    ToolResult(false, "No readable text was found on the page.")
                } else {
                    ToolResult(true, "Fetched ${text.length} characters of readable text.", text)
                }
            }
        }.getOrElse { error ->
            ToolResult(false, "Fetch failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        val lower = host.lowercase(Locale.US)
        if (lower == "localhost" || lower.endsWith(".local") || lower.endsWith(".internal")) return true
        val addresses = runCatching { java.net.InetAddress.getAllByName(host) }.getOrNull() ?: return true
        return addresses.any { addr ->
            if (addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress
            ) {
                return@any true
            }
            val bytes = addr.address
            // IPv6 unique local (fc00::/7)
            if (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC) return@any true
            false
        }
    }

    private fun readCappedUtf8(stream: java.io.InputStream, maxBytes: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val read = stream.read(buf, 0, minOf(buf.size, maxBytes - total))
            if (read <= 0) break
            out.write(buf, 0, read)
            total += read
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun htmlToText(html: String): String {
        val stripped = html
            .replace(COMMENT_REGEX, " ")
            .replace(SCRIPT_STYLE_REGEX, " ")
            .replace(TAG_REGEX, " ")
        val decoded = runCatching {
            Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(stripped)
        return decoded
            .replace('\u00A0', ' ')
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }
}
