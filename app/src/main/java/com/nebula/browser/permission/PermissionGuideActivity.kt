package com.nebula.browser.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.nebula.browser.R
import com.nebula.browser.common.PermissionUtil
import com.nebula.browser.databinding.ActivityPermissionGuideBinding

/**
 * 统一的权限引导页：
 *  - 接收 Intent.extra：title / desc / icon (drawable id) / type (overlay|install|storage|notification|camera|mic)
 *  - 显示 hero 图标 + 标题 + 描述 + 当前状态徽章
 *  - 「去授权」按钮跳到对应系统设置或运行时申请
 *  - 返回后刷新状态：已授权则把 RESULT_OK 返回给调用方
 *
 * 调用：startActivityForResult(Intent(ctx, PermissionGuideActivity::class.java).apply{
 *     putExtra("type","overlay"); putExtra("title",...); putExtra("desc",...)
 * }, code)
 * onResume 时根据 type 判定是否已授权，已授权直接关闭并返回 RESULT_OK。
 */
class PermissionGuideActivity : AppCompatActivity() {

    private lateinit var b: ActivityPermissionGuideBinding
    private lateinit var settingLauncher: ActivityResultLauncher<Intent>
    private lateinit var runtimeLauncher: ActivityResultLauncher<String>
    private var type: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPermissionGuideBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.title = getString(R.string.permission_required)

        settingLauncher = registerForActivityResult(StartActivityForResult()) {
            refreshStatus(); maybeFinishIfGranted()
        }
        runtimeLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            refreshStatus(); maybeFinishIfGranted()
        }

        type = intent.getStringExtra("type") ?: "overlay"
        b.toolbar.title = intent.getStringExtra("actionBarTitle") ?: getString(R.string.permission_required)
        b.tvTitle.text = intent.getStringExtra("title") ?: titleOf(type)
        b.tvDesc.text = intent.getStringExtra("desc") ?: descOf(type)
        val iconRes = intent.getIntExtra("icon", R.drawable.ic_lock)
        b.ivHero.setImageResource(iconRes)

        b.btnAction.text = intent.getStringExtra("btnText") ?: getString(R.string.grant)
        b.btnAction.setOnClickListener { launchGrant() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        maybeFinishIfGranted()
    }

    private fun launchGrant() {
        when (type) {
            "overlay" -> startActivitySafely(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), settingLauncher
            )
            "install" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivitySafely(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")), settingLauncher)
            } else runtimeLaunch()
            "storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivitySafely(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")), settingLauncher)
            } else runtimeLaunch()
            "notification" -> runtimeLaunch()
            "camera", "mic" -> runtimeLaunch()
            else -> runtimeLaunch()
        }
    }

    private fun runtimeLaunch() {
        val perm = when (type) {
            "notification" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.POST_NOTIFICATIONS else null
            "camera" -> android.Manifest.permission.CAMERA
            "mic" -> android.Manifest.permission.RECORD_AUDIO
            "storage" -> android.Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
        if (perm != null) runtimeLauncher.launch(perm) else finishCancel()
    }

    private fun startActivitySafely(intent: Intent, launcher: ActivityResultLauncher<Intent>) {
        try { launcher.launch(intent) }
        catch (_: Exception) {
            try { startActivity(intent) } catch (_: Exception) {
                PermissionUtil.openAppSettings(this, 0x101)
            }
            finishCancel()
        }
    }

    private fun refreshStatus() {
        val granted = isGranted(type, this)
        b.tvStatus.text = if (granted) getString(R.string.permission_granted)
            else getString(R.string.permission_not_granted)
    }

    private fun maybeFinishIfGranted() {
        if (isGranted(type, this)) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun finishCancel() {
        setResult(Activity.RESULT_CANCELED); finish()
    }

    private fun titleOf(t: String) = when (t) {
        "overlay" -> getString(R.string.permission_overlay_title)
        "install" -> getString(R.string.permission_install_title)
        "storage" -> getString(R.string.permission_storage_title)
        "notification" -> getString(R.string.permission_notification_title)
        "camera" -> getString(R.string.permission_camera_title)
        "mic" -> getString(R.string.permission_mic_title)
        else -> getString(R.string.permission_required)
    }

    private fun descOf(t: String) = when (t) {
        "overlay" -> getString(R.string.permission_overlay_desc)
        "install" -> getString(R.string.permission_install_desc)
        "storage" -> getString(R.string.permission_storage_desc)
        "notification" -> getString(R.string.permission_notification_desc)
        "camera" -> getString(R.string.permission_camera_desc)
        "mic" -> getString(R.string.permission_mic_desc)
        else -> ""
    }

    companion object {
        fun isGranted(type: String, ctx: Context): Boolean = when (type) {
            "overlay" -> PermissionUtil.canDrawOverlays(ctx)
            "install" -> PermissionUtil.hasInstallPermission(ctx)
            "storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                android.os.Environment.isExternalStorageManager()
                else PermissionUtil.hasPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            "notification" -> PermissionUtil.hasNotificationPermission(ctx)
            "camera" -> PermissionUtil.hasPermission(ctx, android.Manifest.permission.CAMERA)
            "mic" -> PermissionUtil.hasPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
            else -> true
        }

        /** 便捷构造 Intent 给调用方。 */
        fun intent(
            ctx: Context, type: String,
            title: String? = null, desc: String? = null,
            icon: Int? = null, btnText: String? = null
        ): Intent = Intent(ctx, PermissionGuideActivity::class.java).apply {
            putExtra("type", type)
            title?.let { putExtra("title", it) }
            desc?.let { putExtra("desc", it) }
            icon?.let { putExtra("icon", it) }
            btnText?.let { putExtra("btnText", it) }
        }
    }
}
