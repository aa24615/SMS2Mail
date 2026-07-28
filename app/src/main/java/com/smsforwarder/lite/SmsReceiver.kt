package com.php127.sms2mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * 短信广播接收器：仅在收到系统 SMS 广播时触发，提取发件人与内容，
 * 然后启动前台服务去发邮件（广播接收器不宜做耗时网络操作）。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            AppLog.w(context, "收到短信广播，但解析出的短信内容为空，已忽略")
            return
        }

        val body = StringBuilder()
        var sender: String? = null
        for (m in messages) {
            if (sender == null) sender = m.displayOriginatingAddress
            body.append(m.messageBody)
        }
        val from = sender ?: "unknown"
        val text = body.toString()

        AppLog.i(context, "收到短信 from=$from 字数=${text.length}")

        val i = Intent(context, ForwarderService::class.java).apply {
            putExtra("sender", from)
            putExtra("body", text)
        }
        try {
            ContextCompat.startForegroundService(context, i)
            AppLog.i(context, "已请求转发服务处理 from=$from")
        } catch (e: Exception) {
            // 启动前台服务失败（多见于后台限制 / 权限问题），记下原因便于排查
            AppLog.e(context, "启动转发服务失败 from=$from: ${e.message}")
        }
    }
}
