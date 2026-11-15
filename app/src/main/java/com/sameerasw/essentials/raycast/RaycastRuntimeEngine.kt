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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private val context: Context,
    private val extension: InstalledRaycastExtension,
    private val command: RaycastCommandManifest,
) {
    private val tag = "RaycastRuntime"
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var quickJs: QuickJs? = null
    private val secureRandom = SecureRandom()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    fun start(
        arguments: Map<String, String> = emptyMap(),
        isDarkTheme: Boolean = true,
    ) {
        scope.launch {
            try {
                val entryFile = extension.entryFileForCommand(command.name)
                if (entryFile == null) {
                    _errorState.value = buildMissingEntryDiagnostic(extension, command)
                    return@launch
                }

                val qjs = QuickJs.create(Dispatchers.IO)
                quickJs = qjs
                qjs.memoryLimit = 64L * 1024L * 1024L
                qjs.maxStackSize = 1024L * 1024L

                registerBindings(qjs)

                val runtimeJs = context.assets.open("raycast/runtime.js").bufferedReader().use { it.readText() }
                qjs.evaluate<Any?>(runtimeJs, "runtime.js", false)

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
                    "entryPath" to "/" + entryFile.relativeTo(File(extension.dirPath)).path.replace('\\', '/'),
                )

                val entryCode = entryFile.readText()
                val configJson = gson.toJson(launchConfig)
                val invokeCode = "globalThis.__launchCommand(${gson.toJson(configJson)}, ${gson.toJson(entryCode)});"
                qjs.evaluate<Any?>(invokeCode, entryFile.name, false)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.e(tag, "Extension launch failed", e)
                _errorState.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun registerBindings(qjs: QuickJs) {
        qjs.function("__nativeLog") { args ->
            val level = args.getOrNull(0)?.toString() ?: "LOG"
            val msg = args.getOrNull(1)?.toString() ?: ""
            Log.d("$tag/$level", msg)
            null
        }

        qjs.function("__emitRender") { args ->
            val treeJson = args.getOrNull(0)?.toString() ?: return@function null
            val screens = RaycastTreeParser.parseViewStack(treeJson)
            _viewStack.value = screens
            null
        }

        qjs.asyncFunction("__scheduleTimeout") { args ->
            val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            if (ms > 0) delay(ms)
            true
        }

        qjs.asyncFunction("__nativeFetch") { args ->
            val reqJson = args.getOrNull(0)?.toString() ?: "{}"
            executeHttpFetch(reqJson)
        }

        qjs.function("__hostCallSync") { args ->
            val method = args.getOrNull(0)?.toString() ?: ""
            val payloadJson = args.getOrNull(1)?.toString() ?: "{}"
            handleSyncHostCall(method, payloadJson)
        }

        qjs.asyncFunction("__hostCall") { args ->
            val method = args.getOrNull(0)?.toString() ?: ""
            val payloadJson = args.getOrNull(1)?.toString() ?: "{}"
            handleAsyncHostCall(method, payloadJson)
        }
    }

    fun invokeCallback(callbackId: String, vararg callbackArgs: Any?) {
        val qjs = quickJs ?: return
        scope.launch {
            runCatching {
                val argsJson = gson.toJson(callbackArgs.toList())
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
        val qjs = quickJs ?: return
        scope.launch {
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

    private fun executeHttpFetch(reqJson: String): String {
        return try {
            val obj = gson.fromJson(reqJson, JsonObject::class.java)
            val url = obj.get("url")?.asString ?: throw IllegalArgumentException("Missing URL")
            val method = (obj.get("method")?.asString ?: "GET").uppercase()
            val bodyBase64 = if (obj.has("bodyBase64") && !obj.get("bodyBase64").isJsonNull) obj.get("bodyBase64").asString else null
            val bodyStr = if (obj.has("body") && !obj.get("body").isJsonNull) obj.get("body").asString else null

            val builder = Request.Builder().url(url)
            var contentType = "application/json"
            val headersObj = obj.getAsJsonObject("headers")
            headersObj?.entrySet()?.forEach { (k, v) ->
                if (!v.isJsonNull) {
                    if (k.equals("content-type", ignoreCase = true)) contentType = v.asString
                    builder.header(k, v.asString)
                }
            }
            if (method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE") {
                val mediaType = contentType.toMediaTypeOrNull()
                val reqBytes = when {
                    bodyBase64 != null -> Base64.decode(bodyBase64, Base64.DEFAULT)
                    bodyStr != null -> bodyStr.toByteArray(Charsets.UTF_8)
                    else -> null
                }
                if (method == "DELETE" && reqBytes == null) {
                    builder.method(method, null)
                } else {
                    builder.method(method, (reqBytes ?: ByteArray(0)).toRequestBody(mediaType))
                }
            } else {
                builder.method(method, null)
            }

            httpClient.newCall(builder.build()).execute().use { resp ->
                val respHeaders = linkedMapOf<String, String>()
                for (name in resp.headers.names()) {
                    respHeaders[name] = resp.headers[name] ?: ""
                }
                val bytes = resp.body.bytes()
                val encodedB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val respContentType = (resp.header("Content-Type") ?: "").lowercase()
                val isLikelyBinary = respContentType.startsWith("image/") ||
                    respContentType.startsWith("audio/") ||
                    respContentType.startsWith("video/") ||
                    respContentType.contains("octet-stream") ||
                    respContentType.contains("application/pdf") ||
                    respContentType.contains("application/zip") ||
                    respContentType.contains("font/")
                val bodyText = if (isLikelyBinary) "" else String(bytes, Charsets.UTF_8)
                gson.toJson(
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
        } catch (e: Throwable) {
            gson.toJson(mapOf("error" to (e.message ?: "Network error")))
        }
    }

    private fun handleSyncHostCall(method: String, payloadJson: String): String? {
        val obj = runCatching { gson.fromJson(payloadJson, JsonObject::class.java) }.getOrNull() ?: JsonObject()
        return when (method) {
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
                val keyBytes = runCatching { Base64.decode(keyB64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                val raw = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                val mac = Mac.getInstance(alg)
                mac.init(SecretKeySpec(keyBytes, alg))
                val digest = mac.doFinal(raw)
                gson.toJson(digest.map { it.toInt() and 0xFF })
            }
            "fs.readExtensionFile" -> {
                val rel = obj.get("path")?.asString ?: return null
                val extRoot = File(extension.dirPath).canonicalFile
                val candidate = File(extRoot, rel.trimStart('/')).canonicalFile
                if (!isWithinRoot(candidate, extRoot) || !candidate.exists() || !candidate.isFile) {
                    null
                } else {
                    gson.toJson(candidate.readText())
                }
            }
            "fs.exists" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                (f != null && f.exists()).toString()
            }
            "fs.read" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                if (f != null && f.exists() && f.isFile) gson.toJson(f.readText()) else null
            }
            "fs.readBase64" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                if (f != null && f.exists() && f.isFile) {
                    gson.toJson(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                } else {
                    null
                }
            }
            "fs.write" -> {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return "false"
                f.parentFile?.mkdirs()
                f.writeText(obj.get("content")?.asString ?: "")
                "true"
            }
            "fs.writeBase64" -> {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return "false"
                val b64 = obj.get("base64")?.asString ?: ""
                val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return "false"
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
                "true"
            }
            "fs.mkdir" -> {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return "false"
                if (f.exists() && f.isDirectory) return "true"
                f.mkdirs().toString()
            }
            "fs.readdir" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString)
                if (f != null && f.exists() && f.isDirectory) {
                    gson.toJson((f.list() ?: emptyArray()).toList())
                } else {
                    null
                }
            }
            "fs.stat" -> {
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
            }
            "fs.unlink" -> {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString)
                (f != null && f.exists() && f.isFile && f.delete()).toString()
            }
            "fs.rm" -> {
                val f = resolveWritableSandboxedFile(obj.get("path")?.asString) ?: return "false"
                if (!f.exists()) return "false"
                val recursive = obj.get("recursive")?.asBoolean ?: false
                if (f.isDirectory && recursive) f.deleteRecursively().toString() else f.delete().toString()
            }
            "fs.rename" -> {
                val src = resolveWritableSandboxedFile(obj.get("src")?.asString) ?: return "false"
                val dest = resolveWritableSandboxedFile(obj.get("dest")?.asString) ?: return "false"
                if (!src.exists()) return "false"
                dest.parentFile?.mkdirs()
                src.renameTo(dest).toString()
            }
            "fs.copyFile" -> {
                val src = resolveSandboxedFile(obj.get("src")?.asString) ?: return "false"
                val dest = resolveWritableSandboxedFile(obj.get("dest")?.asString) ?: return "false"
                if (!src.exists() || !src.isFile) return "false"
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                "true"
            }
            else -> null
        }
    }

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
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val extRoot = File(extension.dirPath).canonicalFile
        val f = File(rawPath).canonicalFile
        return if (isWithinRoot(f, supportRoot) || isWithinRoot(f, extRoot)) f else null
    }

    private fun resolveWritableSandboxedFile(rawPath: String?): File? {
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val f = File(rawPath).canonicalFile
        return if (isWithinRoot(f, supportRoot) && f.path != supportRoot.path) f else null
    }

    private suspend fun handleAsyncHostCall(method: String, payloadJson: String): String? {
        val obj = runCatching { gson.fromJson(payloadJson, JsonObject::class.java) }.getOrNull() ?: JsonObject()
        return when (method) {
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
                "true"
            }
            "clipboard.readText" -> {
                val text = withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                }
                gson.toJson(text)
            }
            "clipboard.clear" -> {
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
            "system.open" -> {
                val target = obj.get("target")?.asString ?: return "false"
                val app = if (obj.has("application") && !obj.get("application").isJsonNull) obj.get("application").asString else null
                withContext(Dispatchers.Main) {
                    openTargetExternally(target, app)
                }
                "true"
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
                val intent = buildViewIntent(target, null)
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
                val targetName = obj.get("name")?.asString ?: "command"
                _toastEvents.emit(
                    RaycastToastEvent(
                        id = "launch_${System.currentTimeMillis()}",
                        title = "launchCommand('$targetName') unavailable",
                        message = "Cross-command launch is not supported in the Android runner yet.",
                        style = "Failure",
                    ),
                )
                "false"
            }
            "oauth.authorize" -> {
                val url = obj.get("url")?.asString ?: return "{}"
                val result = RaycastOAuthManager.launchAuthorizeFlow(context, url)
                gson.toJson(result)
            }
            else -> null
        }
    }

    private fun buildViewIntent(target: String, appPackage: String?): Intent {
        val localFile = when {
            target.startsWith("file://") -> runCatching { File(Uri.parse(target).path ?: "") }.getOrNull()
            target.startsWith("/") -> File(target)
            else -> null
        }
        return if (localFile != null && localFile.exists()) {
            val contentUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
            }.getOrElse { Uri.fromFile(localFile) }
            val ext = MimeTypeMap.getFileExtensionFromUrl(localFile.name).lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!appPackage.isNullOrBlank()) setPackage(appPackage)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!appPackage.isNullOrBlank()) setPackage(appPackage)
            }
        }
    }

    private fun openTargetExternally(target: String, appPackage: String?) {
        runCatching {
            val intent = buildViewIntent(target, appPackage)
            context.startActivity(intent)
        }.onFailure {
            if (!appPackage.isNullOrBlank()) {
                val launch = context.packageManager.getLaunchIntentForPackage(appPackage)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            }
        }
    }

    fun close() {
        runCatching {
            httpClient.dispatcher.cancelAll()
            scope.launch {
                runCatching { quickJs?.evaluate<Any?>("globalThis.__unmountExtension();", "unmount.js", false) }
                runCatching { quickJs?.close() }
                quickJs = null
                scope.cancel()
            }
        }
    }
}
