package com.php127.sms2mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 开机 / 应用更新后自启监听服务，确保重启手机后转发不中断。
 * 从后台启动前台服务在部分 Android 12+ 机型可能受限制，
 * 失败时不影响使用——用户下次打开 App 时 MainActivity 也会启动本服务。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            try {
                val i = Intent(context, ListenerService::class.java)
                ContextCompat.startForegroundService(context, i)
                AppLog.i(context, "开机/更新后已尝试启动监听服务")
            } catch (e: Exception) {
                AppLog.e(context, "开机自启监听服务失败：${e.message}")
            }
        }
    }
}
