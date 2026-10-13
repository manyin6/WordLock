package com.wordlock.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;

/** Design tokens + view helpers. 8dp grid: page margin 20, card padding 16, row height 52, radius 22/16/12. */
public class U {
    public static final int ACC = 0xFFE5533D, ACC2 = 0xFFFF8A5C, GREEN = 0xFF22A06B, TXT = 0xFF1D1D1F, SUB = 0xFF6E6E73,
            LINE = 0x14000000, WHITE = 0xFFFFFFFF, BG = 0xFFF7F4F0, CHIP = 0x8CFFFFFF, CHIP_SOLID = 0xFFF0ECE6,
            DARK = 0xFF0B1622, DSUB = 0xFFA9B8C6, DGLASS = 0x22FFFFFF, DCHIP = 0x1AFFFFFF, DLINE = 0x26FFFFFF;
    // legacy aliases
    public static final int RED = ACC, GRAY = SUB, DARK2 = 0xFF1B2B38, DARK3 = 0xFF263B4C;

    public static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    public static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(r); return g;
    }

    /** frosted-glass surface */
    public static GradientDrawable glass(Context c, boolean dark, float radiusDp) {
        GradientDrawable g = round(dark ? DGLASS : 0xB8FFFFFF, dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 0.8f)), dark ? DLINE : 0xD9FFFFFF);
        return g;
    }

    /** progressive fade used at top / bottom edges */
    public static GradientDrawable fade(int color, boolean solidAtTop) {
        int clear = color & 0x00FFFFFF;
        int mid = (((color >>> 24) * 2 / 3) << 24) | (color & 0x00FFFFFF);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                solidAtTop ? new int[]{color, mid, clear} : new int[]{clear, mid, color});
    }

    public static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static void setBg(View v, int bg) { setBg(v, bg, 12); }

    public static void setBg(View v, int bg, float radiusDp) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), round(bg, dp(v.getContext(), radiusDp)), null));
    }

    public static TextView btn(Context c, String s, int bg, int fg, View.OnClickListener l) {
        TextView t = tv(c, s, 15, fg, true); t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 48)); t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12)); setBg(t, bg, 14);
        t.setClickable(true); if (l != null) t.setOnClickListener(l);
        return t;
    }

    /** primary gradient button */
    public static TextView gbtn(Context c, String s, View.OnClickListener l) {
        TextView t = btn(c, s, ACC, WHITE, l); t.setTextSize(16); t.setMinHeight(dp(c, 52));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACC2, ACC});
        g.setCornerRadius(dp(c, 16));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        if (Build.VERSION.SDK_INT >= 28) { t.setElevation(dp(c, 6)); t.setOutlineSpotShadowColor(ACC); }
        return t;
    }

    public static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams full() { return lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    public static LinearLayout.LayoutParams lpw(float weight) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight); }

    public static LinearLayout.LayoutParams m(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b)); return p;
    }

    public static String dur(int min) {
        if (min >= 2880 && min % 1440 == 0) return (min / 1440) + " 天";
        if (min >= 60 && min % 60 == 0) return (min / 60) + " 小时";
        if (min > 60) return (min / 60) + " 小时 " + (min % 60) + " 分钟";
        return min + " 分钟";
    }

    public static Bitmap asset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) { return BitmapFactory.decodeStream(in); } catch (Exception e) { return null; }
    }

    public static ImageView bgView(Context c, boolean dark) {
        ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap b = asset(c, dark ? "bg_dark.jpg" : "bg_light.jpg");
        if (b != null) iv.setImageBitmap(b); else iv.setBackgroundColor(dark ? DARK : BG);
        return iv;
    }

    @SuppressWarnings("deprecation")
    public static void edgeToEdge(Activity a) {
        a.getWindow().setStatusBarColor(0); a.getWindow().setNavigationBarColor(0);
        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        a.getWindow().getDecorView().setSystemUiVisibility(f);
        if (Build.VERSION.SDK_INT >= 29) { a.getWindow().setNavigationBarContrastEnforced(false); a.getWindow().setStatusBarContrastEnforced(false); }
    }

    /** section caption, aligned with card content (left inset 4dp) */
    public static TextView caption(Context c, String s, boolean dark) {
        TextView t = tv(c, s, 13, dark ? DSUB : SUB, true); t.setPadding(dp(c, 4), 0, 0, 0); return t;
    }

    public static View divider(Context c, boolean dark, int insetLeftDp) {
        View v = new View(c); v.setBackgroundColor(dark ? DLINE : LINE);
        LinearLayout.LayoutParams p = lp(-1, Math.max(1, dp(c, 0.6f))); p.leftMargin = dp(c, insetLeftDp); v.setLayoutParams(p);
        return v;
    }
}
