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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Overlay in-game. Pil hitam membulat berisi FPS (putih); diketuk -> panel: Dashboard, Tweaks, Aksi, Apps.
 * Data statistik dari `ultrapower ostat` (1 baris/detik, satu proses root), status tombol dari `ultrapower ovstate`.
 */
public class OverlayService extends Service {
    private static final int GREEN = Color.parseColor("#7FE0A0");
    private static final int ON_GREEN = Color.parseColor("#0B1F14");
    private static final int PANEL_BG = Color.parseColor("#F2101412");
    private static final int CARD_BG = Color.parseColor("#FF1D2421");
    private static final int TXT = Color.parseColor("#E8EFEA");
    private static final int MUTED = Color.parseColor("#8FA096");
    private static final int WARN = Color.parseColor("#FFB74D");
    private static final int BAD = Color.parseColor("#FF6B6B");
    private static final int PAGES = 4;

    /** Tombol toggle fitur: label, id fitur (feat), indeks di baris OVS. */
    private static final String[][] TOGGLES = {
            {"FPSGO off", "fpsgooff"}, {"No Thermal", "nothermal"},
            {"Touch Boost", "touchboost"}, {"Tweak Ekstra", "perfx"},
            {"Game DND", "gamednd"}, {"Bypass Charge", "gamebypass"},
            {"Auto Clean", "gameclean"}, {"Profil Game", "gameprofile"},
            {"GMS Limiter", "gmslimit"}, {"Aggr. Doze", "aggrdoze"}};
    // urutan field OVS|mode|hz|<fpsgooff|nothermal|touchboost|gamednd|gamebypass|perfx|gmslimit|gameclean|gameprofile|aggrdoze>
    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze"};

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout pill;
    private TextView pillNum;
    private LinearLayout panel;
    private final View[] pages = new View[PAGES];
    private final TextView[] rail = new TextView[PAGES];
    private TextView hdrPkg, status, recBtn;
    private TextView vFps, vCpu, vGpu, vPing, vBat, vRam, vFreq;
    private final TextView[] modeChips = new TextView[3];
    private final TextView[] hzChips = new TextView[3];
    private final String[] modeKeys = {"perf", "balance", "eco"};
    private final int[] hzKeys = {60, 90, 120};
    private final List<TextView> toggleChips = new ArrayList<TextView>();
    private final java.util.HashMap<String, Boolean> tstate = new java.util.HashMap<String, Boolean>();
    private String curMode = "", curHz = "";
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
            // izin "tampil di atas aplikasi lain" belum ada: beri lewat root lalu coba lagi sekali
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
                .setContentText("Ketuk kotak FPS untuk membuka panel")
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private SharedPreferences prefs() { return getSharedPreferences("ov", MODE_PRIVATE); }

    private void build() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        root = new FrameLayout(this);

        // --- chip FPS: kotak membulat hitam, angka putih
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        pill.setBackground(bg(Color.parseColor("#F2000000"), 12));
        pillNum = new TextView(this);
        pillNum.setText("--");
        pillNum.setTextColor(Color.WHITE);
        pillNum.setTypeface(Typeface.DEFAULT_BOLD);
        pillNum.setTextSize(17);
        pillNum.setGravity(Gravity.CENTER);
        TextView pl = new TextView(this);
        pl.setText("FPS");
        pl.setTextColor(Color.WHITE);
        pl.setTextSize(8);
        pl.setGravity(Gravity.CENTER);
        pill.addView(pillNum, new LinearLayout.LayoutParams(-2, -2));
        pill.addView(pl, new LinearLayout.LayoutParams(-2, -2));
        pill.setMinimumWidth(dp(46));
        root.addView(pill, new FrameLayout.LayoutParams(-2, -2));

        // --- panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackground(bg(PANEL_BG, 22));
        panel.setVisibility(View.GONE);

