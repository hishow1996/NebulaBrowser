package com.nebula.browser.plugin.core

import com.nebula.browser.common.AppContext
import com.nebula.browser.plugin.model.ExtensionPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 扩展注册表：内存映射 + 文件系统持久化。
 *
 * 每个扩展放在 `fileDir/extensions/<id>/...`
 * 持久化记录 `/extensions/installed.json`（避免重 import 解包费用）。
 * 用户可通过 setEnabled 启停。
 */
class ExtensionRegistry private constructor() {

    private val extDir: File get() =
        File(AppContext.appContext.filesDir, "extensions").apply { mkdirs() }
    private val metaFile: File get() = File(AppContext.appContext.filesDir, "extensions_meta.json")

    private val _installed = MutableStateFlow<List<ExtensionPackage>>(emptyList())
    val installed: StateFlow<List<ExtensionPackage>> = _installed.asStateFlow()

    private val records = mutableMapOf<String, Record>()   // id -> record

    suspend fun loadAll() {
        val list = mutableListOf<ExtensionPackage>()
        try {
            if (metaFile.exists()) {
                val obj = org.json.JSONArray(metaFile.readText())
                for (i in 0 until obj.length()) {
                    val r = obj.getJSONObject(i)
                    val record = Record(r.getString("id"), r.getBoolean("enabled"),
                        System.currentTimeMillis(), r.optString("from_store", "false").toBoolean())
                    records[r.getString("id")] = record
                }
            }
            extDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
                val manifestFile = File(dir, "manifest.json")
                if (!manifestFile.exists()) return@forEach
                val pkg = parseManifest(dir) ?: return@forEach
                val rec = records[pkg.id]
                val enabled = rec?.enabled ?: true
                list.add(pkg.copy(enabled = enabled, installedAt = rec?.installedAt ?: pkg.installedAt,
                    fromChromeWebStore = rec?.fromStore ?: false))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _installed.value = list
        persist()
    }

    fun add(pkg: ExtensionPackage, fromStore: Boolean = false) {
        records[pkg.id] = Record(pkg.id, pkg.enabled, pkg.installedAt, fromStore)
        _installed.value = (_installed.value.filterNot { it.id == pkg.id } + pkg)
        persist()
    }

    fun remove(id: String) {
        records.remove(id)
        _installed.value = _installed.value.filterNot { it.id == id }
        File(extDir, id).deleteRecursively()
        persist()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        records[id]?.enabled = enabled
        _installed.value = _installed.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persist()
    }

    fun get(id: String): ExtensionPackage? = _installed.value.firstOrNull { it.id == id }

    fun list(): List<ExtensionPackage> = _installed.value

    private fun parseManifest(dir: File): ExtensionPackage? = try {
        val text = File(dir, "manifest.json").readText()
        val manifest = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; isLenient = true
        }.decodeFromString(
            com.nebula.browser.plugin.model.WebExtensionManifest.serializer(), text
        )
        ExtensionPackage(id = dir.name, rootDir = dir, manifest = manifest)
    } catch (e: Exception) { null }

    private fun persist() {
        try {
            val arr = org.json.JSONArray()
            records.forEach { (id, r) ->
                arr.put(org.json.JSONObject()
                    .put("id", id)
                    .put("enabled", r.enabled)
                    .put("installed_at", r.installedAt)
                    .put("from_store", r.fromStore))
            }
            metaFile.writeText(arr.toString())
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun rootFor(id: String): File = File(extDir, id)
    val rootDirPublic: File get() = extDir

    private data class Record(var id: String, var enabled: Boolean,
                              var installedAt: Long, var fromStore: Boolean)

    companion object {
        @Volatile private var INST: ExtensionRegistry? = null
        fun get(): ExtensionRegistry = INST ?: synchronized(this) {
            INST ?: ExtensionRegistry().also { INST = it }
        }
    }
}
