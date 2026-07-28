package com.smsforwarder.lite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smsforwarder.lite.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnTest.setOnClickListener { sendTest() }
        binding.btnRefreshLog.setOnClickListener { loadLog() }

        checkPermissions()
        updateStatus()
        loadLog()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        loadLog()
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
                EmailSender.send(cfg, "[短信转发] 测试邮件", "这是一封来自 SmsForwarderLite 的测试邮件。\n时间：${AppLog.now()}")
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
}
