package com.remna.boost;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Oyun sırasında olay tespiti: FPS düşüşü (nedeniyle), termal kısılma, ani ping yükselmesi ve darboğaz sınıflandırması.
 * Kurallar ölçülen verilere dayanır; veri yoksa neden "belirlenemedi" denir, tahmin uydurulmaz.
 */
final class Diagnose {
    private Diagnose() {}

    static final class Ev {
        long t;
        int idx;          // zaman çizelgesindeki örnek sırası
        String type;      // FPS / THERMAL / PING
        String title, detail, rec;
    }

    static final List<Ev> events = new ArrayList<>();
    private static final List<Integer> fpsWin = new ArrayList<>();
    private static float startTemp = -1;
    private static int gpuPeakMhz = -1, fpsPeakMedian = -1, lastSpikeCount;
    private static long lastFpsEv, lastThermalEv;

    static synchronized void reset() {
        events.clear();
        fpsWin.clear();
        startTemp = -1; gpuPeakMhz = -1; fpsPeakMedian = -1; lastSpikeCount = 0;
        lastFpsEv = 0; lastThermalEv = 0;
    }

    static synchronized List<Ev> snapshot() { return new ArrayList<>(events); }

    private static void add(String type, String title, String detail, String rec) {
        Ev e = new Ev();
        e.t = System.currentTimeMillis();
        e.idx = Live.sampleIndex();
        e.type = type; e.title = title; e.detail = detail; e.rec = rec;
        events.add(e);
        while (events.size() > 50) events.remove(0);
    }

    static String time() { return new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()); }

    /** Darboğaz sınıfı: "GPU", "CPU", "THERMAL" ya da null (veri yok / belirgin değil). */
    static String bottleneck(Telemetry t, int thermalStatus, float tempRise) {
        if (t == null) return null;
        boolean freqDrop = t.gpuMhz > 0 && gpuPeakMhz > 0 && t.gpuMhz < gpuPeakMhz * 0.75f;
        if (thermalStatus >= 3 || (freqDrop && t.gpuLoad >= 70 && tempRise >= 3)) return "THERMAL";
        if (t.gpuLoad >= 90 && (t.cpuUsage < 0 || t.cpuUsage < 75)) return "GPU";
        if (t.cpuUsage >= 80 && (t.gpuLoad < 0 || t.gpuLoad < 75)) return "CPU";
        return null;
    }

    static String bottleneckText(String b) {
        if ("GPU".equals(b)) return L.t("GPU darboğazı");
        if ("CPU".equals(b)) return L.t("CPU darboğazı");
        if ("THERMAL".equals(b)) return L.t("Termal darboğaz");
        return null;
    }

    static String recFor(String b) {
        if ("GPU".equals(b)) return L.t("Grafik kalitesini ya da çözünürlüğü düşür.");
        if ("CPU".equals(b)) return L.t("CPU yükü yüksek: FPS hedefini düşür ya da arka plan uygulamalarını kapat.");
        if ("THERMAL".equals(b)) return L.t("Cihaz ısınıyor: Serin profili seç ya da cihazı soğut.");
        return L.t("Belirgin bir donanım nedeni yok; oyundaki sahne yükünden olabilir.");
    }

    private static int median(List<Integer> l) {
        List<Integer> s = new ArrayList<>(l);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /** Servisin her ölçümünde (≈2 sn) çağrılır; yalnız oyun ön plandayken. */
    static synchronized void tick(Context c, int fps, float batt) {
        Telemetry t = Live.tele;
        if (startTemp < 0 && batt > 0) startTemp = batt;
        if (t != null && t.gpuMhz > gpuPeakMhz) gpuPeakMhz = t.gpuMhz;
        int ts = Perf.thermalStatus(c);
        float rise = startTemp > 0 ? batt - startTemp : 0;
        long now = System.currentTimeMillis();

        // 1) FPS düşüşü: son 10 geçerli örneğin ortancasının %75'inin altı
        if (fps >= 10) {
            if (fpsWin.size() >= 6) {
                int m = median(fpsWin);
                fpsPeakMedian = Math.max(fpsPeakMedian, m);
                if (m >= 25 && fps < m * 0.75f && now - lastFpsEv > 20_000) {
                    lastFpsEv = now;
                    String b = bottleneck(t, ts, rise);
                    StringBuilder d = new StringBuilder("FPS " + m + " → " + fps);
                    if (t != null && t.gpuLoad >= 0) d.append(" · GPU %").append(t.gpuLoad);
                    if (t != null && t.cpuUsage >= 0) d.append(" · CPU %").append(t.cpuUsage);
                    d.append(String.format(Locale.US, " · %.0f°C", batt));
                    String why = bottleneckText(b);
                    add("FPS", time() + "  " + L.t("FPS DÜŞÜŞÜ") + (why != null ? " · " + why : ""), d.toString(), recFor(b));
                }
            }
            fpsWin.add(fps);
            while (fpsWin.size() > 10) fpsWin.remove(0);
        }

        // 2) Termal kısılma: sıcaklık ↑ + GPU frekansı ↓ (yük altındayken) ya da cihaz kısılma bildiriyor
        boolean freqDrop = t != null && t.gpuMhz > 0 && gpuPeakMhz > 0 && t.gpuMhz < gpuPeakMhz * 0.75f && t.gpuLoad >= 60;
        if ((ts >= 3 || (freqDrop && rise >= 3)) && now - lastThermalEv > 60_000) {
            lastThermalEv = now;
            StringBuilder d = new StringBuilder(String.format(Locale.US, "%.0f → %.0f°C", startTemp, batt));
            if (freqDrop) d.append(" · GPU ").append(gpuPeakMhz).append(" → ").append(t.gpuMhz).append(" MHz");
            if (fpsPeakMedian > 0 && fps >= 10) d.append(" · FPS ").append(fpsPeakMedian).append(" → ").append(fps);
            if (ts >= 3) d.append(" · ").append(L.t("cihaz kısılma bildiriyor"));
            add("THERMAL", time() + "  " + L.t("TERMAL KISILMA TESPİT EDİLDİ"), d.toString(), recFor("THERMAL"));
        }

        // 3) Ani ping yükselmesi (Live tarafından sayılır)
        if (Live.spikes > lastSpikeCount) {
            lastSpikeCount = Live.spikes;
            add("PING", L.t("ANİ PİNG YÜKSELMESİ"), Live.lastSpike, L.t("Wi-Fi sinyalini ya da ağdaki diğer indirmeleri kontrol et."));
        }
    }

    /** Anlık darboğaz (Performans ekranı için). */
    static String current(Context c) {
        Telemetry t = Live.tele;
        float rise = startTemp > 0 ? Live.batt - startTemp : 0;
        return bottleneck(t, Perf.thermalStatus(c), rise);
    }
}
