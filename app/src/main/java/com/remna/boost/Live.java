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

    static final int N = 150; // 2 sn aralıkla ~5 dakika
    private static final int[] fpsH = new int[N], pingH = new int[N], tempH = new int[N];
    private static int count, pos;
    private static final ArrayDeque<Integer> recentPing = new ArrayDeque<>();
    private static final List<Integer> sessionFps = new ArrayList<>();

    static synchronized void reset() {
        count = 0; pos = 0;
        recentPing.clear();
        sessionFps.clear();
        fps = -1; ping = -1; cpu = -1;
    }

    static synchronized void pushPing(int p) {
        recentPing.addLast(p);
        while (recentPing.size() > 30) recentPing.removeFirst();
    }

    static synchronized void sample(int f, int p, float t) {
        fpsH[pos] = f; pingH[pos] = p; tempH[pos] = Math.round(t);
        pos = (pos + 1) % N;
        if (count < N) count++;
        if (f >= 0) sessionFps.add(f);
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
