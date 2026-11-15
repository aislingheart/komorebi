package com.sameerasw.essentials.raycast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RaycastCompatibilityAndStoreTest {

    @Test
    fun `vicinae parser handles object categories, string categories, and nullable fields`() {
        val json = """
            {
              "extensions": [
                {
                  "id": "ext-1",
                  "name": "github-search",
                  "title": "GitHub Search",
                  "description": "Search GitHub repositories",
                  "downloadUrl": "https://api.vicinae.com/dl/github.tar.gz",
                  "downloadCount": 1234,
                  "author": {
                    "name": "Alice",
                    "handle": "alice",
                    "avatarUrl": "https://example.com/avatar.png",
                    "profileUrl": "https://example.com/alice"
                  },
                  "icons": {
                    "light": "https://example.com/light.png",
                    "dark": "https://example.com/dark.png"
                  },
                  "platforms": ["macOS", "Linux"],
                  "categories": [
                    { "id": "dev-tools", "name": "Developer Tools" },
                    { "id": "productivity", "name": "" }
                  ],
                  "commands": [
                    {
                      "id": "cmd-1",
                      "name": "search",
                      "title": "Search Repos",
                      "subtitle": "GitHub",
                      "description": "Find repos",
                      "mode": "view",
                      "keywords": ["git", "repo"],
                      "disabledByDefault": true,
                      "beta": true
                    }
                  ]
                },
                {
                  "id": "ext-2",
                  "name": "plain-cats",
                  "title": "Plain Cats",
                  "description": null,
                  "downloadUrl": null,
                  "downloadCount": null,
                  "author": null,
                  "icons": null,
                  "platforms": null,
                  "categories": ["Utilities", "Web"],
                  "commands": null
                }
              ]
            }
        """.trimIndent()

        val listings = RaycastStoreClient.parseVicinaeListingsResponse(json)
        assertEquals(2, listings.size)

        val first = listings[0]
        assertEquals("ext-1", first.id)
        assertEquals("github-search", first.name)
        assertEquals("GitHub Search", first.title)
        assertEquals("vicinae", first.source)
        assertEquals(1234, first.downloadCount)
        assertEquals("https://api.vicinae.com/dl/github.tar.gz", first.downloadUrl)
        assertEquals("Alice", first.author.name)
        assertEquals("alice", first.author.handle)
        assertEquals("https://example.com/avatar.png", first.author.avatar)
        assertEquals("https://example.com/alice", first.author.profileUrl)
        assertEquals("https://example.com/dark.png", first.iconUrl)
        assertEquals(listOf("macOS", "Linux"), first.platforms)
        assertEquals(listOf("Developer Tools", "productivity"), first.categories)
        assertEquals(1, first.commands.size)
        assertEquals(listOf("git", "repo"), first.commands[0].keywords)
        assertTrue(first.commands[0].disabledByDefault)
        assertTrue(first.commands[0].beta)

        val second = listings[1]
        assertEquals("ext-2", second.id)
        assertEquals("plain-cats", second.name)
        assertEquals("", second.description)
        assertEquals(0, second.downloadCount)
        assertEquals(null, second.downloadUrl)
        assertEquals("", second.author.name)
        assertEquals(null, second.iconUrl)
        assertEquals(emptyList<String>(), second.platforms)
        assertEquals(listOf("Utilities", "Web"), second.categories)
        assertEquals(emptyList<StoreListingCommand>(), second.commands)
    }

    @Test
    fun `raycast store parser extracts platforms and api_version safely`() {
        val json = """
            {
              "data": [
                {
                  "id": "rc-1",
                  "name": "speedtest",
                  "title": "Speedtest",
                  "description": "Measure internet speed with Ookla Speedtest CLI",
                  "download_count": 9000,
                  "download_url": "https://raycast.com/dl/speedtest.zip",
                  "platforms": ["macOS", "Windows"],
                  "api_version": "1.82.0",
                  "categories": ["Internet"],
                  "commands": [
                    { "name": "run", "title": "Run Speedtest", "mode": "view" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val listings = RaycastStoreClient.parseRaycastListingsResponse(json)
        assertEquals(1, listings.size)
        val item = listings[0]
        assertEquals(listOf("macOS", "Windows"), item.platforms)
        assertEquals("1.82.0", item.apiVersion)
        assertEquals("raycast", item.source)
        assertEquals(9000, item.downloadCount)
    }

    @Test
    fun `analyzer marks speedtest-like native extension as INCOMPATIBLE with exact issues`() {
        val dir = createTempDirectory("komorebi_compat_speedtest").toFile()
        try {
            File(dir, "index.js").writeText(
                """
                const { spawn } = require("child_process");
                const fs = require("fs");
                const URL = "https://install.speedtest.net/app/cli/ookla-speedtest-1.2.0-macosx-universal.tgz";
                function run() {
                  if (process.platform !== "darwin") {
                    throw new Error("Unsupported platform: " + process.platform);
                  }
                  fs.chmodSync("/tmp/speedtest", 0o755);
                  return spawn("/tmp/speedtest", ["--format=json"]);
                }
                """.trimIndent(),
            )
            val ext = InstalledRaycastExtension(
                id = "speedtest",
                dirPath = dir.absolutePath,
                manifest = RaycastPackageManifest(
                    name = "speedtest",
                    title = "Speedtest",
                    platforms = listOf("macOS"),
                    commands = listOf(RaycastCommandManifest(name = "index", title = "Speedtest")),
                ),
            )

            val report = RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
            assertEquals(AndroidCompatLevel.INCOMPATIBLE, report.level)

            val codes = report.issues.map { it.code }.toSet()
            assertTrue("Expected NATIVE_PROCESS in $codes", "NATIVE_PROCESS" in codes)
            assertTrue("Expected DESKTOP_BINARY_DOWNLOAD in $codes", "DESKTOP_BINARY_DOWNLOAD" in codes)
            assertTrue("Expected PLATFORM_CHECK in $codes", "PLATFORM_CHECK" in codes)
            assertTrue("Expected EXECUTABLE_CHMOD in $codes", "EXECUTABLE_CHMOD" in codes)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `analyzer marks pure JS fetch and React extension as COMPATIBLE`() {
        val dir = createTempDirectory("komorebi_compat_pure").toFile()
        try {
            File(dir, "search.js").writeText(
                """
                const React = require("react");
                const { List, ActionPanel, Action, LocalStorage } = require("@raycast/api");
                module.exports = function SearchRepos() {
                  const [items, setItems] = React.useState([]);
                  React.useEffect(() => {
                    const m = /repo:([a-z]+)/.exec("repo:komorebi");
                    fetch("https://api.github.com/search/repositories?q=" + (m ? m[1] : "raycast"))
                      .then((r) => r.json())
                      .then((d) => setItems(d.items || []));
                  }, []);
                  return React.createElement(List, null,
                    items.map((x) => React.createElement(List.Item, { key: x.id, title: x.name }))
                  );
                };
                """.trimIndent(),
            )
            val ext = InstalledRaycastExtension(
                id = "pure-fetch",
                dirPath = dir.absolutePath,
                manifest = RaycastPackageManifest(
                    name = "pure-fetch",
                    title = "Pure Fetch",
                    commands = listOf(RaycastCommandManifest(name = "search", title = "Search")),
                ),
            )

            val report = RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
            assertEquals(AndroidCompatLevel.COMPATIBLE, report.level)
            assertTrue(report.issues.isEmpty())
            assertTrue("@raycast/api" in report.detectedApis)
            assertTrue("fetch" in report.detectedApis)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `analyzer marks MenuBarExtra extension as PARTIAL`() {
        val dir = createTempDirectory("komorebi_compat_menubar").toFile()
        try {
            File(dir, "menu.js").writeText(
                """
                const React = require("react");
                const { MenuBarExtra } = require("@raycast/api");
                module.exports = function StatusBar() {
                  return React.createElement(MenuBarExtra, { title: "Status" },
                    React.createElement(MenuBarExtra.Item, { title: "Online" })
                  );
                };
                """.trimIndent(),
            )
            val ext = InstalledRaycastExtension(
                id = "menu-bar-ext",
                dirPath = dir.absolutePath,
                manifest = RaycastPackageManifest(
                    name = "menu-bar-ext",
                    title = "Status Menu",
                    commands = listOf(RaycastCommandManifest(name = "menu", title = "Status", mode = "menu-bar")),
                ),
            )

            val report = RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
            assertEquals(AndroidCompatLevel.PARTIAL, report.level)
            assertTrue(report.issues.any { it.code == "MENU_BAR" && it.severity == AndroidCompatLevel.PARTIAL })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `store listing and error analysis produce accurate severity and diagnostics`() {
        val desktopOnlyPure = StoreExtensionListing(
            name = "hn-reader",
            title = "Hacker News",
            description = "Read top Hacker News stories",
            platforms = listOf("macOS"),
        )
        assertEquals(AndroidCompatLevel.PARTIAL, RaycastCompatibilityAnalyzer.analyzeStoreListing(desktopOnlyPure).level)

        val appleScriptListing = StoreExtensionListing(
            name = "finder-tools",
            title = "Finder Tools",
            description = "Automate Reveal in Finder with AppleScript",
            platforms = listOf("macOS"),
        )
        val appleReport = RaycastCompatibilityAnalyzer.analyzeStoreListing(appleScriptListing)
        assertEquals(AndroidCompatLevel.INCOMPATIBLE, appleReport.level)
        assertTrue(appleReport.issues.any { it.code == "APPLESCRIPT" })
        assertTrue(appleReport.issues.any { it.code == "FINDER" })

        val missingModuleReport = RaycastCompatibilityAnalyzer.analyzeError("Error: Cannot find module 'lodash'")
        assertEquals(AndroidCompatLevel.INCOMPATIBLE, missingModuleReport.level)
        assertTrue(missingModuleReport.issues.any { it.code == "MISSING_MODULE" })

        val authErrorReport = RaycastCompatibilityAnalyzer.analyzeError("HTTP 401 Unauthorized: Invalid API key")
        assertEquals(AndroidCompatLevel.PARTIAL, authErrorReport.level)
        assertTrue(authErrorReport.issues.any { it.code == "AUTH_OR_PREFERENCE" })
    }

    @Test
    fun `entryFileForCommand resolves cjs, build, out, and manifest main fallbacks`() {
        val dir = createTempDirectory("komorebi_entry_paths").toFile()
        try {
            val buildDir = File(dir, "build").apply { mkdirs() }
            val cmdFile = File(buildDir, "lookup.js").apply { writeText("module.exports = () => null;") }

            val ext1 = InstalledRaycastExtension(
                id = "ext1",
                dirPath = dir.absolutePath,
                manifest = RaycastPackageManifest(name = "ext1"),
            )
            assertEquals(cmdFile.absolutePath, ext1.entryFileForCommand("lookup")?.absolutePath)

            val customMain = File(dir, "bundle.cjs").apply { writeText("module.exports = () => null;") }
            val ext2 = InstalledRaycastExtension(
                id = "ext2",
                dirPath = dir.absolutePath,
                manifest = RaycastPackageManifest(name = "ext2", main = "./bundle.cjs"),
            )
            val resolved = ext2.entryFileForCommand("missing-cmd")
            assertNotNull(resolved)
            assertEquals(customMain.absolutePath, resolved?.absolutePath)
        } finally {
            dir.deleteRecursively()
        }
    }
}