        LinearLayout railBox = new LinearLayout(this);
        railBox.setOrientation(LinearLayout.VERTICAL);
        railBox.setPadding(dp(6), dp(10), dp(4), dp(10));
        railBox.setGravity(Gravity.CENTER_HORIZONTAL);
        String[] rn = {"Dashboard", "Tweaks", "Aksi", "Apps"};
        for (int i = 0; i < PAGES; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(rn[i]);
            t.setTextSize(10);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
            rail[i] = t;
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(70), -2);
            rp.setMargins(0, dp(2), 0, dp(2));
            railBox.addView(t, rp);
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
        hdrPkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hdrPkg.setPadding(dp(8), 0, dp(8), 0);
        hdr.addView(hdrPkg, new LinearLayout.LayoutParams(0, -2, 1f));
        hdr.addView(headBtn("—", new Runnable() { @Override public void run() { setExpanded(false); } }));
        hdr.addView(headBtn("✕", new Runnable() { @Override public void run() {
            new Thread(new Runnable() { @Override public void run() { Root.exec(Root.BIN + " overlay off"); } }).start();
            stopSelf();
        } }));
        right.addView(hdr, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        pages[0] = buildDash();
        pages[1] = buildTweaks();
        pages[2] = buildActions();
        pages[3] = buildApps();
        for (int i = 0; i < PAGES; i++) {
            // halaman yang panjang dibungkus ScrollView bertinggi tetap supaya panel tidak melewati layar
            View v = pages[i];
            if (i == 1 || i == 3) {
                ScrollView sv = new ScrollView(this);
                sv.setVerticalScrollBarEnabled(false);
                sv.addView(v);
                pages[i] = sv;
                content.addView(sv, new FrameLayout.LayoutParams(-1, dp(300)));
            } else {
                content.addView(v, new FrameLayout.LayoutParams(-1, -2));
            }
        }
        right.addView(content, new LinearLayout.LayoutParams(dp(268), -2));

        status = new TextView(this);
        status.setTextSize(11);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(3), dp(6), dp(3), 0);
        right.addView(status, sp);
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
        t.setTextSize(16);
        t.setPadding(dp(10), dp(2), dp(4), dp(2));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    private void attachDrag(View v, final boolean tapToggles) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                    return true;
                } else if (a == MotionEvent.ACTION_MOVE) {
                    float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                    if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
                    if (moved) {
                        lp.x = sx + (int) mx; lp.y = sy + (int) my;
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
                    }
                    return true;
                } else if (a == MotionEvent.ACTION_UP) {
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
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        if (on) loadState();
    }

    private void showPage(int i) {
        for (int k = 0; k < PAGES; k++) {
            pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            rail[k].setTextColor(k == i ? ON_GREEN : MUTED);
            rail[k].setBackground(k == i ? bg(GREEN, 14) : null);
        }
        if (i == 1) loadState();
        if (i == 3 && !appsLoaded) loadApps();
    }

    // ---------------- komponen ----------------
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView sec(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(10);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(8), 0, dp(2));
        return t;
    }

    private TextView chip(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(11), dp(6), dp(11));
        setChip(t, false);
        return t;
    }

    private void setChip(TextView t, boolean active) {
        t.setTextColor(active ? ON_GREEN : TXT);
        t.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setBackground(bg(active ? GREEN : CARD_BG, 14));
    }

    private void addChip(LinearLayout r, TextView c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        r.addView(c, p);
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
        v.setTextSize(big ? 28 : 16);
        c.addView(l);
        c.addView(v);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        parent.addView(c, p);
        return new TextView[]{l, v};
    }

    private View buildDash() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = row();
        vFps = card(r1, "FPS", true)[1];
        vFps.setTextColor(Color.WHITE);
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
        recBtn.setTextColor(ON_GREEN);
        recBtn.setTypeface(Typeface.DEFAULT_BOLD);
        recBtn.setTextSize(13);
        recBtn.setGravity(Gravity.CENTER);
        recBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        recBtn.setBackground(bg(GREEN, 22));
        recBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleRecord(); } });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(dp(3), dp(6), dp(3), 0);
        col.addView(recBtn, rp);
        return col;
    }

    // ---------------- Tweaks (status nyata, tombol menyala bila aktif) ----------------
    private View buildTweaks() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sec("MODE"));
        LinearLayout rm = row();
        String[] mn = {"Performa", "Seimbang", "Eco"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView c = chip(mn[i]);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                runCmd(Root.BIN + " mode " + modeKeys[k], "Mode " + modeKeys[k], new Runnable() { @Override public void run() { curMode = modeKeys[k]; paintState(); } });
            } });
            modeChips[i] = c;
            addChip(rm, c);
        }
        col.addView(rm);
        col.addView(sec("REFRESH RATE"));
        LinearLayout rh = row();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView c = chip(hzKeys[i] + " Hz");
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                runCmd(Root.BIN + " refresh " + hzKeys[k], hzKeys[k] + " Hz", new Runnable() { @Override public void run() { curHz = String.valueOf(hzKeys[k]); paintState(); } });
            } });
            hzChips[i] = c;
            addChip(rh, c);
        }
        col.addView(rh);
        col.addView(sec("FITUR (ketuk untuk nyala / mati)"));
        LinearLayout cur = null;
        for (int i = 0; i < TOGGLES.length; i++) {
            if (i % 2 == 0) { cur = row(); col.addView(cur); }
            final String id = TOGGLES[i][1];
            final String lbl = TOGGLES[i][0];
            TextView c = chip(lbl);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                final boolean now = Boolean.TRUE.equals(tstate.get(id));
                final boolean to = !now;
                runCmd(Root.BIN + " feat " + id + " " + (to ? "on" : "off"), lbl + (to ? " aktif" : " mati"),
                        new Runnable() { @Override public void run() { tstate.put(id, to); paintState(); loadState(); } });
            } });
            toggleChips.add(c);
            addChip(cur, c);
        }
        return col;
    }

    private void paintState() {
        for (int i = 0; i < 3; i++) {
            setChip(modeChips[i], modeKeys[i].equals(curMode));
            setChip(hzChips[i], String.valueOf(hzKeys[i]).equals(curHz));
        }
        for (int i = 0; i < toggleChips.size(); i++) setChip(toggleChips.get(i), Boolean.TRUE.equals(tstate.get(TOGGLES[i][1])));
    }

    private void loadState() {
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh(Root.BIN + " ovstate");
            ui.post(new Runnable() { @Override public void run() {
                for (String l : out.split("\n")) {
                    if (!l.startsWith("OVS|")) continue;
                    String[] a = l.split("\\|", -1);
                    if (a.length > 1 && !a[1].isEmpty()) curMode = a[1];
                    if (a.length > 2 && !a[2].isEmpty()) curHz = a[2];
                    for (int i = 0; i < OVS_ORDER.length && 3 + i < a.length; i++) tstate.put(OVS_ORDER[i], "on".equals(a[3 + i]));
                }
                paintState();
            } });
        } }).start();
    }

    // ---------------- Aksi ----------------
    private View buildActions() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sec("MEMORI"));
        actionRow(col, new String[][]{
                {"Bersihkan RAM", Root.BIN + " ovact clean", "RAM dibersihkan"},
                {"Stop App Latar", Root.BIN + " ovact killbg", "App latar dihentikan"}});
        actionRow(col, new String[][]{
                {"Pangkas Cache", Root.BIN + " ovact trim", "Cache dipangkas"},
                {"Screenshot", Root.BIN + " ovact shot", "Tersimpan di Pictures"}});
        col.addView(sec("MANAGER"));
        actionRow(col, new String[][]{
                {"Buka Manager", "am start --user 0 -n com.comatoze/.MainActivity", "Membuka manager"},
                {"Diagnosa FPS", "mkdir -p /sdcard/Download; " + Root.BIN + " ovdiag > /sdcard/Download/comatoze_ovdiag.txt", "Laporan: Download/comatoze_ovdiag.txt"}});
        return col;
    }

    private void actionRow(LinearLayout col, String[][] items) {
        LinearLayout r = row();
        for (final String[] it : items) {
            TextView c = chip(it[0]);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { runCmd(it[1], it[2], null); } });
            addChip(r, c);
        }
        col.addView(r);
    }

    // ---------------- Apps ----------------
    private View buildApps() {
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        return appsBox;
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
                    t.setSingleLine(true);
                    t.setPadding(dp(14), dp(12), dp(14), dp(12));
                    t.setBackground(bg(CARD_BG, 14));
                    t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        runCmd("monkey -p " + p + " -c android.intent.category.LAUNCHER 1", "Membuka", null);
                    } });
                    LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(-1, -2);
                    q.setMargins(dp(3), dp(3), dp(3), dp(3));
                    appsBox.addView(t, q);
                }
                if (n == 0) {
                    TextView t = new TextView(OverlayService.this);
                    t.setText("Belum ada game terdeteksi.\nTambahkan di tab Aplikasi pada manager.");
                    t.setTextColor(MUTED);
                    t.setTextSize(12);
                    t.setPadding(dp(8), dp(12), dp(8), dp(12));
                    appsBox.addView(t);
                }
            } });
        } }).start();
    }

    // ---------------- umpan balik ----------------
    private void flash(String s, int bgc, int fg, long ms) {
        status.setText(s);
        status.setTextColor(fg);
        status.setBackground(bg(bgc, 12));
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        status.setVisibility(View.VISIBLE);
        ui.removeCallbacks(clearStatus);
        if (ms > 0) ui.postDelayed(clearStatus, ms);
    }
    private final Runnable clearStatus = new Runnable() { @Override public void run() { if (status != null && !recording) status.setVisibility(View.GONE); } };

    private void runCmd(final String cmd, final String okMsg, final Runnable onOk) {
        flash("⏳ Menerapkan…", Color.parseColor("#3A3320"), WARN, 0);
        new Thread(new Runnable() { @Override public void run() {
            final Root.Res r = Root.exec(cmd);
            final String o = r.out().trim();
            final boolean fail = r.code != 0 && r.code != 141 || o.startsWith("ERR|");
            ui.post(new Runnable() { @Override public void run() {
                if (fail) {
                    String why = o.startsWith("ERR|") ? o.substring(4) : (r.code == 124 ? "waktu habis" : "kode " + r.code);
                    int nl = why.indexOf('\n'); if (nl > 0) why = why.substring(0, nl);
                    flash("✕ Gagal: " + why, Color.parseColor("#3A2020"), BAD, 3500);
                } else {
                    flash("✓ " + okMsg, Color.parseColor("#1E3A2A"), GREEN, 2200);
                    if (onOk != null) onOk.run();
                }
            } });
        } }).start();
    }

    // ---------------- Record ----------------
    private void toggleRecord() {
        if (!recording) {
            recording = true;
            recStart = System.currentTimeMillis();
            final String f = "/sdcard/Movies/Comatoze_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            recBtn.setText("■  Stop");
            recBtn.setTextColor(Color.WHITE);
            recBtn.setBackground(bg(BAD, 22));
            new Thread(new Runnable() { @Override public void run() {
                Root.exec("mkdir -p /sdcard/Movies; screenrecord --bit-rate 16000000 --time-limit 600 " + f, 660);
                ui.post(new Runnable() { @Override public void run() {
                    recording = false;
                    recBtn.setText("●  Record");
                    recBtn.setTextColor(ON_GREEN);
                    recBtn.setBackground(bg(GREEN, 22));
                    flash("✓ Tersimpan: Movies/" + f.substring(f.lastIndexOf('/') + 1), Color.parseColor("#1E3A2A"), GREEN, 4000);
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

        pillNum.setText(fps.isEmpty() ? "--" : fps);
        if (!expanded) return;

        vFps.setText(fps.isEmpty() ? "--" : fps);
        vCpu.setText(cpu + "%" + (ct.isEmpty() ? "" : "  " + ct + "°"));
        vGpu.setText(gpu.isEmpty() ? "n/a" : gpu + "%");
        vFreq.setText(cm + (gm.isEmpty() ? "" : " / " + gm));
        vPing.setText(ping.isEmpty() ? "--" : ping + " ms");
        vBat.setText(bat + "%  " + bt + "°");
        vRam.setText(ram + "%");
        setCol(vCpu, parse(cpu), 70, 90);
        setCol(vRam, parse(ram), 80, 92);
        hdrPkg.setText(pkg);
        if (recording) flash("● REC  " + ((System.currentTimeMillis() - recStart) / 1000) + " dtk", Color.parseColor("#3A2020"), BAD, 0);
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
