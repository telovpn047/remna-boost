package com.remna.boost;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    static final int BG = 0xFF070B16, CARD = 0x0DFFFFFF, TX = 0xFFF1F5F9, TX2 = 0xFF94A3B8, TX3 = 0xFF64748B;
    static final int OR = 0xFFF97316, RED = 0xFFE11D48, GREEN = 0xFF34D399;
    /** PUBG Mobile bölgeleri ve o bölgedeki veri merkezine yakın ölçüm noktaları (AWS S3, HTTP 80). */
    static final String[][] REGIONS = {
            {"Orta Doğu", "s3.me-south-1.amazonaws.com", "Bahreyn"},
            {"Avrupa", "s3.eu-central-1.amazonaws.com", "Frankfurt"},
            {"Asya", "s3.ap-southeast-1.amazonaws.com", "Singapur"},
            {"KRJP", "s3.ap-northeast-1.amazonaws.com", "Tokyo"},
            {"Hindistan (BGMI)", "s3.ap-south-1.amazonaws.com", "Mumbai"},
            {"Kuzey Amerika", "s3.us-east-1.amazonaws.com", "Virginia"},
            {"Güney Amerika", "s3.sa-east-1.amazonaws.com", "São Paulo"},
    };
    static final String ADB_CMD = "adb shell pm grant com.remna.boost android.permission.WRITE_SECURE_SETTINGS";

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    String game;
    LinearLayout root, permBox, reportBox;
    TextView srvSub, histSub;
    TextView ramTv, tempTv, pingTv, gameName, gameSub, btnLabel;
    ImageView gameIcon;
    BigButton big;

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Boost.prefs(this);
        if (!BoostService.running && prefs.getBoolean("tw_applied", false)) { Tweaks.restore(this); Boost.dndOff(this); }
        String ph = prefs.getString("ping_host", "");
        if (ph.startsWith("ec2.")) prefs.edit().putString("ping_host", "s3." + ph.substring(4)).apply();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener((code, res) -> h.post(() -> {
                if (res == PackageManager.PERMISSION_GRANTED) { Tweaks.secureAllowed(this); toast("Shizuku bağlandı"); }
                renderPerms();
                renderSetup();
            }));
        } catch (Throwable ignored) {}
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Ekranı güncel verilerle doldurur (onResume ve yeniden çizimlerde). */
    void refresh() {
        pickGame();
        renderPerms();
        renderReport();
        renderSetup();
        int hn = ServerLog.load(this).length();
        if (hn > 0) histSub.setText(hn + " sunucu kaydedildi");
        String sip = prefs.getString("srv_ip", null);
        if (sip != null) srvSub.setText(sip + "  ·  " + new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(new java.util.Date(prefs.getLong("srv_at", 0))));
        if ("com.remna.boost.BOOST".equals(getIntent().getAction())) {
            getIntent().setAction(null);
            h.postDelayed(this::boost, 300);
        }
        h.removeCallbacks(stats);
        h.post(stats);
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); }

    @Override
    protected void onPause() { super.onPause(); h.removeCallbacks(stats); }

    /* ---------------- arayüz ---------------- */
    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g;
    }

    LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = round(CARD, 20); g.setStroke(dp(1), 0x0FFFFFFF);
        l.setBackground(g);
        return l;
    }

    LinearLayout.LayoutParams mlp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    TextView section(String s) {
        TextView t = text(s, 12, TX3, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(20), 0, dp(8));
        return t;
    }

    LinearLayout[] pages = new LinearLayout[3];
    ScrollView[] scrolls = new ScrollView[3];
    TextView[] tabs = new TextView[3];
    int tab = 0;
    TextView statusPill;
    LinearLayout setupBox, profileRow;

    LinearLayout newPage(FrameLayout content, int i) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(4), dp(18), dp(28));
        sv.addView(l);
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        pages[i] = l;
        scrolls[i] = sv;
        return l;
    }

    void selectTab(int i) {
        tab = i;
        for (int k = 0; k < 3; k++) {
            scrolls[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            tabs[k].setTextColor(k == i ? Color.WHITE : TX3);
            tabs[k].setBackground(k == i ? round(0x26F97316, 16) : null);
        }
    }

    View build() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);

        // başlık + durum rozeti
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(10));
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        top.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView title = text("Remna Boost", 21, TX, true);
        title.setPadding(dp(12), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        statusPill = text("…", 12, TX, true);
        statusPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusPill.setOnClickListener(v -> selectTab(2));
        top.addView(statusPill);
        outer.addView(top);

        FrameLayout content = new FrameLayout(this);
        outer.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pGame = newPage(content, 0), pNet = newPage(content, 1), pSet = newPage(content, 2);

        // alt gezinme
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(12), dp(8), dp(12), dp(10));
        nav.setBackground(round(0xFF0B1124, 0));
        String[] names = {"⚡  Oyun", "📶  Ağ", "⚙  Ayarlar"};
        for (int i = 0; i < 3; i++) {
            TextView t = text(names[i], 14, TX3, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            final int idx = i;
            t.setOnClickListener(v -> selectTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(4); lp.rightMargin = dp(4);
            nav.addView(t, lp);
            tabs[i] = t;
        }
        outer.addView(nav);

        root = pGame;

        // oyun kartı
        LinearLayout gc = card();
        gameIcon = new ImageView(this);
        gc.addView(gameIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout gt = new LinearLayout(this);
        gt.setOrientation(LinearLayout.VERTICAL);
        gt.setPadding(dp(14), 0, 0, 0);
        gameName = text("PUBG", 17, TX, true);
        gameSub = text("", 13, TX2, false);
        gt.addView(gameName); gt.addView(gameSub);
        gc.addView(gt, new LinearLayout.LayoutParams(0, -2, 1));
        gc.setOnClickListener(v -> chooseGame());
        root.addView(gc, mlp(4));

        // büyük düğme
        FrameLayout bf = new FrameLayout(this);
        big = new BigButton(this);
        bf.addView(big, new FrameLayout.LayoutParams(dp(230), dp(230), Gravity.CENTER));
        btnLabel = text("BOOST", 26, Color.WHITE, true);
        btnLabel.setLetterSpacing(0.12f);
        btnLabel.setGravity(Gravity.CENTER);
        bf.addView(btnLabel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        big.setOnClickListener(v -> boost());
        root.addView(bf, mlp(18));

        // göstergeler
        LinearLayout st = new LinearLayout(this);
        st.setWeightSum(3);
        ramTv = stat(st, "Boş RAM");
        tempTv = stat(st, "Pil sıcaklığı");
        pingTv = stat(st, "Ping");
        root.addView(st, mlp(18));

        root.addView(section("PROFİL"));
        profileRow = new LinearLayout(this);
        root.addView(profileRow);

        setupBox = new LinearLayout(this);
        setupBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(setupBox);

        reportBox = new LinearLayout(this);
        reportBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(reportBox);

        root = pNet;
        TextView netNote = text("PUBG'nin bağlandığı sunucuları ve bölgelerin gerçek pingini ölç. Doğru sonuç için VPN kapalıyken analiz et.", 12, TX2, false);
        netNote.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(netNote);

        LinearLayout tc = card();
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(text("Sunucu ping testi", 15, TX, true));
        tt.addView(text("Tüm PUBG bölgelerini ölç, en iyisini bul", 12, TX2, false));
        tc.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        TextView go = text("Başlat", 14, OR, true);
        tc.addView(go);
        tc.setOnClickListener(v -> {
            try { openRegionTest(); } catch (Throwable t) { toast("Test açılamadı: " + t); }
        });
        root.addView(tc, mlp(8));

        LinearLayout sv2 = card();
        LinearLayout st3 = new LinearLayout(this);
        st3.setOrientation(LinearLayout.VERTICAL);
        st3.addView(text("Oyun sunucusu", 15, TX, true));
        srvSub = text("Maça girince PUBG'nin bağlandığı sunucu burada görünür", 12, TX2, false);
        st3.addView(srvSub);
        sv2.addView(st3, new LinearLayout.LayoutParams(0, -2, 1));
        sv2.addView(text("Analiz", 14, OR, true));
        sv2.setOnClickListener(v -> openServerInfo());
        root.addView(sv2, mlp(12));

        LinearLayout hc = card();
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(text("Sunucu geçmişi · en iyi bölge", 15, TX, true));
        histSub = text("Oynadıkça PUBG'nin bağlandığı tüm sunucular toplanır", 12, TX2, false);
        ht.addView(histSub);
        hc.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        hc.addView(text("Analiz", 14, OR, true));
        hc.setOnClickListener(v -> openServerHistory());
        root.addView(hc, mlp(12));
        root.addView(regionRow(), mlp(12));

        root = pSet;
        LinearLayout sc = card();
        LinearLayout st2 = new LinearLayout(this);
        st2.setOrientation(LinearLayout.VERTICAL);
        st2.addView(text("Ana ekrana \"Boost & Oyna\"", 15, TX, true));
        st2.addView(text("Tek dokunuşla boost edip PUBG'yi açar", 12, TX2, false));
        sc.addView(st2, new LinearLayout.LayoutParams(0, -2, 1));
        sc.addView(text("Ekle", 14, OR, true));
        sc.setOnClickListener(v -> addShortcut());
        root.addView(sc, mlp(4));

        // ayarlar
        root.addView(section("BOOST"));
        root.addView(choice("Arka plan temizliği", "cleanup", "smart",
                new String[]{"Akıllı (yalnız RAM azsa)", "Agresif (her zaman)", "Kapalı"},
                new String[]{"smart", "aggressive", "off"}, null));

        root.addView(section("ANDROID OYUN MODU · SHIZUKU"));
        root.addView(choice("Oyun modu", "gm_mode", "off",
                new String[]{"Kapalı", "Standart", "Performans", "Pil tasarrufu"},
                new String[]{"off", "standard", "performance", "battery"}, this::askShizuku));
        root.addView(choice("Render çözünürlüğü", "gm_scale", "off",
                new String[]{"Değiştirme", "%90 (az serin)", "%80 (dengeli)", "%70 (serin)", "%50 (çok serin)"},
                new String[]{"off", "0.9", "0.8", "0.7", "0.5"}, this::askShizuku), mlp(8));
        int maxHz = Math.round(Tweaks.maxRefresh(this));
        java.util.List<String> fl = new java.util.ArrayList<>(), fv = new java.util.ArrayList<>();
        fl.add("Kapalı"); fv.add("off");
        int[] opts = maxHz >= 120 ? new int[]{120, 60, 40, 30} : maxHz >= 90 ? new int[]{90, 45, 30} : new int[]{60, 30};
        for (int o : opts) { fl.add(o + " FPS'e sabitle"); fv.add(String.valueOf(o)); }
        root.addView(choice("FPS sabitleme (Android)", "gm_fps", "off",
                fl.toArray(new String[0]), fv.toArray(new String[0]), this::askShizuku), mlp(8));
        TextView gmNote = text("Oyun dosyalarına dokunmaz, sistem ayarıdır. Değişiklik PUBG kapatılıp açılınca geçerli olur. Oyun kendi Game Mode desteğini bildirmişse etkisi olmayabilir.", 11, TX3, false);
        gmNote.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(gmNote);

        root.addView(section("TERMAL KORUMA"));
        root.addView(choice("Termal koruma", "thermal", "standard",
                new String[]{"Standart · 40°C ılık (90 Hz) · 42°C sıcak (60 Hz) · 44°C koruma", "Hassas · 38 / 40 / 42°C", "Kapalı"},
                new String[]{"standard", "sensitive", "off"}, null));
        TextView thNote = text("Isınınca ekran hemen düşürülür; soğuyunca 1 dakika beklenip birer kademe geri yükseltilir. Oyun bitince orijinal ayarlar geri yüklenir.", 11, TX3, false);
        thNote.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(thNote);

        root.addView(section("OYUN SIRASINDA"));
        root.addView(choice("Rahatsız etme", "dnd_mode", "priority",
                new String[]{"Kapalı", "Öncelikli (aramalar önemli kişilerden)", "Sadece alarmlar", "Tam sessiz"},
                new String[]{"off", "priority", "alarms", "none"}, this::askDnd));
        java.util.List<Integer> rates = Tweaks.refreshRates(this);
        String[] hzL = new String[rates.size() + 2], hzV = new String[rates.size() + 2];
        hzL[0] = "Kapalı (sistem yönetsin)"; hzV[0] = "off";
        hzL[1] = "En yüksek (" + Math.round(Tweaks.maxRefresh(this)) + " Hz)"; hzV[1] = "max";
        for (int i = 0; i < rates.size(); i++) { hzL[i + 2] = "Sabit " + rates.get(i) + " Hz"; hzV[i + 2] = String.valueOf(rates.get(i)); }
        root.addView(choice("Sabit yenileme hızı (FPS sınırı)", "hz_mode", "max", hzL, hzV, this::askAdb), mlp(8));
        root.addView(choice("Animasyon hızı", "anim_mode", "0.5",
                new String[]{"Değiştirme", "Hızlı (0.5x)", "Kapalı (0x)"}, new String[]{"off", "0.5", "0"}, this::askAdb), mlp(8));
        root.addView(toggle("Otomatik parlaklığı kapat", "Oyunda parlaklık zıplamaz", "autobright", true, this::askSystem), mlp(8));
        boolean ts = Tweaks.touchSupported(this);
        root.addView(toggle("Dokunma hassasiyeti", ts ? "Ekran dokunuşlara daha hızlı tepki verir · Samsung" : "Bu telefonda Samsung ayarı bulunamadı",
                "touch", true, this::askAdb), mlp(8));

        root.addView(section("OYUN ÜSTÜ PANEL"));
        root.addView(toggle("FPS göstergesi", "Oyunun gerçek FPS'i · Shizuku gerekir", "fps", true, this::askShizuku));
        root.addView(choice("Panel", "ov_mode", "full",
                new String[]{"Kapalı", "FPS · ping", "FPS · ping · RAM · sıcaklık"}, new String[]{"off", "ping", "full"}, this::askOverlay), mlp(8));
        root.addView(choice("Panel boyutu", "ov_size", "normal",
                new String[]{"Küçük", "Normal", "Büyük"}, new String[]{"small", "normal", "large"}, null), mlp(8));
        root.addView(choice("Panel saydamlığı", "ov_alpha", "70",
                new String[]{"%30", "%50", "%70", "%90"}, new String[]{"30", "50", "70", "90"}, null), mlp(8));

        root.addView(section("OTOMATİK"));
        root.addView(choice("Oyundan çıkınca kapat", "autostop", "30",
                new String[]{"Kapalı (elle kapat)", "15 sn sonra", "30 sn sonra", "60 sn sonra"}, new String[]{"0", "15", "30", "60"}, this::askUsage));

        root.addView(section("GİZLİLİK"));
        root.addView(toggle("Sunucu geçmişi kaydı", "PUBG'nin bağlandığı sunucu IP'leri yalnız bu cihazda saklanır", "srv_record", true, null));
        root.addView(toggle("Çevrimiçi konum sorgusu", "Bilinmeyen IP'ler ipwho.is'e (HTTPS) gönderilir · varsayılan kapalı", "geo_online", false, null), mlp(8));
        LinearLayout clr = card();
        clr.addView(text("Sunucu geçmişini temizle", 15, TX, true), new LinearLayout.LayoutParams(0, -2, 1));
        clr.addView(text("Temizle", 14, RED, true));
        clr.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Sunucu geçmişi silinsin mi?").setMessage("Kayıtlı tüm sunucu IP'leri, konumlar ve ölçümler silinir.")
                .setPositiveButton("Sil", (d, w) -> { ServerLog.clear(this); histSub.setText("Kayıtlar silindi"); toast("Sunucu geçmişi silindi"); })
                .setNegativeButton("Vazgeç", null).show());
        root.addView(clr, mlp(8));
        TextView pv = text("Konum bilgisi önce uygulamanın içindeki tablodan bulunur; dışarıya veri gönderilmez. Çevrimiçi sorgu yalnız siz açarsanız yapılır.", 11, TX3, false);
        pv.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(pv);

        root.addView(section("İZİNLER"));
        permBox = new LinearLayout(this);
        permBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(permBox);

        TextView note = text("Not: Root olmadan oyunun FPS'i doğrudan artırılamaz. Remna Boost RAM boşaltır, kesintileri engeller, ekranı en yüksek yenileme hızına sabitler ve oyun sırasında ping/sıcaklığı gösterir. Oyun dosyalarına dokunmaz (ban riski yok).", 12, TX3, false);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);
        renderProfiles();
        selectTab(tab);
        return outer;
    }

    /* ---------------- profiller ---------------- */
    static final String[] PROFILE_NAMES = {"Performans", "Dengeli", "Serin"};
    static final String[] PROFILE_IDS = {"perf", "balanced", "cool"};
    static final String[] PROFILE_SUB = {"En yüksek FPS", "Stabil 60 FPS", "Az ısınma"};

    void applyProfile(String id) {
        SharedPreferences.Editor e = prefs.edit().putString("profile", id);
        switch (id) {
            case "perf":
                e.putString("gm_mode", "performance").putString("gm_scale", "off").putString("gm_fps", "off")
                        .putString("hz_mode", "max").putString("anim_mode", "0.5").putString("thermal", "standard").putString("cleanup", "smart");
                break;
            case "balanced":
                e.putString("gm_mode", "performance").putString("gm_scale", "off").putString("gm_fps", "60")
                        .putString("hz_mode", "max").putString("anim_mode", "0.5").putString("thermal", "standard").putString("cleanup", "smart");
                break;
            default: // cool
                e.putString("gm_mode", "battery").putString("gm_scale", "0.8").putString("gm_fps", "60")
                        .putString("hz_mode", "60").putString("anim_mode", "0.5").putString("thermal", "sensitive").putString("cleanup", "off");
        }
        e.apply();
        if (BoostService.running) Tweaks.reapply(this);
        int y = scrolls[0].getScrollY();
        setContentView(build());
        refresh();
        scrolls[0].post(() -> scrolls[0].scrollTo(0, y));
        toast(nameOf(id) + " profili uygulandı" + (Sh.granted() ? "" : " (Android oyun modu için Shizuku gerekli)"));
    }

    String nameOf(String id) {
        for (int i = 0; i < PROFILE_IDS.length; i++) if (PROFILE_IDS[i].equals(id)) return PROFILE_NAMES[i];
        return "Özel";
    }

    void renderProfiles() {
        profileRow.removeAllViews();
        String cur = prefs.getString("profile", "custom");
        for (int i = 0; i < 3; i++) {
            boolean on = PROFILE_IDS[i].equals(cur);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(6), dp(12), dp(6), dp(12));
            GradientDrawable g = round(on ? 0x26F97316 : CARD, 16);
            g.setStroke(dp(1), on ? OR : 0x0FFFFFFF);
            c.setBackground(g);
            c.addView(text(PROFILE_NAMES[i], 14, on ? Color.WHITE : TX, true));
            c.addView(text(PROFILE_SUB[i], 11, on ? OR : TX3, false));
            final String id = PROFILE_IDS[i];
            c.setOnClickListener(v -> applyProfile(id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(4); lp.rightMargin = dp(4);
            profileRow.addView(c, lp);
        }
        if ("custom".equals(cur)) {
            TextView t = text("Özel ayarlar kullanılıyor · Ayarlar sekmesinden düzenlendi", 11, TX3, false);
            t.setPadding(dp(4), dp(6), 0, 0);
            ((LinearLayout) profileRow.getParent()).addView(t, ((LinearLayout) profileRow.getParent()).indexOfChild(profileRow) + 1);
        }
    }

    /* ---------------- kurulum kontrolü ---------------- */
    void renderSetup() {
        setupBox.removeAllViews();
        java.util.List<String> miss = new java.util.ArrayList<>();
        if (!Settings.canDrawOverlays(this)) miss.add("Oyun üstü panel izni");
        if (!Boost.usageAllowed(this)) miss.add("Kullanım erişimi (otomatik kapanma, rapor)");
        if (!Boost.dndAllowed(this) && !"off".equals(prefs.getString("dnd_mode", "priority"))) miss.add("Rahatsız etme erişimi");
        boolean ok = miss.isEmpty();
        statusPill.setText(ok ? "● Hazır" : "● Kurulum " + miss.size());
        statusPill.setTextColor(ok ? GREEN : OR);
        statusPill.setBackground(round(ok ? 0x1A34D399 : 0x1AF97316, 14));
        if (ok) {
            if (!Sh.granted()) {
                LinearLayout p = card();
                LinearLayout pt = new LinearLayout(this);
                pt.setOrientation(LinearLayout.VERTICAL);
                pt.addView(text("Pro özellikler (isteğe bağlı)", 14, TX, true));
                pt.addView(text("FPS göstergesi · sunucu kaydı · Android oyun modu · Shizuku ile", 11, TX3, false));
                p.addView(pt, new LinearLayout.LayoutParams(0, -2, 1));
                p.addView(text("Nasıl? ›", 13, OR, true));
                p.setOnClickListener(v -> askShizuku());
                setupBox.addView(p, mlp(12));
            }
            return;
        }
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = round(0x14F97316, 20);
        g.setStroke(dp(1), 0x66F97316);
        c.setBackground(g);
        c.addView(text("Kurulumu tamamla", 15, TX, true));
        for (String m : miss) {
            TextView t = text("•  " + m, 12, TX2, false);
            t.setPadding(0, dp(4), 0, 0);
            c.addView(t);
        }
        TextView go = text("İzinlere git ›", 13, OR, true);
        go.setPadding(0, dp(8), 0, 0);
        c.addView(go);
        c.setOnClickListener(v -> {
            selectTab(2);
            scrolls[2].post(() -> scrolls[2].scrollTo(0, permBox.getTop()));
        });
        setupBox.addView(c, mlp(12));
    }

    TextView stat(LinearLayout parent, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, dp(12), 0, dp(12));
        c.setBackground(round(CARD, 16));
        TextView v = text("…", 17, TX, true);
        c.addView(v);
        c.addView(text(label, 11, TX3, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(4); lp.rightMargin = dp(4);
        parent.addView(c, lp);
        return v;
    }

    View toggle(String title, String sub, String key, boolean def, Runnable onEnable) {
        LinearLayout l = card();
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 15, TX, true));
        t.addView(text(sub, 12, TX2, false));
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (on && onEnable != null) onEnable.run();

            if (BoostService.running) Tweaks.reapply(this);
        });
        l.addView(sw);
        l.setOnClickListener(v -> sw.toggle());
        return l;
    }

    /** Seçenekli ayar satırı; oyun modu açıksa değişiklik hemen uygulanır. */
    View choice(String title, String key, String def, String[] labels, String[] values, Runnable onEnable) {
        LinearLayout l = card();
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 15, TX, true));
        TextView sub = text(labelOf(key, def, labels, values), 12, OR, false);
        t.addView(sub);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        l.addView(text("›", 22, TX3, false));
        l.setOnClickListener(v -> {
            String cur = prefs.getString(key, def);
            int sel = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(title)
                    .setSingleChoiceItems(labels, sel, (d, w) -> {
                        prefs.edit().putString(key, values[w]).apply();
                        if (!key.startsWith("ov_") && !"autostop".equals(key) && !"dnd_mode".equals(key)) markCustom();
                        sub.setText(labels[w]);
                        d.dismiss();
                        if (!values[w].equals("off") && onEnable != null) onEnable.run();
                        if (BoostService.running) {
                            Tweaks.reapply(this);
                            if (key.startsWith("ov_")) toast("Panel ayarı bir sonraki oyun modunda geçerli olur");
                        }
                    }).show();
        });
        return l;
    }

    String labelOf(String key, String def, String[] labels, String[] values) {
        String cur = prefs.getString(key, def);
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) return labels[i];
        return labels[0];
    }

    void askShizuku() {
        if (Sh.granted()) return;
        if (Sh.running()) { Sh.request(); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Shizuku")
                .setMessage("FPS göstergesi ve otomatik ADB izni için Shizuku uygulaması gerekir.\n\n1) Shizuku'yu kur (Play Store / GitHub)\n2) Shizuku'yu aç → 'Kablosuz hata ayıklama ile başlat'\n3) Remna Boost'a dönüp izin ver\n\nTelefon yeniden başlarsa Shizuku'yu tekrar başlatman gerekir.")
                .setPositiveButton("Shizuku'yu indir", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest"))))
                .setNegativeButton("Kapat", null).show();
    }

    void markCustom() {
        if (!"custom".equals(prefs.getString("profile", "custom"))) {
            prefs.edit().putString("profile", "custom").apply();
            profileRow.post(() -> { int y = scrolls[2].getScrollY(); setContentView(build()); refresh(); scrolls[2].post(() -> scrolls[2].scrollTo(0, y)); });
        }
    }

    void askAdb() { if (!Tweaks.secureAllowed(this)) showAdbHelp(); }

    View regionRow() {
        LinearLayout l = card();
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text("Ping bölgesi", 15, TX, true));
        TextView sub = text(regionName(), 12, TX2, false);
        t.addView(sub);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        l.setOnClickListener(v -> {
            String[] names = new String[REGIONS.length];
            for (int i = 0; i < names.length; i++) names[i] = REGIONS[i][0];
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Ping bölgesi")
                    .setItems(names, (d, w) -> { prefs.edit().putString("ping_host", REGIONS[w][1]).apply(); sub.setText(regionName()); })
                    .show();
        });
        return l;
    }

    /* ---------------- sunucu geçmişi analizi ---------------- */
    void openServerHistory() {
        if (ServerLog.load(this).length() == 0) { toast(Sh.granted() ? "Henüz kayıt yok. Son tarama: " + prefs.getString("scan_diag", "yok") : "Shizuku çalışmıyor. Shizuku'yu açıp 'Başlat'a bas, sonra tekrar BOOST yap."); return; }
        TextView body = text("Konumlar sorgulanıyor…", 12, TX, false);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        body.setTextIsSelectable(true);
        body.setTypeface(Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("PUBG sunucu analizi").setView(sv)
                .setPositiveButton("Kopyala", (d, w) -> {
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("srv", body.getText()));
                    toast("Kopyalandı");
                })
                .setNeutralButton("Temizle", (d, w) -> { ServerLog.clear(this); histSub.setText("Kayıtlar silindi"); })
                .setNegativeButton("Kapat", null).show();
        new Thread(() -> {
            ServerLog.vpnMode = Boost.vpnActive(this);
            ServerLog.enrich(this);
            java.util.List<ServerLog.Row> rows = ServerLog.rows(this);
            post(dlg, body, rows.size() + " sunucu · ping ölçülüyor (" + (Boost.vpnActive(this) ? "VPN açık" : "VPN kapalı") + ")…");
            boolean vpnOn = Boost.vpnActive(this);
            java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(6);
            if (!vpnOn) for (ServerLog.Row r : rows) ex.submit(() -> {
                int v = Sh.granted() ? Sh.icmp(r.ip) : -1;
                r.how = "icmp";
                if (v < 0) {
                    int gp = 0;
                    try { gp = Integer.parseInt(ServerLog.load(this).optJSONObject(r.ip).optString("port", "0")); } catch (Exception ignored) {}
                    v = gp > 0 ? Boost.tcpProbe(r.ip, 443, 80, gp) : Boost.tcpProbe(r.ip, 443, 80);
                    r.how = "tcp";
                }
                if (v > 0) { r.ping = v; ServerLog.putPing(this, r.ip, v, r.how); }
                else r.stale = r.ping > 0; // kayıtlı eski değer gösterilir
            });
            ex.shutdown();
            try { ex.awaitTermination(120, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            post(dlg, body, buildHistoryReport(rows));
        }).start();
    }

    String buildHistoryReport(java.util.List<ServerLog.Row> rows) {
        java.util.Collections.sort(rows, (a, b) -> {
            int pa = a.ping < 0 ? 99999 : a.ping, pb = b.ping < 0 ? 99999 : b.ping;
            return pa != pb ? pa - pb : b.n - a.n;
        });
        int udp = 0, tcp = 0;
        for (ServerLog.Row r : rows) if (r.proto.equals("UDP")) udp++; else tcp++;
        StringBuilder sb = new StringBuilder();
        sb.append(rows.size()).append(" sunucu · maç (UDP) ").append(udp).append(" · lobi/giriş (TCP) ").append(tcp).append('\n');
        if (Boost.vpnActive(this))
            sb.append("⚠ VPN AÇIK: ping ölçülmedi (VPN ping'i tünelden geçirmiyor). Aşağıdaki değerler son VPN'siz ölçümden.\n\n");
        else sb.append("Ölçüm: VPN KAPALI (doğrudan bağlantı)\n\n");

        // bölge özeti (yalnız maç sunucuları; yoksa hepsi)
        java.util.Map<String, java.util.List<Integer>> reg = new java.util.TreeMap<>();
        java.util.Map<String, Integer> regCount = new java.util.TreeMap<>();
        for (ServerLog.Row r : rows) {
            // bölge tahmini: yalnız PUBG'nin veri merkezleri (Tencent AS132203, Azure AS8075); CDN'ler yanıltır
            boolean dc = r.as.contains("132203") || r.as.contains("8075") || r.as.startsWith("Tencent") || r.as.startsWith("Microsoft");
            if (!dc || "?".equals(r.region)) continue;
            if (r.proto.equals("UDP")) { Integer c = regCount.get(r.region); regCount.put(r.region, c == null ? 1 : c + 1); }
            else if (!regCount.containsKey(r.region)) regCount.put(r.region, 0);
            if (r.ping > 0 && !"tcp".equals(r.how)) {
                if (!reg.containsKey(r.region)) reg.put(r.region, new java.util.ArrayList<>());
                reg.get(r.region).add(r.ping);
            }
        }
        sb.append("== BÖLGELER (PUBG veri merkezleri, ICMP) ==\n");
        String bestReg = null;
        int bestMed = Integer.MAX_VALUE;
        for (String k : regCount.keySet()) {
            java.util.List<Integer> l = reg.get(k);
            if (l == null || l.isEmpty()) { sb.append(String.format(java.util.Locale.US, "%-18s %2d yoklama · ping ölçülemedi (oyunda dene)%n", k, regCount.get(k))); continue; }
            java.util.Collections.sort(l);
            int med = l.get(l.size() / 2), min = l.get(0);
            sb.append(String.format(java.util.Locale.US, "%-18s %2d yoklama · en iyi %d ms · ortanca %d ms%n", k, regCount.get(k), min, med));
            if (med < bestMed) { bestMed = med; bestReg = k; }
        }
        if (bestReg != null) sb.append("\n➜ Önerilen PUBG bölgesi: ").append(bestReg).append(" (ortanca ").append(bestMed).append(" ms)\n");
        sb.append("\n== SUNUCULAR (pinge göre) ==\n");
        for (ServerLog.Row r : rows) {
            String as = r.as.length() > 22 ? r.as.substring(0, 22) : r.as;
            sb.append(String.format(java.util.Locale.US, "%6s%s %s  %-15s %dx%n", r.ping < 0 ? "—" : r.ping + "ms",
                    r.stale ? "*" : "tcp".equals(r.how) && r.ping > 0 ? "ᵗ" : " ", r.proto, r.ip, r.n));
            sb.append("        ").append(r.where).append(" · ").append(as)
                    .append(r.vpn && r.direct ? " · VPN+doğrudan" : r.vpn ? " · VPN'de görüldü" : " · doğrudan görüldü").append('\n');
        }
        String ge = prefs.getString("geo_err", null);
        if (ge != null && rows.size() > 0 && "konum yok".equals(rows.get(0).where)) sb.append("\nKonum hatası: ").append(ge).append('\n');
        if (ge != null && rows.size() > 0 && "konum yok".equals(rows.get(0).where))
            sb.append("İpucu: Bilinmeyen konumlar için Ayarlar → Gizlilik → Çevrimiçi konum sorgusu'nu açabilirsin.\n");
        sb.append("\nUDP sunucuları PUBG'nin eşleştirmede yokladığı bölge noktalarıdır; maçın kendi sunucusu root olmadan görünmez.");
        sb.append("\n* = şimdi ölçülemedi, önceki ölçüm gösteriliyor.");
        if (Boost.vpnActive(this)) sb.append("\n⚠ VPN açık: çoğu VPN ping/yoklamayı tünelden geçirmez. Doğru sonuç için VPN'i kapatıp tekrar analiz et.");
        sb.append("\nᵗ = ICMP kapalı, TCP yoklamasıyla ölçüldü. '—' = güvenilir ölçüm alınamadı.\nNot: Maç sunucusunu PUBG seçer; sen lobideki bölgeyi ve rotayı (VPN/doğrudan) seçebilirsin. '—' sunucunun ICMP'ye yanıt vermediğini gösterir. VPN açık ve kapalıyken ayrı ayrı analiz edip karşılaştır.");
        return sb.toString();
    }

    /* ---------------- oyun sunucusu analizi ---------------- */
    void openServerInfo() {
        String ip = prefs.getString("srv_ip", null);
        if (ip == null) { toast(Sh.granted() ? "Önce BOOST ile oyuna gir; maç başlayınca sunucu tespit edilir\nSon tarama: " + prefs.getString("scan_diag", "yok") : "Shizuku çalışmıyor. Shizuku'yu açıp 'Başlat'a bas, sonra tekrar BOOST yap."); return; }
        TextView body = text("Sunucu: " + ip + "\nKonum sorgulanıyor…", 13, TX, false);
        body.setPadding(dp(20), dp(8), dp(20), dp(8));
        body.setTextIsSelectable(true);
        body.setTypeface(Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("PUBG oyun sunucusu").setView(sv)
                .setPositiveButton("Kopyala", (d, w) -> {
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("srv", body.getText()));
                    toast("Kopyalandı");
                }).setNegativeButton("Kapat", null).show();
        StringBuilder sb = new StringBuilder("Sunucu: " + ip + "\n");
        new Thread(() -> {
            // konum: önce yerel tablo; çevrimiçi sorgu yalnız kullanıcı izin verdiyse (Gizlilik)
            String[] lg = ServerLog.localGeo(ip);
            if (lg != null) sb.append("Konum: ").append(lg[3]).append(", ").append(lg[2]).append("  (yerel veri)\n").append("Ağ: ").append(lg[4]).append('\n');
            else if (prefs.getBoolean("geo_online", false)) {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://ipwho.is/" + ip + "?lang=tr").openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    org.json.JSONObject o = new org.json.JSONObject(new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A").next());
                    org.json.JSONObject con = o.optJSONObject("connection");
                    sb.append("Konum: ").append(o.optString("city")).append(", ").append(o.optString("country")).append("  (ipwho.is)\n");
                    if (con != null) sb.append("Ağ: AS").append(con.optInt("asn")).append(' ').append(con.optString("org")).append('\n');
                } catch (Exception e) {
                    sb.append("Konum alınamadı (").append(e.getClass().getSimpleName()).append(")\n");
                }
            } else sb.append("Konum: bilinmiyor (çevrimiçi konum sorgusu Ayarlar → Gizlilik'te kapalı)\n");
            sb.append("VPN: ").append(Boost.vpnActive(this) ? "açık" : "kapalı").append('\n');
            post(dlg, body, sb + "\nPing ölçülüyor…");
            if (!Sh.granted()) { post(dlg, body, sb + "\nPing ve rota için Shizuku gerekli."); return; }
            int p = Sh.icmp(ip);
            String how = "ICMP";
            if (p < 0) { p = Boost.tcpProbe(ip, 443, 80, 8080); how = "TCP yoklama"; }
            sb.append("Ping: ").append(p < 0 ? "yanıt yok" : p + " ms (" + how + ")").append("\n\n");
            sb.append("Rota (traceroute):\n");
            post(dlg, body, sb + "…");
            for (String[] hop : Sh.trace(ip, 20)) {
                sb.append(String.format(java.util.Locale.US, "%2s  %-16s %s%n", hop[0], hop[1], hop[2]));
                post(dlg, body, sb.toString());
            }
            sb.append("\nİpucu: Süre hangi sıçramada aniden artıyorsa gecikme oradan kaynaklanıyor. '*' o noktanın yanıt vermediğini gösterir.");
            post(dlg, body, sb.toString());
        }).start();
    }

    void post(AlertDialog dlg, TextView tv, String s) { h.post(() -> { if (dlg.isShowing()) tv.setText(s); }); }

    /* ---------------- maç raporu ---------------- */
    void renderReport() {
        reportBox.removeAllViews();
        String js = prefs.getString("last_report", null);
        if (js == null) return;
        try {
            org.json.JSONObject o = new org.json.JSONObject(js);
            LinearLayout c = card();
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.START);
            long min = o.getLong("dur") / 60000;
            String when = new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(new java.util.Date(o.getLong("at")));
            c.addView(text("Son oyun raporu · " + when + " · " + min + " dk", 15, TX, true));
            StringBuilder sb = new StringBuilder();
            if (o.has("avgFps")) sb.append("FPS  ort ").append(o.getInt("avgFps")).append("  ·  en düşük %5: ").append(o.getInt("lowFps")).append("  ·  en yüksek ").append(o.getInt("maxFps")).append('\n');
            if (o.has("avgPing")) sb.append("Ping  ort ").append(o.getInt("avgPing")).append(" ms  ·  en yüksek ").append(o.getInt("maxPing")).append(" ms\n");
            sb.append(String.format(java.util.Locale.US, "Sıcaklık  pil en yüksek %.0f°C", o.getDouble("maxBatt")));
            if (o.getDouble("maxCpu") > 0) sb.append(String.format(java.util.Locale.US, "  ·  CPU en yüksek %.0f°C", o.getDouble("maxCpu")));
            TextView t = text(sb.toString(), 12, TX2, false);
            t.setPadding(0, dp(6), 0, 0);
            t.setLineSpacing(dp(2), 1f);
            c.addView(t);
            if (o.has("graph")) {
                org.json.JSONArray g = o.getJSONArray("graph");
                int[] v = new int[g.length()];
                for (int i = 0; i < v.length; i++) v[i] = g.getInt(i);
                Graph gv = new Graph(this, v);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(80));
                lp.topMargin = dp(10);
                c.addView(gv, lp);
            }
            reportBox.addView(c, mlp(12));
        } catch (Exception ignored) {}
    }

    static final class Graph extends View {
        final int[] v;
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint();

        Graph(Context c, int[] v) {
            super(c);
            this.v = v;
            float d = c.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * d);
            line.setColor(0xFF34D399);
            line.setStrokeJoin(Paint.Join.ROUND);
            grid.setColor(0x1AFFFFFF);
        }

        @Override protected void onDraw(Canvas cv) {
            if (v.length < 2) return;
            float w = getWidth(), h = getHeight();
            int max = 60;
            for (int x : v) max = Math.max(max, x);
            for (int k = 1; k < 4; k++) cv.drawLine(0, h * k / 4f, w, h * k / 4f, grid);
            android.graphics.Path p = new android.graphics.Path(), f = new android.graphics.Path();
            for (int i = 0; i < v.length; i++) {
                float x = w * i / (v.length - 1f), y = h - h * v[i] / (float) max;
                if (i == 0) { p.moveTo(x, y); f.moveTo(x, h); f.lineTo(x, y); } else { p.lineTo(x, y); f.lineTo(x, y); }
            }
            f.lineTo(w, h);
            f.close();
            fill.setShader(new LinearGradient(0, 0, 0, h, 0x5534D399, 0x0034D399, Shader.TileMode.CLAMP));
            cv.drawPath(f, fill);
            cv.drawPath(p, line);
        }
    }

    /* ---------------- ana ekran kısayolu ---------------- */
    void addShortcut() {
        android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) { toast("Başlatıcı kısayolu desteklemiyor"); return; }
        Intent i = new Intent(this, MainActivity.class).setAction("com.remna.boost.BOOST")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.graphics.drawable.Icon icon = gameIcon.getDrawable() != null
                ? android.graphics.drawable.Icon.createWithBitmap(toBitmap(gameIcon.getDrawable()))
                : android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher);
        android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "boost_play")
                .setShortLabel("Boost & Oyna").setIcon(icon).setIntent(i).build();
        sm.requestPinShortcut(info, null);
    }

    static android.graphics.Bitmap toBitmap(Drawable d) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 192, 192);
        d.draw(c);
        return b;
    }

    /* ---------------- bölge ping testi ---------------- */
    void openRegionTest() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        boolean vpn = Boost.vpnActive(this);
        TextView head = text((vpn ? "VPN üzerinden ölçülüyor" : "Doğrudan bağlantı (VPN kapalı)") + " · her bölge 10 örnek · skor: %40 ortanca, %25 dalgalanma, %25 kayıp, %10 kararlılık", 12, TX2, false);
        box.addView(head);
        TextView rec = text("Ölçülüyor…", 15, OR, true);
        rec.setPadding(0, dp(8), 0, dp(10));
        box.addView(rec);
        int n = REGIONS.length;
        TextView[] res = new TextView[n];
        LinearLayout[] rows = new LinearLayout[n];
        for (int i = 0; i < n; i++) {
            LinearLayout r = card();
            r.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text(REGIONS[i][0], 15, TX, true));
            res[i] = text("…", 12, TX2, false);
            t.addView(res[i]);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(text(REGIONS[i][2], 11, TX3, false));
            final int idx = i;
            r.setOnClickListener(v -> {
                prefs.edit().putString("ping_host", REGIONS[idx][1]).apply();
                toast("Panel pingi artık " + REGIONS[idx][0] + " bölgesini ölçecek");
            });
            rows[i] = r;
            box.addView(r, mlp(6));
        }
        TextView foot = text("Bir bölgeye dokunursan oyun üstü panel o bölgenin pingini gösterir. Bölgeyi PUBG lobisinde sol üstten değiştirebilirsin. Değerler o bölgedeki veri merkezine göre ölçülür; oyundaki ping birkaç ms farklı olabilir.", 11, TX3, false);
        foot.setPadding(0, dp(10), 0, 0);
        box.addView(foot);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("PUBG bölge ping testi").setView(sv).setNegativeButton("Kapat", null).show();

        int[] best = new int[n];
        int[] done = {0};
        for (int i = 0; i < n; i++) {
            final int idx = i;
            new Thread(() -> {
                java.util.List<String> dcs = Boost.dcFor(this, regionOfHost(REGIONS[idx][1]) == null ? REGIONS[idx][0] : regionOfHost(REGIONS[idx][1]));
                String dcIp = null;
                for (String ip : dcs) if (Boost.icmpOnce(ip, 1200, 1) > 0) { dcIp = ip; break; }
                final String target = dcIp;
                PingStats ps = new PingStats();
                for (int k = 0; k < 10; k++) {
                    ps.add(target != null ? Boost.icmpOnce(target, 1200, k + 2) : Boost.rtt(REGIONS[idx][1]));
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                }
                h.post(() -> {
                    if (!dlg.isShowing()) return;
                    if (ps.empty()) {
                        res[idx].setText("Ulaşılamadı · ölçülebilir PUBG sunucusu yok (oyunda dene)");
                        res[idx].setTextColor(RED);
                        best[idx] = -1;
                    } else {
                        int sc = ps.score();
                        res[idx].setText("skor " + sc + "  ·  " + ps.summary() + (target != null ? "\nYöntem: PUBG SUNUCUSU (ICMP)" : "\nYöntem: TAHMİNİ (HTTP)"));
                        int m = ps.median();
                        res[idx].setTextColor(m < 80 ? GREEN : m < 150 ? 0xFFFBBF24 : RED);
                        best[idx] = target != null ? sc : sc / 2; // tahmini ölçümler öneride geri planda
                    }
                    done[0]++;
                    if (done[0] == n) {
                        int b = 0;
                        for (int j = 1; j < n; j++) if (best[j] > best[b]) b = j;
                        if (best[b] <= 0) { rec.setText("Hiçbir bölge ölçülemedi"); rec.setTextColor(RED); return; }
                        rec.setText("Önerilen bölge: " + REGIONS[b][0] + "  (skor " + best[b] + ")\nEn düşük ve en stabil bölge budur; fiziksel gecikmeyi değiştirmez.");
                        GradientDrawable g = round(0x1AF97316, 20);
                        g.setStroke(dp(1), OR);
                        rows[b].setBackground(g);
                    }
                });
            }).start();
        }
    }

    /** Ping bölgesi ayarındaki host → ServerLog bölge adı. */
    static String regionOfHost(String host) {
        for (String[] r : REGIONS) if (r[1].equals(host)) {
            if (r[0].startsWith("Hindistan")) return "Hindistan/Güney Asya";
            return r[0];
        }
        return null;
    }

    String regionName() {
        String h = prefs.getString("ping_host", REGIONS[0][1]);
        for (String[] r : REGIONS) if (r[1].equals(h)) return r[0];
        return REGIONS[0][0];
    }

    /* ---------------- oyun ---------------- */
    void pickGame() {
        List<String[]> games = Boost.installedGames(this);
        String saved = prefs.getString("game", null);
        game = null;
        for (String[] g : games) if (g[0].equals(saved)) game = g[0];
        if (game == null && !games.isEmpty()) game = games.get(0)[0];
        if (game == null) {
            gameName.setText("PUBG bulunamadı");
            gameSub.setText("PUBG Mobile yüklü değil");
            gameIcon.setImageDrawable(null);
            return;
        }
        try {
            PackageManager pm = getPackageManager();
            Drawable ic = pm.getApplicationIcon(game);
            gameIcon.setImageDrawable(ic);
            String label = game;
            for (String[] g : Boost.PUBG) if (g[0].equals(game)) label = g[1];
            gameName.setText(label);
            gameSub.setText("v" + pm.getPackageInfo(game, 0).versionName + (games.size() > 1 ? "  •  değiştirmek için dokun" : ""));
        } catch (Exception ignored) {}
    }

    void chooseGame() {
        List<String[]> games = Boost.installedGames(this);
        if (games.size() < 2) return;
        String[] names = new String[games.size()];
        for (int i = 0; i < names.length; i++) names[i] = games.get(i)[1];
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Oyun seç")
                .setItems(names, (d, w) -> { prefs.edit().putString("game", games.get(w)[0]).apply(); pickGame(); }).show();
    }

    void boost() {
        if (game == null) { toast("PUBG Mobile yüklü değil"); return; }
        if (!Sh.granted() && prefs.getBoolean("fps", true) && !prefs.getBoolean("pro_hint", false)) {
            prefs.edit().putBoolean("pro_hint", true).apply();
            toast("Ping ve diğer özellikler çalışıyor. FPS göstergesi için isteğe bağlı Shizuku gerekir.");
        }
        big.setBusy(true);
        new Thread(() -> {
            java.util.List<String> done = new java.util.ArrayList<>(), skipped = new java.util.ArrayList<>();
            step("CİHAZ ANALİZİ");
            long total = Boost.totalRam(this), before = Boost.availRam(this);
            float temp = Boost.batteryTemp(this);
            step("RAM KONTROLÜ");
            String mode = prefs.getString("cleanup", "smart");
            int killed = Boost.cleanup(this, game, mode);
            sleep(700);
            long freed = Math.max(0, Boost.availRam(this) - before);
            if (killed >= 0) done.add(String.format(java.util.Locale.US, "Arka plan temizliği · %d uygulama · +%d MB", killed, freed / 1048576));
            else skipped.add("off".equals(mode) ? "Temizlik kapalı" : "Temizlik gerekmedi · RAM yeterli (%" + (before * 100 / Math.max(1, total)) + " boş)");
            step("AĞ KONTROLÜ");
            int ping = -1;
            String reg = regionOfHost(prefs.getString("ping_host", ""));
            for (String ip : Boost.dcFor(this, reg == null ? "Avrupa" : reg)) { ping = Boost.icmp(ip); if (ping > 0) break; }
            step("SICAKLIK KONTROLÜ");
            float[] th = Thermal.thresholds(this);
            Thermal.State ts = th == null ? Thermal.State.NORMAL : Thermal.classify(temp, th);
            if (th != null) done.add("Termal koruma · şu an " + Thermal.label(ts) + String.format(java.util.Locale.US, " (%.0f°C)", temp));
            step("OPTİMİZASYON");
            String gm = Sh.granted() ? Perf.applyGameMode(this, game) : null;
            if (gm != null && !gm.equals("kapalı") && !gm.toLowerCase().contains("error") && !gm.toLowerCase().contains("exception"))
                done.add("Android oyun modu · " + prefs.getString("gm_mode", "performance"));
            else if (!Sh.granted() && !"off".equals(prefs.getString("gm_mode", "off"))) skipped.add("Android oyun modu · Shizuku gerekli");
            boolean secure = Tweaks.secureAllowed(this);
            String hz = prefs.getString("hz_mode", "max");
            if (!"off".equals(hz)) {
                if (secure) done.add("Yenileme hızı · " + ("max".equals(hz) ? Math.round(Tweaks.maxRefresh(this)) : hz) + " Hz");
                else skipped.add("Sabit yenileme hızı · ADB izni gerekli");
            }
            if (!"off".equals(prefs.getString("dnd_mode", "priority"))) {
                if (Boost.dndAllowed(this)) done.add("Rahatsız etme");
                else skipped.add("Rahatsız etme · izin gerekli");
            }
            if (prefs.getBoolean("touch", true) && Tweaks.touchSupported(this)) {
                if (secure || Sh.granted()) done.add("Dokunma hassasiyeti");
                else skipped.add("Dokunma hassasiyeti · ADB izni gerekli");
            }
            if (!"off".equals(prefs.getString("ov_mode", "full")) && Settings.canDrawOverlays(this)) done.add("Oyun üstü panel");
            final int fPing = ping;
            h.post(() -> {
                big.setBusy(false);
                btnLabel.setText("BOOST");
                Intent sv = new Intent(this, BoostService.class).putExtra("game", game);
                startForegroundService(sv);
                showBoostResult(done, skipped, fPing, temp);
            });
        }).start();
    }

    void step(String t) { h.post(() -> { btnLabel.setTextSize(14); btnLabel.setText(t); }); sleep(350); }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    void showBoostResult(java.util.List<String> done, java.util.List<String> skipped, int ping, float temp) {
        btnLabel.setTextSize(26);
        StringBuilder sb = new StringBuilder();
        sb.append(gameName.getText()).append("\n\n");
        sb.append(String.format(java.util.Locale.US, "RAM  %s boş   ·   PING  %s   ·   SICAKLIK  %.0f°C%n%n",
                Boost.fmtGb(Boost.availRam(this)), ping > 0 ? ping + " ms" : "—", temp));
        sb.append("Uygulananlar:\n");
        for (String d : done) sb.append("✓ ").append(d).append('\n');
        if (!skipped.isEmpty()) {
            sb.append("\nUygulanmayanlar:\n");
            for (String k : skipped) sb.append("–  ").append(k).append('\n');
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("✓ BOOST TAMAMLANDI")
                .setMessage(sb.toString())
                .setPositiveButton("OYUNU BAŞLAT", (d, w) -> {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(game);
                    if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    final Runnable stats = new Runnable() {
        @Override public void run() {
            long a = Boost.availRam(MainActivity.this), t = Boost.totalRam(MainActivity.this);
            ramTv.setText(Boost.fmtGb(a));
            ramTv.setTextColor(a * 100 / Math.max(1, t) > 25 ? GREEN : OR);
            float temp = Boost.batteryTemp(MainActivity.this);
            tempTv.setText(String.format(java.util.Locale.US, "%.0f°C", temp));
            tempTv.setTextColor(temp < 38 ? TX : temp < 43 ? 0xFFFBBF24 : RED);
            String reg = regionOfHost(prefs.getString("ping_host", ""));
            new Thread(() -> {
                int p = -1;
                for (String ip : Boost.dcFor(MainActivity.this, reg == null ? "Avrupa" : reg)) { p = Boost.icmp(ip); if (p > 0) break; }
                final int fp = p;
                h.post(() -> {
                    pingTv.setText(fp < 0 ? "—" : fp + " ms");
                    pingTv.setTextColor(fp < 0 ? RED : fp < 80 ? GREEN : fp < 150 ? 0xFFFBBF24 : RED);
                });
            }).start();
            if (!big.busy) { btnLabel.setTextSize(26); btnLabel.setText(BoostService.running ? "OYUNDA" : "BOOST"); }
            h.postDelayed(this, 3000);
        }
    };

    /* ---------------- izinler ---------------- */
    void askOverlay() {
        if (!Settings.canDrawOverlays(this))
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    void askDnd() {
        if (!Boost.dndAllowed(this)) startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    void askSystem() {
        if (!Tweaks.systemAllowed(this))
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    void askUsage() { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }

    void renderPerms() {
        permBox.removeAllViews();
        perm("Oyun üstü panel", Settings.canDrawOverlays(this), this::askOverlay);
        perm("Rahatsız etme erişimi", Boost.dndAllowed(this), this::askDnd);
        perm("Sistem ayarlarını değiştirme", Tweaks.systemAllowed(this), this::askSystem);
        perm("Kullanım erişimi (oyundan çıkınca otomatik kapanır)", Boost.usageAllowed(this), this::askUsage);
        perm("Shizuku (FPS · otomatik ADB izni)", Sh.granted(), this::askShizuku);
        String diag = prefs.getString("fps_diag", null);
        if (diag != null) {
            LinearLayout l = card();
            l.addView(text("FPS tanılama kaydı", 14, TX, false), new LinearLayout.LayoutParams(0, -2, 1));
            l.addView(text("Göster", 13, OR, true));
            l.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("FPS tanılama").setMessage(diag)
                    .setPositiveButton("Kopyala", (d, w) -> {
                        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("fps", diag));
                        toast("Kopyalandı");
                    }).setNegativeButton("Kapat", null).show());
            permBox.addView(l, mlp(8));
        }
        perm("ADB izni (sabit Hz · animasyon)", Tweaks.secureAllowed(this), this::showAdbHelp);
    }

    void perm(String name, boolean ok, Runnable ask) {
        LinearLayout l = card();
        TextView t = text(name, 14, TX, false);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView s = text(ok ? "Verildi" : "İzin ver", 13, ok ? GREEN : OR, true);
        l.addView(s);
        if (!ok) l.setOnClickListener(v -> ask.run());
        LinearLayout.LayoutParams lp = mlp(8);
        permBox.addView(l, lp);
    }

    void showAdbHelp() {
        String msg = "Bilgisayar gerekmez. Telefonda:\n\n1) Geliştirici seçenekleri → Kablosuz hata ayıklama → aç\n2) Termux'ta adb ile bağlan\n3) Şu komutu çalıştır:\n\n" + ADB_CMD + "\n\nBir kere yeterli; izin kalıcıdır.";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("ADB izni")
                .setMessage(msg)
                .setPositiveButton("Komutu kopyala", (d, w) -> {
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD));
                    toast("Kopyalandı");
                })
                .setNegativeButton("Kapat", null).show();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /* ---------------- büyük düğme ---------------- */
    static final class BigButton extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean busy;
        float spin;

        BigButton(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        void setBusy(boolean b) { busy = b; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2;
            float d = getResources().getDisplayMetrics().density;
            p.setShader(new RadialGradient(cx, cy, R, new int[]{0x66F97316, 0x22E11D48, 0x00000000}, new float[]{0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, R, p);
            float core = R * 0.66f;
            p.setShader(new LinearGradient(cx - core, cy - core, cx + core, cy + core, 0xFFFB923C, 0xFFE11D48, Shader.TileMode.CLAMP));
            p.setShadowLayer(22 * d, 0, 8 * d, 0x88E11D48);
            cv.drawCircle(cx, cy, core, p);
            p.clearShadowLayer();
            p.setShader(new LinearGradient(cx, cy - core, cx, cy + core * 0.2f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, core, p);
            ring.setStrokeWidth(3 * d);
            float r2 = core + 14 * d;
            if (busy) {
                ring.setColor(0xFFFB923C);
                cv.drawArc(new RectF(cx - r2, cy - r2, cx + r2, cy + r2), spin, 100, false, ring);
                spin = (spin + 8) % 360;
                postInvalidateOnAnimation();
            } else {
                ring.setColor(0x22FFFFFF);
                ring.setStrokeWidth(1 * d);
                cv.drawCircle(cx, cy, r2, ring);
            }
        }
    }
}
