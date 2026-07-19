package com.nebula.browser.browser

/**
 * 跨 Activity 的 URL 转发队列：BookmarkActivity / HistoryActivity / 外部 Intent 等
 * 把要打开的 URL 写入 [pending]，BrowserFragment 在 onResume 时消费一次。
 *
 * 用 volatile 单例避免对 MainActivity 的 onNewIntent 改造，且能跨进程内任意层级传递。
 */
object UrlRouter {
    @Volatile
    var pending: String? = null

    /** 投递一个待打开 URL，返回 true 表示成功覆盖（null 时返回 false）。 */
    fun offer(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        pending = url
        return true
    }

    /** 取出（并清空）待打开 URL。 */
    fun consume(): String? {
        val v = pending
        pending = null
        return v
    }
}
