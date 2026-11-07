package com.deltatech.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import java.net.URLEncoder

class MainActivity : Activity() {
    private val BLUE = 0xFF42A9F5.toInt(); private val BG = 0xFF0F1722.toInt(); private val BAR = 0xFF172231.toInt()
    private val HOME = "https://www.google.com"
    private val providers = listOf(
        "Claude" to "https://claude.ai/new?q=", "ChatGPT" to "https://chatgpt.com/?q=", "Gemini" to "https://gemini.google.com/app")
    private var provider = 0
    private val webs = mutableListOf<WebView>()
    private var active = 0
    private var desktop = false
    private lateinit var url: EditText
    private lateinit var panes: LinearLayout
    private lateinit var aiBox: LinearLayout
    private lateinit var ai: WebView
    private lateinit var aiTitle: TextView
    private lateinit var progress: ProgressBar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }

    private fun btn(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BAR; window.navigationBarColor = BAR
        WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }

        val bar = LinearLayout(this).apply { setBackgroundColor(BAR); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(4), dp(6)) }
        bar.addView(ImageView(this).apply { setImageResource(R.drawable.logo); setOnClickListener { load(HOME) } }, LinearLayout.LayoutParams(dp(30), dp(30)))
        url = EditText(this).apply {
            setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xFF8899AA.toInt()); hint = "Tìm kiếm hoặc nhập địa chỉ"
            textSize = 14f; background = round(0xFF22324A.toInt(), 20); setPadding(dp(14), dp(8), dp(14), dp(8))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> load(url.text.toString()); clearFocus(); true }
            setSelectAllOnFocus(true)
        }
        bar.addView(url, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8); marginEnd = dp(4) })
        bar.addView(btn("◫") { toggleSplit() }); bar.addView(btn("✨") { toggleAi() })
        bar.addView(btn("⌘") { devTools() }); bar.addView(btn("⋮") { menu(it) })
        root.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressDrawable.setColorFilter(BLUE, PorterDuff.Mode.SRC_IN); visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        panes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        addPane(intent?.dataString ?: HOME)

        aiBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setBackgroundColor(BAR) }
        val h = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(4), 0) }
        aiTitle = TextView(this).apply { setTextColor(BLUE); textSize = 15f; setTypeface(null, Typeface.BOLD) }
        h.addView(aiTitle, LinearLayout.LayoutParams(0, -2, 1f))
        h.addView(btn("⇄") { provider = (provider + 1) % providers.size; openAi("") })
        h.addView(btn("✕") { aiBox.visibility = View.GONE })
        aiBox.addView(h)
        ai = newWeb(); aiBox.addView(ai, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(aiBox, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun newWeb(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
        settings.loadWithOverviewMode = true; settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(object { @JavascriptInterface fun ask(t: String) { runOnUiThread { openAi("Hãy trả lời/giải thích giúp tôi: $t") } } }, "Delta")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String) { v.evaluateJavascript(SELECT_JS, null); if (v === webs.getOrNull(active)) url.setText(u) }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { if (v === webs.getOrNull(active)) { progress.progress = p; progress.visibility = if (p < 100) View.VISIBLE else View.GONE } }
        }
        setOnTouchListener { v, _ -> webs.indexOf(v).let { if (it >= 0) { active = it; highlight() } }; false }
    }

    private fun addPane(u: String) { val w = newWeb(); webs.add(w); panes.addView(w, LinearLayout.LayoutParams(-1, 0, 1f)); w.loadUrl(u); highlight() }
    private fun highlight() = webs.forEachIndexed { i, w -> w.setBackgroundColor(if (webs.size > 1 && i == active) BLUE else Color.WHITE); w.setPadding(0, if (webs.size > 1 && i == active) 3 else 0, 0, 0) }
    private fun cur() = webs[active.coerceAtMost(webs.size - 1)]

    private fun load(q: String) {
        val s = q.trim(); if (s.isEmpty()) return
        cur().loadUrl(when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            !s.contains(" ") && s.contains(".") -> "https://$s"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(s, "UTF-8")
        })
    }

    private fun toggleSplit() {
        if (webs.size == 1) { addPane(HOME); active = 1; highlight(); toast("Chế độ 2 tab: chạm vào tab để chọn tab đang dùng") }
        else { val w = webs.removeAt(1); panes.removeView(w); w.destroy(); active = 0; highlight() }
    }

    private fun toggleAi() { if (aiBox.visibility == View.VISIBLE) aiBox.visibility = View.GONE else openAi("") }

    private fun openAi(prompt: String) {
        aiBox.visibility = View.VISIBLE
        val (name, base) = providers[provider]; aiTitle.text = "✨ $name"
        if (prompt.isEmpty()) { if (ai.url == null || !ai.url!!.contains(Uri(base))) ai.loadUrl(base.substringBefore("?")); return }
        if (base.contains("?q=")) ai.loadUrl(base + URLEncoder.encode(prompt, "UTF-8"))
        else { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("q", prompt)); ai.loadUrl(base); toast("Đã copy câu hỏi, hãy dán vào ô chat") }
    }
    private fun Uri(b: String) = b.substringAfter("//").substringBefore("/")

    private fun askPage() = cur().evaluateJavascript(
        "(function(){var s=getSelection().toString().trim();return s||document.body.innerText.slice(0,3000)})()") { r ->
        val t = r.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
        openAi("Dựa vào nội dung sau, hãy trả lời câu hỏi/điền form giúp tôi:\n$t")
    }

    private fun devTools() = cur().evaluateJavascript(ERUDA_JS, null)

    private fun menu(v: View) = PopupMenu(this, v).apply {
        menu.add(0, 1, 0, "Hỏi AI về trang / đoạn chọn"); menu.add(0, 2, 1, "Đổi AI (${providers[provider].first})")
        menu.add(0, 3, 2, if (desktop) "Giao diện điện thoại" else "Trang web cho máy tính")
        menu.add(0, 4, 3, "Tải lại"); menu.add(0, 5, 4, "Xóa cache & cookie"); menu.add(0, 6, 5, "Giới thiệu")
        setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> askPage(); 2 -> { provider = (provider + 1) % providers.size; openAi("") }
                3 -> { desktop = !desktop; webs.forEach { w -> w.settings.userAgentString = if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36" else null; w.settings.useWideViewPort = desktop; w.reload() } }
                4 -> cur().reload()
                5 -> { cur().clearCache(true); CookieManager.getInstance().removeAllCookies(null); toast("Đã xóa") }
                6 -> android.app.AlertDialog.Builder(this@MainActivity).setTitle("Delta Browser").setMessage("Delta Browser by Delta Technology\nPhiên bản 1.0").setPositiveButton("OK", null).show()
            }; true }
        show() }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @Deprecated("") override fun onBackPressed() {
        when { aiBox.visibility == View.VISIBLE && ai.canGoBack().not() -> aiBox.visibility = View.GONE
            cur().canGoBack() -> cur().goBack(); else -> super.onBackPressed() }
    }

    companion object {
        const val ERUDA_JS = "(function(){if(window.eruda){eruda._isShow?eruda.hide():eruda.show();return;}var s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/eruda@3.0.1/eruda.min.js';s.onload=function(){eruda.init();eruda.show();};document.head.appendChild(s);})();"
        const val SELECT_JS = """(function(){if(window.__dl)return;window.__dl=1;
var b=document.createElement('div');b.textContent='✨ Hỏi AI';
b.style.cssText='position:fixed;z-index:2147483647;display:none;background:#42A9F5;color:#fff;font:600 14px sans-serif;padding:9px 14px;border-radius:20px;box-shadow:0 4px 14px rgba(0,0,0,.35)';
document.documentElement.appendChild(b);var t;
document.addEventListener('selectionchange',function(){clearTimeout(t);t=setTimeout(function(){var s=getSelection(),x=s.toString().trim();
if(x.length<2||!s.rangeCount){b.style.display='none';return;}
var r=s.getRangeAt(0).getBoundingClientRect();b.style.display='block';b.style.left=Math.max(8,Math.min(innerWidth-110,r.left))+'px';
var y=r.top-50;b.style.top=(y<8?r.bottom+60:y)+'px';b.dataset.t=x;},250);});
b.addEventListener('click',function(){Delta.ask(b.dataset.t);b.style.display='none';});})();"""
    }
}
