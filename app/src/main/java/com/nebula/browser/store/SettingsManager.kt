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
    const val KEY_SAVER_BITRATE = "saver_bitrate"
    const val KEY_SAVER_WIDTH = "saver_width"
    const val KEY_SAVER_AUTO = "saver_auto"
    const val KEY_DARK_WEB = "dark_web"
    const val KEY_FLOAT_GLOBAL = "float_global"
    const val KEY_AI_PROFILE = "ai_profile_id"
    const val KEY_CAPTION_DEFAULT = "caption_default"
    const val KEY_AUTO_TRANSLATE = "auto_translate"
    const val KEY_TGT_LANG = "tgt_lang"
    const val KEY_VIDEO_CACHE = "video_cache"
    const val KEY_VIDEO_CACHE_SIZE = "video_cache_mb"
    const val KEY_READER_THEME = "reader_theme"
    /** 网页内视频画质用户选择档位（0=自动 1=240 2=360 3=480 4=720 5=原画） */
    const val KEY_WEB_VIDEO_QUALITY = "web_video_quality"
    /** yt-dlp 二进制运行时下载地址（assets 缺失情况下从该 URL 下载并以可执行权限存到 filesDir）。 */
    const val KEY_YTDLP_URL = "ytdlp_binary_url"

    var themeMode: Int
        get() = AppContext.prefs.getInt(KEY_THEME_MODE, THEME_DAY)
        set(v) = putPref(KEY_THEME_MODE, v)

    var defaultEngineId: String
        get() = getPrefString(KEY_DEFAULT_ENGINE, "google")
        set(v) = putPref(KEY_DEFAULT_ENGINE, v)

    val adBlock get() = getPrefBoolean(KEY_ADS_BLOCK, true)
    val saveHistory get() = getPrefBoolean(KEY_SAVE_HISTORY, true)
    val javascript get() = getPrefBoolean(KEY_JAVASCRIPT, true)

    /** 网页视频画质压缩（数据节省）总开关 */
    var saveMode: Boolean
        get() = getPrefBoolean(KEY_SAVER, false)
        set(v) = putPref(KEY_SAVER, v)

    /** 目标码率（bps）。0 表示按目标宽度自动选 600kbps。 */
    var saverBitrate: Int
        get() = getPrefInt(KEY_SAVER_BITRATE, 600_000)
        set(v) = putPref(KEY_SAVER_BITRATE, v)

    /** 目标最大宽度（像素），默认 854（480P）。 */
    var saverWidth: Int
        get() = getPrefInt(KEY_SAVER_WIDTH, 854)
        set(v) = putPref(KEY_SAVER_WIDTH, v)

    /** 弱网自动启用（结合带宽采样）。 */
    var saverAuto: Boolean
        get() = getPrefBoolean(KEY_SAVER_AUTO, true)
        set(v) = putPref(KEY_SAVER_AUTO, v)

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

    /** 网页内视频画质用户选择（0=自动 1=240 2=360 3=480 4=720 5=原画） */
    var webVideoQuality: Int
        get() = getPrefInt(KEY_WEB_VIDEO_QUALITY, 0)
        set(v) = putPref(KEY_WEB_VIDEO_QUALITY, v)

    /**
     * yt-dlp 二进制下载 URL。默认留空——表示优先从 assets 取（assets/ytdlp/bin_arm64 等），
     * 找不到则要求用户在设置中填写一个 HTTP/HTTPS 直链（通常指向 GitHub Release 的预编译二进制）。
     */
    var ytdlpBinaryUrl: String
        get() = getPrefString(KEY_YTDLP_URL, "")
        set(v) = putPref(KEY_YTDLP_URL, v)
}
