package com.nebula.browser.reader.shelf

import android.content.Context
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object BookRepository {
    fun observe(ctx: Context): Flow<List<BookEntity>> =
        AppDatabase.get(ctx).bookDao().all()

    suspend fun add(ctx: Context, book: BookEntity): Long = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).bookDao().add(book)
    }

    suspend fun update(ctx: Context, book: BookEntity) = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).bookDao().update(book)
    }

    suspend fun delete(ctx: Context, id: Long) = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).bookDao().delete(id)
    }

    suspend fun pin(ctx: Context, id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).bookDao().pin(id, pinned)
    }
}
