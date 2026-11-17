package com.comatoze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Overlay dalam game: pil FPS yang bisa dibuka jadi panel (Dashboard / Widgets / Apps) seperti AZenith.
 * Data datang dari `ultrapower ostat` (1 baris per detik) lewat satu proses root yang hidup selama overlay aktif.
 */
public class OverlayService extends Service {
    private static final int GREEN = Color.parseColor("#7FE0A0");
    private static final int PANEL_BG = Color.parseColor("#EE101412");
    private static final int CARD_BG = Color.parseColor("#FF1B211E");
    private static final int TXT = Color.parseColor("#E3EAE5");
    private static final int MUTED = Color.parseColor("#8FA096");
    private static final int WARN = Color.parseColor("#FFB74D");
    private static final int BAD = Color.parseColor("#FF6B6B");

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private TextView pill;
    private LinearLayout panel;
    private FrameLayout content;
    private View[] pages = new View[3];
    private TextView[] rail = new TextView[3];
    private TextView hdrPkg, status, recBtn;
    private TextView vFps, vCpu, vGpu, vPing, vBat, vRam, vFreq;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Process proc;
    private volatile boolean running;
    private boolean expanded;
    private long recStart;
    private volatile boolean recording;
    private LinearLayout appsBox;
    private boolean appsLoaded;

