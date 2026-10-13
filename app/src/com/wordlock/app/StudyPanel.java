package com.wordlock.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Word card. LEARN = browse (no grading), TEXT = word -> meaning (A-D), PIC = picture -> word (A-D). */
public class StudyPanel extends LinearLayout {
    public static final int LEARN = 0, TEXT = 1, PIC = 2;
    public interface L { void onViewed(); void onAnswer(int mode, boolean ok); }

    static final LruCache<String, Bitmap> CACHE = new LruCache<>(40);
    static final String[] ABCD = {"A", "B", "C", "D"};
    final L l; final Handler h = new Handler(Looper.getMainLooper());
    final boolean dark; final int fg, sub, chip, accent;
    final TextView tag, star, word, phon, meaning; final ImageView img; final LinearLayout detail, actions;
    boolean favOnly; int mode; Words.W cur; boolean busy; String header = "";

    public StudyPanel(Context c, boolean dark, L l) {
        super(c); this.l = l; this.dark = dark; setOrientation(VERTICAL);
        fg = dark ? U.WHITE : U.TXT; sub = dark ? U.DSUB : U.SUB; chip = dark ? U.DCHIP : 0x99FFFFFF; accent = dark ? 0xFFFFB199 : U.ACC;
        int p = U.dp(c, 16); setPadding(p, p, p, p);
        setBackground(U.glass(c, dark, 22));

        LinearLayout top = U.row(c);
        tag = U.tv(c, "", 13, sub, true);
        top.addView(tag, U.lpw(1));
        star = U.tv(c, "", 13, sub, true); star.setGravity(Gravity.CENTER);
        star.setPadding(U.dp(c, 12), U.dp(c, 6), U.dp(c, 12), U.dp(c, 6));
        star.setOnClickListener(v -> { if (cur != null) { Words.get(getContext()).toggleFav(cur); updateStar(); } });
        top.addView(star);
        addView(top, U.full());

        img = new ImageView(c); img.setVisibility(GONE);
        LinearLayout.LayoutParams ip = U.lp(U.dp(c, 104), U.dp(c, 104)); ip.gravity = Gravity.CENTER_HORIZONTAL; ip.topMargin = U.dp(c, 8);
        addView(img, ip);
        word = U.tv(c, "", 34, fg, true); word.setGravity(Gravity.CENTER); word.setPadding(0, U.dp(c, 12), 0, 0);
        word.setOnClickListener(v -> speak()); addView(word, U.full());
        phon = U.tv(c, "", 15, sub, false); phon.setGravity(Gravity.CENTER); phon.setPadding(0, U.dp(c, 8), 0, 0);
        phon.setOnClickListener(v -> speak()); addView(phon, U.full());
        meaning = U.tv(c, "", 16, fg, false); meaning.setGravity(Gravity.CENTER); meaning.setLineSpacing(0, 1.25f);
        meaning.setPadding(0, U.dp(c, 16), 0, U.dp(c, 8)); addView(meaning, U.full());
        detail = U.col(c); addView(detail, U.full());
        actions = U.col(c); addView(actions, U.m(U.full(), c, 0, 8, 0, 0));
    }

    Words words() { return Words.get(getContext()); }
    void speak() { if (cur != null) Speaker.get(getContext()).speak(cur.w); }

    public void setHeader(String s) { header = s; tag.setText(s); }
    public void stop() { h.removeCallbacksAndMessages(null); }
    public void start(int mode) { stop(); this.mode = mode; next(); }

    void updateStar() {
        boolean f = cur != null && words().isFav(cur);
        star.setText(f ? "★ 已收藏" : "☆ 收藏");
        star.setTextColor(f ? 0xFFFFFFFF : sub);
        U.setBg(star, f ? 0xFFF5A623 : chip, 14);
    }

    Bitmap bitmap(String name) {
        Bitmap b = CACHE.get(name);
        if (b == null) { b = U.asset(getContext(), "img/" + name + ".png"); if (b != null) CACHE.put(name, b); }
        return b;
    }

    void showImage(boolean show) {
        Bitmap b = show && cur != null && !cur.img.isEmpty() ? bitmap(cur.img) : null;
        if (b == null) { img.setVisibility(GONE); return; }
        img.setImageBitmap(b); img.setVisibility(VISIBLE);
    }

    void showWord() {
        word.setText(cur.w);
        phon.setText(cur.p == null || cur.p.isEmpty() ? "🔊 点击发音" : "/" + cur.p + "/   🔊");
    }

    void hint(String s) { meaning.setText(s); meaning.setTextColor(sub); meaning.setTextSize(14); }
    void showMeaning() { meaning.setText(cur.m); meaning.setTextColor(fg); meaning.setTextSize(16); }

