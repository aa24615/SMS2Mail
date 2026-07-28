package com.php127.sms2mail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

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

    /** 把当前配置序列化为 JSON 字符串（含密码，明文，请妥善保管） */
    fun exportJson(context: Context): String {
        val c = loadConfig(context)
        return JSONObject().apply {
            put("smtpHost", c.smtpHost)
            put("smtpPort", c.smtpPort)
            put("username", c.username)
            put("password", c.password)
            put("from", c.from)
            put("to", c.to)
            put("security", c.security.name)
            put("enabled", c.enabled)
        }.toString(2)
    }

    /** 从 JSON 字符串解析并保存配置；成功返回 true，失败（格式错误）返回 false */
    fun importJson(context: Context, json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val sec = when (o.optString("security", "SSL")) {
                "TLS" -> SecurityMode.TLS
                "NONE" -> SecurityMode.NONE
                else -> SecurityMode.SSL
            }
            val cfg = Config(
                smtpHost = o.optString("smtpHost", ""),
                smtpPort = o.optInt("smtpPort", 465),
                username = o.optString("username", ""),
                password = o.optString("password", ""),
                from = o.optString("from", ""),
                to = o.optString("to", ""),
                security = sec,
                enabled = o.optBoolean("enabled", true)
            )
            saveConfig(context, cfg)
            true
        } catch (e: Exception) {
            false
        }
    }
}
