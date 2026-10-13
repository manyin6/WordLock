package com.wordlock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Bouncing guide arrow. dir: 0 = down, 1 = up, 2 = right, 3 = left. */
public class ArrowView extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), sh = new Paint(Paint.ANTI_ALIAS_FLAG);
    int dir; float off; ValueAnimator anim;

    public ArrowView(Context c, int dir, int color) {
        super(c); this.dir = dir;
        p.setColor(color); p.setStyle(Paint.Style.FILL);
        sh.setColor(0x33000000); sh.setStyle(Paint.Style.FILL);
    }

    public void setDir(int d) { dir = d; invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0, 1); anim.setDuration(700); anim.setRepeatMode(ValueAnimator.REVERSE); anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> { off = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), vertical = (dir < 2) ? 1 : 0;
        float len = vertical == 1 ? h : w, thick = vertical == 1 ? w : h, bounce = len * 0.18f * off;
        c.save();
        // draw a down-pointing arrow in a (thick x len) box, then rotate
        c.translate(w / 2, h / 2);
        c.rotate(dir == 0 ? 0 : dir == 1 ? 180 : dir == 2 ? -90 : 90);
        c.translate(-thick / 2, -len / 2 + bounce - len * 0.09f);
        float L = len * 0.82f, sw = thick * 0.30f, head = L * 0.45f;
        Path a = new Path();
        a.moveTo(thick / 2 - sw / 2, 0); a.lineTo(thick / 2 + sw / 2, 0); a.lineTo(thick / 2 + sw / 2, L - head);
        a.lineTo(thick * 0.96f, L - head); a.lineTo(thick / 2, L); a.lineTo(thick * 0.04f, L - head);
        a.lineTo(thick / 2 - sw / 2, L - head); a.close();
        c.save(); c.translate(0, U.dp(getContext(), 2)); c.drawPath(a, sh); c.restore();
        c.drawPath(a, p);
        c.restore();
    }
}
