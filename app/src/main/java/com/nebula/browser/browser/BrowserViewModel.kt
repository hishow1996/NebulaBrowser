package com.nebula.browser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebula.browser.browser.tab.TabManager
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.HistoryEntity
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.launch

class BrowserViewModel : ViewModel() {

    fun recordHistory(title: String, url: String) {
        if (!SettingsManager.saveHistory) return
        if (url.startsWith("about:")) return
        viewModelScope.launch {
            AppDatabase.get(AppContext.appContext).historyDao().add(HistoryEntity(title = title, url = url))
        }
    }

    fun newTab(url: String = "about:blank") = TabManager.get().new(url)
}
