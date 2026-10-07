package com.remna.boost;

import java.util.ArrayList;
import java.util.List;

/** Oyun Sağlığı skoru (0–100): FPS kararlılığı, ağ, sıcaklık ve bellek. Ölçülemeyen bileşen hesaba katılmaz. */
final class Health {
    int total = -1, fps = -1, net = -1, thermal = -1, mem = -1;
    final List<String> issues = new ArrayList<>(), oks = new ArrayList<>();
    String rec;

    static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    static Health compute(FrameMetrics fm, int target, PingStats ps, float batt, long avail, long totalRam) {
        Health h = new Health();
        double sum = 0, w = 0;
        if (fm != null && fm.avgFps > 0) {
            // hedefe yakınlık (sabit 60 değil, oyunun hedef FPS'i), %1 düşük kararlılığı ve kare kararlılığı
            double reach = clamp(fm.avgFps * 100.0 / Math.max(1, target));
            double stab = clamp(fm.low1 * 100.0 / Math.max(1, fm.avgFps));
            double fst = fm.frameStability >= 0 ? fm.frameStability : stab;
            h.fps = (int) Math.round(0.5 * reach + 0.3 * stab + 0.2 * fst);
            sum += h.fps * 0.35; w += 0.35;
            if (reach < 85) h.issues.add(L.t("Hedefin altında: ") + Math.round(fm.avgFps) + " / " + target + " FPS");
            if (stab < 75) h.issues.add(L.t("FPS dalgalanıyor (%1 düşük ") + Math.round(fm.low1) + ")");
            else if (reach >= 85) h.oks.add(L.t("FPS kararlı"));
        }
        if (ps != null && !ps.empty()) {
            double med = clamp(100 - (ps.median() - 40) * 100.0 / 210);
            double jit = clamp(100 - ps.jitter() * 3.0);
            double loss = clamp(100 - ps.lossPct() * 8.0);
            h.net = (int) Math.round(0.6 * med + 0.2 * jit + 0.2 * loss);
            sum += h.net * 0.30; w += 0.30;
            if (ps.median() > 150) h.issues.add(L.t("Ping yüksek (") + ps.median() + " ms)");
            if (ps.jitter() > 15) h.issues.add(L.t("Bağlantı dalgalı (dalgalanma ") + ps.jitter() + " ms)");
            if (ps.lossPct() > 2) h.issues.add(L.t("Paket kaybı %") + ps.lossPct());
            if (h.net >= 80) h.oks.add(L.t("Ağ stabil"));
        }
        if (batt > 0) {
            h.thermal = (int) Math.round(batt <= 35 ? 100 : batt >= 46 ? 10 : 100 - (batt - 35) * 90 / 11);
            sum += h.thermal * 0.20; w += 0.20;
            if (h.thermal < 65) h.issues.add(String.format(java.util.Locale.US, L.t("Sıcaklık yüksek (%.0f°C)"), batt));
            else h.oks.add(L.t("Sıcaklık normal"));
        }
        if (totalRam > 0) {
            double pct = avail * 100.0 / totalRam;
            h.mem = (int) Math.round(clamp(pct >= 30 ? 100 : 40 + (pct - 10) * 3));
            sum += h.mem * 0.15; w += 0.15;
            if (h.mem < 60) h.issues.add(L.t("Boş RAM az (%") + Math.round(pct) + ")");
        }
        if (w > 0) h.total = (int) Math.round(sum / w);
        if (h.thermal >= 0 && h.thermal < 65) h.rec = L.t("Yenileme hızını 90 Hz'e düşür ya da Serin profili seç.");
        else if (h.fps >= 0 && h.fps < 75) h.rec = L.t("Dengeli profili dene: FPS 60'a sabitlenir, dalgalanma azalır.");
        else if (h.net >= 0 && h.net < 60) h.rec = L.t("Ağ sekmesinden en düşük ve en stabil bölgeyi bul.");
        else if (h.mem >= 0 && h.mem < 60) h.rec = L.t("Akıllı temizliği aç ya da arka plandaki uygulamaları kapat.");
        return h;
    }

    static String label(int score) {
        if (score < 0) return L.t("ÖLÇÜLÜYOR");
        if (score >= 90) return L.t("MÜKEMMEL");
        if (score >= 75) return L.t("İYİ");
        if (score >= 55) return L.t("ORTA");
        return L.t("ZAYIF");
    }
}
