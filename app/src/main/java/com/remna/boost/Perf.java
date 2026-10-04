package com.remna.boost;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Android Oyun Modu müdahaleleri, CPU sıcaklığı, şarj/bypass durumu ve maç raporu. */
final class Perf {
    private Perf() {}

    /* ---------------- Android Oyun Modu (Shizuku) ---------------- */

    /** Ayarları oyuna uygular. Oyun yeniden başlatıldığında etkili olur. Sonuç metni döner. */
    static String applyGameMode(Context c, String pkg) {
        if (!Sh.granted()) return "Shizuku yok";
        SharedPreferences p = Boost.prefs(c);
        String mode = p.getString("gm_mode", "off");
        String scale = p.getString("gm_scale", "off");
        String fps = p.getString("gm_fps", "off");
        if (mode.equals("off") && scale.equals("off") && fps.equals("off")) {
            Sh.exec("device_config delete game_overlay " + pkg + "; cmd game mode standard " + pkg);
            return "kapalı";
        }
        String m = mode.equals("off") ? "performance" : mode;
        int id = m.equals("battery") ? 3 : m.equals("standard") ? 1 : 2;
        StringBuilder cfg = new StringBuilder("mode=" + id);
        if (!scale.equals("off")) cfg.append(",downscaleFactor=").append(scale);
        if (!fps.equals("off")) cfg.append(",fps=").append(fps);
        if (id == 1) Sh.exec("device_config delete game_overlay " + pkg);
        else Sh.exec("device_config put game_overlay " + pkg + " " + cfg);
        String out = Sh.exec("cmd game mode " + m + " " + pkg + " 2>&1").trim();
        return out.isEmpty() ? m + (id == 1 ? "" : " (" + cfg + ")") : out;
    }

    /** Oyunun şu anki Android oyun modu (cmd game list-modes). */
    static String gameModeStatus(String pkg) {
        if (!Sh.granted()) return "";
        return Sh.exec("cmd game list-modes " + pkg + " 2>&1").trim();
    }

    /* ---------------- sıcaklık ---------------- */

    static long cpuAt;
    static float cpuCache = -1;

    /** İşlemci sıcaklığı (°C), Shizuku ile thermal zone'lardan; okunamazsa -1. */
    static float cpuTemp() {
        if (System.currentTimeMillis() - cpuAt < 3000) return cpuCache;
        cpuAt = System.currentTimeMillis();
        float best = -1;
        String out = Sh.granted()
                ? Sh.exec("for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat $z/type 2>/dev/null):$(cat $z/temp 2>/dev/null)\"; done")
                : "";
        for (String l : out.split("\n")) {
            int i = l.lastIndexOf(':');
            if (i < 0) continue;
            String type = l.substring(0, i).toLowerCase();
            if (!(type.contains("cpu") || type.equals("ap") || type.contains("soc") || type.contains("tsens"))) continue;
            if (type.contains("gpu") || type.contains("batt")) continue;
            try {
                float v = Float.parseFloat(l.substring(i + 1).trim());
                if (v > 1000) v /= 1000f;
                if (v > 15 && v < 125 && v > best) best = v;
            } catch (NumberFormatException ignored) {}
        }
        cpuCache = best;
        return best;
    }

    /* ---------------- şarj / bypass ---------------- */

    /** "" (şarjda değil), "⚡" (şarj oluyor) veya "⚡Bypass" (güç doğrudan sisteme, pil şarj olmuyor). */
    static String chargeState(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null || i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return "";
        BatteryManager bm = c.getSystemService(BatteryManager.class);
        int cur = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) : Integer.MIN_VALUE;
        if (cur == Integer.MIN_VALUE || cur == 0) return "⚡";
        int ma = Math.abs(cur) > 20000 ? Math.abs(cur) / 1000 : Math.abs(cur); // µA ya da mA
        return ma < 150 ? "⚡Bypass" : "⚡";
    }

    /* ---------------- maç raporu ---------------- */

    static final class Session {
        final long start = System.currentTimeMillis();
        final List<Integer> fps = new ArrayList<>(), ping = new ArrayList<>();
        float maxBatt = 0, maxCpu = 0;

        void add(int f, int p, float batt, float cpu) {
            if (f >= 0) fps.add(f);
            if (p > 0) ping.add(p);
            maxBatt = Math.max(maxBatt, batt);
            maxCpu = Math.max(maxCpu, cpu);
        }

        void save(Context c) {
            long dur = System.currentTimeMillis() - start;
            if (dur < 60_000) return; // 1 dakikadan kısa oturumları kaydetme
            try {
                JSONObject o = new JSONObject();
                o.put("at", start);
                o.put("dur", dur);
                o.put("maxBatt", maxBatt);
                o.put("maxCpu", maxCpu);
                if (!fps.isEmpty()) {
                    List<Integer> s = new ArrayList<>(fps);
                    Collections.sort(s);
                    long sum = 0;
                    for (int v : s) sum += v;
                    o.put("avgFps", sum / s.size());
                    int low = Math.max(1, s.size() / 20); // en düşük %5
                    long ls = 0;
                    for (int k = 0; k < low; k++) ls += s.get(k);
                    o.put("lowFps", ls / low);
                    o.put("maxFps", s.get(s.size() - 1));
                    JSONArray g = new JSONArray();
                    int step = Math.max(1, fps.size() / 120);
                    for (int k = 0; k < fps.size(); k += step) g.put(fps.get(k));
                    o.put("graph", g);
                }
                if (!ping.isEmpty()) {
                    long ps = 0;
                    int pmax = 0;
                    for (int v : ping) { ps += v; pmax = Math.max(pmax, v); }
                    o.put("avgPing", ps / ping.size());
                    o.put("maxPing", pmax);
                }
                Boost.prefs(c).edit().putString("last_report", o.toString()).apply();
            } catch (Exception ignored) {}
        }
    }
}
