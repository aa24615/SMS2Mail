package com.php127.sms2mail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.BaseAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.php127.sms2mail.databinding.ActivitySmsListBinding

class SmsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmsListBinding

    private val syncPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runSync() else {
            AppLog.e(this, "缺少读取短信权限，无法同步")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "短信列表"

        setupBottomNav()

        binding.btnResync.setOnClickListener { resync() }
        binding.btnForwardAll.setOnClickListener { forwardAll() }
        binding.btnClear.setOnClickListener { clearList() }

        loadList()
    }

    override fun onResume() {
        super.onResume()
        loadList()
        // 回到本页时强制校正底部菜单高亮（切走时高亮不动，见 setupBottomNav）
        binding.bottomNav.menu.findItem(R.id.nav_list).isChecked = true
    }

    /** 底部菜单：主页 / 短信列表 / 日志 / 设置。切走时返回 false，保持本页高亮不变。 */
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_list
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_list -> true
                R.id.nav_home -> { open(MainActivity::class.java); false }
                R.id.nav_log -> { open(LogActivity::class.java); false }
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

    private fun loadList() {
        val items = SmsStore.loadAll(this)
        binding.listView.adapter = SmsAdapter(items)
        binding.tvCount.text = "共 ${items.size} 条"
    }

    /** 自定义列表适配器：每条短信下方带「发送到邮箱」按钮，支持单条转发。 */
    private inner class SmsAdapter(private val items: List<SmsStore.Item>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = convertView ?: layoutInflater.inflate(R.layout.sms_list_item, parent, false)
            val item = items[position]
            view.findViewById<android.widget.TextView>(R.id.tvItem).text =
                "发件人：${item.address}\n时间：${item.dateStr()}\n${item.body}"
            view.findViewById<android.widget.Button>(R.id.btnSendOne).setOnClickListener {
                forwardOne(item)
            }
            return view
        }
    }

    /** 单条转发：把指定短信发送到邮箱（后台线程执行，结果记入日志并 Toast 提示）。 */
    private fun forwardOne(item: SmsStore.Item) {
        val cfg = Prefs.loadConfig(this)
        if (!cfg.isConfigured) {
            AppLog.e(this, "未配置邮箱，无法转发。请先在「设置 → 邮箱设置」配置")
            android.widget.Toast.makeText(this, "未配置邮箱，请先在设置中配置", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        AppLog.i(this, "单条转发开始 #${item.id} ｜ 来自 ${item.address} ｜ ${item.dateStr()}")
        android.widget.Toast.makeText(this, "正在发送…", android.widget.Toast.LENGTH_SHORT).show()
        Thread(Runnable {
            try {
                EmailSender.send(
                    this, cfg,
                    "[短信转发] ${item.address}",
                    "发件人：${item.address}\n时间：${item.dateStr()}\n\n${item.body}"
                )
                AppLog.i(this, "单条转发成功 #${item.id} -> ${cfg.to}")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "发送成功", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e(this, "单条转发失败 #${item.id}：${e.message}")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "发送失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }).start()
    }

    /** 点「重新同步」：先确认 READ_SMS 权限，再读取收件箱写入列表。 */
    private fun resync() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AppLog.i(this, "同步短信：缺少读取权限，发起授权")
            syncPermLauncher.launch(Manifest.permission.READ_SMS)
            return
        }
        runSync()
    }

    /** 读取收件箱全部短信，合并写入本地列表（不直接转发）。 */
    private fun runSync() {
        val limit = 200
        // 进入 loading 状态：显示进度圈、按钮置灰防重复点击
        binding.syncLoading.visibility = android.view.View.VISIBLE
        binding.btnResync.isEnabled = false
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
                AppLog.i(this, "同步完成：读取 ${incoming.size} 条短信到列表（未转发）")
                runOnUiThread {
                    hideSyncLoading()
                    loadList()
                    android.widget.Toast.makeText(this, "同步完成：${incoming.size} 条", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e(this, "同步短信异常：${e.message}")
                runOnUiThread {
                    hideSyncLoading()
                    android.widget.Toast.makeText(this, "同步失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }).start()
    }

    /** 退出 loading 状态：隐藏进度圈、恢复按钮。 */
    private fun hideSyncLoading() {
        binding.syncLoading.visibility = android.view.View.GONE
        binding.btnResync.isEnabled = true
    }

    /** 把列表里的全部短信批量转发到邮箱（用户手动触发）。 */
    private fun forwardAll() {
        val cfg = Prefs.loadConfig(this)
        if (!cfg.isConfigured) {
            AppLog.e(this, "未配置邮箱，无法转发。请先在主页「配置邮箱」")
            return
        }
        val items = SmsStore.loadAll(this)
        if (items.isEmpty()) {
            AppLog.e(this, "列表为空，无可转发内容")
            return
        }
        Thread(Runnable {
            var sent = 0
            for (it in items) {
                try {
                    EmailSender.send(
                        this, cfg,
                        "[短信转发] ${it.address}",
                        "发件人：${it.address}\n时间：${it.dateStr()}\n\n${it.body}"
                    )
                    sent++
                } catch (e: Exception) {
                    AppLog.e(this, "转发失败 #${it.id}：${e.message}")
                }
            }
            AppLog.i(this, "批量转发完成：成功 $sent / 共 ${items.size}")
            runOnUiThread { loadList() }
        }).start()
    }

    private fun clearList() {
        SmsStore.clear(this)
        AppLog.i(this, "短信列表已清空")
        loadList()
    }
}
