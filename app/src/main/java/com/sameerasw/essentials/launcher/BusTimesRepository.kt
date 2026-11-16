/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Tools
 * File: BusTimesRepository.kt
 * Description: Finds nearby bus stops from OpenStreetMap and live departures from bustimes.org.
 */

package com.sameerasw.essentials.launcher

import android.net.Uri
import com.sameerasw.essentials.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyBusStop(
    val id: String,
    val name: String,
    val stopCode: String?,
    val website: String?,
    val distanceMeters: Int,
    val latitude: Double,
    val longitude: Double,
)

data class BusDeparture(
    val route: String,
    val destination: String,
    val scheduled: String,
    val expected: String,
) {
    val isLive: Boolean get() = expected.isNotBlank() && expected != scheduled
}

object BusTimesRepository {

    private val USER_AGENT = "Komorebi/${BuildConfig.VERSION_NAME} (Android launcher; nearby bus times)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun findNearbyStops(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 900,
    ): List<NearbyBusStop> = withContext(Dispatchers.IO) {
        val latDelta = radiusMeters / 111_320.0
        val lonDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
        val viewBox = String.format(
            Locale.US,
            "%.6f,%.6f,%.6f,%.6f",
            longitude - lonDelta,
            latitude + latDelta,
            longitude + lonDelta,
            latitude - latDelta,
        )
        val url = "https://nominatim.openstreetmap.org/search" +
            "?format=jsonv2&q=bus%20stop&limit=24&bounded=1&extratags=1" +
            "&viewbox=$viewBox"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body.string().orEmpty()
            val array = runCatching { JSONArray(body) }.getOrNull() ?: return@withContext emptyList()
            val stops = mutableListOf<NearbyBusStop>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val extra = obj.optJSONObject("extratags")
                val type = obj.optString("type")
                val category = obj.optString("category")
                val isBusStop = (category == "highway" && type == "bus_stop") ||
                    (category == "amenity" && type == "bus_station") ||
                    extra?.optString("highway") == "bus_stop" ||
                    extra?.optString("amenity") == "bus_station"
                if (!isBusStop) continue

                val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                val website = extra?.optString("website").orEmpty()
                val stopCode = sequenceOf(
                    Uri.parse(website).getQueryParameter("stopId"),
                    extra?.optString("naptan:AtcoCode"),
                    extra?.optString("ref")?.takeIf { it.length >= 5 },
                ).firstOrNull { !it.isNullOrBlank() }

                val name = obj.optString("name").takeIf { it.isNotBlank() }
                    ?: obj.optJSONObject("address")?.optString("road").orEmpty()
                if (name.isBlank()) continue

                val distance = haversineMeters(latitude, longitude, lat, lon).toInt()
                stops.add(
                    NearbyBusStop(
                        id = "${obj.optString("osm_type")}:${obj.optString("osm_id")}",
                        name = name,
                        stopCode = stopCode,
                        website = website.takeIf { it.isNotBlank() },
                        distanceMeters = distance,
                        latitude = lat,
                        longitude = lon,
                    ),
                )
            }
            stops.distinctBy { it.id }
                .sortedBy { it.distanceMeters }
                .take(12)
        }
    }

    suspend fun departures(stopCode: String): List<BusDeparture> = withContext(Dispatchers.IO) {
        if (stopCode.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url("https://bustimes.org/stops/${Uri.encode(stopCode)}/departures")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body.string().orEmpty()
            val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return@withContext emptyList()
            doc.select("#departures tbody tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 3) return@mapNotNull null
                val route = cells[0].text().trim()
                if (route.isBlank()) return@mapNotNull null
                val destinationCell = cells[1]
                destinationCell.selectFirst(".vehicle")?.remove()
                val destination = destinationCell.text().trim()
                val scheduled = cells.getOrNull(2)?.text()?.trim().orEmpty()
                val expected = cells.getOrNull(3)?.text()?.trim().orEmpty()
                BusDeparture(
                    route = route,
                    destination = destination,
                    scheduled = scheduled,
                    expected = expected,
                )
            }.take(14)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
