package com.php127.sms2mail

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 同步短信的本地存储：把读取到的短信以 JSON 存到应用私有目录
 * files/sms_store.json，按短信 id 去重，按时间倒序保留最近若干条。
 * 仅本机可见，不上传。
 */
object SmsStore {

    private const val FILE_NAME = "sms_store.json"
    private val lock = ReentrantLock()

    data class Item(
        val id: Long,
        val address: String,
        val body: String,
        val dateMs: Long
    ) {
        fun dateStr(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(dateMs))
        }
    }

    /** 读取已保存的全部短信（按时间倒序）。 */
    fun loadAll(context: Context): List<Item> {
        return lock.withLock {
            try {
                val file = java.io.File(context.filesDir, FILE_NAME)
                if (!file.exists()) return emptyList()
                val arr = JSONArray(file.readText())
                val list = mutableListOf<Item>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Item(
                            id = o.optLong("id", 0),
                            address = o.optString("address", "未知号码"),
                            body = o.optString("body", ""),
                            dateMs = o.optLong("date", 0)
                        )
                    )
                }
                list.sortedByDescending { it.dateMs }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** 合并新拉取的短信（按 id 去重，新数据覆盖旧数据），保留最近 limit 条。 */
    fun merge(context: Context, incoming: List<Item>, limit: Int = 300) {
        lock.withLock {
            val existing = loadAll(context).associateBy { it.id }.toMutableMap()
            for (it in incoming) existing[it.id] = it
            val merged = existing.values.sortedByDescending { it.dateMs }.take(limit)
            save(context, merged)
        }
    }

    fun clear(context: Context) {
        lock.withLock {
            try {
                java.io.File(context.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun save(context: Context, items: List<Item>) {
        lock.withLock {
            try {
                val arr = JSONArray()
                for (it in items) {
                    val o = JSONObject()
                    o.put("id", it.id)
                    o.put("address", it.address)
                    o.put("body", it.body)
                    o.put("date", it.dateMs)
                    arr.put(o)
                }
                java.io.File(context.filesDir, FILE_NAME).writeText(arr.toString())
            } catch (e: Exception) {
                // 写入失败忽略，下次同步会重试
            }
        }
    }
}
