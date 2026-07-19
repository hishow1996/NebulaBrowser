package com.nebula.browser.browser.tab

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Tab(
    val id: String,
    var title: String = "新标签",
    var url: String = "about:blank",
    var favicon: String? = null,
    var progress: Int = 0,
    var isLoading: Boolean = false,
    var isIncognito: Boolean = false,
    var webview: WebView? = null
)

class TabManager {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs = _tabs.asStateFlow()
    private val _currentIdx = MutableStateFlow(-1)
    val currentIdx = _currentIdx.asStateFlow()

    val current: Tab? get() = _tabs.value.getOrNull(_currentIdx.value)
    val size: Int get() = _tabs.value.size

    fun new(url: String = "about:blank", incognito: Boolean = false): Tab {
        val tab = Tab(id = "tab_${System.currentTimeMillis()}_${_tabs.value.size}",
            url = url, isIncognito = incognito)
        _tabs.value = _tabs.value + tab
        _currentIdx.value = _tabs.value.size - 1
        return tab
    }

    fun select(id: String) {
        _tabs.value.indexOfFirst { it.id == id }.let { if (it >= 0) _currentIdx.value = it }
    }

    fun close(id: String) {
        val idx = _tabs.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        _tabs.value = _tabs.value.toMutableList().also { it.removeAt(idx).let { _ -> } }
        _tabs.value[idx].webview?.also { it.destroy() }
        _tabs.value = _tabs.value.toMutableList().apply { removeAt(idx) }
        // 索引调整
        _currentIdx.value = when {
            _tabs.value.isEmpty() -> -1
            idx < _currentIdx.value -> _currentIdx.value - 1
            idx == _currentIdx.value && idx > 0 -> idx - 1
            idx == _currentIdx.value && idx == 0 -> 0
            else -> _currentIdx.value
        }
        if (_tabs.value.isEmpty()) new()
    }

    fun closeAllExcept(id: String) {
        val keep = _tabs.value.firstOrNull { it.id == id } ?: return
        _tabs.value.filterNot { it.id == id }.forEach { it.webview?.destroy() }
        _tabs.value = listOf(keep)
        _currentIdx.value = 0
    }

    fun closeAll() {
        _tabs.value.forEach { it.webview?.destroy() }
        _tabs.value = emptyList()
        _currentIdx.value = -1
    }

    fun update(id: String, mutate: (Tab) -> Unit) {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            mutate(list[idx])
            _tabs.value = list
        }
    }

    companion object {
        @Volatile private var INST: TabManager? = null
        fun get(): TabManager = INST ?: synchronized(this) { INST ?: TabManager().also { INST = it } }
    }
}
