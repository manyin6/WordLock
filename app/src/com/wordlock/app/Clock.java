package com.wordlock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Tamper-resistant clock for lock timing.
 * Within one boot, time advances with SystemClock.elapsedRealtime(), so changing the
 * system clock cannot end a lock or a committed app rule early. After a reboot the
 * clock re-anchors to the system time (enable automatic time to keep it accurate).
 */
public final class Clock {
    private static Context app;
    private static long wall0 = -1, el0;
    private static int boot0 = -2;

    private Clock() { }

    static void init(Context c) { if (app == null && c != null) app = c.getApplicationContext(); }

    static long now(Context c) { init(c); return now(); }

    static synchronized long now() {
        long el = SystemClock.elapsedRealtime(), wall = System.currentTimeMillis();
        if (app == null) return wall;
        SharedPreferences sp = app.getSharedPreferences("wl_clock", Context.MODE_PRIVATE);
        if (wall0 < 0) { wall0 = sp.getLong("w", -1); el0 = sp.getLong("e", 0); boot0 = sp.getInt("b", -2); }
        int boot = bootCount();
        boolean sameBoot = wall0 >= 0 && el >= el0 && (boot == -1 ? boot0 == -1 : boot == boot0);
        if (sameBoot) return wall0 + (el - el0);
        wall0 = wall; el0 = el; boot0 = boot;
        sp.edit().putLong("w", wall0).putLong("e", el0).putInt("b", boot0).commit();
        return wall;
    }

    private static int bootCount() {
        try { return Settings.Global.getInt(app.getContentResolver(), Settings.Global.BOOT_COUNT); }
        catch (Exception e) { return -1; }
    }
}
