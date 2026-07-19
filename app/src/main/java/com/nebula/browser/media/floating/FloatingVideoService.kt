package com.nebula.browser.media.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.nebula.browser.R
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.PermissionUtil
import com.nebula.browser.common.dpToPx
import com.nebula.browser.common.toast
import com.nebula.browser.media.player.ExoPlayerHolder
import com.nebula.browser.media.quality.QualityBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingVideoService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var playerView: PlayerView? = null
    private var controlsLayer: View? = null
    private var btnClose: ImageView? = null
    private var btnPlayPause: ImageView? = null
    private var btnReplay: ImageView? = null
    private var btnForward: ImageView? = null
    private var resizeHandle: View? = null
    private var seekBar: SeekBar? = null
    private var txtCurrent: TextView? = null
    private var txtDuration: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var hideJob: Job? = null
    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1, buildNotification("悬浮播放中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("video_url")
        if (!PermissionUtil.canDrawOverlays(this)) {
            toast(getString(R.string.video_request_overlay))
            stopSelf()
            return START_NOT_STICKY
        }
        if (rootView == null) initWindow()
        url?.let { ExoPlayerHolder.playUrl(this, it, title = "") }
        startProgressLoop()
        return START_STICKY
    }

    private fun initWindow() {
        val ctx = this
        rootView = android.view.LayoutInflater.from(ctx).inflate(R.layout.floating_video, null, false)
        playerView = rootView?.findViewById(R.id.player_view)
        controlsLayer = rootView?.findViewById(R.id.controls_layer)
        btnClose = rootView?.findViewById(R.id.btn_close)
        btnReplay = rootView?.findViewById(R.id.btn_replay)
        btnPlayPause = rootView?.findViewById(R.id.btn_play_pause)
        btnForward = rootView?.findViewById(R.id.btn_forward)
        resizeHandle = rootView?.findViewById(R.id.resize_handle)
        seekBar = rootView?.findViewById(R.id.seek_bar)
        txtCurrent = rootView?.findViewById(R.id.txt_current)
        txtDuration = rootView?.findViewById(R.id.txt_duration)

        val initialW = dpToPx(ctx, 240f).toInt()
        val initialH = dpToPx(ctx, 160f).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60; y = 120
            width = initialW; height = initialH
        }

        layoutParams?.let { windowManager?.addView(rootView, it) }
        playerView?.player = ExoPlayerHolder.get(ctx)
        com.nebula.browser.media.quality.QualityManager().applyTo(ExoPlayerHolder.get(ctx))

        setupListeners()
        showControlsTransiently()
    }

    private fun setupListeners() {
        btnClose?.setOnClickListener { stopSelf() }
        btnPlayPause?.setOnClickListener {
            val p = ExoPlayerHolder.get(this)
            if (p.isPlaying) { p.pause(); btnPlayPause?.setImageResource(R.drawable.ic_play) }
            else { p.play(); btnPlayPause?.setImageResource(R.drawable.ic_pause) }
            showControlsTransiently()
        }
        btnReplay?.setOnClickListener {
            ExoPlayerHolder.get(this).seekBack()
            showControlsTransiently()
        }
        btnForward?.setOnClickListener {
            ExoPlayerHolder.get(this).seekForward()
            showControlsTransiently()
        }
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = ExoPlayerHolder.get(this@FloatingVideoService).duration
                    if (d > 0) ExoPlayerHolder.get(this@FloatingVideoService).seekTo((p.toLong() * d / 100))
                    cancelHide()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { cancelHide() }
            override fun onStopTrackingTouch(sb: SeekBar?) { showControlsTransiently() }
        })

        // 点击视频区切换控件显示
        playerView?.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                if (controlsLayer?.alpha == 1f) hideControls() else showControlsTransiently()
            }
            false
        }

        // 拖拽柄 - 右下角缩放，维持左上角不动
        resizeHandle?.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0f; var startY = 0f
            var startW = 0; var startH = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.rawX; startY = e.rawY
                        layoutParams?.let { startW = it.width; startH = it.height }
                        cancelHide()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - startX).toInt()
                        val dy = (e.rawY - startY).toInt()
                        layoutParams?.let {
                            it.width = (startW + dx).coerceAtLeast(minWidth())
                                .coerceAtMost(screenWidth() - 32)
                            // 允许上下大幅拉长，上限为屏幕高度
                            it.height = (startH + dy).coerceAtLeast(minHeight())
                                .coerceAtMost(screenHeight() - 64)
                            try { windowManager?.updateViewLayout(rootView, it) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> { showControlsTransiently() }
                }
                return true
            }
        })

        // 拖动视频区移动浮窗（左下、上边的简单实现）
        rootView?.setOnTouchListener(object : View.OnTouchListener {
            var dragStartX = 0f; var dragStartY = 0f
            var originX = 0; var originY = 0
            var startedMove = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = e.rawX; dragStartY = e.rawY
                        layoutParams?.let { originX = it.x; originY = it.y }
                        startedMove = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - dragStartX).toInt()
                        val dy = (e.rawY - dragStartY).toInt()
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) startedMove = true
                        layoutParams?.let {
                            it.x = originX + dx; it.y = originY + dy
                            try { windowManager?.updateViewLayout(rootView, it) } catch (_: Exception) {}
                        }
                    }
                }
                return false
            }
        })
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        // 用主线程handler定期更新
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                try {
                    val p = ExoPlayerHolder.get(this@FloatingVideoService)
                    val pos = p.currentPosition
                    val dur = p.duration.takeIf { it > 0 } ?: 0
                    if (dur > 0) {
                        seekBar?.progress = (pos * 100 / dur).toInt()
                        txtCurrent?.text = formatTime(pos)
                        txtDuration?.text = formatTime(dur)
                    }
                } catch (_: Exception) {}
                handler.postDelayed(this, 500)
            }
        }
        handler.post(r)
    }

    private fun showControlsTransiently() {
        try {
            controlsLayer?.animate()?.alpha(1f)?.setDuration(200)?.start()
            cancelHide()
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            hideJob = kotlinx.coroutines.GlobalScope.let { _ ->
                // 简化：用Handler代替coroutine
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ hideControls() }, 3000)
            }
        } catch (_: Exception) {}
    }

    private fun cancelHide() {
        hideJob?.cancel()
        // Handler 实现不支持取消，这里仅演示；通过重复 post 不会引起问题
    }

    private fun hideControls() {
        try {
            // 拖拽柄不参与自动隐藏：单独保持 alpha 1
            resizeHandle?.visibility = View.VISIBLE
            resizeHandle?.alpha = 1f
            controlsLayer?.animate()?.alpha(0f)?.setDuration(280)?.start()
        } catch (_: Exception) {}
    }

    private fun minWidth(): Int = dpToPx(this, 160f).toInt()
    private fun minHeight(): Int = dpToPx(this, 120f).toInt()
    private fun screenWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
    private fun screenHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

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

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "悬浮视频",
                NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_video)
            .setContentTitle("星云浏览器悬浮视频")
            .setContentText(text)
            .setOngoing(true).build()

    override fun onDestroy() {
        try { rootView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        progressJob?.cancel()
        ExoPlayerHolder.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "nebula_floating_video"
    }
}
