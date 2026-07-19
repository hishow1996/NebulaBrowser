package com.nebula.browser.userscript.model

import kotlinx.serialization.Serializable

@Serializable
data class UserScriptMeta(
    val name: String = "",
    val namespace: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val matches: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val requires: List<String> = emptyList(),
    val resources: Map<String, String> = emptyMap(),
    val grants: List<String> = emptyList(),
    val runAt: String = "document-idle",    // document-start/end/idle
    val noframes: Boolean = false,
    val icon: String = "",
    val updateUrl: String = "",
    val downloadUrl: String = "",
    val sourceUrl: String = ""
)

data class UserScript(
    val id: String,             // namespace + name 的SHA1
    val meta: UserScriptMeta,
    val code: String,            // 完整脚本（含元数据块）
    val enabled: Boolean = true
)
