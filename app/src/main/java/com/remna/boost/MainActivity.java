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
    static final String[][] REGIONS = {
            {"Orta Doğu", "ec2.me-south-1.amazonaws.com"},
            {"Avrupa", "ec2.eu-central-1.amazonaws.com"},
            {"Asya", "ec2.ap-southeast-1.amazonaws.com"},
            {"Hindistan", "ec2.ap-south-1.amazonaws.com"},
    };
    static final String ADB_CMD = "adb shell pm grant com.remna.boost android.permission.WRITE_SECURE_SETTINGS";

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    String game;
    LinearLayout root, permBox;
    TextView ramTv, tempTv, pingTv, gameName, gameSub, btnLabel;
    ImageView gameIcon;
    BigButton big;

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Boost.prefs(this);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        pickGame();
        renderPerms();
        h.removeCallbacks(stats);
        h.post(stats);
    }

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

    View build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(root);

        // başlık
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        top.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView title = text("Remna Boost", 22, TX, true);
        title.setPadding(dp(12), 0, 0, 0);
        top.addView(title);
        root.addView(top);

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
        root.addView(gc, mlp(20));

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

        // ayarlar
        root.addView(section("OYUN MODU"));
        root.addView(toggle("Arka plan uygulamalarını kapat", "Boost sırasında RAM boşaltır", "kill", true, null));
        root.addView(toggle("Rahatsız etme", "Oyunda bildirim ve arama gelmez", "dnd", true, this::askDnd), mlp(8));
        root.addView(toggle("Oyun üstü panel", "Ping · boş RAM · sıcaklık", "overlay", true, this::askOverlay), mlp(8));
        root.addView(toggle("Maksimum yenileme hızı", String.format(java.util.Locale.US, "Ekranı %.0f Hz'e sabitler · ADB izni gerekir", Tweaks.maxRefresh(this)), "hz", true, this::askSystem), mlp(8));
        root.addView(toggle("Otomatik parlaklığı kapat", "Oyunda parlaklık zıplamaz", "autobright", true, this::askSystem), mlp(8));
        root.addView(toggle("Animasyonları hızlandır", "ADB izni gerekir (tek seferlik)", "anim", true, null), mlp(8));
        root.addView(regionRow(), mlp(8));

        root.addView(section("İZİNLER"));
        permBox = new LinearLayout(this);
        permBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(permBox);

        TextView note = text("Not: Root olmadan oyunun FPS'i doğrudan artırılamaz. Remna Boost RAM boşaltır, kesintileri engeller, ekranı en yüksek yenileme hızına sabitler ve oyun sırasında ping/sıcaklığı gösterir. Oyun dosyalarına dokunmaz (ban riski yok).", 12, TX3, false);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);
        return sv;
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
        });
        l.addView(sw);
        l.setOnClickListener(v -> sw.toggle());
        return l;
    }

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
        big.setBusy(true);
        btnLabel.setText("…");
        new Thread(() -> {
            long before = Boost.availRam(this);
            int killed = prefs.getBoolean("kill", true) ? Boost.killBackground(this, game) : 0;
            try { Thread.sleep(900); } catch (InterruptedException ignored) {}
            long freed = Math.max(0, Boost.availRam(this) - before);
            h.post(() -> {
                big.setBusy(false);
                btnLabel.setText("BOOST");
                toast(String.format(java.util.Locale.US, "%d uygulama kapatıldı · %d MB boşaldı", killed, freed / 1048576));
                Intent s = new Intent(this, BoostService.class).putExtra("game", game);
                startForegroundService(s);
                Intent launch = getPackageManager().getLaunchIntentForPackage(game);
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
        }).start();
    }

    final Runnable stats = new Runnable() {
        @Override public void run() {
            long a = Boost.availRam(MainActivity.this), t = Boost.totalRam(MainActivity.this);
            ramTv.setText(Boost.fmtGb(a));
            ramTv.setTextColor(a * 100 / Math.max(1, t) > 25 ? GREEN : OR);
            float temp = Boost.batteryTemp(MainActivity.this);
            tempTv.setText(String.format(java.util.Locale.US, "%.0f°C", temp));
            tempTv.setTextColor(temp < 38 ? TX : temp < 43 ? 0xFFFBBF24 : RED);
            String host = prefs.getString("ping_host", REGIONS[0][1]);
            new Thread(() -> {
                int p = Boost.ping(host, 443);
                h.post(() -> {
                    pingTv.setText(p < 0 ? "—" : p + " ms");
                    pingTv.setTextColor(p < 0 ? RED : p < 80 ? GREEN : p < 150 ? 0xFFFBBF24 : RED);
                });
            }).start();
            btnLabel.setText(BoostService.running ? "OYUNDA" : "BOOST");
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
        perm("ADB izni (animasyonlar)", Tweaks.secureAllowed(this), this::showAdbHelp);
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
