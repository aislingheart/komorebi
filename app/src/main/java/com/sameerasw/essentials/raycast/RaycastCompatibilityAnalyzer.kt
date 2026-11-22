package com.sameerasw.essentials.raycast

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

enum class AndroidCompatLevel {
    COMPATIBLE,
    PARTIAL,
    INCOMPATIBLE,
}

data class CompatibilityIssue(
    val severity: AndroidCompatLevel,
    val code: String,
    val title: String,
    val detail: String,
)

data class CompatibilityReport(
    val level: AndroidCompatLevel,
    val summary: String,
    val issues: List<CompatibilityIssue>,
    val detectedApis: List<String> = emptyList(),
)

object RaycastCompatibilityAnalyzer {

    private const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private const val MAX_SCAN_FILES = 250
    private const val FS_HEAVY_CALL_THRESHOLD = 8

    // =========================================================================
    // 1. Runtime ground truth.
    //
    // Everything in this section is a hardcoded mirror of
    // app/src/main/assets/raycast/runtime.js. Keep in sync when runtime.js
    // changes; line references are given per table.
    // =========================================================================

    /**
     * The builtinModules table in runtime.js (~lines 4187-4280) plus the `node:`
     * alias loop (~line 4281) that re-registers every `node:` module under its
     * bare name (`require("fs")` and `require("node:fs")` are both valid).
     */
    private val BUILTIN_MODULES: Set<String> = buildSet {
        // Non-Node modules shipped inside the QuickJS runtime.
        addAll(
            listOf(
                "react", "react/jsx-runtime", "react/jsx-dev-runtime", "react-dom",
                "@raycast/api", "@vicinae/api", "@raycast/utils",
                "fs/promises", // explicit non-prefixed alias in the table
                "@komorebi/health",
                "dequal", "dequal/lite",
            ),
        )
        // node:-prefixed modules from the table; the alias loop also registers
        // each of these without the "node:" prefix.
        listOf(
            "crypto", "path", "url", "buffer", "timers", "timers/promises", "os", "fs",
            "fs/promises", "constants", "http", "https", "net", "tls", "zlib", "tty",
            "worker_threads", "http2", "v8", "events", "process", "util", "querystring",
            "stream", "stream/promises", "stream/web", "async_hooks", "diagnostics_channel",
            "string_decoder", "util/types", "child_process", "perf_hooks", "assert", "module",
        ).forEach {
            add("node:$it")
            add(it)
        }
    }

    /**
     * knownNodeBuiltins in runtime.js (~line 4321): modules Node itself ships but
     * the Android QuickJS runtime does NOT provide. Used to enrich the
     * missing-module hint, mirroring the runtime's "Cannot find module" message.
     */
    private val KNOWN_NODE_BUILTINS = setOf(
        "assert", "async_hooks", "buffer", "child_process", "cluster", "console", "constants",
        "crypto", "dgram", "diagnostics_channel", "dns", "domain", "events", "fs", "http",
        "http2", "https", "inspector", "module", "net", "os", "path", "perf_hooks", "process",
        "punycode", "querystring", "readline", "repl", "stream", "string_decoder", "sys",
        "timers", "tls", "trace_events", "tty", "url", "util", "v8", "vm", "wasi",
        "worker_threads", "zlib", "test", "sea", "sqlite",
    )

    /**
     * The @raycast/api export surface: the `raycastApi` table in runtime.js
     * (~lines 2567-2576). Destructuring a name not in this set yields undefined
     * at runtime and fails on first use.
     */
    private val RAYCAST_API_EXPORTS = setOf(
        "AI", "Action", "ActionPanel", "Alert", "BrowserExtension", "Cache", "Clipboard",
        "Color", "Detail", "Dropdown", "Environment", "environment", "Form", "Grid", "Icon",
        "Image", "Keyboard", "LaunchType", "List", "LocalStorage", "MenuBarExtra", "OAuth",
        "PopToRootType", "Toast", "WindowManagement", "captureException", "clearSearchBar",
        "closeMainWindow", "confirmAlert", "getApplications", "getDefaultApplication",
        "getFrontmostApplication", "getPreferenceValues", "getSelectedFinderItems",
        "getSelectedText", "launchCommand", "open", "openCommandPreferences",
        "openExtensionPreferences", "popToRoot", "showHUD", "showInFileBrowser",
        "showInFinder", "showToast", "trash", "updateCommandMetadata", "useNavigation",
    )

    /**
     * VirtShell: the CLI emulator command set landing around
     * cliCommandFromFile (runtime.js ~line 3380). Commands in this set are
     * executed by the in-runtime emulator — fully offline, no desktop binaries.
     * (osascript/defaults are excluded here; see DESKTOP_ONLY_COMMANDS.)
     */
    private val CLI_EMULATED_COMMANDS = setOf(
        "echo", "printf", "cat", "ls", "pwd", "which", "true", "false", "sleep", "date",
        "basename", "dirname", "env", "printenv", "head", "tail", "wc", "grep", "sort",
        "uniq", "tr", "base64", "mkdir", "rm", "touch", "cp", "mv", "hostname", "whoami",
        "id", "uname", "open", "pbcopy", "pbpaste", "sw_vers", "xdg-open", "xclip", "xsel",
        "start", "clip", "explorer", "curl", "wget", "git", "node",
    )

    /** Commands that are desktop-only regardless of emulator coverage. */
    private val DESKTOP_ONLY_COMMANDS = setOf(
        "osascript", "brew", "op", "code", "sqlite3", "yt-dlp", "ffmpeg", "qlmanage",
        "lsof", "netstat", "pmset", "defaults", "caffeinate", "mdfind",
    )

    // =========================================================================
    // 2. Store-listing keyword rules (metadata-only pass).
    // =========================================================================

    private data class KeywordRule(
        val code: String,
        val severity: AndroidCompatLevel,
        val title: String,
        val detail: String,
        val regex: Regex,
        val apiTag: String? = null,
    )

