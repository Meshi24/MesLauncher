package com.example.simplelauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

/** Satu slot widget berisi 1 atau lebih widget. Kalau lebih dari satu, digeser kiri/kanan. */
class StackView(ctx: Context) : FrameLayout(ctx) {

    private val items = ArrayList<View>()
    private val dots = LinearLayout(ctx)
    var index = 0
        private set
    var onLong: (() -> Unit)? = null
    var onIndexChanged: ((Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var longFired = false
    private var vt: VelocityTracker? = null

    private val longRun = Runnable {
        longFired = true
        onLong?.invoke()
    }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ctx.dp(22).toFloat())
            }
        }
        clipToOutline = true
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
    }

    fun count(): Int = items.size

    fun setViews(list: List<View>, start: Int) {
        removeAllViews()
        items.clear()
        for (v in list) {
            (v.parent as? ViewGroup)?.removeView(v)
            addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            items.add(v)
        }
        index = start.coerceIn(0, maxOf(items.size - 1, 0))
        showOnlyCurrent()
        val dlp = LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        dlp.bottomMargin = context.dp(6)
        addView(dots, dlp)
        updateDots()
    }

    private fun showOnlyCurrent() {
        for ((i, v) in items.withIndex()) {
            v.translationX = 0f
            v.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun updateDots() {
        dots.removeAllViews()
        if (items.size <= 1) {
            dots.visibility = View.GONE
            return
        }
        dots.visibility = View.VISIBLE
        for (i in items.indices) {
            val d = View(context)
            d.background = GradientDrawable().apply {
                setColor(if (i == index) 0xEEFFFFFF.toInt() else 0x66FFFFFF)
                cornerRadius = 100f
            }
            val s = context.dp(if (i == index) 6 else 5)
            val lp = LinearLayout.LayoutParams(s, s)
            lp.setMargins(context.dp(2), 0, context.dp(2), 0)
            dots.addView(d, lp)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics.density
        val wd = (w / d).toInt()
        val hd = (h / d).toInt()
        for (v in items) {
            if (v is AppWidgetHostView) v.updateAppWidgetSize(null, wd, hd, wd, hd)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swiping = false
                longFired = false
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(ev)
                removeCallbacks(longRun)
                postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(longRun)
                if (!longFired && !swiping && items.size > 1 &&
                    abs(dx) > slop && abs(dx) > abs(dy) * 1.2f
                ) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longRun)
        }
        return longFired || swiping
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(longRun)
                if (!longFired && !swiping && items.size > 1 &&
                    abs(dx) > slop && abs(dx) > abs(dy) * 1.2f
                ) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (swiping) dragTo(dx)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRun)
                vt?.addMovement(ev)
                if (swiping) finishSwipe(ev.x - downX)
                swiping = false
                vt?.recycle()
                vt = null
                return true
            }
        }
        return true
    }

    private fun otherIndex(dx: Float): Int {
        val n = items.size
        return if (dx < 0) (index + 1) % n else (index + n - 1) % n
    }

    private fun dragTo(dx: Float) {
        val w = width.toFloat()
        if (w <= 0f || items.size < 2) return
        val cur = items[index]
        val oi = otherIndex(dx)
        val other = items[oi]
        for ((i, v) in items.withIndex()) {
            if (i != index && i != oi) v.visibility = View.INVISIBLE
        }
        other.visibility = View.VISIBLE
        cur.translationX = dx
        other.translationX = dx + (if (dx < 0) w else -w)
    }

    private fun finishSwipe(dx: Float) {
        val w = width.toFloat()
        if (w <= 0f || items.size < 2) return
        vt?.computeCurrentVelocity(1000)
        val vx = vt?.xVelocity ?: 0f
        val go = abs(dx) > w * 0.25f || (abs(vx) > 900f && vx * dx > 0f)
        val cur = items[index]
        val oi = otherIndex(dx)
        val other = items[oi]
        if (go) {
            cur.animate().translationX(if (dx < 0) -w else w).setDuration(180).start()
            other.animate().translationX(0f).setDuration(180).withEndAction {
                index = oi
                showOnlyCurrent()
                updateDots()
                onIndexChanged?.invoke(index)
            }.start()
        } else {
            cur.animate().translationX(0f).setDuration(160).start()
            other.animate().translationX(if (dx < 0) w else -w).setDuration(160).withEndAction {
                other.visibility = View.INVISIBLE
                other.translationX = 0f
            }.start()
        }
    }
}
