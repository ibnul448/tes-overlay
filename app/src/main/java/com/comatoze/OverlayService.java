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
import android.content.res.ColorStateList;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Overlay in-game bergaya panel game: header (logo, FPS, jam, baterai), baris app jendela mengambang,
 * rail 4 tab di kiri (Fitur Dasar, Alat Cepat, Anti-ganggu, Gameplay), dial CPU/GPU, mode, dan daftar fitur
 * yang bisa dilipat (ketuk baris panah untuk masuk ke penyesuaian tweaks). Ikon vektor, tanpa emoji.
 */
public class OverlayService extends Service {
    private static final int ECO_C = Color.parseColor("#4CC38A");
    private static final int BAL_C = Color.parseColor("#4A90FF");
    private static final int PERF_C = Color.parseColor("#F0524F");
    private static final int PANEL = Color.parseColor("#F2121316");
    private static final int CARD = Color.parseColor("#1D1F25");
    private static final int CARD2 = Color.parseColor("#2A2D34");
    private static final int RAIL = Color.parseColor("#0D0E11");
    private static final int LINE = Color.parseColor("#33FFFFFF");
    private static final int TXT = Color.parseColor("#ECEFF4");
    private static final int MUTED = Color.parseColor("#8E95A3");
    private static final int WARN = Color.parseColor("#F2B24C");
    private static final int BAD = Color.parseColor("#F0524F");
    private static final int GOOD = Color.parseColor("#4CC38A");
    private static final int TABS = 4, SUB = 4, NPAGES = 5;
    private static final int RAIL_W = 58, MAIN_W = 296, CONTENT_H = 270;
    private static final int PANEL_W = 8 + RAIL_W + 8 + MAIN_W + 8, PANEL_H = 8 + 24 + 4 + 40 + 4 + CONTENT_H + 8;

    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd", "wifilatency", "hzforce", "lessdbg", "datasaver", "def"};
    private static final String[] MODE_KEYS = {"default", "eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"Default", "Eco", "Balance", "Performa"};
    private static final String[] TAB_NAMES = {"Fitur\nDasar", "Alat\nCepat", "Anti-\nganggu", "Gameplay"};
    private static final int[] TAB_ICONS = {IconView.GRID, IconView.SLIDERS, IconView.DND, IconView.PAD};
    private static final int[] HZ = {60, 90, 120};

    private static final class Sw { String id; FrameLayout track; View knob; }

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root, content;
    private LinearLayout pill, panel, appsRow, subBox, subHead;
    private GradientDrawable pillBg;
    private TextView pillNum, hdrFps, hdrClock, hdrBat, status, logo, badge, fpsSub, subTitle, hzLbl, recLbl;
    private final View[] pages = new View[NPAGES];
    private final LinearLayout[] railItem = new LinearLayout[TABS];
    private final IconView[] railIc = new IconView[TABS];
    private final TextView[] railTx = new TextView[TABS];
    private final RingGauge[] gauge = new RingGauge[2];
    private final TextView[] modeTx = new TextView[4];
    private final FrameLayout[] quickBtn = new FrameLayout[4];
    private final IconView[] quickIc = new IconView[4];
    private IconView sunIc;
    private SeekBar bright;
    private final ArrayList<Sw> sws = new ArrayList<Sw>();
    private final ArrayList<Sw> subSws = new ArrayList<Sw>();
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private String curMode = "", curHz = "", curPkg = "";
    private boolean curDef;
    private int accent = BAL_C;
    private int page = 0, lastTab = 0;
    private float sc = 1f, density = 1f;
    private int scrW, scrH;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.US);
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

