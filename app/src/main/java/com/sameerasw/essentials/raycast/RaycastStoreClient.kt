package com.sameerasw.essentials.raycast

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class StoreListingAuthor(
    val name: String = "",
    val handle: String = "",
    val avatar: String? = null,
    val profileUrl: String? = null,
)

data class StoreListingIcons(
    val light: String? = null,
    val dark: String? = null,
)

data class StoreListingCommand(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val description: String = "",
    val mode: String = "view",
    val keywords: List<String> = emptyList(),
    val disabledByDefault: Boolean = false,
    val beta: Boolean = false,
)

data class StoreExtensionListing(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val author: StoreListingAuthor = StoreListingAuthor(),
    val icons: StoreListingIcons = StoreListingIcons(),
    @SerializedName("download_count")
    val downloadCount: Int = 0,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    val categories: List<String> = emptyList(),
    val commands: List<StoreListingCommand> = emptyList(),
    val source: String = "raycast",
    val platforms: List<String> = emptyList(),
    @SerializedName("api_version")
    val apiVersion: String? = null,
) {
    val iconUrl: String?
        get() = icons.dark?.takeIf { it.isNotBlank() } ?: icons.light?.takeIf { it.isNotBlank() }
}

private data class RaycastStoreAuthorDto(
    val name: String? = null,
    val handle: String? = null,
    val avatar: String? = null,
    @SerializedName(value = "profileUrl", alternate = ["profile_url"])
    val profileUrl: String? = null,
)

private data class RaycastStoreIconsDto(
    val light: String? = null,
    val dark: String? = null,
)

private data class RaycastStoreCommandDto(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val mode: String? = null,
    val keywords: List<String?>? = null,
    @SerializedName(value = "disabledByDefault", alternate = ["disabled_by_default"])
    val disabledByDefault: Boolean? = null,
    val beta: Boolean? = null,
)

private data class RaycastStoreExtensionDto(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: RaycastStoreAuthorDto? = null,
    val icons: RaycastStoreIconsDto? = null,
    @SerializedName(value = "download_count", alternate = ["downloadCount"])
    val downloadCount: Int? = null,
    @SerializedName(value = "download_url", alternate = ["downloadUrl"])
    val downloadUrl: String? = null,
    val categories: JsonElement? = null,
    val commands: List<RaycastStoreCommandDto>? = null,
    val platforms: JsonElement? = null,
    @SerializedName(value = "api_version", alternate = ["apiVersion"])
    val apiVersion: String? = null,
)

private data class RaycastStoreResponse(
    val data: List<RaycastStoreExtensionDto>? = null,
)

data class VicinaeCategoryDto(
    val id: String = "",
    val name: String = "",
)

private data class VicinaeStoreResponse(
    val extensions: List<VicinaeStoreExtensionDto>? = null,
    val data: List<VicinaeStoreExtensionDto>? = null,
)

private data class VicinaeStoreAuthorDto(
    val name: String? = null,
    val handle: String? = null,
    @SerializedName(value = "avatarUrl", alternate = ["avatar_url", "avatar"])
    val avatarUrl: String? = null,
    @SerializedName(value = "profileUrl", alternate = ["profile_url"])
    val profileUrl: String? = null,
)

private data class VicinaeStoreIconsDto(
    val light: String? = null,
    val dark: String? = null,
)

private data class VicinaeStoreCommandDto(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val mode: String? = null,
    val keywords: List<String?>? = null,
    @SerializedName(value = "disabledByDefault", alternate = ["disabled_by_default"])
    val disabledByDefault: Boolean? = null,
    val beta: Boolean? = null,
)

private data class VicinaeStoreExtensionDto(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName(value = "downloadUrl", alternate = ["download_url"])
    val downloadUrl: String? = null,
    @SerializedName(value = "downloadCount", alternate = ["download_count"])
    val downloadCount: Int? = null,
    val author: VicinaeStoreAuthorDto? = null,
    val icons: VicinaeStoreIconsDto? = null,
    val categories: JsonElement? = null,
    val platforms: JsonElement? = null,
    val commands: List<VicinaeStoreCommandDto>? = null,
    @SerializedName(value = "apiVersion", alternate = ["api_version"])
    val apiVersion: String? = null,
)

