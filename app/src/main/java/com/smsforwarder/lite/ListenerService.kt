package com.php127.sms2mail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 常驻前台服务：运行时动态注册短信广播接收器。
 *
 * 为什么这样做：在 AndroidManifest 里静态注册的 SMS_RECEIVED 接收器，在 App 退到后台后
 * 很容易被厂商 ROM / Android 8+ 后台限制掐断，导致"收不到短信"。
 * 而运行时（registerReceiver）注册的接收器，只要本服务还活着就会持续收到广播，
 * 不受上述后台广播限制影响，转发更可靠。
 *
 * 配合 BootReceiver 开机自启、onTaskRemoved 自拉起，尽量保证一直在线。
 */
class ListenerService : Service() {

    private val CHANNEL_ID = "sms_listen_channel"
    private val NOTIF_ID = 100
    private var smsReceiver: SmsReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (smsReceiver == null) {
            smsReceiver = SmsReceiver()
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = 999
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(smsReceiver, filter)
            }
            AppLog.i(this, "监听服务已启动，已动态注册短信接收器（前台服务保活中）")
        }
        // START_STICKY：被系统回收后尽量拉起
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户划掉最近任务时，立刻把自己重新拉起，尽量不被杀
        val restart = Intent(this, ListenerService::class.java)
        ContextCompat.startForegroundService(this, restart)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            smsReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        smsReceiver = null
        AppLog.w(this, "监听服务已停止")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "短信转发监听", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (launchIntent != null)
            PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("短信转发监听中")
            .setContentText("收到短信将自动转发到邮箱")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
