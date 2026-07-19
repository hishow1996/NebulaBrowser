package com.nebula.browser.plugin.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.nebula.browser.plugin.api.ChromeApiBridge
import com.nebula.browser.plugin.core.ExtensionRegistry

/**
 * 扩展动作弹窗：点击工具栏 action 图标时打开 manifest.action.default_popup 指向的 HTML。
 */
class ExtensionPopupActivity : AppCompatActivity() {
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("extension_id") ?: run { finish(); return }
        val pkg = ExtensionRegistry.get().get(id) ?: run { finish(); return }
        val popup = pkg.popupHtml ?: run { finish(); return }
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(ChromeApiBridge(id), "androidChrome")
        }
        val file = pkg.resolve(popup) ?: run { finish(); return }
        wv.loadUrl("file://" + file.absolutePath)
        setContentView(wv)
    }
}

/**
 * 扩展选项页：加载 manifest.options_ui.page 或 manifest.options_page。
 */
class ExtensionOptionsActivity : AppCompatActivity() {
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("extension_id") ?: run { finish(); return }
        val pkg = ExtensionRegistry.get().get(id) ?: run { finish(); return }
        val page = pkg.optionsPage ?: run { finish(); return }
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(ChromeApiBridge(id), "androidChrome")
        }
        val file = pkg.resolve(page) ?: run { finish(); return }
        wv.loadUrl("file://" + file.absolutePath)
        setContentView(wv)
    }
}
