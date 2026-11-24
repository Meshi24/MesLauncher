package com.example.simplelauncher

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Layout paling luar. Kalau jari digeser vertikal, dia ambil alih sentuhan
 * dan ngelaporin gerakannya, biar drawer bisa ngikutin jari.
 * decide(dx, dy): 0 = jangan, 1 = mulai buka drawer, 2 = mulai tutup drawer.
 */
class DragFrame(ctx: Context) : FrameLayout(ctx) {

    var onDown: (() -> Unit)? = null
    var decide: ((Float, Float) -> Int)? = null
    var onStart: ((Int) -> Unit)? = null
    var onMove: ((Float) -> Unit)? = null
    var onEnd: ((Float) -> Unit)? = null

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null

    init {
        isClickable = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(ev)
                onDown?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                if (!dragging) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.4f) {
                        val m = decide?.invoke(dx, dy) ?: 0
                        if (m != 0) {
                            dragging = true
                            startY = ev.y
                            onStart?.invoke(m)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.recycle()
                vt = null
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)
        vt?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> onMove?.invoke(ev.y - startY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                var vy = 0f
                if (ev.actionMasked == MotionEvent.ACTION_UP) {
                    vt?.computeCurrentVelocity(1000)
                    vy = vt?.yVelocity ?: 0f
                }
                vt?.recycle()
                vt = null
                dragging = false
                onEnd?.invoke(vy)
            }
        }
        return true
    }
}
