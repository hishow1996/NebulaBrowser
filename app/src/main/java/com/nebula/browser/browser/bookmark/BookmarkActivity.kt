package com.nebula.browser.browser.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.browser.tab.TabManager
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.DialogBrowserMenuBinding
import com.nebula.browser.store.AppDatabase
import com.nebula.browser.store.BookmarkEntity
import kotlinx.coroutines.launch

/**
 * 书签管理页。
 *
 * - 列出所有书签（图标 + 标题 + 链接）
 * - 点击：在当前标签页打开
 * - 长按：删除单条
 * - ActionBar 菜单：加入当前页 / 清空全部
 */
class BookmarkActivity : AppCompatActivity() {

    private val dao get() = AppDatabase.get(this).bookmarkDao()
    private val adapter = BookmarkAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.menu_bookmark)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@BookmarkActivity)
            this.adapter = this@BookmarkActivity.adapter
            setPadding(0, 0, 0, 80)
        }
        setContentView(rv)

        lifecycleScope.launch {
            dao.getAll().collect { list ->
                adapter.submit(list)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(android.view.Menu.NONE, 0, 0, getString(R.string.bookmark_add_current))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(android.view.Menu.NONE, 1, 1, getString(R.string.bookmark_clear_all))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            0 -> addCurrent()
            1 -> clearAll()
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun addCurrent() {
        val tab = TabManager.get().current
        val url = tab?.url
        if (url.isNullOrBlank() || url == "about:blank") {
            toast(getString(R.string.bookmark_no_page))
            return
        }
        lifecycleScope.launch {
            if (dao.exists(url)) {
                toast(getString(R.string.bookmark_already_exists))
                return@launch
            }
            dao.add(BookmarkEntity(title = tab.title.ifBlank { url }, url = url))
            toast(getString(R.string.bookmark_added))
        }
    }

    private fun clearAll() {
        lifecycleScope.launch {
            dao.clear()
            toast(getString(R.string.bookmark_cleared))
        }
    }

    /** 通过 UrlRouter 把 URL 交给前台 MainActivity（singleTask）在当前标签页打开。 */
    private fun openUrl(url: String) {
        com.nebula.browser.browser.UrlRouter.offer(url)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply {
                setClassName(packageName, "com.nebula.browser.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
        finish()
    }

    private inner class BookmarkAdapter : RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        private val items = mutableListOf<BookmarkEntity>()
        fun submit(list: List<BookmarkEntity>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val b = DialogBrowserMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {}
        }
        override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, p: Int) {
            val b = DialogBrowserMenuBinding.bind(h.itemView)
            val item = items[p]
            b.itemIcon.setImageResource(R.drawable.ic_bookmark)
            b.itemText.text = "${item.title}\n${item.url}"
            b.itemSwitch.visibility = View.GONE
            b.root.setOnClickListener { openUrl(item.url) }
            b.root.setOnLongClickListener {
                lifecycleScope.launch {
                    dao.delete(item.id)
                    toast(getString(R.string.bookmark_deleted))
                }
                true
            }
        }
        override fun getItemCount() = items.size
    }
}
