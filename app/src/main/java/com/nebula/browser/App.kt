package com.nebula.browser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nebula.browser.common.AppContext
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.SettingsManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        // 用户偏好主题（默认白天）
        when (SettingsManager.themeMode) {
            SettingsManager.THEME_DAY -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.THEME_NIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        // 初始化数据库
        AppDatabase.get(this)
        // 加载已安装的 Chrome 扩展并启动后台脚本运行时
        kotlinx.coroutines.GlobalScope.launch {
            com.nebula.browser.plugin.core.ExtensionRegistry.get().loadAll()
            com.nebula.browser.plugin.inject.BackgroundScriptRunner.get().startAll(this@App)
        }
    }
}