    private val storeKeywordRules = listOf(
        KeywordRule(
            code = "SPEEDTEST_CLI",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires Ookla Speedtest CLI",
            detail = "Downloads and executes the desktop Speedtest CLI binary.",
            regex = Regex("speedtest\\s*cli|\\bookla\\b", RegexOption.IGNORE_CASE),
            apiTag = "ookla-speedtest",
        ),
        KeywordRule(
            code = "APPLESCRIPT",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires macOS AppleScript",
            detail = "Uses AppleScript / osascript automation, which is only available on macOS.",
            regex = Regex("\\b(applescript|osascript)\\b", RegexOption.IGNORE_CASE),
            apiTag = "AppleScript",
        ),
        KeywordRule(
            code = "HOMEBREW",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires Homebrew CLI",
            detail = "Manages desktop packages via Homebrew, which is unavailable on Android.",
            regex = Regex("\\b(homebrew|brew)\\b", RegexOption.IGNORE_CASE),
            apiTag = "homebrew",
        ),
        KeywordRule(
            code = "FINDER",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires macOS Finder",
            detail = "Integrates with macOS Finder, which does not exist on Android.",
            regex = Regex("\\bFinder\\b|(?i)\\b(macos finder|reveal in finder|show in finder)\\b"),
            apiTag = "Finder",
        ),
        KeywordRule(
            code = "WINDOW_MANAGEMENT",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Desktop Window Management",
            detail = "Controls desktop application windows, which is unsupported on Android.",
            regex = Regex("\\bwindow\\s*management\\b", RegexOption.IGNORE_CASE),
            apiTag = "WindowManagement",
        ),
        KeywordRule(
            code = "XCODE",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires Xcode",
            detail = "Depends on macOS Xcode developer tools.",
            regex = Regex("\\bxcode\\b", RegexOption.IGNORE_CASE),
            apiTag = "Xcode",
        ),
        KeywordRule(
            code = "ALFRED",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Targets macOS Alfred",
            detail = "Interacts with macOS Alfred workflows or data.",
            regex = Regex("\\balfred\\b", RegexOption.IGNORE_CASE),
        ),
        KeywordRule(
            code = "ITERM",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Targets macOS iTerm",
            detail = "Controls macOS iTerm terminal sessions.",
            regex = Regex("\\biterm2?\\b", RegexOption.IGNORE_CASE),
        ),
        KeywordRule(
            code = "DOCKER",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires Docker CLI / daemon",
            detail = "Requires a local Docker engine and CLI binary.",
            regex = Regex("\\bdocker\\b", RegexOption.IGNORE_CASE),
        ),
        KeywordRule(
            code = "TMUX",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires tmux CLI",
            detail = "Requires a local tmux terminal multiplexer binary.",
            regex = Regex("\\btmux\\b", RegexOption.IGNORE_CASE),
        ),
        KeywordRule(
            code = "DESKTOP_WM",
            severity = AndroidCompatLevel.INCOMPATIBLE,
            title = "Requires desktop tiling window manager",
            detail = "Depends on macOS window manager (AeroSpace / Yabai).",
            regex = Regex("\\b(aerospace|yabai)\\b", RegexOption.IGNORE_CASE),
        ),
        KeywordRule(
            code = "RAYCAST_AI",
            severity = AndroidCompatLevel.PARTIAL,
            title = "Uses Raycast AI",
            detail = "Requires Raycast Pro AI runtime, which is stubbed on Android.",
            regex = Regex("\\braycast\\s+ai\\b", RegexOption.IGNORE_CASE),
            apiTag = "AI",
        ),
        KeywordRule(
            code = "MENU_BAR",
            severity = AndroidCompatLevel.PARTIAL,
            title = "Menu bar extension",
            detail = "MenuBarExtra falls back to list view mode on Android.",
            regex = Regex("\\bmenu\\s*bar\\b", RegexOption.IGNORE_CASE),
            apiTag = "MenuBarExtra",
        ),
        KeywordRule(
            code = "LOCALHOST_DAEMON",
            severity = AndroidCompatLevel.PARTIAL,
            title = "Requires localhost daemon",
            detail = "Expects a local background service or daemon running on the device.",
            regex = Regex("\\b(localhost\\s+daemon|local\\s+daemon)\\b", RegexOption.IGNORE_CASE),
        ),
    )

    // =========================================================================
    // 3. Source-scan regexes.
    // =========================================================================

    private val requireRegex = Regex("""require\s*\(\s*(['"])([^'"\n]+?)\1\s*\)""")
    private val raycastDestructureCjsRegex =
        Regex("""(?:const|let|var)\s*\{([^}]*?)\}\s*=\s*require\s*\(\s*['"]@raycast/api['"]\s*\)""")
    private val raycastDestructureEsmRegex =
        Regex("""import\s*\{([^}]*?)\}\s*from\s*['"]@raycast/api['"]""")

    // exec("...") — lookbehind excludes ".exec(" (RegExp.prototype.exec) and
    // other identifier suffixes; aliased ".exec(" is matched separately.
    private val execLiteralRegex =
        Regex("""(?<![.\w])(?:exec|execSync)\s*\(\s*(['"`])([^'"`\n]+?)\1""")
    private val fileLiteralRegex =
        Regex("""\b(?:execFile(?:Sync)?|spawn(?:Sync)?)\s*\(\s*(['"`])([^'"`\n]+?)\1""")
    private val aliasedExecLiteralRegex =
        Regex("""\b(?:child_process|childProcess|cp|execa|shell|proc)\s*\.\s*exec(?:Sync)?\s*\(\s*(['"`])([^'"`\n]+?)\1""")

    private val childProcessRegex = Regex("(?:node:)?child_process")
    private val spawnExecRegex = Regex("\\b(?:spawn|spawnSync|execSync|execFile|execFileSync)\\s*\\(")
    private val dotExecProcessRegex =
        Regex("(?:child_process|childProcess|cp|execa|shell)\\s*\\.\\s*exec\\s*\\(|(?<!/)\\.exec\\s*\\(\\s*[\"'`][^\"'`]+[\"'`]\\s*,")

    private val envAssignmentRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*=\\S*\\s+")
    private val shellSeparatorRegex = Regex("[\\s;&|<>`$(]")

    private val ooklaRegex = Regex("ookla-speedtest|install\\.speedtest\\.net", RegexOption.IGNORE_CASE)
    private val appleScriptRegex = Regex("\\bosascript\\b|\\brunAppleScript\\b|run-applescript|tell application", RegexOption.IGNORE_CASE)
    private val nativeAddonRegex = Regex("\\bdlopen\\b|\\bbindings\\s*\\(|\\bnode-gyp\\b|\\bprebuild-install\\b|\\bffi-napi\\b|\\bkoffi\\b|\\.node[\"'`]")
    private val desktopPathsRegex = Regex(
        """/Applications/|/opt/homebrew|/usr/local/bin|C:\\+Program Files|C:\\+Users|AppData\\+Local|%APPDATA%|\bcmd\.exe\b|\bpowershell\b""",
    )
    private val openAppRegex = Regex("""\bopen\s+-a\b""")
    private val platformCompareRegex = Regex("(?:process\\.platform|os\\.platform\\s*\\(\\s*\\))\\s*[!=]==?\\s*[\"'`](?:darwin|win32)[\"'`]|[\"'`](?:darwin|win32)[\"'`]\\s*[!=]==?\\s*(?:process\\.platform|os\\.platform\\s*\\(\\s*\\))")
    private val platformGateThrowRegex = Regex("(?:!==?|!=)\\s*[\"'`](?:darwin|win32)[\"'`]|unsupported\\s+platform|not\\s+supported\\s+on", RegexOption.IGNORE_CASE)
    private val throwOrErrorRegex = Regex("\\bthrow\\b|new\\s+Error\\s*\\(|showToast")
    private val sqliteRegex = Regex("\\buseSQL\\b|\\bbetter-sqlite3\\b|require\\([\"']sqlite3[\"']\\)|from\\s+[\"']sqlite3[\"']")
    private val windowManagementRegex = Regex("\\bWindowManagement\\b")
    private val menuBarExtraRegex = Regex("\\bMenuBarExtra\\b")
    private val browserExtRegex = Regex("\\bBrowserExtension\\b")
    private val selectedTextRegex = Regex("\\bgetSelectedText\\b")
    private val aiApiRegex = Regex("\\bAI\\.ask\\b|\\buseAI\\b")
    private val chmodRegex = Regex("\\bchmodSync\\s*\\(|\\bfs\\.chmod\\s*\\(|\\bchmod\\s*\\(")
    private val fsCallRegex = Regex(
        """\bfs\.\w+\s*\(|\b(?:readFileSync|writeFileSync|appendFileSync|mkdirSync|rmSync|unlinkSync|readdirSync|statSync|lstatSync|existsSync|copyFileSync|renameSync|createReadStream|createWriteStream|readFile|writeFile)\s*\(""",
    )

