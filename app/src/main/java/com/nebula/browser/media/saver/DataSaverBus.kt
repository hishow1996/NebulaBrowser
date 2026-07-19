package com.nebula.browser.media.saver

import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 数据节省（网页视频画质压缩）全局总线：
 *  - 开关状态 / 累计节省字节数 / 累计原始字节数
 *  - 由 VideoProxyServer 上报每个会话的源站 vs 实际下行字节
 *  - UI 订阅以实时显示「已节省 XX%」
 */
object DataSaverBus {

    private val _enabled = MutableStateFlow(SettingsManager.saveMode)
    val enabled = _enabled.asStateFlow()

    private val _savedBytes = MutableStateFlow(0L)
    val savedBytes = _savedBytes.asStateFlow()

    private val _originBytes = MutableStateFlow(0L)
    val originBytes = _originBytes.asStateFlow()

    fun setEnabled(b: Boolean) {
        SettingsManager.saveMode = b
        _enabled.value = b
        if (!b) resetStats()
    }

    fun report(originBytes: Long, deliveredBytes: Long) {
        if (originBytes <= 0) return
        _originBytes.value += originBytes
        _savedBytes.value += (originBytes - deliveredBytes).coerceAtLeast(0)
    }

    fun savedPercent(): Int {
        val o = _originBytes.value
        if (o <= 0) return 0
        return ((_savedBytes.value.toDouble() / o) * 100).toInt().coerceIn(0, 99)
    }

    fun resetStats() {
        _savedBytes.value = 0
        _originBytes.value = 0
    }
}
