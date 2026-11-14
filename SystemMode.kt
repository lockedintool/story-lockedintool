package com.story.launcher

import android.content.Context
import android.telecom.TelecomManager

/** Lockedintool as your system (owner, a section on the Subscription page): the DASHBOARD is your home screen, so the
 *  phone's own home screen is sent to the Dashboard and you always land back on Lockedintool. Your apps all still work
 *  (owner, 28 Oct 2026) - open them from the Apps button, app pages or recent apps; only the bare home screen redirects.
 *  It only goes off by logging out or deleting your account (owner).
 *  Always still works: phone calls (the phone's dialer / call screen) and emergency, the lock screen and the phone's
 *  own panel, Android's installer (Lockedintool's updates) and permission questions, and whatever Lockedintool itself
 *  opens for one of its own features (camera, gallery, your bank's approval page, a document, a permission switch...):
 *  every launch Lockedintool makes lets the app it opens through (grant), while it stays in front. */
object SystemMode {
    private const val PREF = "system_mode"
    fun on(c: Context): Boolean = c.getSharedPreferences("story", Context.MODE_PRIVATE).getBoolean(PREF, false)
    fun set(c: Context, v: Boolean) { c.getSharedPreferences("story", Context.MODE_PRIVATE).edit().putBoolean(PREF, v).commit(); syncHome(c) }
    /** Lockedintool is the phone's HOME app ONLY in Only system (#237 - owner, 11 Nov 2026: as an app 'I cant close it ...
     *  I need to log out or deactivate the account or delete the account'; 'lockedintool when its switched into an app it
     *  does act like a app'). After leaving Only system the phone kept Lockedintool as its Home app, so every Home swipe
     *  landed back in Lockedintool. Its Home screen now only exists while Only system is on (the switch on): anywhere else
     *  the phone's own home screen is the Home again, by itself - nothing to pick. */
    /** #367 (owner, 14 Nov 2026: 'the only should be lockedintools' - chose 'Yes, make it the Home app'): on SECOND SYSTEM
     *  with companies set up, Lockedintool is the phone's real Home app too - Home always lands on the focus's home
     *  (Business home / Personal home); the phone's own home screen never shows. Turning companies / the System switch off
     *  gives the Home back to the phone by itself. */
    fun spaceHome(c: Context): Boolean = !on(c) && AppMode.systemOn(c) && !WidgetMode.on(c) &&
        c.getSharedPreferences("story", Context.MODE_PRIVATE).getBoolean("spaces_on", false)
    fun syncHome(c: Context) { runCatching {
        val want = (on(c) && AppMode.systemOn(c)) || spaceHome(c)
        val cn = android.content.ComponentName(c, LockedintoolHome::class.java)
        val st = if (want) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (c.packageManager.getComponentEnabledSetting(cn) != st) c.packageManager.setComponentEnabledSetting(cn, st, android.content.pm.PackageManager.DONT_KILL_APP)
    } }

    private val ALWAYS = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.android.incallui",
        "com.android.emergency", "com.google.android.apps.safetyhub", "com.samsung.android.emergency",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.documentsui", "com.google.android.documentsui",
        "com.android.providers.media.module", "com.google.android.providers.media.module", "com.android.printspooler",
        "com.google.android.gms")

    @Volatile private var graceUntil = 0L
    @Volatile private var grantedPkg: String? = null
    /** Lockedintool is opening something itself: the app that comes up next is let through. */
    fun grant() { graceUntil = System.currentTimeMillis() + 20_000 }
    /** The Pages are back: what Lockedintool opened before doesn't count any more. */
    fun reset() { grantedPkg = null; graceUntil = 0L }

    fun allowed(c: Context, pkg: String, home: String?): Boolean {
        if (pkg == c.packageName || pkg in ALWAYS || pkg == grantedPkg) return true
        val dialer = runCatching { (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage }.getOrNull()
        if (pkg == dialer) return true
        if (pkg != home && System.currentTimeMillis() < graceUntil) { grantedPkg = pkg; graceUntil = 0L; return true }
        return false
    }

    /** Apps work in Only system now (owner, 28 Oct 2026: 'apps on dashboard for only system are not available please
     *  fix that'): opening an app / app page / trading platform / sharing to an app is allowed - the Dashboard stays
     *  your home, nothing else is blocked. Kept for its callers; it never refuses. */
    fun refuses(c: Context): Boolean = false

}
