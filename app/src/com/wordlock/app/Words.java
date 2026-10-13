package com.wordlock.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Word lists, favourites and a simplified SM-2 schedule. Only real quiz answers change the schedule. */
public class Words {
    public static class W {
        public String w, p, m, ph = "", ex = "", img = "";
        public boolean isNew;
        String pos, shortM, sig; Set<String> senses;
        /** part-of-speech structure of the short meaning, e.g. "n|adj" */
        String sig() {
            if (sig == null) {
                StringBuilder b = new StringBuilder();
                for (String g : shortM().split("；")) { Matcher mt = POS.matcher(g); if (b.length() > 0) b.append('|'); b.append(mt.find() ? normPos(mt.group(1)) : "?"); }
                sig = b.toString();
            }
            return sig;
        }
        String pos() { if (pos == null) pos = posOf(m); return pos; }
        String shortM() { if (shortM == null) shortM = shortOf(m); return shortM; }
        Set<String> senses() { if (senses == null) senses = sensesOf(m); return senses; }
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context c) { super(c, "words.db", null, 2); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("create table prog(list text, word text, reps integer, ivl real, ease real, due integer, lapses integer, primary key(list, word))");
            db.execSQL("create table fav(list text, word text, t integer, primary key(list, word))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            if (o < 2) db.execSQL("create table if not exists fav(list text, word text, t integer, primary key(list, word))");
        }
    }

    // ---------- meaning helpers ----------
    static final Pattern POS = Pattern.compile("^\\s*([a-z]+)\\.\\s*(.*)$");

    static String normPos(String p) {
        if (p.equals("vt") || p.equals("vi")) return "v";
        if (p.equals("a")) return "adj";
        if (p.equals("ad")) return "adv";
        return p;
    }

    static String posOf(String m) {
        Matcher mt = POS.matcher(m);
        return mt.find() ? normPos(mt.group(1)) : "";
    }

    /** "n. 州, 状态, 情形, 国家…；a. 国家的…" -> "n. 州，状态，情形；a. 国家的，正式的" */
    static String shortOf(String m) {
        StringBuilder sb = new StringBuilder(); int groups = 0;
        for (String g : m.split("；")) {
            g = g.trim(); if (g.isEmpty()) continue;
            Matcher mt = POS.matcher(g);
            String pos = "", rest = g;
            if (mt.find()) { pos = mt.group(1) + ". "; rest = mt.group(2); }
            String[] ss = rest.split("[,，;、]");
            StringBuilder s = new StringBuilder(); int n = 0;
            for (String x : ss) {
                x = x.trim().replaceAll("\\(.*?\\)|（.*?）|\\[.*?]", "").trim();
                if (x.isEmpty() || x.endsWith("…")) continue;
                if (n > 0) s.append("，");
                s.append(x); if (++n == 3) break;
            }
            if (n == 0) continue;
            if (groups > 0) sb.append("；");
            sb.append(pos).append(s);
            if (++groups == 2 || sb.length() > 22) break;
        }
        String r = sb.length() == 0 ? m : sb.toString();
        return r.length() > 30 ? r.substring(0, 30) + "…" : r;
    }

    static Set<String> sensesOf(String m) {
        HashSet<String> r = new HashSet<>();
        for (String x : m.replaceAll("\\b[a-z]+\\.", " ").split("[,，;；、\\s]+")) {
            x = x.replaceAll("[()（）\\[\\]…的]", "").trim();
            if (x.length() >= 1) r.add(x);
        }
        return r;
    }

    // ---------- state ----------
    private static Words inst;
    static final long DAY = 86400000L;
    final Context ctx; final String list;
    final ArrayList<W> all = new ArrayList<>();
    final HashMap<String, W> map = new HashMap<>();
    final HashSet<String> seen = new HashSet<>();
    final LinkedHashSet<String> favs = new LinkedHashSet<>();
    final ArrayList<W> pics = new ArrayList<>();
    final HashSet<String> textAsked = new HashSet<>(), picAsked = new HashSet<>();
    final SQLiteDatabase db; final Random rnd = new Random();
    String last = "";

