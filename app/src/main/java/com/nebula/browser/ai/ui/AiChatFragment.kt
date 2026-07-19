package com.nebula.browser.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.databinding.FragmentAiChatBinding
import com.nebula.browser.store.AiMessageEntity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AiChatFragment : Fragment() {
    private var _b: FragmentAiChatBinding? = null
    private val b get() = _b!!
    private val repo = com.nebula.browser.ai.store.AiMessageRepository()
    private val client = com.nebula.browser.ai.client.OpenAiClient()
    private var conversationId: String = System.currentTimeMillis().toString()
    private val adapter = MsgAdapter()
    private var sending = false

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAiChatBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.messagesRecycler.layoutManager = LinearLayoutManager(requireContext())
        b.messagesRecycler.adapter = adapter

        b.btnSend.setOnClickListener { send() }
        b.aiInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observe(requireContext(), conversationId).collectLatest { list ->
                adapter.submit(list)
                b.messagesRecycler.scrollToPosition(list.size - 1)
            }
        }
    }

    private fun send() {
        if (sending) return
        val text = b.aiInput.text.toString().trim()
        if (text.isEmpty()) return
        sending = true
        val msg = AiMessageEntity(role = "user", content = text, conversationId = conversationId)
        b.aiInput.text = null
        lifecycleScope.launch {
            repo.add(msg)
            val profile = client.loadSilentProfile()
            if (profile == null) {
                toast("未配置 AI 模型"); sending = false; return@launch
            }
            val builder = StringBuilder()
            try {
                kotlinx.coroutines.flow.collect(client.stream(listOf("user" to text), profile)) { delta ->
                    builder.append(delta)
                    adapter.appendStreaming(AiMessageEntity(role = "assistant",
                        content = builder.toString(), conversationId = conversationId))
                    b.messagesRecycler.scrollToPosition(adapter.itemCount - 1)
                }
                if (builder.isNotEmpty())
                    repo.add(AiMessageEntity(role = "assistant",
                        content = builder.toString(), conversationId = conversationId))
            } catch (e: Exception) { toast("AI 错误：${e.message}") }
            sending = false
        }
    }

    private inner class MsgAdapter : RecyclerView.Adapter<VH>() {
        private val items = mutableListOf<AiMessageEntity>()
        fun submit(new: List<AiMessageEntity>) { items.clear(); items.addAll(new); notifyDataSetChanged() }
        fun appendStreaming(msg: AiMessageEntity) {
            if (items.isEmpty() || items.last().role != "assistant") {
                items.add(msg); notifyItemInserted(items.size - 1)
            } else {
                items[items.size - 1] = msg
                notifyItemChanged(items.size - 1)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val m = items[p]
            if (m.role == "user") { h.user.visibility = View.VISIBLE; h.ai.visibility = View.GONE; h.user.text = m.content }
            else { h.ai.visibility = View.VISIBLE; h.user.visibility = View.GONE; h.ai.text = m.content }
        }
        override fun getItemCount() = items.size
    }

    private inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val user = v.findViewById<android.widget.TextView>(R.id.tv_user)
        val ai = v.findViewById<android.widget.TextView>(R.id.tv_ai)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
