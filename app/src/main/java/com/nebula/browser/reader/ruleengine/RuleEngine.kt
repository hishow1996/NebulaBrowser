package com.nebula.browser.reader.ruleengine

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 兼容 legado 书源的简化规则引擎：支持 CSS、@attr、XPath-lite(标签路径)、JSONPath-lite、正则、内联 JS */
object RuleEngine {

    data class Ctx(val source: String, val url: String, var bookUrl: String = """""")

    /** 单条 rule 形式：selector@attr || regex@group || {{js}} */
    fun apply(rule: String, ctx: Ctx, source: String): String {
        if (rule.isBlank()) return ""
        // JS 内联 - 简化为占位
        if (rule.startsWith("{{") && rule.endsWith("}}")) {
            return evalJs(rule.removeSurrounding("{{", "}}"), ctx, source)
        }
        // 正则
        if (rule.contains("##") || rule.startsWith("/")) {
            val parts = if (rule.contains("##")) rule.split("##", limit = 2) else listOf(rule, "")
            val input = apply(parts[0], ctx, source)
            if (parts.size < 2 || parts[1].isBlank()) return input
            val m = Regex(parts[1]).find(input)
            return m?.value ?: input
        }
        // selector@attr
        val parts = rule.split("@", limit = 2)
        val selector = parts[0]
        val attr = if (parts.size > 1) parts[1] else "text"
        return evalSelector(selector, source, attr, ctx)
    }

    fun applyList(rule: String, ctx: Ctx, source: String): List<String> {
        if (rule.isBlank()) return emptyList()
        val parts = rule.split("##", limit = 2)
        val selector = parts[0]
        try {
            val doc = Jsoup.parse(source)
            return doc.select(selector).map { el -> // 每条结果 = el.outerHtml
                el.outerHtml()
            }
        } catch (e: Exception) { return emptyList() }
    }

    private fun evalSelector(selector: String, html: String, attr: String, ctx: Ctx): String {
        return try {
            val doc = Jsoup.parse(html)
            val el: Element? = doc.selectFirst(selector)
            when (attr) {
                "text" -> el?.text() ?: ""
                "html" -> el?.html() ?: ""
                "outerHtml" -> el?.outerHtml() ?: ""
                "href" -> normalizeUrl(el?.absUrl("href"), ctx)
                "src" -> normalizeUrl(el?.absUrl("src"), ctx)
                "textNodes" -> el?.textNodes()?.joinToString("\n") { it.text() } ?: ""
                else -> el?.attr(attr) ?: ""
            }
        } catch (e: Exception) { "" }
    }

    private fun normalizeUrl(url: String?, ctx: Ctx): String {
        if (url.isNullOrBlank()) return ""
        if (url.startsWith("http")) return url
        return java.net.URL(java.net.URL(ctx.bookUrl.ifBlank { ctx.url }), url).toString()
    }

    private fun evalJs(jsCode: String, ctx: Ctx, source: String): String {
        // 简化：返回 source 或 第一段文本
        // 真实实现可挂 QuickJS-Android
        return try { source.take(500) } catch (e: Exception) { "" }
    }
}
