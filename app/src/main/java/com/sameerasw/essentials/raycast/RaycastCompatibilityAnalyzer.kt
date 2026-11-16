package com.sameerasw.essentials.raycast

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

    private val childProcessRegex = Regex("(?:node:)?child_process")
    private val spawnExecRegex = Regex("\\b(?:spawn|spawnSync|execSync|execFile|execFileSync)\\s*\\(")
    private val dotExecProcessRegex = Regex("(?:child_process|childProcess|cp|execa|shell)\\s*\\.\\s*exec\\s*\\(|(?<!/)\\.exec\\s*\\(\\s*[\"'`][^\"'`]+[\"'`]\\s*,")
    private val ooklaRegex = Regex("ookla-speedtest|install\\.speedtest\\.net", RegexOption.IGNORE_CASE)
    private val appleScriptRegex = Regex("\\bosascript\\b|\\brunAppleScript\\b|run-applescript|tell application", RegexOption.IGNORE_CASE)
    private val nativeAddonRegex = Regex("\\bdlopen\\b|\\bbindings\\s*\\(|\\bnode-gyp\\b|\\bprebuild-install\\b|\\bffi-napi\\b|\\bkoffi\\b|\\.node[\"'`]")
    private val desktopPathsRegex = Regex("/Applications/|/opt/homebrew|/usr/local/bin|C:\\\\\\\\?Program Files|AppData\\\\\\\\?Local")
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
        val summary = buildSummary(level, finalIssues, isStorePreview = true)
        return CompatibilityReport(
            level = level,
            summary = summary,
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
    }

    fun analyzeInstalledExtension(extension: InstalledRaycastExtension): CompatibilityReport {
        val issues = linkedMapOf<String, CompatibilityIssue>()
        val detectedApis = linkedSetOf<String>()
        val manifest = extension.manifest

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

        val rootDir = File(extension.dirPath)
        if (rootDir.isDirectory) {
            val jsFiles = rootDir.walkTopDown()
                .onEnter { dir -> dir.name != ".git" }
                .filter { it.isFile && (it.extension.equals("js", ignoreCase = true) || it.extension.equals("cjs", ignoreCase = true)) }
                .sortedBy { file ->
                    val rel = file.relativeTo(rootDir).path
                    if (rel.contains("node_modules")) 1 else 0
                }
                .take(MAX_SCAN_FILES)
                .toList()

            for (file in jsFiles) {
                val content = readFileCapped(file, MAX_FILE_BYTES)
                if (content.isBlank()) continue
                scanJsContent(content, issues, detectedApis)
            }
        }

        val finalIssues = issues.values.toList()
        val level = worstSeverity(finalIssues)
        val summary = buildSummary(level, finalIssues, isStorePreview = false)
        return CompatibilityReport(
            level = level,
            summary = summary,
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
    }

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
            ?: buildSummary(level, finalIssues, isStorePreview = false)
        return CompatibilityReport(
            level = level,
            summary = leadMessage,
            issues = finalIssues,
            detectedApis = detectedApis.toList(),
        )
    }

    private fun scanJsContent(
        content: String,
        issues: MutableMap<String, CompatibilityIssue>,
        detectedApis: MutableSet<String>,
    ) {
        if (childProcessRegex.containsMatchIn(content) ||
            spawnExecRegex.containsMatchIn(content) ||
            dotExecProcessRegex.containsMatchIn(content)
        ) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "NATIVE_PROCESS",
                    title = "Uses shell commands via child_process",
                    detail = "Commands run through a sandboxed Android shell. Desktop-only binaries may not be available.",
                ),
            )
            detectedApis += "node:child_process"
        }

        if (ooklaRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "DESKTOP_BINARY_DOWNLOAD",
                    title = "Desktop binary download",
                    detail = "Downloads macOS/Windows Ookla Speedtest CLI binary.",
                ),
            )
            detectedApis += "ookla-speedtest"
        }

        if (appleScriptRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "APPLESCRIPT",
                    title = "Requires macOS AppleScript",
                    detail = "Requires macOS AppleScript / osascript.",
                ),
            )
            detectedApis += "AppleScript"
        }

        if (nativeAddonRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "NATIVE_ADDON",
                    title = "Native .node addon",
                    detail = "Loads compiled native .node addon.",
                ),
            )
            detectedApis += "native-addon"
        }

        if (desktopPathsRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "DESKTOP_PATHS",
                    title = "Desktop filesystem paths",
                    detail = "Targets macOS/Windows filesystem paths.",
                ),
            )
            detectedApis += "desktop-fs"
        }

        if (platformCompareRegex.containsMatchIn(content)) {
            val rejectsUnsupported = platformGateThrowRegex.containsMatchIn(content) &&
                throwOrErrorRegex.containsMatchIn(content)
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = if (rejectsUnsupported) AndroidCompatLevel.INCOMPATIBLE else AndroidCompatLevel.PARTIAL,
                    code = "PLATFORM_CHECK",
                    title = "Platform check",
                    detail = if (rejectsUnsupported) {
                        "Checks for macOS/Windows platform and rejects unsupported platforms."
                    } else {
                        "Checks for macOS/Windows platform."
                    },
                ),
            )
            detectedApis += "process.platform"
        }

        if (sqliteRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "SQLITE_NATIVE",
                    title = "Desktop SQLite native module",
                    detail = "Uses desktop SQLite native module (useSQL).",
                ),
            )
            detectedApis += "useSQL"
        }

        if (windowManagementRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.INCOMPATIBLE,
                    code = "WINDOW_MANAGEMENT",
                    title = "WindowManagement API",
                    detail = "Desktop WindowManagement API is not supported on Android.",
                ),
            )
            detectedApis += "WindowManagement"
        }

        if (menuBarExtraRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "MENU_BAR",
                    title = "MenuBarExtra fallback",
                    detail = "Menu bar command runs in fallback view mode on Android.",
                ),
            )
            detectedApis += "MenuBarExtra"
        }

        if (browserExtRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "BROWSER_EXT",
                    title = "BrowserExtension bridge",
                    detail = "Desktop browser extension bridge is unavailable on Android.",
                ),
            )
            detectedApis += "BrowserExtension"
        }

        if (selectedTextRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "SELECTED_TEXT",
                    title = "Selected text capture",
                    detail = "Global selected text capture is restricted on Android.",
                ),
            )
            detectedApis += "getSelectedText"
        }

        if (aiApiRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "RAYCAST_AI",
                    title = "Raycast AI runtime",
                    detail = "Requires Raycast Pro AI runtime.",
                ),
            )
            detectedApis += "AI"
        }

        if (chmodRegex.containsMatchIn(content)) {
            addOrUpgradeIssue(
                issues,
                CompatibilityIssue(
                    severity = AndroidCompatLevel.PARTIAL,
                    code = "EXECUTABLE_CHMOD",
                    title = "Executable chmod",
                    detail = "Marks downloaded file executable (Android blocks executing downloaded binaries in app storage).",
                ),
            )
            detectedApis += "fs.chmod"
        }

        for ((apiName, regex) in knownSafeApis) {
            if (regex.containsMatchIn(content)) {
                detectedApis += apiName
            }
        }
    }

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
    ) {
        val existing = issues[candidate.code]
        if (existing == null || candidate.severity.ordinal > existing.severity.ordinal) {
            issues[candidate.code] = candidate
        }
    }

    private fun worstSeverity(issues: List<CompatibilityIssue>): AndroidCompatLevel {
        return when {
            issues.any { it.severity == AndroidCompatLevel.INCOMPATIBLE } -> AndroidCompatLevel.INCOMPATIBLE
            issues.any { it.severity == AndroidCompatLevel.PARTIAL } -> AndroidCompatLevel.PARTIAL
            else -> AndroidCompatLevel.COMPATIBLE
        }
    }

    private fun buildSummary(
        level: AndroidCompatLevel,
        issues: List<CompatibilityIssue>,
        isStorePreview: Boolean,
    ): String {
        return when (level) {
            AndroidCompatLevel.COMPATIBLE -> if (isStorePreview) {
                "No desktop-only APIs or binary dependencies detected in store metadata."
            } else {
                "Pure JavaScript / Raycast API extension; ready to run on Android."
            }
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
}
