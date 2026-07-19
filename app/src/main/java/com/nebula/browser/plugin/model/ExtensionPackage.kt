package com.nebula.browser.plugin.model

import java.io.File

/**
 * 一个已安装的 Chrome 扩展包：包含 manifest 解析结果与解压后的根目录。
 * 参照 Chromium 'extensions::Extension' 的角色。
 */
data class ExtensionPackage(
    val id: String,              // 32 字符 a-p（与 Chrome 一致的扩展 ID 算法）
    val rootDir: File,
    val manifest: WebExtensionManifest,
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val updateUrl: String? = null,
    val fromChromeWebStore: Boolean = false
) {
    val name: String get() = manifest.name
    val version: String get() = manifest.version
    val description: String get() = manifest.description
    val icons: Map<String, String> get() = manifest.icons
    val optionsPage: String?
        get() = manifest.optionsUi?.page ?: manifest.optionsPage
    val popupHtml: String?
        get() = (manifest.action ?: manifest.browserAction)?.defaultPopup
    val actionTitle: String
        get() = (manifest.action ?: manifest.browserAction)?.defaultTitle ?: manifest.name
    val contentScripts: List<ContentScriptDeclaration> get() = manifest.contentScripts
    val background: Background? get() = manifest.background

    fun resolve(path: String?): File? = if (path.isNullOrBlank()) null else File(rootDir, path)
    fun read(path: String?): String? = resolve(path)?.let { it.takeIf { f -> f.isFile }?.readText() }
}
