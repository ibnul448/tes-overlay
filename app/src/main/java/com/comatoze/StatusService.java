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
            startForeground(ID, base("Comatoze", "Menghubungkan ke modul…").build());
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

        String name;
        if ("perf".equals(mode)) name = "Performa";
        else if ("plite".equals(mode)) name = "Performa Lite";
        else if ("eco".equals(mode)) name = "Eco";
        else if ("balance".equals(mode)) name = "Seimbang";
        else name = "Kustom";
        boolean auto = game && ("perf".equals(mode) || "plite".equals(mode));

        String title = "Mode " + name + (auto ? " (otomatis)" : "");
        String text = "Thermal " + (thermalOff ? "dimatikan" : "normal") + "  \u00B7  " + temp + "\u00B0C  \u00B7  Baterai " + bat + "%" + (chg ? " (mengisi)" : "");

        String key = title + "|" + text;
        if (key.equals(lastKey)) return;
        lastKey = key;

        Notification.Builder b = base(title, text);
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

    private Notification.Builder base(String title, String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi);
        return b;
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
