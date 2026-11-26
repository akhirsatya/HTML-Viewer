package com.prototype.htmlviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Menampilkan file HTML di WebView. Tampilan mengikuti ViewerOptions:
 * edge-to-edge (bar transparan), immersive (bar disembunyikan), atau aman (di antara bar).
 */
public class ViewerActivity extends BaseActivity {

    private static final int REQ_FILE_CHOOSER = 2;
    // Base URL "https" supaya localStorage, fetch ke CDN, dll. berjalan normal.
    private static final String BASE_URL = "https://prototype.local/";

    private WebView webView;
    private FrameLayout root;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ViewerOptions opts;

    // Hasil deteksi warna bagian atas halaman
    private int topColor = Color.WHITE;
    private boolean autoDarkIcons = true;
    // Hasil membaca piksel layar sungguhan (bekerja juga untuk isi iframe, mis. artifact Claude)
    private Bitmap probeT, probeB;
    private long pollDelay = 250;
    private int stableCount = 0;
    private boolean touching = false;
    private long lastTouch = 0;
    private boolean autoDarkStatus = true, autoDarkNav = true, pixelOk = false, copying = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable colorPoller = new Runnable() {
        @Override
        public void run() {
            detectTopColor();
            samplePixels();
            handler.postDelayed(this, pollDelay);
        }
    };

    // Ambil warna latar elemen paling atas halaman
    private static final String JS_TOP_COLOR =
            "(function(){function bg(e){if(!e)return null;var c=getComputedStyle(e).backgroundColor;"
          + "return (c&&c!=='transparent'&&!/rgba\\(.*,\\s*0\\)$/.test(c))?c:null;}"
          + "var el=document.elementFromPoint(window.innerWidth/2,4),c=null;"
          + "while(el&&!c){c=bg(el);el=el.parentElement;}"
          + "return c||bg(document.body)||bg(document.documentElement)||'rgb(255,255,255)';})()";


    // Halaman artifact claude.ai punya toolbar sendiri di atas. Sembunyikan semuanya
    // dan buat iframe artifact memenuhi layar supaya tampil seperti app asli.
    private static final String JS_CLEAN_ARTIFACT =
            "(function(){try{"
          +             "var W=innerWidth,H=innerHeight;"
          +             "var fs=[].slice.call(document.querySelectorAll('iframe')).filter(function(x){return x.offsetWidth>=W*0.6&&x.offsetHeight>=H*0.4;});"
          +             "if(!fs.length)return 'wait';"
          +             "fs.sort(function(a,b){return b.offsetWidth*b.offsetHeight-a.offsetWidth*a.offsetHeight;});"
          +             "var f=fs[0];"
          +             "if(window.__pvF===f&&f.style.position==='fixed'&&f.style.zIndex==='2147483647')return 'ok';"
          +             "if(window.__pvUndo){window.__pvUndo.forEach(function(u){try{u[0].style.setProperty(u[1],u[2],u[3]);}catch(e){}});}"
          +             "window.__pvUndo=[];"
          +             "function set(el,k,v){window.__pvUndo.push([el,k,el.style.getPropertyValue(k),el.style.getPropertyPriority(k)]);el.style.setProperty(k,v,'important');}"
          +             "var n=f;"
          +             "while(n&&n!==document.body){var p=n.parentElement;if(!p)break;"
          +             "[].slice.call(p.children).forEach(function(c){if(c!==n&&c.tagName!=='STYLE'&&c.tagName!=='SCRIPT'&&c.tagName!=='LINK'){set(c,'display','none');}});"
          +             "if(p!==document.body){['transform','filter','perspective'].forEach(function(k){set(p,k,'none');});"
          +             "['margin','padding','border','top','left','right','bottom'].forEach(function(k){set(p,k,'0');});"
          +             "set(p,'position','static');set(p,'max-width','none');set(p,'width','100%');set(p,'height','100%');}"
          +             "n=p;}"
          +             "['margin','padding'].forEach(function(k){set(document.body,k,'0');set(document.documentElement,k,'0');});"
          +             "set(document.body,'overflow','hidden');set(document.documentElement,'overflow','hidden');"
          +             "set(f,'position','fixed');['top','left','right','bottom'].forEach(function(k){set(f,k,'0');});"
          +             "set(f,'width','100vw');set(f,'height','100vh');set(f,'max-width','none');set(f,'max-height','none');"
          +             "set(f,'border','0');set(f,'z-index','2147483647');"
          +             "window.__pvF=f;"
          +             "return 'ok';}catch(e){return 'err '+e;}})()";

    private boolean isArtifactPage(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String h = u.getHost();
        return h != null && (h.equals("claude.ai") || h.endsWith(".claude.ai") || h.endsWith(".claudeusercontent.com"))
                && u.getPath() != null && u.getPath().contains("artifact");
    }

