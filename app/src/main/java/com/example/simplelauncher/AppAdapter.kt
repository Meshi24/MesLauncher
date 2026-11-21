package com.example.simplelauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
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

data class App(
    val label: String,
    val pkg: String,
    val cls: String,
    val icon: Drawable,
    val cat: Int
) {
    val key: String get() = "$pkg/$cls"
    fun iconCopy(): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
}

fun roundOutline(ratio: Float = 0.28f) = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, view.width * ratio)
    }
}

fun letterOf(s: String): String {
    val c = s.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

/** Press feedback: ikon mengecil ke 0.9 pakai spring. */
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

/** Satu sel ikon + label. fill=true: ukuran ikon ikut ukuran sel (dipakai di home & dock). */
class IconCell(
    ctx: Context,
    labelColor: Int,
    private val fill: Boolean,
    showLabel: Boolean = true
) : LinearLayout(ctx) {

    val icon = ImageView(ctx)
    val label = TextView(ctx)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        icon.layoutParams = LayoutParams(ctx.dp(56), ctx.dp(56))
        icon.outlineProvider = roundOutline()
        icon.clipToOutline = true
        label.setTextColor(labelColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        if (labelColor == 0xFFFFFFFF.toInt()) label.setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = ctx.dp(5)
        label.layoutParams = lp
        addView(icon)
        addView(label)
        if (!showLabel) label.visibility = View.GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (fill) {
            val w = View.MeasureSpec.getSize(widthMeasureSpec)
            val h = View.MeasureSpec.getSize(heightMeasureSpec)
            if (w > 0 && h > 0) {
                val s = minOf((w * 0.62f).toInt(), (h * 0.58f).toInt(), context.dp(60))
                val p = icon.layoutParams
                p.width = s
                p.height = s
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

fun IconCell.bind(app: App) {
    icon.setImageDrawable(app.iconCopy())
    label.text = app.label
}

/** Adapter grid (drawer biasa + isi folder). */
class GridAdapter(
    private val onClick: (App, View) -> Unit,
    private val onLong: (App) -> Unit
) : RecyclerView.Adapter<GridAdapter.VH>() {

    private var items: List<App> = emptyList()

    fun submit(list: List<App>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val cell: IconCell) : RecyclerView.ViewHolder(cell)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val c = IconCell(ctx, Color.WHITE, false)
        c.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val pad = ctx.dp(10)
        c.setPadding(pad / 2, pad, pad / 2, pad)
        return VH(c)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = items[position]
        h.cell.bind(a)
        h.cell.setOnClickListener { onClick(a, h.cell.icon) }
        h.cell.setOnLongClickListener { onLong(a); true }
        h.cell.pressSpring()
    }

    override fun getItemCount() = items.size
}

data class Row(val letter: String?, val app: App?)

/** Adapter list A-Z (gaya "satu-satu"). */
class AzAdapter(
    private val onClick: (App, View) -> Unit,
    private val onLong: (App) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()

    fun submit(apps: List<App>) {
        val out = ArrayList<Row>()
        var last = ""
        for (a in apps) {
            val l = letterOf(a.label)
            if (l != last) {
                out.add(Row(l, null))
                last = l
            }
            out.add(Row(null, a))
        }
        rows = out
        notifyDataSetChanged()
    }

    fun letters(): List<String> = rows.mapNotNull { it.letter }
    fun positionOf(letter: String): Int = rows.indexOfFirst { it.letter == letter }

    class HeadVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    class RowVH(val root: LinearLayout, val icon: ImageView, val text: TextView) :
        RecyclerView.ViewHolder(root)

    override fun getItemViewType(position: Int) = if (rows[position].letter != null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        if (viewType == 0) {
            val tv = TextView(ctx)
            tv.setTextColor(0xFFFFB74D.toInt())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tv.setTypeface(null, Typeface.BOLD)
            tv.setPadding(ctx.dp(8), ctx.dp(12), 0, ctx.dp(4))
            tv.layoutParams = RecyclerView.LayoutParams(match, wrap)
            return HeadVH(tv)
        }
        val icon = ImageView(ctx)
        icon.layoutParams = LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40))
        icon.outlineProvider = roundOutline()
        icon.clipToOutline = true
        val text = TextView(ctx)
        text.setTextColor(Color.WHITE)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        text.maxLines = 1
        text.ellipsize = TextUtils.TruncateAt.END
        val tlp = LinearLayout.LayoutParams(0, wrap, 1f)
        tlp.marginStart = ctx.dp(14)
        text.layoutParams = tlp
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(6))
        root.layoutParams = RecyclerView.LayoutParams(match, wrap)
        root.addView(icon)
        root.addView(text)
        return RowVH(root, icon, text)
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val r = rows[position]
        if (h is HeadVH) {
            h.tv.text = r.letter
            return
        }
        val v = h as RowVH
        val a = r.app ?: return
        v.icon.setImageDrawable(a.iconCopy())
        v.text.text = a.label
        v.root.setOnClickListener { onClick(a, v.icon) }
        v.root.setOnLongClickListener { onLong(a); true }
        v.root.pressSpring()
    }

    override fun getItemCount() = rows.size
}
