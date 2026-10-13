package com.wordlock.app;

/** Pure Java parser, kept separately so component matching can be regression-tested. */
final class AccessibilityComponents {
    static boolean contains(String enabled, String pkg, String cls) {
        if (enabled == null || pkg == null || cls == null) return false;
        for (String raw : enabled.split(":")) {
            String component = raw.trim();
            int slash = component.indexOf('/');
            if (slash <= 0 || slash != component.lastIndexOf('/')) continue;
            String p = component.substring(0, slash), c = component.substring(slash + 1);
            if (c.startsWith(".")) c = p + c;
            if (pkg.equals(p) && cls.equals(c)) return true;
        }
        return false;
    }
}
