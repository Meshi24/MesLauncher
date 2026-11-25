package com.example.simplelauncher

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Layout paling luar. Tugasnya:
 * 1) kalau jari digeser vertikal, ambil alih sentuhan biar drawer ngikutin jari
 *    (decide: 0 = jangan, 1 = mulai buka drawer, 2 = mulai tutup drawer)
 * 2) kalau ada "pickup" (habis tahan lama sebuah ikon) dan jari mulai bergerak,
 *    mulai drag ikon. Kalau dilepas tanpa gerak, panggil onPickupRelease (buat menu).
 */
class DragFrame(ctx: Context) : FrameLayout(ctx) {

    var onDown: (() -> Unit)? = null
    var decide: ((Float, Float) -> Int)? = null
    var onStart: ((Int) -> Unit)? = null
    var onMove: ((Float) -> Unit)? = null
    var onEnd: ((Float) -> Unit)? = null

    var pickupActive = false
    var onItemStart: ((Float, Float) -> Unit)? = null
    var onItemMove: ((Float, Float) -> Unit)? = null
    var onItemEnd: ((Float, Float, Boolean) -> Unit)? = null
    var onPickupRelease: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var startY = 0f
    private var dragging = false
    private var itemDragging = false
    private var vt: VelocityTracker? = null

    init {
        isClickable = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val a = ev.actionMasked
        if (a == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
            downRawX = ev.rawX
            downRawY = ev.rawY
            dragging = false
            pickupActive = false
            itemDragging = false
            vt?.recycle()
            vt = VelocityTracker.obtain()
            vt?.addMovement(ev)
            onDown?.invoke()
            return false
        }

        if (pickupActive || itemDragging) {
            if (a == MotionEvent.ACTION_MOVE) {
                if (itemDragging) return true
                val d = hypot(ev.rawX - downRawX, ev.rawY - downRawY)
                if (d > slop * 0.6f) {
                    itemDragging = true
                    onItemStart?.invoke(ev.rawX, ev.rawY)
                    return true
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (!itemDragging) {
                    pickupActive = false
                    if (a == MotionEvent.ACTION_UP) onPickupRelease?.invoke()
                }
            }
            return false
        }

        when (a) {
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
        if (itemDragging) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> onItemMove?.invoke(ev.rawX, ev.rawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    itemDragging = false
                    pickupActive = false
                    onItemEnd?.invoke(ev.rawX, ev.rawY, ev.actionMasked == MotionEvent.ACTION_CANCEL)
                }
            }
            return true
        }
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
