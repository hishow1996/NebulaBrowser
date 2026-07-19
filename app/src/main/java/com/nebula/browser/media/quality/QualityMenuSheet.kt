package com.nebula.browser.media.quality

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.nebula.browser.R
import com.nebula.browser.databinding.ItemQualityBinding
import com.nebula.browser.store.SettingsManager

/**
 * 内置播放器底部画质选择面板。
 *
 * - 圆角顶 White sheet，挂在 window 底部；
 * - 每档 item 卡片：左侧彩色圆点 + 标题 + 描述；
 * - 当前档位高亮（边框 + 选中标记）；
 * - 选定后回调。
 */
class QualityMenuSheet(
    context: Context,
    private val current: QualityLevel,
    private val onPick: (QualityLevel) -> Unit
) : Dialog(context, R.style.ThemeOverlay_Nebula_QualitySheet) {

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val root = buildSheet()
        setContentView(root)

        window?.apply {
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(R.style.Animation_Nebula_BottomSheet)
            decorView.setPadding(0, 0, 0, 0)
        }
    }

    private fun buildSheet(): View {
        val ctx = context
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = context.getDrawable(R.drawable.bg_quality_sheet)
            setPadding(0, 20.dp(ctx), 0, 24.dp(ctx))
        }

        // 拖拽柄
        val handle = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp(ctx), 4.dp(ctx)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dp(ctx)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2f
                setColor(0xFFCAD5E1.toInt())
            }
        }
        container.addView(handle)

        // 标题
        val title = android.widget.TextView(ctx).apply {
            text = context.getString(R.string.video_quality)
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(20.dp(ctx), 4.dp(ctx), 20.dp(ctx), 8.dp(ctx))
        }
        container.addView(title)

        QualityLevel.entries.forEach { level ->
            val item = ItemQualityBinding.inflate(LayoutInflater.from(ctx), container, false)
            item.qualityTitle.text = level.label
            item.qualityDesc.text = describe(level)
            item.qualityDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorOf(level))
            }
            val isCurrent = level == current
            item.root.background = context.getDrawable(
                if (isCurrent) R.drawable.bg_quality_item_checked else R.drawable.bg_quality_item
            )
            item.qualityCheck.visibility = if (isCurrent) View.VISIBLE else View.GONE
            item.root.setOnClickListener {
                onPick(level)
                dismiss()
            }
            container.addView(item.root)
        }

        // 取消按钮
        val cancel = android.widget.TextView(ctx).apply {
            text = context.getString(R.string.cancel)
            setTextColor(context.getColor(R.color.text_secondary))
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp(ctx) }
        }
        container.addView(cancel)
        return container
    }

    private fun describe(level: QualityLevel): String = when (level) {
        QualityLevel.AUTO -> "自动 · 根据网速"
        QualityLevel.P240 -> "流畅省流 · 推荐弱网"
        QualityLevel.P360 -> "标清"
        QualityLevel.P480 -> "高清"
        QualityLevel.P720 -> "超清"
        QualityLevel.P1080 -> "蓝光"
        QualityLevel.SOURCE -> "原画质"
    }

    /** 不同档圆点颜色，制造视觉差异 */
    private fun colorOf(level: QualityLevel): Int = when (level) {
        QualityLevel.AUTO -> 0xFF94A3B8.toInt()
        QualityLevel.P240 -> 0xFF10B981.toInt()
        QualityLevel.P360 -> 0xFF60A5FA.toInt()
        QualityLevel.P480 -> 0xFFF59E0B.toInt()
        QualityLevel.P720 -> 0xFF8B5CF6.toInt()
        QualityLevel.P1080 -> 0xFFEF4444.toInt()
        QualityLevel.SOURCE -> 0xFF0F172A.toInt()
    }

    private fun Int.dp(ctx: Context): Int =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(), ctx.resources.displayMetrics
        ).toInt()
}
