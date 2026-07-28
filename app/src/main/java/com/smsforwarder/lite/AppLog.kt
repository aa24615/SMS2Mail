package com.smsforwarder.lite

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量日志：同时输出到 Logcat 与 App 私有文件（getExternalFilesDir/logs），
 * 文件超过阈值时自动裁剪保留最近 500 行，避免无限增长。
 */
object AppLog {

    private const val TAG = "SmsFwd"
    private const val MAX_BYTES = 512 * 1024

    private fun file(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "logs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "smsforwarder.log")
    }

    fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    @Synchronized
    fun log(level: Int, context: Context?, msg: String) {
        val line = "${now()} ${levelName(level)} $msg\n"
        Log.println(level, TAG, msg)
        context?.let {
            try {
                val f = file(it)
                if (f.exists() && f.length() >= MAX_BYTES) {
                    val keep = f.readLines().takeLast(500)
                    f.writeText(keep.joinToString("\n") + "\n")
                }
                f.appendText(line)
            } catch (_: Exception) {
                // 日志写入失败不影响主流程
            }
        }
    }

    private fun levelName(level: Int): String = when (level) {
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "D"
    }

    fun i(context: Context?, msg: String) = log(Log.INFO, context, msg)
    fun w(context: Context?, msg: String) = log(Log.WARN, context, msg)
    fun e(context: Context?, msg: String) = log(Log.ERROR, context, msg)

    fun readLog(context: Context): String {
        return try {
            val f = file(context)
            if (!f.exists()) return "(暂无日志)"
            val lines = f.readLines()
            val start = if (lines.size > 2000) lines.size - 2000 else 0
            val sb = StringBuilder()
            for (i in start until lines.size) sb.append(lines[i]).append("\n")
            sb.toString()
        } catch (e: Exception) {
            "(读取日志失败：${e.message})"
        }
    }
}
