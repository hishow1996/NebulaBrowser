package com.nebula.browser.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Chrome 扩展 manifest.json 数据模型。兼容 manifest_version 2 与 3。
 * 参照 Kiwi Browser / Chromium 扩展规范。
 */
@Serializable
data class WebExtensionManifest(
    val name: String = "",
    val version: String = "1.0",
    val description: String = "",
    val author: String? = null,

    @SerialName("manifest_version")
    val manifestVersion: Int = 2,

    @SerialName("minimum_chrome_version")
    val minimumChromeVersion: String? = null,

    val permissions: List<String> = emptyList(),
    @SerialName("optional_permissions")
    val optionalPermissions: List<String> = emptyList(),
    @SerialName("host_permissions")
    val hostPermissions: List<String> = emptyList(),

    val background: Background? = null,
    @SerialName("content_scripts")
    val contentScripts: List<ContentScriptDeclaration> = emptyList(),

    @SerialName("browser_action")
    val browserAction: ActionDef? = null,
    val action: ActionDef? = null,                  // MV3 替代 browser_action

    @SerialName("options_ui")
    val optionsUi: OptionsUi? = null,
    @SerialName("options_page")
    val optionsPage: String? = null,

    val icons: Map<String, String> = emptyMap(),
    @SerialName("web_accessible_resources")
    val webAccessibleResources: List<JsonElement> = emptyList(),
    @SerialName("content_security_policy")
    val contentSecurityPolicy: JsonElement? = null,
    @SerialName("homepage_url")
    val homepageUrl: String? = null,
    @SerialName("devtools_page")
    val devtoolsPage: String? = null,

    val omnibox: Omnibox? = null,
    @SerialName("chrome_url_overrides")
    val chromeUrlOverrides: Map<String, String> = emptyMap(),
    @SerialName("commands")
    val commands: Map<String, Command> = emptyMap(),
    val storage: JsonElement? = null,
    val default_locale: String? = null
)

@Serializable
data class Background(
    val scripts: List<String> = emptyList(),
    val page: String? = null,
    @SerialName("service_worker")
    val serviceWorker: String? = null,
    val type: String? = null,
    val persistent: Boolean = false
)

@Serializable
data class ContentScriptDeclaration(
    val matches: List<String> = emptyList(),
    @SerialName("exclude_matches")
    val excludeMatches: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    @SerialName("run_at")
    val runAt: String = "document_idle",          // document_start/end/idle
    @SerialName("all_frames")
    val allFrames: Boolean = false,
    @SerialName("include_globs")
    val includeGlobs: List<String> = emptyList(),
    @SerialName("exclude_globs")
    val excludeGlobs: List<String> = emptyList(),
    @SerialName("match_about_blank")
    val matchAboutBlank: Boolean = false
)

@Serializable
data class ActionDef(
    @SerialName("default_popup")
    val defaultPopup: String? = null,
    @SerialName("default_title")
    val defaultTitle: String = "",
    @SerialName("default_icon")
    val defaultIcon: JsonElement? = null,            // string 或 map<string,string>
    @SerialName("default_state")
    val defaultState: String? = null
)

@Serializable
data class OptionsUi(@SerialName("page") val page: String,
                    @SerialName("open_in_tab") val openInTab: Boolean = false)

@Serializable
data class Omnibox(@SerialName("keyword") val keyword: String)

@Serializable
data class Command(val suggested_key: JsonElement? = null,
                  val description: String = "",
                  val global: Boolean = false)
