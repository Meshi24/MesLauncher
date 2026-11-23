package com.example.simplelauncher

import android.app.WallpaperManager
import android.content.Context

data class Placed(val key: String, val col: Int, val row: Int, val w: Int = 1, val h: Int = 1)
data class Folder(val id: String, val name: String, val keys: List<String>)

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
            val w = p.getOrNull(3)?.toIntOrNull() ?: 1
            val h = p.getOrNull(4)?.toIntOrNull() ?: 1
            if (p.size >= 3 && col != null && row != null) {
                Placed(p[0], col, row, w.coerceAtLeast(1), h.coerceAtLeast(1))
            } else null
        }
    }

    fun saveHome(c: Context, l: List<Placed>) {
        sp(c).edit().putString(
            "home",
            l.joinToString("\n") { "${it.key}|${it.col}|${it.row}|${it.w}|${it.h}" }
        ).apply()
    }

    fun dock(c: Context): List<String> =
        (sp(c).getString("dock", "") ?: "").lines().filter { it.isNotBlank() }

    fun saveDock(c: Context, l: List<String>) {
        sp(c).edit().putString("dock", l.joinToString("\n")).apply()
    }

    fun folders(c: Context): List<Folder> {
        val raw = sp(c).getString("folders", "") ?: ""
        return raw.lines().mapNotNull { line ->
            val p = line.split("|")
            if (p.size == 3 && p[0].isNotBlank()) {
                Folder(p[0], p[1], p[2].split(",").filter { it.isNotBlank() })
            } else null
        }
    }

    fun saveFolders(c: Context, l: List<Folder>) {
        sp(c).edit().putString(
            "folders",
            l.joinToString("\n") { "${it.id}|${it.name}|${it.keys.joinToString(",")}" }
        ).apply()
    }
}
