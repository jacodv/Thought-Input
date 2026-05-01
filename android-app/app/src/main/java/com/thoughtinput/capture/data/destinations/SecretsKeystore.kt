package com.thoughtinput.capture.data.destinations

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.thoughtinput.capture.util.CaptureLog

interface SecretsKeystore {
    fun save(ref: KeychainRef, value: String)
    fun loadString(ref: KeychainRef): String?
    fun delete(ref: KeychainRef)
}

class EncryptedKeystore(context: Context) : SecretsKeystore {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "com.thoughtinput.destinations",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        CaptureLog.error("Keystore", "Failed to init EncryptedSharedPreferences: ${e.message}", e)
        context.applicationContext.getSharedPreferences("com.thoughtinput.destinations.fallback", Context.MODE_PRIVATE)
    }

    override fun save(ref: KeychainRef, value: String) {
        prefs.edit().putString(ref.account, value).apply()
    }

    override fun loadString(ref: KeychainRef): String? = prefs.getString(ref.account, null)

    override fun delete(ref: KeychainRef) {
        prefs.edit().remove(ref.account).apply()
    }
}

class InMemoryKeystore : SecretsKeystore {
    private val map = mutableMapOf<String, String>()
    override fun save(ref: KeychainRef, value: String) { map[ref.account] = value }
    override fun loadString(ref: KeychainRef): String? = map[ref.account]
    override fun delete(ref: KeychainRef) { map.remove(ref.account) }
}
