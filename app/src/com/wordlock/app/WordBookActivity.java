package com.wordlock.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WordBookActivity extends Activity {
    StudyPanel panel; TextView summary; final TextView[] tabs = new TextView[3]; int mode; boolean fav;
    int tOk, tBad, pOk, pBad, views;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        mode = getIntent().getIntExtra("mode", 0); fav = getIntent().getBooleanExtra("fav", false);
        FrameLayout root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this); sv.setClipToPadding(false); sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = U.col(this); sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        android.view.View topFade = new android.view.View(this); topFade.setBackground(U.fade(0xF2F7F4F0, true));
        root.addView(topFade, new FrameLayout.LayoutParams(-1, U.dp(this, 40), Gravity.TOP));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            page.setPadding(U.dp(this, 20), ins.getSystemWindowInsetTop() + U.dp(this, 8), U.dp(this, 20), ins.getSystemWindowInsetBottom() + U.dp(this, 24));
            FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) topFade.getLayoutParams();
            tp.height = ins.getSystemWindowInsetTop() + U.dp(this, 20); topFade.setLayoutParams(tp);
            return ins.consumeSystemWindowInsets();
        });

        LinearLayout bar = U.row(this);
        TextView back = U.tv(this, "‹", 30, U.TXT, false); back.setGravity(Gravity.CENTER); back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back, U.lp(U.dp(this, 40), U.dp(this, 48)));
        bar.addView(U.tv(this, fav ? "练习收藏的词" : "自由学习", 20, U.TXT, true), U.lpw(1));
        TextView again = U.tv(this, "换一组", 14, U.ACC, true); again.setPadding(U.dp(this, 12), U.dp(this, 8), U.dp(this, 12), U.dp(this, 8));
        U.setBg(again, 0x1AE5533D, 14); again.setClickable(true);
        again.setOnClickListener(v -> { Words.get(this).newSession(fav); views = tOk = tBad = pOk = pBad = 0; setMode(mode); refresh(); });
        bar.addView(again);
        page.addView(bar, U.m(U.full(), this, -8, 0, 0, 8));

        LinearLayout seg = U.row(this); seg.setPadding(U.dp(this, 4), U.dp(this, 4), U.dp(this, 4), U.dp(this, 4));
        seg.setBackground(U.glass(this, false, 16));
        String[] names = {"学习", "文字题", "看图题"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs[i] = U.tv(this, names[i], 14, U.TXT, true); tabs[i].setGravity(Gravity.CENTER); tabs[i].setMinHeight(U.dp(this, 40));
            tabs[i].setClickable(true); tabs[i].setOnClickListener(v -> setMode(k));
            seg.addView(tabs[i], U.lpw(1));
        }
        page.addView(seg, U.m(U.full(), this, 0, 0, 0, 16));

        panel = new StudyPanel(this, false, new StudyPanel.L() {
            @Override public void onViewed() { views++; refresh(); }
            @Override public void onAnswer(int m, boolean ok) {
                if (m == StudyPanel.PIC) { if (ok) pOk++; else pBad++; } else { if (ok) tOk++; else tBad++; }
                refresh();
            }
        });
        panel.favOnly = fav;
        Words.get(this).newSession(fav);
        page.addView(panel, U.full());
        summary = U.tv(this, "", 12, U.SUB, false); summary.setGravity(Gravity.CENTER); summary.setLineSpacing(0, 1.3f);
        page.addView(summary, U.m(U.full(), this, 0, 16, 0, 0));
        setContentView(root);
        setMode(mode);
    }

    void setMode(int m) {
        mode = m;
        for (int i = 0; i < 3; i++) {
            if (i == m) U.setBg(tabs[i], U.WHITE, 12); else tabs[i].setBackground(null);
            tabs[i].setTextColor(i == m ? U.ACC : U.SUB);
        }
        panel.setHeader(m == StudyPanel.LEARN ? "" : m == StudyPanel.TEXT ? "文字题 · 看词选义" : "看图题 · 看图选词");
        panel.start(m);
    }

    void refresh() {
        Words w = Words.get(this);
        int t = tOk + tBad, p = pOk + pBad;
        int sw = w.sessionWrong();
        summary.setText("本组 " + w.sessionWords().size() + " 个单词" + (sw > 0 ? "（含错题 " + sw + " 个）" : "") + "，学习和答题都在这组里\n本次  浏览 " + views + " · 文字题 " + tOk + "/" + t + " · 看图题 " + pOk + "/" + p
                + "\n已学 " + w.seenCount() + " / " + w.total() + " · 待复习 " + w.dueCount() + " · 已掌握 " + w.masteredCount());
    }
}
