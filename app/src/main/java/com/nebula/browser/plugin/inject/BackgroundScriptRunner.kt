package com.nebula.browser.plugin.inject

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.plugin.core.ExtensionRegistry
import com.nebula.browser.plugin.model.Background
import com.nebula.browser.plugin.model.ExtensionPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import android.webkit.WebView
import android.annotation.SuppressLint

/**
 * 后台脚本运行器。
 *
 * Chrome MV2：background.scripts / background.page
 * Chrome MV3：background.service_worker
 *
 * 由于 Android WebView 不支持 Service Worker，Kiwi 采用的简化路线：
 *   - 在一个隐藏的离屏 WebView 中加载 background 页（或拼接 background.scripts 的 IIFE）
 *   - 共享 ExtensionRegistry 状态与 chrome.* API 模拟
 *   - 单例运行时，启停由扩展启用状态控制
 */
class BackgroundScriptRunner private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @SuppressLint("StaticFieldLeak")
    private var host: WebView? = null
    private val http = OkHttpClient()
    private val loaded = mutableSetOf<String>()

    fun ensureHost(context: Context) {
        if (host != null) return
        host = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(BackgroundBridge(), "nebulaBackgroundBridge")
            loadData("<html><body></body></html>", "text/html", "utf-8")
        }
    }

    fun startAll(context: Context) {
        ensureHost(context)
        ExtensionRegistry.get().list().filter { it.enabled && it.background != null }
            .forEach { start(it) }
    }

    fun start(pkg: ExtensionPackage) {
        val bg = pkg.background ?: return
        val h = host ?: return
        if (!loaded.add(pkg.id)) return
        scope.launch {
            val script = buildScripts(pkg, bg)
            try { h.evaluateJavascript(script, null) } catch (_: Exception) {}
        }
    }

    fun stop(pkg: ExtensionPackage) {
        loaded.remove(pkg.id)
        // 后台脚本无明确生命周期，仅置标记。可由 content_scripts 通过 runtime.sendMessage 失活处理
    }

    fun release() { try { host?.destroy() } catch (_: Exception) {} ; host = null; loaded.clear() }

    private fun buildScripts(pkg: ExtensionPackage, bg: Background): String {
        val sb = StringBuilder()
        sb.append("(function(){\n")
        sb.append("const chromeNs={runtime:{id:${literal(pkg.id)}," +
                "getURL:(p)=>'chrome-runtime://${pkg.id}/'+p.replace(/^\\//, '')," +
                "onMessage:{addListener:()=>{},sendResponse:()=>{}},sendMessage:()=>{},connect:()=>({})},")
        sb.append("storage:{local:{get:(k)=>null,set:()=>{},remove:()=>{},clear:()=>{}},sync:{get:()=>null,set:()=>{}},onChanged:{addListener:()=>{}}},")
        sb.append("tabs:{query:()=>[],create:()=>{},update:()=>{},remove:()=>{},sendMessage:()=>{},onUpdated:{addListener:()=>{}},onRemoved:{addListener:()=>{}}},")
        sb.append("cookies:{get:()=>{},set:()=>{},remove:()=>{},getAll:()=>{}},")
        sb.append("webRequest:{onBeforeRequest:{addListener:()=>{}},onCompleted:{addListener:()=>{}}},")
        sb.append("alarms:{create:()=>{},clear:()=>{},onAlarm:{addListener:()=>{}}}};\n")
        sb.append("window.chrome=chromeNs;window.browser=chromeNs;\n")
        bg.scripts.forEach { path ->
            val text = pkg.read(path) ?: return@forEach
            sb.append(text).append('\n')
        }
        // MV3 service_worker 也作为脚本拼接
        bg.serviceWorker?.let { pkg.read(it)?.let { text -> sb.append(text).append('\n') } }
        // MV2 page
        bg.page?.let { page ->
            sb.append("(function(){const url='chrome-extension://${pkg.id}/$page';" +
                    "window.addEventListener('load',()=>{ /* page bg, no-op */ });})();\n")
        }
        sb.append("})();")
        return sb.toString()
    }

    private fun literal(text: String): String = "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"

    /**
     * 后台桥：与隐藏 WebView 共享的工具，目前仅 stub（API 全在前端 JS 模拟）
     */
    private class BackgroundBridge {
        @android.webkit.JavascriptInterface
        fun log(tag: String, message: String) {
            android.util.Log.d("NebulaBG[$tag]", message)
        }
    }

    companion object {
        @Volatile private var INST: BackgroundScriptRunner? = null
        fun get(): BackgroundScriptRunner = INST ?: synchronized(this) {
            INST ?: BackgroundScriptRunner().also { INST = it }
        }
    }
}
