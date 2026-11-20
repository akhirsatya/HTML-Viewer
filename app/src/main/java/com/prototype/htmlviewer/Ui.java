package com.prototype.htmlviewer;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Palet warna, font, dan komponen UI bersama (gaya square, hitam).
 * Font opsional di app/src/main/res/font/: gajraj_one.ttf dan dm_sans.ttf.
 */
final class Ui {

    static final int ICON_FILE = 0;
    static final int ICON_SLIDERS = 1;
    static final int ICON_GEAR = 2;
    static final int ICON_CHEVRON = 3;
    static final int ICON_BACK = 4;
    static final int ICON_CHECK = 5;

    final Activity a;
    final boolean dark;
    final int bg, text, sub, line, surf, chip, acc, tagBg, tagFg, prim, onPrim, iconBox, sheet, danger, onDanger, dim;
    final Typeface body, brand;

    Ui(Activity a) {
        this.a = a;
        int theme = AppPrefs.theme(a);
        boolean night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = theme == AppPrefs.THEME_DARK || (theme == AppPrefs.THEME_SYSTEM && night);
        if (dark) {
            bg = 0xFF141716; text = 0xFFECF0ED; sub = 0xFF9AA79F; line = 0xFF2C3330;
            surf = 0xFF1E2321; chip = 0xFF262C29; acc = 0xFF8FB8A0; tagBg = 0xFF26332C; tagFg = 0xFF9CC7AC;
            prim = 0xFFF2F2F2; onPrim = 0xFF1B1410; iconBox = 0xFF141716; sheet = 0xFF1A1E1C;
            danger = 0xFFE57368; onDanger = 0xFF1B1410; dim = 0x8C000000;
        } else {
            bg = 0xFFFFFFFF; text = 0xFF1B2420; sub = 0xFF5E6B63; line = 0xFFDDE5DE;
            surf = 0xFFF2F2F2; chip = 0xFFF7F7F7; acc = 0xFF3F6B55; tagBg = 0xFFE5EDE6; tagFg = 0xFF3F6B55;
            prim = 0xFF2A1D19; onPrim = 0xFFFFFFFF; iconBox = 0xFFFFFFFF; sheet = 0xFFFFFFFF;
            danger = 0xFFB3261E; onDanger = 0xFFFFFFFF; dim = 0x61141414;
        }
        body = loadFont("dm_sans", Typeface.DEFAULT);
        brand = loadFont("gajraj_one", Typeface.DEFAULT_BOLD);
    }

    private Typeface loadFont(String name, Typeface fallback) {
        try {
            int id = a.getResources().getIdentifier(name, "font", a.getPackageName());
            if (id != 0) {
                Typeface t = a.getResources().getFont(id);
                if (t != null) return t;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // ---------- Helper dasar ----------

    int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics());
    }

    /** Kotak bersudut tajam, dengan garis tepi opsional. */
    GradientDrawable rect(int fill, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(Math.max(1, Math.round(strokeDp * a.getResources().getDisplayMetrics().density)), stroke);
        }
        return g;
    }

