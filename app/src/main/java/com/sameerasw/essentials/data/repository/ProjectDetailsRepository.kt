/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Data & Repository Layer
 * File: ProjectDetailsRepository.kt
 * Description: Repository to fetch and cache project details from sameerasw.com.
 */

package com.sameerasw.essentials.data.repository

import android.content.Context
import com.google.gson.Gson
import com.sameerasw.essentials.domain.model.github.ProjectDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ProjectDetailsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun getProjectDetails(projectId: String = "komorebi"): ProjectDetails? =
        withContext(Dispatchers.IO) {
            val cachedJson = prefs.getString(KEY_PROJECT_DETAILS_CACHE, null)
            val lastFetchTime = prefs.getLong(KEY_PROJECT_DETAILS_LAST_FETCH, 0L)
            val now = System.currentTimeMillis()

            val isCacheStale = (now - lastFetchTime) > CACHE_EXPIRATION_MS

            if (!cachedJson.isNullOrEmpty() && !isCacheStale) {
                try {
                    val cached: ProjectDetails? = gson.fromJson(cachedJson, ProjectDetails::class.java)
                    if (cached != null) {
                        return@withContext cached
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fetch remote project details from GitHub API
            try {
                val url = URL(GITHUB_REPO_URL)
                val connection =
                    (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "Komorebi-Android")
                    }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val details =
                        ProjectDetails(
                            id = projectId,
                            repo = json.optString("full_name", "aislingheart/komorebi"),
                            stars = json.optInt("stargazers_count", 0),
                        )

                    prefs.edit()
                        .putString(KEY_PROJECT_DETAILS_CACHE, gson.toJson(details))
                        .putLong(KEY_PROJECT_DETAILS_LAST_FETCH, now)
                        .apply()
                    return@withContext details
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback to cache if available
            if (!cachedJson.isNullOrEmpty()) {
                try {
                    return@withContext gson.fromJson(cachedJson, ProjectDetails::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            null
        }

    companion object {
        private const val PREFS_NAME = "project_details_prefs"
        private const val KEY_PROJECT_DETAILS_CACHE = "komorebi_project_details_cache"
        private const val KEY_PROJECT_DETAILS_LAST_FETCH = "komorebi_project_details_last_fetch"
        private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val GITHUB_REPO_URL = "https://api.github.com/repos/aislingheart/komorebi"

        @Volatile
        private var instance: ProjectDetailsRepository? = null

        fun getInstance(context: Context): ProjectDetailsRepository {
            return instance ?: synchronized(this) {
                instance ?: ProjectDetailsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
