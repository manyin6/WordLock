package com.wordlock.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class LockService extends Service {
    static LockService inst;
    static volatile String accPkg;

    WindowManager wm; Handler h;
    FrameLayout full; LinearLayout bubble;
    TextView countdown, endAt, stat, rule, bubbleText; StudyPanel panel;
    final TextView[] tabs = new TextView[3]; int mode = StudyPanel.LEARN; boolean finishing;
    boolean fullShown, bubbleShown, inCall;
    String lastFg, dialer = ""; long graceUntil;
    final Set<String> imes = new HashSet<>();
    TelephonyManager tm; PhoneStateListener psl;

    public static void start(Context c, int min) {
        Prefs.startLock(c, min);
        c.startForegroundService(new Intent(c, LockService.class));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        wm = getSystemService(WindowManager.class);
        h = new Handler(Looper.getMainLooper());
        startFg();
        try { for (InputMethodInfo i : getSystemService(InputMethodManager.class).getEnabledInputMethodList()) imes.add(i.getPackageName()); } catch (Exception e) { }
        try { String d = getSystemService(TelecomManager.class).getDefaultDialerPackage(); if (d != null) dialer = d; } catch (Exception e) { }
        listenPhone();
    }

    void startFg() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("lock", "锁机状态", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "lock").setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("单词锁 · 专注中").setContentText("完成专注或答题通过后自动解锁")
                .setOngoing(true).setContentIntent(pi).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        long end = Prefs.lockEnd(this), now = Clock.now(this);
        if (end <= now) {
            Prefs.clearLock(this); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY;
        }
        if (full == null) buildFull();
        showFull();
        h.removeCallbacks(tick); h.post(tick);
        return START_STICKY;
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            long end = Prefs.lockEnd(LockService.this), remain = end - Clock.now(LockService.this);
            if (remain <= 0) { finishLock(false); return; }
            String t = fmtRemain(remain);
            countdown.setText(t);
            endAt.setText("结束于 " + new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(end)));
            if (bubbleText != null) bubbleText.setText("⏱ " + t + "  回到单词锁");
            if (GuardService.inst == null) { String fg = pollUsage(); if (fg != null) onFg(fg); }
            else if (!fullShown && accPkg != null) onFg(accPkg);
            h.postDelayed(this, 1000);
        }
    };

    static String fmtRemain(long ms) {
        long s = (ms + 999) / 1000, hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss) : String.format(Locale.US, "%02d:%02d", mm, ss);
    }

    // ---------- foreground app guard ----------
    boolean ignored(String p) {
        return p.equals(getPackageName()) || p.equals("com.android.systemui") || p.equals("android") || imes.contains(p);
    }

    final java.util.HashMap<String, Boolean> sysApp = new java.util.HashMap<>();

    /** preinstalled system app only, so a third-party package named like "alarm" or "clock" is not exempt */
    boolean isSystem(String p) {
        Boolean v = sysApp.get(p);
        if (v == null) {
            try { v = (getPackageManager().getApplicationInfo(p, 0).flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0; }
            catch (Exception e) { v = false; }
            sysApp.put(p, v);
        }
        return v;
    }

    boolean emergency(String p) {
        if (!dialer.isEmpty() && p.equals(dialer)) return true;
        return ExemptNames.named(p) && isSystem(p);
    }

    boolean allowed(String p) { return inCall || emergency(p) || Prefs.whitelist(this).contains(p); }

    void onFg(String pkg) {
        if (pkg == null || ignored(pkg) || !Prefs.locked(this)) return;
        boolean changed = !pkg.equals(lastFg);
        lastFg = pkg;
        if (fullShown) { if (changed && emergency(pkg)) enterBubble(0); }
        else {
            if (Clock.now(this) < graceUntil) return;
            if (!allowed(pkg)) showFull();
        }
    }

    String pollUsage() {
        try {
            UsageStatsManager um = getSystemService(UsageStatsManager.class);
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 10000, now);
            UsageEvents.Event e = new UsageEvents.Event(); String r = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && !ignored(e.getPackageName())) r = e.getPackageName();
            }
            return r;
        } catch (Exception ex) { return null; }
    }

    void listenPhone() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        try {
            tm = getSystemService(TelephonyManager.class);
            psl = new PhoneStateListener() {
                @Override public void onCallStateChanged(int s, String n) {
                    if (s != TelephonyManager.CALL_STATE_IDLE) { inCall = true; enterBubble(0); }
                    else if (inCall) { inCall = false; if (Prefs.locked(LockService.this)) showFull(); }
                }
            };
            tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Exception e) { psl = null; }
    }

    // ---------- overlay UI ----------
    int dp(float v) { return U.dp(this, v); }

    void buildFull() {
        full = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
                return super.dispatchKeyEvent(e);
            }
        };
        full.setBackgroundColor(U.DARK);
        full.addView(U.bgView(this, true), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = U.col(this);
        col.setPadding(dp(20), dp(40), dp(20), dp(16));
        full.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView title = U.tv(this, "单词锁 · 专注中", 13, U.DSUB, true); title.setGravity(Gravity.CENTER);
        col.addView(title, U.full());
        countdown = U.tv(this, "--:--", 56, U.WHITE, true); countdown.setGravity(Gravity.CENTER);
        countdown.setSingleLine(true);
        countdown.setAutoSizeTextTypeUniformWithConfiguration(24, 56, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        countdown.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        col.addView(countdown, U.m(U.full(), this, 0, 8, 0, 0));
        endAt = U.tv(this, "", 13, U.DSUB, false); endAt.setGravity(Gravity.CENTER);
        col.addView(endAt, U.m(U.full(), this, 0, 4, 0, 16));

        // segmented tabs
        LinearLayout seg = U.row(this); seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(U.glass(this, true, 16));
        String[] names = {"学习", "文字题", "看图题"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs[i] = U.tv(this, names[i], 14, U.WHITE, true); tabs[i].setGravity(Gravity.CENTER);
            tabs[i].setMinHeight(dp(40)); tabs[i].setClickable(true);
            tabs[i].setOnClickListener(v -> setMode(k));
            seg.addView(tabs[i], U.lpw(1));
        }
        col.addView(seg, U.full());
        rule = U.tv(this, "", 12, U.DSUB, false); rule.setGravity(Gravity.CENTER);
        col.addView(rule, U.m(U.full(), this, 0, 8, 0, 8));

        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        panel = new StudyPanel(this, true, new StudyPanel.L() {
            @Override public void onViewed() { Prefs.inc(LockService.this, "sView"); updateStat(); }
            @Override public void onAnswer(int m, boolean ok) {
                Context c = LockService.this;
                Prefs.inc(c, m == StudyPanel.PIC ? (ok ? "sPOk" : "sPBad") : (ok ? "sTOk" : "sTBad"));
                if (!ok) { Prefs.extend(c, Prefs.penalty(c)); }
                updateTabs(); updateStat();
                if (!ok) stat.setText("答错了，锁机时间 +" + Prefs.penalty(c) + " 分钟");
                if (Prefs.getInt(c, "sTOk", 0) >= Prefs.quizN(c) || Prefs.getInt(c, "sPOk", 0) >= Prefs.picN(c)) {
                    finishing = true; panel.stop(); h.postDelayed(() -> finishLock(true), 1000);
                }
            }
        });
        sv.addView(panel);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        stat = U.tv(this, "", 12, U.DSUB, false); stat.setGravity(Gravity.CENTER);
        col.addView(stat, U.m(U.full(), this, 0, 8, 0, 8));

        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout apps = U.row(this); hs.addView(apps);
        col.addView(hs, U.full());
        apps.addView(appBtn("📞", "电话", null, v -> openDialer()));
        PackageManager pm = getPackageManager();
        for (String pkg : Prefs.whitelist(this)) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                apps.addView(appBtn(null, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai), v -> launchApp(pkg)));
            } catch (Exception e) { }
        }
        setMode(StudyPanel.LEARN); updateStat();
    }

    void setMode(int m) {
        if (finishing) return;
        mode = m; updateTabs();
        panel.setHeader(m == StudyPanel.LEARN ? "" : m == StudyPanel.TEXT ? "文字题 · 看词选义" : "看图题 · 看图选词");
        panel.start(m);
    }

    void updateTabs() {
        int tOk = Prefs.getInt(this, "sTOk", 0), pOk = Prefs.getInt(this, "sPOk", 0), qn = Prefs.quizN(this), pn = Prefs.picN(this);
        tabs[0].setText("学习");
        tabs[1].setText("文字题 " + Math.min(tOk, qn) + "/" + qn);
        tabs[2].setText("看图题 " + Math.min(pOk, pn) + "/" + pn);
        for (int i = 0; i < 3; i++) {
            boolean sel = i == mode;
            if (sel) U.setBg(tabs[i], 0x33FFFFFF, 12); else tabs[i].setBackground(null);
            tabs[i].setTextColor(sel ? U.WHITE : U.DSUB);
        }
        rule.setText("提前解锁：文字题答对 " + qn + " 题，或看图题答对 " + pn + " 题 · 答错 +" + Prefs.penalty(this) + " 分钟\n本次题目来自随机抽取的 " + Words.SESSION + " 个单词（含错题）");
    }

    View appBtn(String emoji, String label, Drawable icon, View.OnClickListener l) {
        LinearLayout b = U.col(this); b.setGravity(Gravity.CENTER); b.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (icon != null) { ImageView iv = new ImageView(this); iv.setImageDrawable(icon); b.addView(iv, U.lp(dp(36), dp(36))); }
        else { TextView e = U.tv(this, emoji, 24, U.WHITE, false); e.setGravity(Gravity.CENTER); b.addView(e, U.lp(dp(36), dp(36))); }
        TextView t = U.tv(this, label, 11, U.DSUB, false); t.setGravity(Gravity.CENTER); t.setMaxLines(1); t.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(t, U.m(U.lp(dp(56), -2), this, 0, 6, 0, 0));
        b.setBackground(U.glass(this, true, 16)); b.setClickable(true); b.setOnClickListener(l);
        LinearLayout.LayoutParams p = U.lp(-2, -2); p.setMargins(0, 0, dp(8), 0); b.setLayoutParams(p);
        return b;
    }

    void updateStat() {
        stat.setText("本次  浏览 " + Prefs.getInt(this, "sView", 0)
                + " · 文字题 ✓" + Prefs.getInt(this, "sTOk", 0) + " ✗" + Prefs.getInt(this, "sTBad", 0)
                + " · 看图题 ✓" + Prefs.getInt(this, "sPOk", 0) + " ✗" + Prefs.getInt(this, "sPBad", 0));
    }

    WindowManager.LayoutParams fullParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }

    void showFull() {
        if (!Settings.canDrawOverlays(this) || full == null) return;
        hideBubble();
        if (!fullShown) { try { wm.addView(full, fullParams()); fullShown = true; } catch (Exception e) { } }
    }

    void hideFull() { if (fullShown) { try { wm.removeView(full); } catch (Exception e) { } fullShown = false; } }

    void enterBubble(long grace) { hideFull(); graceUntil = Clock.now(this) + grace; showBubble(); }

    void showBubble() {
        if (bubble == null) {
            bubble = U.row(this);
            bubbleText = U.tv(this, "⏱ 回到单词锁", 13, U.WHITE, true);
            bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
            bubble.setBackground(U.round(0xEEE5533D, dp(20)));
            bubble.addView(bubbleText);
            bubble.setOnClickListener(v -> showFull());
        }
        if (!bubbleShown && Settings.canDrawOverlays(this)) {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END; p.x = dp(8); p.y = dp(90);
            try { wm.addView(bubble, p); bubbleShown = true; } catch (Exception e) { }
        }
    }

    void hideBubble() { if (bubbleShown) { try { wm.removeView(bubble); } catch (Exception e) { } bubbleShown = false; } }

    void launchApp(String pkg) {
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        lastFg = pkg; enterBubble(3000);
        try { startActivity(li); } catch (Exception e) { showFull(); }
    }

    void openDialer() {
        lastFg = dialer; enterBubble(3000);
        try { startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { showFull(); }
    }

    void finishLock(boolean early) {
        h.removeCallbacks(tick); if (panel != null) panel.stop();
        hideFull(); hideBubble();
        int[] r = Prefs.recordSession(this, early);
        Prefs.clearLock(this);
        Intent d = new Intent(this, DoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("early", early).putExtra("r", r);
        try { startActivity(d); } catch (Exception e) { }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        hideFull(); hideBubble();
        if (tm != null && psl != null) try { tm.listen(psl, PhoneStateListener.LISTEN_NONE); } catch (Exception e) { }
        if (inst == this) inst = null;
        super.onDestroy();
    }
}
