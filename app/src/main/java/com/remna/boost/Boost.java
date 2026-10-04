package com.remna.boost;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Process;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Root gerektirmeyen oyun hızlandırma işlemleri. */
final class Boost {
    private Boost() {}

    /** Bilinen PUBG Mobile paketleri (öncelik sırasıyla). */
    static final String[][] PUBG = {
            {"com.tencent.ig", "PUBG Mobile (Global)"},
            {"com.pubg.imobile", "BGMI"},
            {"com.pubg.krmobile", "PUBG Mobile (KR)"},
            {"com.vng.pubgmobile", "PUBG Mobile (VN)"},
            {"com.rekoo.pubgm", "PUBG Mobile (TW)"},
            {"com.tencent.tmgp.pubgmhd", "和平精英"},
            {"com.tencent.iglite", "PUBG Mobile Lite"},
    };

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("boost", Context.MODE_PRIVATE); }

    static List<String[]> installedGames(Context c) {
        List<String[]> r = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String[] g : PUBG) {
            try { pm.getPackageInfo(g[0], 0); r.add(g); } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return r;
    }

    static long availRam(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    static long totalRam(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    /** Arka plandaki (önbellekteki) diğer uygulamaları kapatır; kapatılan uygulama sayısını döner. */
    static int killBackground(Context c, String keep) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(c.getPackageName()) || ai.packageName.equals(keep)) continue;
            try { am.killBackgroundProcesses(ai.packageName); n++; } catch (Throwable ignored) {}
        }
        System.gc();
        return n;
    }

    /** Pil sıcaklığı (°C). */
    static float batteryTemp(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return 0;
        return i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    }

    /** TCP bağlantı süresiyle ping (ms), başarısızsa -1. */
    static int ping(String host, int port) {
        long t = System.nanoTime();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2000);
            return (int) Math.max(1, (System.nanoTime() - t) / 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }

    /* ---------- Rahatsız etme ---------- */
    static boolean dndAllowed(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    static void dndOn(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return;
        SharedPreferences p = prefs(c);
        if (!p.contains("dnd_prev")) p.edit().putInt("dnd_prev", nm.getCurrentInterruptionFilter()).apply();
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    static void dndOff(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        SharedPreferences p = prefs(c);
        if (nm == null || !p.contains("dnd_prev") || !nm.isNotificationPolicyAccessGranted()) return;
        nm.setInterruptionFilter(p.getInt("dnd_prev", NotificationManager.INTERRUPTION_FILTER_ALL));
        p.edit().remove("dnd_prev").apply();
    }

    /* ---------- Kullanım erişimi (oyundan çıkışı algılamak için) ---------- */
    static boolean usageAllowed(Context c) {
        AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Son 10 sn içinde ön plana gelen uygulama; bilinmiyorsa null. */
    static String lastForeground(Context c, String current) {
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (um == null) return current;
        long now = System.currentTimeMillis();
        UsageEvents ev = um.queryEvents(now - 10_000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String last = current;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last = e.getPackageName();
        }
        return last;
    }

    static String fmtGb(long bytes) { return String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0); }
}
