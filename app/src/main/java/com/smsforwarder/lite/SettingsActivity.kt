package com.php127.sms2mail

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.php127.sms2mail.databinding.ActivitySettingsBinding

/**
 * 设置页：入口菜单（不直接放邮箱配置表单）。
 * 入口项：邮箱设置 / 授权短信(接收) / 授权短信读取 / 授权常驻前台 / 优化电池。
 * 每项下方展示当前状态，点击触发对应的授权或跳转。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // 授权短信（接收）
    private val recvPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLog.i(this, "短信接收权限授权结果：${if (granted) "已授权" else "被拒绝"}")
        if (!granted) maybeGuideToAppSettings("短信接收")
        refreshStatus()
    }

    // 授权短信读取（同步用）
    private val readPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLog.i(this, "短信读取权限授权结果：${if (granted) "已授权" else "被拒绝"}")
        if (!granted) maybeGuideToAppSettings("短信读取")
        refreshStatus()
    }

    // 授权常驻前台：Android 13+ 需要通知权限，授权后立即启动监听服务
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLog.i(this, "通知权限授权结果：${if (granted) "已授权" else "被拒绝"}")
        startListenerService()
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 顶栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "设置"

        // 底部菜单
        setupBottomNav()

        // 入口点击
        binding.itemEmail.setOnClickListener {
            startActivity(Intent(this, EmailConfigActivity::class.java))
        }
        binding.itemPermRecv.setOnClickListener { requestRecvPermission() }
        binding.itemPermRead.setOnClickListener { requestReadPermission() }
        binding.itemForeground.setOnClickListener { requestForeground() }
        binding.itemBattery.setOnClickListener { requestIgnoreBattery() }
        binding.itemAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // 回到本页时强制校正底部菜单高亮（切走时高亮不动，见 setupBottomNav）
        binding.bottomNav.menu.findItem(R.id.nav_settings).isChecked = true
    }

    /** 授权短信（接收）：已授权则提示，未授权则发起系统授权弹窗。 */
    private fun requestRecvPermission() {
        if (hasPerm(Manifest.permission.RECEIVE_SMS)) {
            binding.tvPermRecvStatus.text = "已授权 ✓（无需重复授权）"
            return
        }
        AppLog.i(this, "设置页：发起短信接收授权")
        recvPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
    }

    /** 授权短信读取（同步收件箱用）。 */
    private fun requestReadPermission() {
        if (hasPerm(Manifest.permission.READ_SMS)) {
            binding.tvPermReadStatus.text = "已授权 ✓（无需重复授权）"
            return
        }
        AppLog.i(this, "设置页：发起短信读取授权")
        readPermLauncher.launch(Manifest.permission.READ_SMS)
    }

    /** 授权常驻前台：Android 13+ 先要通知权限（前台服务通知依赖），然后启动/确认监听服务。 */
    private fun requestForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            AppLog.i(this, "设置页：发起通知权限授权（前台服务通知需要）")
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startListenerService()
        refreshStatus()
    }

    private fun startListenerService() {
        if (!isServiceRunning(ListenerService::class.java)) {
            ContextCompat.startForegroundService(this, Intent(this, ListenerService::class.java))
            AppLog.i(this, "设置页：已启动常驻前台监听服务")
        } else {
            AppLog.i(this, "设置页：监听服务已在运行")
        }
    }

    /** 优化电池：跳系统设置把本应用加入电池优化白名单（保活关键）。 */
    private fun requestIgnoreBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            binding.tvBatteryStatus.text = "已忽略电池优化 ✓（无需重复设置）"
            return
        }
        try {
            AppLog.i(this, "设置页：跳转系统「忽略电池优化」授权")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            // 部分 ROM 没有该入口，退回到通用电池设置页
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** 权限被拒（可能勾了"不再询问"）时，引导去应用详情页手动开。 */
    private fun maybeGuideToAppSettings(name: String) {
        try {
            AppLog.i(this, "$name 权限被拒绝，跳转应用详情页引导手动授权")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
        }
    }

    /** 刷新五个入口的状态行。 */
    private fun refreshStatus() {
        // 邮箱
        val cfg = Prefs.loadConfig(this)
        binding.tvEmailStatus.text = if (cfg.isConfigured) {
            "已配置 ✓ 转发到 ${cfg.to}（${if (cfg.enabled) "已启用" else "已停用"}）"
        } else {
            "未配置，点击进入填写 SMTP"
        }

        // 短信接收
        binding.tvPermRecvStatus.text =
            if (hasPerm(Manifest.permission.RECEIVE_SMS)) "已授权 ✓" else "未授权（实时转发不可用），点击授权"

        // 短信读取
        binding.tvPermReadStatus.text =
            if (hasPerm(Manifest.permission.READ_SMS)) "已授权 ✓" else "未授权（同步不可用），点击授权"

        // 常驻前台
        val running = isServiceRunning(ListenerService::class.java)
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        binding.tvForegroundStatus.text = when {
            running -> "监听服务运行中 ✓（常驻前台）"
            !notifOk -> "缺少通知权限，点击授权并启动服务"
            else -> "未运行，点击启动监听服务"
        }

        // 电池优化
        val pm = getSystemService(PowerManager::class.java)
        binding.tvBatteryStatus.text = if (pm.isIgnoringBatteryOptimizations(packageName)) {
            "已忽略电池优化 ✓"
        } else {
            "未设置（后台可能被杀），点击去设置"
        }
    }

    private fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ActivityManager::class.java)
        for (info in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == info.service.className) return true
        }
        return false
    }

    /** 底部菜单：主页 / 短信列表 / 日志 / 设置，切换 Activity 时复用已有实例（保留状态）。 */
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_settings
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> true
                R.id.nav_list -> { openNoAnim(SmsListActivity::class.java); false }
                R.id.nav_log -> { openNoAnim(LogActivity::class.java); false }
                R.id.nav_home -> { openNoAnim(MainActivity::class.java); false }
                else -> false
            }
        }
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /** 无动画跳转：取消 Activity 切换的进入/退出动画，避免底部菜单切页时闪一下。 */
    private fun openNoAnim(cls: Class<*>) {
        startActivity(Intent(this, cls).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        overridePendingTransition(0, 0)
    }
}
