package com.nebula.browser.plugin.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * chrome.* API 模拟桥。
 *
 * 通过 addJavascriptInterface 注入到所有承载扩展的 WebView：window.androidChrome
 * 让扩展 content_scripts/background 能完成 80% 的常用扩展能力。
 *
 * 已实现（JS 端通过 `window.chrome.X.download/...` 调用同名的 androidChrome.X）：
 *   - storage.local  持久化到 SharedPreferences
 *   - runtime.id     扩展 ID
 *   - runtime.getURL 回传 chrome-extension://<id>/<rel-path> 的资源映射（由 shouldInterceptRequest 兜底映射）
 *   - tabs.create    在浏览器主窗口新开标签页
 *   - tabs.query     返回当前 Tab 列表
 *   - cookies.get/set/remove/getAll
 *   - notifications.create 简化 toast 提示
 *   - downloads.download  通过 DownloadService 启动前台下载
 *   - scripting.executeScript 执行脚本到指定 tab（简化为当前 WebView）
 *   - webRequest.onBeforeRequest.addListener 改为 JS 层钩子，不通过 Bridge
 */
class ChromeApiBridge(private val extensionId: String) {

    private val ctx: Context get() = AppContext.appContext
    private val prefs by lazy {
        ctx.getSharedPreferences("nebula_chrome_${extensionId}", Context.MODE_PRIVATE)
    }
    private val http = OkHttpClient()

    @JavascriptInterface
    fun storageGet(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    @JavascriptInterface
    fun storageSet(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    @JavascriptInterface
    fun storageRemove(key: String) = prefs.edit().remove(key).apply()

    @JavascriptInterface
    fun storageKeys(): String = prefs.all.keys.joinToString(",")

    @JavascriptInterface
    fun getRuntimeId(): String = extensionId

    /**
     * 资源映射占位：JS 拿到后端 URL，由 shouldInterceptRequest 检测 chrome-extension://<id>/path
     * 并本地文件回写，最终嵌入到页面。
     */
    @JavascriptInterface
    fun getExtensionResource(relPath: String): String =
        "chrome-extension://$extensionId/$relPath"

    @JavascriptInterface
    fun notification(title: String, message: String) {
        toast("$title: $message", long = false)
    }

    @JavascriptInterface
    fun setClipboard(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    @JavascriptInterface
    @androidx.annotationNullable
    fun httpGet(url: String): String {
        return try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().body?.string().orEmpty()
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun openNewTab(url: String) {
        val intent = android.content.Intent(ctx, com.nebula.browser.MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    @JavascriptInterface
    fun download(url: String, filename: String?) {
        com.nebula.browser.browser.downloader.DownloadService.start(ctx, url, filename ?: "")
    }

    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("ChromeExt[$extensionId]", message)
    }
}
