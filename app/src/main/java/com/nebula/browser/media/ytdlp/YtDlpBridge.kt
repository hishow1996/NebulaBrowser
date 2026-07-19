package com.nebula.browser.media.ytdlp

import android.content.Context
import com.nebula.browser.common.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object YtDlpBridge {
    suspend fun ensureBinary(context: Context): File? = withContext(Dispatchers.IO) {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val name = when {
            abi.startsWith("arm64") -> "bin_arm64"
            abi.startsWith("armeabi") -> "bin_arm"
            abi.startsWith("x86_64") -> "bin_x64"
            abi.startsWith("x86") -> "bin_x86"
            else -> "bin_arm64"
        }
        val target = File(context.filesDir, "ytdlp_bin")
        // 1) 已存在可直接复用
        if (target.exists() && target.canExecute()) return@withContext target
        // 2) 尝试从 assets 中拷annabentbin（assets/ytdlp/bin_arm64 等）
        try {
            context.assets.open("ytdlp/$name").use { input ->
                FileOutputStream(target).use { input.copyTo(it) }
            }
            if (target.setExecutable(true, true)) return@withContext target
        } catch (_: Exception) { /* 继续走运行时下载分支 */ }
        // 3) 回退：从 SettingsManager.ytdlpBinaryUrl 在线下载首个二进制
        val url = com.nebula.browser.store.SettingsManager.ytdlpBinaryUrl
        if (url.isBlank()) {
            toast(context.getString(com.nebula.browser.R.string.ytdlp_no_url))
            return@withContext null
        }
        toast(context.getString(com.nebula.browser.R.string.ytdlp_downloading))
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                toast(context.getString(com.nebula.browser.R.string.ytdlp_download_fail,
                    "HTTP ${conn.responseCode}"))
                return@withContext null
            }
            conn.inputStream.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            }
            if (!target.setExecutable(true, true)) {
                toast(context.getString(com.nebula.browser.R.string.ytdlp_download_fail,
                    "chmod failed"))
                return@withContext null
            }
            toast(context.getString(com.nebula.browser.R.string.ytdlp_download_ok))
            target
        } catch (e: Exception) {
            toast(context.getString(com.nebula.browser.R.string.ytdlp_download_fail,
                e.message ?: e.javaClass.simpleName))
            null
        }
    }

    suspend fun extract(url: String, context: Context): JSONObject? = withContext(Dispatchers.IO) {
        val bin = ensureBinary(context) ?: return@withContext null
        try {
            val pb = ProcessBuilder(bin.absolutePath, "--dump-json", "--no-progress", url)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val out = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code == 0) JSONObject(out) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
