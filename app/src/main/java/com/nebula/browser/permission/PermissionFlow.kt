package com.nebula.browser.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.ComponentActivity
import com.nebula.browser.common.PermissionUtil

/**
 * 系统权限统一封装：危险权限（运行时）+ 系统设置类权限
 * （悬浮窗 / 安装未知来源 / 所有文件访问 / 通知）。
 *
 * 用法：
 *   val flow = PermissionFlow(activity)
 *   flow.request(Permission.Overlay) { granted ->
 *       if (granted) startFloatingWindow()
 *   }
 *
 * 内部用 ActivityResultLauncher，统一返回「是否已授权 + 授权后自动续上原回调」。
 */
class PermissionFlow(private val activity: ComponentActivity) {

    private val runtimeLaunchers = mutableMapOf<String, ActivityResultLauncher<String>>()
    private val settingLaunchers = mutableMapOf<String, ActivityResultLauncher<Intent>>()
    private val callbacks = mutableMapOf<String, (Boolean) -> Unit>()

    private val settingsContract = ActivityResultContracts.StartActivityForResult()

    /**
     * 请求某权限。返回值回调里布尔表示是否已就绪。
     * - 危险权限：走 RequestPermission。
     * - 系统设置类：跳到对应系统设置页，回来后重新检测。
     */
    fun request(permission: Permission, cb: (Boolean) -> Unit) {
        when (permission) {
            is Permission.Runtime -> requestRuntime(permission, cb)
            is Permission.Setting -> requestSetting(permission, cb)
        }
    }

    private fun requestRuntime(p: Permission.Runtime, cb: (Boolean) -> Unit) {
        val launcher = runtimeLaunchers.getOrPut(p.androidPermission) {
            activity.registerForActivityResult(RequestPermission()) { granted ->
                callbacks.remove(p.androidPermission)?.invoke(granted)
            }
        }
        if (PermissionUtil.hasPermission(activity, p.androidPermission)) {
            cb(true); return
        }
        callbacks[p.androidPermission] = cb
        launcher.launch(p.androidPermission)
    }

    private fun requestSetting(p: Permission.Setting, cb: (Boolean) -> Unit) {
        val key = p::class.simpleName ?: p.toString()
        val launcher = settingLaunchers.getOrPut(key) {
            activity.registerForActivityResult(settingsContract) {
                val granted = p.checkGranted(activity)
                callbacks.remove(key)?.invoke(granted)
            }
        }
        if (p.checkGranted(activity)) { cb(true); return }
        callbacks[key] = cb
        val intent = p.intent(activity) ?: run { cb(false); return }
        launcher.launch(intent)
    }
}

/** 权限类型。两种：运行时危险权限、需要跳系统设置的权限。 */
sealed class Permission {

    abstract fun checkGranted(ctx: Context): Boolean

    /** 危险权限（Manifest 中声明，运行时弹系统对话框）。 */
    data class Runtime(val androidPermission: String) : Permission() {
        override fun checkGranted(ctx: Context): Boolean =
            PermissionUtil.hasPermission(ctx, androidPermission)
    }

    /** 需跳到系统设置开启的权限。 */
    sealed class Setting : Permission() {
        abstract fun intent(activity: Activity): Intent?

        /** 悬浮窗 */
        object Overlay : Setting() {
            override fun checkGranted(ctx: Context): Boolean =
                PermissionUtil.canDrawOverlays(ctx)
            override fun intent(activity: Activity): Intent? {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
                return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"))
            }
        }

        /** 安装未知来源 */
        object InstallUnknown : Setting() {
            override fun checkGranted(ctx: Context): Boolean =
                PermissionUtil.hasInstallPermission(ctx)
            override fun intent(activity: Activity): Intent? {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
                return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"))
            }
        }

        /** 所有文件访问（仅 Android 11+） */
        object AllFilesAccess : Setting() {
            override fun checkGranted(ctx: Context): Boolean =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    android.os.Environment.isExternalStorageManager()
            override fun intent(activity: Activity): Intent? {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
                return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}"))
            }
        }

        /** 通知（Android 13+ 走运行时，13- 跳通知设置；统一封装） */
        object Notification : Setting() {
            override fun checkGranted(ctx: Context): Boolean =
                PermissionUtil.hasNotificationPermission(ctx)
            override fun intent(activity: Activity): Intent? {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 13+ 走运行时申请；这里用单独请求
                    return null
                }
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}"))
            }
        }
    }

    companion object {
        val Overlay = Setting.Overlay
        val InstallUnknown = Setting.InstallUnknown
        val AllFilesAccess = Setting.AllFilesAccess
        val Notification = Setting.Notification
        fun Runtime(name: String) = Runtime(name)
        val Camera = Runtime(android.Manifest.permission.CAMERA)
        val Microphone = Runtime(android.Manifest.permission.RECORD_AUDIO)
        val Notifications13 = Runtime(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
