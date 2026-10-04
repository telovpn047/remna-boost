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

    static void apply(Context c) {
        SharedPreferences p = Boost.prefs(c);
        if (p.getBoolean("tw_applied", false)) return;
        SharedPreferences.Editor e = p.edit();
        if (p.getBoolean("anim", true) && secureAllowed(c)) {
            for (String k : ANIM) {
                try {
                    e.putString("prev_" + k, String.valueOf(Settings.Global.getFloat(c.getContentResolver(), k, 1f)));
                    Settings.Global.putFloat(c.getContentResolver(), k, 0.5f);
                } catch (Throwable ignored) {}
            }
        }
        if (systemAllowed(c) || secureAllowed(c)) {
            if (p.getBoolean("hz", true)) {
                float hz = maxRefresh(c);
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
        e.putBoolean("tw_applied", false).apply();
    }
}
