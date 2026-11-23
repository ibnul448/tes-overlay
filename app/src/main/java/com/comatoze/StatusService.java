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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
    private final Root.Shell shell = new Root.Shell();
    private volatile String[] lastArr;
    private volatile String pendingMode;
    private volatile long pendingUntil;
    private static final String CH_REC = "rekam";
    private static final int ID_REC = 8;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean recShown = false;
    private boolean blinkOn = true;
    private long recStartMs;
    private String recName = "";
    private int recFps;
    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (!recShown) return;
            blinkOn = !blinkOn;
            postRec();
            ui.postDelayed(this, 1000);
        }
    };
    private volatile int pendingRec = -1;   // -1 tidak ada, 0/1 = status rekam yang diminta

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent != null ? intent.getAction() : null;
        boolean isAct = "MODE".equals(act) || "REC".equals(act);
        if (!(isAct && running)) {   // ketukan tombol saat layanan sudah jalan: JANGAN pasang ulang notifikasi "Menghubungkan" (itu penyebab patah-patah)
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(CH, "Status Comatoze", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            c.setSound(null, null);
            c.enableVibration(false);
            nm.createNotificationChannel(c);
            NotificationChannel cr = new NotificationChannel(CH_REC, "Perekaman FPS", NotificationManager.IMPORTANCE_LOW);
            cr.setShowBadge(false);
            cr.setSound(null, null);
            cr.enableVibration(false);
            nm.createNotificationChannel(cr);
            try {
                startForeground(ID, base("Comatoze", "Menghubungkan ke modul…").build());
            } catch (Throwable t) { stopSelf(); return START_NOT_STICKY; }
        }

        if ("MODE".equals(act)) {
            final String m = intent.getStringExtra("m");
            if (m != null && m.matches("perf|plite|balance|eco")) {
                pendingMode = m;                                   // tampilkan mode baru SEKETIKA, tanpa menunggu skrip selesai
                pendingUntil = System.currentTimeMillis() + 6000;
                String[] cur = lastArr;
                if (cur != null) { String[] c2 = cur.clone(); c2[1] = m; render(c2); }
                shell.send(Root.BIN + " mode " + m + " </dev/null >/dev/null 2>&1");
            }
        } else if ("REC".equals(act)) {   // Berhenti: akhiri sesi rekam ini saja (disimpan); rekaman berikutnya mulai lagi saat game dibuka
            pendingRec = 1;
            pendingUntil = System.currentTimeMillis() + 6000;
            String[] cur = lastArr;
            if (cur != null) { String[] c2 = cur.clone(); if (c2.length > 10) c2[10] = "1"; render(c2); }
            shell.send(Root.BIN + " rec stop </dev/null >/dev/null 2>&1");
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
        lastArr = a.clone();
        long now = System.currentTimeMillis();
        if (pendingMode != null) {
            if (a[1].equals(pendingMode) || now > pendingUntil) pendingMode = null;
            else a[1] = pendingMode;
        }
        if (pendingRec >= 0 && a.length > 10) {
            int real = num(a, 10);
            if (real == pendingRec || now > pendingUntil) pendingRec = -1;
            else a[10] = String.valueOf(pendingRec);
        }
        render(a);
    }

    private void render(String[] a) {
        if (a.length < 10) return;
        String mode = a[1];
        boolean game = "1".equals(a[2]);
        boolean thermalOff = "1".equals(a[3]);
        String pkg = a[4];
        int cpu = num(a, 5), gpu = num(a, 6), temp = num(a, 7), bat = num(a, 8);
        boolean chg = "1".equals(a[9]);
        int rs = a.length > 10 ? num(a, 10) : 1;   // 0 = perekam FPS dimatikan, 1 = siaga, 2 = sedang merekam
        int fps = a.length > 11 ? num(a, 11) : 0;
        long recStart = 0;
        try { recStart = a.length > 12 ? Long.parseLong(a[12].trim()) * 1000L : 0; } catch (Exception e) { recStart = 0; }
        updateRec(rs == 2, recStart, pkg, fps);

        String name;
        if ("perf".equals(mode)) name = "Performa";
        else if ("plite".equals(mode)) name = "Performa Lite";
        else if ("eco".equals(mode)) name = "Eco";
        else if ("balance".equals(mode)) name = "Seimbang";
        else name = "Kustom";
        boolean auto = game && ("perf".equals(mode) || "plite".equals(mode));

        boolean limited = a.length > 13 && "1".equals(a[13]);   // penjaga suhu sedang membatasi Performa
        String title = "Mode " + name + (limited ? " (dibatasi suhu)" : auto ? " (otomatis)" : "");
        String text = "Thermal " + (thermalOff ? "dimatikan" : "normal") + "  \u00B7  " + temp + "\u00B0C  \u00B7  Baterai " + bat + "%" + (chg ? " (mengisi)" : "");

        String key = title + "|" + text + "|" + rs;
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

    /** Notifikasi terpisah ala perekam layar: hanya ada saat FPS sedang direkam; ada penghitung waktu, titik merah berkedip, dan tombol Berhenti. */
    private void updateRec(boolean on, long startMs, String pkg, int fps) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!on) {
            if (recShown) { recShown = false; ui.removeCallbacks(blink); try { nm.cancel(ID_REC); } catch (Throwable ignored) { } }
            return;
        }
        recStartMs = startMs > 0 ? startMs : (recStartMs > 0 ? recStartMs : System.currentTimeMillis());
        recName = appName(pkg);
        recFps = fps;
        if (!recShown) {
            recShown = true;
            blinkOn = true;
            ui.removeCallbacks(blink);
            ui.postDelayed(blink, 1000);
        }
        postRec();
    }

    private void postRec() {
        try {
            SpannableString title = new SpannableString("\u25CF  Merekam FPS");
            title.setSpan(new ForegroundColorSpan(blinkOn ? Color.rgb(255, 59, 48) : Color.TRANSPARENT), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent si = new Intent(this, StatusService.class).setAction("REC").putExtra("r", "off");
            PendingIntent sp = PendingIntent.getService(this, 4, si, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = new Notification.Builder(this, CH_REC)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(recName + (recFps > 0 ? "  \u00B7  " + recFps + " FPS" : ""))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(true)
                    .setWhen(recStartMs)
                    .setUsesChronometer(true)
                    .setColor(Color.rgb(255, 59, 48))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(pi)
                    .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), "Berhenti", sp).build());
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID_REC, b.build());
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
        shell.close();
        ui.removeCallbacks(blink);
        try { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID_REC); } catch (Throwable ignored) { }
        try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
