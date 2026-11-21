package com.example.simplelauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(24), dp(20), dp(40))
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF121212.toInt())
        sv.addView(box)
        setContentView(sv)
        ViewCompat.setOnApplyWindowInsetsListener(sv) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        box.addView(tv("Setelan MesLauncher", 22f, true))

        box.addView(tv("Home grid", 17f, true, 24))
        val colsLabel = tv("", 15f)
        box.addView(colsLabel)
        box.addView(seek(colsLabel, "Kolom", Prefs.cols(this)) { Prefs.setCols(this, it) })
        val rowsLabel = tv("", 15f)
        box.addView(rowsLabel)
        box.addView(seek(rowsLabel, "Baris", Prefs.rows(this)) { Prefs.setRows(this, it) })

        box.addView(tv("Gaya app drawer", 17f, true, 24))
        box.addView(
            radios(
                1000,
                listOf("Folder (kategori, kayak App Library)", "Drawer biasa (grid)", "Satu-satu (list A-Z)"),
                Prefs.drawerStyle(this)
            ) { Prefs.setDrawerStyle(this, it) }
        )

        box.addView(tv("Warna dock", 17f, true, 24))
        box.addView(
            radios(
                2000,
                listOf("Otomatis (ikut wallpaper)", "Putih transparan", "Hitam transparan"),
                Prefs.dockTint(this)
            ) { Prefs.setDockTint(this, it) }
        )

        box.addView(tv("Perubahan langsung dipakai pas balik ke home.", 13f, false, 24, 0xFF9E9E9E.toInt()))
    }

    private fun tv(t: String, sp: Float, bold: Boolean = false, top: Int = 0, color: Int = Color.WHITE): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        v.setTextColor(color)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        v.setPadding(0, dp(top), 0, dp(6))
        return v
    }

    private fun seek(label: TextView, name: String, value: Int, save: (Int) -> Unit): SeekBar {
        label.text = "$name: $value"
        val s = SeekBar(this)
        s.max = 9
        s.progress = value - 1
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = "$name: ${p + 1}"
                save(p + 1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    private fun radios(base: Int, options: List<String>, current: Int, onPick: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        options.forEachIndexed { i, t ->
            val rb = RadioButton(this)
            rb.id = base + i
            rb.text = t
            rb.setTextColor(Color.WHITE)
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            g.addView(rb)
        }
        g.check(base + current)
        g.setOnCheckedChangeListener { _, id -> onPick(id - base) }
        return g
    }
}
