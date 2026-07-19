package com.nebula.browser.media.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * 单例 ExoPlayer 持有器，方便悬浮窗、内置播放器、AI 解释模式间共享。
 */
object ExoPlayerHolder {
    @Volatile private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer =
        player ?: synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { player = it }
        }

    fun release() {
        player?.release(); player = null
    }

    fun playUrl(context: Context, url: String, headers: Map<String, String> = emptyMap(), title: String = "") {
        val media = MediaItem.Builder().setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()
            )
            .apply {
                if (headers.isNotEmpty()) {
                    val ext = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder
                    // HM headers via Bundle ext：仅 demo 占位
                }
            }
            .build()
        get(context).apply {
            setMediaItem(media); prepare(); playWhenReady = true
        }
    }
}
