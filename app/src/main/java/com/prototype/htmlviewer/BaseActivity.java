package com.prototype.htmlviewer;

import android.app.Activity;
import android.content.Context;

/** Dasar semua layar: menerapkan bahasa pilihan user. */
public class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPrefs.wrap(base));
    }
}
