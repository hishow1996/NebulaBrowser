package com.nebula.browser.ai.client

import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AiProfileEntity
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI 兼容客户端 - 支持 DeepSeek/Grok/GLM/Qwen 等 */
class OpenAiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** SSE 流式 - 返回 delta 文本流 */
    fun stream(messages: List<Pair<String, String>>, profile: AiProfileEntity): Flow<String> = flow {
        val json = JSONObject()
            .put("model", profile.model)
            .put("stream", true)
            .put("temperature", profile.temperature)
            .put("max_tokens", profile.maxLength)
            .put("messages", JSONArray().apply {
                messages.forEach { (role, content) -> put(JSONObject().put("role", role).put("content", content)) }
            }).toString()
        val req = Request.Builder()
            .url("${profile.baseUrl.trimEnd('/')}/chat/completions")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .apply { if (profile.apiKey.isNotBlank()) header("Authorization", "Bearer ${profile.apiKey}") }
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            val resp = client.newCall(req).execute()
            resp.body?.byteStream()?.bufferedReader()?.use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val o = JSONObject(data)
                        val delta = o.optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content")
                        if (!delta.isNullOrEmpty()) emit(delta)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            emit("⚠️ 调用失败：${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /** 一次性非流式调用 - 用于翻译兜底等 */
    suspend fun completeOnceSilentMode(prompt: String, systemPrompt: String = ""): String =
        withContext(Dispatchers.IO) {
            val profile = loadSilentProfile() ?: return@withContext prompt
            val json = JSONObject()
                .put("model", profile.model)
                .put("stream", false)
                .put("messages", JSONArray().apply {
                    if (systemPrompt.isNotBlank())
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", prompt))
                }).toString()
            val req = Request.Builder()
                .url("${profile.baseUrl.trimEnd('/')}/chat/completions")
                .header("Content-Type", "application/json")
                .apply { if (profile.apiKey.isNotBlank()) header("Authorization", "Bearer ${profile.apiKey}") }
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                val body = client.newCall(req).execute().body?.string().orEmpty()
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content") ?: ""
            } catch (e: Exception) { prompt }
        }

    /** 加载当前默认 silent profile */
    suspend fun loadSilentProfile(): AiProfileEntity? = withContext(Dispatchers.IO) {
        val id = SettingsManager.aiProfileId
        val p = AppDatabase.get(AppContext.appContext).aiProfileDao().get(id)
            ?: AppDatabase.get(AppContext.appContext).aiProfileDao().let {
                kotlinx.coroutines.flow.firstOrNull(it.getAll()) { it.isNotEmpty() }?.firstOrNull()
            }
        p
    }
}
