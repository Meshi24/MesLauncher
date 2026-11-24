package com.example.simplelauncher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Halaman home yang bisa digeser kiri/kanan dan nge-snap ke satu halaman. */
class Pager(ctx: Context) : HorizontalScrollView(ctx) {

    private val strip = LinearLayout(ctx)
    var onPageChanged: ((Int) -> Unit)? = null
    var page = 0
        private set
    private var count = 0
    private var flung = false
    private var anim: ValueAnimator? = null

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        strip.orientation = LinearLayout.HORIZONTAL
        addView(
            strip,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun setPages(views: List<View>) {
        strip.removeAllViews()
        for (v in views) {
            strip.addView(
                v,
                LinearLayout.LayoutParams(maxOf(width, 1), ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        count = views.size
        page = page.coerceIn(0, maxOf(count - 1, 0))
        post { scrollTo(page * width, 0) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (w > 0) {
            for (i in 0 until strip.childCount) {
                val lp = strip.getChildAt(i).layoutParams
                if (lp.width != w) lp.width = w
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { scrollTo(page * width, 0) }
    }

    fun currentPage(): Int {
        if (width <= 0) return page
        return ((scrollX + width / 2) / width).coerceIn(0, maxOf(count - 1, 0))
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val p = currentPage()
        if (p != page) {
            page = p
            onPageChanged?.invoke(p)
        }
    }

    fun goTo(target: Int) {
        val t = target.coerceIn(0, maxOf(count - 1, 0))
        anim?.cancel()
        val from = scrollX
        val to = t * width
        if (from == to) return
        val a = ValueAnimator.ofInt(from, to)
        a.duration = 260
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
        anim = a
        a.start()
    }

    override fun fling(velocityX: Int) {
        flung = true
        val cur = if (width > 0) scrollX.toFloat() / width else 0f
        val target = when {
            velocityX > 500 -> floor(cur).toInt() + 1
            velocityX < -500 -> ceil(cur).toInt() - 1
            else -> cur.roundToInt()
        }
        goTo(target)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            flung = false
            anim?.cancel()
        }
        val r = super.onTouchEvent(ev)
        val a = ev.actionMasked
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !flung) {
            goTo(if (width > 0) (scrollX.toFloat() / width).roundToInt() else page)
        }
        return r
    }
}
