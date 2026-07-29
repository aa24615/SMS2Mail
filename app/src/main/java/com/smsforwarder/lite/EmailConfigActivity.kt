package com.php127.sms2mail

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.php127.sms2mail.databinding.ActivityEmailConfigBinding

/**
 * 邮箱设置页（SMTP 配置 + 导入导出）。
 * 由「设置」页的「邮箱设置」入口进入，属于二级页面，返回键/返回按钮回到设置页。
 */
class EmailConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailConfigBinding

    // 导出：让用户选择保存位置（系统文件选择器，无需存储权限）
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val json = Prefs.exportJson(this)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            AppLog.i(this, "配置已导出")
        } catch (e: Exception) {
            AppLog.e(this, "配置导出失败：${e.message}")
        }
    }

    // 导入：让用户选择之前导出的 JSON 文件
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!json.isNullOrBlank() && Prefs.importJson(this, json)) {
                AppLog.i(this, "配置已导入，已自动填充表单")
                reload()
            } else {
                AppLog.e(this, "配置导入失败：文件格式不正确")
            }
        } catch (e: Exception) {
            AppLog.e(this, "配置导入失败：${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 顶栏：左上角显示返回箭头，点击返回上一页（设置页）
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "邮箱设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val secs = arrayOf("SSL", "TLS", "NONE")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, secs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSecurity.adapter = adapter

        reload()

        binding.btnSave.setOnClickListener { save() }

        binding.btnExport.setOnClickListener { exportLauncher.launch("sms2mail_config.json") }
        binding.btnImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "*/*")) }

        // 显示/隐藏密码明文
        binding.cbShowPass.setOnCheckedChangeListener { _, checked ->
            val type = if (checked) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etPass.inputType = type
            binding.etPass.setSelection(binding.etPass.text.length)
        }
    }

    /** 从已保存配置回填表单 */
    private fun reload() {
        val c = Prefs.loadConfig(this)
        binding.etHost.setText(c.smtpHost)
        binding.etPort.setText(c.smtpPort.toString())
        binding.etUser.setText(c.username)
        binding.etPass.setText(c.password)
        binding.etFrom.setText(c.from)
        binding.etTo.setText(c.to)
        binding.switchEnabled.isChecked = c.enabled
        binding.spinnerSecurity.setSelection(
            arrayOf("SSL", "TLS", "NONE").indexOf(c.security.name).coerceAtLeast(0)
        )
    }

    private fun save() {
        val sec = when (binding.spinnerSecurity.selectedItem.toString()) {
            "TLS" -> SecurityMode.TLS
            "NONE" -> SecurityMode.NONE
            else -> SecurityMode.SSL
        }
        val port = binding.etPort.text.toString().toIntOrNull() ?: 465
        val cfg = Prefs.Config(
            smtpHost = binding.etHost.text.toString().trim(),
            smtpPort = port,
            username = binding.etUser.text.toString().trim(),
            password = binding.etPass.text.toString(),
            from = binding.etFrom.text.toString().trim(),
            to = binding.etTo.text.toString().trim(),
            security = sec,
            enabled = binding.switchEnabled.isChecked
        )
        Prefs.saveConfig(this, cfg)
        AppLog.i(this, "配置已保存 -> ${cfg.to}（${cfg.security}）")
        finish()
    }
}
