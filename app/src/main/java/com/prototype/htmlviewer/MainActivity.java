package com.prototype.htmlviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Layar utama: tombol untuk memilih file HTML + daftar file yang terakhir dibuka.
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int MAX_RECENT = 20;
    static final String PREFS = "recent";
    static final String KEY_LIST = "list";

    private LinearLayout recentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecent();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F1EA"));
        root.setPadding(dp(20), dp(28), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Prototype Viewer");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#1F1E1D"));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Buka file HTML dalam mode layar penuh");
        sub.setTextSize(14);
        sub.setTextColor(Color.parseColor("#6B6862"));
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);

        Button pick = new Button(this);
        pick.setText("Pilih file HTML");
        pick.setAllCaps(false);
        pick.setTextSize(16);
        pick.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#D97757"));
        bg.setCornerRadius(dp(14));
        pick.setBackground(bg);
        pick.setOnClickListener(v -> openPicker());
        root.addView(pick, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        TextView recentLabel = new TextView(this);
        recentLabel.setText("Terakhir dibuka");
        recentLabel.setTextSize(13);
        recentLabel.setTypeface(Typeface.DEFAULT_BOLD);
        recentLabel.setTextColor(Color.parseColor("#6B6862"));
        recentLabel.setPadding(dp(4), dp(28), 0, dp(8));
        root.addView(recentLabel);

        ScrollView scroll = new ScrollView(this);
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(recentContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Tips: di dalam viewer, tekan Kembali untuk keluar. Ketuk lama item di daftar untuk menghapusnya.");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#8A867F"));
        hint.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(hint);

        setContentView(root);
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/html", "application/xhtml+xml", "text/plain", "application/octet-stream"});
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            openViewer(uri);
        }
    }

    private void openViewer(Uri uri) {
        Intent v = new Intent(this, ViewerActivity.class);
        v.setData(uri);
        v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(v);
    }

    // ---------- Daftar file terakhir ----------

    static String displayName(Activity a, Uri uri) {
        String name = null;
        try (Cursor c = a.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        }
        if (name == null) name = uri.getLastPathSegment();
        return name == null ? "file.html" : name;
    }

    static void addRecent(Activity a, Uri uri, String name) {
        try {
            SharedPreferences p = a.getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(KEY_LIST, "[]"));
            JSONArray fresh = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("uri", uri.toString());
            item.put("name", name);
            item.put("time", System.currentTimeMillis());
            fresh.put(item);
            for (int i = 0; i < old.length() && fresh.length() < MAX_RECENT; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!o.getString("uri").equals(uri.toString())) fresh.put(o);
            }
            p.edit().putString(KEY_LIST, fresh.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void removeRecent(String uri) {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(KEY_LIST, "[]"));
            JSONArray fresh = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (!o.getString("uri").equals(uri)) fresh.put(o);
            }
            p.edit().putString(KEY_LIST, fresh.toString()).apply();
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        refreshRecent();
    }

    private void refreshRecent() {
        recentContainer.removeAllViews();
        JSONArray list;
        try {
            list = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LIST, "[]"));
        } catch (Exception e) {
            list = new JSONArray();
        }
        if (list.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada file. Pilih file HTML, atau buka file .html dari aplikasi lain lewat \"Buka dengan → Prototype Viewer\".");
            empty.setTextSize(14);
            empty.setTextColor(Color.parseColor("#8A867F"));
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            recentContainer.addView(empty);
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject o = list.getJSONObject(i);
                final String uri = o.getString("uri");
                final String name = o.optString("name", "file.html");
                long time = o.optLong("time", 0);
                recentContainer.addView(makeRow(uri, name, time));
            } catch (Exception ignored) {
            }
        }
    }

    private View makeRow(String uri, String name, long time) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(15);
        n.setTextColor(Color.parseColor("#1F1E1D"));
        n.setSingleLine(true);
        row.addView(n);

        TextView t = new TextView(this);
        t.setText(time > 0 ? DateUtils.getRelativeTimeSpanString(time).toString() : "");
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#8A867F"));
        row.addView(t);

        row.setOnClickListener(v -> openViewer(Uri.parse(uri)));
        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage("Hapus dari daftar?")
                    .setPositiveButton("Hapus", (d, w) -> {
                        removeRecent(uri);
                        Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
}
