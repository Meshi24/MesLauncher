package com.example.simplelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** FrameLayout yang cuma ngintip sentuhan (buat deteksi swipe), tanpa ngambil alih. */
class TouchFrame(ctx: Context, private val watcher: (MotionEvent) -> Unit) : FrameLayout(ctx) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        watcher(ev)
        return super.dispatchTouchEvent(ev)
    }
}

/** Grid home: kolom x baris, tiap anak punya posisi (col,row). */
class HomeGrid(ctx: Context) : ViewGroup(ctx) {
    var cols = 4
    var rows = 6
    var onCellTap: ((Int, Int) -> Unit)? = null
    var moving = false
        set(v) {
            field = v
            invalidate()
        }

    class LP(val col: Int, val row: Int) : ViewGroup.LayoutParams(0, 0)

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(w: Int, h: Int) {
        val ww = View.MeasureSpec.getSize(w)
        val hh = View.MeasureSpec.getSize(h)
        setMeasuredDimension(ww, hh)
        val cw = ww / cols
        val ch = hh / rows
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        val ch = (b - t) / rows
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LP
            child.layout(lp.col * cw, lp.row * ch, (lp.col + 1) * cw, (lp.row + 1) * ch)
        }
    }

    private fun occupied(c: Int, r: Int): Boolean {
        for (i in 0 until childCount) {
            val lp = getChildAt(i).layoutParams as LP
            if (lp.col == c && lp.row == r) return true
        }
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (moving && width > 0) {
            val cw = width.toFloat() / cols
            val ch = height.toFloat() / rows
            for (c in 0 until cols) for (r in 0 until rows) {
                if (!occupied(c, r)) {
                    canvas.drawRoundRect(c * cw + 8, r * ch + 8, (c + 1) * cw - 8, (r + 1) * ch - 8, 24f, 24f, hintPaint)
                }
            }
        }
        super.dispatchDraw(canvas)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = moving

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!moving) return super.onTouchEvent(ev)
        if (ev.action == MotionEvent.ACTION_UP && width > 0) {
            val c = (ev.x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
            val r = (ev.y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            onCellTap?.invoke(c, r)
        }
        return true
    }
}
