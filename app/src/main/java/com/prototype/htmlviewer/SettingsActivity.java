package com.prototype.htmlviewer;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private Ui u;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        u = new Ui(this);
        u.styleBars();
        build();
    }

    private String languageValue() {
        String l = AppPrefs.lang(this);
        if (AppPrefs.LANG_ID.equals(l)) return getString(R.string.lang_id);
        if (AppPrefs.LANG_EN.equals(l)) return getString(R.string.lang_en);
        String sys = Locale.getDefault().getDisplayLanguage(Locale.getDefault());
        if (sys.length() > 0) sys = sys.substring(0, 1).toUpperCase(Locale.getDefault()) + sys.substring(1);
        return getString(R.string.language_system_value, sys);
    }

    private String versionText() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(u.bg);

        root.addView(topBar(getString(R.string.settings_title)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(u.dp(20), u.dp(8), u.dp(20), u.dp(24));

        // Tampilan
        body.addView(groupLabel(getString(R.string.settings_appearance)));
        LinearLayout g1 = group();
        // Tema
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.VERTICAL);
        themeRow.setPadding(u.dp(16), u.dp(14), u.dp(16), u.dp(14));
        themeRow.addView(u.text(getString(R.string.theme_label), 15, u.text, true));
        final int[] values = {AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK, AppPrefs.THEME_SYSTEM};
        String[] names = {getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system)};
        int cur = AppPrefs.theme(this);
        int sel = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) sel = i;
        themeRow.addView(segmented(names, sel, values), u.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
        g1.addView(themeRow);
        g1.addView(divider());
        g1.addView(row(getString(R.string.language_label), languageValue(), v -> showLanguageSheet()));
        body.addView(g1);

        // Tentang
        body.addView(groupLabel(getString(R.string.settings_about)));
        LinearLayout g2 = group();
        g2.addView(row(getString(R.string.about_developer), null,
                v -> startActivity(new Intent(this, AboutActivity.class))));
        g2.addView(divider());
        g2.addView(row(getString(R.string.app_version), versionText(), null));
        body.addView(g2);

        // Footer
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = u.brandText(getString(R.string.app_name), 20, u.sub);
        b.setGravity(Gravity.CENTER);
        foot.addView(b);
        TextView t = u.text(getString(R.string.home_tagline), 12, u.sub, false);
        t.setGravity(Gravity.CENTER);
        foot.addView(t, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 2, 0, 0));
        body.addView(foot, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 32, 0, 0));

        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    // ---------- Komponen ----------

    private View topBar(String title) {
        FrameLayout bar = new FrameLayout(this);
        bar.setPadding(u.dp(8), u.dp(8), u.dp(8), u.dp(8));
        FrameLayout back = u.iconBox(Ui.ICON_BACK, 22, 44, 0x00000000, u.text, u.bg);
        back.setContentDescription(getString(R.string.back_cd));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(u.dp(44), u.dp(44), Gravity.START | Gravity.CENTER_VERTICAL));
        TextView t = u.text(title, 17, u.text, true);
        t.setGravity(Gravity.CENTER);
        bar.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return bar;
    }

    private TextView groupLabel(String s) {
        TextView t = u.text(s, 12, u.sub, true);
        t.setLayoutParams(u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, 22, 0, 8));
        return t;
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(u.rect(u.bg, u.line, 1));
        return g;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(u.line);
        d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, u.dp(1))));
        return d;
    }

    private View row(String title, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u.dp(16), u.dp(16), u.dp(12), u.dp(16));
        r.addView(u.text(title, 15, u.text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = u.text(value, 14, u.sub, false);
            v.setGravity(Gravity.END);
            r.addView(v, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 4, 0));
        }
        if (click != null) {
            r.addView(u.iconBox(Ui.ICON_CHEVRON, 18, 24, 0x00000000, u.sub, u.bg));
            r.setOnClickListener(click);
        } else {
            r.setPadding(u.dp(16), u.dp(16), u.dp(16), u.dp(16));
        }
        return r;
    }

    private LinearLayout segmented(String[] labels, int selected, final int[] values) {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(u.rect(u.surf, 0, 0));
        seg.setPadding(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
        final TextView[] tvs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = u.text(labels[i], 13, u.text, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, u.dp(11), 0, u.dp(11));
            t.setOnClickListener(v -> {
                if (AppPrefs.theme(this) == values[idx]) return;
                AppPrefs.setTheme(this, values[idx]);
                recreate();
            });
            tvs[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int j = 0; j < tvs.length; j++) {
            boolean s = j == selected;
            tvs[j].setBackground(s ? u.rect(u.prim, 0, 0) : null);
            tvs[j].setTextColor(s ? u.onPrim : u.text);
            tvs[j].setTypeface(u.body, s ? Typeface.BOLD : Typeface.NORMAL);
        }
        return seg;
    }

    // ---------- Bahasa ----------

    private void showLanguageSheet() {
        final Dialog d = u.newDialog();
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(u.dp(20), u.dp(12), u.dp(20), u.dp(24));
        sheet.setBackground(u.rect(u.sheet, u.line, 1));

        View handle = new View(this);
        handle.setBackgroundColor(u.line);
        LinearLayout.LayoutParams hp = u.lp(u.dp(36), u.dp(4), 0, 0, 0, 16);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        sheet.addView(u.text(getString(R.string.language_sheet_title), 18, u.text, true));
        sheet.addView(u.text(getString(R.string.language_sheet_sub), 13, u.sub, false),
                u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 14));

        String sys = Locale.getDefault().getDisplayLanguage(Locale.getDefault());
        if (sys.length() > 0) sys = sys.substring(0, 1).toUpperCase(Locale.getDefault()) + sys.substring(1);
        String cur = AppPrefs.lang(this);
        sheet.addView(langOption(d, getString(R.string.lang_follow_system), getString(R.string.lang_follow_system_sub, sys),
                AppPrefs.LANG_SYSTEM, cur));
        sheet.addView(langOption(d, getString(R.string.lang_id), getString(R.string.lang_id_sub), AppPrefs.LANG_ID, cur));
        sheet.addView(langOption(d, getString(R.string.lang_en), getString(R.string.lang_en_sub), AppPrefs.LANG_EN, cur));
        u.showBottom(d, sheet);
    }

    private View langOption(final Dialog d, String title, String sub, final String value, String current) {
        boolean on = value.equals(current);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u.dp(16), u.dp(14), u.dp(12), u.dp(14));
        r.setBackground(on ? u.rect(u.surf, u.prim, 1.5f) : u.rect(u.sheet, u.line, 1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(u.text(title, 15, u.text, true));
        col.addView(u.text(sub, 12, u.sub, false));
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (on) r.addView(u.iconBox(Ui.ICON_CHECK, 20, 28, 0x00000000, u.text, u.sheet));
        r.setOnClickListener(v -> {
            d.dismiss();
            if (!value.equals(AppPrefs.lang(this))) {
                AppPrefs.setLang(this, value);
                recreate();
            }
        });
        r.setLayoutParams(u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));
        return r;
    }
}
