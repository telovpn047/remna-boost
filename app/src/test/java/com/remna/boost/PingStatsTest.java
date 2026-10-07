package com.remna.boost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PingStatsTest {
    @Test public void temelIstatistikler() {
        PingStats s = new PingStats();
        s.add(10); s.add(20); s.add(30); s.add(-1); // -1 = kayıp
        assertEquals(4, s.sent);
        assertEquals(20, s.median());
        assertEquals(10, s.best());
        assertEquals(30, s.worst());
        assertEquals(20, s.avg());
        assertEquals(25, s.lossPct());
        assertEquals(10, s.jitter()); // |20-10| ve |30-20| ortalaması
    }

    @Test public void bosIstatistik() {
        PingStats s = new PingStats();
        assertTrue(s.empty());
        assertEquals(-1, s.median());
        assertEquals(100, s.lossPct());
        assertEquals(0, s.score());
    }

    @Test public void skorDusukPingdeYuksek() {
        PingStats iyi = new PingStats(), kotu = new PingStats();
        for (int i = 0; i < 10; i++) { iyi.add(40); kotu.add(i % 2 == 0 ? 250 : -1); }
        assertTrue(iyi.score() > 90);
        assertTrue(kotu.score() < 50);
        assertTrue(iyi.score() - kotu.score() > 40);
        assertTrue(iyi.score() <= 100 && kotu.score() >= 0);
    }

    @Test public void kararlilik() {
        PingStats s = new PingStats();
        for (int i = 0; i < 9; i++) s.add(100);
        s.add(300); // tek sapma
        assertEquals(90, s.stability());
    }
}
