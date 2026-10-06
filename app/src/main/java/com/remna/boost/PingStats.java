package com.remna.boost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Ping istatistikleri: en iyi, ortalama, ortanca, en kötü, dalgalanma, kayıp, kararlılık ve bölge skoru. */
final class PingStats {
    final List<Integer> samples = new ArrayList<>();
    int sent;

    void add(int ms) { sent++; if (ms > 0) samples.add(ms); }

    boolean empty() { return samples.isEmpty(); }

    int best() { return empty() ? -1 : Collections.min(samples); }

    int worst() { return empty() ? -1 : Collections.max(samples); }

    int avg() { if (empty()) return -1; long s = 0; for (int v : samples) s += v; return (int) (s / samples.size()); }

    int median() {
        if (empty()) return -1;
        List<Integer> l = new ArrayList<>(samples);
        Collections.sort(l);
        int n = l.size();
        return n % 2 == 1 ? l.get(n / 2) : (l.get(n / 2 - 1) + l.get(n / 2)) / 2;
    }

    /** Ardışık örnekler arasındaki ortalama fark (ms). */
    int jitter() {
        if (samples.size() < 2) return 0;
        long s = 0;
        for (int i = 1; i < samples.size(); i++) s += Math.abs(samples.get(i) - samples.get(i - 1));
        return (int) (s / (samples.size() - 1));
    }

    int lossPct() { return sent == 0 ? 100 : (sent - samples.size()) * 100 / sent; }

    /** Kararlılık (0–100): ortancadan sapması ortancanın %25'ini aşan örneklerin oranı düşülür. */
    int stability() {
        if (empty()) return 0;
        int m = median(), bad = 0;
        for (int v : samples) if (Math.abs(v - m) > Math.max(5, m / 4)) bad++;
        return 100 - bad * 100 / samples.size();
    }

    /**
     * Bölge skoru (0–100, yüksek iyi): %40 ortanca ping, %25 dalgalanma, %25 kayıp, %10 kararlılık.
     * Ortanca 30 ms → 100, 300 ms → 0; dalgalanma 0 → 100, 40+ → 0; kayıp %0 → 100, %20+ → 0.
     */
    int score() {
        if (empty()) return 0;
        double p = clamp(100 - (median() - 30) * 100.0 / 270);
        double j = clamp(100 - jitter() * 2.5);
        double l = clamp(100 - lossPct() * 5);
        return (int) Math.round(0.40 * p + 0.25 * j + 0.25 * l + 0.10 * stability());
    }

    static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    String summary() {
        if (empty()) return "Ulaşılamadı";
        return String.format(Locale.US, "ortanca %d ms · en iyi %d · en kötü %d · dalgalanma %d · kayıp %%%d · kararlılık %%%d",
                median(), best(), worst(), jitter(), lossPct(), stability());
    }
}
