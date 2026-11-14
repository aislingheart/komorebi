package com.sameerasw.essentials.raycast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
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
                    _errorState.value = "Command entrypoint '${command.name}.js' not found in ${extension.dirPath}"
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

    private fun executeHttpFetch(reqJson: String): String {
        return try {
            val obj = gson.fromJson(reqJson, JsonObject::class.java)
            val url = obj.get("url")?.asString ?: throw IllegalArgumentException("Missing URL")
            val method = (obj.get("method")?.asString ?: "GET").uppercase()
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
                val reqBody = (bodyStr ?: "").toRequestBody(mediaType)
                builder.method(method, if (method == "DELETE" && bodyStr == null) null else reqBody)
            } else {
                builder.method(method, null)
            }

            httpClient.newCall(builder.build()).execute().use { resp ->
                val respHeaders = linkedMapOf<String, String>()
                for (name in resp.headers.names()) {
                    respHeaders[name] = resp.headers[name] ?: ""
                }
                gson.toJson(
                    mapOf(
                        "status" to resp.code,
                        "statusText" to resp.message,
                        "url" to resp.request.url.toString(),
                        "headers" to respHeaders,
                        "body" to (resp.body?.string() ?: ""),
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
                val alg = when ((obj.get("alg")?.asString ?: "sha256").lowercase()) {
                    "md5" -> "MD5"
                    "sha1", "sha-1" -> "SHA-1"
                    "sha512", "sha-512" -> "SHA-512"
                    else -> "SHA-256"
                }
                val input = obj.get("input")?.asString ?: ""
                val digest = MessageDigest.getInstance(alg).digest(input.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                gson.toJson(hex)
            }
            "fs.readExtensionFile" -> {
                val rel = obj.get("path")?.asString ?: return null
                val extRoot = File(extension.dirPath).canonicalFile
                val candidate = File(extRoot, rel.trimStart('/')).canonicalFile
                if (!candidate.path.startsWith(extRoot.path) || !candidate.exists() || !candidate.isFile) {
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
            "fs.write" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString) ?: return "false"
                f.parentFile?.mkdirs()
                f.writeText(obj.get("content")?.asString ?: "")
                "true"
            }
            "fs.mkdir" -> {
                val f = resolveSandboxedFile(obj.get("path")?.asString) ?: return "false"
                f.mkdirs().toString()
            }
            else -> null
        }
    }

    private fun resolveSandboxedFile(rawPath: String?): File? {
        if (rawPath.isNullOrBlank()) return null
        val supportRoot = RaycastExtensionRepository.supportDir(context, extension.id).canonicalFile
        val extRoot = File(extension.dirPath).canonicalFile
        val f = File(rawPath).canonicalFile
        return if (f.path.startsWith(supportRoot.path) || f.path.startsWith(extRoot.path)) f else null
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
                withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Raycast", text))
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
            "oauth.authorize" -> {
                val url = obj.get("url")?.asString ?: return "{}"
                val result = RaycastOAuthManager.launchAuthorizeFlow(context, url)
                gson.toJson(result)
            }
            else -> null
        }
    }

    private fun openTargetExternally(target: String, appPackage: String?) {
        runCatching {
            val uri = Uri.parse(target)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!appPackage.isNullOrBlank()) setPackage(appPackage)
            }
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
            scope.launch {
                runCatching { quickJs?.evaluate<Any?>("globalThis.__unmountExtension();", "unmount.js", false) }
                runCatching { quickJs?.close() }
                quickJs = null
                scope.cancel()
            }
        }
    }
}
