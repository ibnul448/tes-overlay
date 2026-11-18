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
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
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
import java.util.HashMap;
import java.util.Locale;

/**
 * Overlay in-game bergaya HUD game: panel bersudut, angka FPS besar + grafik, meter bersegmen, tombol mode berwarna
 * (Eco hijau, Balance biru, Performa merah). Seluruh ikon digambar vektor (tanpa emoji).
 * Data: `ultrapower ostat` (1 baris/detik). Status tombol: `ultrapower ovstate`. Rekam: `ultrapower ovrec`.
 */
public class OverlayService extends Service {
    private static final int ECO_A = Color.parseColor("#2BFF88"), ECO_B = Color.parseColor("#12C46A");
    private static final int BAL_A = Color.parseColor("#2EA8FF"), BAL_B = Color.parseColor("#1B6FE8");
    private static final int PERF_A = Color.parseColor("#FF3B3B"), PERF_B = Color.parseColor("#FF8A3D");
    private static final int TXT = Color.parseColor("#E8ECF4");
    private static final int MUTED = Color.parseColor("#7D8696");
    private static final int DIM = Color.parseColor("#9AA3B5");
    private static final int LINE = Color.parseColor("#2A3040");
    private static final int CARD = Color.parseColor("#0D1016");
    private static final int WARN = Color.parseColor("#FFB347");
    private static final int BAD = Color.parseColor("#FF4D4D");
    private static final int GOOD = Color.parseColor("#2BFF88");
    private static final int PAGES = 3;
    private static final int CONTENT_H = 372;
    private static final int MAIN_W = 306;

    // ikon, label, id fitur
    private static final Object[][] TOGGLES = {
            {IconView.TARGET, "FPSGO OFF", "fpsgooff"}, {IconView.THERMO, "NO THERMAL", "nothermal"},
            {IconView.TOUCH, "TOUCH BOOST", "touchboost"}, {IconView.BOLT, "TWEAK EKSTRA", "perfx"},
            {IconView.DND, "GAME DND", "gamednd"}, {IconView.BATTERY, "BYPASS CHARGE", "gamebypass"},
            {IconView.TRASH, "AUTO CLEAN", "gameclean"}, {IconView.PAD, "PROFIL GAME", "gameprofile"},
            {IconView.CLOUD, "GMS LIMITER", "gmslimit"}, {IconView.MOON, "AGGR. DOZE", "aggrdoze"},
            {IconView.PULSE, "WIFI LATENCY", "wifilatency"}, {IconView.HZ, "KUNCI HZ", "hzforce"},
            {IconView.CHIP, "LESS DEBUG", "lessdbg"}, {IconView.RAM, "DATA SAVER", "datasaver"}};
    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd", "wifilatency", "hzforce", "lessdbg", "datasaver", "def"};
    private static final String[] MODE_KEYS = {"default", "eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"DEFAULT", "ECO", "BALANCE", "PERFORMA"};
    private static final int[] MODE_ICONS = {IconView.GAUGE, IconView.LEAF, IconView.EQ, IconView.FLAME};
    private static final int[] HZ = {60, 90, 120};

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout pill;
    private GradientDrawable pillBg;
    private TextView pillNum;
    private LinearLayout panel;
    private HudShape panelBg;
    private final View[] pages = new View[PAGES];
    private final FrameLayout[] railBtn = new FrameLayout[PAGES];
    private final View[] railBar = new View[PAGES];
    private final IconView[] railIcon = new IconView[PAGES];
    private TextView status, logo, badge, fpsBig, fpsSub, pkgTv, hzLbl, recLbl;
    private FpsGraph graph;
    private final SegBar[] bars = new SegBar[4];
    private final TextView[] barVal = new TextView[4];
    private final TextView[] mini = new TextView[4];
    private final LinearLayout[] modeBtn = new LinearLayout[4];
    private final IconView[] modeIc = new IconView[4];
    private final TextView[] modeTx = new TextView[4];
    private final FrameLayout[] quickBtn = new FrameLayout[5];
    private final IconView[] quickIc = new IconView[5];
    private final LinearLayout[] tile = new LinearLayout[TOGGLES.length];
    private final IconView[] tileIc = new IconView[TOGGLES.length];
    private final TextView[] tileSt = new TextView[TOGGLES.length];
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private String curMode = "", curHz = "", curPkg = "";
    private int accent = BAL_A, accent2 = BAL_B;
    private int page = 0;
    private boolean curDef;
    private float sc = 1f, density = 1f;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Process proc;
    private volatile boolean running;
    private boolean expanded;
    private long recStart;
    private volatile boolean recording;
    private LinearLayout appsBox;
    private boolean appsLoaded, floatMode;
    private int tick = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------- util ----------
    private int dp(float v) { return (int) (v * density * sc + 0.5f); }
    private float sp(float v) { return v * sc; }
    private static int alpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }

