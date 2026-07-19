package com.nebula.browser.media.quality

import com.nebula.browser.store.SettingsManager

/**
 * 网页在线视频画质档位（用户在网页里手动选择的清晰度）。
 *
 * - AUTO：不开代理，原样让 WebView 走源站；
 * - LEVEL_*：通过 VideoProxyServer 走转码/重选 BANDWIDTH，
 *           br/w 由对应档位决定，让弱网更顺畅；
 * - SOURCE：走原 URL（不走代理）。
 */
enum class WebVideoQuality(
    val label: String,
    val br: Int,        // 目标码率，0 表示不走代理
    val width: Int      // 目标宽度
) {
    AUTO("自动", 0, 0),
    P240("240P 流畅省流", 300_000, 426),
    P360("360P 标清", 600_000, 640),
    P480("480P 高清", 1_200_000, 854),
    P720("720P 超清", 2_500_000, 1280),
    SOURCE("原画", Int.MAX_VALUE, 0);

    fun shouldProxy(): Boolean = this != AUTO && this != SOURCE
    fun applyToPrefs() { SettingsManager.webVideoQuality = ordinal }

    companion object {
        fun current(): WebVideoQuality =
            entries.getOrElse(SettingsManager.webVideoQuality) { AUTO }

        /** 同时驱动数据节省总开关：选非自动档位 → 默认开启 DataSaverBus。 */
        fun choose(level: WebVideoQuality) {
            SettingsManager.webVideoQuality = level.ordinal
            val bus = com.nebula.browser.media.saver.DataSaverBus
            if (level.shouldProxy()) {
                if (!bus.enabled.value) bus.setEnabled(true)
                SettingsManager.saverBitrate = level.br
                SettingsManager.saverWidth = level.width
            }
        }
    }
}
