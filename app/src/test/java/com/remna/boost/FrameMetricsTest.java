package com.remna.boost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FrameMetricsTest {
    private static float[] frames(int n, float ms) {
        float[] f = new float[n];
        Arrays.fill(f, ms);
        return f;
    }

    @Test public void sabit60Fps() {
        FrameMetrics m = FrameMetrics.fromFrames(frames(1200, 1000f / 60), new ArrayList<>(), 60);
        assertTrue(m.exact);
        assertEquals(60, Math.round(m.avgFps));
        assertEquals(60, Math.round(m.low1));
        assertEquals(60, Math.round(m.low01));
        assertEquals(100, m.frameStability);
        assertEquals(0, m.dropped);
        assertEquals(16.7, m.p99, 0.1);
    }

    @Test public void takilmalarYuzde1DusuguDusurur() {
        float[] f = frames(1000, 1000f / 60);
        for (int i = 0; i < 10; i++) f[i * 100] = 50f; // %1'lik ağır kareler
        FrameMetrics m = FrameMetrics.fromFrames(f, new ArrayList<>(), 60);
        assertEquals(20, Math.round(m.low1));      // en yavaş %1 = 50 ms → 20 FPS
        assertEquals(10, m.dropped);              // 25 ms (1.5 × 16.7) üstü
        assertTrue(m.avgFps < 60 && m.avgFps > 55);
    }

    @Test public void yuzde01AzKaredeHesaplanmaz() {
        FrameMetrics m = FrameMetrics.fromFrames(frames(500, 10f), new ArrayList<>(), 120);
        assertEquals(-1f, m.low01, 0.001);
    }

    @Test public void yaklasikHesap() {
        List<Integer> fps = Arrays.asList(60, 60, 59, 30, 60);
        FrameMetrics m = FrameMetrics.fromFps(fps);
        assertFalse(m.exact);
        assertEquals(30, Math.round(m.low1));
        assertEquals(30, Math.round(m.minFps));
        assertEquals(-1f, m.p99, 0.001); // yaklaşık modda P99 üretilmez
        assertNull(FrameMetrics.fromFps(new ArrayList<>()));
    }
}
