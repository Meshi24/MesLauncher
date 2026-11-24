package com.example.simplelauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.KeyguardManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val sizes = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)

    private var allApps: List<App> = emptyList()

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

    // status gesture
    private var atTopOnDown = true
    private var ignoreSwipe = false
    private var dragOpening = false

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

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false

        root = DragFrame(this)
        setupGestures()
        buildHome()
        buildDrawer()
        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawerLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        applyInsets()

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

    private fun applyInsets() {
        lp(pager).topMargin = insTop + dp(8)
        lp(pager).bottomMargin = insBottom + dp(160)
        bottomBar.setPadding(dp(12), 0, dp(12), insBottom + dp(10))
        lp(searchBox).topMargin = insTop + dp(12)
        lp(drawerContent).topMargin = insTop + dp(68)
        pager.requestLayout()
        bottomBar.requestLayout()
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

    private fun renderHome() {
        ensureGrids()
        val cols = Prefs.cols(this)
        val rows = Prefs.rows(this)
        val folders = Prefs.folders(this)
        val lc = if (lightWp) 0xFF111111.toInt() else Color.WHITE
        val light = dockLight()
        val cardColor = if (light) 0x66FFFFFF else 0x66000000
        val cardText = if (light) 0xFF111111.toInt() else Color.WHITE
        val showLabel = Prefs.showLabels(this)
        val scale = Prefs.iconScale(this) / 100f
        val labelSp = 11f * Prefs.labelScale(this) / 100f
        val all = Prefs.home(this)

        for ((pi, g) in grids.withIndex()) {
            g.cols = cols
            g.rows = rows
            g.removeAllViews()
            g.moving = pendingKey != null

            for (p in all) {
                if (p.page != pi) continue
                if (p.col >= cols || p.row >= rows) continue
                val w = minOf(p.w, cols - p.col)
                val h = minOf(p.h, rows - p.row)

                if (p.key.startsWith("folder:")) {
                    val f = folders.firstOrNull { "folder:" + it.id == p.key } ?: continue
                    val apps = f.keys.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
                    val v = FolderView(this, f.name, apps, w, h, cardColor, cardText)
                    v.layoutParams = HomeGrid.LP(p.col, p.row, w, h)
                    v.setOnClickListener { openHomeFolder(f, apps) }
                    v.setOnLongClickListener { folderMenu(f); true }
                    v.pressSpring()
                    g.addView(v)
                    continue
                }

                val app = allApps.firstOrNull { it.key == p.key } ?: continue
                if (w == 1 && h == 1) {
                    val cell = HomeIcon(this, lc, showLabel, scale, labelSp)
                    cell.bind(app)
                    cell.layoutParams = HomeGrid.LP(p.col, p.row, 1, 1)
                    cell.setOnClickListener { launchApp(app, cell.icon) }
                    cell.setOnLongClickListener { homeAppMenu(app); true }
                    cell.pressSpring()
                    g.addView(cell)
                } else {
                    val big = BigAppView(this, w >= 2 && h == 1, cardText, cardColor)
                    big.icon.setImageDrawable(app.iconCopy())
                    big.label.text = app.label
                    big.layoutParams = HomeGrid.LP(p.col, p.row, w, h)
                    big.setOnClickListener { launchApp(app, big.icon) }
                    big.setOnLongClickListener { homeAppMenu(app); true }
                    big.pressSpring()
                    g.addView(big)
                }
            }
            g.requestLayout()
            g.invalidate()
        }
        renderDots()
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
            val cell = HomeIcon(this, Color.WHITE, false, 1f, 11f)
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

    /** Ikon ngumpul masuk. */
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

    /** Ikon mencar keluar (pas buka app). */
    private fun playScatter() {
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        val sp = Prefs.animSpeed(this) / 100f
        IconFx.scatter(homeViews(), w, h, sp)
        IconFx.barOut(dockBar, dp(120).toFloat(), sp)
        IconFx.barOut(pill, dp(120).toFloat(), sp)
        scattered = true
        // jaga-jaga: kalau app-nya nggak jadi kebuka, balikin ikonnya
        root.postDelayed({
            if (scattered && !paused) playGather()
        }, 1500)
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
            .setItems(arrayOf("Ukuran", "Pindah", "Hapus dari home", "Info app")) { _, which ->
                when (which) {
                    0 -> sizeDialog(app.key)
                    1 -> startPlace(app.key)
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
            .setItems(arrayOf("Buat folder", "Setelan launcher", "Ganti wallpaper")) { _, which ->
                when (which) {
                    0 -> createFolder()
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                    2 -> {
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
            .setItems(arrayOf("Ukuran", "Ubah isi", "Ganti nama", "Pindah", "Hapus folder")) { _, which ->
                when (which) {
                    0 -> sizeDialog(key)
                    1 -> pickApps("Isi folder", f.keys) { keys -> saveFolder(f.copy(keys = keys)) }
                    2 -> renameFolder(f)
                    3 -> startPlace(key, 2, 2)
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
                // masih di balik lock screen: tahan dulu, animasi jalan pas unlock
                waitingUnlock = true
                hideForUnlock()
            } else {
                homeLayer.doOnPreDraw { playGather() }
            }
        }
    }

    /** Deteksi notifikasi / quick settings ditutup: fokus window hilang lalu balik. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            if (!paused && dialogs == 0 && !firstResume) focusLost = true
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
            isMoving() -> cancelMove()
            drawerOpen -> closeDrawer(true)
        }
    }
}
