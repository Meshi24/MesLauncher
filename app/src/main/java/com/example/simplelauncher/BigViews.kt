package com.example.simplelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Warna yang paling dominan di sebuah ikon (buat warna kartu ikon besar). */
fun dominantColor(d: Drawable): Int {
    val fallback = 0xFF2A2A2E.toInt()
    return try {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cd = d.constantState?.newDrawable()?.mutate() ?: d
        cd.setBounds(0, 0, n, n)
        cd.draw(cv)
        val counts = HashMap<Int, IntArray>()
        for (y in 0 until n) {
            for (x in 0 until n) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 200) continue
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val key = (r / 16) * 256 + (g / 16) * 16 + (b / 16)
                val slot = counts.getOrPut(key) { IntArray(4) }
                slot[0] += 1
                slot[1] += r
                slot[2] += g
                slot[3] += b
            }
        }
        bmp.recycle()
        val best = counts.values.maxByOrNull { it[0] }
        if (best == null) fallback else Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    } catch (e: Exception) {
        fallback
    }
}

/**
 * Ikon app ukuran besar (2x1, 1x2, 2x2): kartu berwarna sesuai ikon + ikon gede
 * di tengah. Nama app ada di bawah kartu, dan ilang kalau label dimatiin.
 */
class BigAppView(
    ctx: Context,
    app: App,
    private val showLabel: Boolean,
    labelColor: Int
) : LinearLayout(ctx) {

    val icon = ImageView(ctx)
    val label = TextView(ctx)
    private val card = FrameLayout(ctx)

    init {
        orientation = VERTICAL
        card.background = GradientDrawable().apply {
            setColor(dominantColor(app.icon))
            cornerRadius = ctx.dp(28).toFloat()
        }
        icon.outlineProvider = roundOutline()
        icon.clipToOutline = true
        icon.setImageDrawable(app.iconCopy())
        card.addView(icon, FrameLayout.LayoutParams(ctx.dp(56), ctx.dp(56), Gravity.CENTER))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        label.text = app.label
        label.setTextColor(labelColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        if (labelColor == Color.WHITE) label.setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        if (showLabel) addView(label, LayoutParams(LayoutParams.MATCH_PARENT, ctx.dp(22)))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val labelH = if (showLabel) context.dp(22) else 0
            val s = (minOf(w, h - labelH) * 0.64f).toInt().coerceAtLeast(context.dp(24))
            val lp = icon.layoutParams
            lp.width = s
            lp.height = s
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/**
 * Folder di home: kartu transparan yang nempel ke isinya, nama di bawah kartu.
 * Ukuran ikon ngikutin setelan ukuran ikon. Kalau label disembunyiin, nama ilang.
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
        gravity = Gravity.CENTER
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
        val areaH = h - labelH
        val pad = ctx.dp(8)
        val aspect = w.toFloat() / areaH

        val grid: Pair<Int, Int> = when {
            spanW >= 2 && spanH >= 2 -> if (aspect > 1.15f) 4 to 3 else 3 to 3
            spanW >= 2 -> 4 to 1
            spanH >= 2 -> 2 to 3
            else -> 2 to 2
        }
        val pc = grid.first
        val pr = grid.second

        val cell = minOf((w - 2 * pad) / pc, (areaH - 2 * pad) / pr).coerceAtLeast(ctx.dp(16))
        val scale = Prefs.iconScale(ctx) / 100f
        val iconSz = (cell * 0.82f * scale)
            .coerceAtMost((cell - ctx.dp(4)).toFloat())
            .coerceAtLeast(ctx.dp(12).toFloat())
            .toInt()
        val cellSz = minOf(cell, (iconSz / 0.82f).toInt()).coerceAtLeast(iconSz)

        val slots = pc * pr
        val overflow = apps.size > slots
        val shown = if (overflow) apps.take(slots - 1) else apps
        val rest = if (overflow) apps.drop(slots - 1) else emptyList()

        val used = (if (overflow) slots else apps.size).coerceAtLeast(1)
        val rowsUsed = (used + pc - 1) / pc
        val colsUsed = if (rowsUsed == 1) used else pc

        val boxW = colsUsed * cellSz + 2 * pad
        val boxH = rowsUsed * cellSz + 2 * pad

        val box = LinearLayout(ctx)
        box.orientation = VERTICAL
        box.background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = ctx.dp(26).toFloat()
        }
        box.setPadding(pad, pad, pad, pad)

        for (r in 0 until rowsUsed) {
            val row = LinearLayout(ctx)
            row.orientation = HORIZONTAL
            for (c in 0 until colsUsed) {
                val i = r * pc + c
                if (i >= used) break
                val cellView = FrameLayout(ctx)
                if (i < shown.size) {
                    cellView.addView(iconView(shown[i], iconSz))
                } else if (overflow && i == slots - 1) {
                    cellView.addView(miniGroup(rest, iconSz))
                }
                row.addView(cellView, LayoutParams(cellSz, cellSz))
            }
            box.addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, cellSz))
        }
        val blp = LayoutParams(boxW, boxH)
        blp.gravity = Gravity.CENTER_HORIZONTAL
        addView(box, blp)

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
