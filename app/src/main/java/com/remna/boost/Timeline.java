package com.remna.boost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Ortak zaman çizelgesi: birden çok seri aynı x ekseninde; her seri kendi aralığına göre ölçeklenir
 * (FPS, ping, sıcaklık ve GPU farklı birimlerde). Olaylar dikey kesikli çizgiyle işaretlenir. Geçersiz (-1) noktalar çizilmez.
 */
final class Timeline extends View {
    int[][] series = new int[0][];
    int[] colors = new int[0];
    int[] marks = new int[0];     // olayların çizelgedeki konumu (0..n-1)
    int[] markColors = new int[0];
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(), mark = new Paint(Paint.ANTI_ALIAS_FLAG);

    Timeline(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.8f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(1.2f * d);
        mark.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        for (int i = 1; i < 4; i++) cv.drawLine(0, h * i / 4, w, h * i / 4, grid);
        int n = 0;
        for (int[] s : series) n = Math.max(n, s.length);
        if (n < 2) return;
        float pad = h * 0.08f;
        for (int k = 0; k < marks.length; k++) {
            float x = w * marks[k] / (n - 1);
            mark.setColor(markColors[k]);
            cv.drawLine(x, 0, x, h, mark);
        }
        for (int si = 0; si < series.length; si++) {
            int[] s = series[si];
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int v : s) if (v >= 0) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi < 0) continue;
            if (hi - lo < 4) { lo -= 2; hi += 2; }
            line.setColor(colors[si]);
            Path p = new Path();
            boolean pen = false;
            for (int i = 0; i < s.length; i++) {
                if (s[i] < 0) { pen = false; continue; }
                float x = w * i / (n - 1);
                float y = h - pad - (h - 2 * pad) * (s[i] - lo) / (float) (hi - lo);
                if (!pen) { p.moveTo(x, y); pen = true; } else p.lineTo(x, y);
            }
            cv.drawPath(p, line);
        }
    }
}
