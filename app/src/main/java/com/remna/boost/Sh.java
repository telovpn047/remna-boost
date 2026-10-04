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

    /* ---------------- FPS (SurfaceFlinger kare zamanları) ---------------- */
    static String layer;

    static String findLayer(String pkg) {
        String best = null;
        for (String l : exec("dumpsys SurfaceFlinger --list").split("\n")) {
            if (!l.contains(pkg) || !l.startsWith("SurfaceView")) continue;
            if (l.contains("Background")) continue;
            if (best == null || l.contains("BLAST")) best = l.trim();
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
