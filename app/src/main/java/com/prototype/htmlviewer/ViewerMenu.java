package com.prototype.htmlviewer;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Panel yang muncul saat layar diketuk 3 jari: pengaturan prototype + tombol tutup. */
final class ViewerMenu {

    private final Activity a;
    private final Ui u;
    private final ViewerOptions o;
    private final Runnable onChange;
    private final Runnable onClose;

    ViewerMenu(Activity a, ViewerOptions o, Runnable onChange, Runnable onClose) {
        this.a = a;
        this.u = new Ui(a);
        this.o = o;
        this.onChange = onChange;
        this.onClose = onClose;
    }

    void show(final Runnable onDismiss) {
        final Dialog d = u.newDialog();
        final String[] names = {a.getString(R.string.mode_edge), a.getString(R.string.mode_full), a.getString(R.string.mode_safe)};
        final String[] descs = {a.getString(R.string.mode_edge_desc), a.getString(R.string.mode_full_desc), a.getString(R.string.mode_safe_desc)};

        ScrollView sv = new ScrollView(a);
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(u.dp(20), u.dp(12), u.dp(20), u.dp(24));
        sheet.setBackground(u.rect(u.sheet, u.line, 1));

        View handle = new View(a);
        handle.setBackgroundColor(u.line);
        LinearLayout.LayoutParams hp = u.lp(u.dp(36), u.dp(4), 0, 0, 0, 16);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        TextView title = u.text(a.getString(R.string.proto_menu_title), 18, u.text, true);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title);
        TextView sub = u.text(a.getString(R.string.proto_menu_sub), 13, u.sub, false);
        sub.setGravity(Gravity.CENTER);
        sheet.addView(sub, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));

        // Tampilan
        sheet.addView(label(a.getString(R.string.section_display), 18));
        LinearLayout cardsRow = new LinearLayout(a);
        final LinearLayout[] cards = new LinearLayout[3];
        final Ui.PhoneIcon[] phones = new Ui.PhoneIcon[3];
        final TextView[] labels = new TextView[3];
        final TextView desc = u.text(descs[o.mode], 12, u.sub, false);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(a);
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
                styleCards(cards, phones, labels);
                desc.setText(descs[o.mode]);
                onChange.run();
            });
            cards[i] = card;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) cp.leftMargin = u.dp(8);
            cardsRow.addView(card, cp);
        }
        styleCards(cards, phones, labels);
        sheet.addView(cardsRow);
        sheet.addView(desc, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, 8, 2, 0));

        sheet.addView(label(a.getString(R.string.section_orientation), 20));
        sheet.addView(segmented(new String[]{a.getString(R.string.orient_auto), a.getString(R.string.orient_portrait),
                a.getString(R.string.orient_landscape)}, o.orient, i -> {
            o.orient = i;
            onChange.run();
        }));

        sheet.addView(label(a.getString(R.string.section_icons), 20));
        sheet.addView(segmented(new String[]{a.getString(R.string.icons_auto), a.getString(R.string.icons_dark),
                a.getString(R.string.icons_light)}, o.icons, i -> {
            o.icons = i;
            onChange.run();
        }));

        sheet.addView(switchRow(a.getString(R.string.keep_screen_on), o.keepOn, c -> {
            o.keepOn = c;
            onChange.run();
        }), u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        sheet.addView(switchRow(a.getString(R.string.pinch_zoom), o.zoom, c -> {
            o.zoom = c;
            onChange.run();
        }), u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));

        // Tombol
        TextView resume = u.text(a.getString(R.string.menu_resume), 16, u.onPrim, true);
        resume.setGravity(Gravity.CENTER);
        resume.setBackground(u.rect(u.prim, 0, 0));
        resume.setOnClickListener(v -> d.dismiss());
        sheet.addView(resume, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(54), 0, 22, 0, 0));

        TextView close = u.text(a.getString(R.string.close_prototype), 16, u.danger, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(u.rect(0x00000000, u.danger, 1));
        close.setOnClickListener(v -> {
            d.dismiss();
            onClose.run();
        });
        sheet.addView(close, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(54), 0, 8, 0, 0));

        sv.addView(sheet);
        d.setOnDismissListener(x -> {
            if (onDismiss != null) onDismiss.run();
        });
        u.showBottom(d, sv);
    }

    private TextView label(String s, int topDp) {
        TextView t = u.text(s, 12, u.sub, true);
        t.setLayoutParams(u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, topDp, 0, 8));
        return t;
    }

    private void styleCards(LinearLayout[] cards, Ui.PhoneIcon[] phones, TextView[] labels) {
        for (int i = 0; i < cards.length; i++) {
            boolean on = i == o.mode;
            cards[i].setBackground(on ? u.rect(u.surf, u.prim, 1.5f) : u.rect(u.sheet, u.line, 1));
            phones[i].setOn(on);
            labels[i].setTypeface(u.body, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private LinearLayout segmented(String[] labels, int selected, final IntConsumer cb) {
        LinearLayout seg = new LinearLayout(a);
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
                for (int j = 0; j < tvs.length; j++) style(tvs[j], j == idx);
                cb.accept(idx);
            });
            tvs[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int j = 0; j < tvs.length; j++) style(tvs[j], j == selected);
        return seg;
    }

    private void style(TextView t, boolean sel) {
        t.setBackground(sel ? u.rect(u.prim, 0, 0) : null);
        t.setTextColor(sel ? u.onPrim : u.text);
        t.setTypeface(u.body, sel ? Typeface.BOLD : Typeface.NORMAL);
    }

    private LinearLayout switchRow(String title, boolean checked, final Consumer<Boolean> cb) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(u.dp(2), u.dp(6), u.dp(2), u.dp(6));
        row.addView(u.text(title, 15, u.text, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final boolean[] state = {checked};
        final FrameLayout track = new FrameLayout(a);
        final View thumb = new View(a);
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
}
