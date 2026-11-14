package com.story.launcher

import android.content.Context

/** Lockedintool is an APP first; being a SYSTEM is a feature you switch on (owner, 5 Nov 2026: 'Let's make lockedintool
 *  an app also. The systems will be a feature that someone will choose for themselves. If turned on lockedintool will
 *  work as a system'; Settings > System switch, turned on / off only after the phone's own fingerprint / face / PIN).
 *  OFF = a pure app (owner chose 'Nothing - pure app'): you open it from its icon and use the Dashboard and Pages inside
 *  its own window; nothing runs outside it - no Station 3 over apps, no Apps | Lockedintool switch, no Lockedintool lock
 *  screen, no Always On Display, no lock-screen dial pad / Wallet button, no live widgets. Messages and calls still come
 *  in as notifications. ON = Second system or Only system. Phones that already ran Lockedintool before this keep
 *  working as a system (pref 'enabled' was set); new installs start as the app. */
object AppMode {
    private const val PREF = "system_on"
    /** #370 THE APP-ONLY TEST (owner, 14 Nov 2026: 'Let's make lockedintool just an app ... make everything work like inside
     *  the app'; everything before is kept in the vault branch vault-2026-11-14 and the published 1.544 is left alone):
     *  Lockedintool is ONLY an app - never Second / Only system. Set false to bring the systems back. */
    const val APP_ONLY = true
    fun systemOn(c: Context): Boolean {
        if (APP_ONLY) return false
        val p = c.getSharedPreferences("story", Context.MODE_PRIVATE)
        if (!p.contains(PREF)) { val v = p.getBoolean("enabled", false); p.edit().putBoolean(PREF, v).apply(); return v }
        return p.getBoolean(PREF, true)
    }
    fun appOnly(c: Context) = !systemOn(c)
    fun set(c: Context, on: Boolean) { c.getSharedPreferences("story", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).commit(); SystemMode.syncHome(c); syncLauncher(c) }
    /** The app icon's name follows the switch (#300): 'Lockedintool System' while the System switch is on, 'Lockedintool
     *  App' while it's off - the matching icon entry is switched on, the other off (only when it really changes). */
    fun syncLauncher(c: Context) { runCatching {
        val pm = c.packageManager
        val on = systemOn(c)
        val sys = android.content.ComponentName(c, "com.story.launcher.LauncherSystem")
        val app = android.content.ComponentName(c, "com.story.launcher.LauncherApp")
        fun state(cn: android.content.ComponentName, want: Boolean) {
            val s = if (want) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(cn) != s) pm.setComponentEnabledSetting(cn, s, android.content.pm.PackageManager.DONT_KILL_APP)
        }
        // the new one first, so there is never a moment with no icon
        if (on) { state(sys, true); state(app, false) } else { state(app, true); state(sys, false) }
    } }
}
