package com.wordlock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Prefs {
    static SharedPreferences sp(Context c) { Clock.init(c); return c.getSharedPreferences("wl", Context.MODE_PRIVATE); }

    static String day(long t) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(t)); }
    static String today() { return day(Clock.now()); }

    static int getInt(Context c, String k, int d) { return sp(c).getInt(k, d); }
    static void setInt(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    static void inc(Context c, String k) { sp(c).edit().putInt(k, getInt(c, k, 0) + 1).commit(); }
    static String str(Context c, String k, String d) { return sp(c).getString(k, d); }
    static void setStr(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static int quizN(Context c) { return getInt(c, "quizN", 10); }
    static int picN(Context c) { return getInt(c, "picN", 7); }
    static int penalty(Context c) { return getInt(c, "penalty", 2); }

    static long lockEnd(Context c) { return sp(c).getLong("lockEnd", 0); }
    static boolean locked(Context c) { return lockEnd(c) > Clock.now(c); }

    /** session counters: sView (browsed), sTOk/sTBad (text quiz), sPOk/sPBad (picture quiz) */
    static void startLock(Context c, int min) {
        min = Math.max(1, Math.min(10080, min));
        long now = Clock.now(c);
        sp(c).edit().putLong("lockStart", now).putLong("lockEnd", now + min * 60000L).putInt("lockPlan", min)
                .putInt("sView", 0).putInt("sTOk", 0).putInt("sTBad", 0).putInt("sPOk", 0).putInt("sPBad", 0).commit();
        Words.get(c).resetSession();
    }

    static void extend(Context c, int min) { sp(c).edit().putLong("lockEnd", lockEnd(c) + min * 60000L).commit(); }
    static void clearLock(Context c) { sp(c).edit().putLong("lockEnd", 0).commit(); }

    static Set<String> whitelist(Context c) { return new HashSet<>(sp(c).getStringSet("wl", new HashSet<String>())); }
    static void setWhitelist(Context c, Set<String> s) { sp(c).edit().putStringSet("wl", new HashSet<>(s)).apply(); }

    static int streakNow(Context c) {
        String last = str(c, "lastDay", "");
        long now = Clock.now(c);
        if (last.equals(day(now)) || last.equals(day(now - 86400000L))) return getInt(c, "streak", 0);
        return 0;
    }

    /** returns {minutes, views, tOk, tBad, pOk, pBad, streak, todayMinutes} */
    static int[] recordSession(Context c, boolean early) {
        long now = Clock.now(c);
        long start = sp(c).getLong("lockStart", now);
        int min = (int) Math.max(0, Math.round((now - start) / 60000.0));
        String d = day(now);
        int todayMin = getInt(c, "min_" + d, 0) + min;
        SharedPreferences.Editor e = sp(c).edit();
        e.putInt("min_" + d, todayMin);
        e.putInt("cnt_" + d, getInt(c, "cnt_" + d, 0) + 1);
        e.putInt("totalMin", getInt(c, "totalMin", 0) + min);
        e.putInt("sessions", getInt(c, "sessions", 0) + 1);
        if (!early) {
            String last = str(c, "lastDay", "");
            int st = getInt(c, "streak", 0);
            if (!d.equals(last)) st = last.equals(day(now - 86400000L)) ? st + 1 : 1;
            e.putInt("streak", st).putString("lastDay", d);
        }
        e.commit();
        return new int[]{min, getInt(c, "sView", 0), getInt(c, "sTOk", 0), getInt(c, "sTBad", 0),
                getInt(c, "sPOk", 0), getInt(c, "sPBad", 0), streakNow(c), todayMin};
    }
}