    public static synchronized Words get(Context c) {
        String l = Prefs.str(c, "list", "cet4");
        if (inst == null || !inst.list.equals(l)) inst = new Words(c.getApplicationContext(), l);
        return inst;
    }

    private Words(Context c, String l) {
        ctx = c; list = l;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open(l + ".tsv"), StandardCharsets.UTF_8))) {
            String s;
            while ((s = br.readLine()) != null) {
                String[] a = s.split("\t", -1);
                if (a.length < 3 || a[0].isEmpty()) continue;
                W x = new W(); x.w = a[0]; x.p = a[1]; x.m = a[2];
                all.add(x); map.put(x.w, x);
            }
        } catch (Exception e) { }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open("extra.tsv"), StandardCharsets.UTF_8))) {
            String s;
            while ((s = br.readLine()) != null) {
                String[] a = s.split("\t", -1);
                if (a.length < 4) continue;
                W x = map.get(a[0]); if (x == null) continue;
                x.ph = a[1]; x.ex = a[2]; x.img = a[3];
                if (!x.img.isEmpty()) pics.add(x);
            }
        } catch (Exception e) { }
        if (all.isEmpty()) { W x = new W(); x.w = "focus"; x.p = "ˈfəʊkəs"; x.m = "n. 焦点；v. 集中"; all.add(x); map.put(x.w, x); }
        db = new DB(c).getWritableDatabase();
        Cursor cu = db.rawQuery("select word from prog where list=?", new String[]{list});
        while (cu.moveToNext()) seen.add(cu.getString(0));
        cu.close();
        cu = db.rawQuery("select word from fav where list=? order by t desc", new String[]{list});
        while (cu.moveToNext()) if (map.containsKey(cu.getString(0))) favs.add(cu.getString(0));
        cu.close();
    }

    /** each session draws SESSION words (wrong-book first, then random); every mode only uses these words */
    static final int SESSION = 30;
    final ArrayList<W> sess = new ArrayList<>(); boolean sessFav; int learnIdx, sessWrong;

    public synchronized void resetSession() { newSession(false); }

    public synchronized void newSession(boolean favOnly) {
        textAsked.clear(); picAsked.clear(); sess.clear(); learnIdx = 0; sessWrong = 0; sessFav = favOnly;
        LinkedHashSet<W> s = new LinkedHashSet<>();
        if (favOnly && !favs.isEmpty()) {
            ArrayList<W> f = new ArrayList<>(favWords()); Collections.shuffle(f, rnd);
            for (W x : f) { if (s.size() >= SESSION) break; s.add(x); }
        } else {
            ArrayList<W> wrong = new ArrayList<>(hard()); Collections.shuffle(wrong, rnd);
            for (W x : wrong) { if (s.size() >= 12) break; s.add(x); }
            sessWrong = s.size();
            int needPic = Math.min(pics.size(), Math.max(10, Prefs.picN(ctx) + 4)), havePic = 0;
            for (W x : s) if (!x.img.isEmpty()) havePic++;
            ArrayList<W> pp = new ArrayList<>(pics); Collections.shuffle(pp, rnd);
            for (W x : pp) { if (havePic >= needPic || s.size() >= SESSION) break; if (s.add(x)) havePic++; }
            for (int tries = 0; s.size() < Math.min(SESSION, all.size()) && tries < 5000; tries++) s.add(all.get(rnd.nextInt(all.size())));
        }
        sess.addAll(s); Collections.shuffle(sess, rnd);
    }

    List<W> session(boolean favOnly) {
        if (sess.isEmpty() || sessFav != (favOnly && !favs.isEmpty())) newSession(favOnly && !favs.isEmpty());
        return sess;
    }
    public synchronized List<W> sessionWords() { return new ArrayList<>(session(sessFav)); }
    public synchronized int sessionWrong() { return sessWrong; }

    List<W> favWords() { ArrayList<W> r = new ArrayList<>(); for (String s : favs) { W x = map.get(s); if (x != null) r.add(x); } return r; }

    Set<String> dueSet() {
        HashSet<String> r = new HashSet<>();
        Cursor cu = db.rawQuery("select word from prog where list=? and due<=" + System.currentTimeMillis(), new String[]{list});
        while (cu.moveToNext()) r.add(cu.getString(0));
        cu.close(); return r;
    }

    /** pick from pool with ordered preferences: due, seen, unseen; skipping excluded words when possible */
    W pick(List<W> pool, Set<String> hardExclude, Set<String> softExclude, boolean preferUnseen) {
        if (pool.isEmpty()) pool = all;
        Set<String> due = dueSet();
        ArrayList<W> sh = new ArrayList<>(pool); Collections.shuffle(sh, rnd);
        for (int pass = 0; pass < 2; pass++) {
            Set<String> soft = pass == 0 ? softExclude : Collections.<String>emptySet();
            W fallback = null, seenW = null, unseenW = null;
            for (W x : sh) {
                if (x.w.equals(last) || hardExclude.contains(x.w) || soft.contains(x.w)) continue;
                if (due.contains(x.w)) return x;
                if (seen.contains(x.w)) { if (seenW == null) seenW = x; }
                else if (unseenW == null) unseenW = x;
                if (fallback == null) fallback = x;
            }
            W r = preferUnseen ? (unseenW != null ? unseenW : seenW) : (seenW != null ? seenW : unseenW);
            if (r != null) return r;
            if (fallback != null) return fallback;
        }
        for (W x : sh) if (!x.w.equals(last)) return x;
        return sh.get(0);
    }

    /** browse mode: walk through the session words in order */
    public synchronized W nextLearn(boolean favOnly) {
        List<W> pool = session(favOnly);
        W r = pool.get(learnIdx % pool.size()); learnIdx++;
        if (r.w.equals(last) && pool.size() > 1) { r = pool.get(learnIdx % pool.size()); learnIdx++; }
        r.isNew = !seen.contains(r.w); last = r.w; return r;
    }

    public synchronized W nextText(boolean favOnly) {
        List<W> pool = session(favOnly), plain = new ArrayList<>();
        for (W x : pool) if (x.img.isEmpty()) plain.add(x);
        W r = pick(plain.size() >= 5 ? plain : pool, picAsked, textAsked, false);
        textAsked.add(r.w); r.isNew = !seen.contains(r.w); last = r.w; return r;
    }

    public synchronized W nextPicture(boolean favOnly) {
        List<W> pool = new ArrayList<>();
        for (W x : session(favOnly)) if (!x.img.isEmpty()) pool.add(x);
        if (pool.isEmpty()) return nextText(favOnly);
        W r = pick(pool, textAsked, picAsked, false);
        picAsked.add(r.w); r.isNew = !seen.contains(r.w); last = r.w; return r;
    }

    boolean overlaps(W a, W b) {
        if (a.shortM().equals(b.shortM()) || a.w.equalsIgnoreCase(b.w)) return true;
        for (String s : b.senses()) if (a.senses().contains(s)) return true;
        return false;
    }

    /** text quiz options: short meanings, same part of speech, no overlapping senses */
    public synchronized List<String> textOptions(W x) {
        ArrayList<W> chosen = new ArrayList<>();
        for (int pass = 0; pass < 3 && chosen.size() < 3; pass++) {
            for (int tries = 0; tries < (pass == 0 ? 1500 : 400) && chosen.size() < 3; tries++) {
                W y = all.get(rnd.nextInt(all.size()));
                if (y == x || chosen.contains(y)) continue;
                if (pass == 0 && !y.sig().equals(x.sig())) continue;
                if (pass == 1 && !y.pos().equals(x.pos())) continue;
                if (overlaps(x, y)) continue;
                boolean clash = false; for (W z : chosen) if (z.shortM().equals(y.shortM())) clash = true;
                if (!clash) chosen.add(y);
            }
        }
        ArrayList<String> o = new ArrayList<>(); o.add(x.shortM()); for (W y : chosen) o.add(y.shortM());
        Collections.shuffle(o, rnd); return o;
    }

    /** picture quiz options: English words, preferring other picture words of the same part of speech */
    public synchronized List<String> wordOptions(W x) {
        List<W> pool = pics.size() >= 8 ? pics : all;
        ArrayList<String> o = new ArrayList<>(); o.add(x.w);
        for (int pass = 0; pass < 2 && o.size() < 4; pass++)
            for (int tries = 0; tries < 300 && o.size() < 4; tries++) {
                W y = pool.get(rnd.nextInt(pool.size()));
                if (o.contains(y.w) || overlaps(x, y)) continue;
                if (pass == 0 && !y.pos().equals(x.pos())) continue;
                o.add(y.w);
            }
        Collections.shuffle(o, rnd); return o;
    }

    /** first time a word is shown in browse mode: schedule it for a quiz soon, without grading */
    public synchronized void introduce(W x) {
        if (seen.contains(x.w)) return;
        ContentValues cv = new ContentValues();
        cv.put("list", list); cv.put("word", x.w); cv.put("reps", 0); cv.put("ivl", 0.0);
        cv.put("ease", 2.5); cv.put("due", System.currentTimeMillis() + 5 * 60000L); cv.put("lapses", 0);
        db.insertWithOnConflict("prog", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        seen.add(x.w); Prefs.inc(ctx, "new_" + Prefs.today());
    }

    public synchronized void grade(W x, boolean ok) {
        long now = System.currentTimeMillis();
        int reps = 0, lapses = 0; double ivl = 0, ease = 2.5; boolean isNew = true;
        Cursor cu = db.rawQuery("select reps, ivl, ease, lapses from prog where list=? and word=?", new String[]{list, x.w});
        if (cu.moveToFirst()) { reps = cu.getInt(0); ivl = cu.getDouble(1); ease = cu.getDouble(2); lapses = cu.getInt(3); isNew = false; }
        cu.close();
        long due;
        if (ok) {
            reps++;
            ivl = reps == 1 ? 1 : reps == 2 ? 3 : Math.max(ivl + 1, Math.round(ivl * ease));
            ease = Math.min(2.8, ease + 0.05);
            due = now + (long) (ivl * DAY);
        } else {
            reps = 0; lapses++; ivl = 0; ease = Math.max(1.3, ease - 0.2);
            due = now + 10 * 60000L;
        }
        ContentValues cv = new ContentValues();
        cv.put("list", list); cv.put("word", x.w); cv.put("reps", reps); cv.put("ivl", ivl);
        cv.put("ease", ease); cv.put("due", due); cv.put("lapses", lapses);
        db.insertWithOnConflict("prog", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (isNew) seen.add(x.w);
    }

    // ---------- favourites ----------
    public synchronized boolean isFav(W x) { return favs.contains(x.w); }

    public synchronized boolean toggleFav(W x) {
        if (favs.remove(x.w)) { db.delete("fav", "list=? and word=?", new String[]{list, x.w}); return false; }
        ContentValues cv = new ContentValues(); cv.put("list", list); cv.put("word", x.w); cv.put("t", System.currentTimeMillis());
        db.insertWithOnConflict("fav", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        LinkedHashSet<String> n = new LinkedHashSet<>(); n.add(x.w); n.addAll(favs); favs.clear(); favs.addAll(n);
        return true;
    }

    public synchronized List<W> favList() { return favWords(); }

    // ---------- stats ----------
    public int total() { return all.size(); }
    public int picCount() { return pics.size(); }
    public synchronized int seenCount() { return seen.size(); }

    public synchronized int count(String where) {
        Cursor cu = db.rawQuery("select count(*) from prog where list=? and " + where, new String[]{list});
        int n = cu.moveToFirst() ? cu.getInt(0) : 0; cu.close(); return n;
    }
    public int dueCount() { return count("due<=" + System.currentTimeMillis()); }
    public int masteredCount() { return count("ivl>=21"); }

    public synchronized List<W> hard() {
        ArrayList<W> r = new ArrayList<>();
        Cursor cu = db.rawQuery("select word from prog where list=? and lapses>0 order by lapses desc, due limit 100", new String[]{list});
        while (cu.moveToNext()) { W x = map.get(cu.getString(0)); if (x != null) r.add(x); }
        cu.close(); return r;
    }
}
