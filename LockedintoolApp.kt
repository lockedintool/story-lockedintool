package com.story.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Lockedintool as an APP (AppMode off - owner, 5 Nov 2026: 'Let's make lockedintool an app also'). A normal app
 *  window: while it is on screen the Pages live inside it (the same way Only system's Home holds them -
 *  OverlayService.appShow), and when you leave it they go away with it - nothing of Lockedintool stays over other apps.
 *  Opened from the app icon (MainActivity forwards here once Lockedintool is set up), a widget or a notification. */
class LockedintoolApp : Activity() {
    companion object {
        @Volatile var current: LockedintoolApp? = null
        const val EXTRA_PAGE = "page"
        fun open(c: android.content.Context, page: String?) {
            runCatching { c.startActivity(Intent(c, LockedintoolApp::class.java).apply { if (page != null) putExtra(EXTRA_PAGE, page) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
        }
    }
    private var page: String? = null
    private var tries = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        // #289 (owner's video, 13 Nov 2026: about a second of black after tapping the icon): until the live Pages have
        // drawn inside this window, it shows the exact copy of your Home Screen from last time - never black.
        val root = android.widget.FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        shot = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_START; adjustViewBounds = true }
        root.addView(shot, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        current = this
        showShot()
        page = intent.getStringExtra(EXTRA_PAGE)
        KeepAlive.ensureRunning(this)
    }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); setIntent(intent); intent?.getStringExtra(EXTRA_PAGE)?.let { page = it } }
    private var shot: android.widget.ImageView? = null
    private fun showShot() { runCatching {
        if (android.os.Build.VERSION.SDK_INT < 34) return   // only the exact screen copy (Android 14+), never a drawn picture
        val svc = OverlayService.instance
        // #374 (owner's video, 14 Nov 2026: 'this not supposed to be happening - see the flash'): when the company screen is
        // what opens, the Dashboard's picture never shows first (it flashed, then black, then the company screen).
        if (svc?.chooserOnOpen() == true || (svc == null && getSharedPreferences("story", MODE_PRIVATE).getBoolean("spaces_on", false))) {
            shot?.setImageDrawable(null); return }
        val prefs = getSharedPreferences("story", MODE_PRIVATE)
        if (svc?.homeShot == null && !prefs.getBoolean("home_shot_exact", false)) return
        val b = svc?.homeShot ?: android.graphics.BitmapFactory.decodeFile(java.io.File(filesDir, "home_shot.jpg").path) ?: return
        val top = svc?.homeShot?.let { svc.homeShotTop } ?: prefs.getInt("home_shot_top", 0)
        val v = shot ?: return
        (v.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { it.topMargin = top.coerceAtLeast(0); v.layoutParams = it }
        v.setImageBitmap(b)
    } }
    override fun onResume() { super.onResume(); tries = 0; showShot(); attach() }
    private fun attach() {
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            val svc = OverlayService.instance
            if (svc == null) {   // Lockedintool is still starting: look again in a moment (a few times at most)
                if (tries++ < 10) window.decorView.postDelayed({ attach() }, 300)
                return@post
            }
            svc.appShow(this, page); page = null
        }
    }
    override fun onStop() { OverlayService.instance?.appStopped(this); super.onStop() }
    override fun onDestroy() { OverlayService.instance?.homeGone(this); if (current === this) current = null; super.onDestroy() }
}
