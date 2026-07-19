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
import com.nebula.browser.media.subtitle.SubtitleManager
import com.nebula.browser.media.subtitle.SubtitleTrack
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
    private val subtitleManager = SubtitleManager()
    private var currentSubtitle: SubtitleTrack? = null
    private var currentSpeed: Float = 1.0f

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
        b.btnCaption.setOnClickListener { openCaptionMenu() }
        b.btnSpeed.setOnClickListener { openSpeedMenu() }
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

    /** 倍速菜单：0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 1.75 / 2.0 */
    private fun openSpeedMenu() {
        val speeds = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        // 已选索引（最接近即可）
        val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }.let { if (it < 0) 2 else it }
        val labels = speeds.mapIndexed { i, s ->
            val mark = if (i == currentIdx) "✓ " else ""
            String.format("%s%.2fx", mark, s)
        }.toTypedArray<CharSequence>()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.video_speed))
            .setSingleChoiceItems(labels, currentIdx) { d, which ->
                val s = speeds[which]
                currentSpeed = s
                applySpeed(s)
                toast(getString(R.string.speed_current, s))
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySpeed(speed: Float) {
        try {
            val params = androidx.media3.common.PlaybackParameters(speed)
            player.playbackParameters = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 字幕菜单：
     * 1) 输入在线字幕 URL（srt/vtt）→ 加载并叠加在画面底部；
     * 2) 关闭当前字幕；
     * 3) （可选）ImportUserSrt 暂未实现，留位。
     */
    private fun openCaptionMenu() {
        val items = arrayOf<CharSequence>(
            getString(R.string.caption_input_url),
            getString(R.string.caption_disable)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.video_caption))
            .setItems(items) { d, which ->
                when (which) {
                    0 -> showSubtitleUrlDialog()
                    1 -> disableSubtitle()
                }
                d.dismiss()
            }
            .show()
    }

    private fun showSubtitleUrlDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "https://example.com/subtitle.vtt"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(56, 24, 56, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.caption_input_url))
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) loadOnlineSubtitle(url)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadOnlineSubtitle(url: String) {
        lifecycleScope.launch {
            val track = subtitleManager.loadOnline(url)
            if (track == null || track.cues.isEmpty()) {
                toast(getString(R.string.caption_load_fail))
                return@launch
            }
            currentSubtitle = track
            if (com.nebula.browser.store.SettingsManager.autoTranslate) {
                subtitleManager.translateIfNeeded(track,
                    com.nebula.browser.store.SettingsManager.tgtLang)
            }
            toast(getString(R.string.caption_load_ok, track.cues.size))
        }
    }

    private fun disableSubtitle() {
        currentSubtitle = null
        b.subtitleView.setCues(emptyList())
        toast(getString(R.string.caption_disable))
    }

    /** 根据当前播放进度广播字幕 cue（含译文）到 SubtitleView。 */
    private fun flushSubtitle() {
        val track = currentSubtitle ?: return
        val pos = player.currentPosition
        val cue = subtitleManager.forTime(track, pos) ?: run {
            b.subtitleView.setCues(emptyList())
            return
        }
        val text = buildString {
            append(cue.text)
            if (!cue.translation.isNullOrBlank()) {
                append("\n")
                append(cue.translation)
            }
        }
        // 仅设置文本：Cue.Builder 的 Builder 在 media3 1.2.x 中对位置 API 有变化，
        // 这里只取最稳定可用的 setText 调用；SubtitleView 会使用默认底部居中布局。
        val cueObj = androidx.media3.common.text.Cue.Builder()
            .setText(text)
            .setTextAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .build()
        b.subtitleView.setCues(listOf(cueObj))
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
                flushSubtitle()
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
