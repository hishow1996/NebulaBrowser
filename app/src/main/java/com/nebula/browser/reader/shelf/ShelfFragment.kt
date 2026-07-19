package com.nebula.browser.reader.shelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.ItemShelfBookBinding
import com.nebula.browser.store.BookEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ShelfFragment : Fragment() {
    private lateinit var rv: RecyclerView
    private val adapter = BooksAdapter()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        rv = RecyclerView(inflater.context).apply {
            layoutManager = GridLayoutManager(inflater.context, 3)
            this.adapter = this@ShelfFragment.adapter
            setPadding(24, 24, 24, 24)
        }
        return rv
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            BookRepository.observe(requireContext()).collectLatest { list ->
                adapter.submit(list)
            }
        }
    }

    private inner class BooksAdapter : RecyclerView.Adapter<BookVH>() {
        private val items = mutableListOf<BookEntity>()
        fun submit(newList: List<BookEntity>) {
            items.clear(); items.addAll(newList); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookVH {
            val b = ItemShelfBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return BookVH(b)
        }
        override fun onBindViewHolder(h: BookVH, pos: Int) {
            val item = items[pos]
            h.b.bookTitle.text = item.title
            h.b.bookProgress.progress = if (item.totalChapters > 0)
                (item.currentChapter * 100 / item.totalChapters) else 0
            h.itemView.setOnClickListener {
                toast("打开《${item.title}》")
            }
            h.itemView.setOnLongClickListener {
                toast(getString(R.string.shelf_delete)); true
            }
        }
        override fun getItemCount() = items.size
    }

    private inner class BookVH(val b: ItemShelfBookBinding) : RecyclerView.ViewHolder(b.root)
}
