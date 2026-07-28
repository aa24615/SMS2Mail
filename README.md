# SmsForwarderLite —— 轻量短信转发器（仅转发到邮箱）

参考开源项目 [pppscn/SmsForwarder](https://github.com/pppscn/SmsForwarder) 的思路，
但**只保留最核心的能力**：

- ✅ 监听手机收到的短信
- ✅ 通过 SMTP 把短信内容转发到指定邮箱
- ✅ 记录运行日志（App 内可看 + 文件落盘 + Logcat）
- ❌ 不做来电转发、APP 通知转发、远程控制、各种机器人等其它功能

目标：包体小、依赖少、逻辑一目了然，方便自己改、自己用。

---

## 项目结构

```
SmsForwarderLite/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/smsforwarder/lite/
│       │   ├── MainActivity.kt        # 主界面：状态/权限/日志/测试
│       │   ├── SettingsActivity.kt    # 配置 SMTP 与收件人
│       │   ├── SmsReceiver.kt         # 短信广播接收器
│       │   ├── ForwarderService.kt     # 前台服务：发邮件
│       │   ├── EmailSender.kt          # SMTP 发送（Android 版 JavaMail）
│       │   ├── Prefs.kt               # 加密存储配置
│       │   └── AppLog.kt              # 日志（Logcat + 文件）
│       └── res/...                     # 布局与主题
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

依赖（刻意精简）：`core-ktx`、`appcompat`、`material`、
`security-crypto`（加密保存邮箱密码）、
`com.sun.mail:android-mail` + `android-activation`（SMTP 发信）。

> 注：`gradle-wrapper.jar` 未随项目打包。请用 **Android Studio** 打开本目录直接构建
> （Android Studio 会自动下载对应 Gradle 版本）；若要用命令行，先执行
> `gradle wrapper --gradle-version 8.5` 生成 wrapper，再 `./gradlew assembleDebug`。

---

## 构建步骤（Android Studio）

1. 用 Android Studio（建议 Hedgehog / Iguana 及以上，内置 JDK 17）打开 `SmsForwarderLite` 目录。
2. 等待 Gradle 同步完成（会下载依赖）。
3. 连接安卓手机（开启 USB 调试），或新建模拟器。
4. 点击 **Run ▶**（或 `Build ▶ Build Bundle(s) / APK(s) ▶ Build APK(s)`）安装到手机。

最低支持 Android 7.0（API 24），目标 API 34。

---

## 使用步骤

1. **授予权限**：首次打开 App 会请求「接收短信」和（Android 13+）「发送通知」权限，
   务必全部允许，否则无法转发。
2. **配置邮箱**：点「配置邮箱」，填写：
   - SMTP 服务器（如 `smtp.qq.com`）
   - 端口 + 加密方式（SSL=465 / TLS=587 / 不加密=25）
   - 账号、密码/授权码、发件人（From）、收件人（To，转发到这个邮箱）
   - 启用转发开关
   - 点「保存」
3. **发测试邮件**：点「发送测试邮件」验证 SMTP 配置是否正确（结果会写进日志）。
4. 之后手机收到短信，会自动转发到邮箱，并在「运行日志」里看到每条记录。

---

## 常见邮箱 SMTP 设置

| 服务商 | SMTP 服务器 | 端口 | 加密 | 密码说明 |
|--------|-------------|------|------|----------|
| QQ 邮箱 | `smtp.qq.com` | 465 | SSL | 用**授权码**（非登录密码），在邮箱设置→账户→开启 SMTP 后获取 |
| 163 邮箱 | `smtp.163.com` | 465 | SSL | 用**授权码** |
| Gmail | `smtp.gmail.com` | 465 | SSL | 用**应用专用密码**（需开启 2FA） |
| Outlook | `smtp.office365.com` | 587 | TLS | 用账户密码 |

> ⚠️ 多数邮箱**不允许直接用登录密码发 SMTP**，需要在网页邮箱里开启 SMTP 服务并生成「授权码/应用密码」。

---

## 日志在哪

- **App 内**：主界面底部「运行日志」实时显示（点「刷新日志」更新）。
- **文件**：`Android/data/com.smsforwarder.lite/files/logs/smsforwarder.log`
  （通过 `adb pull` 或手机文件管理器查看）。
- **Logcat**：过滤标签 `SmsFwd`。

日志文件超过 512KB 会自动裁剪保留最近 500 行。

---

## 注意事项

- 非系统默认短信 App 也能收到 `SMS_RECEIVED` 广播，但部分国产 ROM（小米/华为/OPPO 等）
  会限制后台应用接收短信。如转发不生效，请到系统「电池/自启动管理」里把本 App 设为
  **允许后台运行 / 自启动 / 不限制耗电**。
- 转发的是收件箱新到短信，已存在的旧短信不会转发。
- 邮件密码使用 `EncryptedSharedPreferences`（AES256）加密存储，不会明文落盘；
  但 Root 设备仍有被读取风险，请知悉。

---

## 与完整版 SmsForwarder 的区别

完整版功能丰富（来电、通知、钉钉/飞书/企业微信/Telegram/Webhook 等多通道、
远程控制服务端等），代价是体积大、依赖多。
本精简版**只做一件事**：短信 → 邮箱，并保留完整日志，适合备用机长期挂着用。
