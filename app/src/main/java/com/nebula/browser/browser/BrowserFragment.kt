package com.nebula.browser.browser

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nebula.browser.R
import com.nebula.browser.browser.menu.BrowserMenuSheet
import com.nebula.browser.browser.tab.TabManager
import com.nebula.browser.browser.tab.Tab
import com.nebula.browser.browser.webview.NebulaWebView
import com.nebula.browser.common.UrlUtil
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.FragmentBrowserBinding
import com.nebula.browser.databinding.ItemHomeQuickBinding
import com.nebula.browser.media.floating.FloatingVideoService
import com.nebula.browser.search.EngineRegistry
import kotlinx.coroutines.launch

private data class QuickSite(val letter: String, val name: String, val url: String, val color: Int)

class BrowserFragment : Fragment() {

    private var _b: FragmentBrowserBinding? = null
    private val b get() = _b!!
    private val tabManager = TabManager.get()
    private lateinit var vm: BrowserViewModel
    private var overlayPermissionLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null
    private var pendingFloatingVideoUrl: String? = null

    private val quickSites = listOf(
        QuickSite("G", "Google", "https://www.google.com", 0xFF4285F4.toInt()),
        QuickSite("Y", "YouTube", "https://www.youtube.com", 0xFFFF0000.toInt()),
        QuickSite("W", "Wikipedia", "https://www.wikipedia.org", 0xFF1A1A1A.toInt()),
        QuickSite("f", "Facebook", "https://www.facebook.com", 0xFF1877F2.toInt()),
        QuickSite("Ig", "Instagram", "https://www.instagram.com", 0xFFE1306C.toInt()),
        QuickSite("X", "X", "https://x.com", 0xFF000000.toInt()),
        QuickSite("r", "Reddit", "https://www.reddit.com", 0xFFFF4500.toInt()),
        QuickSite("a", "Amazon", "https://www.amazon.com", 0xFFFF9900.toInt()),
        QuickSite("N", "Netflix", "https://www.netflix.com", 0xFFE50914.toInt()),
        QuickSite("G", "Gmail", "https://mail.google.com", 0xFFEA4335.toInt())
    )

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBrowserBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this)[BrowserViewModel::class.java]

        // 地址栏
        b.urlBar.imeOptions = EditorInfo.IME_ACTION_GO
        b.urlBar.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        b.engineIcon.setOnClickListener { showEngineSwitcher() }

        // 底部工具栏
        b.btnBack.setOnClickListener { tabManager.current?.webview?.goBack() }
        b.btnForward.setOnClickListener { tabManager.current?.webview?.goForward() }
        b.btnHome.setOnClickListener { showHome() }
        b.btnTabs.setOnClickListener { toast(getString(R.string.tab_count, tabManager.size)) }
        b.btnMore.setOnClickListener { showMoreMenu() }
        b.fabDetectVideo.setOnClickListener { detectVideo() }

        // 悬浮播放权限引导 → 授权后启动 FloatingVideoService
        overlayPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) startFloatingPlayback()
            else toast(getString(R.string.permission_overlay_required))
        }
        // 主页交互
        b.homeMenu.setOnClickListener { showMoreMenu() }
        b.homeQrcode.setOnClickListener { toast(getString(R.string.menu_qrcode)) }
        b.homeSearchBar.setOnClickListener { enterSearchMode() }
        b.homeVoice.setOnClickListener { toast(getString(R.string.menu_qrcode)) }
        b.homeLiteSwitch.isChecked = com.nebula.browser.media.saver.DataSaverBus.enabled.value
        b.homeLiteSwitch.setOnCheckedChangeListener { _, c ->
            com.nebula.browser.media.saver.DataSaverBus.setEnabled(c)
            toast(getString(R.string.home_data_saver_on, c))
        }
        observeSaverStats()

        buildQuickNav()

        ensureFirstTab()
        observeTab()
        updateHomeVisibility()
    }

    /** 实时数据节省统计：反映到主页 Lite 卡片副标题。 */
    private fun observeSaverStats() {
        // 在 fragment 生命周期内订阅的简单回调式刷新（不依赖额外导入）
        kotlinx.coroutines.MainScope().launch {
            com.nebula.browser.media.saver.DataSaverBus.enabled.collect { en ->
                b.homeLiteSwitch.setOnCheckedChangeListener(null)
                b.homeLiteSwitch.isChecked = en
                b.homeLiteSwitch.setOnCheckedChangeListener { _, c ->
                    com.nebula.browser.media.saver.DataSaverBus.setEnabled(c)
                    toast(getString(R.string.home_data_saver_on, c))
                }
            }
        }
    }

    private fun buildQuickNav() {
        b.quickNavBar.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.quickNavBar.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_home_quick, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {}
            }

            override fun getItemCount() = quickSites.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val item = ItemHomeQuickBinding.bind(holder.itemView)
                val site = quickSites[position]
                item.quickLetter.text = site.letter
                item.quickName.text = site.name
                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    cornerRadius = 56f
                    setColor(site.color)
                }
                item.quickBg.background = circle
                item.root.setOnClickListener { openUrl(site.url) }
            }
        }
    }

    // ============ 模式切换 ============

    private fun enterSearchMode() {
        showBrowserUi()
        b.urlBar.post {
            b.urlBar.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(b.urlBar, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun openUrl(url: String) {
        val tab = tabManager.current ?: tabManager.new()
        if (tab.webview == null) createWebView(tab)
        tabManager.update(tab.id) { it.url = url }
        tab.webview?.loadUrl(url)
        showBrowserUi()
    }

    private fun openUrlSearch(query: String) {
        val engine = EngineRegistry.current()
        val tpl = engine?.urlTemplate ?: "https://www.google.com/search?q=%s"
        openUrl(UrlUtil.toUrlOrSearch(query, tpl))
    }

    private fun showHome() {
        val tab = tabManager.current
        if (tab != null) {
            tabManager.update(tab.id) { it.url = "about:blank" }
            tab.webview?.loadUrl("about:blank")
        }
        showHomeUi()
    }

    private fun showHomeUi() {
        b.homeRoot.visibility = View.VISIBLE
        b.urlBarContainer.visibility = View.GONE
        b.urlProgressBar.visibility = View.GONE
        b.swipe.visibility = View.GONE
        b.fabDetectVideo.visibility = View.GONE
        b.urlBar.setText("")
    }

    private fun showBrowserUi() {
        b.homeRoot.visibility = View.GONE
        b.urlBarContainer.visibility = View.VISIBLE
        b.swipe.visibility = View.VISIBLE
        if (tabManager.current?.url != "about:blank") b.fabDetectVideo.visibility = View.VISIBLE
    }

    private fun updateHomeVisibility() {
        val tab = tabManager.current
        val onHome = tab == null || tab.url == "about:blank" || tab.url.isEmpty()
        if (onHome) showHomeUi() else showBrowserUi()
    }

    // ============ 标签页管理 ============

    private fun ensureFirstTab() {
        if (tabManager.size == 0) {
            tabManager.new().also { createWebView(it) }
        } else {
            attachWebView(tabManager.current!!)
        }
    }

    private fun createWebView(tab: Tab) {
        val wv = NebulaWebView(requireContext()).apply {
            setup()
            tag = tab
            onVideoDetected = { _, _, _ -> }
        }
        tab.webview = wv
        attachWebView(tab)
    }

    private fun attachWebView(tab: Tab) {
        b.webContainer.removeAllViews()
        tab.webview?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            b.webContainer.addView(
                it,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun observeTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            tabManager.tabs.collect {
                b.urlBar.setText(tabManager.current?.url ?: "")
                updateHomeVisibility()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            tabManager.currentIdx.collect { idx ->
                val cur = tabManager.tabs.value.getOrNull(idx)
                cur?.let {
                    b.urlBar.setText(it.url)
                    attachWebView(it)
                    updateHomeVisibility()
                }
            }
        }
    }

    // ============ 操作 ============

    private fun go() {
        val text = b.urlBar.text.toString().trim()
        if (text.isEmpty()) return
        openUrlSearch(text)
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
            BrowserMenuSheet.MenuItem(R.drawable.ic_video, getString(R.string.menu_floating_video), "floating_video"),
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
                "floating_video" -> requestFloatingPlayback()
                "download" -> openActivity("com.nebula.browser.browser.downloader.DownloadListActivity")
                "bookmark" -> toast(getString(R.string.menu_bookmark))
                "history" -> toast(getString(R.string.menu_history))
                "settings" -> openActivity("com.nebula.browser.settings.SettingsActivity")
                "incognito" -> {
                    val t = tabManager.new(incognito = true)
                    createWebView(t)
                }
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
        val url = list.first()
        // 直接打开内置播放器（带画质 chip）。如果用户已经在悬浮窗模式，
        // 可以在更多菜单里选择悬浮播放。
        val intent = android.content.Intent(requireContext(),
            com.nebula.browser.media.player.PlayerActivity::class.java)
        intent.putExtra("video_url", url)
        intent.putExtra("title", tabManager.current?.title ?: "")
        startActivity(intent)
    }

    /** 用户点击「悬浮播放」菜单：若已获悬浮窗权限则直接启动；否则弹引导页。 */
    private fun requestFloatingPlayback() {
        val list = com.nebula.browser.media.detector.VideoDetectorInterceptor.recentList()
        if (list.isEmpty()) {
            toast(getString(R.string.video_no_video_found))
            return
        }
        pendingFloatingVideoUrl = list.first()
        if (com.nebula.browser.common.PermissionUtil.canDrawOverlays(requireContext())) {
            startFloatingPlayback()
        } else {
            val guide = com.nebula.browser.permission.PermissionGuideActivity.intent(
                requireContext(),
                type = "overlay",
                title = getString(R.string.permission_overlay_title),
                desc = getString(R.string.permission_overlay_desc),
                icon = com.nebula.browser.R.drawable.ic_video,
                btnText = getString(R.string.grant)
            )
            overlayPermissionLauncher?.launch(guide)
        }
    }

    private fun startFloatingPlayback() {
        val url = pendingFloatingVideoUrl
        if (url == null) { toast(getString(R.string.video_no_video_found)); return }
        val intent = android.content.Intent(requireContext(), FloatingVideoService::class.java)
        intent.putExtra("video_url", url)
        com.nebula.browser.common.AppContext.appContext.startForegroundService(intent)
        pendingFloatingVideoUrl = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
