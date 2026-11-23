package com.example.simplelauncher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Ikon app ukuran besar (2x1, 1x2, 2x2): kartu transparan + ikon gede + nama. */
class BigAppView(
    ctx: Context,
    private val horizontal: Boolean,
    textColor: Int,
    cardColor: Int
) : LinearLayout(ctx) {

    val icon = ImageView(ctx)
    val label = TextView(ctx)

    init {
        orientation = if (horizontal) HORIZONTAL else VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = ctx.dp(28).toFloat()
        }
        icon.outlineProvider = roundOutline()
        icon.clipToOutline = true
        addView(icon, LayoutParams(ctx.dp(56), ctx.dp(56)))

        label.setTextColor(textColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (horizontal) 14f else 12f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        if (horizontal) {
            label.gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = ctx.dp(12)
            lp.marginEnd = ctx.dp(12)
            addView(label, lp)
        } else {
            label.gravity = Gravity.CENTER
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = ctx.dp(8)
            lp.marginStart = ctx.dp(6)
            lp.marginEnd = ctx.dp(6)
            addView(label, lp)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val s = if (horizontal) {
                (h * 0.58f).toInt()
            } else {
                minOf(w * 0.62f, h * 0.5f).toInt()
            }
            icon.layoutParams.width = s
            icon.layoutParams.height = s
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Folder di home: kartu transparan berisi preview ikon + nama folder. */
class FolderView(
    ctx: Context,
    name: String,
    apps: List<App>,
    spanW: Int,
    spanH: Int,
    cardColor: Int,
    textColor: Int
) : LinearLayout(ctx) {

    private val pc: Int = when {
        spanW >= 2 && spanH >= 2 -> 3
        spanW >= 2 -> 4
        else -> 2
    }
    private val pr: Int = when {
        spanW >= 2 && spanH >= 2 -> 3
        spanW >= 2 -> 1
        spanH >= 2 -> 3
        else -> 2
    }
    private val previews = ArrayList<ImageView>()
    private val label = TextView(ctx)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = ctx.dp(28).toFloat()
        }
        for (r in 0 until pr) {
            val row = LinearLayout(ctx)
            row.orientation = HORIZONTAL
            for (c in 0 until pc) {
                val iv = ImageView(ctx)
                iv.outlineProvider = roundOutline(0.26f)
                iv.clipToOutline = true
                val a = apps.getOrNull(r * pc + c)
                if (a != null) iv.setImageDrawable(a.iconCopy())
                val lp = LayoutParams(ctx.dp(30), ctx.dp(30))
                lp.setMargins(ctx.dp(2), ctx.dp(2), ctx.dp(2), ctx.dp(2))
                row.addView(iv, lp)
                previews.add(iv)
            }
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        label.text = name
        label.setTextColor(textColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        val llp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        llp.topMargin = ctx.dp(2)
        llp.marginStart = ctx.dp(6)
        llp.marginEnd = ctx.dp(6)
        addView(label, llp)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val pad = context.dp(8)
            val labelH = context.dp(22)
            val s = minOf((w - 2 * pad) / pc, (h - 2 * pad - labelH) / pr) - context.dp(4)
            val size = maxOf(s, context.dp(16))
            for (iv in previews) {
                iv.layoutParams.width = size
                iv.layoutParams.height = size
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
