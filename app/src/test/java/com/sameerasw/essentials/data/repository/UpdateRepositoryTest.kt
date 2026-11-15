package com.sameerasw.essentials.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    private val repository = UpdateRepository()

    @Test
    fun sameVersionWithKomorebiSuffixDoesNotTriggerUpdate() {
        assertFalse(repository.isNewerVersion("21.3.4-komorebi", "21.3.4-komorebi"))
        assertFalse(repository.isNewerVersion("21.3.4-komorebi", "21.3.4"))
        assertFalse(repository.isNewerVersion("21.3.4", "21.3.4-komorebi"))
    }

    @Test
    fun newerKomorebiVersionTriggersUpdate() {
        assertTrue(repository.isNewerVersion("21.3.4-komorebi", "21.3.5-komorebi"))
        assertTrue(repository.isNewerVersion("21.3.4-komorebi", "21.4-komorebi"))
        assertTrue(repository.isNewerVersion("21.3.4-komorebi", "22.0.0"))
        assertFalse(repository.isNewerVersion("21.3.4-komorebi", "21.3-beta.1"))
    }

    @Test
    fun preReleaseOrderingWorksWithKomorebiFlavorAndNumericSegments() {
        assertTrue(repository.isNewerVersion("21.3.4-komorebi", "21.3.5-komorebi-beta.1"))
        assertTrue(repository.isNewerVersion("21.3.5-komorebi-beta.1", "21.3.5-komorebi"))
        assertFalse(repository.isNewerVersion("21.3.5-komorebi", "21.3.5-komorebi-beta.1"))
        assertTrue(repository.isNewerVersion("21.3.5-beta.2", "21.3.5-beta.10"))
        assertTrue(repository.isNewerVersion("21.3.5-komorebi-beta.2", "21.3.5-komorebi-beta.10"))
    }

    @Test
    fun normalizeVersionTagStripsPrefixes() {
        assertEquals("21.3.5-komorebi", repository.normalizeVersionTag("v21.3.5-komorebi"))
        assertEquals("21.3.5-komorebi", repository.normalizeVersionTag("V21.3.5-komorebi"))
        assertEquals("21.3.5", repository.normalizeVersionTag("komorebi-v21.3.5"))
    }

    @Test
    fun parseLatestReleaseJsonSkipsUnsignedApkAndDetectsUpdate() {
        val json =
            """
            {
              "tag_name": "v21.3.5-komorebi",
              "draft": false,
              "prerelease": false,
              "body": "Bug fixes and improvements",
              "html_url": "https://github.com/aislingheart/komorebi/releases/tag/v21.3.5-komorebi",
              "assets": [
                {
                  "name": "komorebi-v21.3.5-unsigned.apk",
                  "browser_download_url": "https://example.com/unsigned.apk"
                },
                {
                  "name": "komorebi-v21.3.5-release.apk",
                  "browser_download_url": "https://example.com/signed.apk"
                }
              ]
            }
            """.trimIndent()

        val info =
            repository.parseUpdateInfoFromJson(
                releaseData = json,
                isPreReleaseCheckEnabled = false,
                currentVersion = "21.3.4-komorebi",
            )

        assertNotNull(info)
        assertEquals("21.3.5-komorebi", info?.versionName)
        assertEquals("https://example.com/signed.apk", info?.downloadUrl)
        assertTrue(info?.isUpdateAvailable == true)
    }

    @Test
    fun parseReleasesListIgnoresDraftAndSelectsHighestVersion() {
        val json =
            """
            [
              {
                "tag_name": "v21.4.0-komorebi",
                "draft": true,
                "prerelease": false,
                "body": "Draft release",
                "html_url": "https://github.com/aislingheart/komorebi/releases/tag/v21.4.0-komorebi",
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://example.com/draft.apk"
                  }
                ]
              },
              {
                "tag_name": "v21.3.5-komorebi-beta.1",
                "draft": false,
                "prerelease": true,
                "body": "Beta 1",
                "html_url": "https://github.com/aislingheart/komorebi/releases/tag/v21.3.5-komorebi-beta.1",
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://example.com/beta1.apk"
                  }
                ]
              },
              {
                "tag_name": "v21.3.4-komorebi",
                "draft": false,
                "prerelease": false,
                "body": "Stable",
                "html_url": "https://github.com/aislingheart/komorebi/releases/tag/v21.3.4-komorebi",
                "assets": []
              }
            ]
            """.trimIndent()

        val info =
            repository.parseUpdateInfoFromJson(
                releaseData = json,
                isPreReleaseCheckEnabled = true,
                currentVersion = "21.3.4-komorebi",
            )

        assertNotNull(info)
        assertEquals("21.3.5-komorebi-beta.1", info?.versionName)
        assertEquals("https://example.com/beta1.apk", info?.downloadUrl)
        assertTrue(info?.isUpdateAvailable == true)
    }

    @Test
    fun parseLatestReleaseReturnsNullForDraft() {
        val json =
            """
            {
              "tag_name": "v21.3.5-komorebi",
              "draft": true,
              "prerelease": false,
              "body": "",
              "html_url": "",
              "assets": []
            }
            """.trimIndent()

        val info =
            repository.parseUpdateInfoFromJson(
                releaseData = json,
                isPreReleaseCheckEnabled = false,
                currentVersion = "21.3.4-komorebi",
            )

        assertNull(info)
    }
}
