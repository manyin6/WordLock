package com.wordlock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

/**
 * Guide layer, drawn entirely by hand (no child views, so nothing can be mis-measured):
 * dim the screen, cut a hole around the live target, point at it with a bouncing arrow,
 * and draw a compact card with title, text, "跳过引导" and the action button.
 * Sits at a very high Z so elevated views (bottom nav) are dimmed too.
 */
public class CoachView extends View {
    public interface L { void onTapTarget(); void onNext(); void onSkip(); View target(); }
    final L l;
    final RectF hole = new RectF(), card = new RectF(), nextR = new RectF(), skipR = new RectF();
    boolean hasHole;
    final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            arrowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint titleP = new TextPaint(Paint.ANTI_ALIAS_FLAG), bodyP = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            countP = new TextPaint(Paint.ANTI_ALIAS_FLAG), skipP = new TextPaint(Paint.ANTI_ALIAS_FLAG), btnP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    String title = "", body = "", count = "", next = "下一步";
    float bounce; ValueAnimator anim;
    final int[] a = new int[2], b = new int[2];

    final ViewTreeObserver.OnPreDrawListener pre = () -> { if (getVisibility() == VISIBLE && updateHole()) invalidate(); return true; };

    public CoachView(Context c, L l) {
        super(c); this.l = l; setClickable(true);
        setElevation(U.dp(c, 100)); setTranslationZ(U.dp(c, 100));
        dim.setColor(0xB8000000);
        ring.setStyle(Paint.Style.STROKE); ring.setColor(0xFFFFFFFF); ring.setStrokeWidth(U.dp(c, 2.5f));
        arrowP.setColor(0xFFFFC24D);
        Typeface bold = Typeface.create("sans-serif-medium", Typeface.BOLD);
        titleP.setColor(U.TXT); titleP.setTextSize(sp(17)); titleP.setTypeface(bold);
        bodyP.setColor(U.SUB); bodyP.setTextSize(sp(14));
        countP.setColor(U.SUB); countP.setTextSize(sp(12)); countP.setTypeface(bold);
        skipP.setColor(U.SUB); skipP.setTextSize(sp(14)); skipP.setTypeface(bold);
        btnP.setColor(U.WHITE); btnP.setTextSize(sp(15)); btnP.setTypeface(bold);
        setVisibility(GONE);
    }

