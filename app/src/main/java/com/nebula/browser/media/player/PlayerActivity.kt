package com.nebula.browser.media.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.ActivityPlayerBinding
import com.nebula.browser.media.quality.QualityBus
import com.nebula.browser.media.quality.QualityLevel
import com.nebula.browser.media.quality.QualityManager
import com.nebula.browser.media.quality.QualityMenuSheet
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.launch

/**
 * 内置视频播放器。
 *
 * - 接收 intent: url, title
 * - 共享 ExoPlayerHolder 实例（与悬浮窗一致）
 * - 顶部胶囊画质按钮：弹出 QualityMenuSheet，选档后即时下发到 ExoPlayer；
 * - 滑动条、播放暂停、前后 10 秒、字幕开关、全屏、速度（占位）；
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var qualityMenu: QualityMenuSheet? = null

    private val player get() = ExoPlayerHolder.get(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式横屏体验
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        val url = intent.getStringExtra("video_url") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: ""

        b.txtTitle.text = title
        b.playerView.player = player
        b.playerView.useController = false

        // 画质 chip 文案与 dot 颜色按当前档位同步
        syncQualityChip(QualityBus.level.value)
        lifecycleScope.launch {
            QualityBus.level.collect { syncQualityChip(it) }
        }

        b.btnBack.setOnClickListener { finish() }
        b.btnPlayPause.setOnClickListener {
            if (player.isPlaying) { player.pause(); b.btnPlayPause.setImageResource(R.drawable.ic_play) }
            else { player.play(); b.btnPlayPause.setImageResource(R.drawable.ic_pause) }
        }
        b.btnReplay.setOnClickListener { player.seekBack() }
        b.btnForward.setOnClickListener { player.seekForward() }
        b.btnCaption.setOnClickListener { toast(getString(R.string.video_caption)) }
        b.btnSpeed.setOnClickListener { toast(getString(R.string.video_speed)) }
        b.btnFullscreen.setOnClickListener {
            val cur = window.attributes
            cur.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = cur
        }

        // 画质 chip 点击 → 弹出选择面板
        b.btnQuality.setOnClickListener {
            qualityMenu?.dismiss()
            qualityMenu = QualityMenuSheet(this, QualityBus.level.value) { level ->
                QualityManager().apply {
                    set(level)
                    applyTo(player)
                }
            }.also { it.show() }
        }

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = player.duration
                    if (d > 0) player.seekTo(p.toLong() * d / 100)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        ExoPlayerHolder.playUrl(this, url, title = title)
        // 应用默认画质
        QualityManager().applyTo(player)
        startProgressLoop()
    }

    private fun syncQualityChip(level: QualityLevel) {
        b.txtQuality.text = level.label
        val color = when (level) {
            QualityLevel.AUTO -> 0xFF94A3B8.toInt()
            QualityLevel.P240 -> 0xFF10B981.toInt()
            QualityLevel.P360 -> 0xFF60A5FA.toInt()
            QualityLevel.P480 -> 0xFFF59E0B.toInt()
            QualityLevel.P720 -> 0xFF8B5CF6.toInt()
            QualityLevel.P1080 -> 0xFFEF4444.toInt()
            QualityLevel.SOURCE -> 0xFFFFFFFF.toInt()
        }
        val dot = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        b.qualityDot.background = dot
    }

    private val progressLoop = object : Runnable {
        override fun run() {
            try {
                val d = player.duration.takeIf { it > 0 } ?: 0
                if (d > 0) {
                    b.seekBar.progress = (player.currentPosition * 100 / d).toInt()
                    b.txtCurrent.text = formatTime(player.currentPosition)
                    b.txtDuration.text = formatTime(d)
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 500)
        }
    }

    private fun startProgressLoop() {
        handler.post(progressLoop)
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val s = ms / 1000
        val m = s / 60
        val ss = s % 60
        return if (m >= 60) {
            val h = m / 60; val mm = m % 60
            String.format("%d:%02d:%02d", h, mm, ss)
        } else String.format("%02d:%02d", m, ss)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressLoop)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
