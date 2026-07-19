package com.nebula.browser.store

import com.nebula.browser.common.AppContext
import com.nebula.browser.common.getPrefBoolean
import com.nebula.browser.common.getPrefInt
import com.nebula.browser.common.getPrefString
import com.nebula.browser.common.putPref

object SettingsManager {
    const val THEME_SYSTEM = 0
    const val THEME_DAY = 1
    const val THEME_NIGHT = 2

    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_DEFAULT_ENGINE = "default_engine_id"
    const val KEY_ADS_BLOCK = "ads_block"
    const val KEY_SAVE_HISTORY = "save_history"
    const val KEY_JAVASCRIPT = "js_enabled"
    const val KEY_DEFAULT_QUALITY = "default_quality"
    const val KEY_AUTO_QUALITY = "auto_quality"
    const val KEY_SAVER = "saver_mode"
    const val KEY_DARK_WEB = "dark_web"
    const val KEY_FLOAT_GLOBAL = "float_global"
    const val KEY_AI_PROFILE = "ai_profile_id"
    const val KEY_CAPTION_DEFAULT = "caption_default"
    const val KEY_AUTO_TRANSLATE = "auto_translate"
    const val KEY_TGT_LANG = "tgt_lang"
    const val KEY_VIDEO_CACHE = "video_cache"
    const val KEY_VIDEO_CACHE_SIZE = "video_cache_mb"
    const val KEY_READER_THEME = "reader_theme"

    var themeMode: Int
        get() = AppContext.prefs.getInt(KEY_THEME_MODE, THEME_DAY)
        set(v) = putPref(KEY_THEME_MODE, v)

    var defaultEngineId: String
        get() = getPrefString(KEY_DEFAULT_ENGINE, "google")
        set(v) = putPref(KEY_DEFAULT_ENGINE, v)

    val adBlock get() = getPrefBoolean(KEY_ADS_BLOCK, true)
    val saveHistory get() = getPrefBoolean(KEY_SAVE_HISTORY, true)
    val javascript get() = getPrefBoolean(KEY_JAVASCRIPT, true)
    val saveMode get() = getPrefBoolean(KEY_SAVER, false)
    val darkWeb get() = getPrefBoolean(KEY_DARK_WEB, false)
    val floatGlobal get() = getPrefBoolean(KEY_FLOAT_GLOBAL, true)

    var defaultQuality: Int       // 0=Auto,1=240,2=360,3=480,4=720,5=1080,6=Source
        get() = getPrefInt(KEY_DEFAULT_QUALITY, 0)
        set(v) = putPref(KEY_DEFAULT_QUALITY, v)

    val autoQuality get() = getPrefBoolean(KEY_AUTO_QUALITY, true)

    var aiProfileId: String
        get() = getPrefString(KEY_AI_PROFILE, "nebula-deepseek")
        set(v) = putPref(KEY_AI_PROFILE, v)

    val captionDefault get() = getPrefBoolean(KEY_CAPTION_DEFAULT, false)
    val autoTranslate get() = getPrefBoolean(KEY_AUTO_TRANSLATE, true)

    var tgtLang: String
        get() = getPrefString(KEY_TGT_LANG, "zh-CN")
        set(v) = putPref(KEY_TGT_LANG, v)

    val videoCache get() = getPrefBoolean(KEY_VIDEO_CACHE, true)
    val videoCacheMb get() = getPrefInt(KEY_VIDEO_CACHE_SIZE, 500)

    var readerTheme: Int            // 0=白天 1=护眼 2=夜间 3=AMOLED
        get() = getPrefInt(KEY_READER_THEME, 0)
        set(v) = putPref(KEY_READER_THEME, v)
}
