package com.nebula.browser.reader.novel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class NovelReaderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 简化：使用 Compose 构建全文阅读页（演示）
        title = "小说阅读"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContent()
    }

    private fun setContent() {
        // 将在后续迭代中以 Compose 填充完整阅读器布局
        val tv = android.widget.TextView(this).apply {
            text = "小说阅读器 - Compose 完整版（演示）"
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

class MangaReaderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "漫画阅读"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val tv = android.widget.TextView(this).apply {
            text = "漫画阅读器 - Compose 滑动版（演示）"
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