    @Override public IBinder onBind(Intent i) { return null; }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (root != null) return START_STICKY;
        startFg();
        try {
            build();
        } catch (Throwable t) {
            // Izin "tampil di atas aplikasi lain" belum ada: coba beri lewat root lalu ulangi sekali
            new Thread(new Runnable() { @Override public void run() {
                Root.exec(Root.BIN + " overlay perm");
                ui.post(new Runnable() { @Override public void run() {
                    try { build(); } catch (Throwable t2) { stopSelf(); }
                } });
            } }).start();
        }
        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("ov", "Overlay Comatoze", NotificationManager.IMPORTANCE_MIN);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "ov")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Overlay Comatoze aktif")
                .setContentText("Ketuk pil FPS untuk membuka panel")
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private SharedPreferences prefs() { return getSharedPreferences("ov", MODE_PRIVATE); }

    private void build() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        root = new FrameLayout(this);

        // --- pil (tertutup)
        pill = new TextView(this);
        pill.setText("-- FPS");
        pill.setTextColor(GREEN);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setTextSize(13);
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));
        pill.setBackground(bg(Color.parseColor("#CC101412"), 16));
        root.addView(pill, new FrameLayout.LayoutParams(-2, -2));

        // --- panel (terbuka)
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackground(bg(PANEL_BG, 20));
        panel.setVisibility(View.GONE);

        LinearLayout railBox = new LinearLayout(this);
        railBox.setOrientation(LinearLayout.VERTICAL);
        railBox.setPadding(dp(6), dp(10), dp(6), dp(10));
        railBox.setGravity(Gravity.CENTER_HORIZONTAL);
        String[] rn = {"Dashboard", "Widgets", "Apps"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(rn[i]);
            t.setTextSize(10);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
            rail[i] = t;
            railBox.addView(t, new LinearLayout.LayoutParams(dp(62), -2));
        }
        panel.addView(railBox, new LinearLayout.LayoutParams(-2, -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(4), dp(8), dp(10), dp(10));

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("COMATOZE");
        title.setTextColor(GREEN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(13);
        hdr.addView(title, new LinearLayout.LayoutParams(-2, -2));
        hdrPkg = new TextView(this);
        hdrPkg.setTextColor(MUTED);
        hdrPkg.setTextSize(10);
        hdrPkg.setSingleLine(true);
        hdrPkg.setPadding(dp(8), 0, dp(8), 0);
        hdr.addView(hdrPkg, new LinearLayout.LayoutParams(0, -2, 1f));
        hdr.addView(headBtn("—", new Runnable() { @Override public void run() { setExpanded(false); } }));
        hdr.addView(headBtn("✕", new Runnable() { @Override public void run() { Root.exec(Root.BIN + " overlay off"); stopSelf(); } }));
        right.addView(hdr, new LinearLayout.LayoutParams(-1, -2));

        content = new FrameLayout(this);
        pages[0] = buildDash();
        pages[1] = buildWidgets();
        pages[2] = buildApps();
        for (View p : pages) content.addView(p, new FrameLayout.LayoutParams(-1, p == pages[2] ? dp(250) : -2));
        right.addView(content, new LinearLayout.LayoutParams(dp(236), -2));

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(10);
        status.setPadding(dp(2), dp(4), 0, 0);
        right.addView(status, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(right, new LinearLayout.LayoutParams(-2, -2));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        showPage(0);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs().getInt("x", dp(8));
        lp.y = prefs().getInt("y", dp(80));
        wm.addView(root, lp);
        attachDrag(pill, true);
        attachDrag(hdr, false);

        startStats();
    }

    private TextView headBtn(String s, final Runnable r) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TXT);
        t.setTextSize(15);
        t.setPadding(dp(10), dp(2), dp(4), dp(2));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    private void attachDrag(View v, final boolean tapToggles) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
                        if (moved) { lp.x = sx + (int) mx; lp.y = sy + (int) my; try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { } }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) prefs().edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        else if (tapToggles) setExpanded(true);
                        return true;
                }
                return false;
            }
        });
    }

    private void setExpanded(boolean on) {
        expanded = on;
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        pill.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) {
            // izinkan fokus ketik tidak diperlukan; hanya perbarui ukuran
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        }
    }

    private void showPage(int i) {
        for (int k = 0; k < 3; k++) {
            pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            rail[k].setTextColor(k == i ? Color.parseColor("#0B1F14") : MUTED);
            rail[k].setBackground(k == i ? bg(GREEN, 14) : null);
        }
        if (i == 2 && !appsLoaded) loadApps();
    }

    // ---------------- Dashboard ----------------
    private TextView[] card(LinearLayout parent, String label, boolean big) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(8), dp(10), dp(8));
        c.setBackground(bg(CARD_BG, 14));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(10);
        TextView v = new TextView(this);
        v.setText("--");
        v.setTextColor(TXT);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextSize(big ? 26 : 17);
        c.addView(l);
        c.addView(v);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        parent.addView(c, p);
        return new TextView[]{l, v};
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private View buildDash() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = row();
        vFps = card(r1, "FPS", true)[1];
        vFps.setTextColor(GREEN);
        vFreq = card(r1, "CPU / GPU MHz", false)[1];
        vFreq.setTextSize(13);
        col.addView(r1);
        LinearLayout r2 = row();
        vCpu = card(r2, "CPU", false)[1];
        vGpu = card(r2, "GPU", false)[1];
        col.addView(r2);
        LinearLayout r3 = row();
        vPing = card(r3, "Latensi", false)[1];
        vBat = card(r3, "Baterai", false)[1];
        col.addView(r3);
        LinearLayout r4 = row();
        vRam = card(r4, "RAM", false)[1];
        col.addView(r4);
        recBtn = new TextView(this);
        recBtn.setText("●  Record");
        recBtn.setTextColor(Color.parseColor("#0B1F14"));
        recBtn.setTypeface(Typeface.DEFAULT_BOLD);
        recBtn.setTextSize(13);
        recBtn.setGravity(Gravity.CENTER);
        recBtn.setPadding(dp(8), dp(10), dp(8), dp(10));
        recBtn.setBackground(bg(GREEN, 22));
        recBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleRecord(); } });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(dp(3), dp(6), dp(3), 0);
        col.addView(recBtn, rp);
        return col;
    }

    // ---------------- Widgets ----------------
    private TextView chip(String label, final String cmd, final String ok) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TXT);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(10), dp(6), dp(10));
        t.setBackground(bg(CARD_BG, 14));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { runCmd(cmd, ok); } });
        return t;
    }

    private void chipRow(LinearLayout col, String[][] items) {
        LinearLayout r = row();
        for (String[] it : items) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(chip(it[0], it[1], it[2]), p);
        }
        col.addView(r);
    }

    private TextView sec(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(10);
        t.setPadding(dp(4), dp(6), 0, dp(2));
        return t;
    }

    private View buildWidgets() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sec("MODE"));
        chipRow(col, new String[][]{
                {"Performa", Root.BIN + " mode perf", "Mode Performa"},
                {"Seimbang", Root.BIN + " mode balance", "Mode Seimbang"},
                {"Eco", Root.BIN + " mode eco", "Mode Eco"}});
        col.addView(sec("REFRESH RATE"));
        chipRow(col, new String[][]{
                {"60 Hz", Root.BIN + " refresh 60", "60 Hz"},
                {"90 Hz", Root.BIN + " refresh 90", "90 Hz"},
                {"120 Hz", Root.BIN + " refresh 120", "120 Hz"}});
        col.addView(sec("AKSI CEPAT"));
        chipRow(col, new String[][]{
                {"Bersihkan RAM", "am kill-all; sync; echo 3 > /proc/sys/vm/drop_caches", "RAM dibersihkan"},
                {"FPSGO off", Root.BIN + " feat fpsgooff on", "FPSGO dimatikan"},
                {"FPSGO on", Root.BIN + " feat fpsgooff off", "FPSGO normal"}});
        return col;
    }

    // ---------------- Apps (daftar game) ----------------
    private View buildApps() {
        ScrollView sv = new ScrollView(this);
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(appsBox);
        return sv;
    }

    private void loadApps() {
        appsLoaded = true;
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh("cat /data/adb/ultrapower/games.txt 2>/dev/null");
            ui.post(new Runnable() { @Override public void run() {
                appsBox.removeAllViews();
                int n = 0;
                for (String l : out.split("\n")) {
                    final String p = l.trim();
                    if (!p.matches("[A-Za-z0-9_.]+")) continue;
                    n++;
                    TextView t = new TextView(OverlayService.this);
                    String lbl = p;
                    try { lbl = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0))); } catch (Exception ignored) { }
                    t.setText(lbl);
                    t.setTextColor(TXT);
                    t.setTextSize(13);
                    t.setPadding(dp(12), dp(10), dp(12), dp(10));
                    t.setBackground(bg(CARD_BG, 12));
                    t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        runCmd("monkey -p " + p + " -c android.intent.category.LAUNCHER 1", "Membuka");
                    } });
                    LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
                    lp2.setMargins(dp(3), dp(3), dp(3), dp(3));
                    appsBox.addView(t, lp2);
                }
                if (n == 0) {
                    TextView t = new TextView(OverlayService.this);
                    t.setText("Belum ada game terdeteksi.\nBuka tab Aplikasi di manager.");
                    t.setTextColor(MUTED);
                    t.setTextSize(12);
                    t.setPadding(dp(8), dp(12), dp(8), dp(12));
                    appsBox.addView(t);
                }
            } });
        } }).start();
    }

    private void toast(String s) {
        status.setText(s);
        ui.removeCallbacks(clearStatus);
        ui.postDelayed(clearStatus, 3000);
    }
    private final Runnable clearStatus = new Runnable() { @Override public void run() { if (status != null && !recording) status.setText(""); } };

    private void runCmd(final String cmd, final String okMsg) {
        toast("…");
        new Thread(new Runnable() { @Override public void run() {
            final Root.Res r = Root.exec(cmd);
            ui.post(new Runnable() { @Override public void run() { toast(r.code == 0 ? okMsg : "Gagal (" + r.code + ")"); } });
        } }).start();
    }

    // ---------------- Record ----------------
    private void toggleRecord() {
        if (!recording) {
            recording = true;
            recStart = System.currentTimeMillis();
            final String f = "/sdcard/Movies/Comatoze_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            recBtn.setText("■  Stop");
            recBtn.setBackground(bg(BAD, 22));
            new Thread(new Runnable() { @Override public void run() {
                Root.exec("mkdir -p /sdcard/Movies; screenrecord --bit-rate 16000000 --time-limit 600 " + f);
                ui.post(new Runnable() { @Override public void run() {
                    recording = false;
                    recBtn.setText("●  Record");
                    recBtn.setBackground(bg(GREEN, 22));
                    toast("Tersimpan: Movies/" + f.substring(f.lastIndexOf('/') + 1));
                } });
            } }).start();
        } else {
            new Thread(new Runnable() { @Override public void run() { Root.exec("pkill -2 screenrecord"); } }).start();
        }
    }

    // ---------------- Data ----------------
    private void startStats() {
        running = true;
        new Thread(new Runnable() { @Override public void run() {
            while (running) {
                try {
                    proc = Root.stream("exec " + Root.BIN + " ostat");
                    BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String l;
                    while (running && (l = br.readLine()) != null) {
                        if (l.startsWith("OV|")) {
                            final String line = l;
                            ui.post(new Runnable() { @Override public void run() { onStat(line); } });
                        }
                    }
                } catch (Throwable ignored) { }
                if (running) { try { Thread.sleep(3000); } catch (InterruptedException ignored) { } }
            }
        } }).start();
    }

    private static String f(String[] a, int i) { return i < a.length ? a[i] : ""; }

    private void onStat(String line) {
        if (root == null) return;
        // OV|fps|cpu|gpu|bat|batTemp|ram|cpuMHz|gpuMHz|ping|pkg|cpuTemp
        String[] a = line.split("\\|", -1);
        String fps = f(a, 1), cpu = f(a, 2), gpu = f(a, 3), bat = f(a, 4), bt = f(a, 5), ram = f(a, 6);
        String cm = f(a, 7), gm = f(a, 8), ping = f(a, 9), pkg = f(a, 10), ct = f(a, 11);

        pill.setText((fps.isEmpty() ? "--" : fps) + " FPS");
        if (!expanded) return;

        vFps.setText(fps.isEmpty() ? "--" : fps);
        int fv = fps.isEmpty() ? 0 : parse(fps);
        vFps.setTextColor(fv >= 55 ? GREEN : fv >= 30 ? WARN : (fps.isEmpty() ? MUTED : BAD));
        vCpu.setText(cpu + "%" + (ct.isEmpty() ? "" : "  " + ct + "°"));
        vGpu.setText(gpu.isEmpty() ? "n/a" : gpu + "%");
        vFreq.setText(cm + (gm.isEmpty() ? "" : " / " + gm));
        vPing.setText(ping.isEmpty() ? "--" : ping + " ms");
        vBat.setText(bat + "%  " + bt + "°");
        vRam.setText(ram + "%");
        setCol(vCpu, parse(cpu), 70, 90);
        setCol(vRam, parse(ram), 80, 92);
        hdrPkg.setText(pkg);
        if (recording) status.setText("● REC  " + ((System.currentTimeMillis() - recStart) / 1000) + " dtk");
    }

    private void setCol(TextView t, int v, int warn, int bad) {
        t.setTextColor(v >= bad ? BAD : v >= warn ? WARN : TXT);
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (proc != null) proc.destroy();
        if (root != null) { try { wm.removeView(root); } catch (Exception ignored) { } root = null; }
        super.onDestroy();
    }
}
