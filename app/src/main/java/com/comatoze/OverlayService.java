package com.comatoze;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Overlay in-game: dock kiri (halaman + app jendela mengambang), dashboard dengan gauge CPU/GPU, FPS, mode
 * (Default/Eco/Balance/Performa) dan aksi cepat; halaman Tweaks dikelompokkan menurut kegunaan.
 * Seluruh ikon vektor, tanpa emoji. Data: `ultrapower ostat`; status tombol: `ultrapower ovstate`.
 */
public class OverlayService extends Service {
    private static final int ECO_C = Color.parseColor("#4CC38A");
    private static final int BAL_C = Color.parseColor("#4A90FF");
    private static final int PERF_C = Color.parseColor("#F0524F");
    private static final int PANEL = Color.parseColor("#F2141518");
    private static final int CARD = Color.parseColor("#1F2126");
    private static final int CARD2 = Color.parseColor("#2A2D34");
    private static final int DOCK = Color.parseColor("#0E0F12");
    private static final int LINE = Color.parseColor("#33FFFFFF");
    private static final int TXT = Color.parseColor("#ECEFF4");
    private static final int MUTED = Color.parseColor("#8E95A3");
    private static final int WARN = Color.parseColor("#F2B24C");
    private static final int BAD = Color.parseColor("#F0524F");
    private static final int GOOD = Color.parseColor("#4CC38A");
    private static final int PAGES = 3;          // 0 dashboard, 1 tweaks, 2 pemilih app
    private static final int DOCK_W = 56, MAIN_W = 296, CONTENT_H = 372;

