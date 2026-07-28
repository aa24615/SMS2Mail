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

    private val exportPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doExportLog() else {
            AppLog.e(this, "缺少存储权限，无法导出日志到旧版下载目录")
            binding.tvStatus.text = "导出失败：未授予存储权限"
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

        binding.btnSettings.setOnClickListener { openSettings() }
        binding.btnTest.setOnClickListener { sendTest() }
        binding.btnRefreshLog.setOnClickListener { loadLog() }
        binding.btnExportLog.setOnClickListener { exportLog() }
        binding.btnBattery.setOnClickListener { requestIgnoreBattery() }

        checkPermissions()
        updateStatus()
        loadLog()
    }

    override fun onResume() {
        super.onResume()
        startListener()
        updateStatus()
        updateListenStatus()
        loadLog()
    }

    /** 底部菜单：主页 / 设置，切换 Activity 时复用已有实例（保留状态）。 */
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_settings -> { openSettings(); true }
                else -> false
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
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
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        binding.tvPerm.text = if (sms) "短信权限：已授权 ✓" else "短信权限：未授权（无法转发）"
    }

    /** 启动常驻监听服务（幂等：已在运行则不会重复创建）。 */
    private fun startListener() {
        if (!isServiceRunning(ListenerService::class.java)) {
            ContextCompat.startForegroundService(this, Intent(this, ListenerService::class.java))
            AppLog.i(this, "已启动短信监听服务")
        }
    }

    private fun updateListenStatus() {
        val running = isServiceRunning(ListenerService::class.java)
        binding.tvListen.text = if (running) "监听服务：运行中 ✓（常驻前台）" else "监听服务：未运行（点此页任意按钮可拉起）"
        val pm = getSystemService(PowerManager::class.java)
        val ignored = pm.isIgnoringBatteryOptimizations(packageName)
        if (!ignored) {
            binding.tvListen.append("\n⚠️ 电池优化未关闭，后台可能被杀，请点上方「忽略电池优化」")
        }
    }

    /** 跳转系统设置，申请把本应用加入电池优化白名单（保活关键）。 */
    private fun requestIgnoreBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            binding.tvListen.text = "监听服务：电池优化已关闭 ✓"
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

    private fun updateStatus() {
        val cfg = Prefs.loadConfig(this)
        if (cfg.isConfigured) {
            binding.tvStatus.text = "已配置，转发到：${cfg.to}\nSMTP：${cfg.smtpHost}:${cfg.smtpPort}（${cfg.security}）\n启用：${if (cfg.enabled) "是" else "否"}"
        } else {
            binding.tvStatus.text = "未配置邮箱，请先点击「配置邮箱」"
        }
        updatePermissionStatus()
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

    private fun loadLog() {
        binding.tvLog.text = AppLog.readLog(this)
    }

    private fun exportLog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 及以下：写下载目录需要存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                exportPermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        doExportLog()
    }

    private fun doExportLog() {
        val stamp = AppLog.now().replace(' ', '_').replace(':', '-')
        val fileName = "sms2mail_log_$stamp.txt"
        val ok = exportToDownloads(this, fileName, AppLog.fullLog(this))
        if (ok) {
            AppLog.i(this, "日志已导出到下载目录：$fileName")
            binding.tvStatus.text = "日志已导出：$fileName（下载目录）"
        } else {
            AppLog.e(this, "日志导出失败")
            binding.tvStatus.text = "日志导出失败，详见运行日志"
        }
    }

    /** 通过 MediaStore 写入公共下载目录（Android 10+ 免权限）；旧版回退到传统文件路径。 */
    private fun exportToDownloads(context: Context, fileName: String, content: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val itemUri = resolver.insert(collection, values) ?: return false
                resolver.openOutputStream(itemUri)?.use { os ->
                    os.write(content.toByteArray(StandardCharsets.UTF_8))
                } ?: return false
                true
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                java.io.File(dir, fileName).writeText(content)
                true
            }
        } catch (e: Exception) {
            AppLog.e(context, "日志导出异常：${e.message}")
            false
        }
    }
}
