package com.nebula.browser.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.FragmentSettingsBinding
import com.nebula.browser.databinding.ItemMenuSheetBinding
import com.nebula.browser.store.SettingsManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = FragmentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = getString(R.string.nav_settings)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.settingsRecycler.layoutManager = LinearLayoutManager(this)
        binding.settingsRecycler.adapter = SettingsAdapter(buildItems())
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_settings, c, false)
        if (v is RecyclerView) {
            v.layoutManager = LinearLayoutManager(requireContext())
            v.adapter = SettingsAdapter(buildItems())
        }
        return v
    }
}

data class SettingItem(
    val icon: Int, val title: String, val desc: String,
    val type: Type = Type.CLICK, val action: String,
    var checked: Boolean = false
) {
    enum class Type { CLICK, SWITCH }
}

class SettingsAdapter(val items: List<SettingItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val b = ItemMenuSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return object : RecyclerView.ViewHolder(b.root) {}
    }
    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val item = items[pos]
        val binding = ItemMenuSheetBinding.bind(h.itemView)
        binding.itemIcon.setImageResource(item.icon)
        binding.itemText.text = item.title
        if (item.type == SettingItem.Type.SWITCH) {
            binding.itemSwitch.visibility = View.VISIBLE
            binding.itemSwitch.isChecked = item.checked
            binding.root.setOnClickListener {
                val newState = !item.checked
                item.checked = newState
                binding.itemSwitch.isChecked = newState
                applyToggle(item.action, h.itemView.context, newState)
            }
        } else {
            binding.itemSwitch.visibility = View.GONE
            binding.root.setOnClickListener { handleClick(item.action, h.itemView, h.itemView.context) }
        }
    }
    override fun getItemCount() = items.size

    private fun applyToggle(action: String, ctx: Context, value: Boolean) {
        when (action) {
            "block_ads" -> com.nebula.browser.common.putPref(SettingsManager.KEY_ADS_BLOCK, value)
            "save_history" -> com.nebula.browser.common.putPref(SettingsManager.KEY_SAVE_HISTORY, value)
            "js" -> com.nebula.browser.common.putPref(SettingsManager.KEY_JAVASCRIPT, value)
            "dark_web" -> com.nebula.browser.common.putPref(SettingsManager.KEY_DARK_WEB, value)
            "float_global" -> com.nebula.browser.common.putPref(SettingsManager.KEY_FLOAT_GLOBAL, value)
            "auto_quality" -> com.nebula.browser.common.putPref(SettingsManager.KEY_AUTO_QUALITY, value)
            "saver_mode" -> com.nebula.browser.common.putPref(SettingsManager.KEY_SAVER, value)
            "caption_default" -> com.nebula.browser.common.putPref(SettingsManager.KEY_CAPTION_DEFAULT, value)
            "auto_translate" -> com.nebula.browser.common.putPref(SettingsManager.KEY_AUTO_TRANSLATE, value)
            "video_cache" -> com.nebula.browser.common.putPref(SettingsManager.KEY_VIDEO_CACHE, value)
        }
    }

    private fun handleClick(action: String, v: View, ctx: Context) {
        when (action) {
            "theme_day" -> { SettingsManager.themeMode = SettingsManager.THEME_DAY; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
            "theme_night" -> { SettingsManager.themeMode = SettingsManager.THEME_NIGHT; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
            "theme_system" -> { SettingsManager.themeMode = SettingsManager.THEME_SYSTEM; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            "clear_cache" -> { ctx.cacheDir.deleteRecursively(); toast("已清除缓存") }
            "about" -> toast("星云浏览器 v1.0.0")
            else -> toast("功能开发中")
        }
    }
}

private fun android.content.Context.cacheDir.deleteRecursively() {
    try { this.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
}

private fun buildItems() = listOf(
    SettingItem(R.drawable.ic_sun, "主题：白天", "切换为白天模式", SettingItem.Type.CLICK, "theme_day",
        com.nebula.browser.store.SettingsManager.themeMode == SettingsManager.THEME_DAY),
    SettingItem(R.drawable.ic_moon, "主题：夜间", "切换为夜间模式", SettingItem.Type.CLICK, "theme_night",
        com.nebula.browser.store.SettingsManager.themeMode == SettingsManager.THEME_NIGHT),
    SettingItem(R.drawable.ic_settings, "主题：跟随系统", "切换主题跟随系统设置", SettingItem.Type.CLICK, "theme_system",
        com.nebula.browser.store.SettingsManager.themeMode == SettingsManager.THEME_SYSTEM),

    SettingItem(R.drawable.ic_incognito, "广告拦截", "屏蔽常见广告网络", SettingItem.Type.SWITCH, "block_ads",
        com.nebula.browser.store.SettingsManager.adBlock),
    SettingItem(R.drawable.ic_history, "保存浏览历史", "可在历史记录中查看", SettingItem.Type.SWITCH, "save_history",
        com.nebula.browser.store.SettingsManager.saveHistory),
    SettingItem(R.drawable.ic_userscript, "启用 JavaScript", "允许网页运行 JavaScript", SettingItem.Type.SWITCH, "js",
        com.nebula.browser.store.SettingsManager.javascript),
    SettingItem(R.drawable.ic_moon, "强制网页夜间模式", "对所有网页应用暗色 CSS", SettingItem.Type.SWITCH, "dark_web",
        com.nebula.browser.store.SettingsManager.darkWeb),
    SettingItem(R.drawable.ic_video, "悬浮视频全局显示", "可在桌面与任意 App 上悬浮显示", SettingItem.Type.SWITCH, "float_global",
        com.nebula.browser.store.SettingsManager.floatGlobal),

    SettingItem(R.drawable.ic_quality, "网速自适应画质", "根据网速自动切换清晰度", SettingItem.Type.SWITCH, "auto_quality",
        com.nebula.browser.store.SettingsManager.autoQuality),
    SettingItem(R.drawable.ic_quality, "省流模式", "限制最高 480P", SettingItem.Type.SWITCH, "saver_mode",
        com.nebula.browser.store.SettingsManager.saveMode),

    SettingItem(R.drawable.ic_caption, "默认显示字幕", "视频打开时自动启用字幕", SettingItem.Type.SWITCH, "caption_default",
        com.nebula.browser.store.SettingsManager.captionDefault),
    SettingItem(R.drawable.ic_translate, "自动翻译字幕", "Google + DeepSeek 兜底", SettingItem.Type.SWITCH, "auto_translate",
        com.nebula.browser.store.SettingsManager.autoTranslate),
    SettingItem(R.drawable.ic_quality, "启用视频缓存", "LRU 缓存最近观看视频", SettingItem.Type.SWITCH, "video_cache",
        com.nebula.browser.store.SettingsManager.videoCache),

    SettingItem(R.drawable.ic_close, "清除缓存", "释放存储空间", SettingItem.Type.CLICK, "clear_cache"),
    SettingItem(R.drawable.ic_settings, "关于", "星云浏览器 v1.0.0", SettingItem.Type.CLICK, "about")
)