    private val blockCommentRegex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

    private val knownSafeApis = listOf(
        "@raycast/api" to Regex("[\"']@raycast/api[\"']"),
        "@raycast/utils" to Regex("[\"']@raycast/utils[\"']"),
        "fetch" to Regex("\\bfetch\\s*\\("),
        "LocalStorage" to Regex("\\bLocalStorage\\b"),
        "Cache" to Regex("\\bCache\\b"),
        "Clipboard" to Regex("\\bClipboard\\b"),
        "OAuth" to Regex("\\bOAuth\\b"),
        "node:crypto" to Regex("(?:node:)?crypto"),
        "List" to Regex("\\bList\\b"),
        "Grid" to Regex("\\bGrid\\b"),
        "Detail" to Regex("\\bDetail\\b"),
        "Form" to Regex("\\bForm\\b"),
    )

    // =========================================================================
    // 4. Scan state accumulated across the module graph.
    // =========================================================================

    private class SourceScanState(val root: File) {
        val builtinsUsed = linkedSetOf<String>()
        val exportsFound = linkedSetOf<String>()
        val exportsMissing = linkedSetOf<String>()
        val emulatedCommands = sortedSetOf<String>()
        val bridgeCommands = sortedSetOf<String>()
        val desktopCommands = sortedSetOf<String>()
        val missingRelative = linkedMapOf<String, MutableSet<String>>()
        val missingNpm = linkedMapOf<String, MutableSet<String>>()
        val missingEntries = sortedSetOf<String>()
        val markers = linkedSetOf<String>()
        var childProcessUsed = false
        var fsCallCount = 0
        var scannedCount = 0
    }

    // =========================================================================
    // 5. Public API — store listing (metadata-only pass).
    // =========================================================================

