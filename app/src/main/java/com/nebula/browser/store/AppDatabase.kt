package com.nebula.browser.store

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/* ====================== 实体 ====================== */

@Entity(tableName = "tab")
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val url: String = "",
    val favicon: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val url: String,
    val favicon: String? = null,
    val folder: String = "默认",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "userscript")
data class UserScriptEntity(
    @PrimaryKey val id: String,           // namespace + name 哈希
    val name: String,
    val namespace: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val code: String,                       // 完整脚本（含元数据）
    val matches: String,                    // JSON array
    val excludes: String,                   // JSON array
    val grants: String,                     // JSON array
    val runAt: String = "document-idle",
    val @require: String = "[]",
    val @resource: String = "[]",
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String = ""
)

@Entity(tableName = "plugin")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String = "1.0",
    val author: String = "",
    val description: String = "",
    val manifest: String,                  // JSON
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String = ""
)

@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val path: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: Int = 0,                   // 0=等待, 1=下载中, 2=完成, 3=失败, 4=暂停
    val mimeType: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "engine")
data class EngineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val urlTemplate: String,
    val suggestionUrl: String = "",
    val iconColor: Int = 0xFF3B82F6.toInt(),
    val isDefault: Boolean = false,
    val userAdded: Boolean = false
)

@Entity(tableName = "ai_profile")
data class AiProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val silent: Boolean = false,
    val temperature: Float = 0.7f,
    val maxLength: Int = 4096,
    val systemPrompt: String = "你是星云浏览器的内置AI助手，请简明扼要回答。"
)

@Entity(tableName = "ai_message")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,                       // user / assistant / system
    val content: String,
    val profileId: String = "",
    val conversationId: String = System.currentTimeMillis().toString(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    val intro: String = "",
    val bookUrl: String,
    val sourceName: String = "",
    val bookType: Int = 0,                   // 0=小说, 2=漫画
    val totalChapters: Int = 0,
    val currentChapter: Int = 0,
    val chapterTitle: String = "",
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_source")
data class BookSourceEntity(
    @PrimaryKey val bookSourceUrl: String,
    val bookSourceName: String,
    val bookSourceType: Int = 0,             // 0=小说 1=音频 2=图片
    val enabled: Boolean = true,
    val group: String = "",
    val comment: String = "",
    val header: String = "",
    val searchUrl: String = "",
    val exploreUrl: String = "",
    val loginUrl: String = "",
    val ruleSearch: String = "{}",           // JSON
    val ruleBookInfo: String = "{}",
    val ruleToc: String = "{}",
    val ruleContent: String = "{}",
    val customOrder: Int = 0
)

@Entity(tableName = "subtitle_translation")
data class SubtitleTranslationEntity(
    @PrimaryKey val hash: String,           // SHA1(src+srcLang+tgtLang)
    val srcLang: String,
    val tgtLang: String,
    val translatedText: String,
    val createdAt: Long = System.currentTimeMillis()
)

/* ====================== DAOs ====================== */

@Dao interface TabDao {
    @Query("SELECT * FROM tab ORDER BY orderIndex") fun getAll(): kotlinx.coroutines.flow.Flow<List<TabEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: TabEntity): Long
    @Update suspend fun update(t: TabEntity)
    @Query("DELETE FROM tab WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tab") suspend fun clear()
}

@Dao interface BookmarkDao {
    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC") fun getAll(): kotlinx.coroutines.flow.Flow<List<BookmarkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(b: BookmarkEntity)
    @Query("DELETE FROM bookmark WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE url = :url)") suspend fun exists(url: String): Boolean
}

@Dao interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500") fun getAll(): kotlinx.coroutines.flow.Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(h: HistoryEntity)
    @Query("DELETE FROM history WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM history") suspend fun clear()
    @Query("SELECT * FROM history WHERE title LIKE :q OR url LIKE :q ORDER BY visitedAt DESC LIMIT 50")
    suspend fun search(q: String): List<HistoryEntity>
}

