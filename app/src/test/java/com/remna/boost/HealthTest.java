package com.remna.boost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

public class HealthTest {
    private static final long GB = 1024L * 1024 * 1024;

    private static PingStats ping(int ms, int n) {
        PingStats s = new PingStats();
        for (int i = 0; i < n; i++) s.add(ms);
        return s;
    }

    private static FrameMetrics fm(int fps) {
        float[] f = new float[1200];
        Arrays.fill(f, 1000f / fps);
        return FrameMetrics.fromFrames(f, new ArrayList<>(), fps);
    }

    @Test public void herSeyIyi() {
        Health h = Health.compute(fm(120), 120, ping(40, 10), 33f, 6 * GB, 12 * GB);
        assertTrue(h.total >= 90);
        assertEquals("MÜKEMMEL", Health.label(h.total));
        assertTrue(h.issues.isEmpty());
    }

    @Test public void hedefFpsyeGorePuanlar() {
        // aynı 60 FPS: hedef 60 iken iyi, hedef 120 iken "hedefin altında"
        Health h60 = Health.compute(fm(60), 60, null, 0, 0, 0);
        Health h120 = Health.compute(fm(60), 120, null, 0, 0, 0);
        assertTrue(h60.fps > h120.fps);
        boolean uyari = false;
        for (String s : h120.issues) if (s.startsWith("Hedefin altında")) uyari = true;
        assertTrue(uyari);
    }

    @Test public void olculemeyenBilesenSkoraKatilmaz() {
        Health h = Health.compute(null, 60, null, 0, 0, 0);
        assertEquals(-1, h.total);
        assertEquals(-1, h.fps);
        assertEquals("ÖLÇÜLÜYOR", Health.label(h.total));
    }

    @Test public void sicaklikOneriUretir() {
        Health h = Health.compute(null, 60, null, 45f, 6 * GB, 12 * GB);
        assertTrue(h.thermal < 65);
        assertTrue(h.rec != null && h.rec.contains("90 Hz"));
    }
}
