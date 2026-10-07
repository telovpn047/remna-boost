package com.remna.boost;

import java.util.Arrays;
import java.util.List;

/**
 * FPS ve kare süresi metrikleri. Kesin hesap: tek tek kare süreleri (SurfaceFlinger zaman damgaları).
 * Yaklaşık hesap: yalnız saniyelik FPS örnekleri varsa (timestats); bu durumda P95/P99 ve %0.1 düşük hesaplanmaz.
 * %1 düşük / %0.1 düşük: en yavaş %1 / %0.1 karenin ortalama süresinden FPS (sektör standardı).
 */
final class FrameMetrics {
    boolean exact;           // kare bazında mı?
    int frames;              // ölçülen kare sayısı (kesin) ya da örnek sayısı (yaklaşık)
    float avgFps = -1, low1 = -1, low01 = -1, minFps = -1;
    float ftAvg = -1, p95 = -1, p99 = -1;
    int dropped = -1;        // hedef kare süresinin 1.5 katını aşan kareler
    int frameStability = -1; // ortanca kare süresinin ±%25'i içindeki karelerin oranı
    int fpsStability = -1;   // 100 − FPS değişkenlik katsayısı

    static FrameMetrics fromFrames(float[] ft, List<Integer> fpsSamples, int targetFps) {
        FrameMetrics m = new FrameMetrics();
        m.exact = true;
        m.frames = ft.length;
        float[] s = ft.clone();
        Arrays.sort(s);
        double sum = 0;
        for (float v : s) sum += v;
        m.ftAvg = (float) (sum / s.length);
        m.avgFps = 1000f / m.ftAvg;
        m.p95 = s[Math.min(s.length - 1, (int) Math.floor(s.length * 0.95))];
        m.p99 = s[Math.min(s.length - 1, (int) Math.floor(s.length * 0.99))];
        m.low1 = 1000f / worstAvg(s, 0.01);
        m.low01 = s.length >= 1000 ? 1000f / worstAvg(s, 0.001) : -1; // %0.1 için en az 1000 kare
        float med = s[s.length / 2];
        int in = 0;
        for (float v : s) if (Math.abs(v - med) <= med * 0.25f) in++;
        m.frameStability = in * 100 / s.length;
        float budget = 1000f / Math.max(1, targetFps);
        int d = 0;
        for (float v : s) if (v > budget * 1.5f) d++;
        m.dropped = d;
        fpsSide(m, fpsSamples);
        return m;
    }

    static FrameMetrics fromFps(List<Integer> fps) {
        if (fps.isEmpty()) return null;
        FrameMetrics m = new FrameMetrics();
        m.exact = false;
        m.frames = fps.size();
        int[] s = new int[fps.size()];
        for (int i = 0; i < s.length; i++) s[i] = fps.get(i);
        Arrays.sort(s);
        long sum = 0;
        for (int v : s) sum += v;
        m.avgFps = (float) sum / s.length;
        int k = Math.max(1, s.length / 100);
        long low = 0;
        for (int i = 0; i < k; i++) low += s[i];
        m.low1 = (float) low / k; // yaklaşık: en düşük %1 saniyelik örnek
        m.ftAvg = 1000f / m.avgFps;
        fpsSide(m, fps);
        return m;
    }

    private static float worstAvg(float[] sorted, double frac) {
        int k = Math.max(1, (int) Math.round(sorted.length * frac));
        double s = 0;
        for (int i = sorted.length - k; i < sorted.length; i++) s += sorted[i];
        return (float) (s / k);
    }

    private static void fpsSide(FrameMetrics m, List<Integer> fps) {
        if (fps == null || fps.isEmpty()) return;
        int mn = Integer.MAX_VALUE;
        double sum = 0;
        for (int v : fps) { sum += v; mn = Math.min(mn, v); }
        m.minFps = mn;
        double mean = sum / fps.size(), var = 0;
        for (int v : fps) var += (v - mean) * (v - mean);
        double cv = mean > 0 ? Math.sqrt(var / fps.size()) / mean : 1;
        m.fpsStability = (int) Math.max(0, Math.min(100, Math.round(100 - cv * 100)));
    }

    static String f(float v) { return v < 0 ? "—" : String.valueOf(Math.round(v)); }

    static String ms(float v) { return v < 0 ? "—" : String.format(java.util.Locale.US, "%.1f ms", v); }
}
