package com.sameerasw.essentials.raycast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomorebiStoreListingTest {

    @Test
    fun parsesKomorebiIndex() {
        val json = """
            {
              "extensions": [
                {
                  "id": "time-tools",
                  "name": "komorebi-time-tools",
                  "title": "Time Tools",
                  "description": "World clocks",
                  "author": {"name": "Komorebi", "handle": "komorebi"},
                  "downloadUrl": "https://raw.githubusercontent.com/aislingheart/komorebi-extensions/main/dist/time-tools.zip",
                  "categories": ["Productivity"],
                  "platforms": ["android"],
                  "commands": [
                    {"id": "world-clock", "name": "world-clock", "title": "World Clock", "mode": "view", "keywords": ["time"]}
                  ]
                }
              ]
            }
        """.trimIndent()

        val listings = RaycastStoreClient.parseKomorebiListingsResponse(json)
        assertEquals(1, listings.size)
        val listing = listings.first()
        assertEquals("komorebi", listing.source)
        assertEquals("time-tools", listing.id)
        assertEquals("Komorebi", listing.author.name)
        assertTrue(listing.downloadUrl.orEmpty().endsWith("time-tools.zip"))
        assertEquals("world-clock", listing.commands.first().name)
    }
}
