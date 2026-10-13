package com.wordlock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPickActivity extends Activity {
    static class App { String pkg, label; }
    final ArrayList<App> apps = new ArrayList<>();
    final HashMap<String, Drawable> icons = new HashMap<>();
    Set<String> sel; BaseAdapter adapter; TextView head; boolean single;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        single = getIntent().getBooleanExtra("single", false);
        sel = single ? new HashSet<String>() : Prefs.whitelist(this);
        LinearLayout root = U.col(this); root.setFitsSystemWindows(true); root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), 0);
        root.addView(U.tv(this, single ? "选择要限制的应用" : "白名单应用", 24, U.TXT, true));
        head = U.tv(this, "正在读取应用列表…", 13, U.GRAY, false);
        root.addView(head, U.m(U.full(), this, 0, 4, 0, 8));
        ListView lv = new ListView(this); lv.setDividerHeight(0);
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        adapter = new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int i) { return apps.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                App a = apps.get(i);
                LinearLayout r = U.row(AppPickActivity.this);
                r.setPadding(U.dp(AppPickActivity.this, 8), U.dp(AppPickActivity.this, 8), U.dp(AppPickActivity.this, 8), U.dp(AppPickActivity.this, 8));
                ImageView iv = new ImageView(AppPickActivity.this); iv.setImageDrawable(icon(a.pkg));
                r.addView(iv, U.lp(U.dp(AppPickActivity.this, 40), U.dp(AppPickActivity.this, 40)));
                r.addView(U.tv(AppPickActivity.this, a.label, 16, U.TXT, false), U.m(U.lpw(1), AppPickActivity.this, 12, 0, 0, 0));
                CheckBox cb = new CheckBox(AppPickActivity.this); cb.setChecked(sel.contains(a.pkg)); cb.setClickable(false); cb.setFocusable(false);
                if (!single) r.addView(cb);
                return r;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, i, id) -> {
            String pkg = apps.get(i).pkg;
            if (single) { setResult(RESULT_OK, new Intent().putExtra("pkg", pkg).putExtra("label", apps.get(i).label)); finish(); return; }
            if (!sel.remove(pkg)) sel.add(pkg);
            Prefs.setWhitelist(this, sel); updateHead(); adapter.notifyDataSetChanged();
        });
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
            HashSet<String> seen = new HashSet<>(); ArrayList<App> list = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App(); a.pkg = pkg; a.label = ri.loadLabel(pm).toString(); list.add(a);
            }
            Collections.sort(list, (x, y) -> {
                boolean sx = sel.contains(x.pkg), sy = sel.contains(y.pkg);
                if (sx != sy) return sx ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            });
            runOnUiThread(() -> { apps.clear(); apps.addAll(list); updateHead(); adapter.notifyDataSetChanged(); });
        }).start();
    }

    Drawable icon(String pkg) {
        Drawable d = icons.get(pkg);
        if (d == null) { try { d = getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { d = getDrawable(android.R.drawable.sym_def_app_icon); } icons.put(pkg, d); }
        return d;
    }

    void updateHead() {
        if (single) { head.setText("点一下应用即可选择 · 例如短视频、游戏、社交软件"); return; } head.setText("已选 " + sel.size() + " 个 · 建议只选词典、学习类应用（电话始终可用）"); }

    @Override protected void onPause() { super.onPause(); if (!single) Prefs.setWhitelist(this, sel); }
}
