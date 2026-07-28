package com.smsforwarder.lite

import android.util.Log
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * 通过 SMTP 发送邮件。使用 Android 版 JavaMail（com.sun.mail:android-mail）。
 */
object EmailSender {

    private const val TAG = "SmsFwd"

    fun send(config: Prefs.Config, subject: String, body: String) {
        val props = Properties()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "15000"

        when (config.security) {
            SecurityMode.SSL -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
                props["mail.smtp.socketFactory.port"] = config.smtpPort.toString()
                props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                props["mail.smtp.socketFactory.fallback"] = "false"
            }
            SecurityMode.TLS -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.starttls.required"] = "true"
            }
            SecurityMode.NONE -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
            }
        }

        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.username, config.password)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.to))
            setSubject(subject)
            setText(body)
        }

        Log.d(TAG, "发送邮件 -> ${config.to} via ${config.smtpHost}:${config.smtpPort}")
        Transport.send(message)
    }
}
