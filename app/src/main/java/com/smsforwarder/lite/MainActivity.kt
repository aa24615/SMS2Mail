package com.php127.sms2mail

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.app.ActivityManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.php127.sms2mail.databinding.ActivityMainBinding
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionStatus() }

    // 同步按钮：点击后申请读取短信权限，再读取收件箱
    private val syncPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doSyncSms() else {
            AppLog.e(this, "缺少读取短信权限，无法同步")
            binding.tvStatus.text = "同步失败：未授予读取短信权限"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 顶栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "SMS2Mail"

        // 底部菜单
        setupBottomNav()

        binding.btnSettings.setOnClickListener {
            // 直达邮箱设置（底部菜单「设置」进入的是设置入口菜单页）
            startActivity(Intent(this, EmailConfigActivity::class.java))
        }
        binding.btnTest.setOnClickListener { sendTest() }
        binding.btnSync.setOnClickListener { syncSms() }
        binding.btnBattery.setOnClickListener { requestIgnoreBattery() }

        checkPermissions()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        startListener()
        updateStatus()
        updateListenStatus()
        // 回到本页时强制校正底部菜单高亮（切走时高亮不动，见 setupBottomNav）
        binding.bottomNav.menu.findItem(R.id.nav_home).isChecked = true
    }

    /**
     * 底部菜单：主页 / 短信列表 / 日志 / 设置，切换 Activity 时复用已有实例（保留状态）。
     * 关键点：切走时返回 false —— 不改变当前页的选中项，否则用 REORDER_TO_FRONT
     * 切回本页时高亮停在别的项上，再点该项会被系统当成“已选中”而不触发跳转。
     */
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_list -> { openList(); false }
                R.id.nav_log -> { openLog(); false }
                R.id.nav_settings -> { openSettings(); false }
                else -> false
            }
        }
        // 防止点击当前项时触发 reselect 动作（保持无操作）
        binding.bottomNav.setOnItemReselectedListener { }
    }

    private fun openList() = openNoAnim(SmsListActivity::class.java)

    private fun openLog() = openNoAnim(LogActivity::class.java)

    private fun openSettings() = openNoAnim(SettingsActivity::class.java)

    /** 无动画跳转：取消 Activity 切换的进入/退出动画，避免底部菜单切页时闪一下。 */
    private fun openNoAnim(cls: Class<*>) {
        startActivity(Intent(this, cls).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        overridePendingTransition(0, 0)
    }

    private fun checkPermissions() {
        val needed = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    private fun updatePermissionStatus() {
        val recv = hasPerm(Manifest.permission.RECEIVE_SMS)
        val read = hasPerm(Manifest.permission.READ_SMS)
        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        binding.tvPermDetail.text = buildString {
            append("短信接收（实时转发）：")
            append(if (recv) "已授权 ✓" else "未授权 ✗（实时转发不可用）")
            append("\n短信读取（同步收件箱）：")
            append(if (read) "已授权 ✓" else "未授权 ✗（同步不可用）")
            append("\n通知（前台服务保活）：")
            append(if (notif) "已授权 ✓" else "未授权 ✗（监听服务可能无法常驻）")
        }
    }

    private fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    /** 启动常驻监听服务（幂等：已在运行则不会重复创建）。 */
    private fun startListener() {
        if (!isServiceRunning(ListenerService::class.java)) {
            ContextCompat.startForegroundService(this, Intent(this, ListenerService::class.java))
            AppLog.i(this, "已启动短信监听服务")
        }
    }

    private fun updateListenStatus() {
        val running = isServiceRunning(ListenerService::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val ignored = pm.isIgnoringBatteryOptimizations(packageName)
        val smsCount = try { SmsStore.loadAll(this).size } catch (_: Exception) { 0 }
        binding.tvRunDetail.text = buildString {
            append("监听服务：")
            append(if (running) "运行中 ✓（常驻前台）" else "未运行 ✗（回到此页会自动拉起）")
            append("\n电池优化：")
            append(if (ignored) "已忽略 ✓（不易被系统杀掉）" else "未忽略 ✗（后台可能被杀，请点下方按钮）")
            append("\n已同步短信：")
            append("$smsCount 条（见「短信列表」）")
        }
    }

    /** 跳转系统设置，申请把本应用加入电池优化白名单（保活关键）。 */
    private fun requestIgnoreBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            binding.tvStatus.text = "电池优化已忽略 ✓，无需重复设置"
            updateListenStatus()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            // 部分 ROM 没有该入口，退回到通用电池设置页
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ActivityManager::class.java)
        for (info in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == info.service.className) return true
        }
        return false
    }

    /** 刷新「邮箱配置」区块：展示全部配置项（密码脱敏）。 */
    private fun updateStatus() {
        val cfg = Prefs.loadConfig(this)
        binding.tvCfgDetail.text = if (cfg.isConfigured) {
            buildString {
                append("状态：已配置 ✓（${if (cfg.enabled) "转发已启用" else "转发已停用 ✗"}）")
                append("\nSMTP 服务器：${cfg.smtpHost}:${cfg.smtpPort}")
                append("\n加密方式：${when (cfg.security) {
                    SecurityMode.SSL -> "SSL"
                    SecurityMode.TLS -> "TLS(STARTTLS)"
                    SecurityMode.NONE -> "不加密"
                }}")
                append("\n登录账号：${cfg.username}")
                append("\n密码：${maskPassword(cfg.password)}（明文见邮箱设置）")
                append("\n发件人：${cfg.from}")
                append("\n收件人：${cfg.to}")
            }
        } else {
            "状态：未配置 ✗\n请点击下方「配置邮箱」填写 SMTP 信息"
        }
        updatePermissionStatus()
    }

    /** 密码脱敏：只露首尾各 1 位，中间用 * 代替。 */
    private fun maskPassword(p: String): String = when {
        p.isEmpty() -> "(未填写)"
        p.length <= 2 -> "*".repeat(p.length)
        else -> "${p.first()}${"*".repeat(p.length - 2)}${p.last()}"
    }

    private fun sendTest() {
        val cfg = Prefs.loadConfig(this)
        if (!cfg.isConfigured) {
            binding.tvStatus.text = "未配置邮箱，无法发送测试邮件"
            return
        }
        Thread(Runnable {
            try {
                EmailSender.send(this, cfg, "[短信转发] 测试邮件", "这是一封来自 SMS2Mail 的测试邮件。\n时间：${AppLog.now()}")
                runOnUiThread { binding.tvStatus.text = "测试邮件已发送，请检查收件箱" }
                AppLog.i(this, "测试邮件发送成功")
            } catch (e: Exception) {
                runOnUiThread { binding.tvStatus.text = "测试邮件失败：${e.message}" }
                AppLog.e(this, "测试邮件失败：${e.message}")
            }
        }).start()
    }

    /** 「同步短信」入口：先确认 READ_SMS 权限，无则申请。 */
    private fun syncSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AppLog.i(this, "同步短信：缺少读取权限，发起授权")
            syncPermLauncher.launch(Manifest.permission.READ_SMS)
            return
        }
        doSyncSms()
    }

    /**
     * 主动读取收件箱短信并写入本地「短信列表」（不直接转发，转发由列表页手动触发）。
     * 每条短信都会记到 App 运行日志。
     */
    private fun doSyncSms() {
        val limit = 200
        // 进入 loading 状态：显示进度圈、按钮置灰防重复点击
        binding.syncLoading.visibility = android.view.View.VISIBLE
        binding.btnSync.isEnabled = false
        binding.btnSync.text = "正在同步…"
        binding.tvStatus.text = "正在同步短信…"
        Thread(Runnable {
            try {
                val cr = contentResolver
                val uri = Uri.parse("content://sms/inbox")
                val proj = arrayOf("_id", "address", "body", "date")
                val cur = cr.query(uri, proj, null, null, "date DESC")
                val incoming = mutableListOf<SmsStore.Item>()
                cur?.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(it.getColumnIndexOrThrow("_id"))
                        val address = it.getString(it.getColumnIndexOrThrow("address")) ?: "未知号码"
                        val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                        val dateMs = it.getLong(it.getColumnIndexOrThrow("date"))
                        incoming.add(SmsStore.Item(id, address, body, dateMs))
                    }
                }
                SmsStore.merge(this, incoming, limit)
                AppLog.i(this, "同步完成：读取 ${incoming.size} 条短信到「短信列表」（未转发）")
                runOnUiThread {
                    hideSyncLoading()
                    binding.tvStatus.text = "同步完成：已读取 ${incoming.size} 条短信，去「短信列表」查看/转发"
                    updateListenStatus()
                }
            } catch (e: Exception) {
                AppLog.e(this, "同步短信异常：${e.message}")
                runOnUiThread {
                    hideSyncLoading()
                    binding.tvStatus.text = "同步失败：${e.message}"
                }
            }
        }).start()
    }

    /** 退出 loading 状态：隐藏进度圈、恢复按钮。 */
    private fun hideSyncLoading() {
        binding.syncLoading.visibility = android.view.View.GONE
        binding.btnSync.isEnabled = true
        binding.btnSync.text = "同步短信（读取收件箱写入列表）"
    }
}
