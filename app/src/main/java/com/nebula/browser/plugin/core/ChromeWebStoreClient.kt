package com.nebula.browser.plugin.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Chrome Web Store 一键安装客户端。
 *
 * Kiwi 浏览器选择从 Chrome 网上商店通过 ID/URL 重定向拉取 CRX：
 *   https://clients2.google.com/service/update2/crx?response=redirect&prodversion=126.0
 *   &acceptformat=crx2,crx3&x=id%3D<id>%26installsource%3Dondemand%26uc
 *
 * 当用户在 Web Store 浏览器页面点 "添加到 Chrome" 时，Nebula 通过拦截 webRequest
 * 或者提取扩展页面链接上的 extensionId，调用 installById 完成下载与解包。
 *
 * 兼容从 .crx、.zip 直接下载链接的安装：
 *   installByDirectUrl(url)
 */
class ChromeWebStoreClient {
    private val client = OkHttpClient()

    /** Chrome 推荐 prodversion：使用 Chromium 大版本号 */
    private val prodVersion = "126.0.6478.0"

    fun buildCrxDownloadUrl(extensionId: String): String =
        "https://clients2.google.com/service/update2/crx" +
        "?response=redirect&acceptformat=crx2,crx3" +
        "&prodversion=$prodVersion" +
        "&x=id%3D$extensionId%26installsource%3Dondemand%26uc"

    /** 从商店 URL 解析 extensionId，如 https://chrome.google.com/webstore/detail/<name>/<id> */
    fun extractIdFromStoreUrl(url: String): String? {
        val m = Regex("""/[a-z]{32}(?:$|/|\?)""").find(url) ?: return null
        return m.value.trim('/', '?')
    }

    /** 从用户输入的网址（可能是商店详情页或任意 .crx 直链）一键安装 */
    suspend fun installFromAnyUrl(url: String): com.nebula.browser.plugin.model.ExtensionPackage? =
        withContext(Dispatchers.IO) {
            val id = extractIdFromStoreUrl(url)
            val crxUrl = if (id != null) buildCrxDownloadUrl(id) else url
            try {
                val req = Request.Builder().url(crxUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) NebulaBrowser/1.0")
                    .header("Accept", "application/octet-stream,*/*")
                    .build()
                val resp = client.newCall(req).execute()
                val bytes = resp.body?.bytes() ?: return@withContext null
                ExtensionInstaller().installFromBytes(bytes).also {
                    // 标记来自商店
                    if (it != null && id != null) ExtensionRegistry.get().setEnabled(it.id, true)
                }
            } catch (e: Exception) { null }
        }
}
