package com.nebula.browser.ai.profiles

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.common.FileUtil
import com.nebula.browser.store.AiProfileEntity
import com.nebula.browser.store.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AiProfileJson(
    val id: String, val name: String, val baseUrl: String,
    val apiKey: String = "", val model: String,
    val silent: Boolean = true, val temperature: Float = 0.7f,
    val maxLength: Int = 4096
)
@Serializable
data class AiProfilesJson(val profiles: List<AiProfileJson>, val defaultProfileId: String)

class AiProfileRepository {
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).aiProfileDao()
        val existing = kotlinx.coroutines.flow.firstOrNull(dao.getAll()) { it.isNotEmpty() }
        if (existing.isNullOrEmpty()) {
            seedProfiles(context)
        }
    }

    private suspend fun seedProfiles(context: Context) = withContext(Dispatchers.IO) {
        try {
            val text = FileUtil.assetText(context, "default_ai_profiles.json")
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(AiProfilesJson.serializer(), text)
            val dao = AppDatabase.get(context).aiProfileDao()
            parsed.profiles.forEach {
                dao.insert(AiProfileEntity(
                    id = it.id, name = it.name, baseUrl = it.baseUrl,
                    apiKey = it.apiKey, model = it.model, silent = it.silent,
                    temperature = it.temperature, maxLength = it.maxLength
                ))
            }
            SettingsManager.aiProfileId = parsed.defaultProfileId
        } catch (_: Exception) {}
    }

    fun observe(context: Context): Flow<List<AiProfileEntity>> =
        AppDatabase.get(context).aiProfileDao().getAll()

    suspend fun add(profile: AiProfileEntity) = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).aiProfileDao().insert(profile)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).aiProfileDao().delete(id)
    }
}
