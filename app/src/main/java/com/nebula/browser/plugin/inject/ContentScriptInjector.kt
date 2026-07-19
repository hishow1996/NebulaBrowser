package com.nebula.browser.plugin.inject

import android.webkit.WebView
import com.nebula.browser.plugin.core.ExtensionRegistry
import com.nebula.browser.plugin.model.ExtensionPackage
import com.nebula.browser.userscript.matcher.UrlMatcher
import java.io.File

/**
 * Chrome 扩展 content_scripts 注入器。
 *
 * 实现：
 *   match：与 Chrome match-pattern 同 UserScript 的 @match 算法
 *   run_at：document_start / document_end / document_idle
 *   all_frames：默认仅顶层注入（iframe 简化忽略）
 *   css：在文档头插入 <style> 元素
 *   js：按 manifest 顺序拼接 .js 文件内容注入
 *   注入 chrome.runtime.id / chrome.runtime.getURL 等 API 模拟
 */
class ContentScriptInjector {

    fun injectOnStarted(view: WebView?, url: String?) {
        if (view == null || url == null) return
        for (pkg in ExtensionRegistry.get().list()) {
            if (!pkg.enabled) continue
            for (cs in pkg.contentScripts) {
                if (cs.runAt == "document_start" && matches(pkg, cs, url)) {
                    injectDeclarations(view, pkg, cs)
                }
            }
        }
    }

    fun injectOnFinished(view: WebView?, url: String?) {
        if (view == null || url == null) return
        for (pkg in ExtensionRegistry.get().list()) {
            if (!pkg.enabled) continue
            for (cs in pkg.contentScripts) {
                if (cs.runAt != "document_start" && matches(pkg, cs, url)) {
                    injectDeclarations(view, pkg, cs)
                }
            }
        }
    }

    private fun matches(pkg: ExtensionPackage,
                        cs: com.nebula.browser.plugin.model.ContentScriptDeclaration,
                        url: String): Boolean {
        if (cs.matches.any { UrlMatcher.matchPattern(it, url) }) {
            if (cs.excludeMatches.any { UrlMatcher.matchPattern(it, url) }) return false
            if (cs.excludeGlobs.any { globMatch(it, url) }) return false
            if (cs.includeGlobs.isNotEmpty() && cs.includeGlobs.none { globMatch(it, url) }) return false
            return true
        }
        return false
    }

    private fun globMatch(glob: String, url: String): Boolean =
        Regex(glob.replace(".", "\\.").replace("*", ".*")).containsMatchIn(url)

    private fun injectDeclarations(view: WebView, pkg: ExtensionPackage,
                                   cs: com.nebula.browser.plugin.model.ContentScriptDeclaration) {
        // CSS
        cs.css.forEach { cssPath ->
            val text = pkg.read(cssPath) ?: return@forEach
            runJs(view, "(function(){const s=document.createElement('style');" +
                    "s.textContent=${jsLiteral(text)};document.head.appendChild(s);})();")
        }
        // JS
        val sb = StringBuilder()
        sb.append("(function(){\n")
        sb.append("const chromeNs={};chromeNs.runtime={" +
                "id:${jsLiteral(pkg.id)}," +
                "getURL:(p)=>'chrome-runtime://'+${jsLiteral(pkg.id)}+'/'+p.replace(/^\\//, '')," +
                "onMessage:{addListener:()=>{}},sendMessage:()=>{},connect:()=>({}),lastError:null};\n")
        sb.append("const browserNs={runtime:chromeNs.runtime};\n")
        sb.append("try{window.chrome=window.chrome||chromeNs;}catch(e){}\n")
        sb.append("try{window.browser=window.browser||browserNs;}catch(e){}\n")
        cs.js.forEach { jsPath ->
            val text = pkg.read(jsPath) ?: return@forEach
            sb.append(text).append('\n')
        }
        sb.append("})();")
        runJs(view, sb.toString())
    }

    private fun runJs(view: WebView, js: String) = try { view.evaluateJavascript(js, null) } catch (_: Exception) {}

    private fun jsLiteral(text: String): String {
        val sb = StringBuilder()
        sb.append("'")
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        sb.append("'")
        return sb.toString()
    }
}
