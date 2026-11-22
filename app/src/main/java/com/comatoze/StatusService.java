package com.comatoze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Notifikasi status real-time: mode aktif (Performa / Seimbang / Eco, termasuk otomatis saat game), status thermal,
 * CPU/GPU/suhu/baterai. Data dari satu proses `su` yang mengalirkan baris "ST|..." dari `ultrapower statloop`.
 * Dijalankan sebagai foreground service supaya tidak dimatikan sistem.
 */
public class StatusService extends Service {
    private static final String CH = "status";
    private static final int ID = 7;
    private volatile boolean running = false;
    private volatile Process proc;
    private String lastKey = "";
    private Bitmap lastIcon;
    private String lastIconKey = "";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CH, "Status Comatoze", NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        c.setSound(null, null);
        c.enableVibration(false);
        nm.createNotificationChannel(c);
        try {
            startForeground(ID, base("Comatoze", "Menghubungkan ke modul…", Color.parseColor("#3DDC84")).build());
        } catch (Throwable t) { stopSelf(); return START_NOT_STICKY; }

        if (intent != null && "MODE".equals(intent.getAction())) {
            final String m = intent.getStringExtra("m");
            if (m != null && m.matches("perf|plite|balance|eco")) {
                new Thread(new Runnable() { @Override public void run() { Root.exec(Root.BIN + " mode " + m); } }).start();
            }
        }
        if (!running) {
            running = true;
            new Thread(new Runnable() { @Override public void run() { loop(); } }).start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            try {
                proc = Root.stream("exec </dev/null; " + Root.BIN + " statloop");
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String l;
                while (running && (l = br.readLine()) != null) {
                    if (l.startsWith("ST|")) update(l.split("\\|", -1));
                }
            } catch (Throwable ignored) { }
            try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
        }
    }

    private static int num(String[] a, int i) {
        try { return Integer.parseInt(a[i].trim()); } catch (Exception e) { return 0; }
    }

    private String ghz(int mhz) {
        if (mhz <= 0) return "—";
        return mhz >= 1000 ? String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000f) : mhz + " MHz";
    }

    private String appName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) { return pkg; }
    }

    private static String bar(int v, int max, int n) {
        int f = Math.max(0, Math.min(n, Math.round(n * (float) v / max)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i < f ? '▰' : '▱');
        return sb.toString();
    }

    private void update(String[] a) {
        if (a.length < 10) return;
        String mode = a[1];
        boolean game = "1".equals(a[2]);
        boolean thermalOff = "1".equals(a[3]);
        String pkg = a[4];
        int cpu = num(a, 5), gpu = num(a, 6), temp = num(a, 7), bat = num(a, 8);
        boolean chg = "1".equals(a[9]);

        String name; int color; int level; String glyph;
        if ("perf".equals(mode))        { name = "PERFORMA";      color = Color.parseColor("#FF5A36"); level = 4; glyph = "P"; }
        else if ("plite".equals(mode))  { name = "PERFORMA LITE"; color = Color.parseColor("#FFB020"); level = 3; glyph = "L"; }
        else if ("eco".equals(mode))    { name = "ECO";           color = Color.parseColor("#2DD4BF"); level = 1; glyph = "E"; }
        else if ("balance".equals(mode)){ name = "SEIMBANG";      color = Color.parseColor("#3DDC84"); level = 2; glyph = "S"; }
        else                            { name = "KUSTOM";        color = Color.parseColor("#8B9BFF"); level = 2; glyph = "K"; }

        String icon = ("perf".equals(mode) ? "⚡" : "eco".equals(mode) ? "🍃" : "plite".equals(mode) ? "🔥" : "◉");
        String title = icon + " " + name + (game && ("perf".equals(mode) || "plite".equals(mode)) ? " OTOMATIS" : "") + (thermalOff ? "  ·  🔥 THERMAL OFF" : "");
        String sub1 = game ? "🎮 " + appName(pkg) + " sedang dimainkan" : "💤 Tidak ada game di depan";
        String stats = "CPU " + ghz(cpu) + "   ·   GPU " + (gpu > 0 ? gpu + " MHz" : "—");
        String heat = "🌡 " + temp + "°C " + bar(temp, 80, 8) + "   🔋 " + bat + "%" + (chg ? " ⚡" : "");
        String th = thermalOff ? "🔥 Thermal DIMATIKAN — pantau suhu!" : "❄ Thermal normal";
        String big = sub1 + "\n" + stats + "\n" + heat + "\n" + th;
        String small = (game ? appName(pkg) + " · " : "") + stats + " · " + temp + "°C";

        String key = title + "|" + big;
        if (key.equals(lastKey)) return;
        lastKey = key;

        Notification.Builder b = base(title, small, color);
        String ik = mode + "|" + thermalOff;
        if (!ik.equals(lastIconKey) || lastIcon == null) { lastIcon = gauge(glyph, level, color, thermalOff); lastIconKey = ik; }
        b.setLargeIcon(lastIcon);
        // latar gambar (res/drawable-nodpi/notif_bg.jpg) + scrim gelap + garis aksen warna mode; teks putih agar terbaca
        RemoteViews rs = new RemoteViews(getPackageName(), R.layout.notif_small);
        rs.setTextViewText(R.id.title, title);
        rs.setTextViewText(R.id.line1, small);
        rs.setInt(R.id.accent, "setBackgroundColor", color);
        RemoteViews rb = new RemoteViews(getPackageName(), R.layout.notif_big);
        rb.setTextViewText(R.id.title, title);
        rb.setTextViewText(R.id.line1, sub1);
        rb.setTextViewText(R.id.line2, stats + "   ·   🌡 " + temp + "°C   🔋 " + bat + "%" + (chg ? " ⚡" : ""));
        rb.setTextViewText(R.id.line3, th);
        rb.setInt(R.id.accent, "setBackgroundColor", color);
        rb.setImageViewBitmap(R.id.gauge, lastIcon);
        b.setStyle(new Notification.DecoratedCustomViewStyle());
        b.setCustomContentView(rs);
        b.setCustomBigContentView(rb);
        if (!"perf".equals(mode)) b.addAction(act("Performa", "perf", 1));
        if (!"balance".equals(mode)) b.addAction(act("Seimbang", "balance", 2));
        if (!"eco".equals(mode)) b.addAction(act("Eco", "eco", 3));
        try {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID, b.build());
        } catch (Throwable ignored) { }
    }

    private Notification.Action act(String label, String m, int code) {
        Intent i = new Intent(this, StatusService.class).setAction("MODE").putExtra("m", m);
        PendingIntent pi = PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), label, pi).build();
    }

    private Notification.Builder base(String title, String text, int color) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(color)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi);
        return b;
    }

    /** Ikon besar: cincin level mode (Eco 1/4 … Performa penuh) dengan huruf mode di tengah; titik api kecil bila thermal mati. */
    private Bitmap gauge(String glyph, int level, int color, boolean thermalOff) {
        int s = 128;
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, Color.parseColor("#1B2420"), Color.parseColor("#0B0F0D"), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, s, s), 34, 34, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(16, 16, s - 16, s - 16);
        p.setColor(Color.argb(60, 255, 255, 255));
        c.drawArc(r, 135, 270, false, p);
        p.setColor(color);
        c.drawArc(r, 135, 270f * level / 4f, false, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(54);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(glyph, s / 2f, s / 2f + 19, p);
        if (thermalOff) {
            p.setColor(Color.parseColor("#FF3B30"));
            c.drawCircle(s - 24, 24, 11, p);
            p.setColor(Color.WHITE);
            p.setTextSize(16);
            c.drawText("!", s - 24, 30, p);
        }
        return bm;
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