    // ikon, label, id fitur; baris dengan id null = judul grup
    private static final Object[][] TWEAKS = {
            {0, "PERFORMA", null},
            {IconView.TARGET, "FPSGO Off", "fpsgooff"}, {IconView.THERMO, "No Thermal", "nothermal"},
            {IconView.TOUCH, "Touch Boost", "touchboost"}, {IconView.BOLT, "Tweak Ekstra", "perfx"},
            {IconView.HZ, "Kunci Hz", "hzforce"},
            {0, "GAME", null},
            {IconView.DND, "Game DND", "gamednd"}, {IconView.TRASH, "Auto Clean", "gameclean"},
            {IconView.PAD, "Profil Game", "gameprofile"}, {IconView.BATTERY, "Bypass Charge", "gamebypass"},
            {0, "SISTEM & JARINGAN", null},
            {IconView.CLOUD, "GMS Limiter", "gmslimit"}, {IconView.MOON, "Aggr. Doze", "aggrdoze"},
            {IconView.RAM, "Data Saver", "datasaver"}, {IconView.PULSE, "WiFi Latency", "wifilatency"},
            {IconView.CHIP, "Less Debug", "lessdbg"}};
    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd", "wifilatency", "hzforce", "lessdbg", "datasaver", "def"};
    private static final String[] MODE_KEYS = {"default", "eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"Default", "Eco", "Balance", "Performa"};
    private static final int[] HZ = {60, 90, 120};

    private static final class Tile {
        String id; LinearLayout box; IconView ic; FrameLayout track; View knob;
    }

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout pill, panel;
    private GradientDrawable pillBg;
    private TextView pillNum;
    private final View[] pages = new View[PAGES];
    private final FrameLayout[] dockBtn = new FrameLayout[2];
    private final IconView[] dockIc = new IconView[2];
    private LinearLayout dockApps;
    private TextView status, logo, badge, fpsTv, fpsSub, pkgTv, hzLbl, recLbl, statHint;
    private final RingGauge[] gauge = new RingGauge[2];
    private final TextView[] stat = new TextView[4];
    private final TextView[] modeTx = new TextView[4];
    private final FrameLayout[] quickBtn = new FrameLayout[5];
    private final IconView[] quickIc = new IconView[5];
    private final ArrayList<Tile> tiles = new ArrayList<Tile>();
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private LinearLayout pickerBox;
    private boolean pickerLoaded;
    private String curMode = "", curHz = "", curPkg = "";
    private boolean curDef;
    private int accent = BAL_C;
    private int page = 0;
    private float sc = 1f, density = 1f;
    private int scrW, scrH;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Process proc;
    private volatile boolean running;
    private boolean expanded;
    private long recStart;
    private volatile boolean recording;
    private int tick = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------- util ----------
    private int dp(float v) { return (int) (v * density * sc + 0.5f); }
    private static int alpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }
    private static int accentFor(String m) { return "perf".equals(m) ? PERF_C : "eco".equals(m) ? ECO_C : BAL_C; }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size * sc);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable rounded(int fill, float radius, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private GradientDrawable circle(int fill, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lpw(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private SharedPreferences prefs() { return getSharedPreferences("ov", MODE_PRIVATE); }

    // ---------- siklus hidup ----------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (root != null) return START_STICKY;
        startFg();
        try {
            build();
        } catch (Throwable t) {
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
        nm.createNotificationChannel(new NotificationChannel("ov", "Overlay Comatoze", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "ov")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Overlay Comatoze aktif")
                .setContentText("Ketuk kotak FPS untuk membuka panel")
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    // ---------- bangun UI ----------
    private void build() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        density = getResources().getDisplayMetrics().density;
        scrW = getResources().getDisplayMetrics().widthPixels;
        scrH = getResources().getDisplayMetrics().heightPixels;
        float wDp = scrW / density, hDp = scrH / density;
        float needW = DOCK_W + MAIN_W + 28, needH = CONTENT_H + 56;
        sc = Math.max(0.55f, Math.min(1f, Math.min((wDp - 16) / needW, (hDp - 24) / needH)));

        root = new FrameLayout(this);

        // chip FPS: kotak membulat hitam, angka putih
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        pillBg = rounded(Color.parseColor("#F2000000"), 12, alpha(accent, 150), 1);
        pill.setBackground(pillBg);
        pillNum = tv("--", 17, Color.WHITE, true);
        pillNum.setGravity(Gravity.CENTER);
        TextView pl = tv("FPS", 8, Color.WHITE, false);
        pl.setGravity(Gravity.CENTER);
        pill.addView(pillNum, new LinearLayout.LayoutParams(-2, -2));
        pill.addView(pl, new LinearLayout.LayoutParams(-2, -2));
        pill.setMinimumWidth(dp(46));
        root.addView(pill, new FrameLayout.LayoutParams(-2, -2));

        // panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackground(rounded(PANEL, 22, LINE, 1));
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setVisibility(View.GONE);
        panel.addView(buildDock(), new LinearLayout.LayoutParams(dp(DOCK_W - 8), dp(CONTENT_H + 12)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(10), dp(4), dp(4), 0);
        FrameLayout content = new FrameLayout(this);
        pages[0] = buildDash();
        ScrollView sv1 = new ScrollView(this);
        sv1.setVerticalScrollBarEnabled(false);
        sv1.addView(buildTweaks());
        pages[1] = sv1;
        ScrollView sv2 = new ScrollView(this);
        sv2.setVerticalScrollBarEnabled(false);
        pickerBox = new LinearLayout(this);
        pickerBox.setOrientation(LinearLayout.VERTICAL);
        sv2.addView(pickerBox);
        pages[2] = sv2;
        for (int i = 0; i < PAGES; i++) content.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
        right.addView(content, new LinearLayout.LayoutParams(dp(MAIN_W), dp(CONTENT_H)));
        status = tv("", 10, TXT, true);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        right.addView(status, lpw(-1, -2, 0, 6, 0, 0));
        panel.addView(right, new LinearLayout.LayoutParams(-2, -2));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        showPage(0);
        applyAccent(accent);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs().getInt("x", dp(8));
        lp.y = prefs().getInt("y", dp(80));
        wm.addView(root, lp);
        attachDrag(pill, true);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE && expanded) { setExpanded(false); return true; }
                return false;
            }
        });
        startStats();
    }

    // ---------- Dock kiri ----------
    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER_HORIZONTAL);
        dock.setPadding(dp(4), dp(8), dp(4), dp(8));
        dock.setBackground(rounded(DOCK, 18, 0, 0));
        int[] ic = {IconView.GAUGE, IconView.SLIDERS};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            FrameLayout b = new FrameLayout(this);
            b.setPadding(dp(9), dp(9), dp(9), dp(9));
            IconView iv = new IconView(this, ic[i]);
            b.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
            dockBtn[i] = b; dockIc[i] = iv;
            dock.addView(b, lpw(dp(40), dp(40), 0, 2, 0, 2));
        }
        View sep = new View(this);
        sep.setBackgroundColor(LINE);
        dock.addView(sep, lpw(dp(24), Math.max(1, dp(1)), 0, 6, 0, 6));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        dockApps = new LinearLayout(this);
        dockApps.setOrientation(LinearLayout.VERTICAL);
        dockApps.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(dockApps, new FrameLayout.LayoutParams(-1, -2));
        dock.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        refreshDockApps();

        FrameLayout add = new FrameLayout(this);
        add.setPadding(dp(10), dp(10), dp(10), dp(10));
        add.setBackground(circle(CARD2, 0, 0));
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setColor(TXT);
        add.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(2); } });
        dock.addView(add, lpw(dp(40), dp(40), 0, 6, 0, 0));
        attachDrag(dock, false);
        return dock;
    }

    private ArrayList<String> floatList() {
        ArrayList<String> l = new ArrayList<String>();
        String s = prefs().getString("fl", "");
        for (String p : s.split(",")) if (p.matches("[A-Za-z0-9_.]+")) l.add(p);
        return l;
    }

    private void saveFloat(ArrayList<String> l) { prefs().edit().putString("fl", TextUtils.join(",", l)).apply(); }

    private void refreshDockApps() {
        dockApps.removeAllViews();
        PackageManager pm = getPackageManager();
        for (final String p : floatList()) {
            ImageView iv = new ImageView(this);
            try { iv.setImageDrawable(pm.getApplicationIcon(p)); } catch (Exception e) { continue; }
            iv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                pulse(v);
                runCmd(Root.BIN + " ovfloat " + p, "JENDELA MENGAMBANG", new Runnable() { @Override public void run() { setExpanded(false); } });
            } });
            iv.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                ArrayList<String> l = floatList(); l.remove(p); saveFloat(l);
                refreshDockApps(); pickerLoaded = false; flash("APP DIHAPUS DARI DOCK", MUTED, 1800);
                return true;
            } });
            dockApps.addView(iv, lpw(dp(38), dp(38), 0, 4, 0, 4));
        }
    }

    // ---------- Dashboard ----------
    private View buildDash() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout lc = new LinearLayout(this);
        lc.setOrientation(LinearLayout.VERTICAL);
        logo = tv("COMATOZE", 14, Color.WHITE, true);
        logo.setLetterSpacing(0.12f);
        lc.addView(logo);
        pkgTv = tv("", 9, MUTED, false);
        pkgTv.setSingleLine(true);
        pkgTv.setEllipsize(TextUtils.TruncateAt.END);
        lc.addView(pkgTv, lpw(-2, -2, 0, 2, 0, 0));
        top.addView(lc, new LinearLayout.LayoutParams(0, -2, 1f));
        badge = tv("Balance", 10, Color.WHITE, true);
        badge.setPadding(dp(12), dp(5), dp(12), dp(5));
        top.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        IconView mn = new IconView(this, IconView.MINUS);
        mn.setColor(MUTED);
        mn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(false); } });
        top.addView(mn, lpw(dp(30), dp(30), 4, 0, 0, 0));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        attachDrag(top, false);

        // gauge CPU & GPU
        LinearLayout gr = new LinearLayout(this);
        gr.setOrientation(LinearLayout.HORIZONTAL);
        gr.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            gauge[i] = new RingGauge(this);
            gr.addView(gauge[i], lpw(0, dp(104), i == 0 ? 0 : 6, 0, i == 0 ? 6 : 0, 0));
            ((LinearLayout.LayoutParams) gauge[i].getLayoutParams()).weight = 1f;
        }
        col.addView(gr, lpw(-1, -2, 0, 8, 0, 0));

        // FPS
        LinearLayout fp = new LinearLayout(this);
        fp.setOrientation(LinearLayout.HORIZONTAL);
        fp.setGravity(Gravity.CENTER);
        fp.setBackground(rounded(CARD, 20, 0, 0));
        fp.setPadding(dp(12), dp(8), dp(12), dp(8));
        fpsTv = tv("--", 20, Color.WHITE, true);
        fp.addView(fpsTv);
        fpsSub = tv("FPS  •  -- Hz", 11, MUTED, true);
        fp.addView(fpsSub, lpw(-2, -2, 8, 0, 0, 0));
        col.addView(fp, lpw(-1, -2, 0, 8, 0, 0));

        // statistik kecil
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        String[] sl = {"RAM", "SUHU", "PING", "BATERAI"};
        for (int i = 0; i < 4; i++) {
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(4), dp(5), dp(4), dp(5));
            b.setBackground(rounded(CARD, 12, 0, 0));
            b.addView(tv(sl[i], 7.5f, MUTED, false));
            stat[i] = tv("--", 11, Color.WHITE, true);
            b.addView(stat[i], lpw(-2, -2, 0, 2, 0, 0));
            LinearLayout.LayoutParams bp = lpw(0, -2, i == 0 ? 0 : 5, 0, 0, 0);
            bp.weight = 1f;
            sr.addView(b, bp);
        }
        col.addView(sr, lpw(-1, -2, 0, 8, 0, 0));

        // mode (kontrol segmen)
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(rounded(DOCK, 20, 0, 0));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            TextView t = tv(MODE_NAMES[i], 10, MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(2), dp(8), dp(2), dp(8));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeTx[i] = t;
            LinearLayout.LayoutParams tp = lpw(0, -2, i == 0 ? 0 : 2, 0, 0, 0);
            tp.weight = 1f;
            seg.addView(t, tp);
        }
        col.addView(seg, lpw(-1, -2, 0, 8, 0, 0));

        // aksi cepat
        LinearLayout qr = new LinearLayout(this);
        qr.setOrientation(LinearLayout.HORIZONTAL);
        int[] qi = {IconView.TRASH, IconView.RAM, IconView.DND, IconView.HZ, IconView.REC};
        String[] ql = {"Clean", "RAM", "DND", "-- Hz", "Rekam"};
        for (int i = 0; i < 5; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout b = new FrameLayout(this);
            b.setPadding(dp(11), dp(11), dp(11), dp(11));
            quickIc[i] = new IconView(this, qi[i]);
            b.addView(quickIc[i], new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { quick(k, v); } });
            quickBtn[i] = b;
            cell.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView l = tv(ql[i], 9, MUTED, false);
            l.setGravity(Gravity.CENTER);
            cell.addView(l, lpw(-2, -2, 0, 3, 0, 0));
            if (i == 3) hzLbl = l;
            if (i == 4) recLbl = l;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            qr.addView(cell, cp);
        }
        col.addView(qr, lpw(-1, -2, 0, 8, 0, 0));
        return col;
    }

    // ---------- Tweaks ----------
    private TextView head(String s) {
        TextView t = tv(s, 9, MUTED, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(2), dp(4), 0, dp(6));
        return t;
    }

    private View buildTweaks() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int n = 0;
        for (int i = 0; i < TWEAKS.length; i++) {
            if (TWEAKS[i][2] == null) {
                TextView h = head((String) TWEAKS[i][1]);
                col.addView(h, lpw(-1, -2, 0, i == 0 ? 0 : 8, 0, 0));
                row = null; n = 0;
                continue;
            }
            if (n % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, lpw(-1, -2, 0, 0, 0, 6));
            }
            n++;
            final String id = (String) TWEAKS[i][2];
            final String lbl = (String) TWEAKS[i][1];
            final Tile t = new Tile();
            t.id = id;
            t.box = new LinearLayout(this);
            t.box.setOrientation(LinearLayout.VERTICAL);
            t.box.setPadding(dp(10), dp(9), dp(10), dp(9));
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            t.ic = new IconView(this, (Integer) TWEAKS[i][0]);
            tr.addView(t.ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
            tr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            t.track = new FrameLayout(this);
            t.knob = new View(this);
            t.track.addView(t.knob, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER_VERTICAL));
            tr.addView(t.track, new LinearLayout.LayoutParams(dp(30), dp(16)));
            t.box.addView(tr, new LinearLayout.LayoutParams(-1, -2));
            TextView name = tv(lbl, 11, Color.WHITE, true);
            name.setSingleLine(true);
            t.box.addView(name, lpw(-2, -2, 0, 8, 0, 0));
            t.box.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); toggleFeat(id, lbl); } });
            tiles.add(t);
            LinearLayout.LayoutParams tp = lpw(0, -2, n % 2 == 1 ? 0 : 6, 0, 0, 0);
            tp.weight = 1f;
            row.addView(t.box, tp);
            if (i + 1 >= TWEAKS.length || TWEAKS[i + 1][2] == null) {
                if (n % 2 == 1) { LinearLayout.LayoutParams fp = lpw(0, 1, 6, 0, 0, 0); fp.weight = 1f; row.addView(new View(this), fp); }
            }
        }
        // tombol tambah game (grup GAME)
        TextView add = tv("+  Tambah game yang sedang dibuka", 10, TXT, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(8), dp(10), dp(8), dp(10));
        add.setBackground(rounded(CARD, 12, LINE, 1));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); addCurrentGame(); } });
        col.addView(add, lpw(-1, -2, 0, 4, 0, 6));
        return col;
    }

    // ---------- Pemilih app (untuk dock jendela mengambang) ----------
    private void loadPicker() {
        pickerLoaded = true;
        pickerBox.removeAllViews();
        pickerBox.addView(head("TAMBAH APP KE DOCK  ·  jendela mengambang"));
        TextView hint = tv("Ketuk app untuk menambah/menghapus. Ketuk ikon di dock untuk membukanya sebagai jendela mengambang. Tekan lama ikon dock untuk menghapus.", 9, MUTED, false);
        hint.setPadding(dp(2), 0, dp(2), dp(8));
        pickerBox.addView(hint);
        new Thread(new Runnable() { @Override public void run() {
            final PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ri = pm.queryIntentActivities(q, 0);
            final ArrayList<String[]> apps = new ArrayList<String[]>();
            final HashMap<String, Drawable> icons = new HashMap<String, Drawable>();
            for (ResolveInfo r : ri) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName()) || icons.containsKey(p)) continue;
                icons.put(p, r.loadIcon(pm));
                apps.add(new String[]{p, String.valueOf(r.loadLabel(pm))});
            }
            Collections.sort(apps, new Comparator<String[]>() {
                @Override public int compare(String[] a, String[] b) { return a[1].compareToIgnoreCase(b[1]); }
            });
            ui.post(new Runnable() { @Override public void run() {
                if (root == null) return;
                ArrayList<String> have = floatList();
                LinearLayout row = null;
                int n = 0;
                for (final String[] a : apps) {
                    if (n % 4 == 0) {
                        row = new LinearLayout(OverlayService.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        pickerBox.addView(row, lpw(-1, -2, 0, 0, 0, 6));
                    }
                    n++;
                    final LinearLayout t = new LinearLayout(OverlayService.this);
                    t.setOrientation(LinearLayout.VERTICAL);
                    t.setGravity(Gravity.CENTER_HORIZONTAL);
                    t.setPadding(dp(2), dp(6), dp(2), dp(5));
                    t.setBackground(have.contains(a[0]) ? rounded(alpha(accent, 40), 12, accent, 1.5f) : rounded(CARD, 12, 0, 0));
                    ImageView iv = new ImageView(OverlayService.this);
                    iv.setImageDrawable(icons.get(a[0]));
                    t.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
                    TextView l = tv(a[1], 8, TXT, false);
                    l.setGravity(Gravity.CENTER);
                    l.setSingleLine(true);
                    l.setEllipsize(TextUtils.TruncateAt.END);
                    t.addView(l, lpw(-1, -2, 0, 4, 0, 0));
                    t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        ArrayList<String> l2 = floatList();
                        if (l2.contains(a[0])) { l2.remove(a[0]); t.setBackground(rounded(CARD, 12, 0, 0)); flash("DIHAPUS: " + a[1].toUpperCase(Locale.US), MUTED, 1500); }
                        else { l2.add(a[0]); t.setBackground(rounded(alpha(accent, 40), 12, accent, 1.5f)); flash("DITAMBAH: " + a[1].toUpperCase(Locale.US), GOOD, 1500); }
                        saveFloat(l2);
                        refreshDockApps();
                    } });
                    LinearLayout.LayoutParams tp = lpw(0, -2, n % 4 == 1 ? 0 : 5, 0, 0, 0);
                    tp.weight = 1f;
                    row.addView(t, tp);
                }
                if (row != null) for (int i = n % 4 == 0 ? 4 : n % 4; i < 4; i++) {
                    LinearLayout.LayoutParams fp = lpw(0, 1, 5, 0, 0, 0); fp.weight = 1f;
                    row.addView(new View(OverlayService.this), fp);
                }
            } });
        } }).start();
    }

    private void addCurrentGame() {
        final String p = curPkg;
        if (p == null || p.isEmpty() || p.equals("com.comatoze") || !p.matches("[A-Za-z0-9_.]+")) {
            flash("BUKA GAME DULU, LALU TEKAN TOMBOL INI", WARN, 2500);
            return;
        }
        runCmd(Root.BIN + " games add " + p, "GAME DITAMBAHKAN", null);
    }

    // ---------- tema ----------
    private void applyAccent(int a) {
        accent = a;
        if (pillBg != null) pillBg.setStroke(Math.max(1, dp(1)), alpha(a, 150));
        for (RingGauge g : gauge) if (g != null) g.setAccent(a);
        if (badge != null) {
            badge.setTextColor(a);
            badge.setBackground(rounded(alpha(a, 40), 14, alpha(a, 140), 1));
        }
        paintState();
    }

    private void animateAccent(final int to) {
        if (to == accent) return;
        final int from = accent;
        final ArgbEvaluator ev = new ArgbEvaluator();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(350);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                applyAccent((Integer) ev.evaluate((Float) an.getAnimatedValue(), from, to));
            }
        });
        va.start();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); }
        }).start();
    }

    private void paintState() {
        if (modeTx[0] == null) return;
        for (int i = 0; i < 4; i++) {
            boolean sel = i == 0 ? curDef : (!curDef && MODE_KEYS[i].equals(curMode));
            if (sel) {
                modeTx[i].setBackground(rounded(alpha(accent, 60), 16, accent, 1.5f));
                modeTx[i].setTextColor(Color.WHITE);
            } else {
                modeTx[i].setBackground(null);
                modeTx[i].setTextColor(MUTED);
            }
        }
        boolean dnd = Boolean.TRUE.equals(tstate.get("dnd"));
        for (int i = 0; i < 5; i++) {
            if (quickBtn[i] == null) continue;
            if (i == 4) {
                quickBtn[i].setBackground(circle(recording ? BAD : Color.parseColor("#3A1C1E"), recording ? 0 : alpha(BAD, 150), 1));
                quickIc[i].setColor(recording ? Color.WHITE : BAD);
            } else if (i == 2 && dnd) {
                quickBtn[i].setBackground(circle(alpha(accent, 50), accent, 1.5f));
                quickIc[i].setColor(accent);
            } else {
                quickBtn[i].setBackground(circle(CARD2, 0, 0));
                quickIc[i].setColor(i == 3 ? accent : Color.parseColor("#C9D0DC"));
            }
        }
        if (hzLbl != null) hzLbl.setText(curHz.isEmpty() ? "Hz" : curHz + " Hz");
        if (badge != null) {
            String nm = "Balance";
            for (int i = 1; i < 4; i++) if (MODE_KEYS[i].equals(curMode)) nm = MODE_NAMES[i];
            badge.setText(curDef ? "Default · " + nm : nm);
        }
        for (Tile t : tiles) {
            boolean on = Boolean.TRUE.equals(tstate.get(t.id));
            t.box.setBackground(on ? rounded(alpha(accent, 34), 14, alpha(accent, 170), 1.2f) : rounded(CARD, 14, 0, 0));
            t.ic.setColor(on ? accent : MUTED);
            t.track.setBackground(rounded(on ? accent : CARD2, 8, 0, 0));
            t.knob.setBackground(circle(on ? Color.WHITE : MUTED, 0, 0));
            ((FrameLayout.LayoutParams) t.knob.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
            ((FrameLayout.LayoutParams) t.knob.getLayoutParams()).setMargins(dp(2), 0, dp(2), 0);
            t.knob.requestLayout();
        }
        for (int i = 0; i < 2; i++) {
            if (dockBtn[i] == null) continue;
            boolean sel = i == page;
            dockBtn[i].setBackground(sel ? rounded(CARD2, 14, 0, 0) : null);
            dockIc[i].setColor(sel ? accent : MUTED);
        }
    }

    // ---------- interaksi ----------
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
        if (on) {   // jaga panel tetap di dalam layar
            int pw = dp(DOCK_W + MAIN_W + 28), ph = dp(CONTENT_H + 56);
            lp.x = Math.max(0, Math.min(lp.x, scrW - pw));
            lp.y = Math.max(0, Math.min(lp.y, scrH - ph));
        }
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        pill.setVisibility(on ? View.GONE : View.VISIBLE);
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        if (on) loadState();
    }

    private void showPage(int i) {
        page = i;
        for (int k = 0; k < PAGES; k++) pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        paintState();
        if (i == 1) loadState();
        if (i == 2 && !pickerLoaded) loadPicker();
    }

    private void setMode(final int k, View v) {
        final String key = MODE_KEYS[k];
        final String prevMode = curMode;
        final boolean prevDef = curDef;
        final int pa = accent;
        pulse(v);
        curDef = k == 0;
        if (k != 0) { curMode = key; animateAccent(accentFor(key)); }
        paintState();
        runCmd(Root.BIN + " ovmode " + key, "MODE " + MODE_NAMES[k].toUpperCase(Locale.US), new Runnable() {
            @Override public void run() { if (curDef) loadState(); }
        }, new Runnable() {
            @Override public void run() { curMode = prevMode; curDef = prevDef; animateAccent(pa); paintState(); }
        });
    }

    private void toggleFeat(final String id, final String lbl) {
        final boolean to = !Boolean.TRUE.equals(tstate.get(id));
        runCmd(Root.BIN + " feat " + id + " " + (to ? "on" : "off"), lbl.toUpperCase(Locale.US) + (to ? " ON" : " OFF"),
                new Runnable() { @Override public void run() { tstate.put(id, to); paintState(); loadState(); } });
    }

    private void quick(int k, View v) {
        pulse(v);
        if (k == 0) runCmd(Root.BIN + " ovact killbg", "APP LATAR DIHENTIKAN", null);
        else if (k == 1) runCmd(Root.BIN + " ovact clean", "RAM DIBERSIHKAN", null);
        else if (k == 2) toggleFeat("dnd", "DND");
        else if (k == 3) {
            int cur = 0;
            try { cur = Integer.parseInt(curHz); } catch (Exception ignored) { }
            int next = HZ[0];
            for (int i = 0; i < HZ.length; i++) if (HZ[i] == cur) next = HZ[(i + 1) % HZ.length];
            final int to = next;
            runCmd(Root.BIN + " refresh " + to, to + " HZ", new Runnable() { @Override public void run() { curHz = String.valueOf(to); paintState(); } });
        } else toggleRecord();
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
                curDef = Boolean.TRUE.equals(tstate.get("def"));
                animateAccent(accentFor(curMode));
                paintState();
            } });
        } }).start();
    }

    // ---------- umpan balik ----------
    private void flash(String s, int color, long ms) {
        status.setText(s);
        status.setTextColor(color);
        status.setBackground(rounded(CARD, 14, alpha(color, 150), 1));
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        status.setVisibility(View.VISIBLE);
        ui.removeCallbacks(clearStatus);
        if (ms > 0) ui.postDelayed(clearStatus, ms);
    }
    private final Runnable clearStatus = new Runnable() { @Override public void run() { if (status != null) status.setVisibility(View.GONE); } };

    private void runCmd(String cmd, String okMsg, Runnable onOk) { runCmd(cmd, okMsg, onOk, null); }

    private void runCmd(final String cmd, final String okMsg, final Runnable onOk, final Runnable onFail) {
        flash("MENERAPKAN…", WARN, 0);
        new Thread(new Runnable() { @Override public void run() {
            final Root.Res r = Root.exec(cmd);
            final String o = r.out().trim();
            final boolean fail = (r.code != 0 && r.code != 141) || o.startsWith("ERR|");
            ui.post(new Runnable() { @Override public void run() {
                if (fail) {
                    String why = o.startsWith("ERR|") ? o.substring(4) : (r.code == 124 ? "waktu habis" : "kode " + r.code);
                    int nl = why.indexOf('\n'); if (nl > 0) why = why.substring(0, nl);
                    flash("GAGAL: " + why.toUpperCase(Locale.US), BAD, 3500);
                    if (onFail != null) onFail.run();
                } else {
                    flash(okMsg, GOOD, 2000);
                    if (onOk != null) onOk.run();
                }
            } });
        } }).start();
    }

    // ---------- Rekam layar ----------
    private void toggleRecord() {
        if (!recording) {
            recording = true;
            recStart = System.currentTimeMillis();
            paintState();
            flash("MEREKAM…", BAD, 1800);
            new Thread(new Runnable() { @Override public void run() {
                final Root.Res r = Root.exec(Root.BIN + " ovrec start", 7200);
                final String o = r.out().trim();
                ui.post(new Runnable() { @Override public void run() {
                    recording = false;
                    recLbl.setText("Rekam");
                    paintState();
                    if (o.startsWith("OK|")) {
                        String f = o.substring(3);
                        flash("TERSIMPAN: " + f.substring(f.lastIndexOf('/') + 1), GOOD, 4500);
                    } else {
                        flash("REKAM GAGAL: " + (o.startsWith("ERR|") ? o.substring(4).toUpperCase(Locale.US) : "KODE " + r.code), BAD, 4000);
                    }
                } });
            } }).start();
        } else {
            new Thread(new Runnable() { @Override public void run() { Root.exec(Root.BIN + " ovrec stop"); } }).start();
        }
    }

    // ---------- data ----------
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
        // OV|fps|cpu|gpu|bat|batTemp|ram|cpuMHz|gpuMHz|ping|pkg|cpuTemp|cpuMax|gpuMax
        String[] a = line.split("\\|", -1);
        String fps = f(a, 1), cpu = f(a, 2), gpu = f(a, 3), bat = f(a, 4), bt = f(a, 5), ram = f(a, 6);
        String cm = f(a, 7), gm = f(a, 8), ping = f(a, 9), pkg = f(a, 10), ct = f(a, 11);
        curPkg = pkg;
        pillNum.setText(fps.isEmpty() ? "--" : fps);
        if (!expanded) return;
        tick++;
        fpsTv.setText(fps.isEmpty() ? "--" : fps);
        fpsSub.setText("FPS  •  " + (curHz.isEmpty() ? "--" : curHz) + " Hz");
        pkgTv.setText(pkg);
        float cmv = parse(cm), gmv = parse(gm);
        gauge[0].set(parse(cpu), cpu + "%", "CPU " + (cmv > 0 ? String.format(Locale.US, "%.2fG", cmv / 1000f) : "--"));
        gauge[1].set(gpu.isEmpty() ? 0 : parse(gpu), gpu.isEmpty() ? "n/a" : gpu + "%", "GPU " + (gmv > 0 ? String.format(Locale.US, "%.2fG", gmv / 1000f) : "--"));
        int tv = ct.isEmpty() ? parse(bt) : parse(ct);
        stat[0].setText(ram + "%");
        stat[1].setText(tv + "°C");
        stat[2].setText(ping.isEmpty() ? "--" : ping + "ms");
        stat[3].setText(bat + "%");
        if (recording) {
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recLbl.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            recLbl.setTextColor((tick % 2 == 0) ? BAD : MUTED);
        } else recLbl.setTextColor(MUTED);
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
