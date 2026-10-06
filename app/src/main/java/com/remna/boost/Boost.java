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

    /** Şifreli DNS (Cloudflare DoH) ile A kaydı: yerel DNS engelini aşar. */
    static String doh(String host) {
        for (String base : new String[]{"https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query", "https://dns.google/resolve"}) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(base + "?name=" + host + "&type=A").openConnection();
                c.setRequestProperty("accept", "application/dns-json");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                String js = new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A").next();
                org.json.JSONArray a = new org.json.JSONObject(js).optJSONArray("Answer");
                if (a != null) for (int i = 0; i < a.length(); i++)
                    if (a.getJSONObject(i).optInt("type") == 1) return a.getJSONObject(i).optString("data");
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Engelli alan adına düz HTTP isteği: DoH ile çözülen IP'ye bağlanır, Host başlığını korur. Gövdeyi döner. */
    static String httpVia(String host, String method, String path, String body) throws java.io.IOException {
        String ip = doh(host);
        if (ip == null) ip = host;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, 80), 8000);
            s.setSoTimeout(10000);
            byte[] b = body == null ? new byte[0] : body.getBytes("UTF-8");
            String req = method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n"
                    + (body != null ? "Content-Type: application/json\r\nContent-Length: " + b.length + "\r\n" : "") + "\r\n";
            java.io.OutputStream o = s.getOutputStream();
            o.write(req.getBytes("UTF-8"));
            o.write(b);
            o.flush();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = s.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String r = bo.toString("UTF-8");
            int i = r.indexOf("\r\n\r\n");
            String hdr = i > 0 ? r.substring(0, i).toLowerCase() : "";
            String bodyOut = i > 0 ? r.substring(i + 4) : r;
            if (hdr.contains("transfer-encoding: chunked")) bodyOut = unchunk(bodyOut);
            return bodyOut;
        }
    }

    static String unchunk(String s) {
        StringBuilder out = new StringBuilder();
        int p = 0;
        while (p < s.length()) {
            int e = s.indexOf("\r\n", p);
            if (e < 0) break;
            int len;
            try { len = Integer.parseInt(s.substring(p, e).trim().split(";")[0], 16); } catch (Exception x) { break; }
            if (len == 0) break;
            out.append(s, e + 2, Math.min(s.length(), e + 2 + len));
            p = e + 2 + len + 2;
        }
        return out.toString();
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
                if (ms > 0 && (best < 0 || ms < best)) best = ms;
            }
            if (best > 0) break;
        }
        return best;
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
