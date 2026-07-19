package com.nebula.browser.ai.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.view.*
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nebula.browser.R
import com.nebula.browser.common.toast
import com.nebula.browser.store.AiMessageEntity
import com.nebula.browser.store.SettingsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 简化版AI Chat，替代 AiChatFragment 用于从浏览器菜单打开的独立 Activity 入口。
 */
class AiChatActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private lateinit var input: EditText
    private lateinit var btnSend: ImageView
    private val repo = com.nebula.browser.ai.store.AiMessageRepository()
    private val client = com.nebula.browser.ai.client.OpenAiClient()
    private var conversationId: String = System.currentTimeMillis().toString()
    private val adapter = MsgAdapter()
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ai_assistant)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val root = LinearLayout(this@AiChatActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg_primary))
        }
        rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity)
            this.adapter = this@AiChatActivity.adapter
            setPadding(24, 24, 24, 80)
        }
        root.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(getColor(R.color.bg_primary))
        }
        input = EditText(this).apply {
            hint = getString(R.string.ai_input_hint)
            background = getDrawable(R.drawable.bg_url_bar)
            setPadding(48, 24, 48, 24)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_tertiary))
        }
        (input.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0; height = dpToPx(40f); weight = 1f
            marginEnd = dpToPx(8f)
        }
        vb(input)
        btnSend = ImageView(this).apply {
            setImageResource(R.drawable.ic_send)
            background = getDrawable(R.drawable.bg_pill_blue)
            setColorFilter(getColor(R.color.text_inverse))
        }
        (btnSend.layoutParams as LinearLayout.LayoutParams).apply {
            width = dpToPx(40f); height = dpToPx(40f)
        }
        inputBar.addView(input)
        inputBar.addView(btnSend)
        root.addView(inputBar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)

        lifecycleScope.launch {
            repo.observe(this@AiChatActivity, conversationId).collectLatest { list ->
                adapter.submit(list)
                rv.scrollToPosition(list.size - 1)
            }
        }

        btnSend.setOnClickListener { send() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
    }

    private fun vb(e: EditText) {
        e.layoutParams = LinearLayout.LayoutParams(0, dpToPx(40f), 1f).apply {
            marginEnd = dpToPx(8f)
        }
    }

    private fun send() {
        if (sending) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        sending = true
        val msg = AiMessageEntity(role = "user", content = text, conversationId = conversationId)
        input.text = null
        // 简化：用单次同步调用
        val ctx = this
        lifecycleScope.launch {
            repo.add(msg)
            val profile = client.loadSilentProfile()
            if (profile == null) {
                toast("未配置 AI 模型")
                sending = false; return@launch
            }
            val builder = StringBuilder()
            val flow = client.stream(listOf("user" to text), profile)
            try {
                kotlinx.coroutines.flow.collect(flow) { delta ->
                    builder.append(delta)
                    val current = AiMessageEntity(role = "assistant", content = builder.toString(), conversationId = conversationId)
                    adapter.appendStreaming(current)
                    rv.scrollToPosition(adapter.itemCount - 1)
                }
                if (builder.isNotEmpty()) repo.add(AiMessageEntity(role = "assistant", content = builder.toString(), conversationId = conversationId))
            } catch (e: Exception) {
                toast("AI 错误：${e.message}")
            }
            sending = false
        }
    }

    private fun dpToPx(d: Float): Int =
        (resources.displayMetrics.density * d).toInt()

    private inner class MsgAdapter : RecyclerView.Adapter<MsgVH>() {
        private val items = mutableListOf<AiMessageEntity>()
        fun submit(new: List<AiMessageEntity>) {
            items.clear(); items.addAll(new); notifyDataSetChanged()
        }
        fun appendStreaming(msg: AiMessageEntity) {
            if (items.isEmpty() || items.last().role != "assistant") {
                items.add(msg); notifyItemInserted(items.size - 1)
            } else {
                items[items.size - 1] = msg
                notifyItemChanged(items.size - 1)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): MsgVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            return MsgVH(v)
        }
        override fun onBindViewHolder(h: MsgVH, p: Int) {
            val m = items[p]
            if (m.role == "user") {
                h.user.visibility = View.VISIBLE
                h.ai.visibility = View.GONE
                h.user.text = m.content
            } else {
                h.ai.visibility = View.VISIBLE
                h.user.visibility = View.GONE
                h.ai.text = m.content
            }
        }
        override fun getItemCount() = items.size
    }

    private inner class MsgVH(v: View) : RecyclerView.ViewHolder(v) {
        val user = v.findViewById<TextView>(R.id.tv_user)
        val ai = v.findViewById<TextView>(R.id.tv_ai)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
