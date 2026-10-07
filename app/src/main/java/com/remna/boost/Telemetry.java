package com.remna.boost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CPU/GPU telemetrisi (Shizuku kabuğu ile tek komutta okunur). Okunamayan değer -1 kalır; asla tahmin üretilmez.
 * GPU: Adreno (kgsl) yolları, yoksa Mali/Samsung yolları denenir.
 */
final class Telemetry {
    int cpuUsage = -1;                 // toplam CPU kullanımı (%), /proc/stat farkından
    final List<int[]> clusters = new ArrayList<>(); // {anlık MHz, en yüksek MHz}, en yüksek frekansa göre sıralı (küçük → büyük)
    int gpuLoad = -1, gpuMhz = -1, gpuMaxMhz = -1;
    String gpuSource = "";

    private static long prevIdle = -1, prevTotal = -1;

    static final String CMD =
            "head -1 /proc/stat; "
            + "for p in /sys/devices/system/cpu/cpufreq/policy*; do echo \"P $(cat $p/scaling_cur_freq 2>/dev/null) $(cat $p/cpuinfo_max_freq 2>/dev/null)\"; done; "
            + "K=/sys/class/kgsl/kgsl-3d0; "
            + "echo \"GBP $(cat $K/gpu_busy_percentage 2>/dev/null)\"; "
            + "echo \"GB $(cat $K/gpubusy 2>/dev/null)\"; "
            + "echo \"GCLK $(cat $K/gpuclk 2>/dev/null)\"; "
            + "echo \"GMAX $(cat $K/max_gpuclk 2>/dev/null)\"; "
            + "echo \"MALI $(cat /sys/kernel/gpu/gpu_busy 2>/dev/null)\"; "
            + "echo \"MCLK $(cat /sys/kernel/gpu/gpu_clock 2>/dev/null)\"; "
            + "echo \"MMAX $(cat /sys/kernel/gpu/gpu_max_clock 2>/dev/null)\"";

    /** Bir örnek alır. Shizuku yoksa boş (her şey -1) döner. */
    static Telemetry sample() {
        Telemetry t = new Telemetry();
        if (!Sh.granted()) return t;
        String out = Sh.exec(CMD);
        for (String raw : out.split("\n")) {
            String l = raw.trim();
            try {
                if (l.startsWith("cpu ")) {
                    String[] f = l.split("\\s+");
                    long total = 0, idle = 0;
                    for (int i = 1; i < f.length; i++) total += Long.parseLong(f[i]);
                    idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0); // idle + iowait
                    if (prevTotal > 0 && total > prevTotal) {
                        long dt = total - prevTotal, di = idle - prevIdle;
                        t.cpuUsage = (int) Math.max(0, Math.min(100, Math.round((dt - di) * 100.0 / dt)));
                    }
                    prevTotal = total; prevIdle = idle;
                } else if (l.startsWith("P ")) {
                    String[] f = l.split("\\s+");
                    if (f.length >= 3) t.clusters.add(new int[]{Integer.parseInt(f[1]) / 1000, Integer.parseInt(f[2]) / 1000});
                } else if (l.startsWith("GBP ")) {
                    String v = l.substring(4).replace("%", "").trim();
                    if (!v.isEmpty()) { t.gpuLoad = Integer.parseInt(v.split("\\s+")[0]); t.gpuSource = "Adreno"; }
                } else if (l.startsWith("GB ") && t.gpuLoad < 0) {
                    String[] f = l.substring(3).trim().split("\\s+");
                    if (f.length == 2 && Long.parseLong(f[1]) > 0) { t.gpuLoad = (int) (Long.parseLong(f[0]) * 100 / Long.parseLong(f[1])); t.gpuSource = "Adreno"; }
                } else if (l.startsWith("GCLK ")) {
                    String v = l.substring(5).trim();
                    if (!v.isEmpty()) t.gpuMhz = (int) (Long.parseLong(v) / 1_000_000);
                } else if (l.startsWith("GMAX ")) {
                    String v = l.substring(5).trim();
                    if (!v.isEmpty()) t.gpuMaxMhz = (int) (Long.parseLong(v) / 1_000_000);
                } else if (l.startsWith("MALI ") && t.gpuLoad < 0) {
                    String v = l.substring(5).replace("%", "").trim();
                    if (!v.isEmpty()) { t.gpuLoad = Integer.parseInt(v.split("\\s+")[0]); t.gpuSource = "Mali"; }
                } else if (l.startsWith("MCLK ") && t.gpuMhz < 0) {
                    String v = l.substring(5).trim();
                    if (!v.isEmpty()) t.gpuMhz = Integer.parseInt(v.split("\\s+")[0]);
                } else if (l.startsWith("MMAX ") && t.gpuMaxMhz < 0) {
                    String v = l.substring(5).trim();
                    if (!v.isEmpty()) t.gpuMaxMhz = Integer.parseInt(v.split("\\s+")[0]);
                }
            } catch (RuntimeException ignored) {
                // bu satır okunamadı: değer -1 kalır
            }
        }
        if (t.gpuLoad > 100 || t.gpuLoad < 0) t.gpuLoad = t.gpuLoad > 100 ? -1 : t.gpuLoad;
        Collections.sort(t.clusters, (a, b) -> Integer.compare(a[1], b[1]));
        // aynı en yüksek frekanslı politikalar tek küme
        List<int[]> merged = new ArrayList<>();
        for (int[] c : t.clusters) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1)[1] == c[1]) continue;
            merged.add(c);
        }
        t.clusters.clear();
        t.clusters.addAll(merged);
        return t;
    }

    /** Küme adları: 1 → "CPU", 2 → küçük/büyük, 3+ → küçük/orta/büyük (en sonuncusu "en büyük"). */
    String clusterName(int i) {
        int n = clusters.size();
        if (n <= 1) return "CPU";
        if (i == 0) return L.t("küçük");
        if (i == n - 1) return L.t("büyük");
        return L.t("orta");
    }

    /** En büyük kümenin anlık/en yüksek frekans oranı (%); okunamazsa -1. */
    int bigClusterPct() {
        if (clusters.isEmpty()) return -1;
        int[] c = clusters.get(clusters.size() - 1);
        return c[1] > 0 ? c[0] * 100 / c[1] : -1;
    }
}
