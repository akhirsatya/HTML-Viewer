package com.prototype.htmlviewer;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends BaseActivity {

    private Ui u;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        u = new Ui(this);
        u.styleBars();
        build();
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

        FrameLayout bar = new FrameLayout(this);
        bar.setPadding(u.dp(8), u.dp(8), u.dp(8), u.dp(8));
        FrameLayout back = u.iconBox(Ui.ICON_BACK, 22, 44, 0x00000000, u.text, u.bg);
        back.setContentDescription(getString(R.string.back_cd));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(u.dp(44), u.dp(44), Gravity.START | Gravity.CENTER_VERTICAL));
        TextView title = u.text(getString(R.string.about_developer), 17, u.text, true);
        bar.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(u.dp(20), u.dp(16), u.dp(20), u.dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.developer_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setContentDescription(getString(R.string.logo_cd));
        body.addView(logo, new LinearLayout.LayoutParams(u.dp(96), u.dp(96)));

        TextView name = u.text("Akhir Satya", 22, u.text, true);
        name.setGravity(Gravity.CENTER);
        body.addView(name, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 16, 0, 0));
        TextView role = u.text(getString(R.string.about_role), 13, u.acc, true);
        role.setGravity(Gravity.CENTER);
        body.addView(role, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));
        TextView desc = u.text(getString(R.string.about_desc), 14, u.sub, false);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.25f);
        body.addView(desc, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 14, 4, 0));

        TextView label = u.text(getString(R.string.contact_label), 12, u.sub, true);
        body.addView(label, u.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, 28, 0, 8));

        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(u.rect(u.bg, u.line, 1));
        g.addView(contactRow("Email", getString(R.string.contact_email), v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.contact_email)));
            open(i);
        }));
        View d = new View(this);
        d.setBackgroundColor(u.line);
        g.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, u.dp(1))));
        g.addView(contactRow("GitHub", getString(R.string.contact_github), v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + getString(R.string.contact_github)));
            open(i);
        }));
        body.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView foot = u.text(getString(R.string.app_name) + " " + versionText(), 12, u.sub, false);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 32, 0, 0));

        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private View contactRow(String label, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u.dp(16), u.dp(16), u.dp(12), u.dp(16));
        r.addView(u.text(label, 15, u.text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = u.text(value, 14, u.sub, false);
        v.setGravity(Gravity.END);
        r.addView(v, u.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 4, 0));
        r.addView(u.iconBox(Ui.ICON_CHEVRON, 18, 24, 0x00000000, u.sub, u.bg));
        r.setOnClickListener(click);
        return r;
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_open_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}
