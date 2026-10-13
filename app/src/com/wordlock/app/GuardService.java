package com.wordlock.app;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashSet;
import java.util.Set;

/** Listens only to window-state changes to know which app is in front (reads no screen content).
 *  Drives the full-screen lock (LockService) and the per-app lock (应用锁). */
public class GuardService extends AccessibilityService {
    static volatile GuardService inst;
    static volatile String lastIssue;
    static final String[] QUOTES = {"万事开头难，加油！", "一步一步，努力成为更好的自己！", "放弃很容易，但坚持一定很酷～", "现在多背一个词，考场上就少一分慌。"};

    final Set<String> imes = new HashSet<>();
    final Handler h = new Handler(Looper.getMainLooper()); WindowManager wm; PowerManager pm;
    String cur; FrameLayout block; boolean blockShown; String blockPkg, warnedPkg;

    @Override public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class); pm = getSystemService(PowerManager.class);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); inst = this;
        wm = getSystemService(WindowManager.class); pm = getSystemService(PowerManager.class);
        try { for (InputMethodInfo i : getSystemService(InputMethodManager.class).getEnabledInputMethodList()) imes.add(i.getPackageName()); } catch (Exception e) { }
    }

    boolean ignore(String p) {
        return p.equals(getPackageName()) || p.equals("com.android.systemui") || p.equals("android") || imes.contains(p);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        try {
            String pkg = e.getPackageName().toString();
            if (ignore(pkg)) return;
            LockService s = LockService.inst;
            if (s == null || !s.ignored(pkg)) { LockService.accPkg = pkg; if (s != null) s.onFg(pkg); }
            onFgApp(pkg);
        } catch (RuntimeException ex) { recordIssue(ex); }
    }

    // ---------- 应用锁 ----------
    void onFgApp(String pkg) {
        boolean changed = !pkg.equals(cur); cur = pkg;
        if (blockShown && !pkg.equals(blockPkg)) hideBlock();
        if (changed) { warnedPkg = null; AppLock.invalidate(); }
        check();
        h.removeCallbacks(tick);
        if (AppLock.find(this, pkg) != null) h.postDelayed(tick, 10000);
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if (cur == null || AppLock.find(GuardService.this, cur) == null) return;
                if (pm != null && pm.isInteractive()) { AppLock.invalidate(); check(); }
                h.postDelayed(this, 10000);
            } catch (RuntimeException ex) { recordIssue(ex); }
        }
    };

    void check() {
        if (cur == null || blockShown || Prefs.locked(this)) return;
        AppLock.Rule r = AppLock.find(this, cur);
        if (r == null) return;
        int[] kind = {0};
        String why = AppLock.blockReason(this, r, kind);
        if (why != null) { showBlock(r, why, kind[0]); return; }
        if (AppLock.activeToday(r) && r.limit > 0) {
            long left = AppLock.remainMs(this, r);
            if (left <= 60000 && !cur.equals(warnedPkg)) {
                warnedPkg = cur;
                Toast.makeText(this, "「" + r.label + "」今天还能用 1 分钟", Toast.LENGTH_LONG).show();
            }
        }
    }

    int dp(float v) { return U.dp(this, v); }

    void showBlock(AppLock.Rule r, String why, int kind) {
        if (blockShown) return;
        blockPkg = r.pkg;
        block = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) { if (e.getAction() == KeyEvent.ACTION_UP) goHome(); return true; }
                return super.dispatchKeyEvent(e);
            }
        };
        block.setBackgroundColor(U.DARK);
        block.addView(U.bgView(this, true), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = U.col(this); col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(72), dp(24), dp(32));
        block.addView(col, new FrameLayout.LayoutParams(-1, -1));
        blockHome(col, r, why, kind);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try { wm.addView(block, p); blockShown = true; } catch (Exception e) { blockShown = false; }
    }

    void blockHome(LinearLayout col, AppLock.Rule r, String why, int kind) {
        col.removeAllViews();
        ImageView iv = new ImageView(this);
        try { PackageManager pmg = getPackageManager(); ApplicationInfo ai = pmg.getApplicationInfo(r.pkg, 0); iv.setImageDrawable(pmg.getApplicationIcon(ai)); } catch (Exception e) { }
        col.addView(iv, U.lp(dp(72), dp(72)));
        TextView name = U.tv(this, r.label, 15, U.DSUB, true); name.setGravity(Gravity.CENTER);
        col.addView(name, U.m(U.full(), this, 0, 12, 0, 0));
        TextView big = U.tv(this, "应用锁开启", 32, U.WHITE, true); big.setGravity(Gravity.CENTER);
        col.addView(big, U.m(U.full(), this, 0, 24, 0, 0));
        TextView w = U.tv(this, why + "，不能再用喽～", 16, U.WHITE, false); w.setGravity(Gravity.CENTER); w.setLineSpacing(0, 1.3f);
        col.addView(w, U.m(U.full(), this, 0, 12, 0, 0));
        TextView q = U.tv(this, QUOTES[(int) (System.currentTimeMillis() / 60000 % QUOTES.length)], 14, U.DSUB, false); q.setGravity(Gravity.CENTER);
        col.addView(q, U.m(U.full(), this, 0, 8, 0, 0));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));
        if (kind == 1) {
            int left = AppLock.bonusLeft(this, r.pkg);
            if (left > 0) {
                TextView b = U.btn(this, "背单词换时间：答对 " + AppLock.BONUS_Q + " 题，多用 " + AppLock.BONUS_MIN + " 分钟", 0x26FFFFFF, U.WHITE, v -> blockStudy(col, r, why, kind));
                b.setBackground(U.glass(this, true, 16));
                col.addView(b, U.full());
                TextView t = U.tv(this, "今天还能换 " + left + " 次", 12, U.DSUB, false); t.setGravity(Gravity.CENTER);
                col.addView(t, U.m(U.full(), this, 0, 8, 0, 16));
            } else {
                TextView t = U.tv(this, "今天背单词换时间的 " + AppLock.BONUS_MAX + " 次机会已用完", 12, U.DSUB, false); t.setGravity(Gravity.CENTER);
                col.addView(t, U.m(U.full(), this, 0, 0, 0, 16));
            }
        }
        col.addView(U.gbtn(this, "回到桌面", v -> goHome()), U.full());
    }

    void blockStudy(LinearLayout col, AppLock.Rule r, String why, int kind) {
        col.removeAllViews(); col.setPadding(dp(20), dp(48), dp(20), dp(24));
        final int[] ok = {0};
        TextView head = U.tv(this, "答对 0/" + AppLock.BONUS_Q + " 题 · 换 " + AppLock.BONUS_MIN + " 分钟「" + r.label + "」", 15, U.WHITE, true);
        head.setGravity(Gravity.CENTER);
        col.addView(head, U.m(U.full(), this, 0, 0, 0, 16));
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        final StudyPanel[] ref = new StudyPanel[1];
        ref[0] = new StudyPanel(this, true, new StudyPanel.L() {
            @Override public void onViewed() { }
            @Override public void onAnswer(int m, boolean right) {
                if (right) ok[0]++;
                head.setText("答对 " + ok[0] + "/" + AppLock.BONUS_Q + " 题 · 换 " + AppLock.BONUS_MIN + " 分钟「" + r.label + "」");
                if (ok[0] >= AppLock.BONUS_Q) {
                    ref[0].stop();
                    AppLock.addBonus(GuardService.this, r.pkg);
                    h.postDelayed(() -> {
                        hideBlock();
                        Toast.makeText(GuardService.this, "已解锁「" + r.label + "」" + AppLock.BONUS_MIN + " 分钟", Toast.LENGTH_LONG).show();
                    }, 900);
                }
            }
        });
        ref[0].setHeader("文字题 · 看词选义");
        sv.addView(ref[0]);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(U.btn(this, "不换了，回到桌面", 0x00000000, U.DSUB, v -> { ref[0].stop(); goHome(); }), U.m(U.full(), this, 0, 12, 0, 0));
        ref[0].start(StudyPanel.TEXT);
    }

    void goHome() { hideBlock(); performGlobalAction(GLOBAL_ACTION_HOME); }

    void hideBlock() {
        if (blockShown) { try { wm.removeView(block); } catch (Exception e) { } }
        blockShown = false; block = null;
    }

    void recordIssue(RuntimeException ex) {
        lastIssue = ex.getClass().getSimpleName();
        android.util.Log.e("WordLockGuard", "Accessibility callback failed", ex);
        hideBlock();
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        h.removeCallbacksAndMessages(null); hideBlock();
        if (inst == this) inst = null;
        return super.onUnbind(intent);
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (h != null) h.removeCallbacksAndMessages(null);
        hideBlock();
        if (inst == this) inst = null;
        super.onDestroy();
    }
}