    GradientDrawable pill(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(body, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    TextView brandText(String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(brand);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    /** Warna status bar dan navigation bar mengikuti latar app. */
    @SuppressWarnings("deprecation")
    void styleBars() {
        Window w = a.getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.setBackgroundDrawable(new ColorDrawable(bg));
        View d = w.getDecorView(); // paksa decor dibuat dulu
        int f = d.getSystemUiVisibility();
        int m = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(dark ? (f & ~m) : (f | m));
    }

    // ---------- Ikon gambar sendiri ----------

    class IconView extends View {
        private final int kind, color, fillBg;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconView(int kind, int color, int fillBg) {
            super(a);
            this.kind = kind;
            this.color = color;
            this.fillBg = fillBg;
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 24f, getHeight() / 24f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.7f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            Path path = new Path();
            switch (kind) {
                case ICON_FILE:
                    path.moveTo(14, 3); path.lineTo(6, 3); path.lineTo(6, 21); path.lineTo(18, 21); path.lineTo(18, 7); path.close();
                    c.drawPath(path, p);
                    path = new Path();
                    path.moveTo(14, 3); path.lineTo(14, 7); path.lineTo(18, 7);
                    c.drawPath(path, p);
                    path = new Path();
                    path.moveTo(10, 13); path.lineTo(8, 15); path.lineTo(10, 17);
                    path.moveTo(14, 13); path.lineTo(16, 15); path.lineTo(14, 17);
                    c.drawPath(path, p);
                    break;
                case ICON_SLIDERS:
                    c.drawLine(4, 7, 20, 7, p);
                    c.drawLine(4, 17, 20, 17, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(fillBg);
                    c.drawCircle(9, 7, 2.3f, p);
                    c.drawCircle(15, 17, 2.3f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(color);
                    c.drawCircle(9, 7, 2.3f, p);
                    c.drawCircle(15, 17, 2.3f, p);
                    break;
                case ICON_GEAR:
                    c.drawCircle(12, 12, 3f, p);
                    c.drawCircle(12, 12, 7f, p);
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI / 4 * i;
                        float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
                        c.drawLine(12 + cx * 7f, 12 + cy * 7f, 12 + cx * 9.8f, 12 + cy * 9.8f, p);
                    }
                    break;
                case ICON_CHEVRON:
                    path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                    c.drawPath(path, p);
                    break;
                case ICON_BACK:
                    path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19);
                    c.drawPath(path, p);
                    break;
                default: // ICON_CHECK
                    p.setStrokeWidth(2.2f);
                    path.moveTo(5, 12.5f); path.lineTo(9.5f, 17); path.lineTo(19, 7.5f);
                    c.drawPath(path, p);
            }
            c.restore();
        }
    }

    /** Siluet HP kecil yang menggambarkan mode tampilan (0 edge-to-edge, 1 penuh, 2 aman). */
    class PhoneIcon extends View {
        private final int kind;
        private boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        PhoneIcon(int kind, boolean on) {
            super(a);
            this.kind = kind;
            this.on = on;
        }

        void setOn(boolean v) {
            on = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int ink = on ? prim : sub;
            c.save();
            c.scale(getWidth() / 44f, getHeight() / 72f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(ink);
            c.drawRect(5, 1.5f, 39, 70.5f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(acc);
            p.setAlpha(90);
            if (kind == 2) c.drawRect(8, 15, 36, 57, p);
            else c.drawRect(8, 4, 36, 68, p);
            p.setAlpha(255);
            if (kind != 1) {
                p.setColor(ink);
                c.drawRect(14, 8, 30, 10, p);
                c.drawRect(16, 62, 28, 64, p);
            }
            c.restore();
        }
    }

    /** Kotak persegi berisi ikon di tengah. */
    FrameLayout iconBox(int kind, int sizeDp, int boxDp, int boxColor, int iconColor, int iconBg) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(rect(boxColor, 0, 0));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        p.gravity = Gravity.CENTER;
        f.addView(new IconView(kind, iconColor, iconBg), p);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(boxDp), dp(boxDp)));
        return f;
    }

    // ---------- Dialog ----------

    Dialog newDialog() {
        return new Dialog(a, android.R.style.Theme_Material_Light_Dialog_NoActionBar);
    }

    void showBottom(Dialog d, View content) {
        d.setContentView(content);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
    }

    /** Dialog konfirmasi bersudut tajam di tengah layar; nama file di isi pesan ditebalkan. */
    void confirm(String title, String message, String boldPart, String cancelLabel, String okLabel, final Runnable onOk) {
        final Dialog d = newDialog();
        FrameLayout holder = new FrameLayout(a);
        holder.setPadding(dp(28), 0, dp(28), 0);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(20));
        box.setBackground(rect(sheet, line, 1));

        box.addView(text(title, 18, text, true));

        SpannableStringBuilder sb = new SpannableStringBuilder(message);
        int i = boldPart == null ? -1 : message.indexOf(boldPart);
        if (i >= 0) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), i, i + boldPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(text), i, i + boldPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView msg = text("", 14, sub, false);
        msg.setText(sb);
        msg.setLineSpacing(0, 1.15f);
        box.addView(msg, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = text(cancelLabel, 15, text, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(rect(Color.TRANSPARENT, line, 1));
        cancel.setOnClickListener(v -> d.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1f));
        TextView ok = text(okLabel, 15, onDanger, true);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(rect(danger, 0, 0));
        ok.setOnClickListener(v -> {
            d.dismiss();
            onOk.run();
        });
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        okp.leftMargin = dp(8);
        buttons.addView(ok, okp);
        box.addView(buttons, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 20, 0, 0));

        holder.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(holder);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }
}
