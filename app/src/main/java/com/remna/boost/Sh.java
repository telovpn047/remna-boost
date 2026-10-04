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
        if (ip.startsWith("0.") || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")) return null;
        return ip;
    }

    /** ICMP ping (ms); başarısızsa -1. */
    static int icmp(String ip) {
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

    /** Son 1 saniyede ekrana basılan kare sayısı; ölçülemezse -1. */
    static int fps(String pkg) {
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
        if (n < 2) return 0;
        int c = 0;
        for (int i = 0; i < n; i++) if (ts[i] > max - 1_000_000_000L) c++;
        return c;
    }
}
