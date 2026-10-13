package com.wordlock.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DoneActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        boolean early = getIntent().getBooleanExtra("early", false);
        int[] r = getIntent().getIntArrayExtra("r");
        if (r == null || r.length < 8) r = new int[8];
        int min = r[0], views = r[1], tOk = r[2], tBad = r[3], pOk = r[4], pBad = r[5], streak = r[6], today = r[7];
        int ans = tOk + tBad + pOk + pBad, acc = ans > 0 ? (tOk + pOk) * 100 / ans : 0;

        FrameLayout root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this); LinearLayout page = U.col(this); sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            page.setPadding(U.dp(this, 20), ins.getSystemWindowInsetTop() + U.dp(this, 40), U.dp(this, 20), ins.getSystemWindowInsetBottom() + U.dp(this, 24));
            return ins.consumeSystemWindowInsets();
        });

        page.addView(U.tv(this, early ? "🔓" : "🎉", 56, U.TXT, false), U.m(U.full(), this, 4, 0, 0, 16));
        page.addView(U.tv(this, early ? "答题通过，提前解锁" : "恭喜完成本次专注", 28, U.TXT, true), U.m(U.full(), this, 4, 0, 0, 8));
        page.addView(U.tv(this, early ? "提前解锁不计入连续打卡" : "一步一步，成为更好的自己", 14, U.SUB, false), U.m(U.full(), this, 4, 0, 0, 24));

        LinearLayout card = U.col(this); card.setBackground(U.glass(this, false, 22));
        card.setPadding(U.dp(this, 16), U.dp(this, 20), U.dp(this, 16), U.dp(this, 20));
        LinearLayout r1 = U.row(this), r2 = U.row(this);
        r1.addView(stat(min + "", "专注 · 分钟"), U.lpw(1)); r1.addView(stat(acc + "%", "答题正确率"), U.lpw(1)); r1.addView(stat(streak + "", "连续打卡 · 天"), U.lpw(1));
        r2.addView(stat(views + "", "浏览单词"), U.lpw(1)); r2.addView(stat(tOk + "/" + (tOk + tBad), "文字题"), U.lpw(1)); r2.addView(stat(pOk + "/" + (pOk + pBad), "看图题"), U.lpw(1));
        card.addView(r1, U.full()); card.addView(U.divider(this, false, 0)); 
        ((LinearLayout.LayoutParams) card.getChildAt(1).getLayoutParams()).setMargins(0, U.dp(this, 16), 0, U.dp(this, 16));
        card.addView(r2, U.full());
        page.addView(card, U.full());
        TextView td = U.tv(this, "今日累计专注 " + today + " 分钟 · 正确率只统计文字题和看图题", 12, U.SUB, false);
        page.addView(td, U.m(U.full(), this, 4, 12, 0, 24));

        String share = "我在「单词锁」专注了 " + min + " 分钟，答题正确率 " + acc + "%，已连续打卡 " + streak + " 天 💪 一起来自律吧！";
        page.addView(U.gbtn(this, "分享给朋友", v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share);
            startActivity(Intent.createChooser(i, "分享打卡"));
        }), U.m(U.full(), this, 0, 0, 0, 12));
        page.addView(U.btn(this, "返回首页", 0x99FFFFFF, U.TXT, v -> { startActivity(new Intent(this, MainActivity.class)); finish(); }), U.full());
        setContentView(root);
    }

    View stat(String value, String label) {
        LinearLayout c = U.col(this);
        c.addView(U.tv(this, value, 24, U.TXT, true));
        c.addView(U.tv(this, label, 12, U.SUB, false), U.m(U.full(), this, 0, 6, 0, 0));
        return c;
    }
}
