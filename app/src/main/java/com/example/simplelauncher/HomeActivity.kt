package com.example.simplelauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil

class HomeActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val sizes = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)

    private val HOST_ID = 4242
    private val REQ_BIND = 71
    private val REQ_CONFIG = 72

    private class Pick(val keys: List<String>, val fromHome: Boolean, val menu: () -> Unit)

    private var allApps: List<App> = emptyList()
    private var loadedPack = ""

    private lateinit var root: DragFrame
    private lateinit var homeLayer: FrameLayout
    private lateinit var pager: Pager
    private val grids = ArrayList<HomeGrid>()
    private lateinit var bottomBar: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var dockBar: LinearLayout
    private lateinit var pill: TextView
    private lateinit var drawerLayer: FrameLayout
    private lateinit var searchBox: EditText
    private lateinit var drawerContent: FrameLayout

    // widget
    private lateinit var awm: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private val widgetViews = HashMap<Int, AppWidgetHostView>()
    private val stackIndex = HashMap<String, Int>()
    private var pendWidgetId = -1
    private var pendProvider: AppWidgetProviderInfo? = null
    private var pendStackKey: String? = null

    private var insTop = 0
    private var insBottom = 0
    private var lightWp = false
    private var drawerOpen = false
    private var drawerScroll: View? = null
    private var overlay: View? = null
    private var overlayScroll: View? = null
    private var homeFolder: View? = null
    private var pendingKey: String? = null
    private var pendingOrig: Placed? = null
    private var pendW = 1
    private var pendH = 1
    private var firstResume = true

    // status animasi ikon
    private var paused = false
    private var scattered = false
    private var waitingUnlock = false
    private var focusLost = false
    private var dialogs = 0

    // status gesture drawer
    private var atTopOnDown = true
    private var ignoreSwipe = false
    private var dragOpening = false

    // status drag and drop ikon
    private var pick: Pick? = null
    private var dragging = false
    private var dragGhost: View? = null
    private var dragOthers: List<Placed> = emptyList()
    private var dragOrig: List<Placed> = emptyList()
    private var dragTarget: IntArray? = null
    private var dragX = 0f
    private var dragY = 0f
    private var leadW = 1
    private var leadH = 1
    private var edgeSince = 0L

    // pilih beberapa
    private var selecting = false
    private val selected = LinkedHashSet<String>()
    private var selBar: View? = null
    private var selCount: TextView? = null

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (waitingUnlock && !paused) {
                waitingUnlock = false
                root.post { playGather() }
            }
        }
    }

    private val dragTick = object : Runnable {
        override fun run() {
            if (!dragging) return
            val edge = dp(40)
            val w = root.width
            val now = System.currentTimeMillis()
            val inLeft = dragX < edge
            val inRight = dragX > w - edge
            if (inLeft || inRight) {
                if (edgeSince == 0L) {
                    edgeSince = now
                } else if (now - edgeSince > 550) {
                    val p = pager.currentPage()
                    pager.goTo(if (inLeft) p - 1 else p + 1)
                    edgeSince = now
                }
            } else {
                edgeSince = 0L
            }
            updateHint()
            root.postDelayed(this, 100)
        }
    }

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false

        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID)

        root = DragFrame(this)
        setupGestures()
        buildHome()
        buildDrawer()
        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawerLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        applyInsets()

        pager.lockCheck = { x, y -> stackAt(x, y) }

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
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED
        )
        loadApps()
    }

    override fun onStart() {
        super.onStart()
        try {
            host.startListening()
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun lp(v: View) = v.layoutParams as FrameLayout.LayoutParams

    private fun AlertDialog.Builder.showT() {
        val d = create()
        dialogs++
        d.setOnDismissListener { dialogs = maxOf(0, dialogs - 1) }
        d.show()
    }

    private fun buildHome() {
        homeLayer = FrameLayout(this)

        pager = Pager(this)
        pager.onPageChanged = { renderDots() }
        homeLayer.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))

        bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.VERTICAL
        bottomBar.gravity = Gravity.CENTER_HORIZONTAL

        dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        bottomBar.addView(dots, LinearLayout.LayoutParams(WRAP, dp(14)))

        pill = TextView(this)
        pill.text = "Cari app"
        pill.textSize = 13f
        pill.gravity = Gravity.CENTER
        pill.setPadding(dp(22), 0, dp(22), 0)
        pill.setOnClickListener { openDrawer(true) }
        val plp = LinearLayout.LayoutParams(WRAP, dp(32))
        plp.bottomMargin = dp(8)
        bottomBar.addView(pill, plp)

        dockBar = LinearLayout(this)
        dockBar.orientation = LinearLayout.HORIZONTAL
        dockBar.gravity = Gravity.CENTER_VERTICAL
        dockBar.setPadding(dp(10), 0, dp(10), 0)
        bottomBar.addView(dockBar, LinearLayout.LayoutParams(MATCH, dp(84)))

        homeLayer.addView(bottomBar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
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

    /** Dock ngikut ukuran ikon: makin gede ikon, makin tinggi dock. */
    private fun dockHeight(): Int {
        val s = Prefs.iconScale(this) / 100f
        return (dp(84) * s).toInt().coerceIn(dp(60), dp(140))
    }

    private fun updateDockSize() {
        val h = dockHeight()
        val dlp = dockBar.layoutParams
        if (dlp.height != h) {
            dlp.height = h
            dockBar.layoutParams = dlp
        }
        lp(pager).bottomMargin = insBottom + h + dp(76)
        pager.requestLayout()
    }

    private fun applyInsets() {
        lp(pager).topMargin = insTop + dp(8)
        updateDockSize()
        bottomBar.setPadding(dp(12), 0, dp(12), insBottom + dp(10))
        lp(searchBox).topMargin = insTop + dp(12)
        lp(drawerContent).topMargin = insTop + dp(68)
        bottomBar.requestLayout()
        searchBox.requestLayout()
        drawerContent.requestLayout()
        if (allApps.isNotEmpty()) renderDrawer()
    }

    // ------------------------------------------------------------ data load

    private fun loadApps() {
        val packName = Prefs.iconPack(this)
        loadedPack = packName
        Thread {
            val pm = packageManager
            val pack = IconPacks.load(this, packName)
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    val pkg = it.activityInfo.packageName
                    val cls = it.activityInfo.name
                    val ic = pack?.icon(pkg, cls) ?: it.loadIcon(pm)
                    App(
                        it.loadLabel(pm).toString(),
                        pkg,
                        cls,
                        ic,
                        it.activityInfo.applicationInfo.category
                    )
                }
                .sortedWith(compareBy<App>({ letterOf(it.label) == "#" }, { it.label.lowercase() }))
            runOnUiThread {
                allApps = list
                maybeSeed()
                applySettings()
            }
        }.start()
    }

    // -------------------------------------------------------- app bawaan

    private fun resolvePkg(i: Intent): String? = try {
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        val p = ri?.activityInfo?.packageName
        if (p == null || p == "android") null else p
    } catch (e: Exception) {
        null
    }

    private fun mainCat(c: String): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)

    private fun appFor(i: Intent): App? {
        val p = resolvePkg(i) ?: return null
        return allApps.firstOrNull { it.pkg == p }
    }

    private fun maybeSeed() {
        if (Prefs.seeded(this)) return
        if (Prefs.home(this).isEmpty() && Prefs.dock(this).isEmpty()) seedDefaults()
        Prefs.setSeeded(this, true)
    }

    private fun seedDefaults() {
        if (allApps.isEmpty()) return
        ensureGrids()

        if (Prefs.dock(this).isEmpty()) {
            val dockIntents = listOf(
                Intent(Intent.ACTION_DIAL),
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                Intent("android.media.action.IMAGE_CAPTURE")
            )
            val d = ArrayList<String>()
            for (i in dockIntents) {
                val a = appFor(i) ?: continue
                if (!d.contains(a.key)) d.add(a.key)
            }
            Prefs.saveDock(this, d.take(4))
        }

        val homeIntents = listOf(
            mainCat(Intent.CATEGORY_APP_GALLERY),
            mainCat(Intent.CATEGORY_APP_CALCULATOR),
            mainCat(Intent.CATEGORY_APP_CALENDAR),
            mainCat(Intent.CATEGORY_APP_CONTACTS),
            mainCat(Intent.CATEGORY_APP_MAPS),
            mainCat(Intent.CATEGORY_APP_MUSIC),
            mainCat(Intent.CATEGORY_APP_EMAIL),
            mainCat(Intent.CATEGORY_APP_MARKET),
            Intent(Settings.ACTION_SETTINGS),
            Intent(AlarmClock.ACTION_SHOW_ALARMS)
        )
        val candidates = ArrayList<App>()
        for (i in homeIntents) {
            val a = appFor(i)
            if (a != null) candidates.add(a)
        }
        val yt = allApps.firstOrNull { it.pkg == "com.google.android.youtube" }
        if (yt != null) candidates.add(yt)

        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val cur = ArrayList(Prefs.home(this))
        val dockKeys = Prefs.dock(this)
        for (a in candidates) {
            if (cur.any { it.key == a.key } || dockKeys.contains(a.key)) continue
            val spot = findFree(cur, 0, 0, 0, 1, 1, cols, rows) ?: break
            cur.add(Placed(a.key, spot[1], spot[2], 1, 1, spot[0]))
        }
        Prefs.saveHome(this, cur)
    }

    // -------------------------------------------------------- render: home

    private fun dockLight(): Boolean = when (Prefs.dockTint(this)) {
        1 -> true
        2 -> false
        else -> lightWp
    }

    private fun applySettings() {
        lightWp = wallpaperIsLight(this)
        updateDockSize()
        renderHome()
        renderDock()
        val light = dockLight()
        pill.background = rounded(if (light) 0x66FFFFFF else 0x66000000, dp(16).toFloat())
        pill.setTextColor(if (light) 0xFF111111.toInt() else Color.WHITE)
        if (drawerOpen) renderDrawer()
    }

    private fun isMoving(): Boolean = grids.any { it.moving }

    private fun setMoving(b: Boolean) {
        for (g in grids) g.moving = b
    }

    private fun ensureGrids() {
        val n = Prefs.pages(this).coerceIn(1, 10)
        if (grids.size == n) return
        while (grids.size > n) grids.removeAt(grids.size - 1)
        while (grids.size < n) {
            val idx = grids.size
            val g = HomeGrid(this)
            g.onCellTap = { c, r -> placePending(idx, c, r) }
            g.setOnLongClickListener { showHomeMenu(); true }
            grids.add(g)
        }
        pager.setPages(grids)
    }

    private fun renderDots() {
        dots.removeAllViews()
        val n = grids.size
        if (n <= 1) {
            dots.visibility = View.INVISIBLE
            return
        }
        dots.visibility = View.VISIBLE
        val on = if (dockLight()) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        val off = if (dockLight()) 0x66111111 else 0x66FFFFFF
        for (i in 0 until n) {
            val v = View(this)
            val sel = i == pager.page
            v.background = rounded(if (sel) on else off, dp(4).toFloat())
            val s = if (sel) dp(8) else dp(6)
            val dlp = LinearLayout.LayoutParams(s, s)
            dlp.setMargins(dp(3), 0, dp(3), 0)
            dots.addView(v, dlp)
        }
    }

    private fun selDrawable() = GradientDrawable().apply {
        setColor(0x33FFFFFF)
        setStroke(dp(2), Color.WHITE)
        cornerRadius = dp(20).toFloat()
    }

    private fun decorate(v: View, key: String) {
        v.tag = key
        if (!selecting) {
            v.alpha = 1f
            v.foreground = null
            return
        }
        if (selected.contains(key)) {
            v.alpha = 1f
            v.foreground = selDrawable()
        } else {
            v.alpha = 0.6f
            v.foreground = null
        }
    }

    private fun renderHome() {
        ensureGrids()
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val ha = Prefs.hAlign(this)
        val va = Prefs.vAlign(this)
        val folders = Prefs.folders(this)
        val lc = if (lightWp) 0xFF111111.toInt() else Color.WHITE
        val light = dockLight()
        val cardColor = if (light) 0x66FFFFFF else 0x66000000
        val showLabel = Prefs.showLabels(this)
        val scale = Prefs.iconScale(this) / 100f
        val labelSp = 11f * Prefs.labelScale(this) / 100f
        val all = Prefs.home(this)

        for ((pi, g) in grids.withIndex()) {
            g.cols = cols
            g.rows = rows
            g.hAlign = ha
            g.vAlign = va
            g.removeAllViews()
            g.moving = pendingKey != null
            g.showCells = dragging

            for (p in all) {
                if (p.page != pi) continue
                if (p.col >= cols || p.row >= rows) continue
                val w = minOf(p.w, cols - p.col)
                val h = minOf(p.h, rows - p.row)

                if (p.key.startsWith("w:")) {
                    val views = ArrayList<View>()
                    for (id in widgetIds(p.key)) {
                        val hv = widgetView(id)
                        if (hv != null) views.add(hv)
                    }
                    if (views.isEmpty()) continue
                    val st = StackView(this)
                    st.setViews(views, stackIndex[p.key] ?: 0)
                    st.onIndexChanged = { stackIndex[p.key] = it }
                    st.layoutParams = HomeGrid.LP(p.col, p.row, w, h)
                    st.onLong = { homeLongPress(p.key) { widgetMenu(p.key) } }
                    decorate(st, p.key)
                    g.addView(st)
                    continue
                }

                if (p.key.startsWith("folder:")) {
                    val f = folders.firstOrNull { "folder:" + it.id == p.key } ?: continue
                    val apps = f.keys.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
                    val v = FolderView(this, f.name, apps, w, h, cardColor, lc)
                    v.layoutParams = HomeGrid.LP(p.col, p.row, w, h)
                    v.setOnClickListener {
                        if (selecting) toggleSel(p.key) else openHomeFolder(f, apps)
                    }
                    v.setOnLongClickListener {
                        homeLongPress(p.key) { folderMenu(f) }
                        true
                    }
                    v.pressSpring()
                    decorate(v, p.key)
                    g.addView(v)
                    continue
                }

                val app = allApps.firstOrNull { it.key == p.key } ?: continue
                if (w == 1 && h == 1) {
                    val cell = HomeIcon(this, lc, showLabel, scale, labelSp)
                    cell.bind(app)
                    cell.layoutParams = HomeGrid.LP(p.col, p.row, 1, 1)
                    cell.setOnClickListener {
                        if (selecting) toggleSel(p.key) else launchApp(app, cell.icon)
                    }
                    cell.setOnLongClickListener {
                        homeLongPress(p.key) { homeAppMenu(app) }
                        true
                    }
                    cell.pressSpring()
                    decorate(cell, p.key)
                    g.addView(cell)
                } else {
                    val big = BigAppView(this, app, showLabel, lc)
                    big.layoutParams = HomeGrid.LP(p.col, p.row, w, h)
                    big.setOnClickListener {
                        if (selecting) toggleSel(p.key) else launchApp(app, big.icon)
                    }
                    big.setOnLongClickListener {
                        homeLongPress(p.key) { homeAppMenu(app) }
                        true
                    }
                    big.pressSpring()
                    decorate(big, p.key)
                    g.addView(big)
                }
            }
            g.requestLayout()
            g.invalidate()
        }
        renderDots()
        if (dragging) hideDragged()
    }

    private fun renderDock() {
        dockBar.background = rounded(
            if (dockLight()) 0x66FFFFFF else 0x66000000,
            dp(30).toFloat()
        )
        dockBar.removeAllViews()
        val scale = Prefs.iconScale(this) / 100f
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
            val cell = HomeIcon(this, Color.WHITE, false, scale, 11f)
            cell.bind(a)
            cell.setOnClickListener { launchApp(a, cell.icon) }
            cell.setOnLongClickListener { dockMenu(a); true }
            cell.pressSpring()
            dockBar.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    }

    // ------------------------------------------------------- icon animation

    private fun homeViews(): List<View> {
        val g = grids.getOrNull(pager.currentPage()) ?: return emptyList()
        return (0 until g.childCount).map { g.getChildAt(it) }
    }

    private fun resetFx() {
        scattered = false
        IconFx.reset(dockBar)
        IconFx.reset(pill)
        for (g in grids) {
            for (i in 0 until g.childCount) IconFx.reset(g.getChildAt(i))
        }
    }

    private fun hideForUnlock() {
        for (v in homeViews()) IconFx.hide(v)
        IconFx.hide(dockBar)
        IconFx.hide(pill)
    }

    private fun playGather() {
        waitingUnlock = false
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        val sp = Prefs.animSpeed(this) / 100f
        scattered = false
        IconFx.gather(homeViews(), w, h, sp)
        IconFx.barIn(dockBar, dp(120).toFloat(), sp)
        IconFx.barIn(pill, dp(120).toFloat(), sp)
    }

    private fun playScatter() {
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        val sp = Prefs.animSpeed(this) / 100f
        IconFx.scatter(homeViews(), w, h, sp)
        IconFx.barOut(dockBar, dp(120).toFloat(), sp)
        IconFx.barOut(pill, dp(120).toFloat(), sp)
        scattered = true
        root.postDelayed({
            if (scattered && !paused) playGather()
        }, 1500)
    }

    // ------------------------------------------------------------- widget

    private fun widgetIds(key: String): List<Int> =
        key.removePrefix("w:").split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun widgetView(id: Int): AppWidgetHostView? {
        val cached = widgetViews[id]
        if (cached != null) return cached
        val info = awm.getAppWidgetInfo(id) ?: return null
        return try {
            val hv = host.createView(this, id, info)
            widgetViews[id] = hv
            hv
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteWidgetIds(key: String) {
        for (id in widgetIds(key)) {
            try {
                host.deleteAppWidgetId(id)
            } catch (e: Exception) {
            }
            widgetViews.remove(id)
        }
        stackIndex.remove(key)
    }

    private fun stackAt(x: Float, y: Float): Boolean {
        val g = grids.getOrNull(pager.currentPage()) ?: return false
        val loc = IntArray(2)
        for (i in 0 until g.childCount) {
            val v = g.getChildAt(i)
            if (v is StackView && v.count() > 1) {
                v.getLocationOnScreen(loc)
                if (x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height) {
                    return true
                }
            }
        }
        return false
    }

    private fun appLabelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.')
    }

    private fun pickWidget(addTo: String?) {
        val ps = awm.installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedBy { appLabelOf(it.provider.packageName).lowercase() + it.loadLabel(packageManager).lowercase() }
        if (ps.isEmpty()) {
            Toast.makeText(this, "Nggak ada widget yang ketemu", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = ps.map { it.loadLabel(packageManager) + "  (" + appLabelOf(it.provider.packageName) + ")" }
        AlertDialog.Builder(this).setTitle("Pilih widget")
            .setItems(labels.toTypedArray()) { _, i -> startWidgetBind(ps[i], addTo) }
            .showT()
    }

    private fun startWidgetBind(p: AppWidgetProviderInfo, addTo: String?) {
        val id = host.allocateAppWidgetId()
        pendWidgetId = id
        pendProvider = p
        pendStackKey = addTo
        if (awm.bindAppWidgetIdIfAllowed(id, p.provider)) {
            afterBind()
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
            try {
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                cancelPendingWidget()
            }
        }
    }

    private fun afterBind() {
        val p = pendProvider ?: return
        if (p.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, pendWidgetId, 0, REQ_CONFIG, null)
            } catch (e: Exception) {
                finishWidget()
            }
        } else {
            finishWidget()
        }
    }

    private fun cancelPendingWidget() {
        if (pendWidgetId >= 0) {
            try {
                host.deleteAppWidgetId(pendWidgetId)
            } catch (e: Exception) {
            }
        }
        pendWidgetId = -1
        pendProvider = null
        pendStackKey = null
    }

    private fun defaultSpan(p: AppWidgetProviderInfo): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val gw = if (pager.width > 0) pager.width else dm.widthPixels
        val gh = if (pager.height > 0) pager.height else (dm.heightPixels * 0.7f).toInt()
        val cw = gw / cols.toFloat()
        val ch = gh / rows.toFloat()
        val w = ceil(maxOf(p.minWidth, 1) / cw).toInt().coerceIn(1, cols)
        val h = ceil(maxOf(p.minHeight, 1) / ch).toInt().coerceIn(1, rows)
        return Pair(w, h)
    }

    private fun finishWidget() {
        val id = pendWidgetId
        val p = pendProvider
        val addTo = pendStackKey
        pendWidgetId = -1
        pendProvider = null
        pendStackKey = null
        if (id < 0 || p == null) return

        if (addTo != null) {
            val cur = Prefs.home(this)
            if (cur.none { it.key == addTo }) {
                try {
                    host.deleteAppWidgetId(id)
                } catch (e: Exception) {
                }
                return
            }
            val nk = "$addTo,$id"
            stackIndex.remove(addTo)
            Prefs.saveHome(this, cur.map { if (it.key == addTo) it.copy(key = nk) else it })
            stackIndex[nk] = widgetIds(nk).size - 1
            renderHome()
            Toast.makeText(this, "Widget masuk tumpukan. Geser kiri/kanan buat ganti", Toast.LENGTH_LONG).show()
            return
        }
        val span = defaultSpan(p)
        startPlace("w:$id", span.first, span.second)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BIND) {
            if (resultCode == RESULT_OK) afterBind() else cancelPendingWidget()
        } else if (requestCode == REQ_CONFIG) {
            if (resultCode == RESULT_OK) finishWidget() else cancelPendingWidget()
        }
    }

    private fun widgetMenu(key: String) {
        val ids = widgetIds(key)
        val labels = ArrayList<String>()
        labels.add("Ubah ukuran")
        labels.add("Tambah widget ke tumpukan")
        if (ids.size > 1) labels.add("Hapus widget yang lagi tampil")
        labels.add("Pilih beberapa")
        labels.add("Hapus")
        AlertDialog.Builder(this)
            .setTitle(if (ids.size > 1) "Tumpukan widget (" + ids.size + ")" else "Widget")
            .setItems(labels.toTypedArray()) { _, which ->
                when (labels[which]) {
                    "Ubah ukuran" -> resizeDialog(key)
                    "Tambah widget ke tumpukan" -> pickWidget(key)
                    "Hapus widget yang lagi tampil" -> removeCurrentFromStack(key)
                    "Pilih beberapa" -> enterSelection(key)
                    "Hapus" -> {
                        deleteWidgetIds(key)
                        Prefs.saveHome(this, Prefs.home(this).filter { it.key != key })
                        renderHome()
                    }
                    else -> {}
                }
            }.showT()
    }

    private fun removeCurrentFromStack(key: String) {
        val ids = widgetIds(key).toMutableList()
        if (ids.size < 2) return
        val i = (stackIndex[key] ?: 0).coerceIn(0, ids.size - 1)
        val gone = ids.removeAt(i)
        try {
            host.deleteAppWidgetId(gone)
        } catch (e: Exception) {
        }
        widgetViews.remove(gone)
        val nk = "w:" + ids.joinToString(",")
        stackIndex.remove(key)
        Prefs.saveHome(this, Prefs.home(this).map { if (it.key == key) it.copy(key = nk) else it })
        renderHome()
    }

    private fun resizeDialog(key: String) {
        val item = Prefs.home(this).firstOrNull { it.key == key } ?: return
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        var nw = item.w.coerceIn(1, cols)
        var nh = item.h.coerceIn(1, rows)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))
        val tw = TextView(this)
        val th = TextView(this)
        tw.text = "Lebar: $nw kotak"
        th.text = "Tinggi: $nh kotak"
        val sw = SeekBar(this)
        sw.max = maxOf(cols - 1, 0)
        sw.progress = nw - 1
        val sh = SeekBar(this)
        sh.max = maxOf(rows - 1, 0)
        sh.progress = nh - 1
        sw.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                nw = p + 1
                tw.text = "Lebar: $nw kotak"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                nh = p + 1
                th.text = "Tinggi: $nh kotak"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(tw)
        box.addView(sw)
        box.addView(th)
        box.addView(sh)
        AlertDialog.Builder(this).setTitle("Ubah ukuran").setView(box)
            .setPositiveButton("Terapkan") { _, _ -> resizeItem(key, nw, nh) }
            .setNegativeButton("Batal", null)
            .showT()
    }

    // ------------------------------------------------------- home actions

    private fun fits(list: List<Placed>, page: Int, c: Int, r: Int, w: Int, h: Int): Boolean =
        list.none {
            it.page == page && c < it.col + it.w && it.col < c + w && r < it.row + it.h && it.row < r + h
        }

    private fun startPlace(key: String, defW: Int = 1, defH: Int = 1) {
        val cur = Prefs.home(this)
        val orig = cur.firstOrNull { it.key == key }
        pendingOrig = orig
        pendW = orig?.w ?: defW
        pendH = orig?.h ?: defH
        Prefs.saveHome(this, cur.filter { it.key != key })
        pendingKey = key
        renderHome()
        closeDrawer(true)
        setMoving(true)
        Toast.makeText(
            this,
            "Tap kotak kosong buat naruh. Geser kiri/kanan buat ganti halaman",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun placePending(page: Int, c: Int, r: Int) {
        val key = pendingKey ?: return
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val w = minOf(pendW, cols)
        val h = minOf(pendH, rows)
        val cc = minOf(c, cols - w)
        val rr = minOf(r, rows - h)
        val cur = Prefs.home(this)
        if (!fits(cur, page, cc, rr, w, h)) {
            Toast.makeText(this, "Nggak muat di situ, pilih tempat lain", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.saveHome(this, cur + Placed(key, cc, rr, w, h, page))
        endMove()
        renderHome()
    }

    private fun endMove() {
        pendingKey = null
        pendingOrig = null
        setMoving(false)
    }

    private fun cancelMove() {
        val o = pendingOrig
        val k = pendingKey
        if (o != null) {
            Prefs.saveHome(this, Prefs.home(this) + o)
        } else if (k != null && k.startsWith("folder:")) {
            val id = k.removePrefix("folder:")
            Prefs.saveFolders(this, Prefs.folders(this).filter { it.id != id })
        } else if (k != null && k.startsWith("w:")) {
            deleteWidgetIds(k)
        }
        endMove()
        renderHome()
    }

    private fun resizeItem(key: String, w0: Int, h0: Int) {
        val cur = Prefs.home(this)
        val item = cur.firstOrNull { it.key == key } ?: return
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val w = minOf(w0, cols)
        val h = minOf(h0, rows)
        val c = minOf(item.col, cols - w)
        val r = minOf(item.row, rows - h)
        val others = cur.filter { it.key != key }
        if (!fits(others, item.page, c, r, w, h)) {
            Toast.makeText(
                this,
                "Nggak muat. Kosongin tempat di sebelahnya atau pindahin dulu",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Prefs.saveHome(this, others + Placed(key, c, r, w, h, item.page))
        renderHome()
    }

    private fun sizeDialog(key: String) {
        AlertDialog.Builder(this).setTitle("Ukuran (lebar x tinggi)")
            .setItems(arrayOf("1x1  normal", "2x1  lebar", "1x2  tinggi", "2x2  besar")) { _, i ->
                resizeItem(key, sizes[i].first, sizes[i].second)
            }.showT()
    }

    private fun homeAppMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Ukuran", "Pilih beberapa", "Hapus dari home", "Info app")) { _, which ->
                when (which) {
                    0 -> sizeDialog(app.key)
                    1 -> enterSelection(app.key)
                    2 -> {
                        Prefs.saveHome(this, Prefs.home(this).filter { it.key != app.key })
                        renderHome()
                    }
                    3 -> openInfo(app.pkg)
                    else -> {}
                }
            }.showT()
    }

    private fun dockMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Hapus dari dock", "Info app")) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.saveDock(this, Prefs.dock(this).filter { it != app.key })
                        renderDock()
                    }
                    1 -> openInfo(app.pkg)
                    else -> {}
                }
            }.showT()
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
            .setItems(
                arrayOf(
                    "Tambah widget", "Buat folder", "Isi app bawaan",
                    "Setelan launcher", "Ganti wallpaper"
                )
            ) { _, which ->
                when (which) {
                    0 -> pickWidget(null)
                    1 -> createFolder()
                    2 -> {
                        seedDefaults()
                        renderHome()
                        renderDock()
                        Toast.makeText(this, "App bawaan dipasang", Toast.LENGTH_SHORT).show()
                    }
                    3 -> startActivity(Intent(this, SettingsActivity::class.java))
                    4 -> {
                        try {
                            startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SET_WALLPAPER), "Ganti wallpaper"
                                )
                            )
                        } catch (e: ActivityNotFoundException) {
                        }
                    }
                    else -> {}
                }
            }.showT()
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
            .setItems(
                arrayOf("Tambah ke home", "Tambah ke dock", "Tambah ke folder", "Info app", "Uninstall")
            ) { _, which ->
                when (which) {
                    0 -> startPlace(app.key)
                    1 -> addToDock(app)
                    2 -> addToFolder(app)
                    3 -> openInfo(app.pkg)
                    4 -> uninstall(app.pkg)
                    else -> {}
                }
            }.showT()
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
            playScatter()
        } catch (e: ActivityNotFoundException) {
            loadApps()
        }
    }

    // ------------------------------------------------------ pilih beberapa

    private fun enterSelection(first: String) {
        selecting = true
        selected.clear()
        selected.add(first)
        showSelBar()
        renderHome()
    }

    private fun exitSelection() {
        selecting = false
        selected.clear()
        val b = selBar
        if (b != null) root.removeView(b)
        selBar = null
        selCount = null
    }

    private fun toggleSel(key: String) {
        if (!selected.remove(key)) selected.add(key)
        updateSelBar()
        renderHome()
    }

    private fun updateSelBar() {
        selCount?.text = "${selected.size} dipilih. Tahan lalu tarik buat pindah"
    }

    private fun barButton(t: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = t
        b.setTextColor(0xFFFFB74D.toInt())
        b.textSize = 14f
        b.setTypeface(null, Typeface.BOLD)
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun showSelBar() {
        if (selBar != null) return
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = rounded(0xE6202024.toInt(), dp(24).toFloat())
        bar.setPadding(dp(16), dp(8), dp(8), dp(8))
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = 13f
        selCount = t
        bar.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(barButton("Hapus") { deleteSelected() })
        bar.addView(barButton("Selesai") {
            exitSelection()
            renderHome()
        })
        val blp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP)
        blp.setMargins(dp(16), insTop + dp(12), dp(16), 0)
        root.addView(bar, blp)
        selBar = bar
        updateSelBar()
    }

    private fun deleteSelected() {
        val ks = selected.toSet()
        for (k in ks) {
            if (k.startsWith("w:")) deleteWidgetIds(k)
        }
        Prefs.saveHome(this, Prefs.home(this).filter { !ks.contains(it.key) })
        exitSelection()
        renderHome()
    }

    // --------------------------------------------------- drag and drop ikon

    private fun beginPickup(keys: List<String>, fromHome: Boolean, menu: () -> Unit) {
        if (keys.isEmpty()) return
        pick = Pick(keys, fromHome, menu)
        root.pickupActive = true
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun homeLongPress(key: String, menu: () -> Unit) {
        if (selecting) {
            selected.add(key)
            updateSelBar()
            val order = listOf(key) + selected.filter { it != key }
            beginPickup(order, true) { renderHome() }
        } else {
            beginPickup(listOf(key), true, menu)
        }
    }

    private fun hideDragged() {
        val ks = pick?.keys ?: return
        for (g in grids) {
            for (i in 0 until g.childCount) {
                val v = g.getChildAt(i)
                val k = v.tag as? String
                if (k != null && ks.contains(k)) v.visibility = View.INVISIBLE
            }
        }
    }

    private fun createGhost(pk: Pick) {
        val size = dp(64)
        val box = FrameLayout(this)
        val iv = ImageView(this)
        iv.outlineProvider = roundOutline()
        iv.clipToOutline = true
        val app = allApps.firstOrNull { it.key == pk.keys[0] }
        if (app != null) {
            iv.setImageDrawable(app.iconCopy())
        } else {
            iv.background = rounded(0xCC333338.toInt(), dp(16).toFloat())
        }
        box.addView(iv, FrameLayout.LayoutParams(size, size))
        if (pk.keys.size > 1) {
            val b = TextView(this)
            b.text = pk.keys.size.toString()
            b.setTextColor(Color.WHITE)
            b.textSize = 12f
            b.setTypeface(null, Typeface.BOLD)
            b.gravity = Gravity.CENTER
            b.background = rounded(0xFFE53935.toInt(), dp(11).toFloat())
            box.addView(b, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END))
        }
        box.scaleX = 1.12f
        box.scaleY = 1.12f
        box.alpha = 0.95f
        box.elevation = dp(10).toFloat()
        root.addView(box, FrameLayout.LayoutParams(size, size))
        dragGhost = box
    }

    private fun positionGhost() {
        val g = dragGhost ?: return
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val s = if (g.width > 0) g.width else dp(64)
        g.translationX = dragX - loc[0] - s / 2f
        g.translationY = dragY - loc[1] - s / 2f
    }

    private fun startItemDrag(x: Float, y: Float) {
        val pk = pick ?: return
        dragging = true
        dragX = x
        dragY = y
        edgeSince = 0L
        val cur = Prefs.home(this)
        dragOrig = cur.filter { pk.keys.contains(it.key) }
        dragOthers = cur.filter { !pk.keys.contains(it.key) }
        val lead = dragOrig.firstOrNull { it.key == pk.keys[0] }
        leadW = lead?.w ?: 1
        leadH = lead?.h ?: 1
        hideDragged()
        createGhost(pk)
        if (!pk.fromHome) closeDrawer(true)
        for (g in grids) g.showCells = true
        positionGhost()
        updateHint()
        root.postDelayed(dragTick, 100)
    }

    private fun moveItemDrag(x: Float, y: Float) {
        dragX = x
        dragY = y
        positionGhost()
        updateHint()
    }

    private fun nearestFit(
        list: List<Placed>, page: Int, c: Int, r: Int, w: Int, h: Int, cols: Int, rows: Int
    ): IntArray? {
        var best: IntArray? = null
        var bd = Int.MAX_VALUE
        for (rr in 0..(rows - h)) {
            for (cc in 0..(cols - w)) {
                if (!fits(list, page, cc, rr, w, h)) continue
                val d = (cc - c) * (cc - c) + (rr - r) * (rr - r)
                if (d < bd) {
                    bd = d
                    best = intArrayOf(cc, rr)
                }
            }
        }
        return best
    }

    private fun updateHint() {
        if (!dragging || grids.isEmpty()) return
        val pi = pager.currentPage().coerceIn(0, grids.size - 1)
        val g = grids[pi]
        for (o in grids) {
            if (o !== g) o.hint = null
        }
        val cell = g.cellAtScreen(dragX, dragY)
        if (cell == null) {
            g.hint = null
            dragTarget = null
            return
        }
        val cols = g.cols
        val rows = g.rows
        val w = minOf(leadW, cols)
        val h = minOf(leadH, rows)
        val c = (cell[0] - (w - 1) / 2).coerceIn(0, cols - w)
        val r = (cell[1] - (h - 1) / 2).coerceIn(0, rows - h)
        val spot = nearestFit(dragOthers, pi, c, r, w, h, cols, rows)
        if (spot != null) {
            g.hint = HomeGrid.Hint(spot[0], spot[1], w, h, true)
            dragTarget = intArrayOf(pi, spot[0], spot[1])
        } else {
            g.hint = HomeGrid.Hint(c, r, w, h, false)
            dragTarget = null
        }
    }

    private fun endItemDrag(x: Float, y: Float, cancelled: Boolean) {
        val pk = pick
        pick = null
        dragging = false
        root.removeCallbacks(dragTick)
        val gh = dragGhost
        if (gh != null) root.removeView(gh)
        dragGhost = null
        for (g in grids) {
            g.hint = null
            g.showCells = false
        }
        val t = dragTarget
        dragTarget = null
        if (pk == null || cancelled || t == null) {
            renderHome()
            return
        }
        commitDrop(pk.keys, t[0], t[1], t[2])
    }

    private fun findFree(
        list: List<Placed>, startPage: Int, sc: Int, sr: Int, w: Int, h: Int, cols: Int, rows: Int
    ): IntArray? {
        val n = grids.size
        for (k in 0 until n) {
            val p = (startPage + k) % n
            val r0 = if (k == 0) sr else 0
            for (r in r0..(rows - h)) {
                val c0 = if (k == 0 && r == sr) sc else 0
                for (c in c0..(cols - w)) {
                    if (fits(list, p, c, r, w, h)) return intArrayOf(p, c, r)
                }
            }
        }
        for (r in 0..(rows - h)) {
            for (c in 0..(cols - w)) {
                if (fits(list, startPage, c, r, w, h)) return intArrayOf(startPage, c, r)
            }
        }
        return null
    }

    private fun commitDrop(keys: List<String>, page: Int, c: Int, r: Int) {
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val result = ArrayList<Placed>(dragOthers)
        val leadOrig = dragOrig.firstOrNull { it.key == keys[0] }
        val lw = minOf(leadOrig?.w ?: 1, cols)
        val lh = minOf(leadOrig?.h ?: 1, rows)
        result.add(Placed(keys[0], c, r, lw, lh, page))
        var ok = true
        for (k in keys.drop(1)) {
            val o = dragOrig.firstOrNull { it.key == k }
            val w = minOf(o?.w ?: 1, cols)
            val h = minOf(o?.h ?: 1, rows)
            val spot = findFree(result, page, c, r, w, h, cols, rows)
            if (spot == null) {
                ok = false
                break
            }
            result.add(Placed(k, spot[1], spot[2], w, h, spot[0]))
        }
        if (!ok) {
            Toast.makeText(this, "Nggak muat semua, dibatalin", Toast.LENGTH_SHORT).show()
            renderHome()
            return
        }
        Prefs.saveHome(this, result)
        exitSelection()
        renderHome()
    }

    private fun abortDrag() {
        if (!dragging && pick == null) return
        root.pickupActive = false
        pick = null
        dragging = false
        root.removeCallbacks(dragTick)
        val gh = dragGhost
        if (gh != null) root.removeView(gh)
        dragGhost = null
        for (g in grids) {
            g.hint = null
            g.showCells = false
        }
        dragTarget = null
        renderHome()
    }

    // ------------------------------------------------------------- folders

    private fun cleanName(s: String): String =
        s.replace("|", " ").replace(",", " ").replace("\n", " ").trim().ifBlank { "Folder" }

    private fun pickApps(title: String, selected: List<String>, onDone: (List<String>) -> Unit) {
        val apps = allApps
        if (apps.isEmpty()) {
            Toast.makeText(this, "Daftar app belum kebaca", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(apps.size) { selected.contains(apps[it].key) }
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(Array(apps.size) { apps[it].label }, checked) { _, i, b ->
                checked[i] = b
            }
            .setPositiveButton("Simpan") { _, _ ->
                onDone(apps.indices.filter { checked[it] }.map { apps[it].key })
            }
            .setNegativeButton("Batal", null)
            .showT()
    }

    private fun createFolder() {
        val input = EditText(this)
        input.setText("Folder")
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this).setTitle("Nama folder").setView(input)
            .setPositiveButton("Lanjut") { _, _ ->
                val name = cleanName(input.text.toString())
                pickApps("Isi folder", emptyList()) { keys ->
                    val id = System.currentTimeMillis().toString()
                    Prefs.saveFolders(this, Prefs.folders(this) + Folder(id, name, keys))
                    startPlace("folder:$id", 2, 2)
                }
            }
            .setNegativeButton("Batal", null)
            .showT()
    }

    private fun saveFolder(f: Folder) {
        Prefs.saveFolders(this, Prefs.folders(this).map { if (it.id == f.id) f else it })
        renderHome()
    }

    private fun deleteFolder(f: Folder) {
        Prefs.saveFolders(this, Prefs.folders(this).filter { it.id != f.id })
        Prefs.saveHome(this, Prefs.home(this).filter { it.key != "folder:" + f.id })
        renderHome()
    }

    private fun renameFolder(f: Folder) {
        val input = EditText(this)
        input.setText(f.name)
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this).setTitle("Ganti nama").setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                saveFolder(f.copy(name = cleanName(input.text.toString())))
            }
            .setNegativeButton("Batal", null)
            .showT()
    }

    private fun folderMenu(f: Folder) {
        val key = "folder:" + f.id
        AlertDialog.Builder(this).setTitle(f.name)
            .setItems(
                arrayOf("Ukuran", "Ubah isi", "Ganti nama", "Pilih beberapa", "Hapus folder")
            ) { _, which ->
                when (which) {
                    0 -> sizeDialog(key)
                    1 -> pickApps("Isi folder", f.keys) { keys -> saveFolder(f.copy(keys = keys)) }
                    2 -> renameFolder(f)
                    3 -> enterSelection(key)
                    4 -> deleteFolder(f)
                    else -> {}
                }
            }.showT()
    }

    private fun addToFolder(app: App) {
        val fs = Prefs.folders(this)
        if (fs.isEmpty()) {
            Toast.makeText(
                this,
                "Belum ada folder. Tahan lama area kosong di home, pilih Buat folder",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Pilih folder")
            .setItems(fs.map { it.name }.toTypedArray()) { _, i ->
                val f = fs[i]
                if (!f.keys.contains(app.key)) saveFolder(f.copy(keys = f.keys + app.key))
                Toast.makeText(this, "Ditambah ke " + f.name, Toast.LENGTH_SHORT).show()
            }.showT()
    }

    private fun folderAppMenu(f: Folder, app: App) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Hapus dari folder", "Info app")) { _, which ->
                when (which) {
                    0 -> {
                        closeHomeFolder()
                        saveFolder(f.copy(keys = f.keys - app.key))
                    }
                    1 -> openInfo(app.pkg)
                    else -> {}
                }
            }.showT()
    }

    private fun openHomeFolder(f: Folder, apps: List<App>) {
        closeHomeFolder()
        val dm = resources.displayMetrics

        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(0x99000000.toInt())
        scrim.setOnClickListener { closeHomeFolder() }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(0xF0202024.toInt(), dp(28).toFloat())
        card.setPadding(dp(12), dp(14), dp(12), dp(12))
        card.isClickable = true

        val title = TextView(this)
        title.text = f.name
        title.setTextColor(Color.WHITE)
        title.textSize = 16f
        title.setTypeface(null, Typeface.BOLD)
        title.gravity = Gravity.CENTER
        card.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))

        if (apps.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Folder kosong. Tahan lama folder, lalu pilih Ubah isi"
            empty.setTextColor(0xFFBBBBBB.toInt())
            empty.textSize = 13f
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(8), dp(16), dp(8), dp(16))
            card.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            val rv = RecyclerView(this)
            rv.layoutManager = GridLayoutManager(this, 4)
            rv.overScrollMode = View.OVER_SCROLL_NEVER
            val ad = GridAdapter(
                { a, v ->
                    launchApp(a, v)
                    closeHomeFolder()
                },
                { a -> folderAppMenu(f, a) }
            )
            rv.adapter = ad
            ad.submit(apps)
            val rowsNeeded = ((apps.size + 3) / 4).coerceAtLeast(1)
            val hh = minOf(rowsNeeded * dp(92), (dm.heightPixels * 0.6f).toInt())
            val rlp = LinearLayout.LayoutParams(MATCH, hh)
            rlp.topMargin = dp(8)
            card.addView(rv, rlp)
        }

        scrim.addView(card, FrameLayout.LayoutParams(dm.widthPixels - dp(40), WRAP, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        homeFolder = scrim
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(150).start()
    }

    private fun closeHomeFolder() {
        val v = homeFolder
        if (v != null) (v.parent as? ViewGroup)?.removeView(v)
        homeFolder = null
    }

    // ----------------------------------------------- gesture: drawer ngikut jari

    private fun setupGestures() {
        root.onDown = {
            ignoreSwipe = false
            val sc = overlayScroll ?: drawerScroll
            atTopOnDown = !(sc?.canScrollVertically(-1) ?: false)
        }
        root.decide = { _, dy ->
            when {
                ignoreSwipe -> 0
                !drawerOpen && dy < 0 && !isMoving() && homeFolder == null -> 1
                drawerOpen && dy > 0 && atTopOnDown -> 2
                else -> 0
            }
        }
        root.onStart = { mode -> startDrag(mode) }
        root.onMove = { dy -> moveDrag(dy) }
        root.onEnd = { vy -> endDrag(vy) }

        root.onItemStart = { x, y -> startItemDrag(x, y) }
        root.onItemMove = { x, y -> moveItemDrag(x, y) }
        root.onItemEnd = { x, y, c -> endItemDrag(x, y, c) }
        root.onPickupRelease = {
            val m = pick?.menu
            pick = null
            m?.invoke()
        }
    }

    private fun startDrag(mode: Int) {
        drawerLayer.animate().cancel()
        homeLayer.animate().cancel()
        dragOpening = mode == 1
        if (dragOpening) {
            drawerOpen = true
            searchBox.setText("")
            renderDrawer()
            drawerLayer.visibility = View.VISIBLE
            drawerLayer.translationY = root.height.toFloat()
        }
    }

    private fun moveDrag(dy: Float) {
        val hgt = root.height.toFloat()
        if (hgt <= 0f) return
        val ov = overlay
        if (!dragOpening && ov != null) {
            ov.translationY = dy.coerceIn(0f, hgt)
            return
        }
        val ty = if (dragOpening) (hgt + dy).coerceIn(0f, hgt) else dy.coerceIn(0f, hgt)
        drawerLayer.translationY = ty
        val p = 1f - ty / hgt
        homeLayer.alpha = 1f - p
        homeLayer.scaleX = 1f - 0.05f * p
        homeLayer.scaleY = 1f - 0.05f * p
    }

    private fun endDrag(vy: Float) {
        val hgt = root.height.toFloat()
        val ov = overlay
        if (!dragOpening && ov != null) {
            if (vy > 900f || ov.translationY > hgt * 0.25f) {
                closeOverlay()
            } else {
                ov.animate().translationY(0f).setDuration(180).start()
            }
            return
        }
        val ty = drawerLayer.translationY
        if (dragOpening) {
            val open = if (vy < -900f) true else if (vy > 900f) false else ty < hgt * 0.6f
            if (open) settleOpen() else closeDrawer(true)
        } else {
            val close = if (vy > 900f) true else if (vy < -900f) false else ty > hgt * 0.25f
            if (close) closeDrawer(true) else settleOpen()
        }
    }

    private fun settleOpen() {
        drawerLayer.animate().cancel()
        drawerLayer.animate().translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
        homeLayer.animate().cancel()
        homeLayer.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(220).start()
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

    private fun drawerLong(a: App) {
        beginPickup(listOf(a.key), false) { appMenu(a) }
    }

    private fun buildGrid(apps: List<App>): RecyclerView {
        val rv = RecyclerView(this)
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.clipToPadding = false
        rv.setPadding(dp(8), 0, dp(8), insBottom + dp(16))
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        val ad = GridAdapter({ a, v -> launchApp(a, v) }, { a -> drawerLong(a) })
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
        val ad = AzAdapter({ a, v -> launchApp(a, v) }, { a -> drawerLong(a) })
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
        val colW = (resources.displayMetrics.widthPixels - dp(32)) / 2
        val iconSz = maxOf(dp(40), (colW - dp(16)) / 2 - dp(10))

        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        tile.setPadding(dp(8), dp(8), dp(8), dp(8))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.background = rounded(0x22FFFFFF, dp(30).toFloat())
        for (r in 0..1) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0..1) {
                val a = apps.getOrNull(r * 2 + c)
                val iv = ImageView(this)
                iv.outlineProvider = roundOutline()
                iv.clipToOutline = true
                if (a != null) iv.setImageDrawable(a.iconCopy())
                val ilp = LinearLayout.LayoutParams(iconSz, iconSz)
                ilp.setMargins(dp(5), dp(5), dp(5), dp(5))
                line.addView(iv, ilp)
            }
            box.addView(line)
        }
        tile.addView(box, LinearLayout.LayoutParams(WRAP, WRAP))

        val label = TextView(this)
        label.text = "$name (${apps.size})"
        label.setTextColor(Color.WHITE)
        label.textSize = 13f
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

    override fun onPause() {
        super.onPause()
        paused = true
        abortDrag()
        if (waitingUnlock) {
            waitingUnlock = false
            resetFx()
        }
    }

    override fun onResume() {
        super.onResume()
        paused = false
        focusLost = false
        val animate = !firstResume
        firstResume = false

        if (Prefs.iconPack(this) != loadedPack) loadApps()

        if (drawerOpen) closeDrawer(false)
        homeLayer.animate().cancel()
        homeLayer.alpha = 1f
        homeLayer.scaleX = 1f
        homeLayer.scaleY = 1f
        applySettings()
        resetFx()

        if (animate) {
            val kg = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (kg.isKeyguardLocked) {
                waitingUnlock = true
                hideForUnlock()
            } else {
                homeLayer.doOnPreDraw { playGather() }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            if (!paused && dialogs == 0 && !firstResume && !dragging) focusLost = true
            return
        }
        val kg = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (waitingUnlock && !kg.isKeyguardLocked) {
            focusLost = false
            root.post { playGather() }
            return
        }
        if (focusLost) {
            focusLost = false
            if (!paused && dialogs == 0 && !drawerOpen && homeFolder == null && !waitingUnlock) {
                root.post { playGather() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            abortDrag()
            if (selecting) {
                exitSelection()
                renderHome()
            }
            closeHomeFolder()
            closeOverlay()
            if (isMoving()) cancelMove()
            if (drawerOpen) {
                closeDrawer(true)
            } else if (pager.page != 0) {
                pager.goTo(0)
            }
        }
    }

    @Deprecated("Launcher stays open")
    override fun onBackPressed() {
        when {
            homeFolder != null -> closeHomeFolder()
            overlay != null -> closeOverlay()
            selecting -> {
                exitSelection()
                renderHome()
            }
            isMoving() -> cancelMove()
            drawerOpen -> closeDrawer(true)
        }
    }
}
