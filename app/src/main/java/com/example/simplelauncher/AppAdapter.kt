package com.example.simplelauncher

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

data class App(val label: String, val pkg: String, val cls: String, val icon: Drawable)

/** Press feedback: icon shrinks to 0.9 with a spring, bounces back on release. */
fun View.pressSpring() {
    setOnTouchListener { v, e ->
        val target = when (e.action) {
            MotionEvent.ACTION_DOWN -> 0.9f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1f
            else -> return@setOnTouchListener false
        }
        listOf(DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y).forEach { prop ->
            SpringAnimation(v, prop, target).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }.start()
        }
        false
    }
}

class AppAdapter(private val onClick: (App, View) -> Unit) :
    RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<App> = emptyList()

    fun submit(list: List<App>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val root: LinearLayout, val icon: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt())
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.28f)
                }
            }
            clipToOutline = true
        }
        val label = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (6 * d).toInt() }
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val p = (10 * d).toInt()
            setPadding(p / 2, p, p / 2, p)
            addView(icon)
            addView(label)
        }
        return VH(root, icon, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = items[position]
        h.icon.setImageDrawable(app.icon)
        h.label.text = app.label
        h.root.setOnClickListener { onClick(app, h.icon) }
        h.root.pressSpring()
    }

    override fun getItemCount() = items.size
}
