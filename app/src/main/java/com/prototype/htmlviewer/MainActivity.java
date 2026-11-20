package com.prototype.htmlviewer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Home: judul, daftar file terakhir, dan bar sticky di bawah untuk memilih file HTML.
 */
public class MainActivity extends BaseActivity {

    private static final int REQ_PICK = 1;
    private static final int MAX_RECENT = 20;
    static final String PREFS = "recent";
    static final String KEY_LIST = "list";

    private Ui u;
    private String appliedKey;
    private LinearLayout recentContainer;
    private TextView recentCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        u = new Ui(this);
        appliedKey = AppPrefs.configKey(this);
        u.styleBars();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tema atau bahasa berubah di Pengaturan: bangun ulang layar ini
        if (!AppPrefs.configKey(this).equals(appliedKey)) {
            recreate();
            return;
        }
        refreshRecent();
    }

    private String[] modeNames() {
        return new String[]{getString(R.string.mode_edge), getString(R.string.mode_full), getString(R.string.mode_safe)};
    }

    private String[] modeDescs() {
        return new String[]{getString(R.string.mode_edge_desc), getString(R.string.mode_full_desc), getString(R.string.mode_safe_desc)};
    }

    // ---------- Home ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(u.bg);

        // Bagian atas: judul + tombol pengaturan + subjudul + label daftar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(u.dp(20), u.dp(12), u.dp(20), 0);

        FrameLayout header = new FrameLayout(this);
        TextView brand = u.brandText(getString(R.string.app_name), 34, u.text);
        header.addView(brand, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        FrameLayout gear = u.iconBox(Ui.ICON_GEAR, 22, 44, 0x00000000, u.text, u.bg);
        gear.setContentDescription(getString(R.string.open_settings_cd));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(gear, new FrameLayout.LayoutParams(u.dp(44), u.dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        top.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(52)));

        TextView sub = u.text(getString(R.string.home_tagline), 14, u.sub, false);
        sub.setGravity(Gravity.CENTER);
        top.addView(sub, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 2, 0, 0));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.addView(u.text(getString(R.string.recent_title), 13, u.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        recentCount = u.text("", 12, u.sub, false);
        labelRow.addView(recentCount);
        top.addView(labelRow, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 28, 4, 10));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.VERTICAL);
        recentContainer.setPadding(u.dp(20), 0, u.dp(20), u.dp(16));
        scroll.addView(recentContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bar sticky di bawah: pilih file HTML
        View divider = new View(this);
        divider.setBackgroundColor(u.line);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, u.dp(1))));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(u.dp(20), u.dp(16), u.dp(20), u.dp(20));
        bar.setBackgroundColor(u.surf);

        LinearLayout barTop = new LinearLayout(this);
        barTop.setOrientation(LinearLayout.HORIZONTAL);
        barTop.setGravity(Gravity.CENTER_VERTICAL);
        barTop.addView(u.iconBox(Ui.ICON_FILE, 24, 48, u.iconBox, u.acc, u.iconBox));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(u.text(getString(R.string.pick_title), 16, u.text, true));
        col.addView(u.text(getString(R.string.pick_desc), 13, u.sub, false));
        barTop.addView(col, u.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) col.getLayoutParams()).weight = 1f;
        bar.addView(barTop);

        TextView btn = u.text(getString(R.string.pick_button), 15, u.onPrim, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(u.rect(u.prim, 0, 0));
        btn.setOnClickListener(v -> openPicker());
        bar.addView(btn, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(50), 0, 14, 0, 0));
        root.addView(bar);

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
            showOptionsSheet(uri, displayName(this, uri), true);
        }
    }

    private void openViewer(Uri uri, ViewerOptions o) {
        Intent v = new Intent(this, ViewerActivity.class);
        v.setData(uri);
        v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        o.putInto(v);
        startActivity(v);
    }

    // ---------- Panel pengaturan tampilan ----------

    private void showOptionsSheet(final Uri uri, final String name, boolean fresh) {
        final ViewerOptions o = ViewerOptions.load(this);
        final Dialog d = u.newDialog();
        final String[] names = modeNames();
        final String[] descs = modeDescs();

        ScrollView sv = new ScrollView(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(u.dp(20), u.dp(12), u.dp(20), u.dp(24));
        sheet.setBackground(u.rect(u.sheet, u.line, 1));

        View handle = new View(this);
        handle.setBackgroundColor(u.line);
        LinearLayout.LayoutParams hp = u.lp(u.dp(36), u.dp(4), 0, 0, 0, 16);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        TextView title = u.text(name, 18, u.text, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sheet.addView(title);
        TextView sub = u.text(getString(fresh ? R.string.sheet_title_new : R.string.sheet_title_edit), 13, u.sub, false);
        sub.setGravity(Gravity.CENTER);
        sheet.addView(sub, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));

        // Tampilan: tiga kartu
        sheet.addView(sectionLabel(getString(R.string.section_display), 18));
        LinearLayout cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout[] cards = new LinearLayout[3];
        final Ui.PhoneIcon[] phones = new Ui.PhoneIcon[3];
        final TextView[] labels = new TextView[3];
        final TextView desc = u.text(descs[o.mode], 12, u.sub, false);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(u.dp(4), u.dp(14), u.dp(4), u.dp(12));
            phones[i] = u.new PhoneIcon(i, i == o.mode);
            card.addView(phones[i], new LinearLayout.LayoutParams(u.dp(34), u.dp(56)));
            labels[i] = u.text(names[i], 13, u.text, false);
            labels[i].setGravity(Gravity.CENTER);
            card.addView(labels[i], u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
            card.setOnClickListener(v -> {
                o.mode = idx;
                styleCards(cards, phones, labels, o.mode);
                desc.setText(descs[o.mode]);
            });
            cards[i] = card;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) cp.leftMargin = u.dp(8);
            cardsRow.addView(card, cp);
        }
        styleCards(cards, phones, labels, o.mode);
        sheet.addView(cardsRow);
        sheet.addView(desc, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, 8, 2, 0));

        sheet.addView(sectionLabel(getString(R.string.section_orientation), 20));
        sheet.addView(segmented(new String[]{getString(R.string.orient_auto), getString(R.string.orient_portrait),
                getString(R.string.orient_landscape)}, o.orient, i -> o.orient = i));

        sheet.addView(sectionLabel(getString(R.string.section_icons), 20));
        sheet.addView(segmented(new String[]{getString(R.string.icons_auto), getString(R.string.icons_dark),
                getString(R.string.icons_light)}, o.icons, i -> o.icons = i));

        sheet.addView(switchRow(getString(R.string.keep_screen_on), o.keepOn, c -> o.keepOn = c),
                u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        sheet.addView(switchRow(getString(R.string.pinch_zoom), o.zoom, c -> o.zoom = c),
                u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));

        TextView open = u.text(getString(fresh ? R.string.open_prototype : R.string.save_and_open), 16, u.onPrim, true);
        open.setGravity(Gravity.CENTER);
        open.setBackground(u.rect(u.prim, 0, 0));
        open.setOnClickListener(v -> {
            o.save(this);
            d.dismiss();
            openViewer(uri, o);
        });
        sheet.addView(open, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(54), 0, 22, 0, 0));

        sv.addView(sheet);
        u.showBottom(d, sv);
    }

    private void styleCards(LinearLayout[] cards, Ui.PhoneIcon[] phones, TextView[] labels, int selected) {
        for (int i = 0; i < cards.length; i++) {
            boolean on = i == selected;
            cards[i].setBackground(on ? u.rect(u.surf, u.prim, 1.5f) : u.rect(u.sheet, u.line, 1));
            phones[i].setOn(on);
            labels[i].setTypeface(u.body, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private TextView sectionLabel(String s, int topDp) {
        TextView t = u.text(s, 12, u.sub, true);
        t.setLayoutParams(u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, topDp, 0, 8));
        return t;
    }

    private LinearLayout segmented(String[] labels, int selected, final IntConsumer cb) {
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
                for (int j = 0; j < tvs.length; j++) styleSegment(tvs[j], j == idx);
                cb.accept(idx);
            });
            tvs[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int j = 0; j < tvs.length; j++) styleSegment(tvs[j], j == selected);
        return seg;
    }

    private void styleSegment(TextView t, boolean sel) {
        t.setBackground(sel ? u.rect(u.prim, 0, 0) : null);
        t.setTextColor(sel ? u.onPrim : u.text);
        t.setTypeface(u.body, sel ? Typeface.BOLD : Typeface.NORMAL);
    }

    private LinearLayout switchRow(String title, boolean checked, final Consumer<Boolean> cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(u.dp(2), u.dp(6), u.dp(2), u.dp(6));
        row.addView(u.text(title, 15, u.text, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final boolean[] state = {checked};
        final FrameLayout track = new FrameLayout(this);
        final View thumb = new View(this);
        track.addView(thumb);
        Runnable update = () -> {
            track.setBackground(u.rect(state[0] ? u.prim : u.line, 0, 0));
            thumb.setBackgroundColor(state[0] ? u.onPrim : u.sheet);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(u.dp(26), u.dp(22));
            p.gravity = Gravity.CENTER_VERTICAL | (state[0] ? Gravity.END : Gravity.START);
            p.setMargins(u.dp(3), 0, u.dp(3), 0);
            thumb.setLayoutParams(p);
        };
        update.run();
        track.setOnClickListener(v -> {
            state[0] = !state[0];
            update.run();
            cb.accept(state[0]);
        });
        row.addView(track, new LinearLayout.LayoutParams(u.dp(52), u.dp(28)));
        return row;
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

    static void addRecent(Activity a, Uri uri, String name, int mode) {
        try {
            SharedPreferences p = a.getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(KEY_LIST, "[]"));
            JSONArray fresh = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("uri", uri.toString());
            item.put("name", name);
            item.put("time", System.currentTimeMillis());
            item.put("mode", mode);
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

    private String relTime(long time) {
        if (time <= 0) return "";
        long diff = Math.max(0, System.currentTimeMillis() - time);
        long min = diff / 60000;
        if (min < 1) return getString(R.string.time_just_now);
        if (min < 60) return getResources().getQuantityString(R.plurals.time_minutes, (int) min, (int) min);
        long hours = min / 60;
        if (hours < 24) return getResources().getQuantityString(R.plurals.time_hours, (int) hours, (int) hours);
        long days = hours / 24;
        if (days < 2) return getString(R.string.time_yesterday);
        if (days < 7) return getResources().getQuantityString(R.plurals.time_days, (int) days, (int) days);
        return DateFormat.getDateInstance(DateFormat.MEDIUM, getResources().getConfiguration().getLocales().get(0))
                .format(new Date(time));
    }

    private void refreshRecent() {
        recentContainer.removeAllViews();
        JSONArray list;
        try {
            list = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LIST, "[]"));
        } catch (Exception e) {
            list = new JSONArray();
        }
        recentCount.setText(list.length() == 0 ? ""
                : getResources().getQuantityString(R.plurals.recent_count, list.length(), list.length()));
        if (list.length() == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(u.dp(24), u.dp(40), u.dp(24), u.dp(40));
            android.graphics.drawable.GradientDrawable dash = new android.graphics.drawable.GradientDrawable();
            dash.setColor(0x00000000);
            dash.setStroke(u.dp(1), u.line, u.dp(5), u.dp(4));
            empty.setBackground(dash);
            empty.addView(u.iconBox(Ui.ICON_FILE, 26, 52, u.chip, u.acc, u.chip));
            TextView t1 = u.text(getString(R.string.empty_title), 15, u.text, true);
            t1.setGravity(Gravity.CENTER);
            empty.addView(t1, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
            TextView t2 = u.text(getString(R.string.empty_desc), 13, u.sub, false);
            t2.setGravity(Gravity.CENTER);
            empty.addView(t2, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0));
            recentContainer.addView(empty);
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject o = list.getJSONObject(i);
                recentContainer.addView(makeRow(o.getString("uri"), o.optString("name", "file.html"),
                        o.optLong("time", 0), o.optInt("mode", -1)));
            } catch (Exception ignored) {
            }
        }
    }

    private View makeRow(final String uri, final String name, long time, int mode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(u.dp(12), u.dp(12), u.dp(6), u.dp(12));
        row.setBackground(u.rect(u.bg, u.line, 1));

        row.addView(u.iconBox(Ui.ICON_FILE, 22, 44, u.chip, u.acc, u.chip));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView n = u.text(name, 15, u.text, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(n);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(u.text(relTime(time), 12, u.sub, false));
        String[] names = modeNames();
        if (mode >= 0 && mode < names.length) {
            TextView tag = u.text(names[mode], 11, u.tagFg, true);
            tag.setBackground(u.pill(u.tagBg, 8));
            tag.setPadding(u.dp(8), u.dp(2), u.dp(8), u.dp(2));
            meta.addView(tag, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 0, 0));
        }
        col.addView(meta, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 3, 0, 0));
        row.addView(col, u.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 12, 0, 6, 0));
        ((LinearLayout.LayoutParams) col.getLayoutParams()).weight = 1f;

        // Tombol pengaturan tampilan untuk file ini
        FrameLayout slid = u.iconBox(Ui.ICON_SLIDERS, 20, 44, 0x00000000, u.sub, u.bg);
        slid.setContentDescription(getString(R.string.display_settings_for, name));
        slid.setOnClickListener(v -> showOptionsSheet(Uri.parse(uri), name, false));
        row.addView(slid);

        row.setOnClickListener(v -> openViewer(Uri.parse(uri), ViewerOptions.load(this)));
        row.setOnLongClickListener(v -> {
            u.confirm(getString(R.string.delete_title), getString(R.string.delete_body, name), name,
                    getString(R.string.cancel), getString(R.string.remove), () -> {
                        removeRecent(uri);
                        Toast.makeText(this, R.string.removed_toast, Toast.LENGTH_SHORT).show();
                    });
            return true;
        });

        row.setLayoutParams(u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));
        return row;
    }
}
