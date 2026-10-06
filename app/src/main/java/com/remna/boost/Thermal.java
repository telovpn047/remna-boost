package com.remna.boost;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Termal yönetici: pil sıcaklığına göre durum (NORMAL / WARM / HOT / PROTECT) ve uyarlanabilir yenileme hızı.
 * Isınınca hemen düşürür; soğuyunca ancak belli bir süre bekleyip birer kademe yükseltir (histerezis + bekleme).
 */
final class Thermal {
    enum State { NORMAL, WARM, HOT, PROTECT }

    State state = State.NORMAL;
    long coolSince = 0;
    int appliedHz = 0; // 0 = kullanıcının oyun ayarı geçerli

    static float[] thresholds(Context c) {
        // {warm, hot, protect}
        switch (Boost.prefs(c).getString("thermal", "standard")) {
            case "sensitive": return new float[]{38, 40, 42};
            case "off": return null;
            default: return new float[]{40, 42, 44};
        }
    }

    static State classify(float t, float[] th) {
        if (t >= th[2]) return State.PROTECT;
        if (t >= th[1]) return State.HOT;
        if (t >= th[0]) return State.WARM;
        return State.NORMAL;
    }

    static int hzFor(Context c, State s) {
        float max = Tweaks.maxRefresh(c);
        switch (s) {
            case WARM: return max > 90 ? 90 : 0;
            case HOT:
            case PROTECT: return 60;
            default: return 0;
        }
    }

    /** Her ölçümde çağrılır. Durum değiştiyse yenileme hızını ayarlar; yeni durumu döner (değişmediyse null). */
    State update(Context c, float temp) {
        float[] th = thresholds(c);
        if (th == null) {
            if (appliedHz != 0) { appliedHz = 0; Tweaks.reapply(c); }
            state = State.NORMAL;
            return null;
        }
        State target = classify(temp, th);
        State old = state;
        if (target.ordinal() > state.ordinal()) {
            state = target; // ısınma: hemen kademe atla
            coolSince = 0;
        } else if (target.ordinal() < state.ordinal()) {
            // soğuma: alt eşiğin 1°C altında 60 sn kalınca bir kademe yükselt
            float floor = state == State.WARM ? th[0] - 1 : state == State.HOT ? th[1] - 1 : th[2] - 1;
            if (temp < floor) {
                if (coolSince == 0) coolSince = System.currentTimeMillis();
                else if (System.currentTimeMillis() - coolSince > 60_000) {
                    state = State.values()[state.ordinal() - 1];
                    coolSince = 0;
                }
            } else coolSince = 0;
        }
        if (state == old) return null;
        int hz = hzFor(c, state);
        if (hz != appliedHz) {
            appliedHz = hz;
            if (hz == 0) Tweaks.reapply(c);
            else Tweaks.forceHz(c, hz);
        }
        return state;
    }

    static String label(State s) {
        switch (s) {
            case WARM: return L.t("Ilık");
            case HOT: return L.t("Sıcak");
            case PROTECT: return L.t("Termal koruma");
            default: return L.t("Normal");
        }
    }
}
