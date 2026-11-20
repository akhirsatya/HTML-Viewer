package com.prototype.htmlviewer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.PrintWriter;
import java.io.StringWriter;

public class BaseActivity extends Activity {

    private static boolean handlerSet = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPrefs.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installCrashLogger(getApplicationContext());
        super.onCreate(savedInstanceState);
    }

    /** Simpan jejak error terakhir supaya bisa ditampilkan saat app dibuka lagi. */
    private static synchronized void installCrashLogger(final Context c) {
        if (handlerSet) return;
        handlerSet = true;
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                SharedPreferences p = c.getSharedPreferences("crash", Context.MODE_PRIVATE);
                p.edit().putString("trace", sw.toString()).commit();
            } catch (Throwable ignored) {
            }
            if (old != null) old.uncaughtException(t, e);
        });
    }
}
