package com.prototype.htmlviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
 * Menampilkan file HTML di WebView dalam mode immersive fullscreen
 * (status bar & navigation bar disembunyikan; geser dari tepi untuk memunculkan sementara).
 */
public class ViewerActivity extends Activity {

    private static final int REQ_FILE_CHOOSER = 2;
    // Base URL "https" supaya localStorage, fetch ke CDN, dll. berjalan normal.
    private static final String BASE_URL = "https://prototype.local/";

    private WebView webView;
    private FrameLayout root;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // Ikon status bar gelap (untuk latar terang) atau putih (untuk latar gelap)
    private boolean darkIcons = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable colorPoller = new Runnable() {
        @Override
        public void run() {
            detectTopColor();
            handler.postDelayed(this, 1500);
        }
    };

    // Ambil warna latar elemen paling atas halaman (di bawah status bar)
    private static final String JS_TOP_COLOR =
            "(function(){function bg(e){if(!e)return null;var c=getComputedStyle(e).backgroundColor;"
          + "return (c&&c!=='transparent'&&!/rgba\\(.*,\\s*0\\)$/.test(c))?c:null;}"
          + "var el=document.elementFromPoint(window.innerWidth/2,4),c=null;"
          + "while(el&&!c){c=bg(el);el=el.parentElement;}"
          + "return c||bg(document.body)||bg(document.documentElement)||'rgb(255,255,255)';})()";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        WebView.setWebContentsDebuggingEnabled(true); // bisa di-inspect dari chrome://inspect

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                detectTopColor();
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
                hideSystemBars();
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
                applyEdgeToEdge();
            }
        });

        applyEdgeToEdge();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadFromIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        loadFromIntent(intent);
    }

    private void loadFromIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && text.contains("<")) {
                webView.loadDataWithBaseURL(BASE_URL, text, "text/html", "UTF-8", null);
                return;
            }
            Toast.makeText(this, "Tidak ada file untuk dibuka", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if ("file".equals(uri.getScheme())) {
            // file:// bisa dimuat langsung, sehingga file CSS/JS/gambar relatif ikut terbaca
            webView.loadUrl(uri.toString());
            MainActivity.addRecent(this, uri, uri.getLastPathSegment());
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
            MainActivity.addRecent(this, uri, MainActivity.displayName(this, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ---------- Edge-to-edge (bar sistem transparan) ----------

    /** Konten digambar sampai ke belakang status bar & navigation bar, bar tetap terlihat transparan. */
    @SuppressWarnings("deprecation")
    private void applyEdgeToEdge() {
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
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(darkIcons ? light : 0, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (darkIcons) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Hanya dipakai saat video / elemen masuk fullscreen penuh. */
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
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

    /** Cek warna bagian atas halaman, lalu sesuaikan warna ikon status bar. */
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
                double lum = (0.299 * r + 0.587 * g + 0.114 * bl) / 255.0;
                boolean wantDark = lum > 0.6;
                if (wantDark != darkIcons) {
                    darkIcons = wantDark;
                    applyEdgeToEdge();
                }
            } catch (NumberFormatException ignored) {
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (customView != null) hideSystemBars(); else applyEdgeToEdge();
        }
    }

    // ---------- Lifecycle ----------

    @Override
    public void onBackPressed() {
        if (customView != null) {
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
        if (customView != null) hideSystemBars(); else applyEdgeToEdge();
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
