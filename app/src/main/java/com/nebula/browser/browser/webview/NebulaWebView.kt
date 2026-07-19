package com.nebula.browser.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.nebula.browser.browser.tab.Tab
import com.nebula.browser.common.toast
import com.nebula.browser.media.detector.VideoDetectorInterceptor
import com.nebula.browser.store.SettingsManager
import com.nebula.browser.userscript.injector.UserScriptInjector
import java.io.ByteArrayInputStream

class NebulaWebView(context: Context) : WebView(context) {

    var onVideoDetected: ((String, String?, String?) -> Unit)? = null
    private val detector = VideoDetectorInterceptor()
    private val scriptInjector = UserScriptInjector(context)
    private val extensionInjector = com.nebula.browser.plugin.inject.ContentScriptInjector()
    private var bootJsInjected = false

    @SuppressLint("SetJavaScriptEnabled")
    fun setup() {
        val s: WebSettings = settings
        s.javaScriptEnabled = SettingsManager.javascript
        s.domStorageEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString + " NebulaBrowser/1.0"
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        CookieManager.getInstance().setAcceptCookie(true)

        // 夜间模式CSS注入
        if (SettingsManager.darkWeb && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARK_THEME)) {
            WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_ON)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARK_THEME)) {
            WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_OFF)
        }

        // 时序：先注入boot与所有匹配脚本的 "document-start" 元数据
        webViewClient = NebulaWebViewClient()
        webChromeClient = NebulaChromeClient()
        addJavascriptInterface(com.nebula.browser.userscript.gmbridge.GmBridge(context), "androidBridge")
    }

    inner class NebulaWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            bootJsInjected = false
            // 注入boot loader 与 document-start 脚本
            scriptInjector.injectOnPageStarted(view, url)
            // Chrome 扩展 content_scripts（document_start）
            extensionInjector.injectOnStarted(view, url)
            // 更新Tab
            view?.tag.let { (it as? Tab)?.apply { isLoading = true; this.url = url ?: ""; progress = 0 } }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            scriptInjector.injectOnPageFinished(view, url)
            // Chrome 扩展 content_scripts（document_end/idle）
            extensionInjector.injectOnFinished(view, url)
            view?.tag.let { (it as? Tab)?.apply { isLoading = false; progress = 100 } }
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            // 油猴脚本 @require 预加载（简化） + 视频URL检测 + 广告拦截 + chrome-extension:// 资源映射
            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
            // chrome-extension://<id>/<rel> 资源映射（扩展图标/popup/css/js 等）
            if (url.startsWith("chrome-extension://")) {
                val mapped = resolveExtensionResource(url)
                if (mapped != null) return mapped
            }
            // 视频检测
            detector.handle(view, request, onVideoDetected)
            // 广告拦截
            if (SettingsManager.adBlock && AdBlocker.shouldBlock(url)) {
                return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return super.shouldInterceptRequest(view, request)
        }

        /** 将 chrome-extension://<id>/<rel-path> 映射到本地扩展包内的对应文件 */
        private fun resolveExtensionResource(url: String): WebResourceResponse? {
            return try {
                val uri = android.net.Uri.parse(url)
                val id = uri.host ?: return null
                val relPath = uri.path?.removePrefix("/") ?: return null
                val pkg = com.nebula.browser.plugin.core.ExtensionRegistry.get().get(id) ?: return null
                val file = pkg.resolve(relPath) ?: return null
                if (!file.isFile) return null
                val mime = guessMime(file.name)
                WebResourceResponse(mime, "utf-8", java.io.FileInputStream(file))
            } catch (_: Exception) { null }
        }

        private fun guessMime(name: String): String = when {
            name.endsWith(".html", true) -> "text/html"
            name.endsWith(".css", true) -> "text/css"
            name.endsWith(".js", true) -> "application/javascript"
            name.endsWith(".json", true) -> "application/json"
            name.endsWith(".png", true) -> "image/png"
            name.endsWith(".svg", true) -> "image/svg+xml"
            name.endsWith(".woff", true) -> "font/woff"
            name.endsWith(".woff2", true) -> "font/woff2"
            else -> "application/octet-stream"
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            // 自签证书容错（用户自担风险）
            handler?.proceed()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            val scheme = url.scheme ?: return false
            if (scheme == "http" || scheme == "https") return super.shouldOverrideUrlLoading(view, request)
            // 外部协议触发
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                view?.context?.startActivity(intent)
                return true
            } catch (e: Exception) { return false }
        }
    }

    inner class NebulaChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            view?.tag.let { (it as? Tab)?.apply { progress = newProgress } }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            view?.tag.let { (it as? Tab)?.apply { title?.let { this.title = it } } }
        }

        override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
            // 将favicon处理为参考数据，由上层Tab处理
            super.onReceivedIcon(view, icon)
        }
    }
}

object AdBlocker {
    private val rules = listOf(
        "doubleclick.net", "googlesyndication", "googleadservices",
        "pagead2.googlesyndication", "adview.cn", "tanx.com",
        "ads_", "/ad/banner", "pubmatic", "criteo"
    )
    fun shouldBlock(url: String): Boolean = rules.any { url.contains(it, ignoreCase = true) }
}