    fun analyzeStoreListing(listing: StoreExtensionListing): CompatibilityReport {
        val issues = linkedMapOf<String, CompatibilityIssue>()
        val detectedApis = linkedSetOf<String>()

        val platforms = listing.platforms.filter { it.isNotBlank() }
        if (platforms.isNotEmpty() && platforms.none { it.equals("android", ignoreCase = true) }) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "DESKTOP_PLATFORMS",
                    title = "Declares desktop platforms",
                    detail = "Declared for ${platforms.joinToString()}; works on Android if pure JS/API, incompatible if it spawns desktop binaries or AppleScript.",
                ),
            )
        }

        if (listing.commands.any { it.mode.equals("menu-bar", ignoreCase = true) }) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "MENU_BAR",
                    title = "Menu bar command",
                    detail = "MenuBarExtra falls back to list view on Android.",
                ),
            )
            detectedApis += "MenuBarExtra"
        }

        val textBlob = buildString {
            append(listing.title)
            append(' ')
            append(listing.name)
            append(' ')
            append(listing.description)
            listing.commands.forEach { cmd ->
                append(' ')
                append(cmd.title)
                append(' ')
                append(cmd.subtitle.orEmpty())
                append(' ')
                append(cmd.description)
            }
        }

        for (rule in storeKeywordRules) {
            if (rule.regex.containsMatchIn(textBlob)) {
                addOrUpgradeIssue(
                    issues,
                    CompatibilityIssue(
                        severity = rule.severity,
                        code = rule.code,
                        title = rule.title,
                        detail = rule.detail,
                    ),
                )
                rule.apiTag?.let { detectedApis += it }
            }
        }

        val finalIssues = issues.values.toList()
        val level = worstSeverity(finalIssues)
        return CompatibilityReport(
            level = level,
            summary = buildStoreSummary(level, finalIssues),
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
    }

    // =========================================================================
    // 6. Public API — installed extension (full static-analysis engine).
    // =========================================================================

    private val installedReportCache =
        java.util.concurrent.ConcurrentHashMap<String, CompatibilityReport>()

    private fun cacheKey(extension: InstalledRaycastExtension): String {
        val dir = File(extension.dirPath)
        val manifestFile = File(dir, "package.json")
        return "${extension.id}|${dir.absolutePath}|${dir.lastModified()}|${manifestFile.lastModified()}"
    }

    fun getCachedInstalledReport(extension: InstalledRaycastExtension): CompatibilityReport? =
        installedReportCache[cacheKey(extension)]

    fun clearInstalledReportCache() {
        installedReportCache.clear()
    }

    fun analyzeInstalledExtension(extension: InstalledRaycastExtension): CompatibilityReport {
        val key = cacheKey(extension)
        installedReportCache[key]?.let { return it }
        val issues = linkedMapOf<String, CompatibilityIssue>()
        val detectedApis = linkedSetOf<String>()
        val manifest = extension.manifest

        // --- manifest-level facts -------------------------------------------------
        val platforms = manifest.platforms.orEmpty().filter { it.isNotBlank() }
        if (platforms.isNotEmpty() && platforms.none { it.equals("android", ignoreCase = true) }) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "DESKTOP_PLATFORMS",
                    title = "Declares desktop platforms",
                    detail = "Declares desktop platforms: ${platforms.joinToString()}.",
                ),
            )
        }

        if (manifest.commands.any { it.mode.equals("menu-bar", ignoreCase = true) }) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "MENU_BAR",
                    title = "Menu bar command",
                    detail = "Menu bar command runs in fallback view mode on Android.",
                ),
            )
            detectedApis += "MenuBarExtra"
        }

        if (manifest.ai != null && !manifest.ai.isJsonNull) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "RAYCAST_AI",
                    title = "Raycast AI manifest",
                    detail = "Requires Raycast Pro AI runtime.",
                ),
            )
            detectedApis += "AI"
        }

        if (!manifest.tools.isNullOrEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "RAYCAST_AI_TOOLS",
                    title = "Raycast AI Tools",
                    detail = "Manifest declares Raycast AI tools (${manifest.tools.size}), which require Raycast AI runtime.",
                ),
            )
            detectedApis += "AI.Tools"
        }

        manifest.dependencies.orEmpty().keys.forEach { dep ->
            val lower = dep.lowercase()
            when {
                lower == "run-applescript" -> {
                    addOrUpgradeIssue(
                        issues,
                        CompatibilityIssue(
                            severity = AndroidCompatLevel.INCOMPATIBLE,
                            code = "APPLESCRIPT",
                            title = "AppleScript dependency",
                            detail = "Requires macOS AppleScript / osascript ($dep).",
                        ),
                    )
                    detectedApis += "AppleScript"
                }
                lower == "better-sqlite3" || lower == "sqlite3" -> {
                    addOrUpgradeIssue(
                        issues,
                        CompatibilityIssue(
                            severity = AndroidCompatLevel.INCOMPATIBLE,
                            code = "SQLITE_NATIVE",
                            title = "Native SQLite dependency",
                            detail = "Uses desktop SQLite native module ($dep).",
                        ),
                    )
                    detectedApis += dep
                }
                lower in setOf("node-gyp", "prebuild-install", "ffi-napi", "koffi", "bindings") -> {
                    addOrUpgradeIssue(
                        issues,
                        CompatibilityIssue(
                            severity = AndroidCompatLevel.INCOMPATIBLE,
                            code = "NATIVE_ADDON",
                            title = "Native Node addon dependency",
                            detail = "Loads compiled native .node addon ($dep).",
                        ),
                    )
                    detectedApis += dep
                }
            }
        }

        // --- module graph resolution + platform-call detection ---------------------
        val state = scanModuleGraph(extension, detectedApis)
        assembleScanIssues(state, issues, detectedApis)

        val finalIssues = issues.values.toList()
        val level = worstSeverity(finalIssues)
        val commandCount = manifest.commands.size.takeIf { it > 0 }
            ?: maxOf(state.scannedCount, 1)
        val summary = buildInstalledSummary(level, finalIssues, commandCount, state)
        val report = CompatibilityReport(
            level = level,
            summary = summary,
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
        installedReportCache[key] = report
        return report
    }

    // =========================================================================
    // 7. Public API — runtime error diagnosis (kept for the runner UIs).
    // =========================================================================

    fun analyzeError(
        errorMessage: String,
        extension: InstalledRaycastExtension? = null,
    ): CompatibilityReport {
        val issues = linkedMapOf<String, CompatibilityIssue>()
        val detectedApis = linkedSetOf<String>()

        if (extension != null) {
            val extReport = analyzeInstalledExtension(extension)
            extReport.issues.forEach { addOrUpgradeIssue(issues, it) }
            detectedApis.addAll(extReport.detectedApis)
        }

        val errorIssues = diagnoseErrorMessage(errorMessage)
        errorIssues.forEach { addOrUpgradeIssue(issues, it) }

        if (issues.isEmpty() && errorMessage.isNotBlank()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "RUNTIME_ERROR",
                    title = "Runtime execution error",
                    detail = errorMessage.trim().take(240),
                ),
            )
        }

        val finalIssues = issues.values.toList()
        val level = worstSeverity(finalIssues)
        val leadMessage = errorIssues.firstOrNull()?.let { "${it.title}: ${it.detail}" }
            ?: buildGenericSummary(level, finalIssues)
        return CompatibilityReport(
            level = level,
            summary = leadMessage,
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
    }

    // =========================================================================
    // 8. Module graph resolution — mirrors runtime.js createRequire
    //    (app/src/main/assets/raycast/runtime.js ~lines 4426-4505).
    // =========================================================================

    private fun scanModuleGraph(
        extension: InstalledRaycastExtension,
        detectedApis: MutableSet<String>,
    ): SourceScanState {
        val rawRoot = File(extension.dirPath)
        val root = norm(rawRoot)
        val state = SourceScanState(root)
        if (!root.isDirectory) return state

        val entryFiles = linkedSetOf<File>()
        val commands = extension.manifest.commands
        if (commands.isNotEmpty()) {
            commands.forEach { cmd ->
                val entry = extension.entryFileForCommand(cmd.name)
                if (entry != null) {
                    entryFiles += norm(entry)
                } else if (cmd.name.isNotBlank()) {
                    state.missingEntries += cmd.name
                }
            }
        }
        if (entryFiles.isEmpty()) {
            // No entry file on disk (or no commands declared): fall back to
            // scanning every JS source so a partially-installed bundle is never
            // silently reported as clean.
            root.walkTopDown()
                .onEnter { it.name != ".git" }
                .filter { it.isFile && it.extension.lowercase() in setOf("js", "cjs", "mjs") }
                .sortedBy { if (it.relativeTo(root).path.contains("node_modules")) 1 else 0 }
                .take(MAX_SCAN_FILES)
                .forEach { entryFiles += norm(it) }
        }

        val queue = ArrayDeque(entryFiles)
        val visited = mutableSetOf<File>()
        while (queue.isNotEmpty() && state.scannedCount < MAX_SCAN_FILES) {
            val file = queue.removeFirst()
            if (!file.isFile || !isWithin(root, file)) continue
            if (!visited.add(file)) continue
            val content = readFileCapped(file, MAX_FILE_BYTES)
            if (content.isBlank()) continue
            state.scannedCount++
            scanSourceFile(content, file, root, state, queue, detectedApis)
        }
        return state
    }

    private fun scanSourceFile(
        content: String,
        file: File,
        root: File,
        state: SourceScanState,
        queue: ArrayDeque<File>,
        detectedApis: MutableSet<String>,
    ) {
        val stripped = stripJsComments(content)
        val relPath = file.relativeTo(root).path

        // ---- require() resolution (mirrors createRequire order: builtin ->
        //      relative/absolute -> bare node_modules walk). ----
        for (m in requireRegex.findAll(stripped)) {
            val id = m.groupValues[2]
            when {
                id in BUILTIN_MODULES -> state.builtinsUsed += canonicalBuiltin(id)
                id.startsWith("./") || id.startsWith("../") || id.startsWith("/") -> {
                    val resolved = resolveRelativeRequire(id, file.parentFile, root)
                    if (resolved == null) {
                        state.missingRelative.getOrPut(id) { linkedSetOf() } += relPath
                    } else {
                        enqueueSourceFile(resolved, queue)
                    }
                }
                else -> {
                    val resolved = resolveBareModule(id, file.parentFile, root)
                    if (resolved == null) {
                        state.missingNpm.getOrPut(id) { linkedSetOf() } += relPath
                    } else {
                        enqueueSourceFile(resolved, queue)
                    }
                }
            }
        }

        // ---- @raycast/api destructured exports vs the runtime export table. ----
        for (regex in listOf(raycastDestructureCjsRegex, raycastDestructureEsmRegex)) {
            for (m in regex.findAll(stripped)) {
                m.groupValues[1].split(",").forEach { rawName ->
                    val name = rawName.trim()
                        .split(Regex("\\s+as\\s+"))[0]
                        .trim()
                        .trim('"')
                        .trim('\'')
                        .trim()
                    if (name.isBlank() || name == "default" || name.contains("{") || name.contains(":")) return@forEach
                    if (name in RAYCAST_API_EXPORTS) {
                        state.exportsFound += name
                    } else {
                        state.exportsMissing += name
                    }
                }
            }
        }

        // ---- child_process call sites: extract and classify command literals. ----
        val cliLiterals = sequenceOf(execLiteralRegex, aliasedExecLiteralRegex, fileLiteralRegex)
            .flatMap { it.findAll(stripped) }
            .map { it.groupValues[2] }
            .toList()
        if (cliLiterals.isNotEmpty()) {
            state.childProcessUsed = true
        }
        for (literal in cliLiterals) {
            val head = commandHead(literal)
            if (head.isBlank()) continue
            when {
                head in DESKTOP_ONLY_COMMANDS -> state.desktopCommands += head
                head in CLI_EMULATED_COMMANDS -> state.emulatedCommands += head
                else -> state.bridgeCommands += head
            }
        }
        if (childProcessRegex.containsMatchIn(stripped) ||
            spawnExecRegex.containsMatchIn(stripped) ||
            dotExecProcessRegex.containsMatchIn(stripped)
        ) {
            state.childProcessUsed = true
        }

        // ---- content markers (same families as before, now scoped to the
        //      reachable module graph). ----
        if (ooklaRegex.containsMatchIn(stripped)) state.markers += "DESKTOP_BINARY_DOWNLOAD"
        if (appleScriptRegex.containsMatchIn(stripped)) state.markers += "APPLESCRIPT"
        if (nativeAddonRegex.containsMatchIn(stripped)) state.markers += "NATIVE_ADDON"
        if (desktopPathsRegex.containsMatchIn(stripped)) state.markers += "DESKTOP_PATHS"
        if (openAppRegex.containsMatchIn(stripped)) state.markers += "MAC_APP_LAUNCH"
        if (platformCompareRegex.containsMatchIn(stripped)) {
            val rejectsUnsupported = platformGateThrowRegex.containsMatchIn(stripped) &&
                throwOrErrorRegex.containsMatchIn(stripped)
            state.markers += if (rejectsUnsupported) "PLATFORM_CHECK_REJECT" else "PLATFORM_CHECK"
        }
        if (sqliteRegex.containsMatchIn(stripped)) state.markers += "SQLITE_NATIVE"
        if (windowManagementRegex.containsMatchIn(stripped)) state.markers += "WINDOW_MANAGEMENT"
        if (menuBarExtraRegex.containsMatchIn(stripped)) state.markers += "MENU_BAR"
        if (browserExtRegex.containsMatchIn(stripped)) state.markers += "BROWSER_EXT"
        if (selectedTextRegex.containsMatchIn(stripped)) state.markers += "SELECTED_TEXT"
        if (aiApiRegex.containsMatchIn(stripped)) state.markers += "RAYCAST_AI"
        if (chmodRegex.containsMatchIn(stripped)) state.markers += "EXECUTABLE_CHMOD"

        state.fsCallCount += fsCallRegex.findAll(stripped).count()

        for ((apiName, regex) in knownSafeApis) {
            if (regex.containsMatchIn(stripped)) detectedApis += apiName
        }
    }

    /** resolveFileCandidates in runtime.js (~line 4308), byte-for-byte. */
    private fun fileCandidates(base: File): List<File> = listOf(
        base,
        File(base.path + ".js"),
        File(base.path + ".cjs"),
        File(base.path + ".mjs"),
        File(base.path + ".json"),
        File(base, "index.js"),
        File(base, "index.cjs"),
        File(base, "index.json"),
    )

    /**
     * Relative/absolute require resolution. Mirrors the "./", "../", "/" branch
     * of createRequire (runtime.js ~4431): candidates are the id itself plus
     * .js/.cjs/.mjs/.json and /index.* variants, tried in order.
     */
    private fun resolveRelativeRequire(id: String, fromDir: File?, root: File): File? {
        val base = if (id.startsWith("/")) {
            File(root, id.removePrefix("/"))
        } else {
            File(fromDir ?: root, id)
        }
        val normalized = norm(base)
        for (candidate in fileCandidates(normalized)) {
            val c = norm(candidate)
            if (c.isFile && isWithin(root, c)) return c
        }
        return null
    }

    private data class ParsedPackageId(val name: String, val subpath: String?)

    /** splitPackageId in runtime.js (~line 4418). */
    private fun splitPackageId(id: String): ParsedPackageId {
        val parts = id.split("/")
        return if (parts[0].startsWith("@") && parts.size >= 2) {
            ParsedPackageId(parts[0] + "/" + parts[1], parts.drop(2).joinToString("/").ifEmpty { null })
        } else {
            ParsedPackageId(parts[0], parts.drop(1).joinToString("/").ifEmpty { null })
        }
    }

    /**
     * Bare npm id resolution. Mirrors the node_modules walk of createRequire
     * (runtime.js ~4435-4475): walk up from the requiring file's directory (up to
     * the extension root, the runtime's virtual "/"), and for each level try, in
     * order: package.json `exports` (with subpath/star matching), then the
     * __subpath/__bundle fallbacks, then package.json `main`, then the package
     * directory itself via fileCandidates.
     */
    private fun resolveBareModule(id: String, fromDir: File?, root: File): File? {
        val parsed = splitPackageId(id)
        var dir: File? = fromDir ?: root
        while (dir != null) {
            val nmBase = norm(File(dir, "node_modules/${parsed.name}"))
            val pkg = readPackageJson(File(nmBase, "package.json"))
            val candidates = mutableListOf<File>()

            if (pkg != null && pkg.has("exports")) {
                val exportTarget = resolvePackageExports(pkg.get("exports"), parsed.subpath)
                if (exportTarget != null) {
                    val resolvedExport = norm(File(nmBase, stripLeadingDotSlash(exportTarget)))
                    if (isWithin(nmBase, resolvedExport)) {
                        candidates += fileCandidates(resolvedExport)
                    }
                }
            }

            if (parsed.subpath != null) {
                candidates += fileCandidates(norm(File(nmBase, parsed.subpath)))
                candidates += fileCandidates(norm(File(nmBase, "__subpath/${parsed.subpath}")))
                candidates += fileCandidates(norm(File(nmBase, "__bundle")))
                candidates += fileCandidates(norm(File(nmBase, "__subpath.js")))
            } else {
                val main = pkg?.get("main")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (pkg != null && main.isNotBlank()) {
                    val mainTarget = norm(File(nmBase, stripLeadingDotSlash(main)))
                    if (isWithin(nmBase, mainTarget)) {
                        candidates += fileCandidates(mainTarget)
                    }
                }
                candidates += fileCandidates(norm(File(nmBase, "__bundle")))
                candidates += fileCandidates(norm(File(nmBase, "__subpath")))
                candidates += fileCandidates(nmBase)
            }

            for (candidate in candidates) {
                val c = norm(candidate)
                if (c.isFile && isWithin(root, c)) return c
            }

            if (dir == root) break
            dir = dir.parentFile
        }
        return null
    }

    private fun readPackageJson(file: File): JsonObject? {
        if (!file.isFile) return null
        val raw = readFileCapped(file, MAX_FILE_BYTES)
        if (raw.isBlank()) return null
        return runCatching { JsonParser.parseString(raw) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
    }

    /** stripLeadingDotSlash in runtime.js (~line 4407). */
    private fun stripLeadingDotSlash(target: String): String {
        var value = target
        while (value.startsWith("./")) value = value.substring(2)
        if (value.startsWith("/")) value = value.substring(1)
        return value
    }

    /** isWithinPackage in runtime.js (~line 4414). */
    private fun isWithin(base: File, target: File): Boolean {
        return target == base || target.path.startsWith(base.path + File.separator)
    }

    /**
     * exportTargetValue in runtime.js (~line 4332): strings win, arrays return
     * the first resolvable entry, condition objects prefer require/node/default.
     */
    private fun exportTargetValue(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null
        if (value.isJsonPrimitive) {
            return value.asJsonPrimitive.takeIf { it.isString }?.asString
        }
        if (value.isJsonArray) {
            value.asJsonArray.forEach { element ->
                exportTargetValue(element)?.let { return it }
            }
            return null
        }
        if (value.isJsonObject) {
            val obj = value.asJsonObject
            for (key in listOf("require", "node", "default")) {
                if (obj.has(key)) {
                    exportTargetValue(obj.get(key))?.let { return it }
                }
            }
            for ((_, element) in obj.entrySet()) {
                exportTargetValue(element)?.let { return it }
            }
        }
        return null
    }

    /** resolvePackageExports in runtime.js (~line 4358), including star keys. */
    private fun resolvePackageExports(exportsValue: JsonElement?, subpath: String?): String? {
        if (exportsValue == null || exportsValue.isJsonNull) return null
        val isStringExport = exportsValue.isJsonPrimitive && exportsValue.asJsonPrimitive.isString
        if (isStringExport || exportsValue.isJsonArray) {
            return if (subpath != null) null else exportTargetValue(exportsValue)
        }
        if (!exportsValue.isJsonObject) return null

        val obj = exportsValue.asJsonObject
        val keys = obj.keySet()
        val hasSubpathKeys = keys.any { it == "." || it.startsWith("./") }
        if (!hasSubpathKeys) {
            return if (subpath != null) null else exportTargetValue(exportsValue)
        }
        if (subpath == null) {
            return if (obj.has(".")) exportTargetValue(obj.get(".")) else null
        }

        for (direct in listOf("./$subpath", "./$subpath.js", "./$subpath/index.js")) {
            if (obj.has(direct)) {
                exportTargetValue(obj.get(direct))?.let { return it }
            }
        }

        val request = "./$subpath"
        for (key in keys) {
            val star = key.indexOf("*")
            if (star == -1) continue
            val prefix = key.substring(0, star)
            val suffix = key.substring(star + 1)
            if (!request.startsWith(prefix)) continue
            if (suffix.isNotEmpty() && !request.endsWith(suffix)) continue
            val end = if (suffix.isEmpty()) request.length else request.length - suffix.length
            val matched = if (end > prefix.length) request.substring(prefix.length, end) else ""
            exportTargetValue(obj.get(key))?.let { return it.replace("*", matched) }
        }
        return null
    }

    private fun enqueueSourceFile(file: File, queue: ArrayDeque<File>) {
        val ext = file.extension.lowercase()
        if (ext == "json") return // JSON modules cannot require anything.
        if (ext.isNotEmpty() && ext !in setOf("js", "cjs", "mjs")) return
        queue.addLast(file)
    }

    private fun norm(file: File): File = runCatching { file.canonicalFile }.getOrDefault(file)

    private fun canonicalBuiltin(id: String): String =
        if (id.startsWith("node:")) id else if ("node:$id" in BUILTIN_MODULES) "node:$id" else id

    /**
     * Extracts the head command of a shell literal so it can be classified:
     * strips leading VAR=value assignments, cuts at the first shell separator,
     * then takes the basename of the binary.
     */
    private fun commandHead(literal: String): String {
        var s = literal.trim()
        while (true) {
            val m = envAssignmentRegex.find(s) ?: break
            s = s.substring(m.value.length)
        }
        val cut = shellSeparatorRegex.find(s)?.range?.first ?: s.length
        val token = s.substring(0, cut).trim('\'', '"').trim()
        if (token.isBlank()) return ""
        return token.substringAfterLast('/').substringAfterLast('\\').lowercase()
    }

    private fun stripJsComments(content: String): String {
        val noBlocks = content.replace(blockCommentRegex, " ")
        // Crude line-comment strip: drop "// ..." to end of line unless the
        // "//" is preceded by ":" or a quote (URLs like https://, string
        // literals) — good enough for require-literal extraction.
        return noBlocks.lineSequence().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            when {
                idx < 0 -> line
                idx == 0 -> ""
                else -> {
                    val prev = line[idx - 1]
                    if (prev == ':' || prev == '"' || prev == '\'') line else line.substring(0, idx)
                }
            }
        }
    }

    // =========================================================================
    // 9. Verdict assembly.
    // =========================================================================

    private fun assembleScanIssues(
        state: SourceScanState,
        issues: MutableMap<String, CompatibilityIssue>,
        detectedApis: MutableSet<String>,
    ) {
        // CLI classification.
        if (state.childProcessUsed) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "NATIVE_PROCESS",
                    title = "Uses shell commands via child_process",
                    detail = "Commands run through the Komorebi Android shell (emulated basics + optional on-device CLI bridge). Desktop-only binaries are unavailable.",
                ),
            )
            detectedApis += "node:child_process"
        }
        if (state.emulatedCommands.isNotEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "CLI_EMULATED",
                    title = "Emulated shell commands",
                    detail = "Runs in the built-in Android shell emulator, fully offline: ${state.emulatedCommands.joinToString()}.",
                ),
            )
            detectedApis += "cli-emulator"
        }
        if (state.bridgeCommands.isNotEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "HOST_BRIDGE",
                    title = "On-device CLI bridge commands",
                    detail = "Not emulated; needs the on-device CLI bridge (Shizuku/root) if installed: ${state.bridgeCommands.joinToString()}.",
                ),
            )
            detectedApis += "cli-bridge"
        }
        if (state.desktopCommands.isNotEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "DESKTOP_CLI",
                    title = "Desktop-only binaries",
                    detail = "Requires desktop binaries with no Android equivalent: ${state.desktopCommands.joinToString()}.",
                ),
            )
            detectedApis += "desktop-cli"
        }

        // Content markers.
        for (marker in state.markers) {
            markerIssue(marker)?.let { addOrUpgradeIssue(issues, it) }
            markerApiTag(marker)?.let { detectedApis += it }
        }

        // Commands with no resolvable entry file.
        if (state.missingEntries.isNotEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "MISSING_ENTRY",
                    title = "Command entry file missing",
                    detail = "No built JS file found for: ${state.missingEntries.joinToString()}. Source-only TypeScript extensions must be pre-built.",
                ),
            )
        }

        // Missing modules — the key upgrade over keyword guessing: precise ids.
        state.missingRelative.forEach { (id, fromFiles) ->
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "MISSING_FILE",
                    title = "Missing JS module: $id",
                    detail = "Required by ${fromFiles.joinToString()} but no file matched (tried the id itself, .js, .cjs, .mjs, .json and index.* variants).",
                ),
                key = "MISSING_FILE::$id",
            )
        }
        state.missingNpm.forEach { (id, fromFiles) ->
            val rootId = id.removePrefix("node:").split("/")[0]
            val hint = if (rootId in KNOWN_NODE_BUILTINS) {
                "'$rootId' is a known Node.js builtin that the Android QuickJS runtime does not provide."
            } else {
                "The Android QuickJS runtime only bundles its builtin modules; ship a self-contained bundle or include node_modules/$rootId."
            }
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "MISSING_NPM",
                    title = "Missing JS module: $id",
                    detail = "Cannot find module '$id' (required by ${fromFiles.joinToString()}); createRequire would throw at runtime. $hint",
                ),
                key = "MISSING_NPM::$id",
            )
        }

        // @raycast/api export gaps.
        if (state.exportsMissing.isNotEmpty()) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "RAYCAST_API_GAP",
                    title = "Missing @raycast/api exports",
                    detail = "Destructured from @raycast/api but not provided by the Android runtime (undefined at runtime): ${state.exportsMissing.joinToString()}.",
                ),
            )
        }

        // Heavy node:fs usage note.
        val usesFs = state.builtinsUsed.any { it == "node:fs" || it == "node:fs/promises" }
        if (usesFs && state.fsCallCount >= FS_HEAVY_CALL_THRESHOLD) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "FS_SANDBOX",
                    title = "Heavy filesystem usage",
                    detail = "Reads and writes are confined to the extension's sandboxed support directory on Android.",
                ),
            )
            detectedApis += "node:fs"
        }

        detectedApis += state.builtinsUsed
        detectedApis += state.exportsFound
    }

    private fun markerIssue(marker: String): CompatibilityIssue? = when (marker) {
        "DESKTOP_BINARY_DOWNLOAD" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "DESKTOP_BINARY_DOWNLOAD",
            title = "Desktop binary download",
            detail = "Downloads macOS/Windows Ookla Speedtest CLI binary.",
        )
        "APPLESCRIPT" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "APPLESCRIPT",
            title = "Requires macOS AppleScript",
            detail = "Requires macOS AppleScript / osascript.",
        )
        "NATIVE_ADDON" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "NATIVE_ADDON",
            title = "Native .node addon",
            detail = "Loads compiled native .node addon.",
        )
        "DESKTOP_PATHS" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "DESKTOP_PATHS",
            title = "Desktop filesystem paths",
            detail = "Targets macOS/Windows filesystem paths, shells or app folders (/Applications, C:\\, cmd.exe, powershell).",
        )
        "MAC_APP_LAUNCH" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "MAC_APP_LAUNCH",
            title = "Launches macOS applications",
            detail = "Uses 'open -a' to launch desktop applications, which do not exist on Android.",
        )
        "PLATFORM_CHECK" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "PLATFORM_CHECK",
            title = "Platform check",
            detail = "Checks for macOS/Windows platform.",
        )
        "PLATFORM_CHECK_REJECT" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "PLATFORM_CHECK",
            title = "Platform check",
            detail = "Checks for macOS/Windows platform and rejects unsupported platforms.",
        )
        "SQLITE_NATIVE" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "SQLITE_NATIVE",
            title = "Desktop SQLite native module",
            detail = "Uses desktop SQLite native module (useSQL).",
        )
        "WINDOW_MANAGEMENT" -> CompatibilityIssue(
            severity = AndroidCompatLevel.INCOMPATIBLE,
            code = "WINDOW_MANAGEMENT",
            title = "WindowManagement API",
            detail = "Desktop WindowManagement API is not supported on Android.",
        )
        "MENU_BAR" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "MENU_BAR",
            title = "MenuBarExtra fallback",
            detail = "Menu bar command runs in fallback view mode on Android.",
        )
        "BROWSER_EXT" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "BROWSER_EXT",
            title = "BrowserExtension bridge",
            detail = "Desktop browser extension bridge is unavailable on Android.",
        )
        "SELECTED_TEXT" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "SELECTED_TEXT",
            title = "Selected text capture",
            detail = "Works on Android; reads the fresh on-screen selection only (no desktop selection history).",
        )
        "RAYCAST_AI" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "RAYCAST_AI",
            title = "Raycast AI runtime",
            detail = "Requires Raycast Pro AI runtime.",
        )
        "EXECUTABLE_CHMOD" -> CompatibilityIssue(
            severity = AndroidCompatLevel.PARTIAL,
            code = "EXECUTABLE_CHMOD",
            title = "Executable chmod",
            detail = "Marks downloaded file executable (Android blocks executing downloaded binaries in app storage).",
        )
        else -> null
    }

    private fun markerApiTag(marker: String): String? = when (marker) {
        "DESKTOP_BINARY_DOWNLOAD" -> "ookla-speedtest"
        "APPLESCRIPT" -> "AppleScript"
        "WINDOW_MANAGEMENT" -> "WindowManagement"
        "MENU_BAR" -> "MenuBarExtra"
        "BROWSER_EXT" -> "BrowserExtension"
        "SELECTED_TEXT" -> "getSelectedText"
        "RAYCAST_AI" -> "AI"
        "EXECUTABLE_CHMOD" -> "fs.chmod"
        "PLATFORM_CHECK", "PLATFORM_CHECK_REJECT" -> "process.platform"
        "DESKTOP_PATHS" -> "desktop-fs"
        else -> null
    }

    // =========================================================================
    // 10. Summary builders.
    // =========================================================================

    private fun buildInstalledSummary(
        level: AndroidCompatLevel,
        issues: List<CompatibilityIssue>,
        commandCount: Int,
        state: SourceScanState,
    ): String {
        val missing = buildList {
            addAll(state.missingNpm.keys)
            addAll(state.missingRelative.keys)
            addAll(state.exportsMissing)
        }
        return when (level) {
            AndroidCompatLevel.COMPATIBLE -> {
                val word = if (commandCount == 1) "command" else "commands"
                "Verified compatible — $commandCount $word, all modules resolve"
            }
            AndroidCompatLevel.PARTIAL -> {
                val cliRunsWith = buildList {
                    if (state.emulatedCommands.isNotEmpty()) {
                        add("emulated shell commands (${state.emulatedCommands.joinToString()})")
                    }
                    if (state.bridgeCommands.isNotEmpty()) {
                        add("the on-device CLI bridge (${state.bridgeCommands.joinToString()})")
                    }
                }
                val lead = when {
                    cliRunsWith.isNotEmpty() -> "runs with ${cliRunsWith.joinToString(" and ")}"
                    issues.isNotEmpty() -> issues.first().title
                    else -> "limited support"
                }
                if (missing.isEmpty()) {
                    "Partial — $lead"
                } else {
                    "Partial — $lead (missing: ${missing.joinToString().take(140)})"
                }
            }
            AndroidCompatLevel.INCOMPATIBLE -> {
                when {
                    state.desktopCommands.isNotEmpty() -> {
                        val base = "Not compatible — requires desktop binaries (${state.desktopCommands.joinToString()})"
                        if (missing.isEmpty()) base else "$base; missing: ${missing.joinToString().take(120)}"
                    }
                    missing.isNotEmpty() -> {
                        val word = if (missing.size == 1) "module" else "modules"
                        "Not compatible — missing JS $word: ${missing.joinToString().take(160)}"
                    }
                    else -> {
                        val blockers = issues.filter { it.severity == AndroidCompatLevel.INCOMPATIBLE }
                        val lead = blockers.firstOrNull()?.title ?: "Incompatible with Android"
                        if (blockers.size == 1) "$lead: ${blockers[0].detail}"
                        else "$lead (+${blockers.size - 1} more Android blocker(s))."
                    }
                }
            }
        }
    }

    private fun buildStoreSummary(
        level: AndroidCompatLevel,
        issues: List<CompatibilityIssue>,
    ): String {
        val installNote = "Install to run the full code analysis."
        return when (level) {
            AndroidCompatLevel.COMPATIBLE ->
                "No desktop-only APIs or binary dependencies detected in store metadata. $installNote"
            AndroidCompatLevel.PARTIAL -> {
                val first = issues.firstOrNull()
                val base = when {
                    first == null -> "Limited Android support"
                    issues.size == 1 -> "${first.title} (${first.detail})"
                    else -> "${first.title} and ${issues.size - 1} other compatibility note(s)"
                }
                "Partial — $base. $installNote"
            }
            AndroidCompatLevel.INCOMPATIBLE -> {
                val blockers = issues.filter { it.severity == AndroidCompatLevel.INCOMPATIBLE }
                val lead = blockers.firstOrNull()?.title ?: "Incompatible with Android"
                val base = when {
                    blockers.size == 1 -> "$lead: ${blockers[0].detail}"
                    blockers.isEmpty() -> lead
                    else -> "$lead (+${blockers.size - 1} more Android blocker(s))"
                }
                "$base. $installNote"
            }
        }
    }

    private fun buildGenericSummary(
        level: AndroidCompatLevel,
        issues: List<CompatibilityIssue>,
    ): String {
        return when (level) {
            AndroidCompatLevel.COMPATIBLE ->
                "Pure JavaScript / Raycast API extension; ready to run on Android."
            AndroidCompatLevel.PARTIAL -> {
                val first = issues.firstOrNull()?.title ?: "Limited Android support"
                if (issues.size == 1) "$first (${issues[0].detail})"
                else "$first and ${issues.size - 1} other compatibility note(s)."
            }
            AndroidCompatLevel.INCOMPATIBLE -> {
                val blockers = issues.filter { it.severity == AndroidCompatLevel.INCOMPATIBLE }
                val lead = blockers.firstOrNull()?.title ?: "Incompatible with Android"
                if (blockers.size == 1) "$lead: ${blockers[0].detail}"
                else "$lead (+${blockers.size - 1} more Android blocker(s))."
            }
        }
    }

    // =========================================================================
    // 11. Error-message diagnosis (kept from the previous engine).
    // =========================================================================

    private fun diagnoseErrorMessage(errorMessage: String): List<CompatibilityIssue> {
        if (errorMessage.isBlank()) return emptyList()
        val found = mutableListOf<CompatibilityIssue>()
        val lower = errorMessage.lowercase()

        val missingModuleMatch = Regex("Cannot find module ['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(errorMessage)
        if (missingModuleMatch != null) {
            val moduleName = missingModuleMatch.groupValues[1]
            if (moduleName.contains("child_process", ignoreCase = true)) {
                found += CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "NATIVE_PROCESS",
                    title = "Native process module unavailable",
                    detail = "Extension tried to require '$moduleName', which cannot run native OS processes on Android.",
                )
            } else {
                found += CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "MISSING_MODULE",
                    title = "Missing JS module: $moduleName",
                    detail = "The Android QuickJS runtime does not bundle '$moduleName' and no local file matched it.",
                )
            }
        }

        if ((lower.contains("android") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("platform"))) ||
            lower.contains("unsupported platform")
        ) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.INCOMPATIBLE,
                code = "UNSUPPORTED_PLATFORM",
                title = "Unsupported platform: android",
                detail = "The extension expects macOS or Windows and rejected process.platform === 'android'.",
            )
        }

        if (lower.contains("child_process") || lower.contains("spawn") || lower.contains("execsync")) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.PARTIAL,
                code = "NATIVE_PROCESS",
                title = "Native CLI command failed",
                detail = "The extension uses child_process. Komorebi provides a sandboxed Android shell, but desktop binaries are unavailable.",
            )
        }

        if (lower.contains("osascript") || lower.contains("applescript")) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.INCOMPATIBLE,
                code = "APPLESCRIPT",
                title = "AppleScript unavailable",
                detail = "Extension attempted to execute macOS AppleScript / osascript.",
            )
        }

        if (lower.contains("eacces") || lower.contains("permission denied")) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.INCOMPATIBLE,
                code = "EACCES_BINARY",
                title = "Binary execution blocked (EACCES)",
                detail = "Android security policy blocks executing downloaded native binaries in app storage.",
            )
        }

        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized") ||
            lower.contains("api key") || lower.contains("missing preference") || lower.contains("token")
        ) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.PARTIAL,
                code = "AUTH_OR_PREFERENCE",
                title = "API key or authentication required",
                detail = "Check Extension Preferences to configure required API keys, tokens, or account credentials.",
            )
        }

        if (lower.contains("econnrefused") || lower.contains("enotfound") ||
            lower.contains("unable to resolve host") || lower.contains("timeout") ||
            lower.contains("failed to connect") || lower.contains("network")
        ) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.PARTIAL,
                code = "NETWORK_ERROR",
                title = "Network connection failed",
                detail = "Verify internet access, or check if the extension is trying to reach a desktop localhost daemon.",
            )
        }

        if (lower.contains("entrypoint") && lower.contains("not found")) {
            found += CompatibilityIssue(
                severity = AndroidCompatLevel.INCOMPATIBLE,
                code = "MISSING_ENTRYPOINT",
                title = "Command entrypoint missing",
                detail = "No built JavaScript file was found for this command. Source-only TypeScript extensions must be pre-built.",
            )
        }

        return found
    }

    // =========================================================================
    // 12. Shared helpers.
    // =========================================================================

    private fun readFileCapped(file: File, maxBytes: Int): String {
        return runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes)
                var offset = 0
                while (offset < maxBytes) {
                    val read = input.read(buffer, offset, maxBytes - offset)
                    if (read <= 0) break
                    offset += read
                }
                String(buffer, 0, offset, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private fun addOrUpgradeIssue(
        issues: MutableMap<String, CompatibilityIssue>,
        candidate: CompatibilityIssue,
        key: String = candidate.code,
    ) {
        val existing = issues[key]
        if (existing == null || candidate.severity.ordinal > existing.severity.ordinal) {
            issues[key] = candidate
        }
    }

    private fun worstSeverity(issues: List<CompatibilityIssue>): AndroidCompatLevel {
        return when {
            issues.any { it.severity == AndroidCompatLevel.INCOMPATIBLE } -> AndroidCompatLevel.INCOMPATIBLE
            issues.any { it.severity == AndroidCompatLevel.PARTIAL } -> AndroidCompatLevel.PARTIAL
            else -> AndroidCompatLevel.COMPATIBLE
        }
    }
}
