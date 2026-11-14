package com.story.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * #360 THE BUSINESS HOME'S APPS (owner, 14 Nov 2026: 'Let's create a whole different experience on business - they
 * shouldn't show the phone's home screen but have a specific place for the business designed to run business'; 'Yes
 * build the business home'). A business's Email, Instagram, Facebook... open here: the service's own web version, inside
 * Lockedintool, in that business's OWN browser profile (Android WebView multi-profile) - so each business has its own
 * logins, cookies and storage, and never sees Personal's or another business's. Personal keeps the phone's real apps.
 */
class BizWebActivity : Activity() {
    private var web: WebView? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setWindowAnimations(0)
        val space = intent.getStringExtra(EXTRA_SPACE)?.ifEmpty { null } ?: run { finish(); return }
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val bg = StoryTheme.swap(this, Color.BLACK); val fg = StoryTheme.swap(this, Color.WHITE)
        window.decorView.setBackgroundColor(bg)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); fitsSystemWindows = true }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
        }
        fun btn(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(fg); textSize = 20f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()); setOnClickListener { f() }
        }
        bar.addView(btn("‹") { goBack() })
        bar.addView(TextView(this).apply {
            text = title; setTextColor(fg); textSize = 15f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(btn("↻") { web?.reload() })
        bar.addView(btn("×") { finish() })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))
        // Each business's own logins: its own browser profile. A phone whose WebView can't keep separate profiles never
        // opens a business app in the shared one (that would mix logins) - it says so instead.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            root.addView(TextView(this).apply {
                text = "This phone's Android System WebView needs an update to keep each business's logins separate. Update it from the Play Store, then try again."
                setTextColor(fg); textSize = 15f; gravity = Gravity.CENTER
                setPadding((28 * dp).toInt(), (60 * dp).toInt(), (28 * dp).toInt(), 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setContentView(root)
            ProblemLog.record(this, "bizweb", "no multi-profile WebView", "open")
            return
        }
        val w = WebView(this)
        runCatching { WebViewCompat.setProfile(w, "biz-" + space.replace(Regex("[^A-Za-z0-9_-]"), "")) }
            .onFailure { ProblemLog.record(this, "bizweb", "profile: " + it.javaClass.simpleName, "open"); finish(); return }
        web = w
        w.setBackgroundColor(bg)
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false; loadWithOverviewMode = true; useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            // A normal phone browser's name (like the TradingView window), so Google / Meta sign-ins are allowed.
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(w, true) }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.scheme == "http" || u.scheme == "https") return false
                // intent: / mailto: / tel: links go to the phone, as in any browser.
                runCatching { startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { runCatching { request.grant(request.resources) } }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                val i = runCatching { p.createIntent() }.getOrNull() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                if (p.mode == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                return runCatching { startActivityForResult(i, 77); true }.getOrElse { fileCb = null; false }
            }
        }
        root.addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        if (b != null) w.restoreState(b) else w.loadUrl(url)
    }

    @Deprecated("Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77) return
        val cb = fileCb ?: return; fileCb = null
        val clip = data?.clipData
        val out = when {
            resultCode != RESULT_OK -> null
            clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        cb.onReceiveValue(out)
    }

    private fun goBack() { val w = web; if (w != null && w.canGoBack()) w.goBack() else finish() }
    @Deprecated("Back") override fun onBackPressed() { goBack() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web?.saveState(out) }
    override fun onPause() { web?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }
    override fun finish() { super.finish(); overridePendingTransition(0, 0) }

    companion object {
        const val EXTRA_URL = "url"; const val EXTRA_TITLE = "title"; const val EXTRA_SPACE = "space"
    }
}
