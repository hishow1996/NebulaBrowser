package com.nebula.browser.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nebula.browser.R
import com.nebula.browser.browser.menu.BrowserMenuSheet
import com.nebula.browser.browser.tab.TabManager
import com.nebula.browser.browser.webview.NebulaWebView
import com.nebula.browser.common.UrlUtil
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.FragmentBrowserBinding
import com.nebula.browser.media.floating.FloatingVideoService
import com.nebula.browser.search.EngineRegistry
import com.nebula.browser.userscript.ui.UserScriptManagerActivity
import com.nebula.browser.plugin.ui.ExtensionsManagerActivity
import com.nebula.browser.settings.SettingsActivity
import com.nebula.browser.ai.ui.AiChatActivity
import com.nebula.browser.reader.shelf.AddBookActivity
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {
    private var _b: FragmentBrowserBinding? = null
    private val b get() = _b!!
    private val tabManager = TabManager.get()
    private lateinit var vm: BrowserViewModel

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBrowserBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this)[BrowserViewModel::class.java]
        b.urlBar.imeOptions = EditorInfo.IME_ACTION_GO
        b.urlBar.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        b.engineIcon.setOnClickListener { showEngineSwitcher() }

        b.btnBack.setOnClickListener { tabManager.current?.webview?.goBack() }
        b.btnForward.setOnClickListener { tabManager.current?.webview?.goForward() }
        b.btnHome.setOnClickListener { loadHome() }
        b.btnTabs.setOnClickListener { toast("标签页 ${tabManager.size}") }
        b.btnMore.setOnClickListener { showMoreMenu() }
        b.fabDetectVideo.setOnClickListener { detectVideo() }

        ensureFirstTab()
        observeTab()
    }

    private fun ensureFirstTab() {
        if (tabManager.size == 0) {
            tabManager.new().also { createWebView(it) }
        } else {
            attachWebView(tabManager.current!!)
        }
    }

    private fun createWebView(tab: com.nebula.browser.browser.tab.Tab) {
        val wv = NebulaWebView(requireContext()).apply {
            setup()
            tag = tab
            onVideoDetected = { _, _, _ -> }
        }
        tab.webview = wv
        attachWebView(tab)
    }

    private fun attachWebView(tab: com.nebula.browser.browser.tab.Tab) {
        b.webContainer.removeAllViews()
        tab.webview?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            b.webContainer.addView(it,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun observeTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            tabManager.tabs.collect { _ ->
                val cur = tabManager.current
                cur?.let {
                    b.urlBar.setText(it.url)
                    if (it.webview == null) createWebView(it)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            tabManager.currentIdx.collect { idx ->
                val cur = tabManager.tabs.value.getOrNull(idx)
                cur?.let {
                    b.urlBar.setText(it.url)
                    attachWebView(it)
                }
            }
        }
    }

    private fun go() {
        val text = b.urlBar.text.toString().trim()
        if (text.isEmpty()) return
        val engine = EngineRegistry.current()
        val url = UrlUtil.toUrlOrSearch(text, engine?.urlTemplate ?: "https://www.google.com/search?q=%s")
        val tab = tabManager.current ?: tabManager.new()
        tab.webview?.loadUrl(url)
    }

    private fun loadHome() {
        tabManager.current?.webview?.loadUrl("about:blank")
        b.urlBar.setText("")
    }

    private fun showEngineSwitcher() {
        val engines = EngineRegistry.list()
        val items = engines.map {
            BrowserMenuSheet.MenuItem(R.drawable.ic_search, it.name, "engine_${it.id}")
        }
        BrowserMenuSheet(requireContext(), items) { item ->
            val id = item.action.removePrefix("engine_")
            EngineRegistry.setCurrent(id)
        }.show()
    }

    private fun showMoreMenu() {
        val items = listOf(
            BrowserMenuSheet.MenuItem(R.drawable.ic_bookmark, getString(R.string.menu_bookmark), "bookmark"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_history, getString(R.string.menu_history), "history"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_download, getString(R.string.menu_download), "download"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_userscript, getString(R.string.menu_userscript), "userscript"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_plugin, "扩展程序", "extensions"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_plugin, "Chrome 商店", "webstore"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_ai, getString(R.string.menu_ai), "ai"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_translate, getString(R.string.menu_translate), "translate"),
            BrowserMenuSheet.MenuItem(R.drawable.ic_incognito, getString(R.string.menu_incognito),
                "incognito", checkable = true, checked = false),
            BrowserMenuSheet.MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings), "settings")
        )
        BrowserMenuSheet(requireContext(), items) { item ->
            when (item.action) {
                "userscript" -> openActivity("com.nebula.browser.userscript.ui.UserScriptManagerActivity")
                "extensions" -> openActivity("com.nebula.browser.plugin.ui.ExtensionsManagerActivity")
                "webstore" -> openActivity("com.nebula.browser.plugin.ui.ChromeWebStoreActivity")
                "ai" -> openActivity("com.nebula.browser.ai.ui.AiChatActivity")
                "translate" -> toast(getString(R.string.menu_translate))
                "download" -> openActivity("com.nebula.browser.browser.downloader.DownloadListActivity")
                "bookmark" -> toast(getString(R.string.menu_bookmark))
                "history" -> toast(getString(R.string.menu_history))
                "settings" -> openActivity("com.nebula.browser.settings.SettingsActivity")
                "incognito" -> { val t = tabManager.new(incognito = true); createWebView(t) }
            }
        }.show()
    }

    private fun openActivity(className: String) {
        try {
            val cls = Class.forName(className)
            startActivity(android.content.Intent(requireContext(), cls))
        } catch (e: Exception) {
            toast("功能开发中")
        }
    }

    private fun detectVideo() {
        val list = com.nebula.browser.media.detector.VideoDetectorInterceptor.recentList()
        if (list.isEmpty()) {
            toast(getString(R.string.video_no_video_found))
            return
        }
        toast(getString(R.string.video_detected, list.size))
        // 启动悬浮窗播放第一个
        val url = list.first()
        if (!com.nebula.browser.common.PermissionUtil.canDrawOverlays(requireContext())) {
            com.nebula.browser.common.PermissionUtil.requestOverlayPermission(requireActivity(), 0x101)
            toast(getString(R.string.video_request_overlay))
            return
        }
        val intent = android.content.Intent(requireContext(), FloatingVideoService::class.java)
        intent.putExtra("video_url", url)
        com.nebula.browser.common.AppContext.appContext.startForegroundService(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
