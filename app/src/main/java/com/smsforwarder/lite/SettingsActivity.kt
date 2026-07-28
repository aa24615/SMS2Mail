package com.smsforwarder.lite

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.smsforwarder.lite.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val secs = arrayOf("SSL", "TLS", "NONE")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, secs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSecurity.adapter = adapter

        val c = Prefs.loadConfig(this)
        binding.etHost.setText(c.smtpHost)
        binding.etPort.setText(c.smtpPort.toString())
        binding.etUser.setText(c.username)
        binding.etPass.setText(c.password)
        binding.etFrom.setText(c.from)
        binding.etTo.setText(c.to)
        binding.switchEnabled.isChecked = c.enabled
        binding.spinnerSecurity.setSelection(secs.indexOf(c.security.name).coerceAtLeast(0))

        binding.btnSave.setOnClickListener { save() }
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
