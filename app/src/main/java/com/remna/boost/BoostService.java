package com.remna.boost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Oyun modu: ön plan servisi + oyun üstü panel (ping / RAM / sıcaklık) + rahatsız etme + çıkışta geri alma. */
public class BoostService extends Service {
    static final String ACTION_STOP = "com.remna.boost.STOP";
    static volatile boolean running = false;

    final Handler h = new Handler(Looper.getMainLooper());
    WindowManager wm;
    LinearLayout panel;
    TextView pingTv, ramTv, tempTv, fpsTv;
    volatile int lastFps = -1;
    String game;
    long notForegroundSince = 0;
    PowerManager.WakeLock wl;
    Notification notif;
    volatile int lastPing = -1;
    volatile String pingSrc = "";
    volatile float lastCpu = -1;
    Perf.Session session;
    boolean cooling = false;
    int fpsFails = 0;

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        String g = intent != null ? intent.getStringExtra("game") : null;
        if (running && notif != null) { // zaten açık: ikinci panel/döngü başlatma
            if (g != null) game = g;
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, notif);
            return START_NOT_STICKY;
        }
        game = g;
        running = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("boost", "Oyun modu", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BoostService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "boost")
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFFF97316)
                .setContentTitle("Oyun modu açık").setContentText("Ping ve RAM izleniyor")
                .setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Kapat", stop).build()).build();
        notif = n;
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);

        Tweaks.apply(this);
        if (!Boost.prefs(this).getString("dnd_mode", "priority").equals("off")) Boost.dndOn(this);
        if (!Boost.prefs(this).getString("ov_mode", "full").equals("off") && Settings.canDrawOverlays(this)) showPanel();
        PowerManager pm = getSystemService(PowerManager.class);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boost:game");
        wl.acquire(4 * 3600_000L);
        h.post(tick);
        new Thread(pinger).start();
        Sh.layer = null;
        session = new Perf.Session();
        new Thread(() -> {
            while (running) {
                lastFps = (game != null && Boost.prefs(this).getBoolean("fps", true)) ? Sh.fps(game) : -1;
                if (lastFps < 0 && Sh.granted() && game != null && ++fpsFails == 15) // ~15 sn ölçülemezse tanılama kaydet
                    Boost.prefs(this).edit().putString("fps_diag", Sh.fpsDiag(game)).apply();
                lastCpu = Perf.cpuTemp();
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        }).start();
        return START_NOT_STICKY;
    }

    /** Ping ayrı iş parçacığında (ana iş parçacığını bloklamasın). */
    final Runnable pinger = () -> {
        while (running) {
            int v = -1;
            pingSrc = "";
            // 1) Shizuku varsa: PUBG'nin bağlı olduğu gerçek oyun sunucusuna ICMP ping
            if (game != null && Sh.granted()) {
                try {
                    int uid = getPackageManager().getApplicationInfo(game, 0).uid;
                    String ip = Sh.gameServer(uid);
                    if (ip != null) {
                        int r = Sh.icmp(ip);
                        // VPN bazen ICMP'yi yerelde yanıtlar (1-3 ms) → güvenilmez
                        if (r > 5) { v = r; pingSrc = "oyun"; }
                    }
                } catch (Exception ignored) {}
            }
            // 2) Yedek: seçili bölgeye yaklaşık ölçüm
            if (v < 0) {
                String host = Boost.prefs(this).getString("ping_host", MainActivity.REGIONS[0][1]);
                v = Boost.ping(host, 443);
                pingSrc = "≈";
            }
            lastPing = v;
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    };

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (panel != null) {
                int f = lastFps;
                boolean want = Boost.prefs(BoostService.this).getBoolean("fps", true);
                fpsTv.setVisibility(want ? View.VISIBLE : View.GONE);
                if (!Sh.granted()) { fpsTv.setText("FPS: Shizuku yok"); fpsTv.setTextColor(0xFF94A3B8); }
                else if (f < 0) { fpsTv.setText("— FPS"); fpsTv.setTextColor(0xFF94A3B8); }
                else { fpsTv.setText(f + " FPS"); fpsTv.setTextColor(f >= 55 ? 0xFF34D399 : f >= 30 ? 0xFFFBBF24 : 0xFFEF4444); }
                int p = lastPing;
                pingTv.setText(p < 0 ? "— ms" : ("≈".equals(pingSrc) ? "≈" : "") + p + " ms");
                pingTv.setTextColor(p < 0 ? 0xFFEF4444 : p < 80 ? 0xFF34D399 : p < 150 ? 0xFFFBBF24 : 0xFFEF4444);
                ramTv.setText(Boost.fmtGb(Boost.availRam(BoostService.this)));
                float t = Boost.batteryTemp(BoostService.this);
                float cpu = lastCpu;
                String chg = Perf.chargeState(BoostService.this);
                tempTv.setText(String.format(java.util.Locale.US, "%.0f°C", t)
                        + (cpu > 0 ? String.format(java.util.Locale.US, " · CPU %.0f°", cpu) : "")
                        + (chg.isEmpty() ? "" : " " + chg));
                tempTv.setTextColor(t < 38 ? 0xFFFFFFFF : t < 43 ? 0xFFFBBF24 : 0xFFEF4444);
            }
            // maç raporu için örnek topla (oyun ön plandayken)
            float bt = Boost.batteryTemp(BoostService.this);
            boolean inGame = game == null || !Boost.usageAllowed(BoostService.this)
                    || game.equals(Boost.lastForeground(BoostService.this, game));
            if (session != null && inGame) session.add(lastFps, lastPing, bt, lastCpu);
            // soğutma modu: pil eşiği aşınca 60 Hz'e in, 2° düşünce eski ayara dön
            String ct = Boost.prefs(BoostService.this).getString("cool_temp", "off");
            if (!ct.equals("off")) {
                float thr = Float.parseFloat(ct);
                if (!cooling && bt >= thr) {
                    cooling = true;
                    Tweaks.forceHz(BoostService.this, 60);
                    notifyCool(bt);
                } else if (cooling && bt <= thr - 2) {
                    cooling = false;
                    Tweaks.reapply(BoostService.this);
                    getSystemService(NotificationManager.class).cancel(2);
                }
            }
            // oyundan çıkınca (30 sn) her şeyi geri al
            if (game != null && Boost.usageAllowed(BoostService.this)) {
                String fg = Boost.lastForeground(BoostService.this, null);
                if (fg != null && !fg.equals(game) && !fg.equals(getPackageName())) {
                    if (notForegroundSince == 0) notForegroundSince = System.currentTimeMillis();
                    else {
                        int sec = Integer.parseInt(Boost.prefs(BoostService.this).getString("autostop", "30"));
                        if (sec > 0 && System.currentTimeMillis() - notForegroundSince > sec * 1000L) { stopSelf(); return; }
                    }
                } else if (fg != null && fg.equals(game)) notForegroundSince = 0;
            }
            h.postDelayed(this, 2000);
        }
    };

    void notifyCool(float t) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("cool", "Soğutma", NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(2, new Notification.Builder(this, "cool").setSmallIcon(R.drawable.ic_stat).setColor(0xFFEF4444)
                .setContentTitle(String.format(java.util.Locale.US, "Telefon ısındı (%.0f°C)", t))
                .setContentText("Soğutma modu: ekran 60 Hz'e düşürüldü").setAutoCancel(true).build());
    }

    TextView chip(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        String sz = Boost.prefs(this).getString("ov_size", "normal");
        t.setTextSize(sz.equals("small") ? 10 : sz.equals("large") ? 15 : 12);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(6), 0, dp(6), 0);
        return t;
    }

    void showPanel() {
        wm = getSystemService(WindowManager.class);
        panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable g = new GradientDrawable();
        int alpha = Integer.parseInt(Boost.prefs(this).getString("ov_alpha", "70"));
        g.setColor(((alpha * 255 / 100) << 24) | 0x0B1020);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), 0x33FFFFFF);
        panel.setBackground(g);
        pingTv = chip("…"); ramTv = chip("…"); tempTv = chip("…"); fpsTv = chip("");
        fpsTv.setVisibility(View.GONE);
        panel.addView(fpsTv);
        panel.addView(pingTv);
        if (Boost.prefs(this).getString("ov_mode", "full").equals("full")) { panel.addView(ramTv); panel.addView(tempTv); }
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Boost.prefs(this).getInt("px", dp(12));
        lp.y = Boost.prefs(this).getInt("py", dp(60));
        panel.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { dx = lp.x - e.getRawX(); dy = lp.y - e.getRawY(); return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) { lp.x = (int) (e.getRawX() + dx); lp.y = (int) (e.getRawY() + dy); wm.updateViewLayout(panel, lp); return true; }
                if (e.getAction() == MotionEvent.ACTION_UP) { Boost.prefs(BoostService.this).edit().putInt("px", lp.x).putInt("py", lp.y).apply(); return true; }
                return false;
            }
        });
        try { wm.addView(panel, lp); } catch (Exception e) { panel = null; }
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        if (session != null) session.save(this);
        new Thread(Sh::fpsStop).start();
        getSystemService(NotificationManager.class).cancel(2);
        if (panel != null) try { wm.removeView(panel); } catch (Exception ignored) {}
        Boost.dndOff(this);
        Tweaks.restore(this);
        if (wl != null && wl.isHeld()) wl.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