    /** Margin dalam dp (di-skala); ukuran w/h dipakai apa adanya (piksel atau -1/-2). */
    private LinearLayout.LayoutParams lpw(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams lpwt(float weight, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = lpw(0, -2, l, t, r, b);
        p.weight = weight;
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
        sc = Math.max(0.55f, Math.min(1f, Math.min((wDp - 16) / PANEL_W, (hDp - 24) / (PANEL_H + 8))));

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
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rounded(PANEL, 20, LINE, 1));
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setVisibility(View.GONE);
        panel.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(24)));
        panel.addView(buildAppsRow(), lpw(-1, dp(40), 0, 4, 0, 4));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(buildRail(), new LinearLayout.LayoutParams(dp(RAIL_W), dp(CONTENT_H)));
        content = new FrameLayout(this);
        pages[0] = tabScroll(buildTab0());
        pages[1] = tabScroll(buildTab1());
        pages[2] = tabScroll(buildTab2());
        pages[3] = tabScroll(buildTab3());
        pages[SUB] = buildSubPage();
        for (int i = 0; i < NPAGES; i++) content.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
        status = tv("", 10, TXT, true);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = dp(6);
        content.addView(status, sp);
        body.addView(content, lpw(dp(MAIN_W), dp(CONTENT_H), 8, 0, 0, 0));
        panel.addView(body, new LinearLayout.LayoutParams(-2, -2));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        showPage(0);
        applyAccent(accent);
        loadBrightness();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs().getInt("x", dp(8));
        lp.y = prefs().getInt("y", dp(60));
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

    private View tabScroll(View inner) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(inner, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    // ---------- header ----------
    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), 0, 0, 0);
        logo = tv("COMATOZE", 14, Color.WHITE, true);
        logo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        logo.setLetterSpacing(0.1f);
        h.addView(logo, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView fl = tv("FPS", 8, MUTED, true);
        h.addView(fl, lpw(-2, -2, 0, 4, 3, 0));
        hdrFps = tv("--", 17, Color.WHITE, true);
        hdrFps.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        h.addView(hdrFps, lpw(-2, -2, 0, 0, 10, 0));
        hdrBat = tv("--%", 9, TXT, true);
        h.addView(hdrBat, lpw(-2, -2, 0, 0, 8, 0));
        hdrClock = tv("--:--", 10, TXT, true);
        h.addView(hdrClock, lpw(-2, -2, 0, 0, 8, 0));
        IconView mn = new IconView(this, IconView.MINUS);
        mn.setColor(MUTED);
        mn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(false); } });
        h.addView(mn, new LinearLayout.LayoutParams(dp(24), dp(24)));
        attachDrag(h, false);
        return h;
    }

    // ---------- baris app jendela mengambang ----------
    private View buildAppsRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(appsRow, new FrameLayout.LayoutParams(-2, -1));
        refreshApps();
        return hs;
    }

    private ArrayList<String> floatList() {
        ArrayList<String> l = new ArrayList<String>();
        String s = prefs().getString("fl", "");
        for (String p : s.split(",")) if (p.matches("[A-Za-z0-9_.]+")) l.add(p);
        return l;
    }

    private void saveFloat(ArrayList<String> l) { prefs().edit().putString("fl", TextUtils.join(",", l)).apply(); }

    private void refreshApps() {
        appsRow.removeAllViews();
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
                refreshApps(); flash("APP DIHAPUS DARI BARIS INI", MUTED, 1800);
                return true;
            } });
            appsRow.addView(iv, lpw(dp(36), dp(36), 0, 2, 8, 2));
        }
        FrameLayout add = new FrameLayout(this);
        add.setPadding(dp(8), dp(8), dp(8), dp(8));
        add.setBackground(circle(CARD2, 0, 0));
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setColor(TXT);
        add.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSub("float"); } });
        appsRow.addView(add, lpw(dp(36), dp(36), 0, 2, 4, 2));
    }

    // ---------- rail tab ----------
    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(3), dp(6), dp(3), dp(6));
        rail.setBackground(rounded(RAIL, 16, 0, 0));
        for (int i = 0; i < TABS; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            it.setPadding(dp(2), dp(6), dp(2), dp(5));
            railIc[i] = new IconView(this, TAB_ICONS[i]);
            it.addView(railIc[i], new LinearLayout.LayoutParams(dp(22), dp(22)));
            railTx[i] = tv(TAB_NAMES[i], 7.5f, MUTED, false);
            railTx[i].setGravity(Gravity.CENTER);
            it.addView(railTx[i], lpw(-2, -2, 0, 3, 0, 0));
            it.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
            railItem[i] = it;
            rail.addView(it, lpw(-1, -2, 0, 2, 0, 2));
        }
        return rail;
    }

    // ---------- baris fitur ----------
    private LinearLayout rowBase() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(11), dp(12), dp(11));
        r.setBackground(rounded(CARD, 10, 0, 0));
        return r;
    }

    private void switchRow(LinearLayout parent, final String id, final String label, ArrayList<Sw> reg) {
        LinearLayout r = rowBase();
        TextView t = tv(label, 12, TXT, false);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final Sw sw = new Sw();
        sw.id = id;
        sw.track = new FrameLayout(this);
        sw.knob = new View(this);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER_VERTICAL | Gravity.START);
        kp.setMargins(dp(3), 0, dp(3), 0);
        sw.track.addView(sw.knob, kp);
        r.addView(sw.track, new LinearLayout.LayoutParams(dp(38), dp(20)));
        reg.add(sw);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); toggleFeat(id, label); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 6));
        paintSw(sw);
    }

    private void paintSw(Sw sw) {
        boolean on = Boolean.TRUE.equals(tstate.get(sw.id));
        sw.track.setBackground(rounded(on ? accent : Color.parseColor("#3A3D45"), 10, 0, 0));
        sw.knob.setBackground(circle(Color.WHITE, 0, 0));
        FrameLayout.LayoutParams kp = (FrameLayout.LayoutParams) sw.knob.getLayoutParams();
        kp.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
        sw.knob.setLayoutParams(kp);
    }

    private void navRow(LinearLayout parent, final String label, final String key) {
        LinearLayout r = rowBase();
        r.addView(tv(label, 12, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        IconView ch = new IconView(this, IconView.CHEV);
        ch.setColor(MUTED);
        r.addView(ch, new LinearLayout.LayoutParams(dp(14), dp(14)));
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); openSub(key); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 6));
    }

    private void actionRow(LinearLayout parent, String label, final Runnable act) {
        LinearLayout r = rowBase();
        r.addView(tv(label, 12, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = tv("JALANKAN", 8.5f, accent, true);
        go.setTag("accent");
        r.addView(go);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); act.run(); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 6));
    }

    private LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, 0, dp(2), dp(4));
        return c;
    }

    // ---------- Tab 0: Fitur Dasar ----------
    private View buildTab0() {
        LinearLayout c = col();
        // dial CPU & GPU
        LinearLayout gr = new LinearLayout(this);
        gr.setOrientation(LinearLayout.HORIZONTAL);
        gr.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            gauge[i] = new RingGauge(this);
            LinearLayout.LayoutParams gp = lpw(0, dp(108), i == 0 ? 0 : 6, 0, i == 0 ? 6 : 0, 0);
            gp.weight = 1f;
            gr.addView(gauge[i], gp);
        }
        c.addView(gr, lpw(-1, -2, 0, 0, 0, 0));
        fpsSub = tv("", 9, MUTED, false);
        fpsSub.setGravity(Gravity.CENTER);
        c.addView(fpsSub, lpw(-1, -2, 0, 4, 0, 0));
        badge = tv("Balance", 9, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);

        // mode
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(rounded(RAIL, 10, LINE, 1));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            TextView t = tv(MODE_NAMES[i], 9.5f, MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(2), dp(8), dp(2), dp(8));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeTx[i] = t;
            seg.addView(t, lpwt(1f, i == 0 ? 0 : 2, 0, 0, 0));
        }
        c.addView(seg, lpw(-1, -2, 0, 8, 0, 0));

        // 4 tombol bulat: tangkapan layar, rekam, Hz, pembersih memori
        LinearLayout qr = new LinearLayout(this);
        qr.setOrientation(LinearLayout.HORIZONTAL);
        int[] qi = {IconView.SHOT, IconView.REC, IconView.HZ, IconView.RAM};
        String[] ql = {"Tangkapan\nlayar", "Perekam\nlayar", "-- Hz", "Pembersih\nmemori"};
        for (int i = 0; i < 4; i++) {
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
            cell.addView(b, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView l = tv(ql[i], 8, MUTED, false);
            l.setGravity(Gravity.CENTER);
            cell.addView(l, lpw(-2, -2, 0, 3, 0, 0));
            if (i == 1) recLbl = l;
            if (i == 2) hzLbl = l;
            qr.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        c.addView(qr, lpw(-1, -2, 0, 10, 0, 6));

        // kecerahan
        LinearLayout br = rowBase();
        br.setPadding(dp(12), dp(8), dp(12), dp(8));
        sunIc = new IconView(this, IconView.SUN);
        sunIc.setColor(TXT);
        br.addView(sunIc, new LinearLayout.LayoutParams(dp(20), dp(20)));
        bright = new SeekBar(this);
        bright.setMax(255);
        bright.setProgress(128);
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                final int v = Math.max(1, s.getProgress());
                new Thread(new Runnable() { @Override public void run() {
                    Root.exec("settings put system screen_brightness_mode 0; settings put system screen_brightness " + v);
                } }).start();
            }
        });
        br.addView(bright, lpw(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) bright.getLayoutParams()).weight = 1f;
        c.addView(br, lpw(-1, -2, 0, 0, 0, 6));

        switchRow(c, "perfx", "Tweak Ekstra Performa", sws);
        switchRow(c, "nothermal", "Matikan Thermal", sws);
        navRow(c, "Optimalisasi Performa", "perf");
        navRow(c, "Peningkatan Jaringan", "net");
        return c;
    }

    // ---------- Tab 1: Alat Cepat ----------
    private View buildTab1() {
        LinearLayout c = col();
        actionRow(c, "Tutup app di latar", new Runnable() { @Override public void run() { runCmd(Root.BIN + " ovact killbg", "APP LATAR DIHENTIKAN", null); } });
        actionRow(c, "Pangkas cache", new Runnable() { @Override public void run() { runCmd(Root.BIN + " ovact trim", "CACHE DIPANGKAS", null); } });
        actionRow(c, "Tangkapan layar", new Runnable() { @Override public void run() { shot(); } });
        actionRow(c, "Diagnosa FPS (simpan ke Download)", new Runnable() { @Override public void run() {
            runCmd(Root.BIN + " ovdiag > /sdcard/Download/comatoze_ovdiag.txt 2>&1; echo OK", "TERSIMPAN: Download/comatoze_ovdiag.txt", null);
        } });
        navRow(c, "Jendela Mengambang", "float");
        return c;
    }

    // ---------- Tab 2: Anti-ganggu ----------
    private View buildTab2() {
        LinearLayout c = col();
        switchRow(c, "dnd", "Jangan Ganggu (DND)", sws);
        switchRow(c, "gamednd", "DND otomatis saat game", sws);
        switchRow(c, "gmslimit", "Batasi Google Play Services", sws);
        navRow(c, "Pembatas Latar", "bg");
        return c;
    }

    // ---------- Tab 3: Gameplay Tambahan ----------
    private View buildTab3() {
        LinearLayout c = col();
        switchRow(c, "gameclean", "Bersihkan RAM otomatis", sws);
        switchRow(c, "gameprofile", "Profil Game otomatis", sws);
        switchRow(c, "gamebypass", "Pengisian Daya Bypass", sws);
        actionRow(c, "Tambah game yang sedang dibuka", new Runnable() { @Override public void run() { addCurrentGame(); } });
        return c;
    }

    // ---------- Sub halaman (lipatan fitur) ----------
    private View buildSubPage() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        subHead = new LinearLayout(this);
        subHead.setOrientation(LinearLayout.HORIZONTAL);
        subHead.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setColor(TXT);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { showPage(lastTab); } });
        subHead.addView(back, new LinearLayout.LayoutParams(dp(30), dp(30)));
        subTitle = tv("", 13, Color.WHITE, true);
        subHead.addView(subTitle, lpw(-2, -2, 6, 0, 0, 0));
        v.addView(subHead, lpw(-1, -2, 0, 0, 0, 8));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        subBox = new LinearLayout(this);
        subBox.setOrientation(LinearLayout.VERTICAL);
        subBox.setPadding(0, 0, dp(2), dp(4));
        sv.addView(subBox, new FrameLayout.LayoutParams(-1, -2));
        v.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return v;
    }

    private void openSub(String key) {
        subBox.removeAllViews();
        subSws.clear();
        if ("perf".equals(key)) {
            subTitle.setText("Optimalisasi Performa");
            switchRow(subBox, "fpsgooff", "Matikan FPSGO", subSws);
            switchRow(subBox, "touchboost", "Touch Boost", subSws);
            switchRow(subBox, "hzforce", "Kunci Refresh Rate", subSws);
        } else if ("net".equals(key)) {
            subTitle.setText("Peningkatan Jaringan");
            switchRow(subBox, "wifilatency", "Wi-Fi latensi rendah", subSws);
            switchRow(subBox, "datasaver", "Hemat Data", subSws);
        } else if ("bg".equals(key)) {
            subTitle.setText("Pembatas Latar");
            switchRow(subBox, "aggrdoze", "Doze agresif", subSws);
            switchRow(subBox, "lessdbg", "Kurangi log debug", subSws);
        } else {
            subTitle.setText("Jendela Mengambang");
            loadPicker();
        }
        if (page != SUB) lastTab = Math.min(page, TABS - 1);
        showPage(SUB);
    }

    private void loadPicker() {
        TextView hint = tv("Ketuk app untuk menambah/menghapus dari baris atas. Ketuk ikon di baris atas untuk membukanya sebagai jendela mengambang; tekan lama untuk menghapus.", 9, MUTED, false);
        hint.setPadding(dp(2), 0, dp(2), dp(8));
        subBox.addView(hint);
        final LinearLayout box = subBox;
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
                if (root == null || box != subBox) return;
                ArrayList<String> have = floatList();
                LinearLayout row = null;
                int n = 0;
                for (final String[] a : apps) {
                    if (n % 4 == 0) {
                        row = new LinearLayout(OverlayService.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        box.addView(row, lpw(-1, -2, 0, 0, 0, 6));
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
                        refreshApps();
                    } });
                    row.addView(t, lpwt(1f, n % 4 == 1 ? 0 : 5, 0, 0, 0));
                }
                if (row != null) for (int i = n % 4 == 0 ? 4 : n % 4; i < 4; i++) row.addView(new View(OverlayService.this), lpwt(1f, 5, 0, 0, 0));
            } });
        } }).start();
    }

    private void addCurrentGame() {
        final String p = curPkg;
        if (p == null || p.isEmpty() || p.equals("com.comatoze") || !p.matches("[A-Za-z0-9_.]+")) {
            flash("BUKA GAME DULU, LALU TEKAN LAGI", WARN, 2500);
            return;
        }
        runCmd(Root.BIN + " games add " + p, "GAME DITAMBAHKAN", null);
    }

    private void shot() { runCmd(Root.BIN + " ovact shot", "TANGKAPAN TERSIMPAN", null); }

    private void loadBrightness() {
        new Thread(new Runnable() { @Override public void run() {
            final int v = parse(Root.sh("settings get system screen_brightness").trim());
            if (v > 0) ui.post(new Runnable() { @Override public void run() { if (bright != null) bright.setProgress(Math.min(255, v)); } });
        } }).start();
    }

    // ---------- tema ----------
    private void applyAccent(int a) {
        accent = a;
        if (pillBg != null) pillBg.setStroke(Math.max(1, dp(1)), alpha(a, 150));
        for (RingGauge g : gauge) if (g != null) g.setAccent(a);
        if (hdrFps != null) hdrFps.setTextColor(a);
        if (bright != null) {
            ColorStateList cs = ColorStateList.valueOf(a);
            bright.setProgressTintList(cs);
            bright.setThumbTintList(cs);
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
        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); }
        }).start();
    }

    private void paintState() {
        if (modeTx[0] == null) return;
        for (int i = 0; i < 4; i++) {
            boolean sel = i == 0 ? curDef : (!curDef && MODE_KEYS[i].equals(curMode));
            if (sel) {
                modeTx[i].setBackground(rounded(alpha(accent, 50), 8, accent, 1.5f));
                modeTx[i].setTextColor(Color.WHITE);
            } else {
                modeTx[i].setBackground(null);
                modeTx[i].setTextColor(MUTED);
            }
        }
        for (int i = 0; i < 4; i++) {
            if (quickBtn[i] == null) continue;
            if (i == 1) {
                quickBtn[i].setBackground(circle(recording ? BAD : CARD2, 0, 0));
                quickIc[i].setColor(recording ? Color.WHITE : accent);
            } else {
                quickBtn[i].setBackground(circle(CARD2, 0, 0));
                quickIc[i].setColor(accent);
            }
        }
        if (hzLbl != null) hzLbl.setText(curHz.isEmpty() ? "Hz" : curHz + " Hz");
        for (Sw s : sws) paintSw(s);
        for (Sw s : subSws) paintSw(s);
        int cur = page == SUB ? lastTab : page;
        for (int i = 0; i < TABS; i++) {
            if (railItem[i] == null) continue;
            boolean sel = i == cur;
            railItem[i].setBackground(sel ? rounded(CARD2, 12, 0, 0) : null);
            railIc[i].setColor(sel ? accent : MUTED);
            railTx[i].setTextColor(sel ? Color.WHITE : MUTED);
        }
        tintAccent(panel);
    }

    private void tintAccent(View v) {
        if ("accent".equals(v.getTag()) && v instanceof TextView) ((TextView) v).setTextColor(accent);
        if (v instanceof LinearLayout || v instanceof FrameLayout || v instanceof ScrollView) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tintAccent(g.getChildAt(i));
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
            lp.x = Math.max(0, Math.min(lp.x, scrW - dp(PANEL_W)));
            lp.y = Math.max(0, Math.min(lp.y, scrH - dp(PANEL_H)));
        }
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        pill.setVisibility(on ? View.GONE : View.VISIBLE);
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        if (on) loadState();
    }

    private void showPage(int i) {
        page = i;
        if (i < TABS) lastTab = i;
        for (int k = 0; k < NPAGES; k++) pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        paintState();
        if (i < TABS) loadState();
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
        tstate.put(id, to);
        paintState();
        runCmd(Root.BIN + " feat " + id + " " + (to ? "on" : "off"), lbl.toUpperCase(Locale.US) + (to ? " ON" : " OFF"),
                new Runnable() { @Override public void run() { loadState(); } },
                new Runnable() { @Override public void run() { tstate.put(id, !to); paintState(); } });
    }

    private void quick(int k, View v) {
        pulse(v);
        if (k == 0) shot();
        else if (k == 1) toggleRecord();
        else if (k == 2) {
            int cur = 0;
            try { cur = Integer.parseInt(curHz); } catch (Exception ignored) { }
            int next = HZ[0];
            for (int i = 0; i < HZ.length; i++) if (HZ[i] == cur) next = HZ[(i + 1) % HZ.length];
            final int to = next;
            runCmd(Root.BIN + " refresh " + to, to + " HZ", new Runnable() { @Override public void run() { curHz = String.valueOf(to); paintState(); } });
        } else runCmd(Root.BIN + " ovact clean", "RAM DIBERSIHKAN", null);
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
        status.setBackground(rounded(Color.parseColor("#EE16181D"), 14, alpha(color, 150), 1));
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
                    recLbl.setText("Perekam\nlayar");
                    recLbl.setTextColor(MUTED);
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
        hdrFps.setText(fps.isEmpty() ? "--" : fps);
        hdrBat.setText(bat + "%");
        hdrClock.setText(clockFmt.format(new Date()));
        int tmp = ct.isEmpty() ? parse(bt) : parse(ct);
        fpsSub.setText("RAM " + ram + "%   ·   " + tmp + "°C   ·   ping " + (ping.isEmpty() ? "--" : ping + "ms") + "   ·   " + (curHz.isEmpty() ? "--" : curHz) + " Hz");
        float cmv = parse(cm), gmv = parse(gm);
        gauge[0].set(parse(cpu), cpu + "%", "CPU " + (cmv > 0 ? String.format(Locale.US, "%.2fG", cmv / 1000f) : "--"));
        gauge[1].set(gpu.isEmpty() ? 0 : parse(gpu), gpu.isEmpty() ? "n/a" : gpu + "%", "GPU " + (gmv > 0 ? String.format(Locale.US, "%.2fG", gmv / 1000f) : "--"));
        if (recording) {
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recLbl.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            recLbl.setTextColor((tick % 2 == 0) ? BAD : MUTED);
        }
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
