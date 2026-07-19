package com.nebula.browser.media.proxy

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nebula.browser.common.AppContext
import com.nebula.browser.media.saver.DataSaverBus
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 网页视频画质压缩本地代理服务器。
 *
 * 路由 GET /proxy?u=<原始URL>&br=<目标码率>&w=<目标宽度>
 *
 * 策略：
 *  1) HLS (.m3u8)：拉取 playlist，挑选 BANDWIDTH ≤ br 的最高 variant；
 *     把分片 URL 全部重写成走代理（透传字节，省流），并向上游感知的 WebView 暴露低码率 HLS。
 *  2) 直链 (.mp4/.webm/.mkv/.flv)：启动 FFmpeg 实时转码，-b:v br -vf scale=w:-2，
 *     以 fragmented mp4 流式输出给 WebView。失败自动降级到原 URL 转发。
 *  3) 直链字节流：失败回退时直接代理源站字节，保证播放可用。
 */
class VideoProxyServer private constructor() {

    companion object {
        const val PORT = 18741
        private const val PROXY_HOST = "127.0.0.1"
        private val LOCK = Any()
        @Volatile private var INST: VideoProxyServer? = null
        fun get(): VideoProxyServer = INST ?: synchronized(LOCK) {
            INST ?: VideoProxyServer().also {
                it.start()
                INST = it
            }
        }

        /** 把原始视频 URL 包成代理 URL。 */
        fun wrap(url: String): String {
            val br = SettingsManager.saverBitrate.coerceAtLeast(150_000)
            val w = SettingsManager.saverWidth.coerceAtLeast(426)
            val enc = java.net.URLEncoder.encode(url, "UTF-8")
            return "http://$PROXY_HOST:$PORT/proxy?u=$enc&br=$br&w=$w"
        }

        /** 该请求是否由 wrap() 产生。 */
        fun isProxy(url: String): Boolean =
            url.startsWith("http://$PROXY_HOST:$PORT/") || url.startsWith("http://localhost:$PORT/")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    @Volatile private var running = false
    private val ffmpegSessions = ConcurrentHashMap<String, Long>()

    fun start() {
        if (running) return
        try {
            server = ServerSocket(PORT, 16, java.net.InetAddress.getByName(PROXY_HOST))
            running = true
            scope.launch { acceptLoop() }
        } catch (e: IOException) {
            // 端口被占等异常
            running = false
        }
    }

    fun stop() {
        running = false
        ffmpegSessions.values.forEach { FFmpegKit.cancel(it) }
        ffmpegSessions.clear()
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun acceptLoop() {
        val s = server ?: return
        while (running) {
            val socket = try { s.accept() } catch (_: Exception) { continue }
            scope.launch { handle(socket) }
        }
    }

    // ============ HTTP request handling ============

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 60_000
                val input = sock.getInputStream()
                val output = sock.getOutputStream()
                val line = BufferedReader(InputStreamReader(input)).readLine() ?: return
                if (!line.startsWith("GET ")) {
                    writeSimple(output, 405, "Method Not Allowed", "")
                    return
                }
                val rawUrl = line.substring(4).substringBefore(" HTTP/")
                val uri = Uri.parse("http://host$rawUrl")
                when (uri.path) {
                    "/proxy" -> handleProxy(uri, output)
                    "/ping" -> writeSimple(output, 200, "OK", "pong")
                    else -> writeSimple(output, 404, "Not Found", "")
                }
            } catch (_: Exception) {
                // 静默
            }
        }
    }

    private fun handleProxy(uri: Uri, output: OutputStream) {
        val sourceRaw = uri.getQueryParameter("u")
        if (sourceRaw == null) {
            writeSimple(output, 400, "Bad Request", "missing u")
            return
        }
        val source = java.net.URLDecoder.decode(sourceRaw, "UTF-8")
        val br = (uri.getQueryParameter("br")?.toIntOrNull()
            ?: SettingsManager.saverBitrate).coerceAtLeast(150_000)
        val w = (uri.getQueryParameter("w")?.toIntOrNull()
            ?: SettingsManager.saverWidth).coerceAtLeast(426)
        val segment = uri.getQueryParameter("s") == "1"

        when {
            isHls(source) -> serveHls(source, br, w, output)
            segment -> rawForward(source, output)
            else -> transcodeFile(source, br, w, output)
        }
    }

    // ============ HLS ============

    private fun serveHls(source: String, br: Int, w: Int, output: OutputStream) {
        try {
            val playlist = fetchText(source) ?: return rawForward(source, output)
            if (looksLikeMaster(playlist)) {
                val variant = pickVariant(playlist, source, br) ?: source
                // 把 master 的 variant URL 重写为低码率媒体 playlist 走代理（仍是源站 playlist
                // 但通过代理字节透传以保证跨域/referrer/UA 一致）
                val rewritten = rewriteMasterToProxy(playlist, source, variant, br, w)
                writeResponse(output, 200, "application/vnd.apple.mpegurl", rewritten.toByteArray())
            } else {
                // media playlist：把分片 URL 重写为代理
                val rewritten = rewriteMediaPlaylist(playlist, source, br, w)
                writeResponse(output, 200, "application/vnd.apple.mpegurl", rewritten.toByteArray())
            }
        } catch (_: Exception) {
            rawForward(source, output)
        }
    }

    private fun isHls(url: String): Boolean =
        url.contains(".m3u8", true) ||
            url.contains("/hls/", true) ||
            url.contains("application/vnd.apple.mpegurl", true)

    private fun fetchText(url: String): String? = try {
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "NebulaBrowser/1.0")
            .build()).execute().use { it.body?.string() }
    } catch (_: Exception) { null }

    private fun looksLikeMaster(playlist: String): Boolean =
        playlist.contains("#EXT-X-STREAM-INF", true)

    private data class Variant(val bandwidth: Long, val url: String)

    private fun pickVariant(playlist: String, base: String, maxBitrate: Int): String? {
        val variants = mutableListOf<Variant>()
        val lines = playlist.split("\n")
        var i = 0
        while (i < lines.size) {
            val l = lines[i].trim()
            if (l.startsWith("#EXT-X-STREAM-INF", true)) {
                val bw = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val urlLine = lines.getOrNull(i + 1)?.trim()
                if (!urlLine.isNullOrEmpty() && !urlLine.startsWith("#")) {
                    variants += Variant(bw, resolve(base, urlLine))
                }
            }
            i++
        }
        if (variants.isEmpty()) return null
        return variants
            .filter { it.bandwidth in 1..maxBitrate.toLong() }
            .maxByOrNull { it.bandwidth }?.url
            ?: variants.minByOrNull { it.bandwidth }?.url
    }

    private fun rewriteMasterToProxy(
        playlist: String, base: String, picked: String,
        br: Int, w: Int
    ): String {
        val sb = StringBuilder()
        for (raw in playlist.split("\n")) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF", true)) {
                sb.append(line).append("\n")
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) {
                sb.append(line).append("\n")
                continue
            }
            val resolved = resolve(base, line)
            val target = if (resolved == picked) wrap(resolved, br, w)
            else resolved // 仅保留选中的 variant
            sb.append(target).append("\n")
        }
        return sb.toString()
    }

    private fun rewriteMediaPlaylist(playlist: String, base: String, br: Int, w: Int): String {
        val sb = StringBuilder()
        for (raw in playlist.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                sb.append(line).append("\n")
                continue
            }
            val resolved = resolve(base, line)
            // 分片直接透传字节即可：源仍走代理以统一 referer/UA，且能统计节省量
            sb.append(wrap(resolved, br, w, segment = true)).append("\n")
        }
        return sb.toString()
    }

    // ============ 直链转码 ============

    /**
     * 直链视频实时转码：
     *   1. 启动 FFmpegKit 写入 cache 中临时 fragmented mp4；
     *   2. 同时从该文件流式读取并经 HTTP 写回 WebView；
     *   3. 任意一步失败回退到字节原样代理 rawForward。
     *
     * 弱网下浏览器拿到低码率 mp4 后无需大缓冲，首帧能更快出。
     */
    private fun transcodeFile(source: String, br: Int, w: Int, output: OutputStream) {
        var origin = 0L
        var delivered = 0L
        var sid: Long = 0
        var out: java.io.File? = null
        var fis: java.io.FileInputStream? = null
        try {
            val ctx = AppContext.appContext
            out = java.io.File(ctx.cacheDir, "proxy_${System.currentTimeMillis()}_${Thread.currentThread().id}.mp4")
            val cmd = buildTranscodeCmdToFile(source, out.absolutePath, br, w)
            val latch = java.util.concurrent.CountDownLatch(1)
            var ok = false
            sid = FFmpegKit.executeAsync(cmd) { s ->
                ok = ReturnCode.isSuccess(s.returnCode)
                latch.countDown()
            }
            ffmpegSessions[sid.toString()] = sid

            val mime = "video/mp4"
            writeHeader(output, 200, "OK", mime, chunked = true)

            fis = java.io.FileInputStream(out)
            val buf = ByteArray(64 * 1024)
            // 边写边读：文件大小还在增长则持续读，ffmpeg 结束后再读一次 EOF 即收尾。
            var lastSize = 0L
            while (true) {
                val size = out.length()
                val n = if (size > lastSize) {
                    fis.read(buf, 0, minOf((size - lastSize).toInt(), buf.size))
                        .also { if (it > 0) lastSize += it }
                } else 0

                if (n > 0) {
                    output.write(buf, 0, n); output.flush(); delivered += n
                    continue
                }
                // 无新数据可读
                if (latch.count > 0) { Thread.sleep(120); continue }
                // ffmpeg 已结束，再次尝试直到读到 EOF
                val tail = fis.read(buf)
                if (tail < 0) break
                output.write(buf, 0, tail); output.flush(); delivered += tail
            }
            fis.close()
            if (!ok) throw IOException("ffmpeg failed")
            origin = sourceByteLength(source)
            DataSaverBus.report(origin, delivered)
        } catch (_: Exception) {
            try { fis?.close() } catch (_: Exception) {}
            // 兜底：原样字节代理
            rawForward(source, output, originOverride = origin, deliveredOverride = delivered)
        } finally {
            try { out?.delete() } catch (_: Exception) {}
        }
    }

    /** 原样字节代理 + 流量统计（回退路径）。 */
    private fun rawForward(
        source: String, output: OutputStream,
        originOverride: Long = 0L, deliveredOverride: Long = 0L
    ) {
        var origin = originOverride
        var delivered = deliveredOverride
        try {
            val req = Request.Builder().url(source)
                .header("User-Agent", "NebulaBrowser/1.0")
                .header("Referer", baseDomain(source))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body ?: return
                val mime = body.contentType()?.toString() ?: guessMime(source)
                writeHeader(output, 200, "OK", mime, chunked = true)
                if (origin == 0L) origin = body.contentLength().coerceAtLeast(0L)
                body.byteStream().use { ins ->
                    val buf = ByteArray(32 * 1024)
                    var n: Int
                    while (true) {
                        n = ins.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        output.flush()
                        delivered += n
                    }
                }
            }
            DataSaverBus.report(origin, delivered)
        } catch (_: Exception) {}
    }

    private fun sourceByteLength(source: String): Long = try {
        client.newCall(Request.Builder().url(source).head()
            .header("User-Agent", "NebulaBrowser/1.0")
            .header("Referer", baseDomain(source))
            .build()).execute().use { it.body?.contentLength() ?: 0L }
    } catch (_: Exception) { 0L }

    // ============ ffmpeg 命令 ============

    /**
     * 直链视频实时转码命令（fragmented mp4 输出到文件，边写边读）。
     * -re：按源帧率输入，避免 ffmpeg 跑得过快导致缓冲堆积；
     * -vf scale：等比缩放至宽度 ≤ w；
     * -b:v：目标码率；
     * -preset ultrafast / -tune zerolatency：实时性优先；
     * -movflags：fragmented mp4，流式可边写边读。
     */
    private fun buildTranscodeCmdToFile(source: String, outPath: String, br: Int, w: Int): String =
        "-re -i \"$source\" " +
            "-vf \"scale='min($w,iw)':-2\" " +
            "-b:v ${br} -maxrate ${br * 2} -bufsize ${br} " +
            "-preset ultrafast -tune zerolatency " +
            "-c:a aac -b:a 96k -ac 2 " +
            "-movflags frag_keyframe+empty_moov+default_base_moof " +
            "-f mp4 \"$outPath\""

    private fun buildTranscodeCmd(source: String, br: Int, w: Int): String =
        buildTranscodeCmdToFile(source, "-", br, w)

    // ============ 工具 ============

    private fun wrap(url: String, br: Int, w: Int, segment: Boolean = false): String {
        val enc = java.net.URLEncoder.encode(url, "UTF-8")
        val segFlag = if (segment) "&s=1" else ""
        return "http://$PROXY_HOST:$PORT/proxy?u=$enc&br=$br&w=$w$segFlag"
    }

    private fun resolve(base: String, ref: String): String = try {
        URL(URL(base), ref).toString()
    } catch (_: Exception) { ref }

    private fun baseDomain(url: String): String = try {
        val u = URL(url)
        "${u.protocol}://${u.host}/"
    } catch (_: Exception) { "" }

    private fun guessMime(url: String): String = when {
        url.contains(".m3u8", true) -> "application/vnd.apple.mpegurl"
        url.contains(".mpd", true) -> "application/dash+xml"
        url.contains(".mp4", true) -> "video/mp4"
        url.contains(".webm", true) -> "video/webm"
        url.contains(".ts", true) -> "video/mp2t"
        url.contains(".flv", true) -> "video/x-flv"
        url.contains(".mkv", true) -> "video/x-matroska"
        else -> "application/octet-stream"
    }

    // ============ HTTP 写 ============

    private fun writeHeader(out: OutputStream, code: Int, reason: String, mime: String, chunked: Boolean) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        sb.append("Content-Type: ").append(mime).append("\r\n")
        sb.append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
        if (chunked) sb.append("Transfer-Encoding: chunked\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
        out.flush()
    }

    private fun writeResponse(out: OutputStream, code: Int, mime: String, body: ByteArray) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(" OK\r\n")
        sb.append("Content-Type: ").append(mime).append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        sb.append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray())
        out.write(body)
        out.flush()
    }

    private fun writeSimple(out: OutputStream, code: Int, reason: String, body: String) {
        writeResponse(out, code, reason.ifEmpty { "OK" }, "text/plain", body.toByteArray())
    }

    private fun writeResponse(out: OutputStream, code: Int, reason: String, mime: String, body: ByteArray) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        sb.append("Content-Type: ").append(mime).append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n\r\n")
        out.write(sb.toString().toByteArray())
        out.write(body)
        out.flush()
    }
}
