package com.sameerasw.essentials.raycast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RaycastManifestTest {

    @Test
    fun `fresh root installs all built-in starters with non-empty files`() {
        val root = createTempDirectory("komorebi_raycast_fresh").toFile()
        try {
            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)

            val marker = File(root, ".starters_v2")
            assertTrue(marker.isFile && marker.length() > 0L)

            listOf(
                "komorebi-quick-notes/package.json",
                "komorebi-quick-notes/browse-notes.js",
                "komorebi-quick-notes/create-note.js",
                "komorebi-color-studio/package.json",
                "komorebi-color-studio/palette.js",
                "komorebi-dev-toolkit/package.json",
                "komorebi-dev-toolkit/toolkit.js",
            ).forEach { rel ->
                val f = File(root, rel)
                assertTrue("Expected non-empty $rel", f.isFile && f.length() > 0L)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `zero-byte marker and truncated starter files are healed`() {
        val root = createTempDirectory("komorebi_raycast_heal").toFile()
        try {
            File(root, ".starters_v2").writeText("")
            val notesDir = File(root, "komorebi-quick-notes").apply { mkdirs() }
            File(notesDir, "package.json").writeText("")
            File(notesDir, "browse-notes.js").writeText("")

            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)

            assertTrue(File(root, ".starters_v2").length() > 0L)
            assertTrue(File(notesDir, "package.json").length() > 0L)
            assertTrue(File(notesDir, "browse-notes.js").length() > 0L)
            assertTrue(File(notesDir, "create-note.js").length() > 0L)
            assertTrue(File(root, "komorebi-color-studio/package.json").length() > 0L)
            assertTrue(File(root, "komorebi-dev-toolkit/package.json").length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `uninstalled starter stays uninstalled when marker is intact`() {
        val root = createTempDirectory("komorebi_raycast_uninstall").toFile()
        try {
            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)
            val colorDir = File(root, "komorebi-color-studio")
            assertTrue(colorDir.deleteRecursively())

            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)

            assertFalse(colorDir.exists())
            assertTrue(File(root, "komorebi-quick-notes/package.json").length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }
}
