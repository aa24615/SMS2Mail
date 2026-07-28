package com.smsforwarder.lite

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 加密方式：SSL（465）/ TLS（587）/ 不加密（25） */
enum class SecurityMode { SSL, TLS, NONE }

object Prefs {

    private const val FILE = "sms_fwd_prefs"

    data class Config(
        val smtpHost: String,
        val smtpPort: Int,
        val username: String,
        val password: String,
        val from: String,
        val to: String,
        val security: SecurityMode,
        val enabled: Boolean
    ) {
        val isConfigured: Boolean
            get() = smtpHost.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                    && from.isNotBlank() && to.isNotBlank() && smtpPort > 0
    }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun loadConfig(context: Context): Config {
        val p = prefs(context)
        val sec = when (p.getString("security", "SSL")) {
            "TLS" -> SecurityMode.TLS
            "NONE" -> SecurityMode.NONE
            else -> SecurityMode.SSL
        }
        return Config(
            smtpHost = p.getString("smtpHost", "") ?: "",
            smtpPort = p.getInt("smtpPort", 465),
            username = p.getString("username", "") ?: "",
            password = p.getString("password", "") ?: "",
            from = p.getString("from", "") ?: "",
            to = p.getString("to", "") ?: "",
            security = sec,
            enabled = p.getBoolean("enabled", true)
        )
    }

    fun saveConfig(context: Context, c: Config) {
        prefs(context).edit().apply {
            putString("smtpHost", c.smtpHost)
            putInt("smtpPort", c.smtpPort)
            putString("username", c.username)
            putString("password", c.password)
            putString("from", c.from)
            putString("to", c.to)
            putString("security", c.security.name)
            putBoolean("enabled", c.enabled)
        }.apply()
    }
}
