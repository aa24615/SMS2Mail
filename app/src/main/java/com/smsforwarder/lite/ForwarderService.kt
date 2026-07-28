package com.php127.sms2mail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 前台服务：接收 SMS 内容后通过 SMTP 发送邮件。
 * 用前台服务是为了确保系统不会在发邮件的网络过程中杀掉进程，
 * 同时 Android 8+ 要求后台耗时任务用前台服务并展示通知。
 */
class ForwarderService : Service() {

    private val CHANNEL_ID = "sms_fwd_channel"
    private val NOTIF_ID = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender")
        val body = intent?.getStringExtra("body")

        if (sender == null || body == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val cfg = Prefs.loadConfig(this)
        if (!cfg.enabled) {
            AppLog.w(this, "转发已禁用，跳过 from=$sender")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("正在转发短信…"))

        Thread(Runnable {
            try {
                if (!cfg.isConfigured) {
                    AppLog.w(this, "邮箱未配置，跳过转发 from=$sender（请到「配置邮箱」填写 SMTP 并保存）")
                } else {
                    AppLog.i(this, "开始转发短信 from=$sender -> ${cfg.to}（${cfg.security} ${cfg.smtpHost}:${cfg.smtpPort}）")
                    val subject = "[短信转发] 来自 $sender"
                    val text = "发件人: $sender\n接收时间: ${AppLog.now()}\n\n内容:\n$body"
                    AppLog.d(this, "短信内容长度=${text.length} 字，准备发送")
                    EmailSender.send(this, cfg, subject, text)
                    AppLog.i(this, "邮件转发成功 from=$sender -> ${cfg.to}")
                }
            } catch (e: Exception) {
                AppLog.e(this, "邮件转发失败 from=$sender: ${e.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }).start()

        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "短信转发", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (launchIntent != null)
            PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS2Mail")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
