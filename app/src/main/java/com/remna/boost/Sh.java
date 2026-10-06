package com.remna.boost;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Shizuku üzerinden ADB (shell) yetkisiyle komut çalıştırma. */
final class Sh {
    private Sh() {}
    static final int REQ = 7;

    static boolean running() { try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; } }

    static boolean granted() {
        try { return running() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    static void request() { try { Shizuku.requestPermission(REQ); } catch (Throwable ignored) {} }

    /** Komutu shell olarak çalıştırır, çıktıyı döner (hata olursa ""). */
    static String exec(String cmd) {
        if (!granted()) return "";
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /* ---------------- Oyun sunucusu pingi ---------------- */
    static String serverIp;
    static long serverAt;

    /** PUBG'nin UDP soketinin bağlı olduğu uzak IP (oyun sunucusu). */
    static String gameServer(int uid) {
        if (serverIp != null && System.currentTimeMillis() - serverAt < 15_000) return serverIp;
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        for (String file : new String[]{"/proc/net/udp", "/proc/net/udp6"}) {
            for (String l : exec("cat " + file).split("\n")) {
                String[] f = l.trim().split("\\s+");
                if (f.length < 8 || f[0].startsWith("sl")) continue;
                try { if (Integer.parseInt(f[7]) != uid) continue; } catch (NumberFormatException e) { continue; }
                String ip = hexIp(f[2]);
                if (ip == null) continue;
                Integer c = count.get(ip);
                count.put(ip, c == null ? 1 : c + 1);
            }
        }
        String best = null;
        int bc = 0;
        for (java.util.Map.Entry<String, Integer> e : count.entrySet()) if (e.getValue() > bc) { best = e.getKey(); bc = e.getValue(); }
        serverIp = best;
        serverAt = System.currentTimeMillis();
        return best;
    }

    /** Oyunun tüm açık bağlantıları: {protokol, ip, port}. UDP = maç sunucusu, TCP = lobi/giriş/indirme. */
    static java.util.List<String[]> connections(int uid) {
        java.util.List<String[]> r = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String file : new String[]{"udp", "udp6", "tcp", "tcp6"}) {
            String proto = file.startsWith("udp") ? "UDP" : "TCP";
            for (String l : exec("cat /proc/net/" + file).split("\n")) {
                String[] f = l.trim().split("\\s+");
                if (f.length < 8 || f[0].startsWith("sl")) continue;
                try { if (Integer.parseInt(f[7]) != uid) continue; } catch (NumberFormatException e) { continue; }
                String ip = hexIp(f[2]);
                if (ip == null) continue;
                String port = String.valueOf(Integer.parseInt(f[2].substring(f[2].indexOf(':') + 1), 16));
                if (seen.add(proto + ip)) r.add(new String[]{proto, ip, port});
            }
        }
        return r;
    }

    /** /proc/net/udp(6) "HEXIP:PORT" → "a.b.c.d" (yalnız IPv4; boş/yerel adresler null). */
    static String hexIp(String s) {
        int c = s.indexOf(':');
        if (c < 0) return null;
        String h = s.substring(0, c);
        int port = Integer.parseInt(s.substring(c + 1), 16);
        if (port == 0 || port == 53) return null;
        if (h.length() == 32) {
            if (!h.startsWith("0000000000000000FFFF0000")) return null;
            h = h.substring(24);
        }
        if (h.length() != 8) return null;
        long v = Long.parseLong(h, 16);
        String ip = (v & 0xff) + "." + ((v >> 8) & 0xff) + "." + ((v >> 16) & 0xff) + "." + ((v >> 24) & 0xff);
        if (ip.startsWith("0.") || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) return null;
        if (ip.startsWith("172.")) { int b = (int) ((v >> 8) & 0xff); if (b >= 16 && b <= 31) return null; }
        if (ip.startsWith("100.")) { int b = (int) ((v >> 8) & 0xff); if (b >= 64 && b <= 127) return null; }
        if (ip.startsWith("26.26.26.") || ip.startsWith("172.19.0.")) return null; // VPN tun adresleri
        return ip;
    }

    /** Basit traceroute: TTL'yi artırarak her sıçramanın adresi ve süresi. */
    static java.util.List<String[]> trace(String ip, int maxHops) {
        java.util.List<String[]> hops = new java.util.ArrayList<>();
        for (int ttl = 1; ttl <= maxHops; ttl++) {
            long t = System.nanoTime();
            String out = exec("ping -c 1 -W 1 -t " + ttl + " " + ip + " 2>&1");
            long ms = (System.nanoTime() - t) / 1_000_000;
            String hop = "*", time = "";
            boolean reached = false;
            for (String l : out.split("\n")) {
                if (l.startsWith("From ")) {
                    hop = l.substring(5).split("[ :]")[0];
                    time = ms + " ms";
                } else if (l.contains("bytes from")) {
                    hop = ip;
                    int i = l.indexOf("time=");
                    time = i > 0 ? l.substring(i + 5).split(" ")[0] + " ms" : ms + " ms";
                    reached = true;
                }
            }
            hops.add(new String[]{String.valueOf(ttl), hop, time});
            if (reached) break;
        }
        return hops;
    }

    /** ICMP ping (ms); başarısızsa -1. */
    static int icmp(String ip) {
        int own = Boost.icmp(ip); // root'suz, Shizuku'suz
        if (own > 0 || !granted()) return own;
        String out = exec("ping -c 3 -i 0.2 -W 1 " + ip);
        int best = -1;
        for (String l : out.split("\n")) {
            int i = l.indexOf("time=");
            if (i < 0) continue;
            try {
                String v = l.substring(i + 5).split(" ")[0];
                int ms = Math.round(Float.parseFloat(v));
                if (best < 0 || ms < best) best = ms;
            } catch (Exception ignored) {}
        }
        return best;
    }

    /* ---------------- FPS (SurfaceFlinger kare zamanları) ---------------- */
    static String layer;

    static String findLayer(String pkg) {
        String best = null;
        for (String l : exec("dumpsys SurfaceFlinger --list").split("\n")) {
            String t = l.trim();
            if (!t.contains(pkg) || !t.contains("SurfaceView")) continue;
            if (t.contains("Background") || t.startsWith("Bounds")) continue;
            int i = t.indexOf("SurfaceView");
            t = t.substring(i);
            if (best == null || t.contains("BLAST")) best = t;
        }
        return best;
    }

    /** FPS: önce SurfaceFlinger --latency, olmazsa timestats; ölçülemezse -1. */
    static int fps(String pkg) {
        int f = fpsLatency(pkg);
        if (f > 0) return f;
        ftValid = false; // timestats tek tek kare süresi vermez
        int t = fpsTimestats(pkg);
        return t > 0 ? t : f;
    }

    static boolean tsEnabled;

    /** Android kare istatistikleri: son temizlemeden bu yana oyunun ortalama FPS'i. */
    static int fpsTimestats(String pkg) {
        if (!granted()) return -1;
        if (!tsEnabled) { exec("dumpsys SurfaceFlinger --timestats -enable"); exec("dumpsys SurfaceFlinger --timestats -clear"); tsEnabled = true; return -1; }
        String out = exec("dumpsys SurfaceFlinger --timestats -dump; dumpsys SurfaceFlinger --timestats -clear");
        String cur = "";
        double best = -1;
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith("layerName")) cur = t;
            else if (t.startsWith("averageFPS") && cur.contains(pkg)) {
                try {
                    double v = Double.parseDouble(t.substring(t.indexOf('=') + 1).trim());
                    if (v > best) best = v;
                } catch (Exception ignored) {}
            }
        }
        return best < 0 ? -1 : (int) Math.round(best);
    }

    static void fpsStop() { if (tsEnabled) { exec("dumpsys SurfaceFlinger --timestats -disable"); tsEnabled = false; } }

    /** Tanılama: oyun katmanları, latency ve timestats çıktılarından kısa özet. */
    static String fpsDiag(String pkg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(android.os.Build.VERSION.RELEASE).append(" · ").append(android.os.Build.MODEL).append('\n');
        sb.append("== --list (").append(pkg).append(") ==\n");
        int n = 0;
        for (String l : exec("dumpsys SurfaceFlinger --list").split("\n"))
            if (l.contains(pkg) && n++ < 8) sb.append(l.trim()).append('\n');
        sb.append("seçilen katman: ").append(layer).append('\n');
        if (layer != null) {
            sb.append("== --latency (ilk 4 satır) ==\n");
            String[] lat = exec("dumpsys SurfaceFlinger --latency '" + layer.replace("'", "'\\''") + "'").split("\n");
            for (int i = 0; i < Math.min(4, lat.length); i++) sb.append(lat[i]).append('\n');
        }
        sb.append("== timestats ==\n");
        n = 0;
        String cur = "";
        for (String l : exec("dumpsys SurfaceFlinger --timestats -dump").split("\n")) {
            String t = l.trim();
            if (t.startsWith("layerName")) cur = t;
            if ((t.startsWith("layerName") || t.startsWith("averageFPS") || t.startsWith("totalFrames")) && cur.contains(pkg) && n++ < 12)
                sb.append(t).append('\n');
        }
        if (n == 0) sb.append("(oyun katmanı yok)\n");
        return sb.toString();
    }

    /** Son 1 saniyede ekrana basılan kare sayısı (--latency); ölçülemezse -1. */
    static int fpsLatency(String pkg) {
        if (!granted()) return -1;
        if (layer == null) layer = findLayer(pkg);
        if (layer == null) return -1;
        String out = exec("dumpsys SurfaceFlinger --latency '" + layer.replace("'", "'\\''") + "'");
        String[] lines = out.split("\n");
        if (lines.length < 3) { layer = null; return -1; }
        long[] ts = new long[lines.length];
        int n = 0;
        long max = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].trim().split("\\s+");
            if (f.length < 3) continue;
            try {
                long t = Long.parseLong(f[1]);
                if (t <= 0 || t == Long.MAX_VALUE) continue;
                ts[n++] = t;
                if (t > max) max = t;
            } catch (NumberFormatException ignored) {}
        }
        if (n < 2) { ftValid = false; return 0; }
        int c = 0;
        for (int i = 0; i < n; i++) if (ts[i] > max - 1_000_000_000L) c++;
        // kare süreleri: son 2 sn içindeki ardışık kareler arası (ms)
        long[] w = new long[n];
        int m = 0;
        for (int i = 0; i < n; i++) if (ts[i] > max - 2_000_000_000L) w[m++] = ts[i];
        java.util.Arrays.sort(w, 0, m);
        double sum = 0, mx = 0;
        int st = 0, hv = 0;
        for (int i = 1; i < m; i++) {
            double d = (w[i] - w[i - 1]) / 1_000_000.0;
            sum += d;
            if (d > mx) mx = d;
            if (d > 33.4) hv++;
            else if (d > 25) st++;
        }
        if (m > 1) {
            ftAvg = (float) (sum / (m - 1));
            ftMax = (float) mx;
            stutters = st;
            heavy = hv;
            ftValid = true;
        } else ftValid = false;
        return c;
    }

    /** Son ölçümün kare süresi verileri (yalnız --latency yöntemiyle; timestats'ta yok). */
    static volatile boolean ftValid;
    static volatile float ftAvg, ftMax;
    static volatile int stutters, heavy;
}
