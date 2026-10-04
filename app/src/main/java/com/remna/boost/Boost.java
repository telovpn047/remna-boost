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
        if (Sh.granted()) Sh.exec("am kill-all");
        System.gc();
        return n;
    }

    /** Pil sıcaklığı (°C). */
    static float batteryTemp(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return 0;
        return i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    }

    static final java.util.Map<String, java.net.InetAddress> DNS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Gerçek gidiş-dönüş süresi (ms): bağlantı kurulduktan sonra küçük bir HTTP isteği gönderip ilk baytın
     * gelişini ölçer. VPN açıkken TCP bağlantısı yerelde hemen kabul edildiği için yalnızca bağlantı süresi
     * yanıltıcıdır; bu yöntem tünel dahil gerçek yolu ölçer. DNS süresi ölçüme katılmaz. Başarısızsa -1.
     */
    static int ping(String host, int ignoredPort) {
        int best = -1;
        for (int i = 0; i < 2; i++) {
            int r = rtt(host);
            if (r > 0 && (best < 0 || r < best)) best = r;
        }
        return best;
    }

    static int rtt(String host) {
        try {
            java.net.InetAddress a = DNS.get(host);
            if (a == null) { a = java.net.InetAddress.getByName(host); DNS.put(host, a); }
            try (Socket s = new Socket()) {
                s.setTcpNoDelay(true);
                s.setSoTimeout(4000);
                s.connect(new InetSocketAddress(a, 80), 4000);
                java.io.OutputStream o = s.getOutputStream();
                java.io.InputStream in = s.getInputStream();
                byte[] req = ("HEAD / HTTP/1.1\r\nHost: " + host + "\r\nConnection: keep-alive\r\n\r\n").getBytes();
                // 1) ısınma: VPN/proxy uzak bağlantıyı burada kurar; süresi sayılmaz
                long t0 = System.nanoTime();
                o.write(req);
                o.flush();
                if (!readHeaders(in)) return -1;
                long first = (System.nanoTime() - t0) / 1_000_000;
                // 2) asıl ölçüm: aynı bağlantı üzerinde tek gidiş-dönüş
                long t = System.nanoTime();
                o.write(req);
                o.flush();
                if (in.read() < 0) return (int) Math.max(1, first); // sunucu bağlantıyı kapattıysa
                return (int) Math.max(1, (System.nanoTime() - t) / 1_000_000);
            }
        } catch (Exception e) {
            DNS.remove(host);
            return -1;
        }
    }

    /** HTTP yanıt başlıklarını sonuna (boş satır) kadar okur. */
    private static boolean readHeaders(java.io.InputStream in) throws java.io.IOException {
        int state = 0, b;
        while ((b = in.read()) >= 0) {
            if (b == '\r' && (state == 0 || state == 2)) state++;
            else if (b == '\n' && state == 1) state = 2;
            else if (b == '\n' && state == 3) return true;
            else state = 0;
        }
        return false;
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
        String mode = p.getString("dnd_mode", "priority");
        int f = mode.equals("alarms") ? NotificationManager.INTERRUPTION_FILTER_ALARMS
                : mode.equals("none") ? NotificationManager.INTERRUPTION_FILTER_NONE
                : NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        nm.setInterruptionFilter(f);
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

    /** Şu an bir VPN üzerinden mi bağlıyız? */
    static boolean vpnActive(Context c) {
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            if (cm == null) return false;
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
        } catch (Throwable t) {
            return false;
        }
    }

    static String fmtGb(long bytes) { return String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0); }
}
