package com.php127.sms2mail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.php127.sms2mail.databinding.ActivityAboutBinding

/**
 * 关于本项目页：展示应用简介、版本、开源协议与版权信息，并与 README 口径保持一致。
 * 由「设置」页的「关于本项目」入口进入，属于二级页面，通过顶栏左上角箭头返回设置页。
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 顶栏：左上角返回箭头，点击返回上一页（设置页）
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "关于"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 版本号取自 build.gradle 的 versionName / versionCode
        binding.tvVersion.text =
            "版本 ${BuildConfig.VERSION_NAME}（Build ${BuildConfig.VERSION_CODE}）"
    }
}
