package com.wordlock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

/** Daily auto-lock. planDays bitmask: bit0 = Monday … bit6 = Sunday. */
public class Plan {
    static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

    static int dayIndex(Calendar c) { return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; }

    static long nextTime(Context c) {
        int mask = Prefs.getInt(c, "planDays", 127);
        if (Prefs.getInt(c, "planOn", 0) != 1 || mask == 0) return 0;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Prefs.getInt(c, "planH", 20));
        cal.set(Calendar.MINUTE, Prefs.getInt(c, "planM", 0));
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (cal.getTimeInMillis() > System.currentTimeMillis() + 1000 && (mask & (1 << dayIndex(cal))) != 0) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return 0;
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, PlanReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        long t = nextTime(c);
        if (t == 0) return;
        PendingIntent show = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); }
    }
}
