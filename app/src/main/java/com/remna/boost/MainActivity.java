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
    // Remna Boost paleti: turuncu yalnız BOOST, ana eylem ve etkin durum için
    static final int BG = 0xFF070A12, CARD = 0xFF101522, CARD2 = 0xFF151C2B, TX = 0xFFF5F7FA, TX2 = 0xFF8B95A7, TX3 = 0xFF5B6476;
    static final int OR = 0xFFFF6B35, RED = 0xFFFF4D67, GREEN = 0xFF35E6A1, YEL = 0xFFFFC857;
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
        if (!prefs.getBoolean("onboarded", false)) h.post(() -> onboarding(0));
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
        GradientDrawable g = round(CARD, 18); g.setStroke(dp(1), 0x12FFFFFF);
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

    static final int TABS = 4;
    LinearLayout[] pages = new LinearLayout[TABS];
    ScrollView[] scrolls = new ScrollView[TABS];
    TextView[] tabs = new TextView[TABS];
    int tab = 0;
    TextView statusPill, healthScore, healthLabel, healthBody, fpsBig, fpsSub;
    LinearLayout setupBox, profileRow, healthParts, perfBox;
    final PingStats homePing = new PingStats();
    boolean showAdvanced;

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
        for (int k = 0; k < TABS; k++) {
            scrolls[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            tabs[k].setTextColor(k == i ? TX : TX3);
            tabs[k].setBackground(k == i ? round(CARD2, 14) : null);
        }
        if (i == 2) renderPerf();
    }

    /** Büyük sayı + küçük etiket (ana ekran metrikleri). */
    TextView metric(LinearLayout parent, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, dp(14), 0, dp(12));
        c.setBackground(round(CARD, 18));
        TextView v = text("—", 22, TX, true);
        c.addView(v);
        TextView l = text(label, 11, TX3, true);
        l.setLetterSpacing(0.1f);
        c.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(4); lp.rightMargin = dp(4);
        parent.addView(c, lp);
        return v;
    }

    /** Sağda "›" olan tıklanabilir satır kartı. */
    LinearLayout navCard(String title, String sub, View.OnClickListener l) {
        LinearLayout c = card();
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 15, TX, true));
        if (sub != null) t.addView(text(sub, 12, TX2, false));
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(text("›", 22, TX3, false));
        c.setOnClickListener(l);
        return c;
    }

    View build() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        // başlık
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        top.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        TextView title = text("REMNA BOOST", 17, TX, true);
        title.setLetterSpacing(0.08f);
        tt.addView(title);
        tt.addView(text("Oyun Performans Merkezi", 11, TX2, false));
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        statusPill = text("…", 11, TX, true);
        statusPill.setPadding(dp(10), dp(5), dp(10), dp(5));
        statusPill.setOnClickListener(v -> openPermissions());
        top.addView(statusPill);
        outer.addView(top);

        FrameLayout content = new FrameLayout(this);
        outer.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pHome = newPage(content, 0), pNet = newPage(content, 1), pPerf = newPage(content, 2), pSet = newPage(content, 3);

        // alt gezinme
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(10), dp(8), dp(10), dp(10));
        nav.setBackgroundColor(0xFF0B0F1A);
        String[] names = {"ANA", "AĞ", "PERFORMANS", "AYARLAR"};
        for (int i = 0; i < TABS; i++) {
            TextView t = text(names[i], 11, TX3, true);
            t.setLetterSpacing(0.08f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(11), 0, dp(11));
            final int idx = i;
            t.setOnClickListener(v -> selectTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(3); lp.rightMargin = dp(3);
            nav.addView(t, lp);
            tabs[i] = t;
        }
        outer.addView(nav);

        buildHome(pHome);
        buildNetwork(pNet);
        buildPerformance(pPerf);
        buildSettings(pSet);
        renderProfiles();
        selectTab(tab);
        return outer;
    }

    /* ================= ANA ================= */
    void buildHome(LinearLayout p) {
        root = p;
        LinearLayout gc = card();
        gameIcon = new ImageView(this);
        gc.addView(gameIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout gt = new LinearLayout(this);
        gt.setOrientation(LinearLayout.VERTICAL);
        gt.setPadding(dp(14), 0, 0, 0);
        gameName = text("PUBG", 16, TX, true);
        gameSub = text("", 12, GREEN, true);
        gt.addView(gameName); gt.addView(gameSub);
        gc.addView(gt, new LinearLayout.LayoutParams(0, -2, 1));
        gc.setOnClickListener(v -> chooseGame());
        root.addView(gc, mlp(4));

        FrameLayout bf = new FrameLayout(this);
        big = new BigButton(this);
        bf.addView(big, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
        btnLabel = text("BOOST", 26, Color.WHITE, true);
        btnLabel.setLetterSpacing(0.12f);
        btnLabel.setGravity(Gravity.CENTER);
        bf.addView(btnLabel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        big.setOnClickListener(v -> boost());
        big.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            else if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        root.addView(bf, mlp(10));

        LinearLayout st = new LinearLayout(this);
        fpsBig = metric(st, "FPS");
        pingTv = metric(st, "PING");
        tempTv = metric(st, "SICAKLIK");
        root.addView(st, mlp(8));
        ramTv = new TextView(this); // RAM ana ekranda değil, sağlık kartında

        // Oyun Sağlığı
        LinearLayout hc = card();
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.setGravity(Gravity.START);
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        TextView ht = text("OYUN SAĞLIĞI", 11, TX3, true);
        ht.setLetterSpacing(0.1f);
        hl.addView(ht);
        healthLabel = text("ÖLÇÜLÜYOR", 16, TX, true);
        hl.addView(healthLabel);
        hr.addView(hl, new LinearLayout.LayoutParams(0, -2, 1));
        healthScore = text("—", 34, TX, true);
        hr.addView(healthScore);
        hc.addView(hr);
        healthParts = new LinearLayout(this);
        hc.addView(healthParts, mlp(10));
        healthBody = text("", 12, TX2, false);
        healthBody.setLineSpacing(dp(2), 1f);
        hc.addView(healthBody, mlp(8));
        root.addView(hc, mlp(12));

        TextView ps = section("PROFİL");
        root.addView(ps);
        profileRow = new LinearLayout(this);
        root.addView(profileRow);

        setupBox = new LinearLayout(this);
        setupBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(setupBox);

        root.addView(section("HIZLI ERİŞİM"));
        LinearLayout qa = new LinearLayout(this);
        String[][] q = {{"Performans", "2"}, {"Ağ", "1"}, {"Panel", "p"}};
        for (String[] it : q) {
            TextView t = text(it[0], 13, TX, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(14), 0, dp(14));
            t.setBackground(round(CARD, 16));
            t.setOnClickListener(v -> { if ("p".equals(it[1])) chooseOverlayMode(); else selectTab(Integer.parseInt(it[1])); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(4); lp.rightMargin = dp(4);
            qa.addView(t, lp);
        }
        root.addView(qa);
    }

    void chooseOverlayMode() {
        String[] l = {"Kapalı", "Minimal · FPS • ping", "Ağ · ping, dalgalanma, kayıp", "Performans · FPS, RAM, CPU, pil", "Tam · hepsi + kare süresi"};
        String[] v = {"off", "minimal", "network", "performance", "full"};
        String cur = prefs.getString("ov_mode", "full");
        if ("ping".equals(cur)) cur = "minimal";
        int sel = 0;
        for (int i = 0; i < v.length; i++) if (v[i].equals(cur)) sel = i;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Oyun üstü panel")
                .setSingleChoiceItems(l, sel, (d, w) -> {
                    prefs.edit().putString("ov_mode", v[w]).apply();
                    d.dismiss();
                    if (!v[w].equals("off")) askOverlay();
                    if (BoostService.running) toast("Panel bir sonraki oyun modunda değişir");
                }).show();
    }

    /** Ana ekran: FPS/ping/sıcaklık + sağlık. Oyun modu açıksa canlı veriler, değilse ev ölçümleri. */
    void renderHome(int homePingMs) {
        if (homePingMs != Integer.MIN_VALUE) homePing.add(homePingMs);
        while (homePing.sent > 12) { homePing.sent--; if (!homePing.samples.isEmpty()) homePing.samples.remove(0); }
        boolean live = BoostService.running;
        int f = live ? Live.fps : -1;
        fpsBig.setText(f < 0 ? "—" : String.valueOf(f));
        fpsBig.setTextColor(f < 0 ? TX3 : f >= 55 ? GREEN : f >= 30 ? YEL : RED);
        float temp = Boost.batteryTemp(this);
        tempTv.setText(String.format(java.util.Locale.US, "%.0f°", temp));
        tempTv.setTextColor(temp < 38 ? TX : temp < 43 ? YEL : RED);
        PingStats ps = live && Live.pingStats().sent > 0 ? Live.pingStats() : homePing;
        int pm = ps.median();
        if (!live && homePingMs != Integer.MIN_VALUE) pm = homePingMs > 0 ? homePingMs : pm;
        pingTv.setText(pm < 0 ? "—" : String.valueOf(pm));
        pingTv.setTextColor(pm < 0 ? TX3 : pm < 80 ? GREEN : pm < 150 ? YEL : RED);
        Health hh = Health.compute(live ? Live.fpsStats() : null, ps, temp, Boost.availRam(this), Boost.totalRam(this));
        healthScore.setText(hh.total < 0 ? "—" : String.valueOf(hh.total));
        int col = hh.total < 0 ? TX3 : hh.total >= 75 ? GREEN : hh.total >= 55 ? YEL : RED;
        healthScore.setTextColor(col);
        healthLabel.setText(Health.label(hh.total));
        healthParts.removeAllViews();
        String[] pl = {"FPS", "AĞ", "ISI", "RAM"};
        int[] pv = {hh.fps, hh.net, hh.thermal, hh.mem};
        for (int i = 0; i < 4; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, dp(8), 0, dp(8));
            c.setBackground(round(CARD2, 12));
            TextView v = text(pv[i] < 0 ? "—" : String.valueOf(pv[i]), 15, pv[i] < 0 ? TX3 : pv[i] >= 75 ? GREEN : pv[i] >= 55 ? YEL : RED, true);
            c.addView(v);
            c.addView(text(pl[i], 10, TX3, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(3); lp.rightMargin = dp(3);
            healthParts.addView(c, lp);
        }
        StringBuilder sb = new StringBuilder();
        for (String is : hh.issues) sb.append("⚠ ").append(is).append('\n');
        for (String ok : hh.oks) sb.append("✓ ").append(ok).append('\n');
        if (hh.fps < 0) sb.append(live ? "FPS ölçülemiyor bu cihazda (Gelişmiş → Shizuku)\n" : "FPS oyun sırasında ölçülür\n");
        if (hh.rec != null) sb.append("\nÖneri: ").append(hh.rec);
        healthBody.setText(sb.toString().trim());
        gameSub.setText(game == null ? "" : live ? "● OYUNDA" : "● HAZIR");
        gameSub.setTextColor(live ? OR : GREEN);
    }

    /* ================= AĞ ================= */
    void buildNetwork(LinearLayout p) {
        root = p;
        TextView netNote = text("En düşük ve en stabil mevcut bölgeyi analiz eder. Fiziksel gecikmeyi değiştirmez; doğru sonuç için VPN kapalıyken ölç.", 12, TX2, false);
        netNote.setPadding(dp(4), dp(4), dp(4), dp(8));
        root.addView(netNote);
        root.addView(navCard("Tüm bölgeleri test et", "Ortanca · dalgalanma · kayıp · kararlılık · skor", v -> {
            try { openRegionTest(); } catch (RuntimeException t) { toast("Test açılamadı: " + t.getMessage()); }
        }));
        root.addView(regionRow(), mlp(8));
        root.addView(section("SUNUCULAR"));
        LinearLayout sv2 = navCard("Oyun sunucusu", null, v -> openServerInfo());
        srvSub = text("Maça girince PUBG'nin bağlandığı sunucu burada görünür", 12, TX2, false);
        ((LinearLayout) sv2.getChildAt(0)).addView(srvSub);
        root.addView(sv2);
        LinearLayout hc = navCard("Sunucu geçmişi", null, v -> openServerHistory());
        histSub = text("Oynadıkça bağlanılan sunucular bu cihazda saklanır", 12, TX2, false);
        ((LinearLayout) hc.getChildAt(0)).addView(histSub);
        root.addView(hc, mlp(8));
    }

    /* ================= PERFORMANS ================= */
    void buildPerformance(LinearLayout p) {
        root = p;
        perfBox = new LinearLayout(this);
        perfBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(perfBox);
        reportBox = new LinearLayout(this);
        reportBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(section("SON OTURUM RAPORU"));
        root.addView(reportBox);
    }

    LinearLayout statGrid(String[][] cells) {
        LinearLayout row = new LinearLayout(this);
        for (String[] c : cells) {
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setPadding(dp(12), dp(10), dp(8), dp(10));
            b.setBackground(round(CARD, 14));
            TextView l = text(c[0], 10, TX3, true);
            l.setLetterSpacing(0.08f);
            b.addView(l);
            b.addView(text(c[1], 17, TX, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(3); lp.rightMargin = dp(3);
            row.addView(b, lp);
        }
        return row;
    }

    View graphCard(String title, int[] v, int color, String unit) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.START);
        int last = -1;
        for (int i = v.length - 1; i >= 0; i--) if (v[i] > 0) { last = v[i]; break; }
        c.addView(text(title + (last > 0 ? "  ·  " + last + " " + unit : ""), 12, TX2, true));
        int[] clean = new int[v.length];
        for (int i = 0; i < v.length; i++) clean[i] = Math.max(0, v[i]);
        Graph g = new Graph(this, clean);
        g.line.setColor(color);
        g.fillColor = color;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(70));
        lp.topMargin = dp(8);
        c.addView(g, lp);
        return c;
    }

    void renderPerf() {
        if (perfBox == null) return;
        perfBox.removeAllViews();
        boolean live = BoostService.running;
        TextView hdr = text(live ? "CANLI · oyun modu açık" : "Oyun modu kapalı · son oturum verileri", 12, live ? OR : TX2, true);
        hdr.setPadding(dp(4), dp(4), 0, dp(6));
        perfBox.addView(hdr);
        int[] fs = Live.fpsStats();
        int f = Live.fps;
        perfBox.addView(statGrid(new String[][]{
                {"FPS", live && f >= 0 ? String.valueOf(f) : fs == null ? "Yok" : "—"},
                {"ORT", fs == null ? "—" : String.valueOf(fs[0])},
                {"%1 DÜŞÜK", fs == null ? "—" : String.valueOf(fs[1])},
                {"KARE", live && f > 0 ? String.format(java.util.Locale.US, "%.1f ms", 1000f / f) : "—"}}));
        if (fs == null) {
            TextView n = text(Sh.granted() ? "FPS bu oturumda ölçülemedi." : "FPS ölçümü bu cihazda kullanılamıyor (Gelişmiş → Shizuku).", 11, TX3, false);
            n.setPadding(dp(4), dp(6), 0, 0);
            perfBox.addView(n);
        }
        long tot = Boost.totalRam(this), av = Boost.availRam(this);
        android.content.Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int lvl = bi == null ? -1 : bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        float cpu = live ? Live.cpu : -1;
        LinearLayout g2 = statGrid(new String[][]{
                {"RAM", Boost.fmtGb(tot - av).replace(" GB", "") + " / " + Boost.fmtGb(tot)},
                {"PİL", String.format(java.util.Locale.US, "%.0f°C · %%%d", Boost.batteryTemp(this), lvl)},
                {Perf.cpuLabel.toUpperCase(), cpu > 0 ? String.format(java.util.Locale.US, "%.0f°C", cpu) : "—"}});
        perfBox.addView(g2, mlp(8));
        int[] fps = Live.fpsSeries(), pg = Live.pingSeries(), tp = Live.tempSeries();
        if (fps.length > 1) {
            boolean anyFps = false;
            for (int x : fps) if (x > 0) { anyFps = true; break; }
            if (anyFps) perfBox.addView(graphCard("FPS", fps, GREEN, ""), mlp(10));
            perfBox.addView(graphCard("PING", pg, 0xFF6EA8FE, "ms"), mlp(8));
            perfBox.addView(graphCard("SICAKLIK", tp, YEL, "°C"), mlp(8));
        } else {
            TextView n = text("Grafikler oyun modu açıkken oluşur ve yalnız bellekte tutulur.", 11, TX3, false);
            n.setPadding(dp(4), dp(10), 0, 0);
            perfBox.addView(n);
        }
    }

    /* ================= AYARLAR ================= */
    void buildSettings(LinearLayout p) {
        root = p;
        root.addView(section("PERFORMANS"));
        java.util.List<Integer> rates = Tweaks.refreshRates(this);
        String[] hzL = new String[rates.size() + 2], hzV = new String[rates.size() + 2];
        hzL[0] = "Otomatik (sistem yönetsin)"; hzV[0] = "off";
        hzL[1] = "En yüksek (" + Math.round(Tweaks.maxRefresh(this)) + " Hz)"; hzV[1] = "max";
        for (int i = 0; i < rates.size(); i++) { hzL[i + 2] = rates.get(i) + " Hz"; hzV[i + 2] = String.valueOf(rates.get(i)); }
        root.addView(choice("Yenileme hızı", "hz_mode", "max", hzL, hzV, this::askAdb));
        root.addView(choice("Arka plan temizliği", "cleanup", "smart",
                new String[]{"Akıllı (yalnız RAM azsa)", "Agresif (her zaman)", "Kapalı"},
                new String[]{"smart", "aggressive", "off"}, null), mlp(8));
        root.addView(choice("Termal koruma", "thermal", "standard",
                new String[]{"Standart · 40 / 42 / 44°C", "Hassas · 38 / 40 / 42°C", "Kapalı"},
                new String[]{"standard", "sensitive", "off"}, null), mlp(8));
        boolean ts = Tweaks.touchSupported(this);
        root.addView(toggle("Dokunma optimizasyonu", ts ? "Ekran dokunuşlara daha hızlı tepki verir" : "Bu cihazda desteklenmiyor",
                "touch", true, this::askAdb), mlp(8));
        root.addView(toggle("Otomatik parlaklığı kapat", "Oyunda parlaklık zıplamaz", "autobright", true, this::askSystem), mlp(8));

        root.addView(section("OYUN SIRASINDA"));
        root.addView(choice("Rahatsız etme", "dnd_mode", "priority",
                new String[]{"Kapalı", "Öncelikli", "Sadece alarmlar", "Tam sessiz"},
                new String[]{"off", "priority", "alarms", "none"}, this::askDnd));
        root.addView(navCard("Oyun üstü panel", null, v -> chooseOverlayMode()), mlp(8));
        root.addView(choice("Panel boyutu", "ov_size", "normal",
                new String[]{"Küçük", "Normal", "Büyük"}, new String[]{"small", "normal", "large"}, null), mlp(8));
        root.addView(choice("Panel saydamlığı", "ov_alpha", "70",
                new String[]{"%30", "%50", "%70", "%90"}, new String[]{"30", "50", "70", "90"}, null), mlp(8));
        root.addView(choice("Oyundan çıkınca kapat", "autostop", "30",
                new String[]{"Kapalı (elle kapat)", "15 sn sonra", "30 sn sonra", "60 sn sonra"}, new String[]{"0", "15", "30", "60"}, this::askUsage), mlp(8));

        root.addView(section("UYGULAMA"));
        root.addView(navCard("İzinler", "Hangi izin ne için kullanılır", v -> openPermissions()));
        root.addView(navCard("Gizlilik", "Sunucu geçmişi, konum sorgusu, veri silme", v -> openPrivacy()), mlp(8));
        root.addView(navCard("Tanılama", "Sistem durumu ve kopyalanabilir rapor", v -> openDiagnostics()), mlp(8));
        LinearLayout sc = navCard("Ana ekrana \"Boost & Oyna\"", "Tek dokunuşla boost edip oyunu açar", v -> addShortcut());
        root.addView(sc, mlp(8));

        TextView adv = text(showAdvanced ? "GELİŞMİŞ  ▾" : "GELİŞMİŞ  ▸", 12, TX3, true);
        adv.setLetterSpacing(0.08f);
        adv.setPadding(dp(4), dp(22), dp(4), dp(8));
        adv.setOnClickListener(v -> {
            showAdvanced = !showAdvanced;
            int y = scrolls[3].getScrollY();
            setContentView(build());
            refresh();
            scrolls[3].post(() -> scrolls[3].scrollTo(0, y));
        });
        root.addView(adv);
        if (showAdvanced) {
            TextView an = text("Shizuku gerektiren sistem seviyesi ayarlar. Normal kullanım için gerekmez.", 11, TX3, false);
            an.setPadding(dp(4), 0, dp(4), dp(8));
            root.addView(an);
            root.addView(choice("Android oyun modu", "gm_mode", "off",
                    new String[]{"Kapalı", "Standart", "Performans", "Pil tasarrufu"},
                    new String[]{"off", "standard", "performance", "battery"}, this::askShizuku));
            root.addView(choice("Render çözünürlüğü", "gm_scale", "off",
                    new String[]{"Değiştirme", "%90", "%80", "%70", "%50"},
                    new String[]{"off", "0.9", "0.8", "0.7", "0.5"}, this::askShizuku), mlp(8));
            int maxHz = Math.round(Tweaks.maxRefresh(this));
            java.util.List<String> fl = new java.util.ArrayList<>(), fv = new java.util.ArrayList<>();
            fl.add("Kapalı"); fv.add("off");
            int[] opts = maxHz >= 120 ? new int[]{120, 60, 40, 30} : maxHz >= 90 ? new int[]{90, 45, 30} : new int[]{60, 30};
            for (int o : opts) { fl.add(o + " FPS'e sabitle"); fv.add(String.valueOf(o)); }
            root.addView(choice("FPS sabitleme", "gm_fps", "off", fl.toArray(new String[0]), fv.toArray(new String[0]), this::askShizuku), mlp(8));
            root.addView(choice("Animasyon hızı", "anim_mode", "0.5",
                    new String[]{"Değiştirme", "Hızlı (0.5x)", "Kapalı (0x)"}, new String[]{"off", "0.5", "0"}, this::askAdb), mlp(8));
            root.addView(toggle("FPS ölçümü", "SurfaceFlinger üzerinden · Shizuku", "fps", true, this::askShizuku), mlp(8));
            permBox = new LinearLayout(this); // eski izin listesi tanılamaya taşındı
        } else permBox = new LinearLayout(this);

        TextView note = text("Remna Boost oyun dosyalarına dokunmaz. FPS'i sihirli şekilde artırmaz; ölçer, kesintileri engeller, ısınmayı yönetir ve değiştirdiği her ayarı oyun bitince geri yükler.", 11, TX3, false);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);
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
        statusPill.setText(ok ? "● HAZIR" : "● KURULUM " + miss.size());
        statusPill.setTextColor(ok ? GREEN : OR);
        statusPill.setBackground(round(ok ? 0x1A35E6A1 : 0x1AFF6B35, 14));
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
        c.setOnClickListener(v -> openPermissions());
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
        TextView sub = text(labelOf(key, def, labels, values), 12, TX2, false);
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
            profileRow.post(() -> { int y = scrolls[3].getScrollY(); setContentView(build()); refresh(); scrolls[3].post(() -> scrolls[3].scrollTo(0, y)); });
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
        int fillColor = 0xFF35E6A1;

        Graph(Context c, int[] v) {
            super(c);
            this.v = v;
            float d = c.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * d);
            line.setColor(0xFF35E6A1);
            line.setStrokeJoin(Paint.Join.ROUND);
            grid.setColor(0x1AFFFFFF);
        }

        @Override protected void onDraw(Canvas cv) {
            if (v.length < 2) return;
            float w = getWidth(), h = getHeight();
            int max = 10;
            for (int x : v) max = Math.max(max, x);
            max = max + max / 8;
            for (int k = 1; k < 4; k++) cv.drawLine(0, h * k / 4f, w, h * k / 4f, grid);
            android.graphics.Path p = new android.graphics.Path(), f = new android.graphics.Path();
            for (int i = 0; i < v.length; i++) {
                float x = w * i / (v.length - 1f), y = h - h * v[i] / (float) max;
                if (i == 0) { p.moveTo(x, y); f.moveTo(x, h); f.lineTo(x, y); } else { p.lineTo(x, y); f.lineTo(x, y); }
            }
            f.lineTo(w, h);
            f.close();
            int rgb = fillColor & 0x00FFFFFF;
            fill.setShader(new LinearGradient(0, 0, 0, h, 0x44000000 | rgb, rgb, Shader.TileMode.CLAMP));
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
                        res[idx].setTextColor(m < 80 ? GREEN : m < 150 ? YEL : RED);
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
                big.success();
                btnLabel.setTextSize(26);
                btnLabel.setText("✓");
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
            String reg = regionOfHost(prefs.getString("ping_host", ""));
            new Thread(() -> {
                int p = -1;
                if (!BoostService.running) // oyunda ölçümü servis yapar; çift ölçüm yok
                    for (String ip : Boost.dcFor(MainActivity.this, reg == null ? "Avrupa" : reg)) { p = Boost.icmp(ip); if (p > 0) break; }
                final int fp = BoostService.running ? Integer.MIN_VALUE : p;
                h.post(() -> { if (fpsBig != null) renderHome(fp); if (tab == 2) renderPerf(); });
            }).start();
            if (!big.busy) { btnLabel.setTextSize(26); btnLabel.setText(BoostService.running ? "OYUNDA" : "BOOST"); }
            h.postDelayed(this, 3000);
        }
    };

    /* ---------------- tam ekran sayfa (izinler, gizlilik, tanılama) ---------------- */
    LinearLayout sheet(String title, java.util.function.Consumer<LinearLayout> fill) {
        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(14), dp(18), dp(10));
        TextView back = text("‹", 30, TX, false);
        back.setPadding(dp(10), 0, dp(14), dp(4));
        back.setOnClickListener(v -> d.dismiss());
        bar.addView(back);
        TextView t = text(title, 18, TX, true);
        t.setLetterSpacing(0.05f);
        bar.addView(t);
        outer.addView(bar);
        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(4), dp(18), dp(28));
        sv.addView(body);
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        fill.accept(body);
        d.setContentView(outer);
        d.setOnDismissListener(x -> refresh());
        d.show();
        return body;
    }

    /** İzin kartı: ad, durum, gerekli/isteğe bağlı, ne için kullanıldığı, etkinleştir düğmesi. */
    View permCard(String name, boolean ok, boolean required, String why, Runnable enable) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.START);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(name, 15, TX, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView st = text(ok ? "✓ AÇIK" : required ? "✕ KAPALI" : "○ İSTEĞE BAĞLI", 11, ok ? GREEN : required ? RED : TX2, true);
        r.addView(st);
        c.addView(r);
        TextView w = text(why, 12, TX2, false);
        w.setPadding(0, dp(4), 0, 0);
        c.addView(w);
        TextView tag = text(required ? "GEREKLİ" : "İSTEĞE BAĞLI", 10, TX3, true);
        tag.setLetterSpacing(0.08f);
        tag.setPadding(0, dp(6), 0, 0);
        c.addView(tag);
        if (!ok && enable != null) {
            TextView b = text("ETKİNLEŞTİR", 12, Color.WHITE, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(14), dp(10), dp(14), dp(10));
            b.setBackground(round(OR, 12));
            b.setOnClickListener(v -> enable.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(10);
            c.addView(b, lp);
        }
        return c;
    }

    void openPermissions() {
        sheet("İZİNLER", b -> {
            b.addView(permCard("Oyun üstü panel", Settings.canDrawOverlays(this), true,
                    "Oyun sırasında FPS, ping ve sıcaklık panelini göstermek için.", this::askOverlay));
            b.addView(permCard("Kullanım erişimi", Boost.usageAllowed(this), true,
                    "Oyunun kapandığını algılayıp ayarları geri yüklemek için.", this::askUsage), mlp(10));
            b.addView(permCard("Rahatsız etme", Boost.dndAllowed(this), false,
                    "Oyun sırasında bildirim ve aramaları susturmak için.", this::askDnd), mlp(10));
            b.addView(permCard("Sistem ayarları", Tweaks.systemAllowed(this), false,
                    "Otomatik parlaklığı oyun boyunca kapatmak için.", this::askSystem), mlp(10));
            b.addView(permCard("Shizuku", Sh.granted(), false,
                    (Sh.running() ? "Bağlı değil. " : "Kurulu değil ya da çalışmıyor. ")
                            + "FPS ölçümü, Android oyun modu ve sunucu tespiti gibi gelişmiş özellikler için.", this::askShizuku), mlp(10));
            b.addView(permCard("Güvenli ayarlar (ADB)", Tweaks.secureAllowed(this), false,
                    "Sabit yenileme hızı, animasyon ve dokunma optimizasyonu için. Shizuku bağlıysa otomatik verilir.", this::showAdbHelp), mlp(10));
        });
    }

    void openPrivacy() {
        sheet("GİZLİLİK", b -> {
            b.addView(toggle("Sunucu geçmişi kaydı", "PUBG'nin bağlandığı sunucu IP'leri yalnız bu cihazda saklanır", "srv_record", true, null));
            b.addView(toggle("Çevrimiçi konum sorgusu", "Bilinmeyen IP'ler ipwho.is'e (HTTPS) gönderilir", "geo_online", false, null), mlp(8));
            b.addView(toggle("Hata raporları", "Kapalı · hiçbir veri gönderilmez", "crash_reports", false, null), mlp(8));
            b.addView(toggle("Analitik", "Kapalı · uygulamada analitik yok", "analytics", false, null), mlp(8));
            LinearLayout clr = navCard("Sunucu geçmişini temizle", "Kayıtlı IP'ler, konumlar ve ölçümler", v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Sunucu geçmişi silinsin mi?")
                    .setPositiveButton("Sil", (d, w) -> { ServerLog.clear(this); toast("Sunucu geçmişi silindi"); })
                    .setNegativeButton("Vazgeç", null).show());
            b.addView(clr, mlp(16));
            LinearLayout all = navCard("Tüm uygulama verilerini sil", "Ayarlar, profiller, raporlar ve geçmiş", v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Tüm veriler silinsin mi?").setMessage("Önce değiştirilmiş sistem ayarları geri yüklenir.")
                    .setPositiveButton("Sil", (d, w) -> {
                        if (BoostService.running) stopService(new Intent(this, BoostService.class));
                        Tweaks.restore(this);
                        Boost.dndOff(this);
                        prefs.edit().clear().apply();
                        toast("Tüm veriler silindi");
                        recreate();
                    }).setNegativeButton("Vazgeç", null).show());
            b.addView(all, mlp(8));
            TextView n = text("Konumlar önce uygulamanın içindeki tablodan bulunur. Dışarıya veri yalnız \"Çevrimiçi konum sorgusu\" açıksa ve yalnız sunucu IP'si olarak gönderilir.", 11, TX3, false);
            n.setPadding(dp(4), dp(14), dp(4), 0);
            b.addView(n);
        });
    }

    String diagnosticReport() {
        StringBuilder r = new StringBuilder();
        r.append("SİSTEM\n").append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n");
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        r.append("UYGULAMA  Remna Boost ").append(ver).append('\n');
        r.append("OYUN  ").append(game == null ? "bulunamadı" : gameName.getText()).append('\n');
        r.append("SHIZUKU  ").append(Sh.granted() ? "BAĞLI" : Sh.running() ? "İZİN YOK" : "KULLANILAMIYOR").append('\n');
        r.append("PANEL  ").append(Settings.canDrawOverlays(this) ? "Açık" : "Kapalı").append('\n');
        r.append("KULLANIM ERİŞİMİ  ").append(Boost.usageAllowed(this) ? "Açık" : "Kapalı").append('\n');
        r.append("GÜVENLİ AYARLAR  ").append(Tweaks.secureAllowed(this) ? "Açık" : "Kapalı").append('\n');
        r.append("FPS ÖLÇÜMÜ  ").append(!Sh.granted() ? "Kullanılamıyor (Shizuku yok)" : Live.fpsStats() != null ? "Çalışıyor" : "Henüz ölçülmedi").append('\n');
        r.append("YENİLEME HIZLARI  ").append(Tweaks.refreshRates(this)).append(" Hz\n");
        r.append(String.format(java.util.Locale.US, "TERMAL  pil %.0f°C · sensör %s%n", Boost.batteryTemp(this), Perf.cpuCache > 0 ? Perf.cpuLabel + " " + Math.round(Perf.cpuCache) + "°C" : "okunamıyor"));
        r.append("SON TARAMA  ").append(prefs.getString("scan_diag", "yok")).append('\n');
        r.append("SUNUCU KAYDI  ").append(ServerLog.load(this).length()).append(" sunucu\n");
        return r.toString();
    }

    void openDiagnostics() {
        sheet("TANILAMA", b -> {
            TextView t = text("Ağ testi yapılıyor…", 12, TX, false);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextIsSelectable(true);
            LinearLayout c = card();
            c.addView(t, new LinearLayout.LayoutParams(-1, -2));
            b.addView(c);
            TextView cp = text("TANILAMA RAPORUNU KOPYALA", 13, Color.WHITE, true);
            cp.setGravity(Gravity.CENTER);
            cp.setPadding(0, dp(14), 0, dp(14));
            cp.setBackground(round(OR, 14));
            cp.setOnClickListener(v -> {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("diag", t.getText()));
                toast("Kopyalandı");
            });
            b.addView(cp, mlp(14));
            String fd = prefs.getString("fps_diag", null);
            if (fd != null) b.addView(navCard("FPS tanılama kaydı", "SurfaceFlinger çıktısı özeti", v ->
                    new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("FPS tanılama").setMessage(fd)
                            .setPositiveButton("Kopyala", (d, w) -> ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("fps", fd))).setNegativeButton("Kapat", null).show()), mlp(10));
            String base = diagnosticReport();
            t.setText(base + "AĞ  test ediliyor…");
            new Thread(() -> {
                int p = -1;
                for (String ip : Boost.dcFor(this, "Avrupa")) { p = Boost.icmp(ip); if (p > 0) break; }
                final int fp = p;
                h.post(() -> t.setText(base + "AĞ  " + (fp > 0 ? "Çalışıyor (PUBG Avrupa " + fp + " ms)" : "PUBG sunucusuna ulaşılamadı") + (Boost.vpnActive(this) ? " · VPN açık" : "")));
            }).start();
        });
    }

    /* ---------------- ilk açılış rehberi ---------------- */
    void onboarding(int step) {
        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        d.setCancelable(false);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackgroundColor(BG);
        c.setPadding(dp(28), dp(60), dp(28), dp(36));
        TextView stepTv = text(step == 0 ? "" : "ADIM " + step + " / 3", 11, TX3, true);
        stepTv.setLetterSpacing(0.12f);
        c.addView(stepTv);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(72), dp(72));
        llp.topMargin = dp(24);
        c.addView(logo, llp);
        String title, body;
        switch (step) {
            case 0: title = "REMNA BOOST'A\nHOŞ GELDİN"; body = "Oyun Performans Merkezi\n\n✓  FPS ve kare süresi izleme\n✓  Ağ ve bölge analizi\n✓  Termal koruma\n✓  Akıllı optimizasyon"; break;
            case 1: title = "Oyununu seç"; body = installedNames(); break;
            case 2: title = "Oyun üstü paneli aç"; body = "Oyun sırasında FPS, ping ve sıcaklığı küçük bir panelde gösterir. Paneli istediğin yere sürükleyebilirsin."; break;
            default: title = "Gelişmiş özellikler\n(isteğe bağlı)"; body = "Shizuku ile FPS ölçümü, Android oyun modu ve sunucu tespiti açılır. Kurulum birkaç dakika sürer; şimdi atlayıp sonra Ayarlar → İzinler'den açabilirsin."; break;
        }
        TextView tt = text(title, 24, TX, true);
        tt.setGravity(Gravity.CENTER);
        tt.setPadding(0, dp(24), 0, dp(14));
        c.addView(tt);
        TextView bt = text(body, 14, TX2, false);
        bt.setGravity(Gravity.CENTER);
        bt.setLineSpacing(dp(4), 1f);
        c.addView(bt, new LinearLayout.LayoutParams(-1, 0, 1));
        String primary = step == 0 ? "DEVAM" : step == 1 ? "DEVAM" : "ETKİNLEŞTİR";
        TextView pb = text(primary, 14, Color.WHITE, true);
        pb.setGravity(Gravity.CENTER);
        pb.setPadding(0, dp(16), 0, dp(16));
        pb.setBackground(round(OR, 16));
        c.addView(pb, new LinearLayout.LayoutParams(-1, -2));
        TextView skip = text(step >= 2 ? "ATLA" : "", 13, TX2, true);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, dp(16), 0, 0);
        c.addView(skip, new LinearLayout.LayoutParams(-1, -2));
        Runnable next = () -> {
            d.dismiss();
            if (step >= 3) { prefs.edit().putBoolean("onboarded", true).apply(); refresh(); }
            else onboarding(step + 1);
        };
        pb.setOnClickListener(v -> {
            if (step == 1 && Boost.installedGames(this).size() > 1) { d.dismiss(); chooseGameThen(() -> onboarding(2)); return; }
            if (step == 2) askOverlay();
            if (step == 3) askShizuku();
            next.run();
        });
        skip.setOnClickListener(v -> next.run());
        d.setContentView(c);
        d.show();
    }

    String installedNames() {
        java.util.List<String[]> g = Boost.installedGames(this);
        if (g.isEmpty()) return "Desteklenen bir PUBG Mobile sürümü bulunamadı. Uygulamayı yine de kullanabilir, oyunu yükledikten sonra ana ekrandan seçebilirsin.";
        StringBuilder sb = new StringBuilder("Bulunan oyunlar:\n\n");
        for (String[] x : g) sb.append("●  ").append(x[1]).append('\n');
        sb.append(g.size() > 1 ? "\nDevam'a basınca hangisini kullanacağını seçebilirsin." : "\nBu oyun otomatik seçildi.");
        return sb.toString();
    }

    void chooseGameThen(Runnable after) {
        List<String[]> games = Boost.installedGames(this);
        String[] names = new String[games.size()];
        for (int i = 0; i < names.length; i++) names[i] = games.get(i)[1];
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Oyun seç").setCancelable(false)
                .setItems(names, (d, w) -> { prefs.edit().putString("game", games.get(w)[0]).apply(); pickGame(); after.run(); }).show();
    }

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
        float spin, pulse;
        long successUntil;
        final android.animation.ValueAnimator pulser = android.animation.ValueAnimator.ofFloat(0f, 1f);

        BigButton(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
            pulser.setDuration(2400);
            pulser.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulser.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulser.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            pulser.addUpdateListener(a -> { pulse = (float) a.getAnimatedValue(); invalidate(); });
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); pulser.start(); }

        @Override protected void onDetachedFromWindow() { pulser.cancel(); super.onDetachedFromWindow(); }

        @Override protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            if (v == VISIBLE) { if (!pulser.isStarted()) pulser.start(); } else pulser.cancel(); // görünmezken CPU harcama
        }

        void setBusy(boolean b) { busy = b; invalidate(); }

        void success() { successUntil = System.currentTimeMillis() + 1400; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2;
            float d = getResources().getDisplayMetrics().density;
            boolean ok = System.currentTimeMillis() < successUntil;
            int glowA = (int) (0x30 + 0x30 * (busy ? 1 : pulse));
            int glow = ok ? 0x35E6A1 : 0xFF6B35;
            p.setShader(new RadialGradient(cx, cy, R, new int[]{(glowA << 24) | glow, 0x10000000 | glow, 0}, new float[]{0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, R, p);
            float core = R * 0.64f;
            p.setShader(new LinearGradient(cx - core, cy - core, cx + core, cy + core, ok ? 0xFF35E6A1 : 0xFFFF8A57, ok ? 0xFF1FB57E : 0xFFE8451C, Shader.TileMode.CLAMP));
            p.setShadowLayer(18 * d, 0, 6 * d, ok ? 0x6635E6A1 : 0x66E8451C);
            cv.drawCircle(cx, cy, core, p);
            p.clearShadowLayer();
            p.setShader(new LinearGradient(cx, cy - core, cx, cy + core * 0.2f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, core, p);
            float r2 = core + 14 * d;
            if (busy) {
                ring.setStrokeWidth(3 * d);
                ring.setColor(0xFFFF8A57);
                cv.drawArc(new RectF(cx - r2, cy - r2, cx + r2, cy + r2), spin, 100, false, ring);
                spin = (spin + 7) % 360;
                postInvalidateOnAnimation();
            } else {
                ring.setStrokeWidth(1 * d);
                ring.setColor(ok ? 0x8835E6A1 : (0x14 + (int) (0x18 * pulse)) << 24 | 0xFFFFFF);
                cv.drawCircle(cx, cy, r2, ring);
                if (ok) postInvalidateDelayed(100);
            }
        }
    }
}
