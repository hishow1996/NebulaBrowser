package com.nebula.browser.userscript.parser

import com.nebula.browser.userscript.model.UserScript
import com.nebula.browser.userscript.model.UserScriptMeta
import java.security.MessageDigest

object UserScriptParser {
    private val BLOCK_START = Regex("""//\s*==UserScript==""")
    private val BLOCK_END = Regex("""//\s*==/UserScript==""")
    private val LINE = Regex("""//\s*@(\S+)\s+([^\r\n]*)""")

    fun parse(source: String, sourceUrl: String = ""): UserScript {
        val meta = parseMeta(source)
        val id = sha1("${meta.namespace}/${meta.name}")
        return UserScript(id = id, meta = meta.copy(sourceUrl = sourceUrl), code = source)
    }

    fun parseMeta(source: String): UserScriptMeta {
        val sIdx = BLOCK_START.find(source)?.range?.last ?: -1
        val eIdx = BLOCK_END.find(source)?.range?.first ?: -1
        if (sIdx < 0 || eIdx < 0) return UserScriptMeta()

        val metaBlock = source.substring(sIdx + 1, eIdx)
        val pairs = mutableListOf<Pair<String, String>>()
        for (m in LINE.findAll(metaBlock)) {
            val key = m.groupValues[1]
            val value = m.groupValues[2].trim()
            if (key.isNotEmpty()) pairs.add(key to value)
        }

        return UserScriptMeta(
            name = pairs.firstOrNull { it.first == "name" }?.second ?: "",
            namespace = pairs.firstOrNull { it.first == "namespace" }?.second ?: "",
            version = pairs.firstOrNull { it.first == "version" }?.second ?: "",
            author = pairs.firstOrNull { it.first == "author" }?.second ?: "",
            description = pairs.firstOrNull { it.first == "description" }?.second ?: "",
            matches = pairs.filter { it.first == "match" }.map { it.second },
            includes = pairs.filter { it.first == "include" }.map { it.second },
            excludes = pairs.filter { it.first == "exclude" }.map { it.second },
            requires = pairs.filter { it.first == "require" }.map { it.second },
            resources = pairs.filter { it.first == "resource" }.associate {
                val parts = it.second.split(" ", limit = 2)
                parts[0] to parts.getOrNull(1).orEmpty()
            },
            grants = pairs.filter { it.first == "grant" }.map { it.second },
            runAt = pairs.firstOrNull { it.first == "run-at" }?.second ?: "document-idle",
            noframes = pairs.any { it.first == "noframes" },
            icon = pairs.firstOrNull { it.first == "icon" }?.second ?: "",
            updateUrl = pairs.firstOrNull { it.first == "updateURL" }?.second ?: "",
            downloadUrl = pairs.firstOrNull { it.first == "downloadURL" }?.second ?: ""
        )
    }

    fun stripMeta(source: String): String {
        val s = BLOCK_START.find(source)?.range?.start ?: return source
        val e = BLOCK_END.find(source)?.range?.endInclusive ?: return source
        return source.removeRange(s, e + 1).trim()
    }

    private fun sha1(text: String): String {
        val m = MessageDigest.getInstance("SHA-1")
        return m.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
