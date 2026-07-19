package com.nebula.browser.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchSuggestionManager(
    private val debounceMs: Long = 300
) {
    private val _results = MutableStateFlow<List<String>>(emptyList())
    val results = _results.asStateFlow()

    private var lastJob: kotlinx.coroutines.Job? = null
    private var lastQuery: String = ""

    suspend fun submit(query: String, engine: SearchEngine?) {
        lastJob?.let { it.cancel() }
        if (engine == null || query.isBlank() || query == lastQuery) {
            _results.value = emptyList()
            return
        }
        delay(debounceMs)
        lastQuery = query
        try {
            _results.value = EngineRegistry.fetchSuggestion(engine, query)
        } catch (e: Exception) {
            _results.value = emptyList()
        }
    }

    fun cancel() {
        lastJob?.cancel()
        _results.value = emptyList()
    }
}
