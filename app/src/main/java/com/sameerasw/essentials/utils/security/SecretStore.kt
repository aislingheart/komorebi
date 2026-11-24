/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Security
 * File: SecretStore.kt
 * Description: Unified Android Keystore AES-256-GCM encrypted store for sensitive tokens,
 *              API keys, OAuth credentials, and password-typed extension preferences.
 */

package com.sameerasw.essentials.utils.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecretStore {
    private const val PREFS_NAME = "komorebi_secret_store_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "komorebi_master_secret_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Encrypts and stores [value] under [key]. If [value] is null or blank, deletes [key].
     * Returns true if stored or cleared cleanly.
     */
    fun putSecret(context: Context, key: String, value: String?): Boolean {
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) {
            removeSecret(context, key)
            return true
        }
        return runCatching {
            val encrypted = encrypt(clean)
            prefs(context).edit().putString(key, encrypted).apply()
            true
        }.getOrDefault(false)
    }

    /**
     * Decrypts and returns the secret stored under [key], or null if missing/unreadable.
     */
    fun getSecret(context: Context, key: String): String? {
        val encoded = prefs(context).getString(key, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Reads [key] from [SecretStore]; if absent, transparently migrates any existing plaintext
     * value from [legacyPrefsName]/[legacyKey] into encrypted storage and removes the plaintext copy.
     */
    fun getOrMigrateSecret(
        context: Context,
        key: String,
        legacyPrefsName: String,
        legacyKey: String,
    ): String? {
        getSecret(context, key)?.let { return it }
        val legacyPrefs = context.applicationContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val legacyValue = legacyPrefs.getString(legacyKey, null)?.trim()
        if (legacyValue.isNullOrEmpty()) return null
        if (putSecret(context, key, legacyValue)) {
            legacyPrefs.edit().remove(legacyKey).apply()
        }
        return legacyValue
    }

    fun removeSecret(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    fun removeByPrefix(context: Context, prefix: String) {
        val p = prefs(context)
        val matching = p.all.keys.filter { it.startsWith(prefix) }
        if (matching.isEmpty()) return
        val editor = p.edit()
        matching.forEach { editor.remove(it) }
        editor.apply()
    }

    internal fun encrypt(plaintext: String, key: SecretKey = getOrCreateKey()): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    internal fun decrypt(encoded: String, key: SecretKey = getOrCreateKey()): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH) { "Invalid encrypted payload" }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private val keyLock = Any()

    private fun getOrCreateKey(): SecretKey =
        synchronized(keyLock) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
            generator.generateKey()
        }
}
