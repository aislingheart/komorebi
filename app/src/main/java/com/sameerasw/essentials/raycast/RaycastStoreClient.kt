package com.sameerasw.essentials.raycast

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class StoreListingAuthor(
    val name: String = "",
    val handle: String = "",
    val avatar: String? = null,
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
) {
    val iconUrl: String?
        get() = icons.dark?.takeIf { it.isNotBlank() } ?: icons.light?.takeIf { it.isNotBlank() }
}

private data class RaycastStoreResponse(
    val data: List<StoreExtensionListing> = emptyList(),
)

private data class VicinaeStoreResponse(
    val extensions: List<VicinaeStoreExtensionDto> = emptyList(),
)

private data class VicinaeStoreAuthorDto(
    val name: String = "",
    val handle: String = "",
)

private data class VicinaeStoreExtensionDto(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val downloadUrl: String = "",
    val downloadCount: Int = 0,
    val author: VicinaeStoreAuthorDto = VicinaeStoreAuthorDto(),
    val icons: StoreListingIcons = StoreListingIcons(),
    val categories: List<String> = emptyList(),
    val commands: List<StoreListingCommand> = emptyList(),
)

object RaycastStoreClient {
    private const val RAYCAST_BASE_URL = "https://backend.raycast.com/api/v1"
    private const val VICINAE_BASE_URL = "https://api.vicinae.com/v1"
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchRaycastListings(
        query: String = "",
        page: Int = 1,
        perPage: Int = 30,
    ): Result<List<StoreExtensionListing>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (query.isBlank()) {
                "$RAYCAST_BASE_URL/store_listings?page=$page&per_page=$perPage"
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
                if (!resp.isSuccessful) error("Raycast Store HTTP ${resp.code}")
                val body = resp.body.string()
                gson.fromJson(body, RaycastStoreResponse::class.java).data.map {
                    it.copy(source = "raycast")
                }
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
                "$VICINAE_BASE_URL/store/list?page=$page&limit=$limit"
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
                if (!resp.isSuccessful) error("Vicinae Store HTTP ${resp.code}")
                val body = resp.body.string()
                val dto = gson.fromJson(body, VicinaeStoreResponse::class.java)
                dto.extensions.map { ext ->
                    StoreExtensionListing(
                        id = ext.id.ifBlank { ext.name },
                        name = ext.name,
                        title = ext.title.ifBlank { ext.name },
                        description = ext.description,
                        author = StoreListingAuthor(name = ext.author.name, handle = ext.author.handle),
                        icons = ext.icons,
                        downloadCount = ext.downloadCount,
                        downloadUrl = ext.downloadUrl,
                        categories = ext.categories,
                        commands = ext.commands,
                        source = "vicinae",
                    )
                }
            }
        }
    }

    suspend fun installExtension(
        context: Context,
        listing: StoreExtensionListing,
    ): Result<InstalledRaycastExtension> = withContext(Dispatchers.IO) {
        runCatching {
            val url = listing.downloadUrl?.takeIf { it.isNotBlank() }
                ?: error("No download URL provided for ${listing.title}")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Komorebi-RaycastCompat/1.0")
                .build()
            val bytes = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed (HTTP ${resp.code})")
                resp.body.bytes()
            }

            val safeFolder = "${listing.author.handle.ifBlank { "ext" }}-${listing.name}"
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetDir = File(RaycastExtensionRepository.extensionsRootDir(context), safeFolder)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            extractArchiveSafely(bytes, targetDir)
            flattenSingleNestedDirIfNeeded(targetDir)

            val pkgFile = File(targetDir, "package.json")
            if (!pkgFile.exists()) {
                val fallbackManifest = RaycastPackageManifest(
                    name = listing.name,
                    title = listing.title.ifBlank { listing.name },
                    description = listing.description,
                    author = listing.author.name.ifBlank { listing.author.handle },
                    categories = listing.categories,
                    commands = listing.commands.map {
                        RaycastCommandManifest(
                            name = it.name,
                            title = it.title.ifBlank { it.name },
                            subtitle = it.subtitle,
                            description = it.description,
                            mode = it.mode.ifBlank { "view" },
                        )
                    },
                )
                pkgFile.writeText(gson.toJson(fallbackManifest))
            }

            RaycastExtensionRepository.findExtension(context, safeFolder)
                ?: error("Installed extension manifest could not be parsed.")
        }
    }

    private fun extractArchiveSafely(bytes: ByteArray, destDir: File) {
        val isZip = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (isZip) {
            ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = safeChildFile(destDir, entry.name)
                        if (outFile != null) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            return
        }

        val isGzip = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
        val rawStream = BufferedInputStream(ByteArrayInputStream(bytes))
        val tarStream = if (isGzip) {
            TarArchiveInputStream(GzipCompressorInputStream(rawStream))
        } else {
            TarArchiveInputStream(rawStream)
        }
        tarStream.use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = safeChildFile(destDir, entry.name)
                    if (outFile != null) {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { tis.copyTo(it) }
                    }
                }
                entry = tis.nextEntry
            }
        }
    }

    private fun safeChildFile(destDir: File, entryName: String): File? {
        val clean = entryName.trimStart('/', '\\')
        if (clean.isBlank()) return null
        val destCanonical = destDir.canonicalFile
        val out = File(destCanonical, clean).canonicalFile
        return if (out.path.startsWith(destCanonical.path + File.separator)) out else null
    }

    private fun flattenSingleNestedDirIfNeeded(destDir: File) {
        if (File(destDir, "package.json").exists()) return
        val children = destDir.listFiles() ?: return
        if (children.size == 1 && children[0].isDirectory) {
            val nested = children[0]
            nested.listFiles()?.forEach { child ->
                child.renameTo(File(destDir, child.name))
            }
            nested.deleteRecursively()
        }
    }
}
