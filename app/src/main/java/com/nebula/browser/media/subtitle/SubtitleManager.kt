package com.nebula.browser.media.subtitle

import com.nebula.browser.common.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/** 单条字幕 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String, var translation: String? = null)

/** 字幕轨集合 */
data class SubtitleTrack(val cues: List<SubtitleCue>, val srcLang: String, var targetLang: String?)

class SubtitleManager {
    private val _current = MutableStateFlow<SubtitleTrack?>(null)
    val current = _current.asStateFlow()

    private val client = OkHttpClient()

    fun parseSrt(content: String): SubtitleTrack {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r", "").split("\n\n")
        for (b in blocks) {
            val lines = b.lines().filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timeLine = lines[1]
            val ms = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3}) --> (\d{2}):(\d{2}):(\d{2}),(\d{3})""").find(timeLine)
            if (ms == null) continue
            val s = ms.groupValues
            val start = (s[1].toInt() * 3600 + s[2].toInt() * 60 + s[3].toInt()) * 1000L + s[4].toInt()
            val end = (s[5].toInt() * 3600 + s[6].toInt() * 60 + s[7].toInt()) * 1000L + s[8].toInt()
            val text = lines.drop(2).joinToString("\n")
            cues.add(SubtitleCue(start, end, text))
        }
        return SubtitleTrack(cues, "auto", null)
    }

    fun parseVtt(content: String): SubtitleTrack {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r", "").split("\n\n")
        for (b in blocks) {
            val lines = b.lines().filter { it.isNotEmpty() && !it.contains("-->") }
            val ms = Regex("""(\d{2}):(\d{2}):(\d{2})\.(\d{3}) --> (\d{2}):(\d{2}):(\d{2})\.(\d{3})""").find(b)
            if (ms == null) continue
            val s = ms.groupValues
            val start = (s[1].toInt() * 3600 + s[2].toInt() * 60 + s[3].toInt()) * 1000L + s[4].toInt()
            val end = (s[5].toInt() * 3600 + s[6].toInt() * 60 + s[7].toInt()) * 1000L + s[8].toInt()
            val text = lines.joinToString("\n")
            cues.add(SubtitleCue(start, end, text))
        }
        return SubtitleTrack(cues, "auto", null)
    }

    fun forTime(track: SubtitleTrack, ms: Long): SubtitleCue? =
        track.cues.firstOrNull { ms in it.startMs..it.endMs }

    suspend fun loadOnline(url: String): SubtitleTrack? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().body?.string().orEmpty()
            val track = if (body.contains("-->")) parseVtt(body) else parseSrt(body)
            _current.value = track
            track
        } catch (e: Exception) { null }
    }

    suspend fun translateIfNeeded(track: SubtitleTrack, targetLang: String): SubtitleTrack = withContext(Dispatchers.IO) {
        val dao = com.nebula.browser.store.AppDatabase.get(AppContext.appContext).subtitleDao()
        for (cue in track.cues) {
            if (cue.translation != null) continue
            val hash = sha1(cue.text + cue.text + targetLang)
            val cached = dao.get(hash)
            if (cached != null) {
                cue.translation = cached.translatedText
                continue
            }
            val translated = Translator.translate(cue.text, targetLang)
            cue.translation = translated
            dao.insert(com.nebula.browser.store.SubtitleTranslationEntity(
                hash = hash, srcLang = "auto", tgtLang = targetLang, translatedText = translated
            ))
        }
        track.targetLang = targetLang
        track
    }
}

object Translator {
    private val client = OkHttpClient()

    /** 第一优先：Google Translate 非官方免费接口；失败则尝试 DeepSeek 兜底 */
    suspend fun translate(text: String, targetLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        try { googleTranslate(text, targetLang) }
        catch (e: Exception) { try { deepSeekFallback(text, targetLang) } catch (_: Exception) { text } }
    }

    private fun googleTranslate(text: String, targetLang: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=" + targetLang +
            "&q=" + java.net.URLEncoder.encode(text, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        val body = client.newCall(req).execute().body?.string().orEmpty()
        // body 为 [[["译文","原文",...],...], [srcLang], ...]
        val arr = Json.decodeFromString<kotlinx.serialization.json.JsonArray>(body)
        val sb = StringBuilder()
        arr[0].let { out ->
            if (out is kotlinx.serialization.json.JsonArray) {
                out.forEach { pair ->
                    if (pair is kotlinx.serialization.json.JsonArray) {
                        pair.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            ?.let { sb.append(it) }
                    }
                }
            }
        }
        return sb.toString()
    }

    private suspend fun deepSeekFallback(text: String, targetLang: String): String {
        val api = com.nebula.browser.ai.client.OpenAiClient()
        return api.completeOnceSilentMode("将下面文字翻译为$targetLang，只输出翻译结果，不要解释：$text",
            systemPrompt = "你是一个翻译引擎，输出纯净译文。")
    }

    private fun sha1(text: String): String {
        val m = MessageDigest.getInstance("SHA-1")
        return m.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
