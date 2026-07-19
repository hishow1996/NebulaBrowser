package com.nebula.browser.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import androidx.core.content.FileProvider
import java.io.File

object PermissionUtil {

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun requestOverlayPermission(activity: Activity, code: Int) {
        if (!canDrawOverlays(activity)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"))
            activity.startActivityForResult(i, code)
        }
    }

    fun hasInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** dp -> px */
    fun dpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            context.resources.displayMetrics)
}

object FileUtil {
    fun downloadDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "NebulaBrowser")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    fun videoCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "video_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    fun readabilityJs(context: Context): String =
        context.assets.open("readability.js").bufferedReader().use { it.readText() }
    fun nebulaBootJs(context: Context): String =
        context.assets.open("nebula_boot.js").bufferedReader().use { it.readText() }
    fun assetText(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
    fun openFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = guessMime(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "打开"))
    }
    private fun guessMime(name: String): String = when {
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".mkv", true) -> "video/x-matroska"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".epub", true) -> "application/epub+zip"
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".zip", true) -> "application/zip"
        else -> "*/*"
    }
}

object UrlUtil {
    fun isProbablyUrl(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
               (lower.startsWith("www.") && !text.contains(" "))
    }
    fun toUrlOrSearch(text: String, searchUrlTemplate: String): String =
        if (isProbablyUrl(text)) {
            if (!text.startsWith("http", true)) "https://$text" else text
        } else {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            searchUrlTemplate.replace("%s", encoded)
        }
}
