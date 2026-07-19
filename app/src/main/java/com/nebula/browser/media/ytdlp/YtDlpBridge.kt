package com.nebula.browser.media.ytdlp

import android.content.Context
import com.nebula.browser.common.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
        try {
            if (target.exists() && target.canExecute()) return@withContext target
            context.assets.open("ytdlp/$name").use { input ->
                FileOutputStream(target).use { input.copyTo(it) }
            }
            target.setExecutable(true, true)
            target
        } catch (e: Exception) {
            toast("yt-dlp 二进制缺失，请通过热更新下载")
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