    float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
    int dp(float v) { return U.dp(getContext(), v); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(pre);
        anim = ValueAnimator.ofFloat(0, 1); anim.setDuration(650); anim.setRepeatMode(ValueAnimator.REVERSE); anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(x -> { bounce = (float) x.getAnimatedValue(); if (getVisibility() == VISIBLE && hasHole) invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(pre);
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    public void show(String t, String bodyText, String step, String nextLabel) {
        title = t; body = bodyText; count = step; next = nextLabel;
        setVisibility(VISIBLE); bringToFront();
        updateHole(); invalidate();
    }

    /** true when the highlighted rect changed */
    boolean updateHole() {
        View t = l.target();
        boolean ok = t != null && t.isAttachedToWindow() && t.isShown() && t.getWidth() > 0 && t.getHeight() > 0 && getWidth() > 0;
        float L = 0, T = 0, R = 0, B = 0;
        if (ok) {
            t.getLocationInWindow(a); getLocationInWindow(b);
            int pad = dp(6);
            L = Math.max(dp(4), a[0] - b[0] - pad); R = Math.min(getWidth() - dp(4), a[0] - b[0] + t.getWidth() + pad);
            T = Math.max(0, a[1] - b[1] - pad); B = Math.min(getHeight(), a[1] - b[1] + t.getHeight() + pad);
            ok = B - T > dp(8) && R - L > dp(8);
        }
        boolean changed = ok != hasHole || (ok && (hole.left != L || hole.top != T || hole.right != R || hole.bottom != B));
        hasHole = ok; if (ok) hole.set(L, T, R, B);
        return changed;
    }

    StaticLayout text(String s, TextPaint p, int w, float spacing) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, w)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, spacing).setIncludePad(false).build();
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        int top = dp(8), bot = dp(8);
        WindowInsets ins = getRootWindowInsets();
        if (ins != null) { top += ins.getSystemWindowInsetTop(); bot += ins.getSystemWindowInsetBottom(); }

        // 1. dim with a hole
        Path p = new Path(); p.setFillType(Path.FillType.EVEN_ODD);
        p.addRect(0, 0, W, H, Path.Direction.CW);
        float r = dp(16);
        if (hasHole) p.addRoundRect(hole, r, r, Path.Direction.CW);
        c.drawPath(p, dim);
        if (hasHole) c.drawRoundRect(hole, r, r, ring);

        // 2. measure the card
        int m = dp(20), pad = dp(16), cw = W - 2 * m, inner = cw - 2 * pad;
        float countW = countP.measureText(count);
        StaticLayout tl = text(title, titleP, (int) (inner - countW - dp(12)), 1f);
        StaticLayout bl = text(body, bodyP, inner, 1.3f);
        int btnH = dp(40);
        int ch = pad + tl.getHeight() + dp(8) + bl.getHeight() + dp(14) + btnH + dp(14);

        // 3. place arrow + card next to the hole, on the side with more room
        int aw = dp(30), ah = dp(46), gap = dp(6);
        float cy;
        if (!hasHole) cy = (H - ch) / 2f;
        else {
            float spaceBelow = H - bot - hole.bottom, spaceAbove = hole.top - top;
            boolean below = spaceBelow >= ah + ch + gap * 2 || spaceBelow > spaceAbove;
            float ax = Math.max(m, Math.min(W - m - aw, hole.centerX() - aw / 2f));
            float ay = below ? hole.bottom + gap : hole.top - gap - ah;
            cy = below ? ay + ah + gap : ay - gap - ch;
            cy = Math.max(top, Math.min(H - bot - ch, cy));
            drawArrow(c, ax, ay, aw, ah, below);
        }
        card.set(m, cy, m + cw, cy + ch);

        // 4. card
        fill.setColor(0x33000000); c.drawRoundRect(card.left, card.top + dp(3), card.right, card.bottom + dp(3), dp(20), dp(20), fill);
        fill.setColor(0xFFFFFFFF); c.drawRoundRect(card, dp(20), dp(20), fill);
        float x = card.left + pad, y = card.top + pad;
        c.save(); c.translate(x, y); tl.draw(c); c.restore();
        c.drawText(count, card.right - pad - countW, y - countP.ascent(), countP);
        y += tl.getHeight() + dp(8);
        c.save(); c.translate(x, y); bl.draw(c); c.restore();
        y += bl.getHeight() + dp(14);

        float bw = btnP.measureText(next) + dp(40);
        nextR.set(card.right - pad - bw, y, card.right - pad, y + btnH);
        fill.setColor(U.ACC); c.drawRoundRect(nextR, dp(14), dp(14), fill);
        Paint.FontMetrics fm = btnP.getFontMetrics();
        c.drawText(next, nextR.centerX() - btnP.measureText(next) / 2, nextR.centerY() - (fm.ascent + fm.descent) / 2, btnP);

        String skip = "跳过引导"; float sw = skipP.measureText(skip);
        skipR.set(x - dp(8), y, x + sw + dp(8), y + btnH);
        Paint.FontMetrics sfm = skipP.getFontMetrics();
        c.drawText(skip, x, skipR.centerY() - (sfm.ascent + sfm.descent) / 2, skipP);
    }

    /** up = arrow points up (card below the target) */
    void drawArrow(Canvas c, float x, float y, float w, float h, boolean up) {
        float d = h * 0.16f * bounce, len = h * 0.84f, head = len * 0.46f, shaft = w * 0.34f;
        c.save();
        if (!up) c.translate(x, y + h - len - h * 0.16f + d);
        else { c.translate(x, y + h * 0.16f - d + len); c.scale(1, -1); }
        Path a = new Path();
        a.moveTo(w / 2 - shaft / 2, 0); a.lineTo(w / 2 + shaft / 2, 0); a.lineTo(w / 2 + shaft / 2, len - head);
        a.lineTo(w, len - head); a.lineTo(w / 2, len); a.lineTo(0, len - head); a.lineTo(w / 2 - shaft / 2, len - head); a.close();
        c.drawPath(a, arrowP);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        if (nextR.contains(x, y)) l.onNext();
        else if (skipR.contains(x, y)) l.onSkip();
        else if (hasHole && hole.contains(x, y) && !card.contains(x, y)) l.onTapTarget();
        return true;
    }
}
