/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Engine
 * File: NavigationApps.kt
 * Description: Detects installed navigation and transit apps and builds route options.
 */

package com.sameerasw.essentials.search

import android.content.Context
import android.content.Intent
import android.net.Uri

object NavigationApps {

    private data class AppSpec(
        val packageName: String,
        val id: String,
        val label: String,
        val buildUri: (String) -> String,
    )

    private val drivingApps = listOf(
        AppSpec(
            packageName = "com.google.android.apps.maps",
            id = "google_maps",
            label = "Google Maps",
            buildUri = { encoded -> "google.navigation:q=$encoded" },
        ),
        AppSpec(
            packageName = "com.waze",
            id = "waze",
            label = "Waze",
            buildUri = { encoded -> "waze://?q=$encoded&navigate=yes" },
        ),
        AppSpec(
            packageName = "com.citymapper.app.release",
            id = "citymapper",
            label = "Citymapper",
            buildUri = { encoded -> "citymapper://directions?endaddress=$encoded" },
        ),
        AppSpec(
            packageName = "com.thetransitapp.droid",
            id = "transit",
            label = "Transit",
            buildUri = { encoded -> "transit://directions?to=$encoded" },
        ),
        AppSpec(
            packageName = "com.tranzmate",
            id = "moovit",
            label = "Moovit",
            buildUri = { encoded -> "moovit://directions?dest_name=$encoded" },
        ),
        AppSpec(
            packageName = "app.organicmaps",
            id = "organic_maps",
            label = "Organic Maps",
            buildUri = { encoded -> "geo:0,0?q=$encoded" },
        ),
        AppSpec(
            packageName = "net.osmand",
            id = "osmand",
            label = "OsmAnd",
            buildUri = { encoded -> "geo:0,0?q=$encoded" },
        ),
    )

    fun routeOptions(context: Context, destination: String): List<EntityNavigationOption> {
        val encoded = Uri.encode(destination.trim())
        if (encoded.isEmpty()) return emptyList()

        val installedOptions = drivingApps.mapNotNull { spec ->
            if (!isInstalled(context, spec.packageName)) return@mapNotNull null
            EntityNavigationOption(
                id = spec.id,
                label = spec.label,
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(spec.buildUri(encoded))).apply {
                    setPackage(spec.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.toMutableList()

        if (isInstalled(context, "com.google.android.apps.maps")) {
            installedOptions.add(
                1,
                EntityNavigationOption(
                    id = "google_transit",
                    label = "Google Transit",
                    intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=transit"),
                    ).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                ),
            )
        }

        if (installedOptions.isEmpty()) {
            installedOptions.add(
                EntityNavigationOption(
                    id = "google_maps_web",
                    label = "Google Maps",
                    intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                ),
            )
        }

        return installedOptions.distinctBy { it.id }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
