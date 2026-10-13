package com.wordlock.app;

/** Package-name patterns for call and alarm screens that stay usable during a lock (system apps only). */
final class ExemptNames {
    private ExemptNames() { }

    static boolean named(String p) {
        if (p == null) return false;
        return p.contains("incallui") || p.contains("telecom") || p.contains("deskclock")
                || p.endsWith(".clock") || p.contains(".clock.") || p.endsWith(".alarm") || p.contains(".alarmclock");
    }
}
