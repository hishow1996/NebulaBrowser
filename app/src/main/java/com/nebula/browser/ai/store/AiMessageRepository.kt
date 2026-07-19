package com.nebula.browser.ai.store

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AiMessageEntity
import com.nebula.browser.store.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiMessageRepository {
    fun observe(ctx: Context, conversationId: String): Flow<List<AiMessageEntity>> =
        AppDatabase.get(ctx).aiMessageDao().list(conversationId)

    suspend fun add(msg: AiMessageEntity): Long = withContext(Dispatchers.IO) {
        AppDatabase.get(AppContext.appContext).aiMessageDao().add(msg)
    }

    suspend fun clear(ctx: Context, conversationId: String) = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).aiMessageDao().clear(conversationId)
    }
}
