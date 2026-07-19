package com.nebula.browser.media.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

data class VideoInfo(
    val title: String,
    val url: String,
    val streams: List<VideoStream>,
    val thumbnailUrl: String?
)

data class VideoStream(
    val url: String,
    val mimeType: String,
    val quality: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0
)

class VideoExtractClient {
    private val client = OkHttpClient()

    suspend fun extract(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            // newpipe-extractor 仅支持有限站点（YouTube/B站等）
            val pageUrl = org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
            val sid = NewPipe.getServiceId(url)
            val service = ServiceList.all[sid]
            val handler = service.getStreamLHFactory().fromUrl(url)
            val info = StreamInfo.getInfo(service, handler)
            val streams = info.videoStreams.map {
                VideoStream(it.url, it.mediaFormat?.mimeType ?: "video/mp4",
                    it.getResolution() ?: "480p", it.getWidth(), it.getHeight())
            }
            VideoInfo(info.name, url, streams, info.thumbnailUrl)
        } catch (e: Exception) {
            // fallback：直接当作直链
            try {
                val req = Request.Builder().url(url).head().build()
                val resp = client.newCall(req).execute()
                val mime = resp.header("Content-Type") ?: "video/mp4"
                val size = resp.header("Content-Length")?.toLongOrNull() ?: 0
                VideoInfo("直链视频", url,
                    listOf(VideoStream(url, mime, "原画质", 0, 0, size)), null)
            } catch (_: Exception) { null }
        }
    }
}
