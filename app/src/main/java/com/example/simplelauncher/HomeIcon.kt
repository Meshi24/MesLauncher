package com.example.simplelauncher

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Ikon di home/dock: ukuran ikon dan label bisa diatur, label bisa disembunyiin. */
class HomeIcon(
    ctx: Context,
    labelColor: Int,
    private val showLabel: Boolean,
    private val scale: Float,
    private val labelSp: Float
) : LinearLayout(ctx) {

    val icon = ImageView(ctx)
    val label = TextView(ctx)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        icon.layoutParams = LayoutParams(ctx.dp(56), ctx.dp(56))
        icon.outlineProvider = roundOutline()
        icon.clipToOutline = true
        label.setTextColor(labelColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        if (labelColor == 0xFFFFFFFF.toInt()) label.setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = ctx.dp(5)
        label.layoutParams = lp
        addView(icon)
        addView(label)
        if (!showLabel) label.visibility = View.GONE
    }

    fun bind(app: App) {
        icon.setImageDrawable(app.iconCopy())
        label.text = app.label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val d = resources.displayMetrics.density
            val labelH = if (showLabel) (labelSp * 1.5f * d).toInt() + context.dp(5) else 0
            val base = minOf(w * 0.62f, if (showLabel) h * 0.58f else h * 0.72f, 60f * d)
            val maxS = minOf(w * 0.96f, (h - labelH - context.dp(4)).toFloat())
            val s = minOf(base * scale, maxS).coerceAtLeast(context.dp(24).toFloat()).toInt()
            icon.layoutParams.width = s
            icon.layoutParams.height = s
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
