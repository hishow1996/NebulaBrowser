package com.nebula.browser.media.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture

/** 媒体会话服务，配合通知栏进度/暂停 */
class MediaSessionService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayerHolder.get(this)
        val activity = Intent(this, com.nebula.browser.MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, activity,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(pi)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p?.playWhenReady == false || p?.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** 内置全屏播放器 Activity */
class PlayerActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("NebulaPlayer", "PlayerActivity created (demo)")
        finish()
    }
}
