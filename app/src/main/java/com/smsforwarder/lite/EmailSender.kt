package com.php127.sms2mail

import android.content.Context
import javax.mail.AuthenticationFailedException
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.event.TransportEvent
import javax.mail.event.TransportListener
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.Properties

/**
 * 通过 SMTP 发送邮件。使用 Android 版 JavaMail（com.sun.mail:android-mail）。
 * 全程用 AppLog 记录细节（连接 / 认证 / 投递成功失败），便于排查。
 */
object EmailSender {

    fun send(context: Context, config: Prefs.Config, subject: String, body: String) {
        AppLog.i(context, "开始发送邮件 -> ${config.to}（${config.security} ${config.smtpHost}:${config.smtpPort}）")

        val props = Properties()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "15000"
        props["mail.smtp.writetimeout"] = "15000"
        props["mail.debug"] = "false"

        when (config.security) {
            SecurityMode.SSL -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
                // 标准 SSL 写法：启用 SSL 并信任该 SMTP 主机（规避部分设备信任库不完整导致的握手失败）
                props["mail.smtp.ssl.enable"] = "true"
                props["mail.smtp.ssl.trust"] = config.smtpHost
            }
            SecurityMode.TLS -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.starttls.required"] = "true"
                props["mail.smtp.ssl.trust"] = config.smtpHost
            }
            SecurityMode.NONE -> {
                props["mail.smtp.host"] = config.smtpHost
                props["mail.smtp.port"] = config.smtpPort.toString()
            }
        }

        AppLog.d(context, "正在创建 SMTP 会话并连接 ${config.smtpHost}:${config.smtpPort}（${config.security}）")

        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.username, config.password)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.to))
                setSubject(subject)
                setText(body)
            }

            val transport = session.getTransport("smtp")
            transport.addTransportListener(object : TransportListener {
                override fun messageDelivered(e: TransportEvent) {
                    AppLog.i(context, "SMTP 投递成功（服务器已接收）-> ${config.to}")
                }

                override fun messageNotDelivered(e: TransportEvent) {
                    AppLog.e(context, "SMTP 投递失败（服务器拒绝）-> ${config.to}")
                }

                override fun messagePartiallyDelivered(e: TransportEvent) {
                    AppLog.w(context, "SMTP 部分投递（收件人未全部成功）-> ${config.to}")
                }
            })

            AppLog.d(context, "正在认证并连接账号 ${config.username} ...")
            transport.connect(config.smtpHost, config.smtpPort, config.username, config.password)
            AppLog.d(context, "SMTP 连接已建立 ${config.smtpHost}:${config.smtpPort}")

            AppLog.d(context, "认证成功，正在发送邮件（主题：$subject）")
            transport.sendMessage(message, message.allRecipients)

            AppLog.i(context, "邮件发送完成 -> ${config.to}")
            transport.close()
            AppLog.d(context, "SMTP 连接已关闭")
        } catch (ae: AuthenticationFailedException) {
            AppLog.e(context, "SMTP 认证失败：账号或密码/授权码错误（${config.username}）")
            throw ae
        } catch (me: javax.mail.MessagingException) {
            AppLog.e(context, "SMTP 通信异常：${me.message}")
            throw me
        } catch (e: Exception) {
            AppLog.e(context, "邮件发送异常：${e.message}")
            throw e
        }
    }
}
