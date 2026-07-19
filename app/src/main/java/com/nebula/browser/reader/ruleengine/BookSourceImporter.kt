package com.nebula.browser.reader.ruleengine

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.BookSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BookSourceJson(
    val bookSourceName: String,
    val bookSourceUrl: String,
    val bookSourceType: Int = 0,
    val enabled: Boolean = true,
    val bookSourceGroup: String = "",
    val bookSourceComment: String = "",
    val header: String = "",
    val searchUrl: String = "",
    val exploreUrl: String = "",
    val loginUrl: String = "",
    val ruleSearch: String = "{}",
    val ruleBookInfo: String = "{}",
    val ruleToc: String = "{}",
    val ruleContent: String = "{}",
    val customOrder: Int = 0
)
@Serializable data class BookSourcesJson(val sources: List<BookSourceJson>)

class BookSourceImporter {
    suspend fun importFromJson(ctx: Context, json: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(BookSourcesJson.serializer(), json)
            val dao = AppDatabase.get(ctx).bookSourceDao()
            parsed.sources.forEach { s ->
                val entity = BookSourceEntity(
                    bookSourceUrl = s.bookSourceUrl,
                    bookSourceName = s.bookSourceName,
                    bookSourceType = s.bookSourceType,
                    enabled = s.enabled,
                    group = s.bookSourceGroup,
                    comment = s.bookSourceComment,
                    header = s.header,
                    searchUrl = s.searchUrl,
                    exploreUrl = s.exploreUrl,
                    loginUrl = s.loginUrl,
                    ruleSearch = s.ruleSearch,
                    ruleBookInfo = s.ruleBookInfo,
                    ruleToc = s.ruleToc,
                    ruleContent = s.ruleContent,
                    customOrder = s.customOrder
                )
                dao.insert(entity)
                count++
            }
        } catch (_: Exception) {}
        count
    }

    suspend fun importDefaults(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            val text = com.nebula.browser.common.FileUtil.assetText(ctx, "default_book_sources.json")
            importFromJson(ctx, text)
        } catch (_: Exception) {}
    }
}
