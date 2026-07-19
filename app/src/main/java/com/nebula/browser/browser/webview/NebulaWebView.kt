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
import com.nebula.browser.browser.tab.TabManager
import com.nebula.browser.common.toast
import com.nebula.browser.media.detector.VideoDetectorInterceptor
import com.nebula.browser.store.SettingsManager
import com.nebula.browser.userscript.injector.UserScriptInjector
import java.io.ByteArrayInputStream

class NebulaWebView(context: Context) : WebView(context) {

    var onVideoDetected: ((String, String?, String?) -> Unit)? = null
    /** 页面访问完成（onPageFinished）后回调一次：(url, title)。供 BrowserFragment 记录历史用。 */
    var onVisit: ((url: String, title: String) -> Unit)? = null
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

        if (SettingsManager.darkWeb && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARK_THEME)) {
            WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_ON)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARK_THEME)) {
            WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_OFF)
        }

        webViewClient = NebulaWebViewClient()
        webChromeClient = NebulaChromeClient()
        addJavascriptInterface(com.nebula.browser.userscript.gmbridge.GmBridge(context), "androidBridge")
    }

    inner class NebulaWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            bootJsInjected = false
            scriptInjector.injectOnPageStarted(view, url)
            extensionInjector.injectOnStarted(view, url)
            val tab = view?.tag as? Tab
            if (tab != null) {
                TabManager.get().update(tab.id) {
                    it.url = url ?: ""
                    it.isLoading = true
                    it.progress = 0
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            scriptInjector.injectOnPageFinished(view, url)
            extensionInjector.injectOnFinished(view, url)
            val tab = view?.tag as? Tab
            if (tab != null) {
                TabManager.get().update(tab.id) {
                    it.url = url ?: ""
                    it.isLoading = false
                    it.progress = 100
                }
                // 通知外部记录浏览历史（BrowserFragment 决定是否要记）
                val safeUrl = url ?: ""
                if (safeUrl.isNotBlank() && !safeUrl.startsWith("about:")) {
                    val title = tab.title.ifBlank { safeUrl }
                    onVisit?.invoke(safeUrl, title)
                }
            }
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
            if (url.startsWith("chrome-extension://")) {
                val mapped = resolveExtensionResource(url)
                if (mapped != null) return mapped
            }
            detector.handle(view, request, onVideoDetected)
            if (SettingsManager.adBlock && AdBlocker.shouldBlock(url)) {
                return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            // 网页视频画质压缩：把视频/流媒体请求改写到本地 VideoProxyServer
            if (com.nebula.browser.media.saver.DataSaverBus.enabled.value &&
                !com.nebula.browser.media.proxy.VideoProxyServer.isProxy(url) &&
                isStreamableVideo(url, request)
            ) {
                return routeThroughProxy(url)
            }
            return super.shouldInterceptRequest(view, request)
        }

        /**
         * 判断该请求是否是网页在线视频流（m3u8/dash/mp4/webm/mkv/flv/ts 或 mime video/*）。
         * 注意排除广告与小图标（按文件大小无法 here 估算，先按规则粗判）。
         */
        private fun isStreamableVideo(url: String, request: WebResourceRequest): Boolean {
            val low = url.lowercase()
            // 跳过极小资源（如 segment 列表中带 .ts.xxx 的非视频）—— 通过 Accept 或 Range 启发判定非小图标：
            // 视频请求几乎都带 Range 或 Accept 含 video
            val accept = request.requestHeaders?.get("Accept") ?: ""
            val range = request.requestHeaders?.get("Range")
            val looksLikeVideoByExt = low.endsWith(".m3u8") || low.contains(".m3u8?") ||
                low.endsWith(".mpd") || low.contains(".mpd?") ||
                low.endsWith(".mp4") || low.contains(".mp4?") ||
                low.endsWith(".webm") || low.contains(".webm?") ||
                low.endsWith(".mkv") || low.endsWith(".flv") ||
                low.endsWith(".ts") || low.contains(".ts?")
            val looksLikeVideoByMime = accept.contains("video/") ||
                accept.contains("application/vnd.apple.mpegurl") ||
                accept.contains("application/x-mpegurl") ||
                accept.contains("application/dash+xml")
            // 排除很可能是图片/音频的情况
            if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".gif") ||
                low.endsWith(".webp") || low.endsWith(".bmp") || low.endsWith(".svg") ||
                low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".aac") ||
                low.endsWith(".ogg") || low.endsWith(".wav") || low.endsWith(".flac")
            ) return false
            // Range 通常出现在视频/大文件分块请求
            return looksLikeVideoByExt || (looksLikeVideoByMime && range != null)
        }

        /** 改写到本地代理并把代理 InputStream 包装为 WebResourceResponse。 */
        private fun routeThroughProxy(url: String): WebResourceResponse? = try {
            val proxyUrl = com.nebula.browser.media.proxy.VideoProxyServer.wrap(url)
            val conn = java.net.URL(proxyUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 60_000
            conn.requestMethod = "GET"
            // 透传原 Range 让代理支持断点续播
            conn.setRequestProperty("User-Agent", "NebulaBrowser/1.0 Proxy")
            val mime = guessStreamMime(url)
            WebResourceResponse(mime, "utf-8", conn.inputStream).apply {
                setStatusCodeAndReasonPhrase(conn.responseCode, "OK")
                responseHeaders = mapOf(
                    "Accept-Ranges" to "bytes",
                    "Cache-Control" to "no-cache",
                    "Access-Control-Allow-Origin" to "*"
                )
            }
        } catch (_: Exception) { null }

        private fun guessStreamMime(url: String): String {
            val l = url.lowercase()
            return when {
                l.contains(".m3u8") -> "application/vnd.apple.mpegurl"
                l.contains(".mpd") -> "application/dash+xml"
                l.contains(".mp4") -> "video/mp4"
                l.contains(".webm") -> "video/webm"
                l.contains(".ts") -> "video/mp2t"
                l.contains(".flv") -> "video/x-flv"
                l.contains(".mkv") -> "video/x-matroska"
                else -> "application/octet-stream"
            }
        }

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
            handler?.proceed()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            val scheme = url.scheme ?: return false
            if (scheme == "http" || scheme == "https") return super.shouldOverrideUrlLoading(view, request)
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
            val tab = view?.tag as? Tab
            if (tab != null) {
                TabManager.get().update(tab.id) { it.progress = newProgress }
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            val tab = view?.tag as? Tab
            if (tab != null && title != null) {
                TabManager.get().update(tab.id) { it.title = title }
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
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
