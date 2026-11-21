package com.example.simplelauncher

import android.app.WallpaperManager
import android.content.Context

data class Placed(val key: String, val col: Int, val row: Int)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun wallpaperIsLight(c: Context): Boolean = try {
    val wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    if (wc == null) false else {
        val cs = listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor)
        cs.map { it.luminance() }.average() > 0.5
    }
} catch (e: Exception) {
    false
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("mes", Context.MODE_PRIVATE)

    fun cols(c: Context) = sp(c).getInt("cols", 4)
    fun rows(c: Context) = sp(c).getInt("rows", 6)
    fun setCols(c: Context, v: Int) = sp(c).edit().putInt("cols", v).apply()
    fun setRows(c: Context, v: Int) = sp(c).edit().putInt("rows", v).apply()

    fun drawerStyle(c: Context) = sp(c).getInt("drawer", 0)
    fun setDrawerStyle(c: Context, v: Int) = sp(c).edit().putInt("drawer", v).apply()

    fun dockTint(c: Context) = sp(c).getInt("tint", 0)
    fun setDockTint(c: Context, v: Int) = sp(c).edit().putInt("tint", v).apply()

    fun home(c: Context): List<Placed> {
        val raw = sp(c).getString("home", "") ?: ""
        return raw.lines().mapNotNull { line ->
            val p = line.split("|")
            val col = p.getOrNull(1)?.toIntOrNull()
            val row = p.getOrNull(2)?.toIntOrNull()
            if (p.size == 3 && col != null && row != null) Placed(p[0], col, row) else null
        }
    }

    fun saveHome(c: Context, l: List<Placed>) {
        sp(c).edit().putString("home", l.joinToString("\n") { "${it.key}|${it.col}|${it.row}" }).apply()
    }

    fun dock(c: Context): List<String> =
        (sp(c).getString("dock", "") ?: "").lines().filter { it.isNotBlank() }

    fun saveDock(c: Context, l: List<String>) {
        sp(c).edit().putString("dock", l.joinToString("\n")).apply()
    }
}
