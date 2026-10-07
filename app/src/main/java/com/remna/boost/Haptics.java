package com.remna.boost;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Kısa dokunsal geri bildirimler (ayarlardan kapatılabilir). Sürekli titreşim yok. */
final class Haptics {
    private Haptics() {}

    private static void play(Context c, int predefined, long fallbackMs) {
        if (!Boost.prefs(c).getBoolean("haptics", true)) return;
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(predefined));
            else v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) {}
    }

    /** BOOST'a basınca: hafif. */
    static void light(Context c) { play(c, VibrationEffect.EFFECT_TICK, 10); }

    /** BOOST tamamlandı: başarı. */
    static void success(Context c) { play(c, VibrationEffect.EFFECT_DOUBLE_CLICK, 30); }

    /** Termal uyarı: belirgin tek vuruş. */
    static void warn(Context c) { play(c, VibrationEffect.EFFECT_HEAVY_CLICK, 60); }
}
