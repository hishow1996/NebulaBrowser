package com.nebula.browser.media.detector

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.collection.LruCache

object VideoDetectorInterceptor {
    private val recentUrls = LruCache<String, String>(20)
    private val mimeMatchers = arrayOf("video/", "application/vnd.apple.mpegurl",
        "application/x-mpegurl", "application/dash+xml")
    private val extMatchers = arrayOf(".m3u8", ".mp4", ".flv", ".ts", ".mpd", ".webm", ".mov", ".mkv")

    fun handle(view: WebView?, request: WebResourceRequest?, onDetect: ((String, String?, String?) -> Unit)?) {
        if (view == null || request == null || onDetect == null) return
        val url = request.url.toString()
        val mime = mimeMatchers.firstOrNull { url.contains(it, ignoreCase = true) }
        val ext = extMatchers.firstOrNull { url.contains(it, ignoreCase = true) }
        if (mime != null || ext != null) {
            recentUrls.put(url, view.url ?: url)
            onDetect(url, mime ?: ext, view.url)
        }
    }

    fun recentList(): List<String> = recentUrls.snapshot().keys.toList().reversed()
    fun clear() = recentUrls.evictAll()
}
