package com.sameerasw.essentials.assistant

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AssistantAiSettings {
    private const val PREFS_NAME = "komorebi_assistant_ai"
    private const val KEY_MODE = "mode"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_STYLE = "api_style"
    private const val KEY_PROVIDER_ID = "provider_id"

    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val DEFAULT_MODEL = "gpt-4o-mini"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "komorebi_assistant_ai_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SEPARATOR = ":"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): AssistantAiMode {
        val raw = prefs(context).getString(KEY_MODE, null) ?: return AssistantAiMode.OFF
        return runCatching { AssistantAiMode.valueOf(raw) }.getOrDefault(AssistantAiMode.OFF)
    }

    fun setMode(context: Context, mode: AssistantAiMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getApiStyle(context: Context): AssistantApiStyle {
        val raw = prefs(context).getString(KEY_API_STYLE, null) ?: return AssistantApiStyle.OPENAI
        return runCatching { AssistantApiStyle.valueOf(raw) }.getOrDefault(AssistantApiStyle.OPENAI)
    }

    fun setApiStyle(context: Context, style: AssistantApiStyle) {
        prefs(context).edit().putString(KEY_API_STYLE, style.name).apply()
    }

    fun getProviderId(context: Context): String =
        prefs(context).getString(KEY_PROVIDER_ID, null) ?: "openai"

    fun setProviderId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_PROVIDER_ID, id).apply()
    }

    fun isLocalEndpoint(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return false
        val parsedHost = runCatching {
            val uri = java.net.URI(trimmed)
            uri.host ?: java.net.URI("http://$trimmed").host
        }.getOrNull()
        val host = (parsedHost ?: trimmed).lowercase(java.util.Locale.ROOT)
        if (host.isBlank()) return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host == "::1" ||
            host == "[::1]" ||
            host.endsWith(".local") ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            Regex("""^172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(host)
    }

    fun getBaseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, baseUrl: String) {
        val value = baseUrl.trim()
        if (value.isEmpty()) {
            prefs(context).edit().remove(KEY_BASE_URL).apply()
        } else {
            prefs(context).edit().putString(KEY_BASE_URL, value).apply()
        }
    }

    fun getModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

    fun setModel(context: Context, model: String) {
        val value = model.trim()
        if (value.isEmpty()) {
            prefs(context).edit().remove(KEY_MODEL).apply()
        } else {
            prefs(context).edit().putString(KEY_MODEL, value).apply()
        }
    }

    fun saveApiKey(context: Context, apiKey: String): Boolean {
        val value = apiKey.trim()
        if (value.isEmpty()) {
            prefs(context).edit().remove(KEY_API_KEY).apply()
            return true
        }
        val encrypted = encrypt(value) ?: return false
        prefs(context).edit().putString(KEY_API_KEY, encrypted).apply()
        return true
    }

    fun readApiKey(context: Context): String {
        val stored = prefs(context).getString(KEY_API_KEY, null) ?: return ""
        return decrypt(stored) ?: ""
    }

    fun isConfigured(context: Context): Boolean =
        when (getMode(context)) {
            AssistantAiMode.OFF -> false
            AssistantAiMode.ON_DEVICE -> true
            AssistantAiMode.BYOK -> {
                val baseUrl = getBaseUrl(context)
                val preset = AssistantProviderPresets.byId(getProviderId(context))
                baseUrl.isNotBlank() &&
                    getModel(context).isNotBlank() &&
                    (readApiKey(context).isNotBlank() ||
                        isLocalEndpoint(baseUrl) ||
                        preset?.requiresKey == false)
            }
        }

    fun normalizeBaseUrl(context: Context): String = normalizeBaseUrl(getBaseUrl(context))

    fun normalizeBaseUrl(baseUrl: String): String {
        var url = baseUrl.trim()
        if (url.isEmpty()) return url
        while (url.endsWith("/")) {
            url = url.dropLast(1)
        }
        if (url.endsWith("/chat/completions")) return url
        return "$url/chat/completions"
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null, null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val ciphertext = Base64.encodeToString(
                cipher.doFinal(plain.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
            iv + IV_SEPARATOR + ciphertext
        } catch (_: Exception) {
            null
        }

    private fun decrypt(stored: String): String? {
        val separator = stored.indexOf(IV_SEPARATOR)
        if (separator <= 0) return null
        return try {
            val iv = Base64.decode(stored.substring(0, separator), Base64.NO_WRAP)
            val ciphertext = Base64.decode(stored.substring(separator + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
