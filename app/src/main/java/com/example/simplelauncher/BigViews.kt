package com.example.simplelauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

/**
 * Folder di home: kartu transparan berisi ikon (ngisi penuh), nama di bawah kartu.
 * Kalau label disembunyiin di setelan, nama folder ilang dan kartu ngisi penuh.
 * Kalau app lebih banyak dari slot, slot terakhir jadi grup mini 2x2.
 */
class FolderView(
    ctx: Context,
    private val name: String,
    private val apps: List<App>,
    private val spanW: Int,
    private val spanH: Int,
    private val cardColor: Int,
    private val textColor: Int
) : LinearLayout(ctx) {

    private var built = false
    private val showLabel = Prefs.showLabels(ctx)

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (!built && w > 0 && h > 0) {
            built = true
            build(w, h)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun iconView(a: App, size: Int): ImageView {
        val iv = ImageView(context)
        iv.outlineProvider = roundOutline(0.26f)
        iv.clipToOutline = true
        iv.setImageDrawable(a.iconCopy())
        iv.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        return iv
    }

    private fun miniGroup(rest: List<App>, size: Int): View {
        val ctx = context
        val g = LinearLayout(ctx)
        g.orientation = VERTICAL
        g.gravity = Gravity.CENTER
        g.background = GradientDrawable().apply {
            setColor(if (textColor == Color.WHITE) 0x33FFFFFF else 0x22000000)
            cornerRadius = size * 0.26f
        }
        val s = (size * 0.40f).toInt().coerceAtLeast(ctx.dp(8))
        for (r in 0..1) {
            val row = LinearLayout(ctx)
            row.orientation = HORIZONTAL
            for (c in 0..1) {
                val a = rest.getOrNull(r * 2 + c)
                val iv = ImageView(ctx)
                iv.outlineProvider = roundOutline(0.26f)
                iv.clipToOutline = true
                if (a != null) iv.setImageDrawable(a.iconCopy())
                row.addView(iv, LayoutParams(s, s))
            }
            g.addView(row)
        }
        g.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        return g
    }

    private fun build(w: Int, h: Int) {
        val ctx = context
        val labelH = if (showLabel) minOf(ctx.dp(22), h / 4) else 0
        val boxH = h - labelH
        val pad = ctx.dp(8)
        val aspect = w.toFloat() / boxH

        val grid: Pair<Int, Int> = when {
            spanW >= 2 && spanH >= 2 -> if (aspect > 1.15f) 4 to 3 else 3 to 3
            spanW >= 2 -> 4 to 1
            spanH >= 2 -> 2 to 3
            else -> 2 to 2
        }
        val pc = grid.first
        val pr = grid.second
        val cellW = (w - 2 * pad) / pc
        val cellH = (boxH - 2 * pad) / pr
        val iconSz = (minOf(cellW, cellH) * 0.84f).toInt().coerceAtLeast(ctx.dp(16))

        val slots = pc * pr
        val overflow = apps.size > slots
        val shown = if (overflow) apps.take(slots - 1) else apps
        val rest = if (overflow) apps.drop(slots - 1) else emptyList()

        val box = LinearLayout(ctx)
        box.orientation = VERTICAL
        box.background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = ctx.dp(26).toFloat()
        }
        box.setPadding(pad, pad, pad, pad)

        for (r in 0 until pr) {
            val row = LinearLayout(ctx)
            row.orientation = HORIZONTAL
            for (c in 0 until pc) {
                val cell = FrameLayout(ctx)
                val i = r * pc + c
                if (i < shown.size) {
                    cell.addView(iconView(shown[i], iconSz))
                } else if (overflow && i == slots - 1) {
                    cell.addView(miniGroup(rest, iconSz))
                }
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
            box.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        if (showLabel) {
            val label = TextView(ctx)
            label.text = name
            label.setTextColor(textColor)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.gravity = Gravity.CENTER
            if (textColor == Color.WHITE) label.setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, labelH))
        }
    }
}