    void showDetail() {
        detail.removeAllViews();
        Context c = getContext();
        if (!cur.ph.isEmpty()) {
            detail.addView(U.tv(c, "常用词组", 13, accent, true), U.m(U.full(), c, 0, 12, 0, 6));
            for (String it : cur.ph.split("§")) {
                String[] a = it.split("\\|", 2); if (a.length < 2) continue;
                TextView t = U.tv(c, "", 15, fg, false);
                SpannableString ss = new SpannableString(a[0] + "   " + a[1]);
                ss.setSpan(new StyleSpan(Typeface.BOLD), 0, a[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(sub), a[0].length(), ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                t.setText(ss); t.setPadding(0, U.dp(c, 4), 0, U.dp(c, 4));
                t.setOnClickListener(v -> Speaker.get(c).speak(a[0]));
                detail.addView(t, U.full());
            }
        }
        if (!cur.ex.isEmpty()) {
            detail.addView(U.tv(c, "例句 · 点击朗读", 13, accent, true), U.m(U.full(), c, 0, 12, 0, 6));
            for (String it : cur.ex.split("§")) {
                String[] a = it.split("\\|", 2); if (a.length < 2) continue;
                TextView en = U.tv(c, "", 15, fg, false); en.setText(highlight(a[0], cur.w)); en.setLineSpacing(0, 1.2f);
                TextView zh = U.tv(c, a[1], 13, sub, false); zh.setPadding(0, U.dp(c, 4), 0, 0);
                LinearLayout box = U.col(c); box.setPadding(U.dp(c, 12), U.dp(c, 10), U.dp(c, 12), U.dp(c, 10));
                box.setBackground(U.round(chip, U.dp(c, 12)));
                box.addView(en, U.full()); box.addView(zh, U.full());
                box.setOnClickListener(v -> Speaker.get(c).speak(a[0]));
                detail.addView(box, U.m(U.full(), c, 0, 0, 0, 8));
            }
        }
    }

    CharSequence highlight(String sentence, String w) {
        SpannableString ss = new SpannableString(sentence);
        String stem = w.length() > 4 ? w.substring(0, w.length() - (w.endsWith("e") || w.endsWith("y") ? 1 : 0)) : w;
        Matcher m = Pattern.compile("(?i)\\b" + Pattern.quote(stem) + "[a-z]*").matcher(sentence);
        while (m.find()) {
            ss.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(accent), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    void next() {
        busy = false; detail.removeAllViews(); actions.removeAllViews();
        if (mode == LEARN) nextLearn(); else if (mode == TEXT) nextText(); else nextPic();
        tag.setText(header); updateStar();
    }

    void nextLearn() {
        cur = words().nextLearn(favOnly);
        boolean fresh = cur.isNew;
        words().introduce(cur); l.onViewed();
        if (header.isEmpty()) tag.setText(fresh ? "新词" : "复习");
        showWord(); showImage(true); showMeaning(); showDetail();
        actions.addView(U.gbtn(getContext(), "下一个", v -> next()), U.full());
        speak();
    }

    void nextText() {
        cur = words().nextText(favOnly);
        showWord(); showImage(false); hint("选择正确的中文释义");
        List<String> opts = words().textOptions(cur);
        addOptions(opts, cur.shortM(), 15);
        speak();
    }

    void nextPic() {
        cur = words().nextPicture(favOnly);
        showImage(true); word.setText("?"); phon.setText(" "); hint("图片对应哪个英文单词？");
        addOptions(words().wordOptions(cur), cur.w, 17);
    }

    void addOptions(List<String> opts, String right, float sp) {
        final ArrayList<LinearLayout> bs = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) {
            String o = opts.get(i);
            LinearLayout b = option(ABCD[i], o, sp);
            b.setOnClickListener(v -> answer(b, o, right, bs));
            bs.add(b); actions.addView(b, U.m(U.full(), getContext(), 0, 0, 0, 8));
        }
    }

    LinearLayout option(String letter, String text, float sp) {
        Context c = getContext();
        LinearLayout r = U.row(c); r.setMinimumHeight(U.dp(c, 52)); r.setPadding(U.dp(c, 12), U.dp(c, 10), U.dp(c, 12), U.dp(c, 10));
        TextView badge = U.tv(c, letter, 13, fg, true); badge.setGravity(Gravity.CENTER);
        badge.setBackground(U.round(dark ? 0x26FFFFFF : 0x14000000, U.dp(c, 12)));
        r.addView(badge, U.lp(U.dp(c, 24), U.dp(c, 24)));
        TextView t = U.tv(c, text, sp, fg, false); t.setLineSpacing(0, 1.15f);
        r.addView(t, U.m(U.lpw(1), c, 12, 0, 0, 0));
        r.setTag(text); U.setBg(r, chip, 14); r.setClickable(true);
        return r;
    }

    void paint(LinearLayout r, int bg) {
        U.setBg(r, bg, 14);
        for (int i = 0; i < r.getChildCount(); i++) { View v = r.getChildAt(i); if (v instanceof TextView) ((TextView) v).setTextColor(U.WHITE); }
    }

    void answer(LinearLayout chosen, String o, String right, List<LinearLayout> bs) {
        if (busy) return; busy = true;
        boolean ok = o.equals(right);
        for (LinearLayout b : bs) { b.setClickable(false); if (right.equals(b.getTag())) paint(b, U.GREEN); }
        if (!ok) paint(chosen, U.ACC);
        showWord(); showMeaning(); showImage(true); showDetail(); speak();
        words().grade(cur, ok);
        l.onAnswer(mode, ok);
        tag.setText(header + (ok ? "   ✓ 答对了" : "   ✗ 答错了"));
        if (ok) h.postDelayed(this::next, 1200);
        else actions.addView(U.gbtn(getContext(), "下一题", v -> next()), U.full());
    }
}
