package com.nebula.browser.browser.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nebula.browser.databinding.DialogBrowserMenuBinding
import com.nebula.browser.databinding.ItemMenuSheetBinding

class BrowserMenuSheet(
    private val host: android.content.Context,
    private val items: List<MenuItem>,
    private val onItem: (MenuItem) -> Unit
) {
    data class MenuItem(val icon: Int, val text: String, val action: String,
                        val checkable: Boolean = false, val checked: Boolean = false)

    private var dialog: BottomSheetDialog? = null
    private var view: DialogBrowserMenuBinding? = null

    fun show() {
        val v = DialogBrowserMenuBinding.inflate(LayoutInflater.from(host))
        view = v
        v.menuList.layoutManager = LinearLayoutManager(host)
        v.menuList.adapter = Adapter()

        val d = BottomSheetDialog(host)
        d.setContentView(v.root)
        this.dialog = d
        d.show()
    }

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemMenuSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.b.itemIcon.setImageResource(item.icon)
            h.b.itemText.text = item.text
            if (item.checkable) {
                h.b.itemSwitch.visibility = View.VISIBLE
                h.b.itemSwitch.isChecked = item.checked
            } else h.b.itemSwitch.visibility = View.GONE
            h.b.root.setOnClickListener {
                onItem(item)
                dialog?.dismiss()
            }
        }
        override fun getItemCount() = items.size
    }

    private class VH(val b: ItemMenuSheetBinding) : RecyclerView.ViewHolder(b.root)
}
