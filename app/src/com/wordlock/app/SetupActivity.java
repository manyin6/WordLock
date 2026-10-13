package com.wordlock.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** First-run gate: every permission must be on before the app can be used. An arrow points at the next step. */
public class SetupActivity extends Activity {
    LinearLayout page; ScrollView sv; int insetTop, insetBot; View focus; boolean agreeTick;
    final Handler h = new Handler(Looper.getMainLooper());
    boolean resumed, watching; long settleUntil; String lastState;
    final android.database.ContentObserver observer = new android.database.ContentObserver(h) {
        @Override public void onChange(boolean selfChange) { if (resumed) refreshIfChanged(); }
    };
    final Runnable recheck = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshIfChanged();
            if (android.os.SystemClock.uptimeMillis() < settleUntil) h.postDelayed(this, 500);
        }
    };
    String permissionState() {
        StringBuilder b = new StringBuilder();
        for (Perms.P p : Perms.all(this)) b.append(p.id).append('=').append(Perms.ok(this, p.id)).append(';');
        b.append("connected=").append(GuardService.inst != null);
        return b.toString();
    }
    void refreshIfChanged() {
        String now = permissionState();
        if (!now.equals(lastState)) { render(); lastState = now; }
    }

    int dp(float v) { return U.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        sv = new ScrollView(this); sv.setClipToPadding(false); sv.setVerticalScrollBarEnabled(false);
        page = U.col(this); sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            insetTop = ins.getSystemWindowInsetTop(); insetBot = ins.getSystemWindowInsetBottom(); render();
            return ins.consumeSystemWindowInsets();
        });
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; lastState = null; refreshIfChanged();
        settleUntil = android.os.SystemClock.uptimeMillis() + 10000; h.postDelayed(recheck, 250);
        try {
            getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
            watching = true;
            getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ACCESSIBILITY_ENABLED), false, observer);
        } catch (RuntimeException ignored) { }
    }
    @Override protected void onPause() {
        resumed = false; h.removeCallbacksAndMessages(null);
        if (watching) { getContentResolver().unregisterContentObserver(observer); watching = false; }
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) { render(); }
    @Override public void onBackPressed() { finishAffinity(); }

    void link(android.text.SpannableStringBuilder sb, String text, int type) {
        int st = sb.length(); sb.append(text);
        sb.setSpan(new android.text.style.ClickableSpan() {
            @Override public void onClick(View w) { startActivity(new android.content.Intent(SetupActivity.this, DocActivity.class).putExtra("type", type)); }
            @Override public void updateDrawState(android.text.TextPaint ds) { ds.setColor(U.ACC); ds.setUnderlineText(false); }
        }, st, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    void render() {
        if (page == null) return;
        page.removeAllViews();
        page.setPadding(dp(20), insetTop + dp(24), dp(20), insetBot + dp(24));
        List<Perms.P> ps = Perms.all(this);
        int done = 0; for (Perms.P p : ps) if (Perms.ok(this, p.id)) done++;
        boolean all = done == ps.size();

        page.addView(U.tv(this, "欢迎使用单词锁 👋", 28, U.TXT, true), U.m(U.full(), this, 4, 8, 0, 0));
        TextView sub = U.tv(this, "锁机和应用锁要靠下面这些权限才能生效。\n跟着箭头一项一项开启，全部开启后才能进入。", 14, U.SUB, false);
        sub.setLineSpacing(0, 1.3f);
        page.addView(sub, U.m(U.full(), this, 4, 8, 0, 16));

        // progress
        LinearLayout pr = U.row(this);
        pr.addView(U.tv(this, "已开启 " + done + " / " + ps.size(), 13, all ? U.GREEN : U.ACC, true), U.lpw(1));
        page.addView(pr, U.m(U.full(), this, 4, 0, 4, 8));
        FrameLayout bar = new FrameLayout(this); bar.setBackground(U.round(0x14000000, dp(4)));
        View fill = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{U.ACC2, all ? U.GREEN : U.ACC}); g.setCornerRadius(dp(4));
        fill.setBackground(g);
        bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
        page.addView(bar, U.m(U.lp(-1, dp(8)), this, 0, 0, 0, 20));
        final int fd = done, fn = ps.size();
        bar.post(() -> { FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams(); lp.width = bar.getWidth() * fd / fn; fill.setLayoutParams(lp); });

        focus = null; boolean arrowUsed = false; int n = 0;
        for (Perms.P p : ps) {
            n++;
            boolean ok = Perms.ok(this, p.id), current = !ok && !arrowUsed;
            if (current) arrowUsed = true;
            LinearLayout card = U.col(this);
            GradientDrawable bg = U.glass(this, false, 20);
            if (current) bg.setStroke(dp(2), U.ACC);
            card.setBackground(bg); card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout r = U.row(this);
            TextView num = U.tv(this, ok ? "✓" : String.valueOf(n), 13, U.WHITE, true); num.setGravity(Gravity.CENTER);
            num.setBackground(U.round(ok ? U.GREEN : current ? U.ACC : 0xFFC7C2BC, dp(13)));
            r.addView(num, U.lp(dp(26), dp(26)));
            LinearLayout t = U.col(this);
            t.addView(U.tv(this, p.name, 16, U.TXT, true));
            TextView d = U.tv(this, p.desc, 12, U.SUB, false); t.addView(d, U.m(U.full(), this, 0, 4, 0, 0));
            r.addView(t, U.m(U.lpw(1), this, 12, 0, 8, 0));
            if (current) {
                ArrowView av = new ArrowView(this, 2, U.ACC);
                r.addView(av, U.m(U.lp(dp(30), dp(22)), this, 0, 0, 6, 0));
            }
            TextView btn = U.tv(this, ok ? "已开启" : "去开启", 14, ok ? U.GREEN : current ? U.WHITE : U.ACC, true);
            btn.setGravity(Gravity.CENTER); btn.setMinHeight(dp(36)); btn.setPadding(dp(14), 0, dp(14), 0);
            U.setBg(btn, ok ? 0x1A22A06B : current ? U.ACC : 0x1AE5533D, 18);
            if (!ok) { btn.setClickable(true); btn.setOnClickListener(v -> Perms.open(this, p.id)); }
            r.addView(btn);
            card.addView(r, U.full());
            if (p.id.equals("acc")) {
                TextView state = U.tv(this, ok ? (GuardService.inst != null ? "服务已连接" : "已授权，等待系统连接服务…") : "未检测到授权；请核对系统开关", 12, U.SUB, false);
                card.addView(state, U.m(U.full(), this, 0, 8, 0, 0));
                card.addView(U.btn(this, "检查无障碍状态", 0x14000000, U.TXT, v -> Perms.accStatus(this)), U.m(U.full(), this, 0, 8, 0, 0));
            }
            if (current) {
                TextView how = U.tv(this, "👉 怎么开：" + p.how, 13, U.TXT, false); how.setLineSpacing(0, 1.3f);
                how.setPadding(dp(12), dp(10), dp(12), dp(10)); how.setBackground(U.round(0x14E5533D, dp(12)));
                card.addView(how, U.m(U.full(), this, 0, 12, 0, 0));
                if (p.id.equals("acc")) {
                    TextView info = U.btn(this, "打开「应用信息」（允许受限制的设置）", 0x99FFFFFF, U.TXT, v -> Perms.appInfo(this));
                    info.setTextSize(13); card.addView(info, U.m(U.full(), this, 0, 8, 0, 0));
                }
                card.setClickable(true); card.setOnClickListener(v -> Perms.open(this, p.id));
                focus = card;
            }
            page.addView(card, U.m(U.full(), this, 0, 0, 0, 10));
        }

        // terms agreement (must accept once)
        boolean agreed = Prefs.getInt(this, "termsAgreed", 0) >= Docs.VERSION || agreeTick;
        LinearLayout ag = U.row(this); ag.setPadding(dp(4), dp(8), dp(4), dp(4));
        TextView box = U.tv(this, agreed ? "☑" : "☐", 20, agreed ? U.ACC : U.SUB, false);
        box.setClickable(true); box.setOnClickListener(v -> { agreeTick = !agreed; if (agreed) Prefs.setInt(this, "termsAgreed", 0); render(); });
        ag.addView(box, U.lp(dp(32), dp(32)));
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("我已阅读并同意");
        link(sb, "《用户协议》", 0); sb.append("和"); link(sb, "《隐私政策》", 1);
        TextView link = U.tv(this, "", 13, U.TXT, false); link.setText(sb);
        link.setMovementMethod(android.text.method.LinkMovementMethod.getInstance()); link.setHighlightColor(0);
        ag.addView(link, U.lpw(1));
        page.addView(ag, U.m(U.full(), this, 0, 6, 0, 0));
        boolean ready = all && agreed;

        TextView go = U.gbtn(this, !all ? "还有 " + (ps.size() - done) + " 项未开启" : !agreed ? "请先同意用户协议" : "全部开启，进入单词锁", v -> {
            if (Perms.missing(this) > 0 || !(Prefs.getInt(this, "termsAgreed", 0) >= Docs.VERSION || agreeTick)) { render(); return; }
            Prefs.setInt(this, "termsAgreed", Docs.VERSION);
            Prefs.setInt(this, "setupDone", 1); finish();
        });
        if (!ready) { go.setBackground(U.round(0xFFC7C2BC, dp(16))); go.setElevation(0); }
        page.addView(go, U.m(U.full(), this, 0, 14, 0, 0));
        TextView foot = U.tv(this, "单词锁只用无障碍判断前台是哪个应用，不读取屏幕内容，不联网。", 12, U.SUB, false);
        foot.setGravity(Gravity.CENTER); foot.setLineSpacing(0, 1.25f);
        page.addView(foot, U.m(U.full(), this, 0, 12, 0, 0));

        if (focus != null) { final View f = focus; sv.post(() -> sv.smoothScrollTo(0, Math.max(0, f.getTop() - dp(120)))); }
    }
}
