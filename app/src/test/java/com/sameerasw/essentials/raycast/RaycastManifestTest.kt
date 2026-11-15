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

            val marker = File(root, ".starters_v1")
            assertTrue(marker.isFile && marker.length() > 0L)

            listOf(
                "essentials-quick-notes/package.json",
                "essentials-quick-notes/browse-notes.js",
                "essentials-quick-notes/create-note.js",
                "essentials-color-studio/package.json",
                "essentials-color-studio/palette.js",
                "essentials-dev-toolkit/package.json",
                "essentials-dev-toolkit/toolkit.js",
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
            File(root, ".starters_v1").writeText("")
            val notesDir = File(root, "essentials-quick-notes").apply { mkdirs() }
            File(notesDir, "package.json").writeText("")
            File(notesDir, "browse-notes.js").writeText("")

            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)

            assertTrue(File(root, ".starters_v1").length() > 0L)
            assertTrue(File(notesDir, "package.json").length() > 0L)
            assertTrue(File(notesDir, "browse-notes.js").length() > 0L)
            assertTrue(File(notesDir, "create-note.js").length() > 0L)
            assertTrue(File(root, "essentials-color-studio/package.json").length() > 0L)
            assertTrue(File(root, "essentials-dev-toolkit/package.json").length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `uninstalled starter stays uninstalled when marker is intact`() {
        val root = createTempDirectory("komorebi_raycast_uninstall").toFile()
        try {
            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)
            val colorDir = File(root, "essentials-color-studio")
            assertTrue(colorDir.deleteRecursively())

            RaycastExtensionRepository.ensureStarterExtensionsInDir(root)

            assertFalse(colorDir.exists())
            assertTrue(File(root, "essentials-quick-notes/package.json").length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }
}
