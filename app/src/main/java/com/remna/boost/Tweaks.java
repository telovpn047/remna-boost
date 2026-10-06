package com.remna.boost;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.Display;
import android.hardware.display.DisplayManager;

/** Oyun süresince sistem ayarları; oyundan çıkınca eski değerlere döner. */
final class Tweaks {
    private Tweaks() {}

    static final String[] ANIM = {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"};

    /** Tek seferlik ADB izni verilmiş mi? (pm grant ... WRITE_SECURE_SETTINGS) */
    static boolean secureAllowed(Context c) {
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED && Sh.granted())
            Sh.exec("pm grant " + c.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static boolean systemAllowed(Context c) { return Settings.System.canWrite(c); }

    static float maxRefresh(Context c) {
        float m = 60;
        DisplayManager dm = c.getSystemService(DisplayManager.class);
        Display d = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (d != null) for (Display.Mode mode : d.getSupportedModes()) m = Math.max(m, mode.getRefreshRate());
        return m;
    }

    /** Ekranın desteklediği yenileme hızları (60, 90, 120…). */
    static java.util.List<Integer> refreshRates(Context c) {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        DisplayManager dm = c.getSystemService(DisplayManager.class);
        Display d = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (d != null) for (Display.Mode mode : d.getSupportedModes()) set.add(Math.round(mode.getRefreshRate()));
        if (set.isEmpty()) set.add(60);
        return new java.util.ArrayList<>(set);
    }

    /** Samsung "Dokunma hassasiyeti" ayarının anahtarı (One UI). */
    static final String TOUCH_KEY = "auto_adjust_touch";

    /** Bu telefonda Samsung dokunma hassasiyeti ayarı var mı? */
    static boolean touchSupported(Context c) {
        return Settings.System.getString(c.getContentResolver(), TOUCH_KEY) != null;
    }

    static void setTouch(Context c, int v) {
        try { Settings.System.putInt(c.getContentResolver(), TOUCH_KEY, v); }
        catch (Throwable t) { Sh.exec("settings put system " + TOUCH_KEY + " " + v); }
    }

    /** Soğutma modu: yenileme hızını geçici olarak sabitler (oyun modu bitince restore() eski değeri geri yükler). */
    static void forceHz(Context c, float hz) {
        for (String k : new String[]{"peak_refresh_rate", "min_refresh_rate"}) {
            try { Settings.System.putFloat(c.getContentResolver(), k, hz); } catch (Throwable ignored) {}
        }
    }

    /** Oyun modu açıkken ayar değişince hemen uygula. */
    static void reapply(Context c) {
        restore(c);
        apply(c);
        Boost.dndOff(c);
        if (!Boost.prefs(c).getString("dnd_mode", "priority").equals("off")) Boost.dndOn(c);
    }

    static void apply(Context c) {
        SharedPreferences p = Boost.prefs(c);
        if (p.getBoolean("tw_applied", false)) return;
        SharedPreferences.Editor e = p.edit();
        String anim = p.getString("anim_mode", "0.5");
        if (!anim.equals("off") && secureAllowed(c)) {
            for (String k : ANIM) {
                try {
                    e.putString("prev_" + k, String.valueOf(Settings.Global.getFloat(c.getContentResolver(), k, 1f)));
                    Settings.Global.putFloat(c.getContentResolver(), k, Float.parseFloat(anim));
                } catch (Throwable ignored) {}
            }
        }
        if (systemAllowed(c) || secureAllowed(c)) {
            String hzMode = p.getString("hz_mode", "max");
            if (!hzMode.equals("off")) {
                float hz = hzMode.equals("max") ? maxRefresh(c) : Float.parseFloat(hzMode);
                for (String k : new String[]{"peak_refresh_rate", "min_refresh_rate"}) {
                    try {
                        e.putString("prev_" + k, Settings.System.getString(c.getContentResolver(), k));
                        Settings.System.putFloat(c.getContentResolver(), k, hz);
                    } catch (Throwable ignored) {}
                }
            }
            if (p.getBoolean("autobright", true)) {
                try {
                    e.putInt("prev_bmode", Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0));
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                } catch (Throwable ignored) {}
            }
        }
        if (p.getBoolean("touch", true) && touchSupported(c) && (secureAllowed(c) || Sh.granted())) {
            e.putInt("prev_touch", Settings.System.getInt(c.getContentResolver(), TOUCH_KEY, 0));
            setTouch(c, 1);
        }
        e.putBoolean("tw_applied", true).apply();
    }

    static void restore(Context c) {
        SharedPreferences p = Boost.prefs(c);
        if (!p.getBoolean("tw_applied", false)) return;
        SharedPreferences.Editor e = p.edit();
        for (String k : ANIM) {
            String v = p.getString("prev_" + k, null);
            if (v != null) try { Settings.Global.putFloat(c.getContentResolver(), k, Float.parseFloat(v)); } catch (Throwable ignored) {}
            e.remove("prev_" + k);
        }
        for (String k : new String[]{"peak_refresh_rate", "min_refresh_rate"}) {
            if (!p.contains("prev_" + k)) continue;
            String v = p.getString("prev_" + k, null);
            try { Settings.System.putString(c.getContentResolver(), k, v); } catch (Throwable ignored) {}
            e.remove("prev_" + k);
        }
        if (p.contains("prev_bmode")) {
            try { Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("prev_bmode", 1)); } catch (Throwable ignored) {}
            e.remove("prev_bmode");
        }
        if (p.contains("prev_touch")) {
            setTouch(c, p.getInt("prev_touch", 0));
            e.remove("prev_touch");
        }
        e.putBoolean("tw_applied", false).apply();
    }
}
