package com.example.simplelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

class PackInfo(val pkg: String, val label: String)

class IconPack(val pkg: String, private val ctx: Context, private val map: Map<String, String>) {
    private val res = ctx.resources

    fun icon(appPkg: String, cls: String): Drawable? {
        val name = map["$appPkg/$cls"] ?: map["$appPkg/*"] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(res, id, null)
        } catch (e: Exception) {
            null
        }
    }
}

object IconPacks {
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME"
    )

    fun list(pm: PackageManager): List<PackInfo> {
        val seen = LinkedHashMap<String, PackInfo>()
        for (a in actions) {
            val l = try {
                pm.queryIntentActivities(Intent(a), 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in l) {
                val p = ri.activityInfo.packageName
                if (!seen.containsKey(p)) seen[p] = PackInfo(p, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun load(ctx: Context, pkg: String): IconPack? {
        if (pkg.isBlank()) return null
        return try {
            val pctx = ctx.createPackageContext(pkg, 0)
            val id = pctx.resources.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) {
                pctx.resources.getXml(id)
            } else {
                val ins = pctx.assets.open("appfilter.xml")
                val p = Xml.newPullParser()
                p.setInput(ins, null)
                p
            }
            val map = HashMap<String, String>()
            parse(parser, map)
            IconPack(pkg, pctx, map)
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(p: XmlPullParser, map: MutableMap<String, String>) {
        val rx = Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}")
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val dr = p.getAttributeValue(null, "drawable")
                if (comp != null && dr != null) {
                    val m = rx.find(comp)
                    if (m != null) {
                        val pk = m.groupValues[1]
                        var cls = m.groupValues[2]
                        if (cls.startsWith(".")) cls = pk + cls
                        map["$pk/$cls"] = dr
                        if (!map.containsKey("$pk/*")) map["$pk/*"] = dr
                    }
                }
            }
            ev = p.next()
        }
    }
}