object RaycastStoreClient {
    private const val RAYCAST_BASE_URL = "https://backend.raycast.com/api/v1"
    private const val VICINAE_BASE_URL = "https://api.vicinae.com/v1"
    private const val KOMOREBI_INDEX_URL =
        "https://raw.githubusercontent.com/aislingheart/komorebi-extensions/main/index.json"
    private const val MAX_ARCHIVE_DOWNLOAD_BYTES = 25L * 1024L * 1024L
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    suspend fun fetchRaycastListings(
        query: String = "",
        page: Int = 1,
        perPage: Int = 30,
    ): Result<List<StoreExtensionListing>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (query.isBlank()) {
                "$RAYCAST_BASE_URL/store_listings?page=${page.coerceAtLeast(1)}&per_page=${perPage.coerceAtLeast(1)}"
            } else {
                val enc = URLEncoder.encode(query.trim(), "UTF-8")
                "$RAYCAST_BASE_URL/store_listings/search?q=$enc"
            }
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Komorebi-RaycastCompat/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = readErrorBodySnippet(resp.body)
                    error(
                        if (errBody.isNotBlank()) "Raycast Store HTTP ${resp.code}: $errBody"
                        else "Raycast Store HTTP ${resp.code}",
                    )
                }
                val body = resp.body.string()
                parseRaycastListingsResponse(body)
            }
        }
    }

    suspend fun fetchVicinaeListings(
        query: String = "",
        page: Int = 1,
        limit: Int = 40,
    ): Result<List<StoreExtensionListing>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (query.isBlank()) {
                "$VICINAE_BASE_URL/store/list?page=${page.coerceAtLeast(1)}&limit=${limit.coerceAtLeast(1)}"
            } else {
                val enc = URLEncoder.encode(query.trim(), "UTF-8")
                "$VICINAE_BASE_URL/store/search?q=$enc"
            }
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Komorebi-RaycastCompat/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = readErrorBodySnippet(resp.body)
                    error(
                        if (errBody.isNotBlank()) "Vicinae Store HTTP ${resp.code}: $errBody"
                        else "Vicinae Store HTTP ${resp.code}",
                    )
                }
                val body = resp.body.string()
                parseVicinaeListingsResponse(body)
            }
        }
    }

    suspend fun fetchKomorebiListings(
        query: String = "",
        page: Int = 1,
        perPage: Int = 50,
    ): Result<List<StoreExtensionListing>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(KOMOREBI_INDEX_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "Komorebi-RaycastCompat/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = readErrorBodySnippet(resp.body)
                    error(
                        if (errBody.isNotBlank()) "Komorebi Store HTTP ${resp.code}: $errBody"
                        else "Komorebi Store HTTP ${resp.code}",
                    )
                }
                val body = resp.body.string()
                val filtered = parseKomorebiListingsResponse(body).filter { it.matchesQuery(query) }
                val safePage = page.coerceAtLeast(1)
                val safePerPage = perPage.coerceAtLeast(1)
                val fromIndex = (safePage - 1) * safePerPage
                if (fromIndex >= filtered.size) emptyList()
                else filtered.drop(fromIndex).take(safePerPage)
            }
        }
    }

    internal fun parseRaycastListingsResponse(body: String): List<StoreExtensionListing> {
        val dto = gson.fromJson(body, RaycastStoreResponse::class.java)
            ?: return emptyList()
        return dto.data.orEmpty().map { ext ->
            val safeName = ext.name.orEmpty()
            StoreExtensionListing(
                id = ext.id?.takeIf { it.isNotBlank() } ?: safeName,
                name = safeName,
                title = ext.title?.takeIf { it.isNotBlank() } ?: safeName,
                description = ext.description.orEmpty(),
                author = StoreListingAuthor(
                    name = ext.author?.name.orEmpty(),
                    handle = ext.author?.handle.orEmpty(),
                    avatar = ext.author?.avatar?.takeIf { it.isNotBlank() },
                    profileUrl = ext.author?.profileUrl?.takeIf { it.isNotBlank() },
                ),
                icons = StoreListingIcons(
                    light = ext.icons?.light?.takeIf { it.isNotBlank() },
                    dark = ext.icons?.dark?.takeIf { it.isNotBlank() },
                ),
                downloadCount = ext.downloadCount ?: 0,
                downloadUrl = ext.downloadUrl?.takeIf { it.isNotBlank() },
                categories = parseFlexibleCategories(ext.categories),
                commands = ext.commands.orEmpty().map { cmd ->
                    val cmdName = cmd.name.orEmpty()
                    StoreListingCommand(
                        id = cmd.id?.takeIf { it.isNotBlank() } ?: cmdName,
                        name = cmdName,
                        title = cmd.title?.takeIf { it.isNotBlank() } ?: cmdName,
                        subtitle = cmd.subtitle?.takeIf { it.isNotBlank() },
                        description = cmd.description.orEmpty(),
                        mode = cmd.mode?.takeIf { it.isNotBlank() } ?: "view",
                        keywords = cmd.keywords.orEmpty().filterNotNull().filter { it.isNotBlank() },
                        disabledByDefault = cmd.disabledByDefault ?: false,
                        beta = cmd.beta ?: false,
                    )
                },
                source = "raycast",
                platforms = parseStringList(ext.platforms),
                apiVersion = ext.apiVersion?.takeIf { it.isNotBlank() },
            )
        }
    }

    internal fun parseVicinaeListingsResponse(body: String): List<StoreExtensionListing> {
        val dto = gson.fromJson(body, VicinaeStoreResponse::class.java)
            ?: return emptyList()
        val list = dto.extensions ?: dto.data ?: emptyList()
        return list.map { ext ->
            val safeName = ext.name.orEmpty()
            StoreExtensionListing(
                id = ext.id?.takeIf { it.isNotBlank() } ?: safeName,
                name = safeName,
                title = ext.title?.takeIf { it.isNotBlank() } ?: safeName,
                description = ext.description.orEmpty(),
                author = StoreListingAuthor(
                    name = ext.author?.name.orEmpty(),
                    handle = ext.author?.handle.orEmpty(),
                    avatar = ext.author?.avatarUrl?.takeIf { it.isNotBlank() },
                    profileUrl = ext.author?.profileUrl?.takeIf { it.isNotBlank() },
                ),
                icons = StoreListingIcons(
                    light = ext.icons?.light?.takeIf { it.isNotBlank() },
                    dark = ext.icons?.dark?.takeIf { it.isNotBlank() },
                ),
                downloadCount = ext.downloadCount ?: 0,
                downloadUrl = ext.downloadUrl?.takeIf { it.isNotBlank() },
                categories = parseFlexibleCategories(ext.categories),
                commands = ext.commands.orEmpty().map { cmd ->
                    val cmdName = cmd.name.orEmpty()
                    StoreListingCommand(
                        id = cmd.id?.takeIf { it.isNotBlank() } ?: cmdName,
                        name = cmdName,
                        title = cmd.title?.takeIf { it.isNotBlank() } ?: cmdName,
                        subtitle = cmd.subtitle?.takeIf { it.isNotBlank() },
                        description = cmd.description.orEmpty(),
                        mode = cmd.mode?.takeIf { it.isNotBlank() } ?: "view",
                        keywords = cmd.keywords.orEmpty().filterNotNull().filter { it.isNotBlank() },
                        disabledByDefault = cmd.disabledByDefault ?: false,
                        beta = cmd.beta ?: false,
                    )
                },
                source = "vicinae",
                platforms = parseStringList(ext.platforms),
                apiVersion = ext.apiVersion?.takeIf { it.isNotBlank() },
            )
        }
    }

    internal fun parseKomorebiListingsResponse(body: String): List<StoreExtensionListing> {
        val dto = gson.fromJson(body, VicinaeStoreResponse::class.java)
            ?: return emptyList()
        val list = dto.extensions ?: dto.data ?: emptyList()
        return list.map { ext ->
            val safeName = ext.name.orEmpty()
            StoreExtensionListing(
                id = ext.id?.takeIf { it.isNotBlank() } ?: safeName,
                name = safeName,
                title = ext.title?.takeIf { it.isNotBlank() } ?: safeName,
                description = ext.description.orEmpty(),
                author = StoreListingAuthor(
                    name = ext.author?.name.orEmpty(),
                    handle = ext.author?.handle.orEmpty(),
                    avatar = ext.author?.avatarUrl?.takeIf { it.isNotBlank() },
                    profileUrl = ext.author?.profileUrl?.takeIf { it.isNotBlank() },
                ),
                icons = StoreListingIcons(
                    light = ext.icons?.light?.takeIf { it.isNotBlank() },
                    dark = ext.icons?.dark?.takeIf { it.isNotBlank() },
                ),
                downloadCount = ext.downloadCount ?: 0,
                downloadUrl = ext.downloadUrl?.takeIf { it.isNotBlank() },
                categories = parseFlexibleCategories(ext.categories),
                commands = ext.commands.orEmpty().map { cmd ->
                    val cmdName = cmd.name.orEmpty()
                    StoreListingCommand(
                        id = cmd.id?.takeIf { it.isNotBlank() } ?: cmdName,
                        name = cmdName,
                        title = cmd.title?.takeIf { it.isNotBlank() } ?: cmdName,
                        subtitle = cmd.subtitle?.takeIf { it.isNotBlank() },
                        description = cmd.description.orEmpty(),
                        mode = cmd.mode?.takeIf { it.isNotBlank() } ?: "view",
                        keywords = cmd.keywords.orEmpty().filterNotNull().filter { it.isNotBlank() },
                        disabledByDefault = cmd.disabledByDefault ?: false,
                        beta = cmd.beta ?: false,
                    )
                },
                source = "komorebi",
                platforms = parseStringList(ext.platforms),
                apiVersion = ext.apiVersion?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun StoreExtensionListing.matchesQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return sequenceOf(name, title, description)
            .plus(commands.asSequence().flatMap { cmd ->
                sequenceOf(cmd.title) + cmd.keywords.asSequence()
            })
            .any { it.contains(trimmed, ignoreCase = true) }
    }

    internal fun parseFlexibleCategories(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                extractCategoryLabel(item)
            }
            else -> listOfNotNull(extractCategoryLabel(element))
        }
    }

    private fun extractCategoryLabel(item: JsonElement?): String? {
        if (item == null || item.isJsonNull) return null
        if (item.isJsonPrimitive) {
            return item.asString.trim().takeIf { it.isNotBlank() }
        }
        if (item.isJsonObject) {
            val cat = runCatching { gson.fromJson(item, VicinaeCategoryDto::class.java) }.getOrNull()
            val fromName = cat?.name?.trim()?.takeIf { it.isNotBlank() }
            val fromId = cat?.id?.trim()?.takeIf { it.isNotBlank() }
            if (fromName != null || fromId != null) return fromName ?: fromId
            val obj = item.asJsonObject
            val rawName = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val rawId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            return rawName?.takeIf { it.isNotBlank() } ?: rawId?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                if (item != null && item.isJsonPrimitive) item.asString.trim().takeIf { it.isNotBlank() }
                else null
            }
            element.isJsonPrimitive -> listOfNotNull(element.asString.trim().takeIf { it.isNotBlank() })
            else -> emptyList()
        }
    }

    private fun readErrorBodySnippet(body: ResponseBody?): String {
        if (body == null) return ""
        return runCatching {
            body.string()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(280)
        }.getOrDefault("")
    }

    suspend fun installExtension(
        context: Context,
        listing: StoreExtensionListing,
    ): Result<InstalledRaycastExtension> = withContext(Dispatchers.IO) {
        runCatching {
            val url = listing.downloadUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: error("No download URL provided for ${listing.title}")
            val parsedUrl = url.toHttpUrlOrNull()
                ?: error("Invalid download URL for ${listing.title}")
            require(parsedUrl.scheme.equals("https", ignoreCase = true)) {
                "Refusing insecure non-HTTPS extension download URL: ${parsedUrl.scheme}"
            }

            val req = Request.Builder()
                .url(parsedUrl)
                .header("User-Agent", "Komorebi-RaycastCompat/1.0")
                .build()

            val cacheDir = context.applicationContext.cacheDir.apply { mkdirs() }
            val tempArchive = File.createTempFile("raycast_ext_", ".pkg", cacheDir)
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = readErrorBodySnippet(resp.body)
                        error(
                            if (errBody.isNotBlank()) "Download failed (HTTP ${resp.code}): $errBody"
                            else "Download failed (HTTP ${resp.code})",
                        )
                    }
                    require(resp.request.url.isHttps) {
                        "Refusing insecure non-HTTPS extension download redirect."
                    }
                    val contentLength = resp.body.contentLength()
                    if (contentLength > MAX_ARCHIVE_DOWNLOAD_BYTES) {
                        error(
                            "Extension archive exceeds maximum download size (${MAX_ARCHIVE_DOWNLOAD_BYTES / (1024 * 1024)} MB).",
                        )
                    }
                    resp.body.byteStream().use { input ->
                        tempArchive.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloadedBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                downloadedBytes += read
                                if (downloadedBytes > MAX_ARCHIVE_DOWNLOAD_BYTES) {
                                    error(
                                        "Extension archive exceeds maximum download size (${MAX_ARCHIVE_DOWNLOAD_BYTES / (1024 * 1024)} MB).",
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }

                val safeFolder = sanitizeExtensionFolder(listing.id.ifBlank { listing.name })
                val extensionsRoot = RaycastExtensionRepository.extensionsRootDir(context).canonicalFile
                val targetDir = File(extensionsRoot, safeFolder).canonicalFile
                require(targetDir.parentFile == extensionsRoot) {
                    "Invalid extension target folder: $safeFolder"
                }

                val stagingDir = File(extensionsRoot, ".staging_${safeFolder}_${System.nanoTime()}")
                stagingDir.mkdirs()
                try {
                    extractArchiveSafely(tempArchive, stagingDir)
                    flattenSingleNestedDirIfNeeded(stagingDir)

                    val pkgFile = File(stagingDir, "package.json")
                    if (!pkgFile.exists()) {
                        val fallbackManifest = RaycastPackageManifest(
                            name = listing.name,
                            title = listing.title.ifBlank { listing.name },
                            description = listing.description,
                            author = listing.author.name.ifBlank { listing.author.handle },
                            categories = listing.categories.takeIf { it.isNotEmpty() },
                            platforms = listing.platforms.takeIf { it.isNotEmpty() },
                            commands = listing.commands.map {
                                RaycastCommandManifest(
                                    name = it.name,
                                    title = it.title.ifBlank { it.name },
                                    subtitle = it.subtitle,
                                    description = it.description,
                                    mode = it.mode.ifBlank { "view" },
                                    keywords = it.keywords.takeIf { k -> k.isNotEmpty() },
                                    disabledByDefault = it.disabledByDefault.takeIf { d -> d },
                                )
                            },
                        )
                        pkgFile.writeText(gson.toJson(fallbackManifest))
                    }

                    if (targetDir.exists()) targetDir.deleteRecursively()
                    if (!stagingDir.renameTo(targetDir)) {
                        stagingDir.copyRecursively(targetDir, overwrite = true)
                        stagingDir.deleteRecursively()
                    }
                } finally {
                    if (stagingDir.exists()) stagingDir.deleteRecursively()
                }

                RaycastExtensionRepository.findExtension(context, safeFolder)
                    ?: error("Installed extension manifest could not be parsed.")
            } finally {
                runCatching { tempArchive.delete() }
            }
        }
    }

    internal fun sanitizeExtensionFolder(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .replace(Regex("\\.{2,}"), "_")
            .trim('.')
        return cleaned.ifBlank { "extension_${System.currentTimeMillis()}" }
    }

    private fun extractArchiveSafely(archiveFile: File, destDir: File) {
        val maxEntries = 10_000
        val maxTotalBytes = 100L * 1024L * 1024L
        var entryCount = 0
        var totalBytes = 0L

        fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream) {
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalBytes += read
                if (totalBytes > maxTotalBytes) {
                    error("Extension archive exceeds maximum extracted size limit.")
                }
                output.write(buffer, 0, read)
            }
        }

        val magic = ByteArray(2)
        val magicRead = FileInputStream(archiveFile).use { it.read(magic) }
        val isZip = magicRead == 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
        if (isZip) {
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (++entryCount > maxEntries) {
                        error("Extension archive contains too many entries.")
                    }
                    if (!entry.isDirectory) {
                        val outFile = safeChildFile(destDir, entry.name)
                        if (outFile != null) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { copyBounded(zis, it) }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            return
        }

        val isGzip = magicRead == 2 && magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()
        val rawStream = BufferedInputStream(FileInputStream(archiveFile))
        val tarStream = if (isGzip) {
            TarArchiveInputStream(GzipCompressorInputStream(rawStream))
        } else {
            TarArchiveInputStream(rawStream)
        }
        tarStream.use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                if (++entryCount > maxEntries) {
                    error("Extension archive contains too many entries.")
                }
                if (!entry.isDirectory) {
                    val outFile = safeChildFile(destDir, entry.name)
                    if (outFile != null) {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { copyBounded(tis, it) }
                    }
                }
                entry = tis.nextEntry
            }
        }
    }

    internal fun safeChildFile(destDir: File, entryName: String): File? {
        val clean = entryName.trimStart('/', '\\')
        if (clean.isBlank()) return null
        val destCanonical = destDir.canonicalFile
        val out = File(destCanonical, clean).canonicalFile
        return if (out.path.startsWith(destCanonical.path + File.separator)) out else null
    }

    internal fun flattenSingleNestedDirIfNeeded(destDir: File) {
        if (File(destDir, "package.json").exists()) return
        val children = destDir.listFiles() ?: return
        if (children.size == 1 && children[0].isDirectory) {
            val nested = children[0]
            val tempNested = File(destDir, ".flatten_tmp_${System.nanoTime()}")
            val sourceDir = if (nested.renameTo(tempNested)) tempNested else nested
            sourceDir.listFiles()?.forEach { child ->
                val target = File(destDir, child.name)
                if (!child.renameTo(target)) {
                    child.copyRecursively(target, overwrite = true)
                    child.deleteRecursively()
                }
            }
            sourceDir.deleteRecursively()
        }
    }
}
