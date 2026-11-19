/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: ContactsSearchUtil.kt
 * Description: Cached contact lookup and context-aware contact ranking.
 */

package com.sameerasw.essentials.utils

import android.content.Context
import android.provider.ContactsContract
import com.sameerasw.essentials.domain.model.PixelSearchResultItem

object ContactsSearchUtil {

    fun loadAll(context: Context): List<PixelSearchResultItem.ContactItem> {
        val results = mutableListOf<PixelSearchResultItem.ContactItem>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            )
            val seen = HashSet<String>()
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    if (name.isBlank()) continue
                    val dedupeKey = "$id:${number.orEmpty()}"
                    if (!seen.add(dedupeKey)) continue
                    results.add(
                        PixelSearchResultItem.ContactItem(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            photoUri = photo,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun search(
        context: Context,
        contacts: List<PixelSearchResultItem.ContactItem>,
        query: String,
        limit: Int = 5,
    ): List<PixelSearchResultItem.ContactItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts.take(limit)
        val snapshot = SearchContextRanker.snapshot(context)
        val contextBoost = SearchContextRanker.boostForContact(snapshot)
        return contacts.mapNotNull { contact ->
            val baseScore = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = contact.name,
                secondary = listOfNotNull(contact.phoneNumber),
            )
            if (baseScore <= 0) {
                null
            } else {
                val usageBoost = SearchUsageTracker.boost(
                    context,
                    SearchUsageTracker.suggestionKey("contact", contact.id),
                )
                contact to (baseScore + usageBoost + contextBoost)
            }
        }.sortedWith(
            compareByDescending<Pair<PixelSearchResultItem.ContactItem, Int>> { it.second }
                .thenBy { it.first.name.lowercase() },
        ).take(limit).map { it.first }
    }
}
