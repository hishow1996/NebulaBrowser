package com.nebula.browser.reader.readability

import android.content.Context
import android.webkit.WebView
import com.nebula.browser.common.FileUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ReadabilityResult(
    val title: String,
    val byline: String,
    val content: String,  // HTML
    val text: String,
    val excerpt: String,
    val length: Int
)

class ReadabilityExtractor(private val ctx: Context) {

    private val readabilityJs by lazy { FileUtil.readabilityJs(ctx) }

    suspend fun extractFromUrl(url: String, onResult: (ReadabilityResult?) -> Unit) {
        // 简化：使用后台WebView加载页面，加载完成后注入readability.js 提取
        val wv = WebView(ctx)
        wv.settings.javaScriptEnabled = true
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(readabilityJs, null)
                view?.postDelayed({
                    view?.evaluateJavascript(
                        "(function(){const r = window.NebulaReadability && window.NebulaReadability.parse();" +
                        "return JSON.stringify({title:r?.title||'',byline:r?.byline||'',content:r?.content||'',text:r?.text||'',excerpt:r?.excerpt||'',length:r?.length||0});})();",
                        { str ->
                            val obj = parseJsResult(str)
                            onResult(obj)
                            view.destroy()
                        })
                }, 600)
            }
        }
        wv.loadUrl(url)
    }

    private fun parseJsResult(jsonString: String?): ReadabilityResult? {
        if (jsonString == null || jsonString == "null") return null
        try {
            val s = jsonString.unescapeJson()
            val o = org.json.JSONObject(s)
            return ReadabilityResult(
                o.optString("title"), o.optString("byline"),
                o.optString("content"), o.optString("text"),
                o.optString("excerpt"), o.optInt("length")
            )
        } catch (e: Exception) { return null }
    }

    private fun String.unescapeJson(): String {
        if (startsWith("\"") && endsWith("\"") && length >= 2) {
            return substring(1, length - 1)
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return this
    }
}
