package com.nebula.browser.plugin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nebula.browser.common.toast
import com.nebula.browser.plugin.core.ChromeWebStoreClient
import kotlinx.coroutines.launch

/**
 * 内置 Chrome Web Store 浏览页：
 *   - 加载 https://chrome.google.com/webstore/category/extensions
 *   - 拦截用户点 "添加到 Chrome" 的 update2/crx 重定向与商店 URL
 *   - 自动调用 ChromeWebStoreClient.installFromAnyUrl 完成 CRX 下载与解包
 */
class ChromeWebStoreActivity : AppCompatActivity() {

    private val storeClient = ChromeWebStoreClient()

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Chrome Web Store"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // 拦截 CRX 重定向 → 立即安装
                    if (url.contains("clients2.google.com/service/update2/crx")) {
                        view?.stopLoading()
                        lifecycleScope.launch {
                            toast("开始安装…")
                            val pkg = storeClient.installFromAnyUrl(url)
                            toast(if (pkg != null) "已安装：${pkg.name}" else "安装失败")
                        }
                        return true
                    }
                    // 用户点击商店页面"添加到 Chrome"按钮跳转
                    if (url.contains("chrome.google.com/webstore/detail/") &&
                        (url.contains("detail") || url.contains("/detail/"))) {
                        lifecycleScope.launch {
                            toast("开始安装…")
                            val pkg = storeClient.installFromAnyUrl(url)
                            toast(if (pkg != null) "已安装：${pkg.name}" else "安装失败")
                        }
                        return true
                    }
                    return false
                }
            }
        }
        setContentView(wv)
        wv.loadUrl("https://chrome.google.com/webstore/category/extensions")
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
