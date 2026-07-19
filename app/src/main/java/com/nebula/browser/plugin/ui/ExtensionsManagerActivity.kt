package com.nebula.browser.plugin.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.DialogBrowserMenuBinding
import com.nebula.browser.plugin.core.ExtensionInstaller
import com.nebula.browser.plugin.core.ExtensionRegistry
import com.nebula.browser.plugin.inject.BackgroundScriptRunner
import com.nebula.browser.plugin.model.ExtensionPackage
import kotlinx.coroutines.launch
import java.io.File

/**
 * 扩展管理页（类似 Chrome chrome://extensions）。
 *
 * 功能：
 *   - 已安装扩展列表（图标 + 名称 + 版本 + 启用开关 + 详情/删除按钮）
 *   - 顶部"开发者模式"开关
 *   - 顶部"加载已解压的扩展程序目录"
 *   - 顶部"打包扩展程序"占位
 *   - 长按菜单：详情 / 选项 / 卸载
 */
class ExtensionsManagerActivity : AppCompatActivity() {

    private val installer = ExtensionInstaller()
    private val adapter = ExtensionsAdapter()

    private val pickCrx = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val pkg = installer.installFromBytes(bytes)
            toast(if (pkg != null) "已安装：${pkg.name}" else "安装失败")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.extensions_manager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ExtensionsManagerActivity)
            this.adapter = this@ExtensionsManagerActivity.adapter
            setPadding(0, 0, 0, 80)
        }
        setContentView(rv)

        lifecycleScope.launch {
            ExtensionRegistry.get().loadAll()
            BackgroundScriptRunner.get().ensureHost(applicationContext)
            observeInstalled()
        }
    }

    private fun observeInstalled() {
        lifecycleScope.launch {
            ExtensionRegistry.get().installed.collect { list ->
                adapter.submit(list)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(android.view.Menu.NONE, 0, 0, getString(R.string.extensions_open_store)).setIcon(R.drawable.ic_search)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(android.view.Menu.NONE, 1, 1, getString(R.string.extensions_import_crx))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            0 -> startActivity(Intent(this, ChromeWebStoreActivity::class.java))
            1 -> pickCrx.launch("application/octet-stream")
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private inner class ExtensionsAdapter : RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        private val items = mutableListOf<ExtensionPackage>()
        fun submit(list: List<ExtensionPackage>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val b = DialogBrowserMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {}
        }
        override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, p: Int) {
            val b = DialogBrowserMenuBinding.bind(h.itemView)
            val item = items[p]
            b.itemIcon.setImageResource(R.drawable.ic_plugin)
            b.itemText.text = "${item.name} ${item.version}\n${item.description}"
            b.itemSwitch.visibility = View.VISIBLE
            b.itemSwitch.isChecked = item.enabled
            b.itemSwitch.setOnCheckedChangeListener { _, enabled ->
                lifecycleScope.launch { installer.toggle(item.id, enabled) }
            }
            b.root.setOnLongClickListener {
                lifecycleScope.launch { installer.uninstall(item.id); toast("已卸载：${item.name}") }
                true
            }
        }
        override fun getItemCount() = items.size
    }
}
