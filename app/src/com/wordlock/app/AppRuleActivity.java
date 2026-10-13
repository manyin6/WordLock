package com.wordlock.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Add / edit one 应用锁 rule: app, weekdays, daily minutes, blocked window. */
public class AppRuleActivity extends Activity {
    AppLock.Rule r, orig; String oldPkg; LinearLayout page; int insetTop, insetBot;

    int dp(float v) { return U.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        oldPkg = getIntent().getStringExtra("pkg");
        AppLock.Rule ex = oldPkg == null ? null : AppLock.find(this, oldPkg);
        r = ex != null ? ex.copy() : new AppLock.Rule(); orig = ex;
        if (ex == null) oldPkg = null;
        String np = getIntent().getStringExtra("newPkg");
        if (ex == null && np != null) { r.pkg = np; r.label = getIntent().getStringExtra("newLabel"); }
        FrameLayout root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this); sv.setClipToPadding(false); sv.setVerticalScrollBarEnabled(false);
        page = U.col(this); sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            insetTop = ins.getSystemWindowInsetTop(); insetBot = ins.getSystemWindowInsetBottom(); render();
            return ins.consumeSystemWindowInsets();
        });
        setContentView(root);
        if (ex == null && r.pkg.isEmpty()) pickApp();
    }

    void pickApp() { startActivityForResult(new Intent(this, AppPickActivity.class).putExtra("single", true), 1); }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        if (rc == 1 && res == RESULT_OK && d != null) { r.pkg = d.getStringExtra("pkg"); r.label = d.getStringExtra("label"); render(); }
        else if (rc == 1 && r.pkg.isEmpty()) finish();
    }

    void section(String s) { page.addView(U.caption(this, s, false), U.m(U.full(), this, 0, 8, 0, 8)); }

    LinearLayout card() {
        LinearLayout c = U.col(this); c.setBackground(U.glass(this, false, 22)); c.setPadding(dp(16), dp(16), dp(16), dp(16));
        page.addView(c, U.m(U.full(), this, 0, 0, 0, 16)); return c;
    }

    TextView chip(String label, boolean sel, View.OnClickListener l) {
        TextView t = U.tv(this, label, 14, sel ? U.WHITE : U.TXT, true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(40));
        U.setBg(t, sel ? U.ACC : 0x99FFFFFF, 12); t.setClickable(true); t.setOnClickListener(l); return t;
    }

    void chipRows(LinearLayout c, String[] labels, boolean[] sel, View.OnClickListener[] ls, int perRow) {
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % perRow == 0) { row = U.row(this); c.addView(row, U.m(U.full(), this, 0, i == 0 ? 0 : 8, 0, 0)); }
            row.addView(chip(labels[i], sel[i], ls[i]), U.m(U.lpw(1), this, i % perRow == 0 ? 0 : 3, 0, i % perRow == perRow - 1 ? 0 : 3, 0));
        }
        if (row != null) for (int k = labels.length % perRow; k != 0 && k < perRow; k++) row.addView(new View(this), U.m(U.lpw(1), this, 3, 0, 0, 0));
    }

    LinearLayout line(LinearLayout c, String label, String value, View.OnClickListener l) {
        if (c.getChildCount() > 0) c.addView(U.divider(this, false, 0));
        LinearLayout row = U.row(this); row.setMinimumHeight(dp(52));
        row.addView(U.tv(this, label, 15, U.TXT, false), U.lpw(1));
        row.addView(U.tv(this, value, 15, U.SUB, false));
        if (l != null) { row.addView(U.tv(this, "  ›", 17, 0xFFB0B0B5, false)); row.setOnClickListener(l); row.setClickable(true); }
        c.addView(row, U.full()); return row;
    }

    void render() {
        page.removeAllViews();
        page.setPadding(dp(20), insetTop + dp(8), dp(20), insetBot + dp(24));
        LinearLayout bar = U.row(this);
        TextView back = U.tv(this, "‹", 30, U.TXT, false); back.setGravity(Gravity.CENTER); back.setClickable(true); back.setOnClickListener(v -> finish());
        bar.addView(back, U.lp(dp(40), dp(48)));
        bar.addView(U.tv(this, oldPkg == null ? "添加应用锁" : orig != null && AppLock.expired(orig) ? "重新开启应用锁" : "编辑应用锁", 20, U.TXT, true), U.lpw(1));
        page.addView(bar, U.m(U.full(), this, -8, 0, 0, 8));

        section("应用");
        LinearLayout ac = card(); ac.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout ar = U.row(this);
        ImageView iv = new ImageView(this);
        try { iv.setImageDrawable(getPackageManager().getApplicationIcon(r.pkg)); } catch (Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
        ar.addView(iv, U.lp(dp(40), dp(40)));
        ar.addView(U.tv(this, r.pkg.isEmpty() ? "点击选择应用" : r.label, 16, U.TXT, true), U.m(U.lpw(1), this, 12, 0, 0, 0));
        ar.addView(U.tv(this, "更换 ›", 14, U.SUB, false));
        ar.setClickable(true); ar.setOnClickListener(v -> pickApp());
        ac.addView(ar, U.full());

        section("持续时间");
        LinearLayout pc = card();
        int[] terms = {1, 3, 7, 14, 30};
        String[] tls = {"今天", "3 天", "7 天", "14 天", "30 天"};
        boolean[] tsel = new boolean[5]; View.OnClickListener[] tcl = new View.OnClickListener[5];
        for (int i = 0; i < 5; i++) { final int t = terms[i]; tsel[i] = r.term == t; tcl[i] = v -> { r.term = t; render(); }; }
        chipRows(pc, tls, tsel, tcl, 5);
        pc.addView(U.tv(this, "到 " + AppLock.endText(previewEnd()) + " 结束", 13, U.SUB, false), U.m(U.full(), this, 0, 12, 0, 0));

        section("星期 / 周期");
        LinearLayout dc = card();
        String[] dl = Plan.DAY_NAMES; boolean[] ds = new boolean[7]; View.OnClickListener[] dls = new View.OnClickListener[7];
        for (int i = 0; i < 7; i++) { final int bit = 1 << i; ds[i] = (r.days & bit) != 0; dls[i] = v -> { r.days ^= bit; render(); }; }
        chipRows(dc, dl, ds, dls, 7);
        chipRows(dc, new String[]{"每天", "工作日", "周末"}, new boolean[]{r.days == 127, r.days == 31, r.days == 96},
                new View.OnClickListener[]{v -> { r.days = 127; render(); }, v -> { r.days = 31; render(); }, v -> { r.days = 96; render(); }}, 3);

        section("每天可用时长");
        LinearLayout lc = card();
        int[] vals = {10, 30, 60, 120, AppLock.UNLIMITED, AppLock.BANNED};
        String[] ls = {"10 分钟", "30 分钟", "1 小时", "2 小时", "不限时长", "全天禁用"};
        boolean known = false; for (int v : vals) if (v == r.limit) known = true;
        String[] labels = new String[7]; boolean[] sel = new boolean[7]; View.OnClickListener[] cl = new View.OnClickListener[7];
        for (int i = 0; i < 6; i++) { final int v = vals[i]; labels[i] = ls[i]; sel[i] = r.limit == v; cl[i] = x -> { r.limit = v; render(); }; }
        labels[6] = known ? "自定义" : U.dur(r.limit); sel[6] = !known; cl[6] = x -> customLimit();
        chipRows(lc, labels, sel, cl, 4);

        section("不可用时间段（可选）");
        LinearLayout tc = card(); tc.setPadding(dp(16), dp(4), dp(16), dp(4));
        boolean has = r.rs >= 0;
        line(tc, "启用不可用时段", has ? "已开启" : "已关闭", v -> { if (r.rs >= 0) { r.rs = -1; r.re = -1; } else { r.rs = 22 * 60; r.re = 7 * 60; } render(); });
        if (has) {
            line(tc, "开始时间", AppLock.hm(r.rs), v -> time(true));
            line(tc, "结束时间", AppLock.hm(r.re), v -> time(false));
        }

        TextView tip = U.tv(this, "时长用完或处于不可用时段时，打开这个应用会被拦下。时长用完后可以「背单词换时间」：答对 "
                + AppLock.BONUS_Q + " 题多用 " + AppLock.BONUS_MIN + " 分钟，每天最多 " + AppLock.BONUS_MAX + " 次。", 12, U.SUB, false);
        tip.setLineSpacing(0, 1.25f); tip.setPadding(dp(4), 0, dp(4), 0);
        page.addView(tip, U.m(U.full(), this, 0, 0, 0, 20));

        page.addView(U.gbtn(this, orig != null && AppLock.expired(orig) ? "重新开启" : "保存", v -> save()), U.full());
        if (oldPkg != null) {
            page.addView(U.btn(this, r.on ? "暂停这条应用锁" : "恢复这条应用锁", 0x99FFFFFF, U.TXT, v -> {
                if (r.on && AppLock.committed(orig)) { refuse(); return; }
                r.on = !r.on; save();
            }), U.m(U.full(), this, 0, 12, 0, 0));
            page.addView(U.btn(this, "删除", 0x00000000, U.ACC, v -> {
                if (AppLock.committed(orig)) { refuse(); return; }
                new AlertDialog.Builder(this).setTitle("删除「" + r.label + "」的应用锁？")
                        .setPositiveButton("删除", (d, i) -> { AppLock.remove(this, oldPkg); finish(); }).setNegativeButton("取消", null).show();
            }), U.m(U.full(), this, 0, 4, 0, 0));
        }
    }

    void time(boolean start) {
        int m = start ? r.rs : r.re;
        new TimePickerDialog(this, (tp, hh, mm) -> { if (start) r.rs = hh * 60 + mm; else r.re = hh * 60 + mm; render(); }, m / 60, m % 60, true).show();
    }

    void customLimit() {
        EditText et = new EditText(this); et.setInputType(InputType.TYPE_CLASS_NUMBER); et.setHint("1 - 720 分钟");
        LinearLayout box = U.col(this); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(et, U.full());
        new AlertDialog.Builder(this).setTitle("每天可用多少分钟").setView(box).setPositiveButton("确定", (d, i) -> {
            try { r.limit = Math.max(1, Math.min(720, Integer.parseInt(et.getText().toString().trim()))); } catch (Exception e) { }
            render();
        }).setNegativeButton("取消", null).show();
    }

    /** the existing rule is still inside its term */
    boolean running() { return orig != null && orig.until > 0 && !AppLock.expired(orig); }

    long previewEnd() {
        long t = AppLock.termEnd(running() ? orig.created : System.currentTimeMillis(), r.term);
        return running() && AppLock.committed(orig) ? Math.max(t, orig.until) : t;
    }

    void refuse() {
        new AlertDialog.Builder(this).setTitle("应用锁生效中")
                .setMessage("「" + orig.label + "」的应用锁到 " + AppLock.endText(orig.until) + " 结束，结束前不能暂停、删除或放宽限制（见 设置 → 用户协议）。\n\n可以改得更严格：缩短时长、增加生效日、延长持续时间。")
                .setPositiveButton("好", null).show();
    }

    void save() {
        if (r.pkg.isEmpty()) { pickApp(); return; }
        if (r.days == 0) { new AlertDialog.Builder(this).setMessage("至少选择一天").setPositiveButton("好", null).show(); return; }
        if (running()) {
            r.created = orig.created; r.until = AppLock.termEnd(orig.created, r.term);
            if (AppLock.committed(orig)) {
                if (r.until < orig.until) r.until = orig.until;
                if (AppLock.loosened(orig, r) != null) { r = orig.copy(); render(); refuse(); return; }
            }
        } else { long now = System.currentTimeMillis(); r.created = now; r.until = AppLock.termEnd(now, r.term); r.on = true; }
        AppLock.put(this, r, oldPkg == null ? r.pkg : oldPkg);
        finish();
    }
}
