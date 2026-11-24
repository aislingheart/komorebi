package com.sameerasw.essentials.raycast

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sameerasw.essentials.assistant.ScreenContextExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RaycastToastEvent(
    val id: String,
    val title: String,
    val message: String,
    val style: String,
    val hidden: Boolean = false,
)

data class RaycastAlertRequest(
    val title: String,
    val message: String,
    val primaryTitle: String,
    val primaryStyle: String,
    val dismissTitle: String,
    val deferred: CompletableDeferred<Boolean>,
)

class RaycastRuntimeEngine(
    context: Context,
    private val extension: InstalledRaycastExtension,
    private val command: RaycastCommandManifest,
) {
    private val context: Context = context.applicationContext

    companion object {
        private const val MAX_HTTP_REDIRECTS = 5
        private const val MAX_HTTP_BODY_BYTES = 4 * 1024 * 1024
        private const val MAX_FS_READ_BYTES = 2 * 1024 * 1024
        private val ALLOWED_OPEN_URI_SCHEMES = setOf("https", "http", "mailto", "geo")

        internal fun isDisallowedAddress(addr: InetAddress): Boolean {
            if (
                addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress
            ) {
                return true
            }
            val raw = addr.address
            if (raw.size == 4) {
                return isDisallowedIpv4Bytes(
                    raw[0].toInt() and 0xFF,
                    raw[1].toInt() and 0xFF,
                    raw[2].toInt() and 0xFF,
                    raw[3].toInt() and 0xFF,
                )
            }
            if (raw.size == 16) {
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                // IPv6 Unique Local Addresses (fc00::/7)
                if ((b0 and 0xFE) == 0xFC) return true
                // IPv6 Link-Local (fe80::/10) and deprecated Site-Local (fec0::/10)
                if (b0 == 0xFE && ((b1 and 0xC0) == 0x80 || (b1 and 0xC0) == 0xC0)) return true
                // IPv6 Multicast (ff00::/8)
                if (b0 == 0xFF) return true

                // IPv4-mapped IPv6 (::ffff:0:0/96)
                val isIpv4Mapped = (0 until 10).all { raw[it] == 0.toByte() } &&
                    raw[10] == 0xFF.toByte() && raw[11] == 0xFF.toByte()
                // IPv4-compatible IPv6 (::w.x.y.z)
                val isIpv4Compatible = (0 until 12).all { raw[it] == 0.toByte() }
                // NAT64 well-known prefix (64:ff9b::/96)
                val isNat64 = b0 == 0x00 && b1 == 0x64 &&
                    (raw[2].toInt() and 0xFF) == 0xFF &&
                    (raw[3].toInt() and 0xFF) == 0x9B &&
                    (4 until 12).all { raw[it] == 0.toByte() }

                if (isIpv4Mapped || isIpv4Compatible || isNat64) {
                    return isDisallowedIpv4Bytes(
                        raw[12].toInt() and 0xFF,
                        raw[13].toInt() and 0xFF,
                        raw[14].toInt() and 0xFF,
                        raw[15].toInt() and 0xFF,
                    )
                }
            }
            return false
        }

        private fun isDisallowedIpv4Bytes(b0: Int, b1: Int, b2: Int, b3: Int): Boolean {
            if (b0 == 0) return true // 0.0.0.0/8
            if (b0 == 10) return true // 10.0.0.0/8 (RFC1918)
            if (b0 == 100 && (b1 and 0xC0) == 64) return true // 100.64.0.0/10 (CGNAT)
            if (b0 == 127) return true // 127.0.0.0/8 (loopback)
            if (b0 == 169 && b1 == 254) return true // 169.254.0.0/16 (link-local / metadata)
            if (b0 == 172 && (b1 and 0xF0) == 16) return true // 172.16.0.0/12 (RFC1918)
            if (b0 == 192 && b1 == 0 && b2 == 0) return true // 192.0.0.0/24
            if (b0 == 192 && b1 == 168) return true // 192.168.0.0/16 (RFC1918)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true // 198.18.0.0/15
            if (b0 >= 224) return true // 224.0.0.0/4 multicast + 240.0.0.0/4 reserved
            return false
        }

        private val safeDns = Dns { hostname ->
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty()) {
                throw UnknownHostException("Unable to resolve host: $hostname")
            }
            for (addr in addresses) {
                if (isDisallowedAddress(addr)) {
                    throw SecurityException("Blocked network request to restricted address for host: $hostname")
                }
            }
            addresses
        }

        internal fun validateOutboundHttpUrl(httpUrl: HttpUrl) {
            val scheme = httpUrl.scheme.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") {
                throw IllegalArgumentException("Only http and https URLs are allowed")
            }
            val host = httpUrl.host.lowercase(Locale.ROOT)
            if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
                throw SecurityException("Blocked network request to restricted host: $host")
            }
            safeDns.lookup(httpUrl.host)
        }

        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(safeDns)
                .build()
        }

        private val warmDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
        private val warmPoolScope = CoroutineScope(SupervisorJob() + warmDispatcher)
        private val warmLock = Any()

        @Volatile
        private var cachedRuntimeJs: String? = null

        private data class WarmIsolate(
            val quickJs: QuickJs,
            val engineRef: AtomicReference<RaycastRuntimeEngine?>,
        )

        @Volatile
        private var standbyIsolate: WarmIsolate? = null

        @Volatile
        private var warmingInProgress = false

        private fun loadRuntimeJs(context: Context): String {
            cachedRuntimeJs?.let { return it }
            val loaded = context.applicationContext.assets
                .open("raycast/runtime.js")
                .bufferedReader()
                .use { it.readText() }
            cachedRuntimeJs = loaded
            return loaded
        }

        /**
         * Pre-boots a single QuickJS isolate with `runtime.js` already evaluated in the background
         * so subsequent extension launches skip parsing the 6K-line runtime on the critical path.
         */
        fun prewarm(context: Context) {
            val appContext = context.applicationContext
            synchronized(warmLock) {
                if (standbyIsolate != null || warmingInProgress) return
                warmingInProgress = true
            }
            warmPoolScope.launch {
                val created = runCatching { buildWarmIsolate(appContext) }.getOrNull()
                synchronized(warmLock) {
                    warmingInProgress = false
                    if (created != null) {
                        if (standbyIsolate == null) {
                            standbyIsolate = created
                        } else {
                            created.engineRef.set(null)
                            runCatching { created.quickJs.close() }
                        }
                    }
                }
            }
        }

        private suspend fun buildWarmIsolate(appContext: Context): WarmIsolate {
            val engineRef = AtomicReference<RaycastRuntimeEngine?>(null)
            val qjs = QuickJs.create(Dispatchers.IO)
            qjs.memoryLimit = 64L * 1024L * 1024L
            qjs.maxStackSize = 1024L * 1024L
            registerDelegatingBindings(qjs, engineRef)
            val runtimeJs = loadRuntimeJs(appContext)
            qjs.evaluate<Any?>(runtimeJs, "runtime.js", false)
            return WarmIsolate(qjs, engineRef)
        }

        private fun claimStandby(engine: RaycastRuntimeEngine): WarmIsolate? {
            val claimed = synchronized(warmLock) {
                val iso = standbyIsolate
                standbyIsolate = null
                iso
            } ?: return null
            claimed.engineRef.set(engine)
            return claimed
        }

        private fun registerDelegatingBindings(
            qjs: QuickJs,
            engineRef: AtomicReference<RaycastRuntimeEngine?>,
        ) {
            qjs.function("__nativeLog") { args ->
                val level = args.getOrNull(0)?.toString() ?: "LOG"
                val msg = args.getOrNull(1)?.toString() ?: ""
                Log.d("RaycastRuntime/$level", msg)
                null
            }

            qjs.function("__emitRender") { args ->
                val engine = engineRef.get() ?: return@function null
                val treeJson = args.getOrNull(0)?.toString() ?: return@function null
                engine.onEmitRender(treeJson)
                null
            }

            qjs.asyncFunction("__scheduleTimeout") { args ->
                val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
                if (ms > 0) delay(ms)
                true
            }

            qjs.asyncFunction("__nativeFetch") { args ->
                val engine = engineRef.get()
                    ?: return@asyncFunction """{"error":"Runtime not attached"}"""
                val reqJson = args.getOrNull(0)?.toString() ?: "{}"
                engine.executeHttpFetch(reqJson)
            }

            qjs.function("__hostCallSync") { args ->
                val engine = engineRef.get() ?: return@function null
                val method = args.getOrNull(0)?.toString() ?: ""
                val payloadJson = args.getOrNull(1)?.toString() ?: "{}"
                engine.handleSyncHostCall(method, payloadJson)
            }

            qjs.asyncFunction("__hostCall") { args ->
                val engine = engineRef.get() ?: return@asyncFunction null
                val method = args.getOrNull(0)?.toString() ?: ""
                val payloadJson = args.getOrNull(1)?.toString() ?: "{}"
                engine.handleAsyncHostCall(method, payloadJson)
            }
        }
    }

    private val tag = "RaycastRuntime"
    private val gson = Gson()
    private val jsDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val evalJob = SupervisorJob()
    private val scope = CoroutineScope(evalJob + jsDispatcher)
    private val cleanupScope = CoroutineScope(SupervisorJob() + jsDispatcher)
    @Volatile
    private var quickJs: QuickJs? = null
    @Volatile
    private var activeEngineRef: AtomicReference<RaycastRuntimeEngine?>? = null
    private val closed = AtomicBoolean(false)
    private val secureRandom = SecureRandom()

    private val httpClient: OkHttpClient
        get() = sharedHttpClient

    private val _viewStack = MutableStateFlow<List<RaycastViewScreen>>(emptyList())
    val viewStack: StateFlow<List<RaycastViewScreen>> = _viewStack.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<RaycastToastEvent>(extraBufferCapacity = 16)
    val toastEvents = _toastEvents.asSharedFlow()

    private val _hudEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val hudEvents = _hudEvents.asSharedFlow()

    private val _alertRequests = MutableSharedFlow<RaycastAlertRequest>(extraBufferCapacity = 4)
    val alertRequests = _alertRequests.asSharedFlow()

    private val _closeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val closeRequests = _closeRequests.asSharedFlow()

    private val _openPreferencesRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openPreferencesRequests = _openPreferencesRequests.asSharedFlow()

    internal fun onEmitRender(treeJson: String) {
        if (closed.get()) return
        val screens = RaycastTreeParser.parseViewStack(treeJson)
        _viewStack.value = screens
    }

    fun start(
        arguments: Map<String, String> = emptyMap(),
        isDarkTheme: Boolean = true,
        launchType: String? = null,
    ) {
        if (closed.get()) return
        scope.launch {
            var unpublishedQjs: QuickJs? = null
            var unpublishedRef: AtomicReference<RaycastRuntimeEngine?>? = null
            try {
                if (closed.get()) return@launch
                val entryFile = extension.entryFileForCommand(command.name)
                if (entryFile == null) {
                    _errorState.value = buildMissingEntryDiagnostic(extension, command)
                    return@launch
                }

                val warm = claimStandby(this@RaycastRuntimeEngine)
                val (qjs, engineRef) = if (warm != null) {
                    unpublishedQjs = warm.quickJs
                    unpublishedRef = warm.engineRef
                    warm.quickJs to warm.engineRef
                } else {
                    val freshRef = AtomicReference<RaycastRuntimeEngine?>(this@RaycastRuntimeEngine)
                    val fresh = QuickJs.create(Dispatchers.IO)
                    unpublishedQjs = fresh
                    unpublishedRef = freshRef
                    fresh.memoryLimit = 64L * 1024L * 1024L
                    fresh.maxStackSize = 1024L * 1024L
                    registerDelegatingBindings(fresh, freshRef)
                    val runtimeJs = loadRuntimeJs(context)
                    fresh.evaluate<Any?>(runtimeJs, "runtime.js", false)
                    fresh to freshRef
                }
                if (closed.get()) {
                    engineRef.set(null)
                    runCatching { qjs.close() }
                    return@launch
                }
                activeEngineRef = engineRef
                quickJs = qjs
                unpublishedQjs = null
                unpublishedRef = null
                prewarm(context)

                val supportDir = RaycastExtensionRepository.supportDir(context, extension.id)
                val assetsDir = File(extension.dirPath, "assets")
                val prefsMap = RaycastExtensionRepository.getPreferenceValuesMap(context, extension, command)

                val launchConfig = mapOf(
                    "commandName" to command.name,
                    "extensionName" to extension.manifest.name,
                    "author" to extension.authorName,
                    "mode" to command.mode,
                    "supportPath" to supportDir.absolutePath,
                    "assetsPath" to assetsDir.absolutePath,
                    "appearance" to if (isDarkTheme) "dark" else "light",
                    "preferences" to prefsMap,
                    "arguments" to arguments,
                    "launchType" to (launchType ?: "User"),
                    "entryPath" to "/" + entryFile.relativeTo(File(extension.dirPath)).path.replace('\\', '/'),
                )

                val entryCode = entryFile.readText()
                val configJson = gson.toJson(launchConfig)
                val invokeCode = "globalThis.__launchCommand(${gson.toJson(configJson)}, ${gson.toJson(entryCode)});"
                qjs.evaluate<Any?>(invokeCode, entryFile.name, false)
            } catch (e: Throwable) {
                unpublishedRef?.set(null)
                unpublishedQjs?.let { runCatching { it.close() } }
                if (e is CancellationException) throw e
                Log.e(tag, "Extension launch failed", e)
                _errorState.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun invokeCallback(callbackId: String, vararg callbackArgs: Any?) {
        if (closed.get()) return
        val argsCopy = callbackArgs.toList()
        scope.launch {
            if (closed.get()) return@launch
            val qjs = quickJs ?: return@launch
            runCatching {
                val argsJson = gson.toJson(argsCopy)
                qjs.evaluate<Any?>(
                    "globalThis.__invokeCallback(${gson.toJson(callbackId)}, ${gson.toJson(argsJson)});",
                    "callback.js",
                    false,
                )
            }.onFailure { err ->
                if (err !is CancellationException) {
                    Log.e(tag, "Callback invocation error for $callbackId", err)
                }
            }
        }
    }

    fun notifyViewPopped() {
        if (closed.get()) return
        scope.launch {
            if (closed.get()) return@launch
            val qjs = quickJs ?: return@launch
            runCatching {
                qjs.evaluate<Any?>(
                    "globalThis.__dispatchHostEvent('ui.viewPopped', '{}');",
                    "event.js",
                    false,
                )
            }
        }
    }

    private fun buildMissingEntryDiagnostic(
        ext: InstalledRaycastExtension,
        cmd: RaycastCommandManifest,
    ): String {
        val dir = File(ext.dirPath)
        val allFiles = dir.walkTopDown().maxDepth(3).filter { it.isFile }.toList()
        val hasTsSource = allFiles.any { f ->
            val name = f.name.lowercase()
            name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".jsx")
        }
        val availableJs = allFiles
            .filter { it.name.endsWith(".js", ignoreCase = true) || it.name.endsWith(".cjs", ignoreCase = true) }
            .map { it.relativeTo(dir).path }
            .take(8)
        return buildString {
            append("Command entrypoint '${cmd.name}.js' was not found in ${ext.dirPath}.")
            if (hasTsSource && availableJs.isEmpty()) {
                append(" This extension appears to contain uncompiled TypeScript/JSX source files (.ts/.tsx/.jsx) rather than a compiled JavaScript bundle (dist/*.js).")
                append(" Please build the extension (npm run build) or install a pre-built release.")
            } else if (availableJs.isNotEmpty()) {
                append(" Available JS files: ${availableJs.joinToString(", ")}.")
            }
        }
    }

    private suspend fun OkHttpClient.awaitCall(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) {
                            cont.resume(response)
                        } else {
                            runCatching { response.close() }
                        }
                    }
                },
            )
        }

    private fun readResponseBodyBounded(resp: Response, maxBytes: Int = MAX_HTTP_BODY_BYTES): ByteArray {
        val contentLength = resp.body.contentLength()
        if (contentLength > maxBytes) {
            throw IOException("Response body exceeds maximum allowed size of ${maxBytes / (1024 * 1024)} MB")
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0
        resp.body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > maxBytes) {
                    throw IOException("Response body exceeds maximum allowed size of ${maxBytes / (1024 * 1024)} MB")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun readFileBytesBounded(file: File, maxBytes: Int = MAX_FS_READ_BYTES): ByteArray? {
        if (!file.exists() || !file.isFile) return null
        if (file.length() > maxBytes) {
            Log.w(tag, "Blocked reading file exceeding ${maxBytes / (1024 * 1024)} MB cap: ${file.name}")
            return null
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > maxBytes) {
                    Log.w(tag, "Blocked reading file exceeding ${maxBytes / (1024 * 1024)} MB cap: ${file.name}")
                    return null
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private suspend fun executeHttpFetch(reqJson: String): String {
        if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.NETWORK)) {
            return gson.toJson(mapOf("error" to "Network access is disabled for ${extension.manifest.title}. Enable it in Extensions permissions."))
        }
        return try {
            withContext<String>(Dispatchers.IO) {
                val obj = gson.fromJson(reqJson, JsonObject::class.java)
                val rawUrl = obj.get("url")?.asString?.trim() ?: throw IllegalArgumentException("Missing URL")
                var currentUrl = rawUrl.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Invalid URL")
                validateOutboundHttpUrl(currentUrl)

                var currentMethod = (obj.get("method")?.asString ?: "GET").uppercase(Locale.ROOT)
                val bodyBase64 = if (obj.has("bodyBase64") && !obj.get("bodyBase64").isJsonNull) obj.get("bodyBase64").asString else null
                val bodyStr = if (obj.has("body") && !obj.get("body").isJsonNull) obj.get("body").asString else null

                var contentType = "application/json"
                val currentHeaders = linkedMapOf<String, String>()
                val headersObj = obj.getAsJsonObject("headers")
                headersObj?.entrySet()?.forEach { (k, v) ->
                    if (!v.isJsonNull) {
                        if (k.equals("content-type", ignoreCase = true)) contentType = v.asString
                        currentHeaders[k] = v.asString
                    }
                }
                var currentBodyBytes: ByteArray? =
                    if (currentMethod == "POST" || currentMethod == "PUT" || currentMethod == "PATCH" || currentMethod == "DELETE") {
                        when {
                            bodyBase64 != null -> Base64.decode(bodyBase64, Base64.DEFAULT)
                            bodyStr != null -> bodyStr.toByteArray(Charsets.UTF_8)
                            else -> null
                        }
                    } else {
                        null
                    }

                fun buildRequestForCurrentHop(): Request {
                    val builder = Request.Builder().url(currentUrl)
                    currentHeaders.forEach { (k, v) -> builder.header(k, v) }
                    if (currentMethod == "POST" || currentMethod == "PUT" || currentMethod == "PATCH" || currentMethod == "DELETE") {
                        if (currentMethod == "DELETE" && currentBodyBytes == null) {
                            builder.method(currentMethod, null)
                        } else {
                            val mediaType = contentType.toMediaTypeOrNull()
                            builder.method(currentMethod, (currentBodyBytes ?: ByteArray(0)).toRequestBody(mediaType))
                        }
                    } else {
                        builder.method(currentMethod, null)
                    }
                    return builder.build()
                }

                var redirectCount = 0
                while (redirectCount <= MAX_HTTP_REDIRECTS) {
                    val req = buildRequestForCurrentHop()
                    var nextRedirectUrl: HttpUrl? = null
                    var finalResult: String? = null

                    httpClient.awaitCall(req).use { resp ->
                        val isRedirectCode = resp.code == 301 || resp.code == 302 || resp.code == 303 ||
                            resp.code == 307 || resp.code == 308
                        val location = if (isRedirectCode) resp.header("Location") else null

                        if (location != null) {
                            if (redirectCount >= MAX_HTTP_REDIRECTS) {
                                throw IOException("Too many redirects (max $MAX_HTTP_REDIRECTS)")
                            }
                            val resolved = resp.request.url.resolve(location)
                                ?: throw IllegalArgumentException("Invalid redirect URL")
                            validateOutboundHttpUrl(resolved)

                            val sameOrigin = currentUrl.scheme.equals(resolved.scheme, ignoreCase = true) &&
                                currentUrl.host.equals(resolved.host, ignoreCase = true) &&
                                currentUrl.port == resolved.port
                            if (!sameOrigin) {
                                val keysToDrop = currentHeaders.keys.filter { key ->
                                    key.equals("authorization", ignoreCase = true) ||
                                        key.equals("proxy-authorization", ignoreCase = true) ||
                                        key.equals("cookie", ignoreCase = true)
                                }
                                keysToDrop.forEach { currentHeaders.remove(it) }
                            }

                            if (resp.code == 303 || ((resp.code == 301 || resp.code == 302) && currentMethod == "POST")) {
                                currentMethod = "GET"
                                currentBodyBytes = null
                                val bodyHeaderKeys = currentHeaders.keys.filter { key ->
                                    key.equals("content-type", ignoreCase = true) ||
                                        key.equals("content-length", ignoreCase = true)
                                }
                                bodyHeaderKeys.forEach { currentHeaders.remove(it) }
                            }

                            nextRedirectUrl = resolved
                        } else {
                            val respHeaders = linkedMapOf<String, String>()
                            for (name in resp.headers.names()) {
                                respHeaders[name] = resp.headers[name] ?: ""
                            }
                            val bytes = readResponseBodyBounded(resp, MAX_HTTP_BODY_BYTES)
                            val encodedB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val respContentType = (resp.header("Content-Type") ?: "").lowercase(Locale.ROOT)
                            val isLikelyBinary = respContentType.startsWith("image/") ||
                                respContentType.startsWith("audio/") ||
                                respContentType.startsWith("video/") ||
                                respContentType.contains("octet-stream") ||
                                respContentType.contains("application/pdf") ||
                                respContentType.contains("application/zip") ||
                                respContentType.contains("font/")
                            val bodyText = if (isLikelyBinary) "" else String(bytes, Charsets.UTF_8)
                            finalResult = gson.toJson(
                                mapOf(
                                    "status" to resp.code,
                                    "statusText" to resp.message,
                                    "url" to resp.request.url.toString(),
                                    "headers" to respHeaders,
                                    "body" to bodyText,
                                    "bodyBase64" to encodedB64,
                                ),
                            )
                        }
                    }

                    if (finalResult != null) {
                        return@withContext finalResult!!
                    }
                    currentUrl = nextRedirectUrl!!
                    redirectCount++
                }
                throw IOException("Too many redirects (max $MAX_HTTP_REDIRECTS)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            gson.toJson(mapOf("error" to (e.message ?: "Network error")))
        }
    }

    private fun handleSyncHostCall(method: String, payloadJson: String): String? {
        val obj = runCatching { gson.fromJson(payloadJson, JsonObject::class.java) }.getOrNull() ?: JsonObject()
        return when (method) {
            "cli.exec" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLI_BRIDGE)) {
                    return gson.toJson(
                        CliResult("", "CLI/Shell access is disabled for ${extension.manifest.title}. Enable it in Extensions permissions.", 126),
                    )
                }
                val command = obj.get("command")?.asString
                    ?: return gson.toJson(CliResult("", "No command provided", 1))
                val cwd = obj.get("cwd")?.asString
                val timeout = obj.get("timeoutMs")?.asLong ?: 15_000L
                val result = CliBridge.execute(
                    context = context,
                    supportDir = RaycastExtensionRepository.supportDir(context, extension.id),
                    extRoot = File(extension.dirPath),
                    command = command,
                    cwd = cwd,
                    timeoutMs = timeout,
                    extensionId = extension.id,
                )
                gson.toJson(result)
            }
            "runtime.reportError" -> {
                val message = obj.get("message")?.asString ?: "Unknown JavaScript error"
                val phase = obj.get("phase")?.asString ?: "runtime"
                val stack = obj.get("stack")?.asString.orEmpty()
                val fatal = obj.get("fatal")?.asBoolean ?: (_viewStack.value.isEmpty())
                Log.e(tag, "JS error [$phase]: $message\n$stack")
                if (fatal || _viewStack.value.isEmpty()) {
                    val stackFirstLine = stack.lineSequence().drop(1).firstOrNull()?.trim().orEmpty()
                    val formatted = if (stackFirstLine.isNotEmpty() && !message.contains(stackFirstLine)) {
                        "$message\n($stackFirstLine)"
                    } else {
                        message
                    }
                    _errorState.value = formatted
                } else {
                    _toastEvents.tryEmit(
                        RaycastToastEvent(
                            id = "err_${System.currentTimeMillis()}",
                            title = "Extension Error ($phase)",
                            message = message,
                            style = "Failure",
                        ),
                    )
                }
                "true"
            }
            "cache.get" -> {
                val ns = obj.get("ns")?.asString ?: "default"
                val key = obj.get("key")?.asString ?: return null
                val prefs = RaycastExtensionRepository.cachePrefs(context, extension.id)
                val v = prefs.getString("$ns::$key", null) ?: return null
                gson.toJson(v)
            }
            "cache.set" -> {
                val ns = obj.get("ns")?.asString ?: "default"
                val key = obj.get("key")?.asString ?: return null
                val value = obj.get("value")?.asString ?: ""
                RaycastExtensionRepository.cachePrefs(context, extension.id)
                    .edit().putString("$ns::$key", value).apply()
                "true"
            }
            "cache.remove" -> {
                val ns = obj.get("ns")?.asString ?: "default"
                val key = obj.get("key")?.asString ?: return "false"
                val prefs = RaycastExtensionRepository.cachePrefs(context, extension.id)
                val existed = prefs.contains("$ns::$key")
                prefs.edit().remove("$ns::$key").apply()
                existed.toString()
            }
            "cache.clear" -> {
                val ns = obj.get("ns")?.asString ?: "default"
                val prefs = RaycastExtensionRepository.cachePrefs(context, extension.id)
                val editor = prefs.edit()
                prefs.all.keys.filter { it.startsWith("$ns::") }.forEach { editor.remove(it) }
                editor.apply()
                "true"
            }
            "storage.getItem" -> {
                val key = obj.get("key")?.asString ?: return null
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .getString(key, null)
            }
            "storage.setItem" -> {
                val key = obj.get("key")?.asString ?: return "false"
                val valEl = obj.get("value")
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().putString(key, gson.toJson(valEl)).apply()
                "true"
            }
            "storage.removeItem" -> {
                val key = obj.get("key")?.asString ?: return "false"
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().remove(key).apply()
                "true"
            }
            "storage.clear" -> {
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().clear().apply()
                "true"
            }
            "storage.allItems" -> {
                val all = RaycastExtensionRepository.localStoragePrefs(context, extension.id).all
                val out = linkedMapOf<String, Any?>()
                for ((k, v) in all) {
                    val str = v as? String ?: continue
                    out[k] = runCatching { gson.fromJson(str, Any::class.java) }.getOrDefault(str)
                }
                gson.toJson(out)
            }
            "crypto.randomUuid" -> gson.toJson(UUID.randomUUID().toString())
            "crypto.randomBytes" -> {
                val len = (obj.get("length")?.asInt ?: 16).coerceIn(1, 256)
                val bytes = ByteArray(len)
                secureRandom.nextBytes(bytes)
                gson.toJson(bytes.map { it.toInt() and 0xFF })
            }
            "crypto.sha256Bytes" -> {
                val input = obj.get("input")?.asString ?: ""
                val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                gson.toJson(digest.map { it.toInt() and 0xFF })
            }
            "crypto.hash" -> {
                val alg = mapDigestAlgorithm(obj.get("alg")?.asString)
                val input = obj.get("input")?.asString ?: ""
                val digest = MessageDigest.getInstance(alg).digest(input.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                gson.toJson(hex)
            }
            "crypto.hashBytesBase64" -> {
                val alg = mapDigestAlgorithm(obj.get("alg")?.asString)
                val b64 = obj.get("base64")?.asString ?: ""
                val raw = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                val digest = MessageDigest.getInstance(alg).digest(raw)
                gson.toJson(digest.map { it.toInt() and 0xFF })
            }
            "crypto.hmacBytesBase64" -> {
                val alg = mapHmacAlgorithm(obj.get("alg")?.asString)
                val keyB64 = obj.get("keyBase64")?.asString ?: ""
                val b64 = obj.get("base64")?.asString ?: ""
                val decodedKey = runCatching { Base64.decode(keyB64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                val keyBytes = if (decodedKey.isEmpty()) ByteArray(1) { 0 } else decodedKey
                val raw = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                val mac = Mac.getInstance(alg)
                mac.init(SecretKeySpec(keyBytes, alg))
                val digest = mac.doFinal(raw)
                gson.toJson(digest.map { it.toInt() and 0xFF })
            }
            "time.format" -> formatTimeForHost(obj)
            "fs.readExtensionFile" -> runCatching {
                val rel = obj.get("path")?.asString ?: return@runCatching null
                val extRoot = File(extension.dirPath).canonicalFile
                val candidate = File(extRoot, rel.trimStart('/')).canonicalFile
                if (!isWithinRoot(candidate, extRoot)) {
                    null
                } else {
                    val bytes = readFileBytesBounded(candidate, MAX_FS_READ_BYTES) ?: return@runCatching null
                    gson.toJson(String(bytes, Charsets.UTF_8))
                }
            }.getOrNull()
            "fs.exists" -> runCatching {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                (f != null && f.exists()).toString()
            }.getOrDefault("false")
            "fs.read" -> runCatching {
                val f = resolveSandboxedFile(obj.get("path")?.asString) ?: return@runCatching null
                val bytes = readFileBytesBounded(f, MAX_FS_READ_BYTES) ?: return@runCatching null
                gson.toJson(String(bytes, Charsets.UTF_8))
            }.getOrNull()
            "fs.readBase64" -> runCatching {
                val f = resolveSandboxedFile(obj.get("path")?.asString) ?: return@runCatching null
                val bytes = readFileBytesBounded(f, MAX_FS_READ_BYTES) ?: return@runCatching null
                gson.toJson(Base64.encodeToString(bytes, Base64.NO_WRAP))
            }.getOrNull()
            "fs.write" -> runCatching {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return@runCatching "false"
                f.parentFile?.mkdirs()
                f.writeText(obj.get("content")?.asString ?: "")
                "true"
            }.getOrDefault("false")
            "fs.writeBase64" -> runCatching {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return@runCatching "false"
                val b64 = obj.get("base64")?.asString ?: ""
                val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return@runCatching "false"
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
                "true"
            }.getOrDefault("false")
            "fs.mkdir" -> runCatching {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return@runCatching "false"
                if (f.exists() && f.isDirectory) return@runCatching "true"
                f.mkdirs().toString()
            }.getOrDefault("false")
            "fs.readdir" -> runCatching {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                if (f != null && f.exists() && f.isDirectory) {
                    gson.toJson((f.list() ?: emptyArray()).toList())
                } else {
                    null
                }
            }.getOrNull()
            "fs.stat" -> runCatching {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                if (f != null && f.exists()) {
                    gson.toJson(
                        mapOf(
                            "size" to if (f.isFile) f.length() else 0L,
                            "mtimeMs" to f.lastModified(),
                            "isFile" to f.isFile,
                            "isDirectory" to f.isDirectory,
                        ),
                    )
                } else {
                    null
                }
            }.getOrNull()
            "fs.unlink" -> runCatching {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString)
                (f != null && f.exists() && f.isFile && f.delete()).toString()
            }.getOrDefault("false")
            "fs.rm" -> runCatching {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return@runCatching "false"
                if (!f.exists()) return@runCatching "false"
                val recursive = obj.get("recursive")?.asBoolean ?: false
                if (f.isDirectory && recursive) f.deleteRecursively().toString() else f.delete().toString()
            }.getOrDefault("false")
            "fs.rename" -> runCatching {
                val src = resolveWritableSandboxedFile(obj.get("src")?.asString) ?: return@runCatching "false"
                val dest = resolveWritableSandboxedFile(obj.get("dest")?.asString) ?: return@runCatching "false"
                if (!src.exists()) return@runCatching "false"
                dest.parentFile?.mkdirs()
                src.renameTo(dest).toString()
            }.getOrDefault("false")
            "fs.copyFile" -> runCatching {
                val src = resolveSandboxedFile(obj.get("src")?.asString) ?: return@runCatching "false"
                val dest = resolveWritableSandboxedFile(obj.get("dest")?.asString) ?: return@runCatching "false"
                if (!src.exists() || !src.isFile) return@runCatching "false"
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                "true"
            }.getOrDefault("false")
            else -> null
        }
    }

    private fun formatTimeForHost(obj: JsonObject): String {
        val options = obj.getAsJsonObject("options") ?: JsonObject()
        val rawZone = obj.optString("zone") ?: options.optString("timeZone")
        val zoneId = rawZone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val epochMs = obj.get("epochMs")
            ?.takeIf { it.isJsonPrimitive }
            ?.asLong
            ?: System.currentTimeMillis()
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        val locale = obj.optString("locale")
            ?.let { runCatching { Locale.forLanguageTag(it.replace('_', '-')) }.getOrNull() }
            ?.takeIf { it.language.isNotBlank() }
            ?: Locale.getDefault()

        options.optString("dateStyle")?.takeIf { it.isNotBlank() }?.let { style ->
            when (style) {
                "full" -> {
                    options.addProperty("weekday", "long")
                    options.addProperty("year", "numeric")
                    options.addProperty("month", "long")
                    options.addProperty("day", "numeric")
                }
                "long" -> {
                    options.addProperty("year", "numeric")
                    options.addProperty("month", "long")
                    options.addProperty("day", "numeric")
                }
                "medium" -> {
                    options.addProperty("year", "numeric")
                    options.addProperty("month", "short")
                    options.addProperty("day", "numeric")
                }
                else -> {
                    options.addProperty("year", "2-digit")
                    options.addProperty("month", "numeric")
                    options.addProperty("day", "numeric")
                }
            }
        }
        options.optString("timeStyle")?.takeIf { it.isNotBlank() }?.let { style ->
            when (style) {
                "full", "long" -> {
                    options.addProperty("hour", "2-digit")
                    options.addProperty("minute", "2-digit")
                    options.addProperty("second", "2-digit")
                }
                "medium" -> {
                    options.addProperty("hour", "2-digit")
                    options.addProperty("minute", "2-digit")
                }
                else -> {
                    options.addProperty("hour", "numeric")
                    options.addProperty("minute", "2-digit")
                }
            }
        }

        val weekday = options.optString("weekday")
        val year = options.optString("year")
        val month = options.optString("month")
        val day = options.optString("day")
        val hour = options.optString("hour")
        val minute = options.optString("minute")
        val second = options.optString("second")
        val hourCycle = options.optString("hourCycle")
        val hour12 = options.get("hour12")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: when (hourCycle) {
                "h11", "h12" -> true
                "h23", "h24" -> false
                else -> false
            }

        val parts = mutableListOf<Map<String, String>>()
        fun add(type: String, value: String) {
            parts += linkedMapOf("type" to type, "value" to value)
        }
        fun addLiteral(value: String) {
            if (value.isNotEmpty()) add("literal", value)
        }

        var hasDate = false
        var previousDateType: String? = null
        if (weekday != null) {
            add("weekday", zoned.dayOfWeek.getDisplayName(textStyle(weekday), locale))
            hasDate = true
            previousDateType = "weekday"
        }
        if (month != null) {
            if (hasDate) addLiteral(dateSeparator(previousDateType, "month", month))
            add(
                "month",
                when (month) {
                    "2-digit" -> zoned.monthValue.toString().padStart(2, '0')
                    "numeric" -> zoned.monthValue.toString()
                    "long" -> zoned.month.getDisplayName(TextStyle.FULL, locale)
                    "narrow" -> zoned.month.getDisplayName(TextStyle.NARROW, locale)
                    else -> zoned.month.getDisplayName(TextStyle.SHORT, locale)
                },
            )
            hasDate = true
            previousDateType = "month"
        }
        if (day != null) {
            if (hasDate) addLiteral(dateSeparator(previousDateType, "day", month))
            add(
                "day",
                if (day == "2-digit") zoned.dayOfMonth.toString().padStart(2, '0')
                else zoned.dayOfMonth.toString(),
            )
            hasDate = true
            previousDateType = "day"
        }
        if (year != null) {
            if (hasDate) addLiteral(dateSeparator(previousDateType, "year", month))
            add(
                "year",
                if (year == "2-digit") (zoned.year % 100).toString().padStart(2, '0')
                else zoned.year.toString(),
            )
            hasDate = true
            previousDateType = "year"
        }

        var hasTime = false
        if (hour != null) {
            val rawHour = if (hour12) {
                if (hourCycle == "h11") zoned.hour % 12 else {
                    val value = zoned.hour % 12
                    if (value == 0) 12 else value
                }
            } else {
                zoned.hour
            }
            val value = if (hour == "2-digit") {
                rawHour.toString().padStart(2, '0')
            } else {
                rawHour.toString()
            }
            if (hasDate) addLiteral(", ")
            if (hasTime) addLiteral(":")
            add("hour", value)
            hasTime = true
        }
        if (minute != null) {
            val value = if (minute == "2-digit") zoned.minute.toString().padStart(2, '0')
            else zoned.minute.toString()
            if (hasDate && !hasTime) addLiteral(", ")
            if (hasTime) addLiteral(":")
            add("minute", value)
            hasTime = true
        }
        if (second != null) {
            val value = if (second == "2-digit") zoned.second.toString().padStart(2, '0')
            else zoned.second.toString()
            if (hasDate && !hasTime) addLiteral(", ")
            if (hasTime) addLiteral(":")
            add("second", value)
            hasTime = true
        }
        if (hour != null && hour12) {
            if (hasTime) addLiteral(" ")
            add("dayPeriod", zoned.format(DateTimeFormatter.ofPattern("a", locale)))
            hasTime = true
        }
        val timeZoneName = options.optString("timeZoneName")
        if (timeZoneName != null) {
            if (hasDate && !hasTime) addLiteral(", ")
            if (hasTime) addLiteral(" ")
            val pattern = if (timeZoneName == "long" || timeZoneName == "longOffset") "zzzz" else "z"
            add("timeZoneName", zoned.format(DateTimeFormatter.ofPattern(pattern, locale)))
            hasTime = true
        }

        if (!hasDate && !hasTime) {
            add("year", zoned.year.toString())
            addLiteral("/")
            add("month", zoned.monthValue.toString().padStart(2, '0'))
            addLiteral("/")
            add("day", zoned.dayOfMonth.toString().padStart(2, '0'))
        }

        val text = buildString { parts.forEach { append(it["value"] ?: "") } }
        return gson.toJson(mapOf("text" to text, "parts" to parts))
    }

    private fun textStyle(value: String): TextStyle = when (value.lowercase(Locale.ROOT)) {
        "long" -> TextStyle.FULL
        "narrow" -> TextStyle.NARROW
        else -> TextStyle.SHORT
    }

    private fun dateSeparator(
        previous: String?,
        current: String,
        month: String?,
    ): String = when {
        previous == null -> ""
        previous == "weekday" -> ", "
        previous == "month" && current == "day" ->
            if (month == "2-digit" || month == "numeric") "/" else " "
        previous == "day" && current == "year" ->
            if (month == "2-digit" || month == "numeric") "/" else ", "
        else -> ", "
    }

    private fun JsonObject.optString(name: String): String? =
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() && it != "none" }

    private fun mapDigestAlgorithm(raw: String?): String {
        return when ((raw ?: "sha256").lowercase().replace("-", "")) {
            "md5" -> "MD5"
            "sha1" -> "SHA-1"
            "sha384" -> "SHA-384"
            "sha512" -> "SHA-512"
            else -> "SHA-256"
        }
    }

    private fun mapHmacAlgorithm(raw: String?): String {
        return when ((raw ?: "sha256").lowercase().replace("-", "")) {
            "md5" -> "HmacMD5"
            "sha1" -> "HmacSHA1"
            "sha384" -> "HmacSHA384"
            "sha512" -> "HmacSHA512"
            else -> "HmacSHA256"
        }
    }

    private fun isWithinRoot(candidate: File, root: File): Boolean {
        return candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
    }

    private fun resolveSandboxedFile(rawPath: String?): File? {
        if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.FILES)) return null
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val extRoot = File(extension.dirPath).canonicalFile
        val f = File(rawPath).canonicalFile
        return if (isWithinRoot(f, supportRoot) || isWithinRoot(f, extRoot)) f else null
    }

    private fun resolveWritableSandboxedFile(rawPath: String?): File? {
        if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.FILES)) return null
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val f = File(rawPath).canonicalFile
        return if (isWithinRoot(f, supportRoot) && f.path != supportRoot.path) f else null
    }

    private suspend fun handleAsyncHostCall(method: String, payloadJson: String): String? {
        val obj = runCatching { gson.fromJson(payloadJson, JsonObject::class.java) }.getOrNull() ?: JsonObject()
        return when (method) {
            "cli.exec" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLI_BRIDGE)) {
                    return gson.toJson(
                        CliResult("", "CLI/Shell access is disabled for ${extension.manifest.title}. Enable it in Extensions permissions.", 126),
                    )
                }
                val command = obj.get("command")?.asString
                    ?: return gson.toJson(CliResult("", "No command provided", 1))
                val cwd = obj.get("cwd")?.asString
                val timeout = obj.get("timeoutMs")?.asLong ?: 15_000L
                val result = withContext(Dispatchers.IO) {
                    CliBridge.execute(
                        context = context,
                        supportDir = RaycastExtensionRepository.supportDir(context, extension.id),
                        extRoot = File(extension.dirPath),
                        command = command,
                        cwd = cwd,
                        timeoutMs = timeout,
                        extensionId = extension.id,
                    )
                }
                gson.toJson(result)
            }
            "storage.getItem" -> {
                val key = obj.get("key")?.asString ?: return null
                val prefs = RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                val raw = prefs.getString(key, null) ?: return null
                raw // already JSON-encoded value or JSON string
            }
            "storage.setItem" -> {
                val key = obj.get("key")?.asString ?: return null
                val valEl = obj.get("value")
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().putString(key, gson.toJson(valEl)).apply()
                "true"
            }
            "storage.removeItem" -> {
                val key = obj.get("key")?.asString ?: return null
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().remove(key).apply()
                "true"
            }
            "storage.clear" -> {
                RaycastExtensionRepository.localStoragePrefs(context, extension.id)
                    .edit().clear().apply()
                "true"
            }
            "storage.allItems" -> {
                val all = RaycastExtensionRepository.localStoragePrefs(context, extension.id).all
                val out = linkedMapOf<String, Any?>()
                for ((k, v) in all) {
                    val str = v as? String ?: continue
                    out[k] = runCatching { gson.fromJson(str, Any::class.java) }.getOrDefault(str)
                }
                gson.toJson(out)
            }
            "clipboard.copy", "clipboard.paste" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLIPBOARD)) {
                    return "false"
                }
                val text = obj.get("text")?.asString ?: ""
                val concealed = obj.get("concealed")?.asBoolean ?: false
                withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Raycast", text)
                    if (concealed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    cm.setPrimaryClip(clip)
                }
                if (
                    method == "clipboard.paste" &&
                    ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLI_BRIDGE) &&
                    com.sameerasw.essentials.utils.ShellUtils.hasPermission(context)
                ) {
                    withContext(Dispatchers.IO) {
                        com.sameerasw.essentials.utils.ShellUtils.runCommandWithOutput(
                            context,
                            "input keyevent ${android.view.KeyEvent.KEYCODE_PASTE}",
                            notifyOnError = false,
                        )
                    }
                }
                "true"
            }
            "clipboard.readText" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLIPBOARD)) {
                    return gson.toJson("")
                }
                val text = withContext(Dispatchers.Main) {
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                        } else {
                            ""
                        }
                    }.getOrDefault("")
                }
                gson.toJson(text)
            }
            "clipboard.clear" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.CLIPBOARD)) {
                    return "false"
                }
                withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                "true"
            }
            "ui.showToast" -> {
                val event = RaycastToastEvent(
                    id = obj.get("id")?.asString ?: "",
                    title = obj.get("title")?.asString ?: "",
                    message = obj.get("message")?.asString ?: "",
                    style = obj.get("style")?.asString ?: "Success",
                    hidden = false,
                )
                _toastEvents.emit(event)
                "true"
            }
            "ui.hideToast" -> {
                val id = obj.get("id")?.asString ?: ""
                _toastEvents.emit(RaycastToastEvent(id = id, title = "", message = "", style = "Success", hidden = true))
                "true"
            }
            "ui.showHud" -> {
                val title = obj.get("title")?.asString ?: ""
                _hudEvents.emit(title)
                "true"
            }
            "ui.confirmAlert" -> {
                val primary = obj.getAsJsonObject("primaryAction")
                val dismiss = obj.getAsJsonObject("dismissAction")
                val deferred = CompletableDeferred<Boolean>()
                _alertRequests.emit(
                    RaycastAlertRequest(
                        title = obj.get("title")?.asString ?: "Confirm",
                        message = obj.get("message")?.asString ?: "",
                        primaryTitle = primary?.get("title")?.asString ?: "Confirm",
                        primaryStyle = primary?.get("style")?.asString ?: "Default",
                        dismissTitle = dismiss?.get("title")?.asString ?: "Cancel",
                        deferred = deferred,
                    ),
                )
                deferred.await().toString()
            }
            "ui.closeMainWindow", "ui.noViewComplete" -> {
                _closeRequests.emit(Unit)
                "true"
            }
            "ui.openPreferences" -> {
                _openPreferencesRequests.emit(obj.get("scope")?.asString ?: "extension")
                "true"
            }
            "ui.popToRoot", "ui.clearSearchBar", "ui.pushView", "ui.popView", "ui.updateCommandMetadata" -> "true"
            "health.availability" -> {
                gson.toJson(
                    mapOf("status" to com.sameerasw.essentials.assistant.HealthConnectManager.availability(context)),
                )
            }
            "health.summary", "health.sleep", "health.steps" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.HEALTH_DATA)) {
                    return gson.toJson(
                        mapOf(
                            "error" to "Health data access is disabled for ${extension.manifest.title}. " +
                                "Enable it in Extensions permissions.",
                        ),
                    )
                }
                val manager = com.sameerasw.essentials.assistant.HealthConnectManager
                when (manager.availability(context)) {
                    "available" -> Unit
                    "update_required" -> return gson.toJson(
                        mapOf("error" to "Health Connect needs an update; open it once to finish."),
                    )
                    else -> return gson.toJson(
                        mapOf("error" to "Health Connect is not available on this device."),
                    )
                }
                val snapshot = manager.readSnapshot(context).getOrElse { err ->
                    val reason = err.localizedMessage?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                    return gson.toJson(mapOf("error" to "Could not read health data ($reason)."))
                }
                if (snapshot.missingPermissions) {
                    return gson.toJson(
                        mapOf(
                            "error" to "Health permissions missing. Open Komorebi Settings > Permissions > " +
                                "Health Connect and grant access, then reopen this extension.",
                        ),
                    )
                }
                if (snapshot.backgroundReadBlocked) {
                    return gson.toJson(
                        mapOf(
                            "error" to "Background Health Connect access is required from the search overlay. " +
                                "Open Komorebi Settings > Permissions > Health Connect and enable 'Access data in background'.",
                        ),
                    )
                }
                val map = snapshot.map
                val payload: Map<String, Any?> = when (method) {
                    "health.sleep" -> mapOf(
                        "minutesLastNight" to map["sleepMinutesLastNight"],
                        "deepMin" to map["sleepDeepMin"],
                        "remMin" to map["sleepRemMin"],
                        "lightMin" to map["sleepLightMin"],
                        "awakeMin" to map["sleepAwakeMin"],
                        "avg7Min" to map["sleepAvg7Min"],
                        "history" to map["sleepHistory"],
                    )
                    "health.steps" -> mapOf(
                        "today" to map["stepsToday"],
                        "yesterday" to map["stepsYesterday"],
                        "avg7" to map["stepsAvg7"],
                        "history" to map["stepsHistory"],
                    )
                    else -> map
                }
                gson.toJson(payload)
            }
            "system.getSelectedText" -> {
                if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.SCREEN_CONTEXT)) {
                    return gson.toJson("")
                }
                val contextSnapshot = ScreenContextExtractor.current()
                val fresh = contextSnapshot != null &&
                    System.currentTimeMillis() - contextSnapshot.capturedAt < 15_000L
                val selected = if (fresh) contextSnapshot?.selectedText.orEmpty() else ""
                gson.toJson(selected)
            }
            "system.open" -> {
                val target = obj.get("target")?.asString ?: return "false"
                val app = if (obj.has("application") && !obj.get("application").isJsonNull) obj.get("application").asString else null
                val opened = withContext(Dispatchers.Main) {
                    openTargetExternally(target, app)
                }
                opened.toString()
            }
            "system.getApplications" -> {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { ri ->
                    mapOf(
                        "name" to ri.loadLabel(pm).toString(),
                        "bundleId" to ri.activityInfo.packageName,
                        "path" to ri.activityInfo.packageName,
                    )
                }.distinctBy { it["bundleId"] }
                gson.toJson(apps)
            }
            "system.getDefaultApplication" -> {
                val target = obj.get("target")?.asString ?: return null
                val pm = context.packageManager
                val intent = buildViewIntent(target, null) ?: return null
                val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
                val pkg = ri.activityInfo?.packageName ?: return null
                gson.toJson(
                    mapOf(
                        "name" to ri.loadLabel(pm).toString(),
                        "bundleId" to pkg,
                        "path" to pkg,
                    ),
                )
            }
            "system.trash" -> {
                val pathsEl = obj.getAsJsonArray("paths") ?: return "false"
                var allOk = true
                for (el in pathsEl) {
                    val p = if (el != null && !el.isJsonNull) el.asString else continue
                    val targetFile = resolveWritableSandboxedFile(p)
                    if (targetFile != null && targetFile.exists()) {
                        if (!targetFile.deleteRecursively()) allOk = false
                    } else {
                        allOk = false
                    }
                }
                if (!allOk) {
                    _toastEvents.emit(
                        RaycastToastEvent(
                            id = "trash_${System.currentTimeMillis()}",
                            title = "Cannot trash files outside extension storage",
                            message = "Android restricts deleting files outside the extension sandbox.",
                            style = "Failure",
                        ),
                    )
                }
                allOk.toString()
            }
            "system.launchCommand" -> {
                val commandName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val requestedExtension =
                    obj.get("extensionName")?.takeIf { !it.isJsonNull }?.asString
                val targetExtension =
                    if (requestedExtension.isNullOrBlank()) {
                        extension
                    } else {
                        RaycastExtensionRepository.listInstalledExtensions(context).firstOrNull {
                            it.manifest.name.equals(requestedExtension, ignoreCase = true) ||
                                it.displayTitle.equals(requestedExtension, ignoreCase = true)
                        }
                    }
                val targetCommand =
                    targetExtension?.manifest?.commands?.firstOrNull { it.name == commandName }
                        ?: targetExtension?.manifest?.commands?.firstOrNull {
                            it.name.equals(commandName, ignoreCase = true)
                        }

                if (targetExtension == null || targetCommand == null) {
                    _toastEvents.emit(
                        RaycastToastEvent(
                            id = "launch_${System.currentTimeMillis()}",
                            title = "launchCommand('$commandName') unavailable",
                            message =
                                if (targetExtension == null) {
                                    "Command could not find extension '${requestedExtension ?: extension.manifest.name}'."
                                } else {
                                    "Extension '${targetExtension.displayTitle}' has no command named '$commandName'."
                                },
                            style = "Failure",
                        ),
                    )
                    return "false"
                }

                val arguments =
                    targetCommand.arguments.orEmpty().associate { arg ->
                        val provided =
                            obj.getAsJsonObject("arguments")
                                ?.get(arg.name)
                                ?.takeIf { !it.isJsonNull }
                                ?.let { element ->
                                    if (element.isJsonPrimitive) element.asString else element.toString()
                                }
                        arg.name to (provided ?: "")
                    }.filterValues { it.isNotEmpty() }
                val launchType =
                    obj.get("launchType")?.takeIf { !it.isJsonNull }?.asString

                withContext(Dispatchers.Main) {
                    val intent =
                        com.sameerasw.essentials.ui.activities.ExtensionRunnerActivity.createIntent(
                            context = context,
                            extensionId = targetExtension.id,
                            commandName = targetCommand.name,
                            arguments = arguments,
                            launchType = launchType,
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                }
                "true"
            }
            "oauth.authorize" -> {
                val url = obj.get("url")?.asString ?: return "{}"
                val result = RaycastOAuthManager.launchAuthorizeFlow(context, url)
                gson.toJson(result)
            }
            else -> null
        }
    }

    private fun resolveOpenableExtensionFile(rawPath: String?): File? {
        if (!ExtensionPermissionManager.isAllowed(context, extension.id, ExtensionCapability.FILES)) return null
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val extRoot = File(extension.dirPath).canonicalFile
        val cacheBase = File(context.cacheDir, "raycast_cache").canonicalFile
        val extCacheRoot = File(cacheBase, extension.id).canonicalFile
        val candidate = File(rawPath).canonicalFile
        val inExtCache = isWithinRoot(extCacheRoot, cacheBase) &&
            extCacheRoot.path != cacheBase.path &&
            isWithinRoot(candidate, extCacheRoot)
        return if (isWithinRoot(candidate, supportRoot) || isWithinRoot(candidate, extRoot) || inExtCache) {
            candidate
        } else {
            null
        }
    }

    private fun buildViewIntent(target: String, appPackage: String?): Intent? {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("/")) {
            val localFile = resolveOpenableExtensionFile(trimmed) ?: return null
            if (!localFile.exists() || !localFile.isFile) return null
            val contentUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
            }.getOrNull() ?: return null
            val ext = MimeTypeMap.getFileExtensionFromUrl(localFile.name).lowercase(Locale.ROOT)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!appPackage.isNullOrBlank()) setPackage(appPackage)
            }
        }

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in ALLOWED_OPEN_URI_SCHEMES) {
            Log.w(tag, "Blocked system.open for disallowed URI scheme: $scheme")
            return null
        }
        if ((scheme == "http" || scheme == "https") && parsed.host.isNullOrBlank()) {
            return null
        }
        return Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!appPackage.isNullOrBlank()) setPackage(appPackage)
        }
    }

    private fun openTargetExternally(target: String, appPackage: String?): Boolean {
        val intent = buildViewIntent(target, appPackage) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            if (!appPackage.isNullOrBlank()) {
                val launch = context.packageManager.getLaunchIntentForPackage(appPackage)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return runCatching {
                        context.startActivity(launch)
                        true
                    }.getOrDefault(false)
                }
            }
            false
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeEngineRef?.set(null)
        evalJob.cancel()
        cleanupScope.launch {
            runCatching { evalJob.join() }
            val qjs = quickJs
            quickJs = null
            activeEngineRef?.set(null)
            activeEngineRef = null
            if (qjs != null) {
                runCatching { qjs.evaluate<Any?>("globalThis.__unmountExtension();", "unmount.js", false) }
                runCatching { qjs.close() }
            }
            scope.cancel()
            cleanupScope.cancel()
        }
    }
}
