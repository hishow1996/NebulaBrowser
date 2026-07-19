package com.nebula.browser.userscript.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.ItemMenuSheetBinding
import com.nebula.browser.store.UserScriptEntity
import com.nebula.browser.userscript.store.UserScriptStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserScriptManagerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.menu_userscript)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@UserScriptManagerActivity)
            adapter = ScriptAdapter(emptyList()) { _ -> toast(getString(R.string.userscript_disabled)) }
        }
        setContentView(rv)
        lifecycleScope.launch {
            UserScriptStore.observe(this@UserScriptManagerActivity).collectLatest { list ->
                (rv.adapter as ScriptAdapter).update(list)
            }
        }
    }

    private inner class ScriptAdapter(
        var items: List<UserScriptEntity>,
        private val onToggle: (UserScriptEntity) -> Unit
    ) : RecyclerView.Adapter<ViewHolder>() {
        fun update(newItems: List<UserScriptEntity>) { items = newItems; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemMenuSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(h: ViewHolder, position: Int) {
            val item = items[position]
            h.b.itemIcon.setImageResource(R.drawable.ic_userscript)
            h.b.itemText.text = item.name
            h.b.itemSwitch.visibility = View.VISIBLE
            h.b.itemSwitch.isChecked = item.enabled
            h.b.root.setOnClickListener {
                onToggle(item)
                h.itemView.post {
                    lifecycleScope.launch {
                        UserScriptStore.toggle(item.id, !item.enabled)
                        toast(if (!item.enabled) getString(R.string.userscript_install_success,
                                item.name) else getString(R.string.userscript_disable))
                    }
                }
            }
        }
        override fun getItemCount() = items.size
    }

    inner class ViewHolder(val b: ItemMenuSheetBinding) : RecyclerView.ViewHolder(b.root)

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
