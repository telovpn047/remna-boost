package com.remna.boost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Oyun Sağlığı halkası: 0–100 arası dolan yay, ortada skor. Değer değişince yumuşakça animasyonla geçer. */
final class HealthRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shown = 0;
    private int target = -1, color = 0xFF5B6476;
    private ValueAnimator anim;

    HealthRing(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(7 * d);
        track.setColor(0xFF151C2B);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(7 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(0xFFF5F7FA);
    }

    /** score < 0: ölçülüyor (boş halka, "—"). */
    void setScore(int score, int col) {
        color = col;
        if (score == target) { invalidate(); return; }
        target = score;
        if (anim != null) anim.cancel();
        float to = Math.max(0, score);
        anim = ValueAnimator.ofFloat(shown, to);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2, cy = h / 2;
        float r = s / 2 - track.getStrokeWidth();
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        cv.drawArc(o, 0, 360, false, track);
        if (target >= 0) {
            arc.setColor(color);
            cv.drawArc(o, -90, 360 * shown / 100f, false, arc);
        }
        txt.setTextSize(s * 0.32f);
        String v = target < 0 ? "—" : String.valueOf(Math.round(shown));
        cv.drawText(v, cx, cy + s * 0.11f, txt);
    }
}