    private WebView popupView;

    private void closePopup() {
        if (popupView != null) {
            root.removeView(popupView);
            popupView.destroy();
            popupView = null;
        }
    }

    private int cleanGen = 0;

    /** Ulangi beberapa detik karena halaman dibuat bertahap oleh JavaScript (juga setelah login). */
    private void startCleaning() {
        cleanGen++;
        cleanArtifactPage(cleanGen, 20);
    }

    private void cleanArtifactPage(final int gen, final int tries) {
        if (webView == null || tries <= 0 || gen != cleanGen) return;
        if (isArtifactPage(webView.getUrl())) webView.evaluateJavascript(JS_CLEAN_ARTIFACT, null);
        handler.postDelayed(() -> cleanArtifactPage(gen, tries - 1), 1500);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        opts = ViewerOptions.from(getIntent(), this);

        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true); // popup login (mis. Google) harus jadi jendela terpisah
        WebView.setWebContentsDebuggingEnabled(true); // bisa di-inspect dari chrome://inspect

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                detectTopColor();
                if (isArtifactPage(url)) {
                    startCleaning();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // Link ke luar (tel:, mailto:, intent:, dll.) dilempar ke aplikasi lain
                if (!url.startsWith("http") && !url.startsWith("file") && !url.startsWith("about")
                        && !url.startsWith("data") && !url.startsWith("blob") && !url.startsWith("javascript")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // Popup (mis. login Google): tampil sebagai lapisan di atas halaman, tetap terhubung ke halaman asal
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                final WebView pop = new WebView(ViewerActivity.this);
                WebSettings ps = pop.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setJavaScriptCanOpenWindowsAutomatically(true);
                ps.setSupportMultipleWindows(true);
                ps.setUserAgentString(ps.getUserAgentString().replace("; wv", ""));
                CookieManager.getInstance().setAcceptThirdPartyCookies(pop, true);
                pop.setWebViewClient(new WebViewClient());
                pop.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        closePopup();
                    }
                });
                closePopup();
                popupView = pop;
                root.addView(pop, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(pop);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                closePopup();
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            // Dukungan video/elemen fullscreen (requestFullscreen)
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                applyBars();
            }

            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    root.removeView(customView);
                    customView = null;
                }
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
                applyBars();
            }
        });

        applyOptions();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadFromIntent(getIntent());
            showHintOnce();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        opts = ViewerOptions.from(intent, this);
        applyOptions();
        loadFromIntent(intent);
    }

    // ---------- Pengaturan ----------

    @SuppressWarnings("deprecation")
    private void applyOptions() {
        switch (opts.orient) {
            case ViewerOptions.ORIENT_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                break;
            case ViewerOptions.ORIENT_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        if (opts.keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        WebSettings s = webView.getSettings();
        s.setSupportZoom(opts.zoom);
        s.setBuiltInZoomControls(opts.zoom);
        s.setDisplayZoomControls(false);
        applyBackgrounds();
        applyBars();
    }

    private void loadFromIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            Uri shared = text == null ? null : MainActivity.extractUrl(text);
            if (shared != null) {
                uri = shared;
            } else if (text != null && text.contains("<")) {
                webView.loadDataWithBaseURL(BASE_URL, text, "text/html", "UTF-8", null);
                return;
            }
            if (uri == null) {
                Toast.makeText(this, getString(R.string.toast_no_file), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            // Link online (mis. artifact Claude): muat langsung, tetap fullscreen
            WebSettings ws = webView.getSettings();
            ws.setUserAgentString(ws.getUserAgentString().replace("; wv", ""));
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);
            webView.loadUrl(uri.toString());
            MainActivity.addRecent(this, uri, MainActivity.displayName(this, uri), opts.mode);
            return;
        }

        if ("file".equals(uri.getScheme())) {
            // file:// bisa dimuat langsung, sehingga file CSS/JS/gambar relatif ikut terbaca
            webView.loadUrl(uri.toString());
            MainActivity.addRecent(this, uri, uri.getLastPathSegment(), opts.mode);
            return;
        }

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("stream null");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
            MainActivity.addRecent(this, uri, MainActivity.displayName(this, uri), opts.mode);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_open_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** Minta layar berjalan di refresh rate tertinggi (mis. 120 Hz) supaya animasi terasa halus. */
    private void requestHighRefreshRate() {
        try {
            android.view.Display d = Build.VERSION.SDK_INT >= 30 ? getDisplay() : getWindowManager().getDefaultDisplay();
            if (d == null) return;
            android.view.Display.Mode cur = d.getMode();
            android.view.Display.Mode best = cur;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) {
                    best = m;
                }
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {
        }
    }

    // ---------- Ketuk 3 jari: panel pengaturan prototype ----------

    private boolean tfActive = false;
    private long tfStart = 0;
    private final float[] tfX = new float[3], tfY = new float[3];
    private boolean menuOpen = false;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        lastTouch = android.os.SystemClock.uptimeMillis();
        int am = ev.getActionMasked();
        if (am == android.view.MotionEvent.ACTION_DOWN) touching = true;
        else if (am == android.view.MotionEvent.ACTION_UP || am == android.view.MotionEvent.ACTION_CANCEL) touching = false;
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                tfActive = false;
                break;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                if (ev.getPointerCount() == 3) {
                    tfActive = true;
                    tfStart = ev.getEventTime();
                    for (int i = 0; i < 3; i++) {
                        tfX[i] = ev.getX(i);
                        tfY[i] = ev.getY(i);
                    }
                } else {
                    tfActive = false;
                }
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                if (tfActive && ev.getPointerCount() == 3) {
                    float slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop() * 2f;
                    for (int i = 0; i < 3; i++) {
                        if (Math.abs(ev.getX(i) - tfX[i]) > slop || Math.abs(ev.getY(i) - tfY[i]) > slop) {
                            tfActive = false;
                        }
                    }
                }
                break;
            case android.view.MotionEvent.ACTION_POINTER_UP:
                if (tfActive && ev.getPointerCount() == 3 && ev.getEventTime() - tfStart < 600) {
                    tfActive = false;
                    showMenu();
                }
                break;
            case android.view.MotionEvent.ACTION_CANCEL:
            case android.view.MotionEvent.ACTION_UP:
                tfActive = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showMenu() {
        if (menuOpen) return;
        menuOpen = true;
        ViewerMenu m = new ViewerMenu(this, opts, this::applyOptions, this::finish);
        m.show(() -> menuOpen = false);
    }

    private void showHintOnce() {
        android.content.SharedPreferences p = getSharedPreferences("hints", MODE_PRIVATE);
        if (p.getBoolean("three_finger", false)) return;
        p.edit().putBoolean("three_finger", true).apply();
        handler.postDelayed(() -> Toast.makeText(this, R.string.hint_three_finger, Toast.LENGTH_LONG).show(), 1200);
    }

    // ---------- Bar sistem ----------

    private boolean effectiveDarkIcons() {
        return iconDark(autoDarkIcons);
    }

    private boolean iconDark(boolean auto) {
        switch (opts.icons) {
            case ViewerOptions.ICONS_DARK:
                return true;
            case ViewerOptions.ICONS_LIGHT:
                return false;
            default:
                return auto;
        }
    }

    private int barHeight(String name, int fallbackDp) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        return h > 0 ? h : (int) (fallbackDp * getResources().getDisplayMetrics().density);
    }

    /** Baca warna piksel di balik status bar dan navigation bar, lalu atur warna ikonnya. */
    private void samplePixels() {
        if (webView == null || customView != null || copying || opts.mode == ViewerOptions.MODE_IMMERSIVE) return;
        // Jangan baca piksel saat jari menyentuh layar (bikin frame tersendat); setelah jari lepas langsung diperbarui
        if (touching) return;
        final View dv = getWindow().getDecorView();
        final int w = dv.getWidth(), h = dv.getHeight();
        if (w <= 0 || h <= 0 || !dv.isAttachedToWindow()) return;
        final boolean edge = opts.mode == ViewerOptions.MODE_EDGE;
        final int sb = barHeight("status_bar_height", 24), nb = Math.min(barHeight("navigation_bar_height", 24), h / 6);
        final int strip = Math.max(6, (int) (6 * getResources().getDisplayMetrics().density));
        final Rect top = edge ? new Rect(0, 0, w, sb) : new Rect(0, sb, w, Math.min(h, sb + strip));
        final Rect bottom = edge ? new Rect(0, h - nb, w, h) : new Rect(0, Math.max(0, h - nb - strip), w, h - nb);
        if (top.height() <= 0 || bottom.height() <= 0) return;
        copying = true;
        if (probeT == null) {
            probeT = Bitmap.createBitmap(24, 4, Bitmap.Config.ARGB_8888);
            probeB = Bitmap.createBitmap(24, 4, Bitmap.Config.ARGB_8888);
        }
        final Bitmap bt = probeT, bb = probeB;
        try {
            PixelCopy.request(getWindow(), top, bt, r1 -> {
                if (r1 != PixelCopy.SUCCESS) {
                    copying = false;
                    return;
                }
                try {
                    PixelCopy.request(getWindow(), bottom, bb, r2 -> {
                        copying = false;
                        if (r2 == PixelCopy.SUCCESS) applySamples(bt, bb);
                    }, handler);
                } catch (Exception e) {
                    copying = false;
                }
            }, handler);
        } catch (Exception e) {
            copying = false;
        }
    }

    private int[] avg(Bitmap b) {
        long r = 0, g = 0, bl = 0;
        int n = b.getWidth() * b.getHeight();
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                int c = b.getPixel(x, y);
                r += Color.red(c);
                g += Color.green(c);
                bl += Color.blue(c);
            }
        }
        return new int[]{(int) (r / n), (int) (g / n), (int) (bl / n)};
    }

    private boolean isBright(int[] c) {
        return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0 > 0.55;
    }

    private void applySamples(Bitmap bt, Bitmap bb) {
        int[] t = avg(bt), n = avg(bb);
        boolean sd = isBright(t), nd = isBright(n);
        int color = Color.rgb(t[0], t[1], t[2]);
        boolean changed = !pixelOk || sd != autoDarkStatus || nd != autoDarkNav;
        pixelOk = true;
        autoDarkStatus = sd;
        autoDarkNav = nd;
        autoDarkIcons = sd;
        int dr = Math.abs(Color.red(color) - Color.red(topColor)) + Math.abs(Color.green(color) - Color.green(topColor))
                + Math.abs(Color.blue(color) - Color.blue(topColor));
        if (dr > 30) { // abaikan perubahan warna kecil supaya tidak sibuk menggambar ulang
            topColor = color;
            applyBackgrounds();
        }
        if (changed) applyBars();
        // Cepat saat warna sedang berubah, hemat saat stabil
        if (changed || dr > 30) {
            stableCount = 0;
            pollDelay = 250;
        } else if (++stableCount > 6) {
            pollDelay = 600;
        }
    }

    private void applyBackgrounds() {
        root.setBackgroundColor(topColor);
        getWindow().setBackgroundDrawable(new ColorDrawable(topColor));
    }

    /** Terapkan mode tampilan yang dipilih. */
    private void applyBars() {
        if (customView != null || opts.mode == ViewerOptions.MODE_IMMERSIVE) {
            hideSystemBars();
        } else {
            showTransparentBars(opts.mode == ViewerOptions.MODE_EDGE);
        }
    }

    /**
     * Bar sistem terlihat dengan latar transparan.
     * edge = true: konten tampil sampai ke belakang bar. edge = false: konten berada di antara bar.
     */
    @SuppressWarnings("deprecation")
    private void showTransparentBars(boolean edge) {
        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            w.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            // Matikan lapisan semi-gelap otomatis di belakang bar
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        boolean dark = iconDark(autoDarkStatus);
        boolean navDark = iconDark(autoDarkNav);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(!edge);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                int st = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                int nv = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance((dark ? st : 0) | (navDark ? nv : 0), st | nv);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (edge) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            if (dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (navDark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Bar sistem disembunyikan (geser dari tepi untuk memunculkan sementara). */
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    /** Cek warna bagian atas halaman; dipakai untuk warna ikon bar (mode otomatis) dan latar di mode aman. */
    private void detectTopColor() {
        if (webView == null || customView != null || pixelOk) return;
        webView.evaluateJavascript(JS_TOP_COLOR, value -> {
            if (value == null || pixelOk) return;
            String v = value.replace("\"", "");
            int a = v.indexOf('('), b = v.indexOf(')');
            if (a < 0 || b < 0) return;
            String[] p = v.substring(a + 1, b).split(",");
            if (p.length < 3) return;
            try {
                double r = Double.parseDouble(p[0].trim());
                double g = Double.parseDouble(p[1].trim());
                double bl = Double.parseDouble(p[2].trim());
                int color = Color.rgb((int) r, (int) g, (int) bl);
                boolean wantDark = (0.299 * r + 0.587 * g + 0.114 * bl) / 255.0 > 0.6;
                boolean changed = false;
                if (color != topColor) {
                    topColor = color;
                    changed = true;
                }
                if (wantDark != autoDarkIcons) {
                    autoDarkIcons = wantDark;
                    autoDarkStatus = wantDark;
                    autoDarkNav = wantDark;
                    changed = true;
                }
                if (changed) {
                    applyBackgrounds();
                    applyBars();
                }
            } catch (NumberFormatException ignored) {
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyBars();
    }

    // ---------- Lifecycle ----------

    @Override
    public void onBackPressed() {
        if (popupView != null) {
            closePopup();
        } else if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        handler.removeCallbacks(colorPoller);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        requestHighRefreshRate();
        applyBars();
        handler.removeCallbacks(colorPoller);
        handler.postDelayed(colorPoller, 800);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
