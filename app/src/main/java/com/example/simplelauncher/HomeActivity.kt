package com.example.simplelauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class HomeActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var allApps: List<App> = emptyList()

    private lateinit var root: TouchFrame
    private lateinit var homeLayer: FrameLayout
    private lateinit var homeGrid: HomeGrid
    private lateinit var dockBar: LinearLayout
    private lateinit var pill: TextView
    private lateinit var drawerLayer: FrameLayout
    private lateinit var searchBox: EditText
    private lateinit var drawerContent: FrameLayout

    private var insTop = 0
    private var insBottom = 0
    private var lightWp = false
    private var drawerOpen = false
    private var drawerScroll: View? = null
    private var overlay: View? = null
    private var overlayScroll: View? = null
    private var pendingKey: String? = null
    private var pendingOrig: Placed? = null
    private var firstResume = true

    private var downX = 0f
    private var downY = 0f
    private var atTopOnDown = true
    private var ignoreSwipe = false

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = TouchFrame(this) { ev -> onSwipe(ev) }
        buildHome()
        buildDrawer()
        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawerLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insTop = b.top
            insBottom = b.bottom
            applyInsets()
            insets
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        loadApps()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        super.onDestroy()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun lp(v: View) = v.layoutParams as FrameLayout.LayoutParams

    private fun buildHome() {
        homeLayer = FrameLayout(this)

        homeGrid = HomeGrid(this)
        homeGrid.onCellTap = { c, r -> placePending(c, r) }
        homeGrid.setOnLongClickListener { showHomeMenu(); true }
        homeLayer.addView(homeGrid, FrameLayout.LayoutParams(MATCH, MATCH))

        pill = TextView(this)
        pill.text = "Cari app"
        pill.textSize = 13f
        pill.gravity = Gravity.CENTER
        pill.setPadding(dp(22), 0, dp(22), 0)
        pill.setOnClickListener { openDrawer(true) }
        homeLayer.addView(
            pill,
            FrameLayout.LayoutParams(WRAP, dp(32), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )

        dockBar = LinearLayout(this)
        dockBar.orientation = LinearLayout.HORIZONTAL
        dockBar.gravity = Gravity.CENTER_VERTICAL
        dockBar.setPadding(dp(10), 0, dp(10), 0)
        homeLayer.addView(dockBar, FrameLayout.LayoutParams(MATCH, dp(84), Gravity.BOTTOM))
    }

    private fun buildDrawer() {
        drawerLayer = FrameLayout(this)
        drawerLayer.setBackgroundColor(0xE6101014.toInt())
        drawerLayer.visibility = View.GONE
        drawerLayer.isClickable = true

        searchBox = EditText(this)
        searchBox.hint = "Cari app"
        searchBox.setHintTextColor(0x99FFFFFF.toInt())
        searchBox.setTextColor(Color.WHITE)
        searchBox.setSingleLine(true)
        searchBox.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchBox.background = rounded(0x33FFFFFF, dp(22).toFloat())
        searchBox.setPadding(dp(18), 0, dp(18), 0)
        searchBox.doAfterTextChanged { renderDrawer() }
        searchBox.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        val slp = FrameLayout.LayoutParams(MATCH, dp(44))
        slp.marginStart = dp(16)
        slp.marginEnd = dp(16)
        drawerLayer.addView(searchBox, slp)

        drawerContent = FrameLayout(this)
        drawerLayer.addView(drawerContent, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun applyInsets() {
        lp(homeGrid).topMargin = insTop + dp(8)
        lp(homeGrid).bottomMargin = insBottom + dp(150)
        lp(dockBar).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            bottomMargin = insBottom + dp(10)
        }
        lp(pill).bottomMargin = insBottom + dp(10 + 84 + 8)
        lp(searchBox).topMargin = insTop + dp(12)
        lp(drawerContent).topMargin = insTop + dp(68)
        homeGrid.requestLayout()
        dockBar.requestLayout()
        pill.requestLayout()
        searchBox.requestLayout()
        drawerContent.requestLayout()
        if (allApps.isNotEmpty()) renderDrawer()
    }

    // ------------------------------------------------------------ data load

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    App(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                        it.loadIcon(pm),
                        it.activityInfo.applicationInfo.category
                    )
                }
                .sortedWith(compareBy<App>({ letterOf(it.label) == "#" }, { it.label.lowercase() }))
            runOnUiThread {
                allApps = list
                applySettings()
            }
        }.start()
    }

    // -------------------------------------------------------- render: home

    private fun dockLight(): Boolean = when (Prefs.dockTint(this)) {
        1 -> true
        2 -> false
        else -> lightWp
    }

    private fun applySettings() {
        lightWp = wallpaperIsLight(this)
        homeGrid.cols = Prefs.cols(this)
        homeGrid.rows = Prefs.rows(this)
        renderHome()
        renderDock()
        val light = dockLight()
        pill.background = rounded(if (light) 0x66FFFFFF else 0x66000000, dp(16).toFloat())
        pill.setTextColor(if (light) 0xFF111111.toInt() else Color.WHITE)
        if (drawerOpen) renderDrawer()
    }

    private fun renderHome() {
        homeGrid.removeAllViews()
        val cols = homeGrid.cols
        val lc = if (lightWp) 0xFF111111.toInt() else Color.WHITE
        for (p in Prefs.home(this)) {
            if (p.col >= cols || p.row >= homeGrid.rows) continue
            val app = allApps.firstOrNull { it.key == p.key } ?: continue
            val cell = IconCell(this, lc, true, cols <= 6)
            cell.bind(app)
            cell.layoutParams = HomeGrid.LP(p.col, p.row)
            cell.setOnClickListener { launchApp(app, cell.icon) }
            cell.setOnLongClickListener { homeAppMenu(app); true }
            cell.pressSpring()
            homeGrid.addView(cell)
        }
        homeGrid.requestLayout()
        homeGrid.invalidate()
    }

    private fun renderDock() {
        dockBar.background = rounded(
            if (dockLight()) 0x66FFFFFF else 0x66000000,
            dp(30).toFloat()
        )
        dockBar.removeAllViews()
        val apps = Prefs.dock(this).mapNotNull { k -> allApps.firstOrNull { it.key == k } }
        if (apps.isEmpty()) {
            val h = TextView(this)
            h.text = "Dock kosong. Tahan lama app di drawer, lalu pilih Tambah ke dock"
            h.setTextColor(if (dockLight()) 0xFF111111.toInt() else Color.WHITE)
            h.textSize = 12f
            h.gravity = Gravity.CENTER
            dockBar.addView(h, LinearLayout.LayoutParams(MATCH, MATCH))
            return
        }
        for (a in apps) {
            val cell = IconCell(this, Color.WHITE, true, false)
            cell.bind(a)
            cell.setOnClickListener { launchApp(a, cell.icon) }
            cell.setOnLongClickListener { dockMenu(a); true }
            cell.pressSpring()
            dockBar.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    }

    // ------------------------------------------------------- home actions

    private fun startPlace(key: String) {
        val cur = Prefs.home(this)
        pendingOrig = cur.firstOrNull { it.key == key }
        Prefs.saveHome(this, cur.filter { it.key != key })
        pendingKey = key
        renderHome()
        closeDrawer(true)
        homeGrid.moving = true
        Toast.makeText(this, "Tap kotak kosong buat naruh ikon", Toast.LENGTH_SHORT).show()
    }

    private fun placePending(c: Int, r: Int) {
        val key = pendingKey ?: return
        if (Prefs.home(this).any { it.col == c && it.row == r }) return
        Prefs.saveHome(this, Prefs.home(this) + Placed(key, c, r))
        endMove()
        renderHome()
    }

    private fun endMove() {
        pendingKey = null
        pendingOrig = null
        homeGrid.moving = false
    }

    private fun cancelMove() {
        val o = pendingOrig
        if (o != null) Prefs.saveHome(this, Prefs.home(this) + o)
        endMove()
        renderHome()
    }

    private fun homeAppMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Pindah", "Hapus dari home", "Info app")) { _, w ->
                when (w) {
                    0 -> startPlace(app.key)
                    1 -> {
                        Prefs.saveHome(this, Prefs.home(this).filter { it.key != app.key })
                        renderHome()
                    }
                    2 -> openInfo(app.pkg)
                    else -> {}
                }
            }.show()
    }

    private fun dockMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Hapus dari dock", "Info app")) { _, w ->
                when (w) {
                    0 -> {
                        Prefs.saveDock(this, Prefs.dock(this).filter { it != app.key })
                        renderDock()
                    }
                    1 -> openInfo(app.pkg)
                    else -> {}
                }
            }.show()
    }

    private fun addToDock(app: App) {
        val cur = Prefs.dock(this)
        if (cur.contains(app.key)) {
            Toast.makeText(this, "Sudah ada di dock", Toast.LENGTH_SHORT).show()
            return
        }
        if (cur.size >= 4) {
            Toast.makeText(this, "Dock penuh (maks 4)", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.saveDock(this, cur + app.key)
        renderDock()
        Toast.makeText(this, "Ditambah ke dock", Toast.LENGTH_SHORT).show()
    }

    private fun showHomeMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Setelan launcher", "Ganti wallpaper")) { _, w ->
                if (w == 0) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    try {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER), "Ganti wallpaper"
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                    }
                }
            }.show()
    }

    private fun openInfo(pkg: String) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        )
    }

    private fun uninstall(pkg: String) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    private fun appMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Tambah ke home", "Tambah ke dock", "Info app", "Uninstall")) { _, w ->
                when (w) {
                    0 -> startPlace(app.key)
                    1 -> addToDock(app)
                    2 -> openInfo(app.pkg)
                    3 -> uninstall(app.pkg)
                    else -> {}
                }
            }.show()
    }

    private fun launchApp(app: App, view: View) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.pkg, app.cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val opts = ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height)
        try {
            startActivity(intent, opts.toBundle())
        } catch (e: ActivityNotFoundException) {
            loadApps()
        }
    }

    // ------------------------------------------------------- swipe + drawer

    private fun onSwipe(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ignoreSwipe = false
                downX = ev.rawX
                downY = ev.rawY
                val sc = overlayScroll ?: drawerScroll
                atTopOnDown = !(sc?.canScrollVertically(-1) ?: false)
            }
            MotionEvent.ACTION_UP -> {
                if (ignoreSwipe) return
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dy) < abs(dx) * 1.5f) return
                if (!drawerOpen && !homeGrid.moving && dy < -dp(90)) {
                    openDrawer(false)
                } else if (drawerOpen && atTopOnDown && dy > dp(110)) {
                    if (overlay != null) closeOverlay() else closeDrawer(true)
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun focusSearch() {
        searchBox.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchBox, 0)
    }

    private fun openDrawer(focus: Boolean) {
        if (drawerOpen) {
            if (focus) focusSearch()
            return
        }
        drawerOpen = true
        searchBox.setText("")
        renderDrawer()
        drawerLayer.animate().cancel()
        drawerLayer.visibility = View.VISIBLE
        drawerLayer.translationY = root.height.toFloat()
        drawerLayer.animate().translationY(0f).setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (focus && drawerOpen) focusSearch() }
            .start()
        homeLayer.animate().cancel()
        homeLayer.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(280).start()
    }

    private fun closeDrawer(animate: Boolean) {
        if (!drawerOpen) return
        drawerOpen = false
        closeOverlay()
        hideKeyboard()
        searchBox.clearFocus()
        drawerLayer.animate().cancel()
        homeLayer.animate().cancel()
        if (!animate) {
            drawerLayer.visibility = View.GONE
            homeLayer.alpha = 1f
            homeLayer.scaleX = 1f
            homeLayer.scaleY = 1f
            return
        }
        drawerLayer.animate().translationY(root.height.toFloat()).setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (!drawerOpen) drawerLayer.visibility = View.GONE }
            .start()
        homeLayer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
    }

    // -------------------------------------------------------- render: drawer

    private fun renderDrawer() {
        if (!::drawerContent.isInitialized) return
        closeOverlay()
        drawerContent.removeAllViews()
        drawerScroll = null
        val q = searchBox.text.toString().trim().lowercase()
        val apps = if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) }
        val style = Prefs.drawerStyle(this)
        when {
            q.isEmpty() && style == 0 -> renderFolders(apps)
            style == 2 -> renderAz(apps)
            else -> {
                val rv = buildGrid(apps)
                drawerContent.addView(rv, FrameLayout.LayoutParams(MATCH, MATCH))
                drawerScroll = rv
            }
        }
    }

    private fun buildGrid(apps: List<App>): RecyclerView {
        val rv = RecyclerView(this)
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.clipToPadding = false
        rv.setPadding(dp(8), 0, dp(8), insBottom + dp(16))
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        val ad = GridAdapter({ a, v -> launchApp(a, v) }, { a -> appMenu(a) })
        rv.adapter = ad
        ad.submit(apps)
        return rv
    }

    private fun renderAz(apps: List<App>) {
        val rv = RecyclerView(this)
        val lm = LinearLayoutManager(this)
        rv.layoutManager = lm
        rv.clipToPadding = false
        rv.setPadding(dp(16), 0, dp(40), insBottom + dp(16))
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        val ad = AzAdapter({ a, v -> launchApp(a, v) }, { a -> appMenu(a) })
        rv.adapter = ad
        ad.submit(apps)
        drawerContent.addView(rv, FrameLayout.LayoutParams(MATCH, MATCH))

        val letters = ad.letters().distinct()
        val idx = LinearLayout(this)
        idx.orientation = LinearLayout.VERTICAL
        idx.gravity = Gravity.CENTER_HORIZONTAL
        for (l in letters) {
            val t = TextView(this)
            t.text = l
            t.setTextColor(0xFFBBBBBB.toInt())
            t.textSize = 10f
            t.gravity = Gravity.CENTER
            idx.addView(t, LinearLayout.LayoutParams(dp(24), 0, 1f))
        }
        idx.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) ignoreSwipe = true
            if (letters.isNotEmpty() && v.height > 0) {
                val i = ((e.y / v.height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                val pos = ad.positionOf(letters[i])
                if (pos >= 0) lm.scrollToPositionWithOffset(pos, 0)
            }
            true
        }
        val ilp = FrameLayout.LayoutParams(dp(24), MATCH, Gravity.END)
        ilp.topMargin = dp(8)
        ilp.bottomMargin = insBottom + dp(16)
        drawerContent.addView(idx, ilp)
        drawerScroll = rv
    }

    private fun folderName(cat: Int): String = when (cat) {
        ApplicationInfo.CATEGORY_GAME -> "Game"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Musik & Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Foto"
        ApplicationInfo.CATEGORY_SOCIAL -> "Sosial"
        ApplicationInfo.CATEGORY_NEWS -> "Berita"
        ApplicationInfo.CATEGORY_MAPS -> "Peta"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produktivitas"
        else -> "Lainnya"
    }

    private fun renderFolders(apps: List<App>) {
        val groups = apps.groupBy { folderName(it.cat) }
        val order = listOf(
            "Sosial", "Musik & Video", "Foto", "Game",
            "Produktivitas", "Peta", "Berita", "Lainnya"
        )
        val tiles = ArrayList<Pair<String, List<App>>>()
        tiles.add("Semua app" to apps)
        for (n in order) {
            val g = groups[n]
            if (g != null) tiles.add(n to g)
        }

        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.setPadding(dp(16), dp(4), dp(16), insBottom + dp(16))
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val t = tiles.getOrNull(i + j)
                if (t != null) {
                    row.addView(folderTile(t.first, t.second), LinearLayout.LayoutParams(0, WRAP, 1f))
                } else {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            i += 2
        }
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        drawerContent.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        drawerScroll = sv
    }

    private fun folderTile(name: String, apps: List<App>): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        tile.setPadding(dp(8), dp(8), dp(8), dp(8))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.background = rounded(0x22FFFFFF, dp(26).toFloat())
        for (r in 0..1) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0..1) {
                val a = apps.getOrNull(r * 2 + c)
                val iv = ImageView(this)
                iv.outlineProvider = roundOutline()
                iv.clipToOutline = true
                if (a != null) iv.setImageDrawable(a.iconCopy())
                val ilp = LinearLayout.LayoutParams(dp(44), dp(44))
                ilp.setMargins(dp(4), dp(4), dp(4), dp(4))
                line.addView(iv, ilp)
            }
            box.addView(line)
        }
        tile.addView(box, LinearLayout.LayoutParams(WRAP, WRAP))

        val label = TextView(this)
        label.text = "$name (${apps.size})"
        label.setTextColor(Color.WHITE)
        label.textSize = 12f
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        val llp = LinearLayout.LayoutParams(WRAP, WRAP)
        llp.topMargin = dp(6)
        tile.addView(label, llp)

        tile.setOnClickListener { openFolder(name, apps) }
        tile.pressSpring()
        return tile
    }

    private fun openFolder(name: String, apps: List<App>) {
        closeOverlay()
        val ov = FrameLayout(this)
        ov.setBackgroundColor(0xF0101014.toInt())
        ov.isClickable = true
        val title = TextView(this)
        title.text = name
        title.setTextColor(Color.WHITE)
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setPadding(dp(20), dp(8), dp(20), dp(8))
        ov.addView(title, FrameLayout.LayoutParams(WRAP, WRAP))
        val rv = buildGrid(apps)
        val rlp = FrameLayout.LayoutParams(MATCH, MATCH)
        rlp.topMargin = dp(44)
        ov.addView(rv, rlp)
        drawerContent.addView(ov, FrameLayout.LayoutParams(MATCH, MATCH))
        overlay = ov
        overlayScroll = rv
        ov.alpha = 0f
        ov.animate().alpha(1f).setDuration(150).start()
    }

    private fun closeOverlay() {
        val o = overlay
        if (o != null) (o.parent as? ViewGroup)?.removeView(o)
        overlay = null
        overlayScroll = null
    }

    // ------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        applySettings()
        if (firstResume) {
            firstResume = false
            return
        }
        if (drawerOpen) closeDrawer(false)
        homeLayer.scaleX = 0.92f
        homeLayer.scaleY = 0.92f
        homeLayer.alpha = 0f
        homeLayer.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(250).setInterpolator(DecelerateInterpolator()).start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            closeOverlay()
            if (homeGrid.moving) cancelMove()
            if (drawerOpen) closeDrawer(true)
        }
    }

    @Deprecated("Launcher stays open")
    override fun onBackPressed() {
        when {
            overlay != null -> closeOverlay()
            homeGrid.moving -> cancelMove()
            drawerOpen -> closeDrawer(true)
        }
    }
}
