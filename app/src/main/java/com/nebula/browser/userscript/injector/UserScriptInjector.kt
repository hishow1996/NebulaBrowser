package com.nebula.browser.userscript.injector

import android.content.Context
import android.webkit.WebView
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.FileUtil
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.userscript.matcher.UrlMatcher
import com.nebula.browser.userscript.model.UserScript
import com.nebula.browser.userscript.parser.UserScriptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class UserScriptInjector(@Suppress("unused") private val ctx: Context) {

    /** 在 onPageStarted 时调用：注入 boot loader 与所有 document-start 脚本 */
    fun injectOnPageStarted(view: WebView?, url: String?) {
        if (view == null || url == null) return
        val enabled = getEnabledScriptsBlocking()
        if (enabled.isEmpty()) return
        val scripts = enabled.filter {
            it.meta.runAt == "document-start" && UrlMatcher.shouldRun(it.meta, url, true)
        }
        if (scripts.isEmpty()) return
        val bootJs = FileUtil.nebulaBootJs(ctx)
        runJs(view, bootJs)
        scripts.forEach { runScript(view, it) }
    }

    /** 在 onPageFinished 时调用：注入 document-end / idle 脚本 */
    fun injectOnPageFinished(view: WebView?, url: String?) {
        if (view == null || url == null) return
        val enabled = getEnabledScriptsBlocking()
        enabled.forEach { s ->
            if ((s.meta.runAt == "document-end" || s.meta.runAt == "document-idle")
                && UrlMatcher.shouldRun(s.meta, url, true)) {
                runScript(view, s)
            }
        }
    }

    private fun getEnabledScriptsBlocking(): List<UserScript> = try {
        runBlocking {
            withContext(Dispatchers.IO) {
                AppDatabase.get(ctx).userScriptDao().getEnabled().map {
                    val meta = UserScriptParser.parseMeta(it.code)
                    val id = it.id
                    UserScript(id = id, meta = meta, code = it.code, enabled = it.enabled)
                }
            }
        }
    } catch (e: Exception) { emptyList() }

    private fun runScript(view: WebView, script: UserScript) {
        // eslint-wrap in IIFE，并扩展 GM_* 别名到 androidBridge
        val gmWrap = buildGmWrapper(script)
        val js = "(function(){\n'use strict';\n$gmWrap\n${UserScriptParser.stripMeta(script.code)}\n})();"
        runJs(view, js)
    }

    private fun runJs(view: WebView, js: String) {
        view.evaluateJavascript(js, null)
    }

    private fun buildGmWrapper(script: UserScript): String {
        val grants = script.meta.grants
        val sb = StringBuilder()
        sb.append("const window = window || this;\n")
        sb.append("const unsafeWindow = window;\n")
        if (grants.any { it == "GM_setValue" || it == "GM_getValue" || it == "GM_deleteValue" } || grants.contains("GM.getValue") || grants.contains("none")) {
            sb.append("""
                window.GM_setValue = (k,v) => window.androidBridge && window.androidBridge.setValue(k, String(v));
                window.GM_getValue = (k,d) => window.androidBridge ? window.androidBridge.getValue(k, d||'') : (d||'');
                window.GM_deleteValue = (k) => window.androidBridge && window.androidBridge.deleteValue(k);
                window.GM_listValues = () => window.androidBridge ? (window.androidBridge.listValues().split(',')) : [];
            """.trimIndent())
            sb.append("\n")
        }
        if (grants.contains("GM_xmlhttpRequest") || grants.contains("GM.xmlHttpRequest")) {
            sb.append("""
                window.GM_xmlhttpRequest = function(opts){ try {
                    const r = window.androidBridge.httpRequest(opts.url, opts.method || 'GET');
                    if (opts.onload) opts.onload({ responseText: r, status: 200, statusCode: 200 });
                } catch(e){ if (opts.onerror) opts.onerror(e); } return {};
                };
            """.trimIndent())
            sb.append("\n")
        }
        if (grants.contains("GM_addStyle") || grants.contains("GM.addElement")) {
            sb.append("""
                window.GM_addStyle = function(css){ const s=document.createElement('style'); s.textContent=css; document.head.appendChild(s); };
            """.trimIndent())
            sb.append("\n")
        }
        if (grants.contains("GM_notification") || grants.contains("GM.notification")) {
            sb.append("window.GM_notification = (...) => window.androidBridge && window.androidBridge.notification(...Array.from(arguments).map(String));")
            sb.append("\n")
        }
        if (grants.contains("GM_setClipboard") || grants.contains("GM.setClipboard")) {
            sb.append("window.GM_setClipboard = (t) => window.androidBridge && window.androidBridge.setClipboard(t);")
            sb.append("\n")
        }
        sb.append("console.log('[NebulaUS] running: ${script.meta.name}');")
        return sb.toString()
    }
}
