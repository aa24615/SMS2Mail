package com.php127.sms2mail

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.php127.sms2mail.databinding.ActivityLogBinding
import java.nio.charset.StandardCharsets

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding

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
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "运行日志"

        setupBottomNav()

        binding.btnRefreshLog.setOnClickListener { loadLog() }
        binding.btnExportLog.setOnClickListener { exportLog() }

        loadLog()
    }

    override fun onResume() {
        super.onResume()
        loadLog()
        // 回到本页时强制校正底部菜单高亮（切走时高亮不动，见 setupBottomNav）
        binding.bottomNav.menu.findItem(R.id.nav_log).isChecked = true
    }

    /** 底部菜单：主页 / 短信列表 / 日志 / 设置。切走时返回 false，保持本页高亮不变。 */
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_log
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_log -> true
                R.id.nav_home -> { open(MainActivity::class.java); false }
                R.id.nav_list -> { open(SmsListActivity::class.java); false }
                R.id.nav_settings -> { open(SettingsActivity::class.java); false }
                else -> false
            }
        }
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /** 无动画跳转：取消 Activity 切换的进入/退出动画，避免底部菜单切页时闪一下。 */
    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        overridePendingTransition(0, 0)
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
        loadLog()
    }

    /** 通过 MediaStore 写入公共下载目录（Android 10+ 免权限）；旧版回退到传统文件路径。 */
    private fun exportToDownloads(context: android.content.Context, fileName: String, content: String): Boolean {
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
