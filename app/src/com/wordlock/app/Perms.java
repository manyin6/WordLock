package com.wordlock.app;

import android.Manifest;
import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Every permission the app needs, with a check, an action and a plain-language "how to". */
public class Perms {
    public static class P {
        final String id, name, desc, how; P(String id, String name, String desc, String how) { this.id = id; this.name = name; this.desc = desc; this.how = how; }
    }

    static List<P> all(Context c) {
        ArrayList<P> r = new ArrayList<>();
        r.add(new P("overlay", "悬浮窗", "显示锁机界面", "在列表里找到「单词锁」→ 打开「允许显示在其他应用的上层」开关，然后返回"));
        r.add(new P("acc", "无障碍服务", "检测打开了哪个应用，锁机和应用锁都靠它", "点「已下载的应用」或「已安装的服务」→「单词锁」→ 打开开关 → 确定。若提示「受限制的设置」，先点下方「应用信息」→ 右上角 ⋮ →「允许受限制的设置」"));
        r.add(new P("usage", "使用情况访问", "统计每个应用今天用了多久", "在列表里找到「单词锁」→ 打开「允许访问使用情况」，然后返回"));
        if (Build.VERSION.SDK_INT >= 33) r.add(new P("notify", "通知", "显示锁机倒计时", "在弹窗里点「允许」"));
        r.add(new P("phone", "电话状态", "来电时自动让出屏幕", "在弹窗里点「允许」"));
        r.add(new P("battery", "忽略电池优化", "防止被系统省电杀掉", "在弹窗里点「允许」"));
        if (Build.VERSION.SDK_INT >= 31 && !c.getSystemService(AlarmManager.class).canScheduleExactAlarms())
            r.add(new P("alarm", "闹钟和提醒", "让自动锁机准时触发", "打开「允许设置闹钟和提醒」开关，然后返回"));
        r.add(new P("auto", "自启动 / 后台弹出界面", "小米、华为、OPPO、vivo 必须手动开", "在应用信息里打开「自启动」，并在「其他权限」里允许「后台弹出界面」。设置完返回即可"));
        return r;
    }

    static boolean ok(Context c, String id) {
        switch (id) {
            case "overlay": return Settings.canDrawOverlays(c);
            case "acc": return accOn(c);
            case "usage": return usageOn(c);
            case "notify": return granted(c, Manifest.permission.POST_NOTIFICATIONS);
            case "phone": return granted(c, Manifest.permission.READ_PHONE_STATE);
            case "battery": return c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
            case "alarm": return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
            case "auto": return Prefs.getInt(c, "autoDone", 0) == 1;
        }
        return true;
    }

    static int missing(Context c) { int n = 0; for (P p : all(c)) if (!ok(c, p.id)) n++; return n; }

    static void open(Activity a, String id) {
        String pkg = "package:" + a.getPackageName();
        switch (id) {
            case "overlay": go(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg))); break;
            case "acc": go(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); break;
            case "usage": go(a, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); break;
            case "notify": runtime(a, Manifest.permission.POST_NOTIFICATIONS, 1); break;
            case "phone": runtime(a, Manifest.permission.READ_PHONE_STATE, 2); break;
            case "battery": go(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg))); break;
            case "alarm": go(a, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse(pkg))); break;
            case "auto": Prefs.setInt(a, "autoDone", 1); appInfo(a); break;
        }
    }

    /** runtime permission; if the user chose "don't ask again", jump to the app's settings instead */
    static void runtime(Activity a, String perm, int rc) {
        String key = "asked_" + perm;
        if (Prefs.getInt(a, key, 0) == 1 && !a.shouldShowRequestPermissionRationale(perm)) { appInfo(a); return; }
        Prefs.setInt(a, key, 1);
        a.requestPermissions(new String[]{perm}, rc);
    }

    static void appInfo(Activity a) { go(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()))); }

    static void go(Activity a, Intent i) {
        try { a.startActivity(i); } catch (Exception e) {
            try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()))); } catch (Exception ignored) { }
        }
    }

    static boolean granted(Context c, String p) { return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    /** No cached preference: inspect the exact service, not any service in our package. */
    static boolean accOn(Context c) {
        if (GuardService.inst != null) return true;
        ComponentName target = new ComponentName(c, GuardService.class);
        try {
            AccessibilityManager manager = c.getSystemService(AccessibilityManager.class);
            if (manager != null) {
                for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    ResolveInfo ri = info.getResolveInfo();
                    if (ri != null && ri.serviceInfo != null && target.equals(new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name))) return true;
                }
            }
        } catch (RuntimeException ignored) { }
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return AccessibilityComponents.contains(enabled, target.getPackageName(), target.getClassName());
        } catch (RuntimeException ignored) { return false; }
    }

    static void accStatus(Activity a) {
        boolean granted = accOn(a), connected = GuardService.inst != null;
        String message = "手机：" + Build.MANUFACTURER + " " + Build.MODEL
                + "\n系统：Android " + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）"
                + "\n应用：0.6.5"
                + "\n系统授权：" + (granted ? "已检测到" : "未检测到")
                + "\n服务连接：" + (connected ? "已连接" : "尚未连接")
                + "\n\n" + (connected ? "服务已连接。如引导状态刚发生变化，请返回后稍等片刻。"
                    : granted ? "权限已授权，但服务尚未连接。请稍等；若一直未连接，请检查系统无障碍页面是否提示服务异常，并检查自启动及后台运行设置。"
                    : "请返回系统无障碍页面，确认「单词锁」的服务开关。若系统开关也自动关闭，请提供此状态信息和系统页面截图，继续排查。")
                + (GuardService.lastIssue == null ? "" : "\n\n本次服务异常类型：" + GuardService.lastIssue);
        new android.app.AlertDialog.Builder(a).setTitle("无障碍状态检查")
                .setMessage(message)
                .setPositiveButton("打开系统设置", (d, w) -> open(a, "acc"))
                .setNeutralButton("复制状态", (d, w) -> {
                    android.content.ClipboardManager cb = a.getSystemService(android.content.ClipboardManager.class);
                    if (cb != null) cb.setPrimaryClip(android.content.ClipData.newPlainText("无障碍状态", message));
                    android.widget.Toast.makeText(a, "状态已复制，可发给开发者排查", android.widget.Toast.LENGTH_SHORT).show();
                }).setNegativeButton("返回", null).show();
    }

    static boolean usageOn(Context c) {
        AppOpsManager ao = c.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
