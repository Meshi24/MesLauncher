package com.example.simplelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** FrameLayout yang cuma ngintip sentuhan, tanpa ngambil alih. */
class TouchFrame(ctx: Context, private val watcher: (MotionEvent) -> Unit) : FrameLayout(ctx) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        watcher(ev)
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Grid home: kolom x baris, tiap anak punya posisi (col,row) dan ukuran (w,h).
 * hAlign: 0 penuh, 1 kiri, 2 tengah, 3 kanan. vAlign: 0 penuh, 1 atas, 2 tengah, 3 bawah.
 */
class HomeGrid(ctx: Context) : ViewGroup(ctx) {
    var cols = 4
    var rows = 6
    var hAlign = 0
    var vAlign = 0
    var onCellTap: ((Int, Int) -> Unit)? = null

    var moving = false
        set(v) {
            field = v
            invalidate()
        }

    var showCells = false
        set(v) {
            field = v
            invalidate()
        }

    class Hint(val col: Int, val row: Int, val w: Int, val h: Int, val ok: Boolean)

    var hint: Hint? = null
        set(v) {
            field = v
            invalidate()
        }

    class LP(val col: Int, val row: Int, val w: Int = 1, val h: Int = 1) :
        ViewGroup.LayoutParams(0, 0)

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cw = 1
    private var chh = 1
    private var offX = 0
    private var offY = 0

    init {
        setWillNotDraw(false)
    }

    private fun calc(w: Int, h: Int) {
        val fw = maxOf(w / maxOf(cols, 1), 1)
        val fh = maxOf(h / maxOf(rows, 1), 1)
        val sq = minOf(fw, fh)
        cw = if (hAlign == 0) fw else sq
        chh = if (vAlign == 0) fh else sq
        val usedW = cw * cols
        val usedH = chh * rows
        offX = when (hAlign) {
            2 -> (w - usedW) / 2
            3 -> w - usedW
            else -> 0
        }
        offY = when (vAlign) {
            2 -> (h - usedH) / 2
            3 -> h - usedH
            else -> 0
        }
    }

    private fun inset(lp: LP): Int = if (lp.w > 1 || lp.h > 1) context.dp(6) else 0

    override fun onMeasure(w: Int, h: Int) {
        val ww = View.MeasureSpec.getSize(w)
        val hh = View.MeasureSpec.getSize(h)
        setMeasuredDimension(ww, hh)
        calc(ww, hh)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LP
            val ins = inset(lp)
            child.measure(
                View.MeasureSpec.makeMeasureSpec((cw * lp.w - 2 * ins).coerceAtLeast(0), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((chh * lp.h - 2 * ins).coerceAtLeast(0), View.MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        calc(r - l, b - t)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LP
            val ins = inset(lp)
            child.layout(
                offX + lp.col * cw + ins,
                offY + lp.row * chh + ins,
                offX + (lp.col + lp.w) * cw - ins,
                offY + (lp.row + lp.h) * chh - ins
            )
        }
    }

    private fun occupied(c: Int, r: Int): Boolean {
        for (i in 0 until childCount) {
            val lp = getChildAt(i).layoutParams as LP
            if (c >= lp.col && c < lp.col + lp.w && r >= lp.row && r < lp.row + lp.h) return true
        }
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            calc(width, height)
            if (moving || showCells) {
                for (c in 0 until cols) {
                    for (r in 0 until rows) {
                        if (!occupied(c, r)) {
                            canvas.drawRoundRect(
                                offX + c * cw + 8f, offY + r * chh + 8f,
                                offX + (c + 1) * cw - 8f, offY + (r + 1) * chh - 8f,
                                24f, 24f, cellPaint
                            )
                        }
                    }
                }
            }
            val hi = hint
            if (hi != null) {
                hintPaint.color = if (hi.ok) 0x66FFFFFF else 0x66FF5252
                canvas.drawRoundRect(
                    offX + hi.col * cw + 6f, offY + hi.row * chh + 6f,
                    offX + (hi.col + hi.w) * cw - 6f, offY + (hi.row + hi.h) * chh - 6f,
                    28f, 28f, hintPaint
                )
            }
        }
        super.dispatchDraw(canvas)
    }

    /** Kotak grid yang ada di bawah titik layar (sx, sy). Null kalau di luar grid. */
    fun cellAtScreen(sx: Float, sy: Float): IntArray? {
        if (width <= 0 || height <= 0) return null
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val x = sx - loc[0]
        val y = sy - loc[1]
        if (x < 0f || y < 0f || x > width || y > height) return null
        calc(width, height)
        val c = ((x - offX) / cw).toInt().coerceIn(0, cols - 1)
        val r = ((y - offY) / chh).toInt().coerceIn(0, rows - 1)
        return intArrayOf(c, r)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = moving

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!moving) return super.onTouchEvent(ev)
        if (ev.action == MotionEvent.ACTION_UP && width > 0) {
            calc(width, height)
            val c = ((ev.x - offX) / cw).toInt().coerceIn(0, cols - 1)
            val r = ((ev.y - offY) / chh).toInt().coerceIn(0, rows - 1)
            onCellTap?.invoke(c, r)
        }
        return true
    }
}
