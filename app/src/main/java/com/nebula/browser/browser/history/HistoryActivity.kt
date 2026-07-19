package com.nebula.browser.browser.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.browser.UrlRouter
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.DialogBrowserMenuBinding
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.HistoryEntity
import kotlinx.coroutines.launch

/**
 * 历史记录页。
 *
 * - 列出最近 500 条访问记录（图标 + 标题 + URL）
 * - 点击：在浏览器当前标签页打开
 * - 长按：删除单条
 * - 顶部搜索框：实时过滤（标题或 URL 匹配）
 * - ActionBar 菜单：清空全部
 */
class HistoryActivity : AppCompatActivity() {

    private val dao get() = AppDatabase.get(this).historyDao()
    private val adapter = HistoryAdapter()
    private var all: List<HistoryEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.menu_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val search = EditText(this).apply {
            hint = getString(R.string.history_search_hint)
            setSingleLine(true)
            setPadding(48, 32, 48, 32)
            background = null
        }
        container.addView(search, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            this.adapter = this@HistoryActivity.adapter
            setPadding(0, 0, 0, 80)
        }
        container.addView(rv, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(container)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                applyFilter(if (q.isEmpty()) null else q)
            }
        })

        lifecycleScope.launch {
            dao.getAll().collect { list ->
                all = list
                applyFilter(null)
            }
        }
    }

    private fun applyFilter(q: String?) {
        if (q == null) {
            adapter.submit(all)
        } else {
            lifecycleScope.launch {
                adapter.submit(dao.search("%$q%"))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(android.view.Menu.NONE, 0, 0, getString(R.string.history_clear_all))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            0 -> clearAll()
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun clearAll() {
        lifecycleScope.launch {
            dao.clear()
            toast(getString(R.string.history_cleared))
        }
    }

    private fun openUrl(url: String) {
        UrlRouter.offer(url)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: android.content.Intent().apply {
                setClassName(packageName, "com.nebula.browser.MainActivity")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
        finish()
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        private val items = mutableListOf<HistoryEntity>()
        fun submit(list: List<HistoryEntity>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val b = DialogBrowserMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {}
        }
        override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, p: Int) {
            val b = DialogBrowserMenuBinding.bind(h.itemView)
            val item = items[p]
            b.itemIcon.setImageResource(R.drawable.ic_history)
            b.itemText.text = "${item.title}\n${item.url}"
            b.itemSwitch.visibility = View.GONE
            b.root.setOnClickListener { openUrl(item.url) }
            b.root.setOnLongClickListener {
                lifecycleScope.launch {
                    dao.delete(item.id)
                    toast(getString(R.string.history_deleted))
                }
                true
            }
        }
        override fun getItemCount() = items.size
    }
}
