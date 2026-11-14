package com.story.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

/**
 * #345 (owner, 14 Nov 2026: 'the company screen is showing up every time, just that the glimpse of app and home screen
 * still gets visible before it shows up'): the phone's unlock reveals the top ACTIVITY first and only then draws app
 * overlays like the company screen - so the app / home screen you were on showed for a moment. When the screen goes off
 * (Business, Second system) this plain screen in the company screen's own colour (the System theme's black / white) is put
 * on top, under the lock screen; the unlock reveals it, the company screen lands on it, and it goes away the moment you
 * choose - so what you were in is never glimpsed. Not in Recents, no animation, nothing on it to tap.
 */
class SpacesCover : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        instance = this; made++
        window.setWindowAnimations(0)
        val bg = StoryTheme.swap(this, Color.BLACK)
        window.decorView.setBackgroundColor(bg)
        setContentView(View(this).apply { setBackgroundColor(bg) })
    }
    override fun onResume() { super.onResume(); resumed = true }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onDestroy() { if (instance === this) instance = null; gone++; super.onDestroy() }
    @Deprecated("Back is the phone's own") override fun onBackPressed() { }   // the company screen above decides
    override fun finish() { super.finish(); overridePendingTransition(0, 0) }
    companion object {
        @Volatile var instance: SpacesCover? = null
        @Volatile var resumed = false
        @Volatile var made = 0
        @Volatile var gone = 0
        @Volatile var closedBy = ""
        fun close() { instance?.let { a -> closedBy = Throwable().stackTrace.getOrNull(1)?.methodName ?: "?"; runCatching { a.finish() } } }
    }
}
