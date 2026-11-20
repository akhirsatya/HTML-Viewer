package com.prototype.htmlviewer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Pengaturan tampilan viewer. Disimpan sebagai "terakhir dipakai" dan dikirim ke ViewerActivity lewat Intent.
 */
final class ViewerOptions {

    // Mode tampilan
    static final int MODE_EDGE = 0;       // konten sampai belakang bar sistem (transparan)
    static final int MODE_IMMERSIVE = 1;  // bar sistem disembunyikan
    static final int MODE_SAFE = 2;       // konten di antara bar sistem (tidak tertutup)

    // Orientasi
    static final int ORIENT_AUTO = 0;
    static final int ORIENT_PORTRAIT = 1;
    static final int ORIENT_LANDSCAPE = 2;

    // Warna ikon status bar
    static final int ICONS_AUTO = 0;
    static final int ICONS_DARK = 1;
    static final int ICONS_LIGHT = 2;

    private static final String PREFS = "viewer_options";
    private static final String K_MODE = "opt_mode";
    private static final String K_ORIENT = "opt_orient";
    private static final String K_ICONS = "opt_icons";
    private static final String K_KEEP = "opt_keepon";
    private static final String K_ZOOM = "opt_zoom";

    int mode = MODE_EDGE;
    int orient = ORIENT_AUTO;
    int icons = ICONS_AUTO;
    boolean keepOn = true;
    boolean zoom = false;

    static ViewerOptions load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ViewerOptions o = new ViewerOptions();
        o.mode = p.getInt(K_MODE, MODE_EDGE);
        o.orient = p.getInt(K_ORIENT, ORIENT_AUTO);
        o.icons = p.getInt(K_ICONS, ICONS_AUTO);
        o.keepOn = p.getBoolean(K_KEEP, true);
        o.zoom = p.getBoolean(K_ZOOM, false);
        return o;
    }

    void save(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(K_MODE, mode)
                .putInt(K_ORIENT, orient)
                .putInt(K_ICONS, icons)
                .putBoolean(K_KEEP, keepOn)
                .putBoolean(K_ZOOM, zoom)
                .apply();
    }

    void putInto(Intent i) {
        i.putExtra(K_MODE, mode);
        i.putExtra(K_ORIENT, orient);
        i.putExtra(K_ICONS, icons);
        i.putExtra(K_KEEP, keepOn);
        i.putExtra(K_ZOOM, zoom);
    }

    /** Pakai pengaturan dari Intent kalau ada; kalau tidak (mis. "Buka dengan"), pakai yang terakhir disimpan. */
    static ViewerOptions from(Intent i, Context c) {
        if (i != null && i.hasExtra(K_MODE)) {
            ViewerOptions o = new ViewerOptions();
            o.mode = i.getIntExtra(K_MODE, MODE_EDGE);
            o.orient = i.getIntExtra(K_ORIENT, ORIENT_AUTO);
            o.icons = i.getIntExtra(K_ICONS, ICONS_AUTO);
            o.keepOn = i.getBooleanExtra(K_KEEP, true);
            o.zoom = i.getBooleanExtra(K_ZOOM, false);
            return o;
        }
        return load(c);
    }
}
