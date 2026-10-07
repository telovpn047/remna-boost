package com.remna.boost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Oyun oturumunun canlı verileri. Yalnız bellekte tutulur (diske yazılmaz); grafikler ve panel buradan okur. */
final class Live {
    private Live() {}

    static volatile int fps = -1, ping = -1;
    static volatile float cpu = -1, batt = 0;
    static volatile String pingSrc = "";
    static volatile boolean inGame;
    static volatile Telemetry tele;
    private static long sampleCounter;
    static volatile int targetFps = 60;
    private static final int MAX_FRAMES = 60000;
    private static float[] frameBuf = new float[MAX_FRAMES];
    private static int frameN;
    /** Tahmini (HTTP) ping ölçümleri: kayıp sayılmaz, ayrı tutulur. */
    private static final ArrayDeque<Integer> estPing = new ArrayDeque<>();
    static volatile int icmpSent, icmpLost, estFail, spikes;
    static volatile String lastSpike = "";

    static final int N = 150; // 2 sn aralıkla ~5 dakika
    private static final int[] gpuH = new int[N], cpuH = new int[N];
    private static final int[] fpsH = new int[N], pingH = new int[N], tempH = new int[N], ftH = new int[N];
    static volatile float ftAvg = -1, ftMax = -1;
    static volatile int stutterTotal, heavyTotal;
    private static int count, pos;
    private static final ArrayDeque<Integer> recentPing = new ArrayDeque<>();
    private static final List<Integer> sessionFps = new ArrayList<>();

    static synchronized void reset() {
        count = 0; pos = 0;
        recentPing.clear();
        sessionFps.clear();
        estPing.clear();
        frameN = 0;
        tele = null;
        sampleCounter = 0;
        icmpSent = 0; icmpLost = 0; estFail = 0; spikes = 0; lastSpike = "";
        fps = -1; ping = -1; cpu = -1;
        ftAvg = -1; ftMax = -1; stutterTotal = 0; heavyTotal = 0;
    }

    /** Kare süresi örneği (ms). Geçersizse -1. */
    static void frame(boolean valid, float avg, float max, int st, int hv) {
        if (!valid) { ftAvg = -1; ftMax = -1; return; }
        ftAvg = avg; ftMax = max;
        stutterTotal += st;
        heavyTotal += hv;
    }

    /** Kare süreleri ekle (yalnız oyun ön plandayken çağrılır). */
    static synchronized void addFrames(float[] ft) {
        for (float v : ft) {
            if (frameN >= MAX_FRAMES) { // en eski yarıyı at
                System.arraycopy(frameBuf, MAX_FRAMES / 2, frameBuf, 0, MAX_FRAMES / 2);
                frameN = MAX_FRAMES / 2;
            }
            frameBuf[frameN++] = v;
        }
    }

    /**
     * Ping örneği. method "icmp": gerçek ICMP; başarısızlık paket kaybıdır. "est": tahmini HTTP ölçümü; başarısızlığı
     * kayıp değil "tahmin başarısız" sayılır ve istatistiklere karışmaz.
     */
    static synchronized void pushPing(int p, String method) {
        if ("icmp".equals(method)) {
            icmpSent++;
            if (p < 0) icmpLost++;
            // ani yükselme: ortancanın 1.8 katı ve 40 ms üstü
            PingStats cur = pingStats();
            int med = cur.median();
            if (p > 0 && med > 0 && cur.samples.size() >= 5 && p > med * 1.8 && p - med > 40) {
                spikes++;
                lastSpike = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + "  " + med + " → " + p + " ms";
            }
            recentPing.addLast(p);
            while (recentPing.size() > 30) recentPing.removeFirst();
        } else {
            if (p < 0) estFail++;
            else { estPing.addLast(p); while (estPing.size() > 30) estPing.removeFirst(); }
        }
    }

    /** Yalnız tahmini ölçümler varsa onların istatistiği (kayıp hesaplanmaz). */
    static synchronized PingStats estStats() {
        PingStats s = new PingStats();
        for (int p : estPing) s.add(p);
        return s;
    }

    /** Oturum FPS/kare metrikleri; kare verisi varsa kesin, yoksa saniyelik FPS'ten yaklaşık. */
    static synchronized FrameMetrics metrics() {
        List<Integer> f = new ArrayList<>(sessionFps);
        if (frameN >= 120) return FrameMetrics.fromFrames(java.util.Arrays.copyOf(frameBuf, frameN), f, targetFps);
        return FrameMetrics.fromFps(f);
    }

    static synchronized void sample(int f, int p, float t) {
        fpsH[pos] = f; pingH[pos] = p; tempH[pos] = Math.round(t); ftH[pos] = ftMax < 0 ? -1 : Math.round(ftMax);
        Telemetry tl = tele;
        gpuH[pos] = tl == null ? -1 : tl.gpuLoad;
        cpuH[pos] = tl == null ? -1 : tl.cpuUsage;
        sampleCounter++;
        pos = (pos + 1) % N;
        if (count < N) count++;
        // 10 FPS altı: yükleme ekranı / arka plana geçiş — oyun performansı değil, istatistiğe katma
        if (f >= 10) sessionFps.add(f);
    }

    private static int[] ordered(int[] a) {
        int[] r = new int[count];
        int start = (pos - count + N) % N;
        for (int i = 0; i < count; i++) r[i] = a[(start + i) % N];
        return r;
    }

    static synchronized int[] fpsSeries() { return ordered(fpsH); }

    static synchronized int[] pingSeries() { return ordered(pingH); }

    static synchronized int[] tempSeries() { return ordered(tempH); }

    static synchronized int[] ftSeries() { return ordered(ftH); }

    static synchronized int[] gpuSeries() { return ordered(gpuH); }

    static synchronized int[] cpuSeries() { return ordered(cpuH); }

    /** Şu anki örneğin toplam sırası (olayları zaman çizelgesine yerleştirmek için). */
    static synchronized int sampleIndex() { return (int) sampleCounter; }

    /** Zaman çizelgesinin ilk örneğinin toplam sırası. */
    static synchronized int firstIndex() { return (int) (sampleCounter - count); }

    /** Son 30 ping ölçümünden istatistik (başarısızlar kayıp sayılır). */
    static synchronized PingStats pingStats() {
        PingStats s = new PingStats();
        for (int p : recentPing) s.add(p);
        return s;
    }

    /** {ortalama, %1 düşük, örnek sayısı}; FPS hiç ölçülmediyse null. %1 düşük: en düşük %1 örneklerin ortalaması. */
    static synchronized int[] fpsStats() {
        if (sessionFps.isEmpty()) return null;
        List<Integer> s = new ArrayList<>(sessionFps);
        Collections.sort(s);
        long sum = 0;
        for (int v : s) sum += v;
        int k = Math.max(1, s.size() / 100);
        long low = 0;
        for (int i = 0; i < k; i++) low += s.get(i);
        return new int[]{(int) (sum / s.size()), (int) (low / k), s.size()};
    }
}
