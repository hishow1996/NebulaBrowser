package com.nebula.browser.plugin.core

import com.nebula.browser.plugin.model.ExtensionPackage
import com.nebula.browser.plugin.crx.CrxUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一安装入口：
 *   1. 从本地 .crx / .zip / 已解压目录
 *   2. 从 Chrome Web Store（通过商店客户端拉 CRX）
 *   3. 直接用户友好的"开发者模式加载目录"
 *
 * 处理流程：
 *   unpack → 解析 manifest → 写入 fileDir/extensions/<id>/ → 注册到 ExtensionRegistry
 */
class ExtensionInstaller {

    suspend fun installFromCrx(file: File, fromStore: Boolean = false): ExtensionPackage? =
        withContext(Dispatchers.IO) {
            val registry = ExtensionRegistry.get()
            val unpacked = CrxUnpacker.unpack(file, registry.rootDirPublic) ?: return@withContext null
            val pkg = parseManifestOf(unpacked.rootDir, unpacked.id) ?: return@withContext null
            registry.add(pkg, fromStore = fromStore)
            pkg
        }

    suspend fun installFromUnpackedDir(dir: File): ExtensionPackage? = withContext(Dispatchers.IO) {
        val meta = File(dir, "manifest.json")
        if (!meta.exists()) return@withContext null
        val seedName = dir.canonicalPath
        val id = com.nebula.browser.plugin.model.ExtensionId.fromSeed(seedName)
        val registry = ExtensionRegistry.get()
        val target = registry.rootFor(id)
        if (target.exists()) target.deleteRecursively()
        dir.copyRecursively(target, true)
        val pkg = parseManifestOf(target, id) ?: return@withContext null
        registry.add(pkg)
        pkg
    }

    suspend fun installFromBytes(bytes: ByteArray, suggestedName: String = "temp.crx"): ExtensionPackage? =
        withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("nebula_ext_", ".crx").apply { writeBytes(bytes) }
            val pkg = installFromCrx(tmp)
            tmp.delete()
            pkg
        }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO) {
        ExtensionRegistry.get().remove(id)
    }

    suspend fun toggle(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        ExtensionRegistry.get().setEnabled(id, enabled)
    }

    private fun parseManifestOf(dir: File, id: String): ExtensionPackage? {
        val file = File(dir, "manifest.json")
        if (!file.exists()) return null
        return try {
            val manifest = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true; isLenient = true
            }.decodeFromString(
                com.nebula.browser.plugin.model.WebExtensionManifest.serializer(),
                file.readText()
            )
            ExtensionPackage(id = id, rootDir = dir, manifest = manifest)
        } catch (e: Exception) { null }
    }
}
