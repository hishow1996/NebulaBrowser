package com.nebula.browser.userscript.store

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.UserScriptEntity
import com.nebula.browser.userscript.model.UserScript
import com.nebula.browser.userscript.parser.UserScriptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UserScriptStore {
    private val client = OkHttpClient()

    fun observe(@Suppress("UNUSED_PARAMETER") ctx: Context): Flow<List<UserScriptEntity>> =
        AppDatabase.get(ctx).userScriptDao().getAll()

    suspend fun installFromUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; NebulaBrowser/1.0)")
                .build()
            val code = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            if (code.isBlank() || !code.contains("==UserScript==")) return@withContext false
            installFromCode(code, sourceUrl = url)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun installFromCode(code: String, enabled: Boolean = true, sourceUrl: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            val script = UserScriptParser.parse(code, sourceUrl)
            val matches = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                script.meta.matches
            )
            val excludes = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                script.meta.excludes + script.meta.includes
            )
            val grants = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                script.meta.grants
            )
            val requires = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                script.meta.requires
            )
            val entity = UserScriptEntity(
                id = script.id, name = script.meta.name, namespace = script.meta.namespace,
                version = script.meta.version, author = script.meta.author,
                description = script.meta.description, code = code,
                matches = matches, excludes = excludes, grants = grants,
                runAt = script.meta.runAt,
                @require = requires, enabled = enabled, sourceUrl = sourceUrl
            )
            AppDatabase.get(AppContext.appContext).userScriptDao().insert(entity)
            true
        }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).userScriptDao().delete(id)
    }

    suspend fun toggle(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).userScriptDao().toggle(id, enabled)
    }
}