    private static int accentFor(String m) { return "perf".equals(m) ? PERF_A : "eco".equals(m) ? ECO_A : BAL_A; }
    private static int accent2For(String m) { return "perf".equals(m) ? PERF_B : "eco".equals(m) ? ECO_B : BAL_B; }

    private TextView mono(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp(size));
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLetterSpacing(0.06f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private HudShape chamfer(int top, int bottom, int stroke, float sw, int corners, float cut) {
        return new HudShape(top, bottom, stroke, Math.max(1, dp(sw)), corners, dp(cut));
    }

    private HudShape slant(int top, int bottom, int stroke, float sw, float skew) {
        return new HudShape(top, bottom, stroke, Math.max(1, dp(sw)), 0, 0).skew(dp(skew));
    }

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

    private SharedPreferences prefs() { return getSharedPreferences("ov", MODE_PRIVATE); }

    // ---------- bangun UI ----------
    private void build() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        density = getResources().getDisplayMetrics().density;
        float hDp = getResources().getDisplayMetrics().heightPixels / density;
        float wDp = getResources().getDisplayMetrics().widthPixels / density;
        float needH = CONTENT_H + 44, needW = MAIN_W + 66;
        sc = Math.max(0.6f, Math.min(1f, Math.min((hDp - 20) / needH, (wDp - 10) / needW)));

        root = new FrameLayout(this);

        // chip FPS: kotak membulat hitam, angka putih
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#F2000000"));
        pillBg.setCornerRadius(dp(12));
        pillBg.setStroke(Math.max(1, dp(1)), alpha(accent, 160));
        pill.setBackground(pillBg);
        pillNum = mono("--", 17, Color.WHITE, true);
        pillNum.setGravity(Gravity.CENTER);
        TextView pl = mono("FPS", 8, Color.WHITE, false);
        pl.setGravity(Gravity.CENTER);
        pill.addView(pillNum, new LinearLayout.LayoutParams(-2, -2));
        pill.addView(pl, new LinearLayout.LayoutParams(-2, -2));
        pill.setMinimumWidth(dp(46));
        root.addView(pill, new FrameLayout.LayoutParams(-2, -2));

        // panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panelBg = chamfer(Color.parseColor("#13161E"), Color.parseColor("#07080B"), accent, 1.5f, HudShape.TR | HudShape.BL, 22)
                .glow(true, accent);
        panel.setBackground(panelBg);
        panel.setVisibility(View.GONE);

        panel.addView(buildRail(), new LinearLayout.LayoutParams(dp(54), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(10), dp(10), dp(14), dp(12));

        FrameLayout content = new FrameLayout(this);
        pages[0] = buildDash();
        ScrollView sv1 = new ScrollView(this);
        sv1.setVerticalScrollBarEnabled(false);
        sv1.addView(buildTweaks());
        pages[1] = sv1;
        ScrollView sv2 = new ScrollView(this);
        sv2.setVerticalScrollBarEnabled(false);
        sv2.addView(buildApps());
        pages[2] = sv2;
        for (int i = 0; i < PAGES; i++) content.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
        right.addView(content, new LinearLayout.LayoutParams(dp(MAIN_W), dp(CONTENT_H)));

        status = mono("", 10, TXT, true);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.setMargins(0, dp(6), 0, 0);
        right.addView(status, stp);
        panel.addView(right, new LinearLayout.LayoutParams(-2, -2));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        showPage(0);
        applyAccent(accent, accent2);

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

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(4), dp(14), dp(4), dp(12));
        HudShape rb = new HudShape(Color.parseColor("#06070A"), Color.parseColor("#06070A"), 0, 0, HudShape.BL, dp(22));
        rail.setBackground(rb);
        int[] ic = {IconView.GAUGE, IconView.SLIDERS, IconView.GRID};
        for (int i = 0; i < PAGES; i++) {
            final int idx = i;
            FrameLayout b = new FrameLayout(this);
            IconView iv = new IconView(this, ic[i]);
            b.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            View bar = new View(this);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(3), dp(24), Gravity.START | Gravity.CENTER_VERTICAL);
            b.addView(bar, bp);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (idx == 2 && floatMode) { floatMode = false; appsLoaded = false; } showPage(idx); } });
            railBtn[i] = b; railIcon[i] = iv; railBar[i] = bar;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46), dp(42));
            p.setMargins(0, dp(3), 0, dp(3));
            rail.addView(b, p);
        }
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        IconView add = new IconView(this, IconView.PLUS);
        add.setColor(TXT);
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { floatMode = true; appsLoaded = false; showPage(2); } });
        rail.addView(add, new LinearLayout.LayoutParams(dp(42), dp(42)));
        attachDrag(rail, false);
        return rail;
    }

    // ---------- Dashboard ----------
    private View buildDash() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // bar atas: logo + badge mode + minimize
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout lc = new LinearLayout(this);
        lc.setOrientation(LinearLayout.VERTICAL);
        logo = mono("COMATOZE", 13, Color.WHITE, true);
        logo.setLetterSpacing(0.2f);
        lc.addView(logo);
        pkgTv = mono("", 8, MUTED, false);
        pkgTv.setSingleLine(true);
        pkgTv.setEllipsize(TextUtils.TruncateAt.END);
        lc.addView(pkgTv);
        top.addView(lc, new LinearLayout.LayoutParams(0, -2, 1f));
        badge = mono("BALANCE", 10, Color.WHITE, true);
        badge.setLetterSpacing(0.12f);
        badge.setPadding(dp(12), dp(4), dp(12), dp(4));
        top.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        IconView mini0 = new IconView(this, IconView.MINUS);
        mini0.setColor(DIM);
        mini0.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(false); } });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(30), dp(30));
        mp.setMargins(dp(4), 0, 0, 0);
        top.addView(mini0, mp);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        attachDrag(top, false);

        // hero: FPS besar + grafik
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        fpsBig = mono("--", 46, Color.WHITE, true);
        fpsBig.setLetterSpacing(0f);
        hl.addView(fpsBig, new LinearLayout.LayoutParams(-2, -2));
        fpsSub = mono("FPS · -- HZ", 9, accent, true);
        fpsSub.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams fsp = new LinearLayout.LayoutParams(-2, -2);
        fsp.setMargins(0, dp(2), 0, 0);
        hl.addView(fpsSub, fsp);
        hero.addView(hl, new LinearLayout.LayoutParams(dp(118), -2));
        graph = new FpsGraph(this);
        hero.addView(graph, new LinearLayout.LayoutParams(0, dp(66), 1f));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(10), 0, 0);
        col.addView(hero, hp);

        // meter
        String[] ml = {"CPU", "GPU", "RAM", "TEMP"};
        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView l = mono(ml[i], 9, DIM, false);
            row.addView(l, new LinearLayout.LayoutParams(dp(34), -2));
            bars[i] = new SegBar(this);
            row.addView(bars[i], new LinearLayout.LayoutParams(0, dp(9), 1f));
            barVal[i] = mono("--", 10, Color.WHITE, true);
            barVal[i].setGravity(Gravity.END);
            row.addView(barVal[i], new LinearLayout.LayoutParams(dp(50), -2));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, dp(i == 0 ? 9 : 7), 0, 0);
            col.addView(row, rp);
        }

        // kotak info kecil
        LinearLayout mr = new LinearLayout(this);
        mr.setOrientation(LinearLayout.HORIZONTAL);
        String[] mt = {"CPU CLK", "GPU CLK", "PING", "BAT"};
        for (int i = 0; i < 4; i++) {
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setPadding(dp(6), dp(4), dp(4), dp(4));
            b.setBackground(chamfer(CARD, CARD, Color.parseColor("#232938"), 1, 0, 0));
            b.addView(mono(mt[i], 7.5f, MUTED, false));
            mini[i] = mono("--", 10.5f, Color.WHITE, true);
            b.addView(mini[i]);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
            bp.setMargins(i == 0 ? 0 : dp(5), 0, 0, 0);
            mr.addView(b, bp);
        }
        LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(-1, -2);
        mrp.setMargins(0, dp(10), 0, 0);
        col.addView(mr, mrp);

        // mode
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(5), dp(10), dp(5), dp(10));
            modeIc[i] = new IconView(this, MODE_ICONS[i]);
            b.addView(modeIc[i], new LinearLayout.LayoutParams(dp(13), dp(13)));
            modeTx[i] = mono(MODE_NAMES[i], 8f, MUTED, true);
            modeTx[i].setLetterSpacing(0.02f);
            modeTx[i].setSingleLine(true);
            modeTx[i].setPadding(dp(3), 0, 0, 0);
            b.addView(modeTx[i]);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeBtn[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
            bp.setMargins(i == 0 ? 0 : dp(4), 0, 0, 0);
            modes.addView(b, bp);
        }
        LinearLayout.LayoutParams mdp = new LinearLayout.LayoutParams(-1, -2);
        mdp.setMargins(0, dp(10), 0, 0);
        col.addView(modes, mdp);

        // aksi cepat
        LinearLayout qr = new LinearLayout(this);
        qr.setOrientation(LinearLayout.HORIZONTAL);
        int[] qi = {IconView.TRASH, IconView.RAM, IconView.DND, IconView.HZ, IconView.REC};
        String[] ql = {"CLEAN", "RAM", "DND", "-- HZ", "REC"};
        for (int i = 0; i < 5; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout b = new FrameLayout(this);
            b.setPadding(dp(9), dp(9), dp(9), dp(9));
            quickIc[i] = new IconView(this, qi[i]);
            b.addView(quickIc[i], new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { quick(k, v); } });
            quickBtn[i] = b;
            cell.addView(b, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView l = mono(ql[i], 7.5f, DIM, false);
            l.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(-2, -2);
            lpp.setMargins(0, dp(3), 0, 0);
            cell.addView(l, lpp);
            if (i == 3) hzLbl = l;
            if (i == 4) recLbl = l;
            qr.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams qrp = new LinearLayout.LayoutParams(-1, -2);
        qrp.setMargins(0, dp(10), 0, 0);
        col.addView(qr, qrp);
        return col;
    }

    // ---------- Tweaks ----------
    private TextView head(String s) {
        TextView t = mono(s, 8.5f, MUTED, true);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(2), dp(2), 0, dp(8));
        return t;
    }

    private View buildTweaks() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(head("// TWEAKS  ·  ketuk untuk nyala / mati"));
        LinearLayout row = null;
        for (int i = 0; i < TOGGLES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.setMargins(0, 0, 0, dp(7));
                col.addView(row, rp);
            }
            final int idx = i;
            final String id = (String) TOGGLES[i][2];
            final String lbl = (String) TOGGLES[i][1];
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(dp(10), dp(9), dp(9), dp(9));
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            tileIc[i] = new IconView(this, (Integer) TOGGLES[i][0]);
            tr.addView(tileIc[i], new LinearLayout.LayoutParams(dp(24), dp(24)));
            tr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            tileSt[i] = mono("OFF", 9, MUTED, true);
            tr.addView(tileSt[i]);
            t.addView(tr, new LinearLayout.LayoutParams(-1, -2));
            TextView name = mono(lbl, 10, Color.WHITE, true);
            name.setSingleLine(true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
            np.setMargins(0, dp(8), 0, 0);
            t.addView(name, np);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); toggleFeat(id, lbl); } });
            tile[idx] = t;
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMargins(i % 2 == 0 ? 0 : dp(7), 0, 0, 0);
            row.addView(t, tp);
        }
        return col;
    }

    // ---------- Apps ----------
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
                appsBox.addView(head(floatMode ? "// JENDELA MENGAMBANG  ·  pilih app" : "// GAME TERDAFTAR  ·  ketuk untuk membuka"));
                TextView addBtn = mono("+ TAMBAH GAME INI", 9.5f, accent, true);
                addBtn.setGravity(Gravity.CENTER);
                addBtn.setPadding(dp(8), dp(9), dp(8), dp(9));
                addBtn.setBackground(slant(CARD, CARD, accent, 1, 6));
                addBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); addCurrentGame(); } });
                LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(-1, -2);
                abp.setMargins(0, 0, 0, dp(8));
                appsBox.addView(addBtn, abp);
                int n = 0;
                LinearLayout row = null;
                for (String l : out.split("\n")) {
                    final String p = l.trim();
                    if (!p.matches("[A-Za-z0-9_.]+")) continue;
                    if (n % 3 == 0) {
                        row = new LinearLayout(OverlayService.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                        rp.setMargins(0, 0, 0, dp(7));
                        appsBox.addView(row, rp);
                    }
                    n++;
                    LinearLayout t = new LinearLayout(OverlayService.this);
                    t.setOrientation(LinearLayout.VERTICAL);
                    t.setGravity(Gravity.CENTER_HORIZONTAL);
                    t.setPadding(dp(4), dp(9), dp(4), dp(8));
                    t.setBackground(chamfer(Color.parseColor("#151922"), CARD, LINE, 1, HudShape.TR | HudShape.BL, 8));
                    ImageView iv = new ImageView(OverlayService.this);
                    String lbl = p;
                    try {
                        Drawable d = getPackageManager().getApplicationIcon(p);
                        iv.setImageDrawable(d);
                        lbl = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0)));
                    } catch (Exception ignored) { }
                    t.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    TextView tv = mono(lbl, 8.5f, TXT, true);
                    tv.setGravity(Gravity.CENTER);
                    tv.setMaxLines(2);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams tvp = new LinearLayout.LayoutParams(-1, -2);
                    tvp.setMargins(0, dp(6), 0, 0);
                    t.addView(tv, tvp);
                    t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        pulse(v);
                        if (floatMode) runCmd(Root.BIN + " ovfloat " + p, "JENDELA MENGAMBANG", new Runnable() { @Override public void run() { setExpanded(false); } });
                        else runCmd("monkey -p " + p + " -c android.intent.category.LAUNCHER 1", "MEMBUKA", null);
                    } });
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
                    tp.setMargins((n - 1) % 3 == 0 ? 0 : dp(7), 0, 0, 0);
                    row.addView(t, tp);
                }
                if (n == 0) {
                    TextView t = mono("Belum ada game terdaftar.\nBuka game lalu tekan + di rail kiri untuk menambahkannya.", 10, MUTED, false);
                    t.setPadding(dp(4), dp(10), dp(4), dp(10));
                    appsBox.addView(t);
                }
                int pad = 3 - (n % 3 == 0 ? 3 : n % 3);
                if (row != null && n > 0) for (int i = 0; i < pad; i++) row.addView(new View(OverlayService.this), new LinearLayout.LayoutParams(0, 1, 1f));
            } });
        } }).start();
    }

    private void addCurrentGame() {
        final String p = curPkg;
        if (p == null || p.isEmpty() || p.equals("com.comatoze") || !p.matches("[A-Za-z0-9_.]+")) {
            flash("BUKA GAME DULU, LALU TEKAN +", WARN, 2500);
            return;
        }
        runCmd(Root.BIN + " games add " + p, "GAME DITAMBAHKAN", new Runnable() { @Override public void run() { appsLoaded = false; if (page == 2) loadApps(); } });
    }

    // ---------- tema / status ----------
    private void applyAccent(int a, int b) {
        accent = a; accent2 = b;
        if (panelBg != null) panelBg.setStrokeColor(a);
        if (pillBg != null) pillBg.setStroke(Math.max(1, dp(1)), alpha(a, 170));
        if (graph != null) graph.setAccent(a);
        for (SegBar s : bars) if (s != null) s.setAccent(a);
        if (fpsBig != null) fpsBig.setShadowLayer(dp(10), 0, 0, a);
        if (fpsSub != null) fpsSub.setTextColor(a);
        if (logo != null) {
            SpannableStringBuilder sb = new SpannableStringBuilder("COMATOZE");
            sb.setSpan(new ForegroundColorSpan(Color.WHITE), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(a), 4, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            logo.setText(sb);
        }
        if (badge != null) badge.setBackground(slant(a, b, a, 0, 6));
        paintState();
        paintRail();
    }

    private void animateAccent(final int toA, final int toB) {
        if (toA == accent && toB == accent2) return;
        final int fa = accent, fb = accent2;
        final ArgbEvaluator ev = new ArgbEvaluator();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(420);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                float t = (Float) an.getAnimatedValue();
                applyAccent((Integer) ev.evaluate(t, fa, toA), (Integer) ev.evaluate(t, fb, toB));
            }
        });
        va.start();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(110).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(170).start(); }
        }).start();
    }

    private void paintState() {
        if (modeBtn[0] == null) return;
        for (int i = 0; i < 4; i++) {
            boolean sel = i == 0 ? curDef : (!curDef && MODE_KEYS[i].equals(curMode));
            if (sel) {
                modeBtn[i].setBackground(slant(accent, accent2, accent, 1, 8).glow(true, accent));
                modeIc[i].setColor(Color.WHITE);
                modeTx[i].setTextColor(Color.WHITE);
            } else {
                modeBtn[i].setBackground(slant(CARD, CARD, LINE, 1, 8));
                modeIc[i].setColor(MUTED);
                modeTx[i].setTextColor(MUTED);
            }
        }
        boolean dnd = Boolean.TRUE.equals(tstate.get("dnd"));
        for (int i = 0; i < 5; i++) {
            boolean act = (i == 2 && dnd);
            if (i == 4) {
                if (recording) { quickBtn[i].setBackground(chamfer(BAD, Color.parseColor("#B01E1E"), BAD, 1, HudShape.TR | HudShape.BL, 8).glow(true, BAD)); quickIc[i].setColor(Color.WHITE); }
                else { quickBtn[i].setBackground(chamfer(Color.parseColor("#2A0E0E"), Color.parseColor("#160707"), BAD, 1, HudShape.TR | HudShape.BL, 8)); quickIc[i].setColor(BAD); }
            } else if (act) {
                quickBtn[i].setBackground(chamfer(Color.parseColor("#151922"), CARD, accent, 1.5f, HudShape.TR | HudShape.BL, 8).glow(true, accent));
                quickIc[i].setColor(accent);
            } else {
                quickBtn[i].setBackground(chamfer(Color.parseColor("#151922"), CARD, LINE, 1, HudShape.TR | HudShape.BL, 8));
                quickIc[i].setColor(i == 3 ? accent : Color.parseColor("#CFD6E6"));
            }
        }
        if (hzLbl != null) hzLbl.setText(curHz.isEmpty() ? "HZ" : curHz + " HZ");
        if (badge != null) {
            String nm = "BALANCE";
            for (int i = 1; i < 4; i++) if (MODE_KEYS[i].equals(curMode)) nm = MODE_NAMES[i];
            badge.setText(nm);
        }
        for (int i = 0; i < TOGGLES.length; i++) {
            if (tile[i] == null) continue;
            boolean on = Boolean.TRUE.equals(tstate.get((String) TOGGLES[i][2]));
            if (on) {
                tile[i].setBackground(chamfer(Color.parseColor("#161B25"), CARD, accent, 1.5f, HudShape.TR | HudShape.BL, 10).glow(true, accent));
                tileIc[i].setColor(accent);
                tileSt[i].setText("ON");
                tileSt[i].setTextColor(accent);
            } else {
                tile[i].setBackground(chamfer(Color.parseColor("#151922"), CARD, LINE, 1, HudShape.TR | HudShape.BL, 10));
                tileIc[i].setColor(MUTED);
                tileSt[i].setText("OFF");
                tileSt[i].setTextColor(MUTED);
            }
        }
    }

    private void paintRail() {
        for (int i = 0; i < PAGES; i++) {
            if (railBtn[i] == null) continue;
            boolean sel = i == page;
            railIcon[i].setColor(sel ? accent : Color.parseColor("#7D8696"));
            railBar[i].setBackgroundColor(sel ? accent : Color.TRANSPARENT);
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
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        pill.setVisibility(on ? View.GONE : View.VISIBLE);
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        if (on) loadState();
    }

    private void showPage(int i) {
        page = i;
        for (int k = 0; k < PAGES; k++) pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        paintRail();
        if (i == 1) loadState();
        if (i != 2) floatMode = false;
        if (i == 2 && !appsLoaded) loadApps();
    }

    private void setMode(final int k, View v) {
        final String key = MODE_KEYS[k];
        final String prevMode = curMode;
        final boolean prevDef = curDef;
        final int pa = accent, pb = accent2;
        pulse(v);
        curDef = k == 0;
        if (k != 0) { curMode = key; animateAccent(accentFor(key), accent2For(key)); }
        paintState();
        runCmd(Root.BIN + " ovmode " + key, "MODE " + MODE_NAMES[k], new Runnable() {
            @Override public void run() { if (curDef) loadState(); }
        }, new Runnable() {
            @Override public void run() { curMode = prevMode; curDef = prevDef; animateAccent(pa, pb); paintState(); }
        });
    }

    private void toggleFeat(final String id, final String lbl) {
        final boolean to = !Boolean.TRUE.equals(tstate.get(id));
        runCmd(Root.BIN + " feat " + id + " " + (to ? "on" : "off"), lbl + (to ? " ON" : " OFF"),
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
                animateAccent(accentFor(curMode), accent2For(curMode));
                paintState();
            } });
        } }).start();
    }

    // ---------- umpan balik ----------
    private void flash(String s, int color, long ms) {
        status.setText(s);
        status.setTextColor(color);
        status.setBackground(slant(Color.parseColor("#10131A"), Color.parseColor("#0A0C11"), alpha(color, 160), 1, 6));
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

    // ---------- Record ----------
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
                    recLbl.setText("REC");
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

        int fv = parse(fps);
        fpsBig.setText(fps.isEmpty() ? "--" : fps);
        fpsSub.setText("FPS · " + (curHz.isEmpty() ? "--" : curHz) + " HZ");
        if (!fps.isEmpty()) graph.push(fv);
        pkgTv.setText(pkg);

        int tv = ct.isEmpty() ? parse(bt) : parse(ct);
        bars[0].setValue(parse(cpu));
        bars[1].setValue(gpu.isEmpty() ? 0 : parse(gpu));
        bars[2].setValue(parse(ram));
        bars[3].setValue(tv);
        barVal[0].setText(cpu + "%");
        barVal[1].setText(gpu.isEmpty() ? "n/a" : gpu + "%");
        barVal[2].setText(ram + "%");
        barVal[3].setText(tv + "°C");

        float cmv = parse(cm), gmv = parse(gm);
        mini[0].setText(cmv > 0 ? String.format(Locale.US, "%.2fG", cmv / 1000f) : "--");
        mini[1].setText(gmv > 0 ? String.format(Locale.US, "%.2fG", gmv / 1000f) : "--");
        mini[2].setText(ping.isEmpty() ? "--" : ping + "ms");
        mini[3].setText(bat + "%");

        if (recording) {
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recLbl.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            recLbl.setTextColor((tick % 2 == 0) ? BAD : DIM);
        } else recLbl.setTextColor(DIM);
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
