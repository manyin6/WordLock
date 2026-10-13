package com.wordlock.app;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-app limits (modelled on 番茄锁「应用锁」): active weekdays, daily minutes, and a blocked time window. */
public class AppLock {
    public static final int UNLIMITED = -1, BANNED = 0, BONUS_MIN = 10, BONUS_Q = 5, BONUS_MAX = 3;

    public static class Rule {
        public String pkg = "", label = "";
        public int days = 127, limit = 30, rs = -1, re = -1, term = 7; public boolean on = true;
        /** created: when this term started; until: when it ends (0 = legacy rule without a term) */
        public long created, until;
        Rule copy() { Rule r = new Rule(); r.pkg = pkg; r.label = label; r.days = days; r.limit = limit; r.rs = rs; r.re = re; r.on = on;
            r.term = term; r.created = created; r.until = until; return r; }
        String enc() { return pkg + "\t" + label.replace("\t", " ").replace("\n", " ") + "\t" + days + "\t" + limit + "\t" + rs + "\t" + re + "\t" + (on ? 1 : 0)
                + "\t" + term + "\t" + created + "\t" + until; }
        static Rule dec(String s) {
            String[] a = s.split("\t", -1); if (a.length < 7) return null;
            try {
                Rule r = new Rule(); r.pkg = a[0]; r.label = a[1]; r.days = Integer.parseInt(a[2]); r.limit = Integer.parseInt(a[3]);
                r.rs = Integer.parseInt(a[4]); r.re = Integer.parseInt(a[5]); r.on = a[6].equals("1");
                if (a.length >= 10) { r.term = Integer.parseInt(a[7]); r.created = Long.parseLong(a[8]); r.until = Long.parseLong(a[9]); }
                return r;
            } catch (Exception e) { return null; }
        }
        public String limitText() { return limit == UNLIMITED ? "不限时长" : limit == BANNED ? "全天禁用" : "每天 " + U.dur(limit); }
        public String rangeText() { return rs < 0 ? "" : hm(rs) + " – " + hm(re) + " 不可用"; }
        public String daysText() {
            if (days == 127) return "每天"; if (days == 31) return "工作日"; if (days == 96) return "周末";
            StringBuilder b = new StringBuilder("周");
            for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) b.append(Plan.DAY_NAMES[i]);
            return b.toString();
        }
    }

    static String hm(int m) { return String.format(Locale.US, "%02d:%02d", m / 60, m % 60); }

    static List<Rule> rules(Context c) {
        ArrayList<Rule> r = new ArrayList<>();
        for (String line : Prefs.str(c, "rules", "").split("\n")) { Rule x = line.isEmpty() ? null : Rule.dec(line); if (x != null) r.add(x); }
        return r;
    }

    static void save(Context c, List<Rule> rs) {
        StringBuilder b = new StringBuilder(); for (Rule r : rs) b.append(r.enc()).append('\n');
        Prefs.sp(c).edit().putString("rules", b.toString()).commit();
    }

    static Rule find(Context c, String pkg) { for (Rule r : rules(c)) if (r.pkg.equals(pkg)) return r; return null; }

    static void put(Context c, Rule nr, String oldPkg) {
        List<Rule> rs = rules(c); int at = -1;
        for (int i = 0; i < rs.size(); i++) if (rs.get(i).pkg.equals(oldPkg) || rs.get(i).pkg.equals(nr.pkg)) { if (at < 0) at = i; rs.remove(i); i--; }
        if (at < 0 || at > rs.size()) rs.add(nr); else rs.add(at, nr);
        save(c, rs);
    }

    static void remove(Context c, String pkg) { List<Rule> rs = rules(c); for (int i = rs.size() - 1; i >= 0; i--) if (rs.get(i).pkg.equals(pkg)) rs.remove(i); save(c, rs); }

    // ---------- usage ----------
    static long dayStart() { return dayStart(Clock.now()); }
    static long dayStart(long t) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(t); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static Map<String, Long> cache; static long cacheAt;

    /** foreground milliseconds per package since midnight, from system usage events */
    static synchronized Map<String, Long> usageToday(Context c) {
        long tick = android.os.SystemClock.elapsedRealtime();
        if (cache != null && tick - cacheAt < 5000) return cache;
        long trusted = Clock.now(c), now = Math.max(System.currentTimeMillis(), trusted);
        HashMap<String, Long> total = new HashMap<>(), open = new HashMap<>();
        try {
            UsageEvents ev = c.getSystemService(UsageStatsManager.class).queryEvents(dayStart(trusted), now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType(); String p = e.getPackageName(); long ts = e.getTimeStamp();
                if (t == 1) { if (!open.containsKey(p)) open.put(p, ts); }
                else if (t == 2 || t == 23) { Long s = open.remove(p); if (s != null) add(total, p, ts - s); }
                else if (t == 16 || t == 26) { for (Map.Entry<String, Long> o : open.entrySet()) add(total, o.getKey(), ts - o.getValue()); open.clear(); }
            }
            for (Map.Entry<String, Long> o : open.entrySet()) add(total, o.getKey(), Math.min(now - o.getValue(), 6 * 3600000L));
        } catch (Exception ex) { }
        cache = total; cacheAt = tick; return total;
    }

    static void add(Map<String, Long> m, String k, long v) { if (v > 0) m.put(k, (m.containsKey(k) ? m.get(k) : 0L) + v); }

    static long usedMs(Context c, String pkg) { Long v = usageToday(c).get(pkg); return v == null ? 0 : v; }
    static void invalidate() { cache = null; }

    // ---------- bonus minutes earned by answering words ----------
    static int bonusMin(Context c, String pkg) { return Prefs.getInt(c, "bonus_" + Prefs.today() + "_" + pkg, 0); }
    static int bonusLeft(Context c, String pkg) { return BONUS_MAX - bonusMin(c, pkg) / BONUS_MIN; }
    static void addBonus(Context c, String pkg) { Prefs.setInt(c, "bonus_" + Prefs.today() + "_" + pkg, bonusMin(c, pkg) + BONUS_MIN); invalidate(); }

    // ---------- decisions ----------
    static boolean activeToday(Rule r) { return r.on && !expired(r) && (r.days & (1 << Plan.dayIndex(Calendar.getInstance()))) != 0; }

    // ---------- term & commitment (disclosed in 用户协议与隐私政策) ----------
    static final long GRACE = 60000L;
    static boolean expired(Rule r) { return r.until > 0 && Clock.now() >= r.until; }
    /** after a 1-minute grace period a running rule can no longer be paused, deleted or loosened until its term ends */
    static boolean committed(Rule r) { long now = Clock.now(); return r.until > 0 && now >= r.created + GRACE && now < r.until; }
    static long termEnd(long start, int termDays) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(start);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, termDays); return c.getTimeInMillis();
    }
    static String termLeft(Rule r) {
        if (r.until == 0) return "";
        long now = Clock.now(), left = r.until - now;
        if (left <= 0) return "已结束";
        if (r.until <= termEnd(now, 1)) return "今天结束";
        return "剩 " + (int) Math.ceil(left / 86400000.0) + " 天";
    }
    static String endText(long t) { return new java.text.SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new java.util.Date(t)); }

    /** null when nr is at least as strict as old; otherwise what was loosened */
    static String loosened(Rule old, Rule nr) {
        if (!old.pkg.equals(nr.pkg)) return "更换应用";
        if (!nr.on) return "暂停";
        if ((nr.days & old.days) != old.days) return "减少生效日";
        long ol = old.limit == UNLIMITED ? Long.MAX_VALUE : old.limit, nl = nr.limit == UNLIMITED ? Long.MAX_VALUE : nr.limit;
        if (nl > ol) return "增加可用时长";
        if (old.rs >= 0 && (nr.rs != old.rs || nr.re != old.re)) return "修改不可用时段";
        if (nr.until < old.until) return "缩短持续时间";
        return null;
    }

    static boolean inRange(Rule r) {
        if (r.rs < 0 || r.rs == r.re) return false;
        Calendar c = Calendar.getInstance(); int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return r.rs < r.re ? (m >= r.rs && m < r.re) : (m >= r.rs || m < r.re);
    }

    /** ms left today, Long.MAX_VALUE when unlimited */
    static long remainMs(Context c, Rule r) {
        if (r.limit == UNLIMITED) return Long.MAX_VALUE;
        return (r.limit + bonusMin(c, r.pkg)) * 60000L - usedMs(c, r.pkg);
    }

    /** null = allowed. kind[0]: 1 time used up (bonus possible), 2 blocked window, 3 banned */
    static String blockReason(Context c, Rule r, int[] kind) {
        if (r == null || !activeToday(r)) return null;
        if (inRange(r)) { kind[0] = 2; return "现在是不可用时段（" + hm(r.rs) + " – " + hm(r.re) + "）"; }
        if (r.limit == BANNED) { kind[0] = 3; return "今天全天禁止使用"; }
        if (r.limit != UNLIMITED && remainMs(c, r) <= 0) {
            kind[0] = 1; int b = bonusMin(c, r.pkg);
            return "今日 " + U.dur(r.limit) + (b > 0 ? " + 奖励 " + b + " 分钟" : "") + "已用完";
        }
        return null;
    }
}
