package com.example.simplelauncher

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

/** Efek ikon mencar / ngumpul ala iOS. speed: 1.0 normal, makin kecil makin pelan. */
object IconFx {
    private const val K = 1.6f

    private class Off(val v: View, val dx: Float, val dy: Float, val dist: Float)

    fun reset(v: View) {
        v.animate().cancel()
        v.translationX = 0f
        v.translationY = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = 1f
    }

    fun hide(v: View) {
        v.animate().cancel()
        v.alpha = 0f
    }

    private fun measure(views: List<View>, w: Int, h: Int): List<Off> {
        val cx = w / 2f
        val cy = h / 2f
        val loc = IntArray(2)
        return views.map { v ->
            reset(v)
            v.getLocationInWindow(loc)
            val dx = loc[0] + v.width / 2f - cx
            val dy = loc[1] + v.height / 2f - cy
            Off(v, dx, dy, hypot(dx, dy))
        }
    }

    /** Ikon mulai melebar + gede + transparan, lalu ngumpul ke tempatnya. */
    fun gather(views: List<View>, w: Int, h: Int, speed: Float) {
        val offs = measure(views, w, h)
        val max = (offs.maxOfOrNull { it.dist } ?: 1f).coerceAtLeast(1f)
        for (o in offs) {
            o.v.translationX = o.dx * (K - 1f)
            o.v.translationY = o.dy * (K - 1f)
            o.v.scaleX = K
            o.v.scaleY = K
            o.v.alpha = 0f
            o.v.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay((o.dist / max * 180f / speed).toLong())
                .setDuration((650f / speed).toLong())
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    /** Kebalikannya: ikon mencar keluar sambil memudar. */
    fun scatter(views: List<View>, w: Int, h: Int, speed: Float) {
        val offs = measure(views, w, h)
        for (o in offs) {
            o.v.animate()
                .translationX(o.dx * (K - 1f)).translationY(o.dy * (K - 1f))
                .scaleX(K).scaleY(K).alpha(0f)
                .setStartDelay(0)
                .setDuration((380f / speed).toLong())
                .setInterpolator(AccelerateInterpolator(1.4f))
                .start()
        }
    }

    /** Dock dan search pill naik dari bawah. */
    fun barIn(v: View, dist: Float, speed: Float) {
        v.animate().cancel()
        v.translationY = dist
        v.alpha = 0f
        v.animate().translationY(0f).alpha(1f)
            .setStartDelay(0)
            .setDuration((650f / speed).toLong())
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    /** Dock dan search pill turun keluar. */
    fun barOut(v: View, dist: Float, speed: Float) {
        v.animate().cancel()
        v.animate().translationY(dist).alpha(0f)
            .setStartDelay(0)
            .setDuration((380f / speed).toLong())
            .setInterpolator(AccelerateInterpolator(1.4f))
            .start()
    }
}
