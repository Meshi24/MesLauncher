package com.example.simplelauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.CheckBox
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

        box.addView(tv("Home", 17f, true, 24))
        seek(box, "Kolom", "", 1, 10, 1, Prefs.cols(this)) { Prefs.setCols(this, it) }
        seek(box, "Baris", "", 1, 10, 1, Prefs.rows(this)) { Prefs.setRows(this, it) }
        seek(box, "Jumlah halaman", "", 1, 10, 1, Prefs.pages(this)) { Prefs.setPages(this, it) }

        box.addView(tv("Posisi grid di home", 17f, true, 24))
        box.addView(tv("Rapatkan ke (horizontal)", 14f, false, 0, 0xFF9E9E9E.toInt()))
        box.addView(
            radios(3000, listOf("Penuh (melebar)", "Kiri", "Tengah", "Kanan"), Prefs.hAlign(this)) {
                Prefs.setHAlign(this, it)
            }
        )
        box.addView(tv("Rapatkan ke (vertikal)", 14f, false, 8, 0xFF9E9E9E.toInt()))
        box.addView(
            radios(4000, listOf("Penuh (memanjang)", "Atas", "Tengah", "Bawah"), Prefs.vAlign(this)) {
                Prefs.setVAlign(this, it)
            }
        )

        box.addView(tv("Ikon", 17f, true, 24))
        seek(box, "Ukuran ikon", "%", 60, 140, 5, Prefs.iconScale(this)) { Prefs.setIconScale(this, it) }
        seek(box, "Ukuran label", "%", 70, 140, 5, Prefs.labelScale(this)) { Prefs.setLabelScale(this, it) }
        val cb = CheckBox(this)
        cb.text = "Tampilkan label ikon"
        cb.setTextColor(Color.WHITE)
        cb.isChecked = Prefs.showLabels(this)
        cb.setOnCheckedChangeListener { _, b -> Prefs.setShowLabels(this, b) }
        box.addView(cb)

        box.addView(tv("Animasi", 17f, true, 24))
        seek(box, "Kecepatan animasi", "%", 50, 200, 10, Prefs.animSpeed(this)) { Prefs.setAnimSpeed(this, it) }
        box.addView(tv("Makin kecil makin pelan.", 12f, false, 0, 0xFF9E9E9E.toInt()))

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

    private fun seek(
        box: LinearLayout,
        title: String,
        unit: String,
        min: Int,
        max: Int,
        step: Int,
        value: Int,
        save: (Int) -> Unit
    ) {
        val label = tv("", 15f)
        box.addView(label)
        val v0 = value.coerceIn(min, max)
        label.text = "$title: $v0$unit"
        val s = SeekBar(this)
        s.max = (max - min) / step
        s.progress = (v0 - min) / step
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = min + p * step
                label.text = "$title: $v$unit"
                save(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(s)
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
