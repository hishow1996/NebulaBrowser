package com.nebula.browser.browser.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nebula.browser.R
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.FileUtil
import com.nebula.browser.common.toast
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val active = mutableMapOf<Long, Job>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification(0, 0, "下载服务就绪"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "下载文件"
        startDownload(url, title)
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, title: String) {
        scope.launch {
            try {
                val file = File(FileUtil.downloadDir(),
                    title.ifBlank { "nebula_" + System.currentTimeMillis() })
                val entity = DownloadEntity(url = url, title = title, path = file.absolutePath,
                    status = 1, mimeType = guessMime(url))
                val id = AppDatabase.get(this@DownloadService).downloadDao().insert(entity)
                entity.id = id

                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body ?: throw RuntimeException("无响应体")
                    val total = body.contentLength()
                    file.outputStream().use { out ->
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        var acc = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                read = input.read(buf); if (read < 0) break
                                out.write(buf, 0, read); acc += read
                                if (total > 0) {
                                    notifyProgress(id.toInt(), acc, total, title)
                                }
                            }
                        }
                    }
                    entity.downloadedBytes = file.length()
                    entity.totalBytes = file.length()
                    entity.status = 2
                    AppDatabase.get(this@DownloadService).downloadDao().update(entity)
                    notifyComplete(id.toInt(), title)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toast("下载失败：${e.message}")
            }
        }
    }

    private fun guessMime(url: String): String = when {
        url.endsWith(".mp4", true) -> "video/mp4"
        url.endsWith(".epub", true) -> "application/epub+zip"
        else -> "application/octet-stream"
    }

    private fun notifyProgress(id: Int, acc: Long, total: Long, title: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pct = if (total > 0) ((acc * 100) / total).toInt() else 0
        mgr.notify(id, buildNotification(pct, 100, "正在下载：$title"))
    }

    private fun notifyComplete(id: Int, title: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(id, buildNotification(0, 0, "下载完成：$title").apply {
            setContentText("已完成")
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "下载服务", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(progress: Int, max: Int, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("星云浏览器")
            .setContentText(text)
            .setProgress(max, progress, max == 0)
            .setOngoing(progress in 1 until max)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nebula_download"
        fun start(ctx: Context, url: String, title: String = "") {
            val i = Intent(ctx, DownloadService::class.java).apply {
                putExtra("url", url); putExtra("title", title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}

class DownloadListActivity : android.app.ListActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        toast("下载管理界面（演示）")
    }
}
