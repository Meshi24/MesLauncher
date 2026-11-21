package com.example.simplelauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : Activity() {

    private lateinit var recycler: RecyclerView
    private val appAdapter = AppAdapter { app, view -> launchApp(app, view) }
    private var firstResume = true

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 4)
            adapter = appAdapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        setContentView(recycler)

        val d = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                (8 * d).toInt(), b.top + (16 * d).toInt(),
                (8 * d).toInt(), b.bottom + (16 * d).toInt()
            )
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

    override fun onResume() {
        super.onResume()
        if (firstResume) { firstResume = false; return }
        recycler.scaleX = 0.92f
        recycler.scaleY = 0.92f
        recycler.alpha = 0f
        recycler.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(250).setInterpolator(DecelerateInterpolator()).start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) recycler.smoothScrollToPosition(0)
    }

    @Deprecated("Launcher stays open")
    override fun onBackPressed() {}

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    App(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                        it.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { appAdapter.submit(list) }
        }.start()
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
}
