package com.example.spinach

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "Add a Login with passkey button that logs in a user whose device is registered, and lets the user pick if several are registered."

/** An account registered on this device, shown in the passkey login list. */
data class DeviceAccount(val username: String, val name: String)

/**
 * Remembers the accounts registered on this device, so "Login with passkey" works
 * after logging out (US 01.09.01). A device ID alone can't start a Firebase session,
 * so each account's password is kept here, encrypted with a key that stays in the
 * Android Keystore and can't leave this device. Nothing here is sent to Firestore.
 *
 * Uninstalling the app removes everything. The password then has to be entered once
 * to bring passkey login back.
 *
 * @param context The app's context.
 */
class DeviceAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The accounts registered on this device, sorted by username. */
    fun accounts(): List<DeviceAccount> = prefs.all.keys.sorted().mapNotNull { username ->
        read(username)?.let { DeviceAccount(username, it.optString(NAME)) }
    }

    /** Whether this account is registered on this device. */
    fun contains(username: String): Boolean = prefs.contains(username)

    /**
     * Registers an account on this device, replacing any earlier entry for it.
     * @return `true` if it was saved, `false` if encryption failed.
     */
    fun save(username: String, name: String, password: String): Boolean = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val entry = JSONObject()
            .put(NAME, name)
            .put(IV, encode(cipher.iv))
            .put(PASSWORD, encode(encrypted))
        prefs.edit().putString(username, entry.toString()).apply()
        true
    } catch (e: GeneralSecurityException) {
        false
    }

    /** The account's password, or `null` if it is missing or can't be decrypted. */
    fun password(username: String): String? {
        val entry = read(username) ?: return null
        return try {
            val iv = Base64.decode(entry.getString(IV), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            val encrypted = Base64.decode(entry.getString(PASSWORD), Base64.NO_WRAP)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Removes an account from this device. */
    fun remove(username: String) {
        prefs.edit().remove(username).apply()
    }

    private fun read(username: String): JSONObject? = try {
        prefs.getString(username, null)?.let { JSONObject(it) }
    } catch (e: JSONException) {
        null
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "device_accounts"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "spinach_device_accounts"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val NAME = "name"
        private const val IV = "iv"
        private const val PASSWORD = "password"
    }
}
