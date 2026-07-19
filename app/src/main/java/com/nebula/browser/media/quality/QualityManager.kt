package com.nebula.browser.media.quality

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.TrackSelectionParameters
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QualityLevel(val label: String, val maxWidth: Int, val maxBitrate: Int) {
    AUTO("自动", Int.MAX_VALUE, Int.MAX_VALUE),
    P240("240P", 426, 300_000),
    P360("360P", 640, 800_000),
    P480("480P", 854, 1_200_000),
    P720("720P", 1280, 2_500_000),
    P1080("1080P", 1920, 5_000_000),
    SOURCE("原画", Int.MAX_VALUE, Int.MAX_VALUE);

    companion object {
        fun byIndex(i: Int): QualityLevel = (entries).getOrElse(i) { AUTO }
    }
}

class QualityManager {
    private val _current = MutableStateFlow(QualityLevel.byIndex(SettingsManager.defaultQuality))
    val current = _current.asStateFlow()

    fun set(level: QualityLevel) {
        _current.value = level
        SettingsManager.defaultQuality = level.ordinal
        QualityBus.broadcast(level)
    }

    fun applyTo(player: ExoPlayer) {
        val params = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(current.value.maxWidth, Int.MAX_VALUE)
            .setMaxVideoBitrate(current.value.maxBitrate)
            .setForceHighestSupportedBitrate(false)
            .build()
        player.trackSelectionParameters = params
    }
}

/** 跨内置播放器和悬浮窗同步画质 */
object QualityBus {
    private val _level = MutableStateFlow(QualityLevel.byIndex(SettingsManager.defaultQuality))
    val level = _level.asStateFlow()

    fun broadcast(level: QualityLevel) { _level.value = level }
}

/** 带宽监听 + 自适应降档 */
object AutoQualityScaler {
    var lastBandwidth: Long = 0
    fun adapt(player: ExoPlayer) {
        val bw = lastBandwidth
        val target = when {
            bw <= 0 || bw > 4_000_000 -> QualityLevel.P1080
            bw > 2_000_000 -> QualityLevel.P720
            bw > 1_000_000 -> QualityLevel.P480
            bw > 500_000 -> QualityLevel.P360
            else -> QualityLevel.P240
        }
        if (QualityBus.level.value == QualityLevel.AUTO) {
            val p = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(target.maxWidth, Int.MAX_VALUE)
                .setMaxVideoBitrate(target.maxBitrate)
                .build()
            player.trackSelectionParameters = p
        }
    }
}
