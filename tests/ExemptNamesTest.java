package com.wordlock.app;

public class ExemptNamesTest {
    static int fails = 0, total = 0;
    static void check(String p, boolean want) {
        total++;
        if (ExemptNames.named(p) != want) { fails++; System.out.println("FAIL " + p + " expected " + want); }
    }
    public static void main(String[] a) {
        check("com.android.incallui", true);
        check("com.samsung.android.incallui", true);
        check("com.android.server.telecom", true);
        check("com.android.deskclock", true);
        check("com.google.android.deskclock", true);
        check("com.vivo.clock", true);
        check("com.oplus.alarmclock", true);
        check("com.coloros.alarmclock", true);
        check("com.huawei.android.clock.xyz", true);
        check("com.example.alarm", true);
        check("com.example.alarmgame", false);
        check("com.funny.clockwork", false);
        check("com.ss.android.ugc.aweme", false);
        check("com.tencent.tmgp.sgame", false);
        check(null, false);
        System.out.println((total - fails) + "/" + total + " exempt-name tests passed");
        if (fails > 0) System.exit(1);
    }
}
