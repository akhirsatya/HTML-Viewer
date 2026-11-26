package com.prototype.htmlviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable colorPoller = new Runnable() {
        @Override
        public void run() {
            detectTopColor();
            handler.postDelayed(this, 1500);
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
          + "var old=document.querySelectorAll('[data-pv-hide],[data-pv-anc]');"
          + "for(var i=0;i<old.length;i++){old[i].removeAttribute('data-pv-hide');old[i].removeAttribute('data-pv-anc');}"
          + "var W=window.innerWidth,H=window.innerHeight;"
          + "var fs=[].slice.call(document.querySelectorAll('iframe')).filter(function(x){"
          + "return x.offsetWidth>=W*0.6&&x.offsetHeight>=H*0.4;});"
          + "if(!fs.length)return 'wait';"
          + "fs.sort(function(a,b){return b.offsetWidth*b.offsetHeight-a.offsetWidth*a.offsetHeight;});"
          + "var f=fs[0];"
          + "var st=document.getElementById('pv-clean');"
          + "if(!st){st=document.createElement('style');st.id='pv-clean';"
          + "st.textContent='[data-pv-hide]{display:none!important}"
          + "[data-pv-anc]{transform:none!important;filter:none!important;margin:0!important;padding:0!important;border:0!important;"
          + "position:static!important;max-width:none!important;width:100%!important;height:100%!important}"
          + "iframe[data-pv-frame]{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;"
          + "width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;border:0!important;z-index:2147483647!important}"
          + "html,body{margin:0!important;padding:0!important;overflow:hidden!important}';"
          + "document.head.appendChild(st);}"
          + "var n=f;"
          + "while(n&&n!==document.body){var p=n.parentElement;if(!p)break;"
          + "[].slice.call(p.children).forEach(function(c){if(c!==n&&c.tagName!=='STYLE'&&c.tagName!=='SCRIPT'&&c.tagName!=='LINK'){c.setAttribute('data-pv-hide','1');}});"
          + "if(p!==document.body)p.setAttribute('data-pv-anc','1');"
          + "n=p;}"
          + "var prev=document.querySelectorAll('iframe[data-pv-frame]');for(var j=0;j<prev.length;j++)prev[j].removeAttribute('data-pv-frame');"
          + "f.setAttribute('data-pv-frame','1');"
          + "return 'ok';}catch(e){return 'err';}})()";

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
        cleanArtifactPage(cleanGen, 40);
    }

    private void cleanArtifactPage(final int gen, final int tries) {
        if (webView == null || tries <= 0 || gen != cleanGen) return;
        if (isArtifactPage(webView.getUrl())) webView.evaluateJavascript(JS_CLEAN_ARTIFACT, null);
        handler.postDelayed(() -> cleanArtifactPage(gen, tries - 1), 800);
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
                if (isArtifactPage(url)) startCleaning();
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

    // ---------- Bar sistem ----------

    private boolean effectiveDarkIcons() {
        switch (opts.icons) {
            case ViewerOptions.ICONS_DARK:
                return true;
            case ViewerOptions.ICONS_LIGHT:
                return false;
            default:
                return autoDarkIcons;
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
        boolean dark = effectiveDarkIcons();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(!edge);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? light : 0, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (edge) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            if (dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
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
        if (webView == null || customView != null) return;
        webView.evaluateJavascript(JS_TOP_COLOR, value -> {
            if (value == null) return;
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
