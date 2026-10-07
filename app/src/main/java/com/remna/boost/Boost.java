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

    /**
     * Arka plan temizliği. Yalnız önbellekteki (arka planda bekleyen) işlemler kapatılır; ön plan servisleri (VPN,
     * müzik vb.) etkilenmez. Global "am kill-all" kullanılmaz.
     * mode: "smart" → yalnız RAM baskısı varsa (boş RAM < %25), "aggressive" → her zaman, "off" → hiç.
     * Dönüş: kapatılan uygulama sayısı; atlandıysa -1.
     */
    static int cleanup(Context c, String keep, String mode) {
        if ("off".equals(mode)) return -1;
        if ("smart".equals(mode) && availRam(c) * 100 / Math.max(1, totalRam(c)) >= 25) return -1;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        int n = 0;
        // <queries> ile yalnız başlatıcıda görünen uygulamalar listelenir (QUERY_ALL_PACKAGES gerekmez)
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(c.getPackageName()) || ai.packageName.equals(keep)) continue;
            try { am.killBackgroundProcesses(ai.packageName); n++; } catch (SecurityException ignored) {}
        }
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

    /**
     * TCP yoklaması (ms): ICMP'yi engelleyen sunucularda gidiş-dönüş süresi. Port açıksa bağlantı,
     * kapalıysa sunucunun "reddedildi" (RST) yanıtı ölçülür; ikisi de bir tam gidiş-dönüştür. Yanıt yoksa -1.
     */
    static int tcpProbe(String ip, int... ports) {
        int best = -1;
        for (int port : ports) {
            for (int k = 0; k < 2; k++) {
                long t = System.nanoTime();
                int ms = -1;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip, port), 1500);
                    ms = (int) ((System.nanoTime() - t) / 1_000_000);
                } catch (java.net.ConnectException e) {
                    long d = (System.nanoTime() - t) / 1_000_000;
                    if (d < 1400) ms = (int) d; // hızlı ret = RST yanıtı
                } catch (Exception ignored) {}
                // 25 ms altı "yanıt" yerel güvenlik duvarının sahte RST'si (gerçek sunucuya fiziksel olarak imkânsız)
                if (ms >= 60 && ms < 900 && (best < 0 || ms < best)) best = ms;
            }
            if (best > 0) break;
        }
        return best;
    }

    /**
     * Root/Shizuku gerektirmeyen gerçek ICMP ping (ms): Android tüm uygulamalara "datagram ICMP"
     * soketi izni verir (ping_group_range). En iyi 3 denemeyi döner; yanıt yoksa -1.
     */
    static int icmp(String ip) {
        // 1) ping soketi (bazı cihazlarda SELinux engeller)
        if (icmpMode != 2) {
            int best = -1;
            for (int i = 0; i < 3; i++) {
                int r = icmpOnce(ip, 1000, i + 1);
                if (r >= 0 && (best < 0 || r < best)) best = r;
            }
            if (best > 0) { icmpMode = 1; return best; }
            if (icmpMode == 0 && icmpSocketBroken) icmpMode = 2; // soket hiç açılamıyorsa bir daha deneme
        }
        // 2) sistemin ping programı (Termux'taki yöntemle aynı; Shizuku gerekmez)
        int e = execPing(ip);
        if (e > 0) icmpMode = 2;
        return e;
    }

    /** Tek örnek (istatistik için): çalışan yöntemle 1 ping. Yanıt yoksa -1. */
    static int icmpSample(String ip, int timeoutMs, int seq) {
        if (icmpMode != 2) {
            int r = icmpOnce(ip, timeoutMs, seq);
            if (r > 0) { icmpMode = 1; return r; }
            if (icmpMode == 1) return -1; // soket çalışıyor, bu paket kayboldu
        }
        int r = execPingOnce(ip, Math.max(1, timeoutMs / 1000));
        if (r > 0) icmpMode = 2;
        return r;
    }

    static int execPingOnce(String ip, int waitSec) {
        if (!ip.matches("[0-9.]+")) return -1;
        java.lang.Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"/system/bin/ping", "-c", "1", "-W", String.valueOf(waitSec), ip});
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) {
                    int i = l.indexOf("time=");
                    if (i >= 0) return Math.max(1, Math.round(Float.parseFloat(l.substring(i + 5).split(" ")[0])));
                }
            }
            return -1;
        } catch (Exception ex) {
            return -1;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** 0 bilinmiyor, 1 ping soketi, 2 sistem ping programı. Tanılamada gösterilir. */
    static volatile int icmpMode = 0;
    static volatile boolean icmpSocketBroken;
    static volatile String icmpErr = "";

    static String icmpMethod() {
        return icmpMode == 1 ? "ping soketi" : icmpMode == 2 ? "sistem ping programı" : "henüz belirlenmedi";
    }

    /** /system/bin/ping ile ölçüm (3 deneme, en iyisi). Yanıt yoksa -1. */
    static int execPing(String ip) {
        if (!ip.matches("[0-9.]+")) return -1;
        java.lang.Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"/system/bin/ping", "-c", "3", "-i", "0.2", "-W", "1", ip});
            int best = -1;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) {
                    int i = l.indexOf("time=");
                    if (i < 0) continue;
                    try {
                        int ms = Math.round(Float.parseFloat(l.substring(i + 5).split(" ")[0]));
                        if (ms > 0 && (best < 0 || ms < best)) best = ms;
                    } catch (NumberFormatException ignored) {}
                }
            }
            p.waitFor();
            return best;
        } catch (Exception ex) {
            icmpErr = "exec: " + ex.getClass().getSimpleName();
            return -1;
        } finally {
            if (p != null) p.destroy();
        }
    }

    static int icmpOnce(String ip, int timeoutMs, int seq) {
        java.io.FileDescriptor fd = null;
        try {
            fd = android.system.Os.socket(android.system.OsConstants.AF_INET, android.system.OsConstants.SOCK_DGRAM, android.system.OsConstants.IPPROTO_ICMP);
            byte[] pkt = new byte[16];
            pkt[0] = 8; // echo request
            pkt[6] = (byte) (seq >> 8); pkt[7] = (byte) seq;
            for (int i = 8; i < 16; i++) pkt[i] = (byte) i;
            int sum = 0;
            for (int i = 0; i < pkt.length; i += 2) sum += ((pkt[i] & 0xff) << 8) | (pkt[i + 1] & 0xff);
            while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
            sum = ~sum & 0xffff;
            pkt[2] = (byte) (sum >> 8); pkt[3] = (byte) sum;
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            long t = System.nanoTime();
            android.system.Os.sendto(fd, pkt, 0, pkt.length, 0, addr, 0);
            byte[] buf = new byte[256];
            long deadline = t + timeoutMs * 1_000_000L;
            while (true) {
                int left = (int) ((deadline - System.nanoTime()) / 1_000_000);
                if (left <= 0) return -1;
                android.system.StructPollfd pf = new android.system.StructPollfd();
                pf.fd = fd;
                pf.events = (short) android.system.OsConstants.POLLIN;
                if (android.system.Os.poll(new android.system.StructPollfd[]{pf}, left) <= 0) return -1;
                int n = android.system.Os.recvfrom(fd, buf, 0, buf.length, 0, null);
                if (n >= 8 && buf[0] == 0 && buf[7] == (byte) seq)
                    return (int) Math.max(1, (System.nanoTime() - t) / 1_000_000);
            }
        } catch (Throwable e) {
            if (fd == null) icmpSocketBroken = true; // soket oluşturulamadı (izin/SELinux)
            icmpErr = "soket: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
            return -1;
        } finally {
            if (fd != null) try { android.system.Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    /**
     * Yerleşik PUBG veri merkezi listesi (Türkmenistan'dan yapılan analizde ICMP'ye yanıt verdiği doğrulananlar).
     * {bölge, ip, şehir}. Kullanıcının kendi sunucu kaydı varsa onunla birleştirilir.
     */
    static final String[][] PUBG_DC = {
            {"Avrupa", "49.51.130.96", "Frankfurt"},
            {"Avrupa", "49.51.130.11", "Frankfurt"},
            {"Asya", "101.32.138.88", "Singapur"},
            {"Asya", "101.32.110.254", "Singapur"},
            {"KRJP", "119.28.149.60", "Seul"},
    };

    /** Bir bölge için ICMP ile ölçülebilen PUBG veri merkezi IP'leri (yerleşik + kayıttan). */
    static java.util.List<String> dcFor(Context c, String region) {
        java.util.LinkedHashSet<String> r = new java.util.LinkedHashSet<>();
        for (String[] d : PUBG_DC) if (d[0].equals(region)) r.add(d[1]);
        String[] t = ServerLog.pingTarget(c, region);
        if (t != null && region.equals(t[1])) r.add(t[0]);
        return new java.util.ArrayList<>(r);
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
