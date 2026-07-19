package com.nebula.browser.userscript.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GreasyForkScript(
    val id: Int,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val code_url: String,
    val url: String,
    val version: String? = null,
    val updated_at: String,
    val daily_installations: Int = 0,
    val total_installations: Int = 0,
    val fan_score: Double = 0.0
)

class GreasyForkRepository {
    private val client = OkHttpClient()
    private val base = "https://greasyfork.org/en/scripts.json"

    suspend fun search(q: String): List<GreasyForkScript> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) NebulaBrowser").build()
            val body = client.newCall(req).execute().body?.string().orEmpty()
            Json { ignoreUnknownKeys = true }.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(GreasyForkScript.serializer()),
                body
            )
        } catch (e: Exception) { emptyList() }
    }

    suspend fun popular(): List<GreasyForkScript> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base?sort=total_installs")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) NebulaBrowser").build()
            val body = client.newCall(req).execute().body?.string().orEmpty()
            Json { ignoreUnknownKeys = true }.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(GreasyForkScript.serializer()),
                body
            )
        } catch (e: Exception) { emptyList() }
    }
}

class OpenUserJsRepository {
    private val client = OkHttpClient()
    private val installBase = "https://openuserjs.org/install/"
    private val searchBase = "https://openuserjs.org/search/"

    suspend fun installableUrl(user: String, name: String): String = "$installBase$user/$name.user.js"
}
