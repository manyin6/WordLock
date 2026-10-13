package com.wordlock.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows one of Docs (用户协议 / 隐私政策 / 权限清单 / 第三方SDK清单 / 收集信息清单). */
public class DocActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.edgeToEdge(this);
        int type = getIntent().getIntExtra("type", 0);
        FrameLayout root = new FrameLayout(this);
        root.addView(U.bgView(this, false), new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this); sv.setClipToPadding(false); sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = U.col(this); sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            page.setPadding(U.dp(this, 20), ins.getSystemWindowInsetTop() + U.dp(this, 8), U.dp(this, 20), ins.getSystemWindowInsetBottom() + U.dp(this, 24));
            return ins.consumeSystemWindowInsets();
        });
        LinearLayout bar = U.row(this);
        TextView back = U.tv(this, "‹", 30, U.TXT, false); back.setGravity(Gravity.CENTER); back.setClickable(true); back.setOnClickListener(v -> finish());
        bar.addView(back, U.lp(U.dp(this, 40), U.dp(this, 48)));
        bar.addView(U.tv(this, Docs.TITLES[type], 20, U.TXT, true), U.lpw(1));
        page.addView(bar, U.m(U.full(), this, -8, 0, 0, 16));
        for (String[] s : Docs.get(type)) {
            LinearLayout c = U.col(this); c.setBackground(U.glass(this, false, 22)); c.setPadding(U.dp(this, 16), U.dp(this, 16), U.dp(this, 16), U.dp(this, 16));
            if (!s[0].isEmpty()) c.addView(U.tv(this, s[0], 16, s[0].contains("重点") ? U.ACC : U.TXT, true), U.m(U.full(), this, 0, 0, 0, 10));
            TextView t = U.tv(this, "", 14, U.TXT, false); t.setLineSpacing(0, 1.45f); t.setTextIsSelectable(true);
            t.setText(styled(s[1]));
            c.addView(t, U.full());
            page.addView(c, U.m(U.full(), this, 0, 0, 0, 12));
        }
        setContentView(root);
    }

    static CharSequence styled(String body) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = body.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i]; int start = sb.length();
            boolean em = l.startsWith("▲"); if (em) l = l.substring(1);
            int mark = l.indexOf('▲'); // inline emphasis: rest of line after ▲
            if (!em && mark >= 0) { sb.append(l, 0, mark); start = sb.length(); l = l.substring(mark + 1); em = true; }
            sb.append(l);
            if (em) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(U.ACC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb;
    }
}
