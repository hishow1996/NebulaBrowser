package com.nebula.browser.userscript.matcher

object UrlMatcher {
    /** Chrome match pattern：<scheme>://<host>/<path>，host 支持 *.example.com，path 支持 * */
    fun matchPattern(pattern: String, url: String): Boolean {
        if (pattern == "*") return true
        val parts = pattern.split(":", limit = 2)
        val scheme = if (parts.size == 2) parts[0] else "*"
        val rest = if (parts.size == 2) parts[1].removePrefix("//") else parts[0]

        val slashIdx = rest.indexOf('/')
        val host = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
        val path = if (slashIdx >= 0) rest.substring(slashIdx) else "/"

        val u = try { java.net.URL(url) } catch (e: Exception) { return false }
        if (scheme != "*" && scheme != u.protocol) return false

        val hostPattern = host.replace(".", "\\.").replace("*", ".*")
        val hostRegex = Regex("^$hostPattern$")
        if (!hostRegex.matches(u.host)) return false

        if (path == "/" || path.isEmpty()) return true
        val pathRegex = ("^" + path.replace("*", ".*") + "$").toRegex()
        return pathRegex.containsMatchIn(u.path + if (u.ref.isNotEmpty()) "#${u.ref}" else "")
    }

    fun matchInclude(pattern: String, url: String): Boolean {
        if (pattern == "*") return true
        // 旧式通配/正则混合：将通配 * 转为 .*，其他保留
        return try {
            Regex(pattern.replace("*", ".*")).containsMatchIn(url)
        } catch (e: Exception) { pattern in url }
    }

    fun shouldRun(meta: com.nebula.browser.userscript.model.UserScriptMeta, url: String, isTopFrame: Boolean): Boolean {
        if (meta.noframes && !isTopFrame) return false
        if (meta.excludes.any { matchInclude(it, url) }) return false
        if (meta.matches.any { matchPattern(it, url) }) return true
        if (meta.includes.any { matchInclude(it, url) }) return true
        if (meta.matches.isEmpty() && meta.includes.isEmpty()) return true
        return false
    }
}
