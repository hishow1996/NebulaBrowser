package com.nebula.browser.media.saver

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.SettingsManager

/**
 * 网络带宽采样：基于 uid 移动流量 / WebView 字节流统计。
 *  弱网（< 300 kbps 持续 2 秒）自动建议开启数据节省；
 *  旺网（> 1.5 Mbps 持续 4 秒）建议关闭。
 */
object BandwidthMeter {

    private val handler = Handler(Looper.getMainLooper())
    private var lastUidRx: Long = 0
    private var lastTimeMs: Long = 0
    private var lowStreak = 0
    private var highStreak = 0
    private var running = false

    /** 当前估算的下行速度，单位 bps。 */
    @Volatile var currentBps: Long = 0
        private set

    @Volatile var suggestion: Suggestion = Suggestion.IDLE
        private set

    enum class Suggestion { IDLE, FORCE_LITE, MAYBE_LITE, OK }

    fun start() {
        if (running) return
        running = true
        lastUidRx = uidRxBytes()
        lastTimeMs = System.currentTimeMillis()
        handler.postDelayed(tick, 1000)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val cur = uidRxBytes()
            val now = System.currentTimeMillis()
            val dBytes = (cur - lastUidRx).coerceAtLeast(0)
            val dSec = ((now - lastTimeMs).coerceAtLeast(1)) / 1000.0
            currentBps = (dBytes * 8 / dSec).toLong()
            lastUidRx = cur
            lastTimeMs = now

            when {
                currentBps in 1..300_000 -> { lowStreak++; highStreak = 0 }
                currentBps in 300_001..1_500_000 -> { lowStreak = 0; highStreak = 0 }
                currentBps > 1_500_000 -> { highStreak++; lowStreak = 0 }
                else -> { lowStreak = 0; highStreak = 0 }
            }
            suggestion = when {
                lowStreak >= 2 -> Suggestion.FORCE_LITE
                lowStreak == 1 -> Suggestion.MAYBE_LITE
                highStreak >= 4 -> Suggestion.OK
                else -> suggestion
            }
            autoApply()
            handler.postDelayed(this, 1000)
        }
    }

    private fun autoApply() {
        if (!SettingsManager.saverAuto) return
        when (suggestion) {
            Suggestion.FORCE_LITE -> if (!DataSaverBus.enabled.value) DataSaverBus.setEnabled(true)
            Suggestion.OK -> if (DataSaverBus.enabled.value) DataSaverBus.setEnabled(false)
            else -> {}
        }
    }

    private fun uidRxBytes(): Long = try {
        TrafficStats.getUidRxBytes(android.os.Process.myUid())
            .coerceAtLeast(0).toLong()
    } catch (_: Exception) { 0L }
}