@Dao interface UserScriptDao {
    @Query("SELECT * FROM userscript") fun getAll(): kotlinx.coroutines.flow.Flow<List<UserScriptEntity>>
    @Query("SELECT * FROM userscript WHERE enabled = 1") suspend fun getEnabled(): List<UserScriptEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: UserScriptEntity)
    @Query("DELETE FROM userscript WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE userscript SET enabled = :enabled WHERE id = :id") suspend fun toggle(id: String, enabled: Boolean)
    @Query("SELECT * FROM userscript WHERE id = :id") suspend fun get(id: String): UserScriptEntity?
}

@Dao interface PluginDao {
    @Query("SELECT * FROM plugin") fun getAll(): kotlinx.coroutines.flow.Flow<List<PluginEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: PluginEntity)
    @Query("DELETE FROM plugin WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE plugin SET enabled = :en WHERE id = :id") suspend fun toggle(id: String, en: Boolean)
}

@Dao interface DownloadDao {
    @Query("SELECT * FROM download ORDER BY createdAt DESC") fun getAll(): kotlinx.coroutines.flow.Flow<List<DownloadEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(d: DownloadEntity): Long
    @Update suspend fun update(d: DownloadEntity)
    @Query("DELETE FROM download WHERE id = :id") suspend fun delete(id: Long)
}

@Dao interface EngineDao {
    @Query("SELECT * FROM engine") fun getAll(): kotlinx.coroutines.flow.Flow<List<EngineEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: EngineEntity)
    @Query("DELETE FROM engine WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE engine SET isDefault = (id = :id)") suspend fun setDefault(id: String)
}

@Dao interface AiProfileDao {
    @Query("SELECT * FROM ai_profile") fun getAll(): kotlinx.coroutines.flow.Flow<List<AiProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: AiProfileEntity)
    @Query("DELETE FROM ai_profile WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM ai_profile WHERE id = :id") suspend fun get(id: String): AiProfileEntity?
}

@Dao interface AiMessageDao {
    @Query("SELECT * FROM ai_message WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun list(cid: String): kotlinx.coroutines.flow.Flow<List<AiMessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(m: AiMessageEntity): Long
    @Query("DELETE FROM ai_message WHERE conversationId = :cid") suspend fun clear(cid: String)
    @Query("SELECT DISTINCT conversationId FROM ai_message ORDER BY createdAt DESC")
    fun conversations(): kotlinx.coroutines.flow.Flow<List<String>>
}

@Dao interface BookDao {
    @Query("SELECT * FROM book ORDER BY pinned DESC, updatedAt DESC") fun all(): kotlinx.coroutines.flow.Flow<List<BookEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(b: BookEntity): Long
    @Update suspend fun update(b: BookEntity)
    @Query("DELETE FROM book WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE book SET pinned = :p WHERE id = :id") suspend fun pin(id: Long, p: Boolean)
}

@Dao interface BookSourceDao {
    @Query("SELECT * FROM book_source ORDER BY customOrder") fun all(): kotlinx.coroutines.flow.Flow<List<BookSourceEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: BookSourceEntity)
    @Query("DELETE FROM book_source WHERE bookSourceUrl = :url") suspend fun delete(url: String)
    @Query("SELECT * FROM book_source WHERE enabled = 1") suspend fun getEnabled(): List<BookSourceEntity>
}

@Dao interface SubtitleDao {
    @Query("SELECT * FROM subtitle_translation WHERE hash = :h") suspend fun get(h: String): SubtitleTranslationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: SubtitleTranslationEntity)
}

/* ====================== 数据库 ====================== */

@Database(entities = [
    TabEntity::class, BookmarkEntity::class, HistoryEntity::class,
    UserScriptEntity::class, PluginEntity::class, DownloadEntity::class,
    EngineEntity::class, AiProfileEntity::class, AiMessageEntity::class,
    BookEntity::class, BookSourceEntity::class, SubtitleTranslationEntity::class
], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun userScriptDao(): UserScriptDao
    abstract fun pluginDao(): PluginDao
    abstract fun downloadDao(): DownloadDao
    abstract fun engineDao(): EngineDao
    abstract fun aiProfileDao(): AiProfileDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun bookDao(): BookDao
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun subtitleDao(): SubtitleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "nebula.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
