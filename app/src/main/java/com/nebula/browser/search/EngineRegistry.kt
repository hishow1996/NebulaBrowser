package com.nebula.browser.search

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.FileUtil
import com.nebula.browser.common.toast
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.EngineEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class EngineJson(
    val id: String, val name: String, val urlTemplate: String,
    val suggestionUrl: String = "", val iconColor: String = "#3B82F6"
)
@Serializable data class EnginesJson(val engines: List<EngineJson>, val defaultEngineId: String = "google")

data class SearchEngine(
    val id: String, val name: String, val urlTemplate: String,
    val suggestionUrl: String, val iconColor: Int, var isDefault: Boolean = false, val userAdded: Boolean = false
) {
    fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return urlTemplate.replace("%s", encoded)
    }
    fun buildSuggestUrl(query: String): String? {
        if (suggestionUrl.isBlank()) return null
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return suggestionUrl.replace("%s", encoded)
    }
}

object EngineRegistry {
    private val _engines = MutableStateFlow<List<SearchEngine>>(emptyList())
    val engines: Flow<List<SearchEngine>> = _engines.asStateFlow()

    private val _currentId = MutableStateFlow("google")
    val currentId = _currentId.asStateFlow()

    private val client = OkHttpClient()

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val dao = db.engineDao()
        val existing = db.engineDao().getAll()  // ignore warning, use Flow with first
        // Load default engines from JSON if DB is empty
        val loaded = mutableListOf<SearchEngine>()
        try {
            val daoFlow = dao.getAll()
            val listInDb = collectFirst(daoFlow)
            if (listInDb.isEmpty()) {
                seedFromAssets(context)
            }
            val finalList = collectFirst(dao.getAll())
            val defaultId = com.nebula.browser.store.SettingsManager.defaultEngineId
            finalList.forEach { e ->
                loaded.add(SearchEngine(e.id, e.name, e.urlTemplate, e.suggestionUrl, e.iconColor, e.isDefault, e.userAdded))
            }
            _engines.value = loaded
            _currentId.value = defaultId.ifEmpty { loaded.firstOrNull()?.id ?: "google" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun collectFirst(flow: Flow<List<EngineEntity>>): List<EngineEntity> =
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.flow.firstOrNull(flow) ?: emptyList()
        }

    suspend fun seedFromAssets(context: Context) = withContext(Dispatchers.IO) {
        try {
            val text = FileUtil.assetText(context, "default_engines.json")
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(EnginesJson.serializer(), text)
            val dao = AppDatabase.get(context).engineDao()
            parsed.engines.forEach {
                dao.insert(EngineEntity(
                    id = it.id, name = it.name, urlTemplate = it.urlTemplate,
                    suggestionUrl = it.suggestionUrl, iconColor = parseColor(it.iconColor),
                    isDefault = it.id == parsed.defaultEngineId, userAdded = false
                ))
            }
            com.nebula.browser.store.SettingsManager.defaultEngineId = parsed.defaultEngineId
        } catch (e: Exception) {
            toast("加载默认引擎失败")
            e.printStackTrace()
        }
    }

    fun current(): SearchEngine? = _engines.value.firstOrNull { it.id == _currentId.value }

    fun setCurrent(id: String) {
        _currentId.value = id
        com.nebula.browser.store.SettingsManager.defaultEngineId = id
    }

    fun list(): List<SearchEngine> = _engines.value

    private fun parseColor(color: String?): Int {
        return try {
            color?.let { android.graphics.Color.parseColor(it) } ?: 0xFF3B82F6.toInt()
        } catch (e: Exception) { 0xFF3B82F6.toInt() }
    }

    suspend fun addCustom(name: String, urlTemplate: String, suggestionUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            val id = "custom_${System.currentTimeMillis()}"
            val e = EngineEntity(id, name, urlTemplate, suggestionUrl, 0xFF8B5CF6.toInt(), false, true)
            AppDatabase.get(AppContext.appContext).engineDao().insert(e)
            _engines.value = _engines.value + SearchEngine(id, name, urlTemplate, suggestionUrl, 0xFF8B5CF6.toInt(), false, true)
            true
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).engineDao().delete(id)
        _engines.value = _engines.value.filterNot { it.id == id }
    }

    suspend fun fetchSuggestion(engine: SearchEngine, query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val url = engine.buildSuggestUrl(query) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13) NebulaBrowser").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                parseSuggestion(body, engine.id)
            } catch (e: Exception) { emptyList() }
        }
    }

    private fun parseSuggestion(body: String, engineId: String): List<String> {
        if (body.isBlank()) return emptyList()
        return try {
            when (engineId) {
                "google", "duckduckgo", "bing", "brave" -> {
                    val arr = Json.decodeFromString<kotlinx.serialization.json.JsonArray>(body)
                    arr[1].let { if (it is kotlinx.serialization.json.JsonArray) it.mapNotNull { el -> (el as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        .filter { s -> s.isNotEmpty() } } else emptyList()
                }
                "baidu" -> {
                    val regex = """"word":"([^"]+)"""".toRegex()
                    regex.findAll(body).map { it.groupValues[1] }.toList()
                }
                else -> {
                    val arr = Json.decodeFromString<kotlinx.serialization.json.JsonArray>(body)
                    arr.drop(1).mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.filter { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) { emptyList() }
    }
}
