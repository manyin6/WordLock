package com.wordlock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String[] LIST_KEYS = {"cet4", "cet6", "ky", "ielts"};
    static final String[] LIST_NAMES = {"四级 CET-4", "六级 CET-6", "考研", "雅思 IELTS"};
    static final String[] TAB_NAMES = {"首页", "计划", "应用锁", "收藏", "设置"};
    static final int NAV_H = 64;

    int tab = 0, insetTop, insetBot;
    FrameLayout root, host; LinearLayout nav; View topFade, botFade;
    final IconView[] icons = new IconView[5]; final TextView[] labels = new TextView[5]; final View[] navItems = new View[5];
    ScrollView curSv; LinearLayout curPage; View tDur, tLock, tStudy, tPlanRow, tAddRule, tWhite;
    CoachView coach; int step = -1, lastTab = -1;

    int dp(float v) { return U.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        if (b != null) tab = b.getInt("tab", 0);
        root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        host = new FrameLayout(this); root.addView(host, new FrameLayout.LayoutParams(-1, -1));
        topFade = new View(this); topFade.setBackground(U.fade(0xF2F7F4F0, true));
        root.addView(topFade, new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP));
        botFade = new View(this); botFade.setBackground(U.fade(0xE6F7F4F0, false));
        root.addView(botFade, new FrameLayout.LayoutParams(-1, dp(120), Gravity.BOTTOM));
        buildNav();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            insetTop = ins.getSystemWindowInsetTop(); insetBot = ins.getSystemWindowInsetBottom();
            layoutChrome(); render();
            return ins.consumeSystemWindowInsets();
        });
        setContentView(root);
    }

    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putInt("tab", tab); }

    @Override protected void onResume() {
        super.onResume();
        if (Prefs.getInt(this, "setupDone", 0) != 1 || Prefs.getInt(this, "termsAgreed", 0) < Docs.VERSION) { startActivity(new Intent(this, SetupActivity.class)); return; }
        if (Prefs.getInt(this, "tourDone", 0) != 1 && step < 0) root.postDelayed(this::startTour, 400);
        else if (step >= 0) root.postDelayed(() -> showStep(step), 300);
        if (Prefs.locked(this)) { try { startForegroundService(new Intent(this, LockService.class)); } catch (Exception e) { } }
        render();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) { render(); }

    // ---------- chrome ----------
    void buildNav() {
        nav = U.row(this); nav.setBackground(U.glass(this, false, 26));
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(8));
        int[] types = {IconView.HOME, IconView.PLAN, IconView.LOCK, IconView.STAR, IconView.GEAR};
        for (int i = 0; i < 5; i++) {
            final int k = i;
            LinearLayout it = U.col(this); it.setGravity(Gravity.CENTER); it.setClickable(true);
            icons[i] = new IconView(this, types[i]);
            it.addView(icons[i], U.lp(dp(24), dp(24)));
            labels[i] = U.tv(this, TAB_NAMES[i], 11, U.SUB, true); labels[i].setGravity(Gravity.CENTER);
            it.addView(labels[i], U.m(U.full(), this, 0, 4, 0, 0));
            it.setOnClickListener(v -> { tab = k; render(); });
            navItems[i] = it;
            nav.addView(it, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        root.addView(nav, new FrameLayout.LayoutParams(-1, dp(NAV_H), Gravity.BOTTOM));
    }

    void layoutChrome() {
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) topFade.getLayoutParams(); tp.height = insetTop + dp(28); topFade.setLayoutParams(tp);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) botFade.getLayoutParams(); bp.height = insetBot + dp(NAV_H + 56); botFade.setLayoutParams(bp);
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nav.getLayoutParams();
        np.setMargins(dp(20), 0, dp(20), insetBot + dp(12)); nav.setLayoutParams(np);
    }

    void updateNav() {
        for (int i = 0; i < 5; i++) {
            boolean sel = i == tab;
            icons[i].set(sel ? U.ACC : U.SUB, sel && i == 3);
            labels[i].setTextColor(sel ? U.ACC : U.SUB);
        }
    }

    void render() {
        if (host == null) return;
        int keep = (curSv != null && tab == lastTab) ? curSv.getScrollY() : 0; lastTab = tab;
        host.removeAllViews();
        ScrollView sv = new ScrollView(this); sv.setClipToPadding(false); sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = U.col(this);
        page.setPadding(dp(20), insetTop + dp(16), dp(20), insetBot + dp(NAV_H + 40));
        sv.addView(page); curSv = sv; curPage = page;
        if (tab == 0) home(page); else if (tab == 1) plan(page); else if (tab == 2) appLock(page); else if (tab == 3) favs(page); else settings(page);
        host.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        if (keep > 0) sv.post(() -> sv.scrollTo(0, keep));
        updateNav();
    }

    // ---------- shared building blocks ----------
    void title(LinearLayout page, String big, String sub) {
        page.addView(U.tv(this, big, 30, U.TXT, true), U.m(U.full(), this, 4, 8, 0, 0));
        page.addView(U.tv(this, sub, 14, U.SUB, false), U.m(U.full(), this, 4, 8, 0, 24));
    }

    void section(LinearLayout page, String s) { page.addView(U.caption(this, s, false), U.m(U.full(), this, 0, 8, 0, 8)); }

    LinearLayout card(LinearLayout page) {
        LinearLayout c = U.col(this); c.setBackground(U.glass(this, false, 22)); c.setPadding(dp(16), dp(16), dp(16), dp(16));
        page.addView(c, U.m(U.full(), this, 0, 0, 0, 16));
        return c;
    }

    /** list card: rows with zero vertical padding on the card so dividers line up with labels */
    LinearLayout listCard(LinearLayout page) {
        LinearLayout c = card(page); c.setPadding(dp(16), dp(4), dp(16), dp(4)); return c;
    }

    LinearLayout row(LinearLayout c, String label, String value, View.OnClickListener l) {
        if (c.getChildCount() > 0) c.addView(U.divider(this, false, 0));
        LinearLayout r = U.row(this); r.setMinimumHeight(dp(52));
        r.addView(U.tv(this, label, 15, U.TXT, false), U.lpw(1));
        r.addView(U.tv(this, value, 15, U.SUB, false));
        if (l != null) { r.addView(U.tv(this, "  ›", 17, 0xFFB0B0B5, false)); r.setOnClickListener(l); r.setClickable(true); }
        c.addView(r, U.full());
        return r;
    }

    TextView chip(String label, boolean sel, View.OnClickListener l) {
        TextView t = U.tv(this, label, 14, sel ? U.WHITE : U.TXT, true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(40));
        U.setBg(t, sel ? U.ACC : 0x99FFFFFF, 12); t.setClickable(true); t.setOnClickListener(l);
        return t;
    }

    View stat(String value, String label) {
        LinearLayout c = U.col(this);
        c.addView(U.tv(this, value, 26, U.TXT, true));
        c.addView(U.tv(this, label, 12, U.SUB, false), U.m(U.full(), this, 0, 6, 0, 0));
        return c;
    }

    // ---------- 首页 ----------
    void home(LinearLayout page) {
        String date = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
        title(page, "单词锁", date + " · 锁住手机，背完单词再解锁");
        Words w = Words.get(this);

        LinearLayout st = card(page); LinearLayout sr = U.row(st.getContext()); st.addView(sr, U.full());
        sr.addView(stat(Prefs.getInt(this, "min_" + Prefs.today(), 0) + "", "今日专注 · 分钟"), U.lpw(1));
        sr.addView(stat(Prefs.streakNow(this) + "", "连续打卡 · 天"), U.lpw(1));
        sr.addView(stat(w.seenCount() + "", "已学单词"), U.lpw(1));

        if (Prefs.locked(this)) {
            LinearLayout c0 = card(page);
            c0.addView(U.tv(this, "🔒 正在锁机中", 16, U.TXT, true));
            c0.addView(U.gbtn(this, "回到锁机界面", v -> startForegroundService(new Intent(this, LockService.class))), U.m(U.full(), this, 0, 12, 0, 0));
        }

        section(page, "立即锁机");
        LinearLayout c1 = card(page);
        int dur = Math.max(1, Math.min(10080, Prefs.getInt(this, "dur", 15)));
        int[] opts = {15, 30, 60, 360, 1440, 10080};
        String[] labels = {"15 分钟", "30 分钟", "1 小时", "6 小时", "24 小时", "7 天"};
        boolean inOpts = false; for (int o : opts) if (o == dur) inOpts = true;
        LinearLayout r = null, chips = U.col(this); c1.addView(chips, U.full()); tDur = chips;
        for (int i = 0; i < opts.length + 1; i++) {
            if (i % 4 == 0) { r = U.row(this); chips.addView(r, U.m(U.full(), this, 0, i == 0 ? 0 : 8, 0, 0)); }
            final int val = i < opts.length ? opts[i] : -1;
            String label = i < opts.length ? labels[i] : "自定义";
            boolean sel = i < opts.length ? val == dur : !inOpts;
            r.addView(chip(label, sel, v -> { if (val > 0) { Prefs.setInt(this, "dur", val); render(); } else customDur(); }),
                    U.m(U.lpw(1), this, i % 4 == 0 ? 0 : 4, 0, i % 4 == 3 ? 0 : 4, 0));
        }
        tLock = U.gbtn(this, "开始锁机 · " + U.dur(dur), v -> confirmLock(dur));
        c1.addView(tLock, U.m(U.full(), this, 0, 16, 0, 0));
        TextView rule = U.tv(this, "提前解锁：文字题答对 " + Prefs.quizN(this) + " 题，或看图题答对 " + Prefs.picN(this)
                + " 题。答错一题 +" + Prefs.penalty(this) + " 分钟。", 12, U.SUB, false);
        rule.setLineSpacing(0, 1.2f);
        c1.addView(rule, U.m(U.full(), this, 0, 12, 0, 0));

        section(page, "自由学习 · 每次从词库和错题本随机抽 " + Words.SESSION + " 词");
        LinearLayout c2 = listCard(page); tStudy = c2;
        row(c2, "📖  学习本组单词", "错题 " + w.hard().size(), v -> study(StudyPanel.LEARN, false));
        row(c2, "✍️  文字测验", "待复习 " + w.dueCount(), v -> study(StudyPanel.TEXT, false));
        row(c2, "🖼  看图测验", "配图词 " + w.picCount(), v -> study(StudyPanel.PIC, false));
    }

    void study(int mode, boolean fav) {
        startActivity(new Intent(this, WordBookActivity.class).putExtra("mode", mode).putExtra("fav", fav));
    }

    // ---------- 计划 ----------
    void plan(LinearLayout page) {
        title(page, "计划", "固定时间自动锁机，帮你养成习惯");
        boolean on = Prefs.getInt(this, "planOn", 0) == 1;
        int mask = Prefs.getInt(this, "planDays", 127);

        section(page, "每日自动锁机");
        LinearLayout c = listCard(page);
        tPlanRow = row(c, "自动锁机", on ? "已开启" : "已关闭", v -> { Prefs.setInt(this, "planOn", on ? 0 : 1); Plan.schedule(this); render(); });
        row(c, "开始时间", String.format(Locale.US, "%02d:%02d", Prefs.getInt(this, "planH", 20), Prefs.getInt(this, "planM", 0)), v -> pickPlanTime());
        row(c, "锁机时长", U.dur(Prefs.getInt(this, "planDur", 60)), v -> {
            int[] vals = {15, 25, 30, 45, 60, 90, 120}; String[] ls = new String[vals.length];
            for (int i = 0; i < vals.length; i++) ls[i] = U.dur(vals[i]);
            new AlertDialog.Builder(this).setTitle("每次锁机多久").setItems(ls, (d, i) -> { Prefs.setInt(this, "planDur", vals[i]); Plan.schedule(this); render(); }).show();
        });

        section(page, "重复");
        LinearLayout dc = card(page); LinearLayout dr = U.row(this); dc.addView(dr, U.full());
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i; boolean sel = (mask & bit) != 0;
            dr.addView(chip(Plan.DAY_NAMES[i], sel, v -> { Prefs.setInt(this, "planDays", mask ^ bit); Plan.schedule(this); render(); }),
                    U.m(U.lpw(1), this, i == 0 ? 0 : 3, 0, i == 6 ? 0 : 3, 0));
        }
        long nt = Plan.nextTime(this);
        String next = nt == 0 ? "未开启自动锁机" : "下次锁机：" + new SimpleDateFormat("M月d日 EEEE HH:mm", Locale.CHINA).format(new Date(nt));
        dc.addView(U.tv(this, next, 13, U.SUB, false), U.m(U.full(), this, 0, 12, 0, 0));

        section(page, "最近 7 天");
        LinearLayout ch = card(page);
        int[] mins = new int[7]; String[] dn = new String[7]; int max = 30, sum = 0, cnt = 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            String key = Prefs.day(cal.getTimeInMillis());
            mins[i] = Prefs.getInt(this, "min_" + key, 0); sum += mins[i]; cnt += Prefs.getInt(this, "cnt_" + key, 0);
            max = Math.max(max, mins[i]); dn[i] = i == 6 ? "今天" : "周" + Plan.DAY_NAMES[Plan.dayIndex(cal)];
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        LinearLayout bars = U.row(this); bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 7; i++) {
            LinearLayout b = U.col(this); b.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            b.addView(U.tv(this, mins[i] > 0 ? mins[i] + "" : "", 11, U.SUB, false));
            View bar = new View(this);
            bar.setBackground(U.round(i == 6 ? U.ACC : 0x66E5533D, dp(6)));
            LinearLayout.LayoutParams bp = U.lp(dp(20), Math.max(dp(4), dp(96) * mins[i] / max)); bp.topMargin = dp(4);
            b.addView(bar, bp);
            b.addView(U.tv(this, dn[i], 11, i == 6 ? U.ACC : U.SUB, i == 6), U.m(U.lp(-2, -2), this, 0, 8, 0, 0));
            bars.addView(b, new LinearLayout.LayoutParams(0, dp(140), 1f));
        }
        ch.addView(bars, U.full());
        ch.addView(U.tv(this, "7 天累计 " + sum + " 分钟 · 锁机 " + cnt + " 次", 13, U.SUB, false), U.m(U.full(), this, 0, 12, 0, 0));
    }

    void pickPlanTime() {
        new TimePickerDialog(this, (tp, hh, mm) -> {
            Prefs.setInt(this, "planH", hh); Prefs.setInt(this, "planM", mm); Plan.schedule(this); render();
        }, Prefs.getInt(this, "planH", 20), Prefs.getInt(this, "planM", 0), true).show();
    }

    // ---------- 应用锁 ----------
    void appLock(LinearLayout page) {
        title(page, "应用锁", "限制单个应用每天能用多久、哪些时段不能用");
        if (!Perms.accOn(this) || !Perms.usageOn(this)) {
            LinearLayout w = card(page);
            TextView t = U.tv(this, "⚠️ 应用锁需要「无障碍服务」和「使用情况访问」两项权限，现在还没全部开启。", 14, U.TXT, false);
            t.setLineSpacing(0, 1.3f); w.addView(t);
            w.addView(U.btn(this, "去开启", U.ACC, U.WHITE, v -> Perms.open(this, Perms.accOn(this) ? "usage" : "acc")), U.m(U.full(), this, 0, 12, 0, 0));
        }
        tAddRule = U.gbtn(this, "＋ 添加应用锁", v -> startActivity(new Intent(this, AppRuleActivity.class)));
        page.addView(tAddRule, U.m(U.full(), this, 0, 0, 0, 16));

        List<AppLock.Rule> rs = AppLock.rules(this);
        java.util.Map<String, Long> use = AppLock.usageToday(this);
        if (rs.isEmpty()) {
            LinearLayout c = card(page);
            TextView e = U.tv(this, "还没有应用锁。\n点上面的按钮，选一个想少用的应用（比如短视频、游戏），设置每天能用多久。", 14, U.SUB, false);
            e.setLineSpacing(0, 1.3f); c.addView(e);
        } else {
            section(page, "已锁应用 · " + rs.size());
            LinearLayout c = listCard(page);
            for (AppLock.Rule r : rs) ruleRow(c, r, use.containsKey(r.pkg) ? use.get(r.pkg) : 0L);
        }

        section(page, "今日应用使用时长");
        java.util.ArrayList<java.util.Map.Entry<String, Long>> top = new java.util.ArrayList<>();
        PackageManager pm = getPackageManager();
        for (java.util.Map.Entry<String, Long> e : use.entrySet())
            if (e.getValue() >= 60000 && !e.getKey().equals(getPackageName()) && pm.getLaunchIntentForPackage(e.getKey()) != null) top.add(e);
        java.util.Collections.sort(top, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (top.isEmpty()) {
            LinearLayout c = card(page);
            c.addView(U.tv(this, Perms.usageOn(this) ? "今天还没有使用超过 1 分钟的应用" : "开启「使用情况访问」后显示", 14, U.SUB, false));
        } else {
            LinearLayout c = listCard(page);
            for (int i = 0; i < Math.min(8, top.size()); i++) {
                String pkg = top.get(i).getKey(); long ms = top.get(i).getValue();
                String label = pkg;
                try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ex) { }
                boolean locked = AppLock.find(this, pkg) != null; final String fl = label;
                LinearLayout r = appRow(c, pkg, label, U.dur((int) (ms / 60000)), null);
                TextView act = U.tv(this, locked ? "已锁" : "＋ 锁", 13, locked ? U.SUB : U.ACC, true);
                act.setPadding(dp(12), dp(6), dp(12), dp(6)); act.setBackground(U.round(locked ? 0x14000000 : 0x1AE5533D, dp(12)));
                r.addView(act, U.m(U.lp(-2, -2), this, 8, 0, 0, 0));
                r.setClickable(true);
                r.setOnClickListener(v -> startActivity(new Intent(this, AppRuleActivity.class).putExtra(locked ? "pkg" : "newPkg", pkg).putExtra("newLabel", fl)));
            }
        }
    }

    LinearLayout appRow(LinearLayout c, String pkg, String label, String sub, View extra) {
        if (c.getChildCount() > 0) c.addView(U.divider(this, false, 56));
        LinearLayout r = U.row(this); r.setPadding(0, dp(12), 0, dp(12));
        ImageView iv = new ImageView(this);
        try { iv.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); } catch (Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
        r.addView(iv, U.lp(dp(40), dp(40)));
        LinearLayout t = U.col(this);
        TextView name = U.tv(this, label, 16, U.TXT, true); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name);
        TextView s2 = U.tv(this, sub, 12, U.SUB, false); s2.setMaxLines(2); t.addView(s2, U.m(U.full(), this, 0, 4, 0, 0));
        if (extra != null) t.addView(extra, U.m(U.full(), this, 0, 8, 0, 0));
        r.addView(t, U.m(U.lpw(1), this, 16, 0, 0, 0));
        c.addView(r, U.full());
        return r;
    }

    void ruleRow(LinearLayout c, AppLock.Rule r, long usedMs) {
        int used = (int) (usedMs / 60000), bonus = AppLock.bonusMin(this, r.pkg);
        String tl = AppLock.termLeft(r);
        String sub = r.daysText() + " · " + r.limitText() + (r.rs >= 0 ? " · " + r.rangeText() : "") + (tl.isEmpty() ? "" : " · " + tl);
        View bar = null;
        if (r.limit > 0) {
            FrameLayout b = new FrameLayout(this); b.setBackground(U.round(0x14000000, dp(3)));
            View f = new View(this);
            int total = r.limit + bonus; float frac = Math.min(1f, used / (float) total);
            f.setBackground(U.round(frac >= 1 ? U.ACC : frac > 0.8f ? U.ACC2 : U.GREEN, dp(3)));
            b.addView(f, new FrameLayout.LayoutParams(0, -1));
            b.setLayoutParams(U.lp(-1, dp(6)));
            b.post(() -> { FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) f.getLayoutParams(); lp.width = (int) (b.getWidth() * frac); f.setLayoutParams(lp); });
            bar = b;
        }
        LinearLayout row = appRow(c, r.pkg, r.label, sub, bar);
        int[] k = {0};
        String status = AppLock.expired(r) ? "已结束" : !r.on ? "已暂停" : !AppLock.activeToday(r) ? "今天不限" : AppLock.blockReason(this, r, k) != null ? "已锁定"
                : r.limit > 0 ? used + "/" + (r.limit + bonus) + " 分" : "已用 " + used + " 分";
        TextView st = U.tv(this, status, 13, "已锁定".equals(status) ? U.ACC : U.SUB, true);
        row.addView(st, U.m(U.lp(-2, -2), this, 8, 0, 0, 0));
        row.addView(U.tv(this, " ›", 17, 0xFFB0B0B5, false));
        row.setClickable(true);
        row.setOnClickListener(v -> startActivity(new Intent(this, AppRuleActivity.class).putExtra("pkg", r.pkg)));
    }

    // ---------- 新手引导 ----------
    static class Step { int tab; int target; String title, text; Step(int tab, int target, String title, String text) { this.tab = tab; this.target = target; this.title = title; this.text = text; } }
    static final int T_DUR = 0, T_LOCK = 1, T_STUDY = 2, T_PLAN = 3, T_ADD = 4, T_WHITE = 5, NAV = 10;
    static final Step[] STEPS = {
            new Step(0, T_DUR, "选择锁机时长", "点这里选择要专注多久，比如 15 分钟，最长可选 7 天。"),
            new Step(0, T_LOCK, "开始锁机", "点这个按钮就会锁住手机。锁机后在「文字题」或「看图题」答对足够的题，就能提前解锁。\n（引导中点它不会真的锁机）"),
            new Step(0, T_STUDY, "自由学习", "不锁机也能背单词。每次从词库和错题本里随机抽 " + Words.SESSION + " 个词，学习和答题都只在这 " + Words.SESSION + " 个词里。"),
            new Step(0, NAV + 1, "点底部「计划」", "下一步去设置每天自动锁机。点亮起来的「计划」。"),
            new Step(1, T_PLAN, "每天自动锁机", "点这里打开，到点就自动锁机，还能选星期几重复。"),
            new Step(1, NAV + 2, "点底部「应用锁」", "应用锁可以单独限制某个应用。点亮起来的「应用锁」。"),
            new Step(2, T_ADD, "添加应用锁", "点这里选一个应用（比如抖音、游戏），设置每天能用多久、哪些时段不能用。时长用完后，背单词还能换时间。"),
            new Step(2, NAV + 3, "点底部「收藏」", "收藏的单词和错题本都在这里，可以专门练习。"),
            new Step(3, NAV + 4, "点底部「设置」", "最后看看设置。"),
            new Step(4, T_WHITE, "白名单", "锁机时仍然能打开的应用（比如词典）在这里选。\n以后想再看引导，在设置 → 帮助 → 新手引导。"),
    };

    View target(int t) {
        if (t >= NAV) return navItems[t - NAV];
        switch (t) { case T_DUR: return tDur; case T_LOCK: return tLock; case T_STUDY: return tStudy; case T_PLAN: return tPlanRow; case T_ADD: return tAddRule; default: return tWhite; }
    }

    void startTour() {
        if (step >= 0 || isFinishing()) return;
        if (coach == null) {
            coach = new CoachView(this, new CoachView.L() {
                @Override public void onTapTarget() { advance(); }
                @Override public void onNext() { advance(); }
                @Override public void onSkip() { endTour(); }
                @Override public View target() { return step >= 0 ? MainActivity.this.target(STEPS[step].target) : null; }
            });
            root.addView(coach, new FrameLayout.LayoutParams(-1, -1));
        }
        coach.setVisibility(View.VISIBLE); coach.bringToFront();
        showStep(0);
    }

    void advance() {
        Step s = STEPS[step];
        if (s.target >= NAV) { tab = s.target - NAV; render(); }
        if (step + 1 >= STEPS.length) endTour(); else showStep(step + 1);
    }

    void endTour() {
        step = -1; Prefs.setInt(this, "tourDone", 1);
        if (coach != null) coach.setVisibility(View.GONE);
        tab = 0; render();
    }

    void showStep(int i) {
        step = i; Step s = STEPS[i];
        if (tab != s.tab) { tab = s.tab; render(); }
        root.post(() -> {
            View t = target(s.target);
            if (t == null || coach == null) return;
            if (s.target < NAV && curSv != null) {
                int top = 0; View v = t;
                while (v != null && v != curPage) { top += v.getTop(); v = (View) v.getParent(); }
                int want = Math.max(0, top - curSv.getHeight() / 3);
                curSv.scrollTo(0, want);
            }
            boolean last = i == STEPS.length - 1;
            coach.show(s.title, s.text, (i + 1) + " / " + STEPS.length, last ? "完成" : s.target >= NAV ? "点它" : "下一步");
        });
    }

    @Override public void onBackPressed() { if (step >= 0) return; super.onBackPressed(); }

    // ---------- 收藏 ----------
    void favs(LinearLayout page) {
        Words w = Words.get(this);
        List<Words.W> fl = w.favList();
        title(page, "收藏夹", "共 " + fl.size() + " 个收藏 · 点单词听发音，点星标取消收藏");
        if (!fl.isEmpty()) {
            LinearLayout pr = U.row(this);
            String[] ms = {"浏览", "文字题", "看图题"};
            for (int i = 0; i < 3; i++) {
                final int k = i;
                pr.addView(i == 0 ? U.gbtn(this, "练习 · " + ms[i], v -> study(k, true)) : U.btn(this, ms[i], 0x99FFFFFF, U.TXT, v -> study(k, true)),
                        U.m(i == 0 ? new LinearLayout.LayoutParams(0, -2, 1.4f) : U.lpw(1), this, i == 0 ? 0 : 8, 0, 0, 0));
            }
            page.addView(pr, U.m(U.full(), this, 0, 0, 0, 16));
            LinearLayout c = listCard(page);
            for (Words.W x : fl) wordRow(c, x);
        } else {
            LinearLayout c = card(page);
            TextView e = U.tv(this, "还没有收藏的单词。\n学习或答题时点卡片右上角的「☆ 收藏」即可加入。", 14, U.SUB, false);
            e.setLineSpacing(0, 1.3f); c.addView(e);
        }
        List<Words.W> hard = w.hard();
        section(page, "错题本 · " + hard.size());
        if (hard.isEmpty()) { LinearLayout c = card(page); c.addView(U.tv(this, "暂时没有答错的词，继续保持～", 14, U.SUB, false)); }
        else { LinearLayout c = listCard(page); for (Words.W x : hard) wordRow(c, x); }
    }

    void wordRow(LinearLayout c, Words.W x) {
        Words w = Words.get(this);
        if (c.getChildCount() > 0) c.addView(U.divider(this, false, 56));
        LinearLayout r = U.row(this); r.setPadding(0, dp(12), 0, dp(12));
        Bitmap b = x.img.isEmpty() ? null : U.asset(this, "img/" + x.img + ".png");
        if (b != null) { ImageView iv = new ImageView(this); iv.setImageBitmap(b); r.addView(iv, U.lp(dp(40), dp(40))); }
        else {
            TextView ph = U.tv(this, x.w.substring(0, 1).toUpperCase(Locale.US), 17, U.ACC, true); ph.setGravity(Gravity.CENTER);
            ph.setBackground(U.round(0x1AE5533D, dp(12))); r.addView(ph, U.lp(dp(40), dp(40)));
        }
        LinearLayout t = U.col(this);
        t.addView(U.tv(this, x.w + (x.p.isEmpty() ? "" : "   /" + x.p + "/"), 16, U.TXT, true));
        TextView m = U.tv(this, x.shortM(), 13, U.SUB, false); m.setMaxLines(2);
        t.addView(m, U.m(U.full(), this, 0, 4, 0, 0));
        r.addView(t, U.m(U.lpw(1), this, 16, 0, 8, 0));
        TextView s = U.tv(this, w.isFav(x) ? "★" : "☆", 22, w.isFav(x) ? 0xFFF5A623 : 0xFFB0B0B5, false);
        s.setGravity(Gravity.CENTER); s.setClickable(true);
        s.setOnClickListener(v -> { w.toggleFav(x); render(); });
        r.addView(s, U.lp(dp(40), dp(40)));
        r.setOnClickListener(v -> Speaker.get(this).speak(x.w));
        c.addView(r, U.full());
    }

    // ---------- 设置 ----------
    void settings(LinearLayout page) {
        title(page, "设置", "学习、解锁规则与权限");
        Words w = Words.get(this);
        String list = Prefs.str(this, "list", "cet4"); String lname = LIST_NAMES[0];
        for (int i = 0; i < LIST_KEYS.length; i++) if (LIST_KEYS[i].equals(list)) lname = LIST_NAMES[i];

        section(page, "学习");
        LinearLayout c1 = listCard(page);
        row(c1, "词表", lname + " · " + w.total() + " 词", v -> new AlertDialog.Builder(this).setTitle("选择词表")
                .setItems(LIST_NAMES, (d, i) -> { Prefs.setStr(this, "list", LIST_KEYS[i]); render(); }).show());
        row(c1, "每日新词", Prefs.getInt(this, "newPerDay", 20) + " 个", v -> choose("每日新词上限", "newPerDay", new int[]{10, 20, 30, 50, 100}, " 个"));

        section(page, "提前解锁规则（两种题分开计数，满足一种即可）");
        LinearLayout c2 = listCard(page);
        row(c2, "文字题需答对", Prefs.quizN(this) + " 题", v -> choose("文字题需要答对", "quizN", new int[]{5, 10, 15, 20, 30}, " 题"));
        row(c2, "看图题需答对", Prefs.picN(this) + " 题", v -> choose("看图题需要答对", "picN", new int[]{3, 5, 7, 10, 15, 20}, " 题"));
        row(c2, "答错加时", Prefs.penalty(this) + " 分钟", v -> choose("每答错一题延长", "penalty", new int[]{0, 1, 2, 5, 10}, " 分钟"));

        section(page, "白名单");
        LinearLayout c3 = listCard(page);
        tWhite = row(c3, "锁机时可用的应用", Prefs.whitelist(this).size() + " 个", v -> startActivity(new Intent(this, AppPickActivity.class)));

        section(page, "权限");
        LinearLayout c4 = listCard(page);
        for (Perms.P p : Perms.all(this)) {
            boolean ok = Perms.ok(this, p.id);
            perm(c4, p.name, p.desc, p.id.equals("auto") ? null : ok, () -> Perms.open(this, p.id));
        }
        TextView tip = U.tv(this, "安卓 13 及以上：无障碍若提示「受限制的设置」，先到 应用信息 → 右上角 ⋮ → 允许受限制的设置。", 12, U.SUB, false);
        tip.setLineSpacing(0, 1.25f); tip.setPadding(dp(4), 0, dp(4), 0);
        page.addView(tip, U.m(U.full(), this, 0, 0, 0, 16));

        section(page, "帮助");
        LinearLayout c6 = listCard(page);
        row(c6, "微信客服", Docs.WECHAT, v -> Support.wechat(this));
        row(c6, "新手引导", "重新查看", v -> { tab = 0; render(); root.post(this::startTour); });

        section(page, "协议与隐私");
        LinearLayout c7 = listCard(page);
        for (int i = 0; i < Docs.TITLES.length; i++) { final int k = i; row(c7, Docs.TITLES[i], "", v -> startActivity(new Intent(this, DocActivity.class).putExtra("type", k))); }

        section(page, "关于");
        LinearLayout c5 = card(page);
        TextView ab = U.tv(this, "单词锁 v0.6.5 · MIT 开源版\n词库 ECDICT（MIT）· 例句 Tatoeba（CC BY 2.0 FR）· 图片 Noto Emoji（Apache 2.0）", 12, U.SUB, false);
        ab.setLineSpacing(0, 1.3f); c5.addView(ab);
    }

    void perm(LinearLayout c, String name, String desc, Boolean ok, Runnable go) {
        if (c.getChildCount() > 0) c.addView(U.divider(this, false, 0));
        LinearLayout r = U.row(this); r.setMinimumHeight(dp(60)); r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout t = U.col(this);
        t.addView(U.tv(this, name, 15, U.TXT, false)); t.addView(U.tv(this, desc, 12, U.SUB, false), U.m(U.full(), this, 0, 4, 0, 0));
        r.addView(t, U.lpw(1));
        TextView s = U.tv(this, ok == null ? "去设置" : ok ? "已开启" : "去开启", 13, ok == null ? U.SUB : ok ? U.GREEN : U.WHITE, true);
        s.setPadding(dp(12), dp(6), dp(12), dp(6));
        s.setBackground(U.round(ok == null ? 0x14000000 : ok ? 0x1A22A06B : U.ACC, dp(12)));
        r.addView(s);
        r.setClickable(true); r.setOnClickListener(v -> go.run());
        c.addView(r, U.full());
    }

    boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    boolean accOn() {
        return Perms.accOn(this);
    }

    boolean usageOn() {
        AppOpsManager ao = getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName())
                : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    void go(Intent i) {
        try { startActivity(i); } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
        }
    }

    void openOverlay() { go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }

    void choose(String title, String key, int[] vals, String unit) {
        String[] labels = new String[vals.length];
        for (int i = 0; i < vals.length; i++) labels[i] = vals[i] + unit;
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (d, i) -> { Prefs.setInt(this, key, vals[i]); render(); }).show();
    }

    void customDur() {
        EditText et = new EditText(this); et.setInputType(InputType.TYPE_CLASS_NUMBER); et.setHint("1 - 10080 分钟（最长 7 天）");
        et.setText(String.valueOf(Math.max(1, Math.min(10080, Prefs.getInt(this, "dur", 15)))));
        et.selectAll();
        LinearLayout box = U.col(this); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(et, U.full());
        box.addView(U.tv(this, "按分钟输入：1 小时 = 60 分钟，7 天 = 10080 分钟", 12, U.SUB, false));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义锁机时长").setView(box)
                .setPositiveButton("确定", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int minutes = Integer.parseInt(et.getText().toString().trim());
                if (minutes < 1 || minutes > 10080) { et.setError("请输入 1 - 10080 分钟，最长 7 天"); return; }
                Prefs.setInt(this, "dur", minutes); dialog.dismiss(); render();
            } catch (NumberFormatException e) { et.setError("请输入 1 - 10080 的整数分钟"); }
        }));
        dialog.show();
    }

    void confirmLock(int dur) {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("需要悬浮窗权限").setMessage("锁机界面要显示在其他应用上方，请先开启悬浮窗权限。")
                    .setPositiveButton("去开启", (d, i) -> openOverlay()).setNegativeButton("取消", null).show();
            return;
        }
        String warn = (!accOn() && !usageOn()) ? "\n\n⚠️ 还没开无障碍或使用情况权限：打开白名单应用后，切走时不能自动回到锁机界面。" : "";
        new AlertDialog.Builder(this).setTitle("开始锁机 " + U.dur(dur) + "？")
                .setMessage("锁机期间只能用白名单应用和电话。\n提前解锁：文字题答对 " + Prefs.quizN(this) + " 题，或看图题答对 " + Prefs.picN(this)
                        + " 题；答错一题 +" + Prefs.penalty(this) + " 分钟。" + warn)
                .setPositiveButton("开始", (d, i) -> { LockService.start(this, dur); render(); })
                .setNegativeButton("取消", null).show();
    }
}
