package com.wordlock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn on a 24x24 grid so every tab icon shares the same optical size and stroke. */
public class IconView extends View {
    public static final int HOME = 0, PLAN = 1, STAR = 2, GEAR = 3, LOCK = 4;
    final int type; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean filled;

    public IconView(Context c, int type) {
        super(c); this.type = type;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void set(int color, boolean filled) { p.setColor(color); this.filled = filled; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save(); c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2); c.scale(s, s);
        p.setStrokeWidth(1.8f);
        Path path = new Path();
        switch (type) {
            case HOME:
                path.moveTo(3.5f, 11f); path.lineTo(12f, 4f); path.lineTo(20.5f, 11f);
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9.5f);
                path.moveTo(10f, 20f); path.lineTo(10f, 14.5f); path.lineTo(14f, 14.5f); path.lineTo(14f, 20f);
                c.drawPath(path, p); break;
            case PLAN:
                c.drawRoundRect(new RectF(4f, 5.5f, 20f, 20f), 3f, 3f, p);
                c.drawLine(4f, 10f, 20f, 10f, p); c.drawLine(8.5f, 3.5f, 8.5f, 7f, p); c.drawLine(15.5f, 3.5f, 15.5f, 7f, p);
                c.drawCircle(9f, 14.5f, 0.6f, p); c.drawCircle(15f, 14.5f, 0.6f, p); break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 2 + i * Math.PI / 5; float r = i % 2 == 0 ? 8.6f : 3.9f;
                    float x = 12f - (float) (r * Math.cos(a)), y = 12.8f - (float) (r * Math.sin(a));
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                if (filled) { p.setStyle(Paint.Style.FILL_AND_STROKE); c.drawPath(path, p); p.setStyle(Paint.Style.STROKE); }
                else c.drawPath(path, p);
                break;
            case LOCK:
                c.drawRoundRect(new RectF(5f, 10.5f, 19f, 20.5f), 3f, 3f, p);
                path.moveTo(8f, 10.5f); path.lineTo(8f, 7.5f); path.cubicTo(8f, 2.5f, 16f, 2.5f, 16f, 7.5f); path.lineTo(16f, 10.5f);
                c.drawPath(path, p); c.drawLine(12f, 14.5f, 12f, 16.5f, p); break;
            default:
                c.drawCircle(12f, 12f, 3f, p); c.drawCircle(12f, 12f, 6.8f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(12f + (float) (6.8 * Math.cos(a)), 12f + (float) (6.8 * Math.sin(a)),
                            12f + (float) (9.3 * Math.cos(a)), 12f + (float) (9.3 * Math.sin(a)), p);
                }
        }
        c.restore();
    }
}
