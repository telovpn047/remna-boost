package com.remna.boost;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Oyun bazlı profiller: her oyunun kendi ayarları saklanır; oyun değişince önceki oyunun ayarları kaydedilip
 * yeni oyununki yüklenir. Resmi destek yalnız test edilen PUBG Mobile sürümleri içindir.
 */
final class GameProfiles {
    private GameProfiles() {}

    static final String[] KEYS = {"profile", "hz_mode", "cleanup", "thermal", "dnd_mode", "ov_mode", "gm_mode", "gm_scale", "gm_fps", "anim_mode"};
    static final String[] BOOL_KEYS = {"touch", "autobright", "fps"};

    static void save(Context c, String pkg) {
        if (pkg == null) return;
        SharedPreferences p = Boost.prefs(c);
        JSONObject o = new JSONObject();
        try {
            for (String k : KEYS) if (p.contains(k)) o.put(k, p.getString(k, ""));
            for (String k : BOOL_KEYS) if (p.contains(k)) o.put(k, p.getBoolean(k, true));
        } catch (Exception ignored) {}
        p.edit().putString("gp_" + pkg, o.toString()).apply();
    }

    /** Kayıtlı profil varsa yükler ve true döner. */
    static boolean load(Context c, String pkg) {
        if (pkg == null) return false;
        SharedPreferences p = Boost.prefs(c);
        String js = p.getString("gp_" + pkg, null);
        if (js == null) return false;
        try {
            JSONObject o = new JSONObject(js);
            SharedPreferences.Editor e = p.edit();
            for (String k : KEYS) if (o.has(k)) e.putString(k, o.getString(k));
            for (String k : BOOL_KEYS) if (o.has(k)) e.putBoolean(k, o.getBoolean(k));
            e.apply();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Oyun değiştir: eskisinin ayarlarını kaydet, yenisininkini yükle. */
    static boolean switchTo(Context c, String oldPkg, String newPkg) {
        if (newPkg == null || newPkg.equals(oldPkg)) return false;
        save(c, oldPkg);
        Boost.prefs(c).edit().putString("game", newPkg).apply();
        return load(c, newPkg);
    }
}
