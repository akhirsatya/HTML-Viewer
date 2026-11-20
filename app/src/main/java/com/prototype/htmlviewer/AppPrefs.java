package com.prototype.htmlviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Pengaturan app: tema (ikut sistem / terang / gelap) dan bahasa (ikut sistem / Indonesia / Inggris).
 * Default keduanya: ikut sistem.
 */
final class AppPrefs {

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    static final String LANG_SYSTEM = "system";
    static final String LANG_ID = "id";
    static final String LANG_EN = "en";

    private static final String PREFS = "app_settings";
    private static final String K_THEME = "theme";
    private static final String K_LANG = "lang";

    private AppPrefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int theme(Context c) {
        return p(c).getInt(K_THEME, THEME_SYSTEM);
    }

    static void setTheme(Context c, int v) {
        p(c).edit().putInt(K_THEME, v).apply();
    }

    static String lang(Context c) {
        return p(c).getString(K_LANG, LANG_SYSTEM);
    }

    static void setLang(Context c, String v) {
        p(c).edit().putString(K_LANG, v).apply();
    }

    /** Berubah kalau tema atau bahasa diganti; dipakai layar lain untuk tahu kapan harus dibuat ulang. */
    static String configKey(Context c) {
        return theme(c) + "|" + lang(c);
    }

    /** Terapkan bahasa pilihan user pada Context sebuah Activity. */
    static Context wrap(Context base) {
        String l = lang(base);
        if (LANG_SYSTEM.equals(l)) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(new Locale(l));
        return base.createConfigurationContext(cfg);
    }
}
