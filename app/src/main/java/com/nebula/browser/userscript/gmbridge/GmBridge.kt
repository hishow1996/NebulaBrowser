package com.nebula.browser.userscript.gmbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.annotations.Nullable

/**
 * 由 UserScript 通过 window.GM_* 调用的桥接对象。
 * addJavascriptInterface(this, "androidBridge") 注入 WebView。
 * JavaScript 包装层（nebula_boot.js）会把 window.GM_setValue 等映射到 androidBridge.setValue 等方法。
 */
class GmBridge(@Suppress("unused") private val ctx: Context) {
    private val client = OkHttpClient()

    @JavascriptInterface
    fun setValue(key: String, value: String) {
        val p = ctx.getSharedPreferences("nebula_gm", Context.MODE_PRIVATE).edit()
        p.putString(key, value).apply()
    }

    @JavascriptInterface
    fun getValue(key: String, default: String = ""): String =
        ctx.getSharedPreferences("nebula_gm", Context.MODE_PRIVATE).getString(key, default) ?: default

    @JavascriptInterface
    fun deleteValue(key: String) {
        ctx.getSharedPreferences("nebula_gm", Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    @JavascriptInterface
    fun listValues(): String =
        ctx.getSharedPreferences("nebula_gm", Context.MODE_PRIVATE).all.keys.joinToString(",")

    @JavascriptInterface
    fun addStyle(css: String) {
        // 这里仅暂存，真正的注入由 JS 包装层完成
    }

    @JavascriptInterface
    fun notification(text: String, title: String = "Nebula脚本") {
        toast("$title: $text")
    }

    @JavascriptInterface
    fun setClipboard(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("NebulaGM", msg)
    }

    /** GM_xmlhttpRequest 同步返回无效，这里返回响应文本供 JS 异步使用 */
    @JavascriptInterface
    @Nullable
    fun httpRequest(url: String, method: String = "GET"): String {
        return try {
            val req = Request.Builder().url(url).method(method.uppercase(),
                if (method.uppercase() == "POST" || method.uppercase() == "PUT" ||
                    method.uppercase() == "PATCH") okhttp3.RequestBody.create(null, ByteArray(0)) else null).build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    @Nullable
    fun fetchResource(scriptId: String, resourceName: String): String {
        // 简化：返回资源URL，由JS fetchBlob获取
        return ""
    }
}
