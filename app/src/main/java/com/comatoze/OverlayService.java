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
 * Overlay in-game bergaya panel XBoost: header (logo, sinyal, baterai, jam), baris app jendela mengambang,
 * rail 4 menu di kiri yang ikut berpindah saat daftar digulir (Fitur Dasar, Alat Cepat, Anti-ganggu, Gameplay),
 * dial CPU/GPU bercahaya, mode, tombol bulat, kecerahan, dan saklar fitur. Baris berpanah membuka sub-halaman
 * penyesuaian tweaks. Ikon vektor, tanpa emoji. Data: `ultrapower ostat`; status tombol: `ultrapower ovstate`.
 */
public class OverlayService extends Service {
    private static final int ECO_C = Color.parseColor("#33D17A");
    private static final int BAL_C = Color.parseColor("#2F8CFF");
    private static final int PERF_C = Color.parseColor("#FF6A3D");
    private static final int PANEL = Color.parseColor("#F4121620");
    private static final int PANEL2 = Color.parseColor("#F40A0C11");
    private static final int CARD = Color.parseColor("#181B23");
    private static final int CARD2 = Color.parseColor("#252932");
    private static final int LINE = Color.parseColor("#2AFFFFFF");
    private static final int TXT = Color.parseColor("#ECEFF4");
    private static final int MUTED = Color.parseColor("#8A91A0");
    private static final int WARN = Color.parseColor("#F2B24C");
    private static final int BAD = Color.parseColor("#F0524F");
    private static final int GOOD = Color.parseColor("#33D17A");
    private static final int TABS = 4;
    private static final int RAIL_W = 62, BODY_W = 372, BODY_H = 300, HEAD_H = 26, APPS_H = 52;
    private static final int PANEL_W = 8 + BODY_W + 8, PANEL_H = 8 + HEAD_H + 4 + BODY_H + 8;

    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd", "wifilatency", "hzforce", "lessdbg", "datasaver", "def"};
    private static final String[] MODE_KEYS = {"default", "eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"Default", "Eco", "Balance", "Performa"};
    private static final String[] TAB_NAMES = {"Fitur\nDasar", "Alat Akses\nCepat", "Anti-\nganggu", "Gameplay\nTambahan"};
    private static final int[] TAB_ICONS = {IconView.GRID, IconView.SLIDERS, IconView.DND, IconView.PAD};
    private static final int[] HZ = {60, 90, 120};

    private static final class Sw { String id; SkewSwitch view; }

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root, body;
    private LinearLayout pill, panel, appsRow, subBox;
    private ScrollView scroll;
    private LinearLayout scrollCol;
    private final LinearLayout[] sec = new LinearLayout[TABS];
    private FrameLayout subPage;
    private GradientDrawable pillBg;
    private TextView pillNum, hdrClock, hdrBat, status, logo, fpsNum, infoTv, subTitle, hzLbl, recLbl;
    private IconView logoIc;
    private LinearLayout rail;
    private final LinearLayout[] railItem = new LinearLayout[TABS];
    private final IconView[] railIc = new IconView[TABS];
    private final TextView[] railTx = new TextView[TABS];
    private final RingGauge[] gauge = new RingGauge[2];
    private final TextView[] modeTx = new TextView[4];
    private final FrameLayout[] quickBtn = new FrameLayout[4];
    private final IconView[] quickIc = new IconView[4];
    private final TextView[] quickLbl = new TextView[4];
    private SeekBar bright;
    private final ArrayList<Sw> sws = new ArrayList<Sw>();
    private final ArrayList<Sw> subSws = new ArrayList<Sw>();
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private String curMode = "", curHz = "", curPkg = "";
    private boolean curDef, subOpen;
    private int accent = BAL_C, curTab = 0;
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
        sc = Math.max(0.55f, Math.min(1f, Math.min((wDp - 16) / PANEL_W, (hDp - 24) / (PANEL_H + 6))));

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
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{PANEL, PANEL2});
        pbg.setCornerRadius(dp(18));
        pbg.setStroke(Math.max(1, dp(1)), LINE);
        panel.setBackground(pbg);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setVisibility(View.GONE);
        panel.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(HEAD_H)));

        body = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollCol = new LinearLayout(this);
        scrollCol.setOrientation(LinearLayout.VERTICAL);
        scrollCol.setPadding(0, 0, 0, dp(20));
        scrollCol.addView(buildAppsRow(), lpw(-1, dp(APPS_H), 0, 4, 0, 0));
        sec[0] = buildSec0();
        sec[1] = buildSec1();
        sec[2] = buildSec2();
        sec[3] = buildSec3();
        for (int i = 0; i < TABS; i++) scrollCol.addView(sec[i], lpw(-1, -2, RAIL_W + 8, i == 0 ? 6 : 14, 0, 0));
        scroll.addView(scrollCol, new FrameLayout.LayoutParams(-1, -2));
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        rail = buildRail();
        body.addView(rail, new FrameLayout.LayoutParams(dp(RAIL_W), -2));
        subPage = buildSubPage();
        subPage.setVisibility(View.GONE);
        FrameLayout.LayoutParams sbp = new FrameLayout.LayoutParams(-1, -1);
        sbp.leftMargin = dp(RAIL_W + 8);
        body.addView(subPage, sbp);
        status = tv("", 10, TXT, true);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = dp(6);
        body.addView(status, sp);
        panel.addView(body, lpw(dp(BODY_W), dp(BODY_H), 0, 4, 0, 0));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { syncRail(); }
        });
        scrollCol.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() { syncRail(); }
        });
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

    // ---------- header ----------
    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), 0, 0, 0);
        logoIc = new IconView(this, IconView.GAUGE);
        h.addView(logoIc, new LinearLayout.LayoutParams(dp(22), dp(22)));
        logo = tv("COMATOZE", 14, Color.WHITE, true);
        logo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        logo.setLetterSpacing(0.1f);
        h.addView(logo, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView wf = new IconView(this, IconView.WIFI);
        wf.setColor(TXT);
        h.addView(wf, lpw(dp(16), dp(16), 0, 0, 6, 0));
        hdrBat = tv("--%", 9, TXT, true);
        h.addView(hdrBat, lpw(-2, -2, 0, 0, 8, 0));
        hdrClock = tv("--:--", 11, TXT, true);
        hdrClock.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
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
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setGravity(Gravity.CENTER_VERTICAL);
        appsRow.setPadding(dp(4), 0, dp(4), 0);
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
            appsRow.addView(iv, lpw(dp(44), dp(44), 0, 0, 12, 0));
        }
        FrameLayout add = new FrameLayout(this);
        add.setPadding(dp(12), dp(12), dp(12), dp(12));
        add.setBackground(rounded(CARD2, 14, 0, 0));
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setColor(TXT);
        add.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSub("float"); } });
        appsRow.addView(add, lpw(dp(44), dp(44), 0, 0, 4, 0));
    }

    // ---------- rail menu (ikut bergeser saat daftar digulir) ----------
    private LinearLayout buildRail() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < TABS; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            it.setPadding(dp(1), dp(8), dp(1), dp(8));
            railIc[i] = new IconView(this, TAB_ICONS[i]);
            it.addView(railIc[i], new LinearLayout.LayoutParams(dp(26), dp(26)));
            railTx[i] = tv(TAB_NAMES[i], 8, MUTED, false);
            railTx[i].setGravity(Gravity.CENTER);
            it.addView(railTx[i], lpw(-2, -2, 0, 3, 0, 0));
            it.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goSection(idx); } });
            railItem[i] = it;
            r.addView(it, lpw(-1, -2, 0, 0, 0, 6));
        }
        return r;
    }

    private void goSection(int i) {
        if (subOpen) closeSub();
        int y = i == 0 ? 0 : Math.max(0, sec[i].getTop() - dp(6));
        scroll.smoothScrollTo(0, y);
        curTab = i;
        paintRail();
    }

    private void syncRail() {
        if (rail == null) return;
        int sy = scroll.getScrollY();
        int appsBottom = dp(APPS_H + 8);
        rail.setTranslationY(Math.max(dp(4), appsBottom - sy));
        int cur = 0;
        int ref = sy + scroll.getHeight() / 3;
        for (int i = 0; i < TABS; i++) if (sec[i].getTop() <= ref) cur = i;
        View c = scroll.getChildAt(0);
        if (c != null && c.getHeight() > 0 && sy + scroll.getHeight() >= c.getHeight() - dp(3)) cur = TABS - 1;
        if (cur != curTab) { curTab = cur; paintRail(); }
    }

    private void paintRail() {
        for (int i = 0; i < TABS; i++) {
            if (railItem[i] == null) continue;
            boolean sel = i == curTab;
            railIc[i].setColor(sel ? Color.WHITE : MUTED);
            railTx[i].setTextColor(sel ? Color.WHITE : MUTED);
            railTx[i].setTypeface(Typeface.create("sans-serif-medium", sel ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    // ---------- baris fitur ----------
    private LinearLayout rowBase() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(44));
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        r.setBackground(rounded(CARD, 12, 0, 0));
        return r;
    }

    private void switchRow(LinearLayout parent, final String id, final String label, ArrayList<Sw> reg) {
        LinearLayout r = rowBase();
        r.addView(tv(label, 13, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        final Sw sw = new Sw();
        sw.id = id;
        sw.view = new SkewSwitch(this);
        r.addView(sw.view, lpw(dp(40), dp(16), 8, 0, 0, 0));
        reg.add(sw);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); toggleFeat(id, label); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 8));
        paintSw(sw);
    }

    private void paintSw(Sw sw) { sw.view.setState(Boolean.TRUE.equals(tstate.get(sw.id)), accent); }

    private void navRow(LinearLayout parent, final String label, final String key) {
        LinearLayout r = rowBase();
        r.addView(tv(label, 13, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        IconView ch = new IconView(this, IconView.CHEV);
        ch.setColor(MUTED);
        r.addView(ch, new LinearLayout.LayoutParams(dp(16), dp(16)));
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); openSub(key); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 8));
    }

    private void actionRow(LinearLayout parent, String label, final Runnable act) {
        LinearLayout r = rowBase();
        r.addView(tv(label, 13, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        IconView ch = new IconView(this, IconView.CHEV);
        ch.setColor(MUTED);
        r.addView(ch, new LinearLayout.LayoutParams(dp(16), dp(16)));
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); act.run(); } });
        parent.addView(r, lpw(-1, -2, 0, 0, 0, 8));
    }

    private LinearLayout section(int minHeightDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setMinimumHeight(dp(minHeightDp));
        return c;
    }

    // ---------- Seksi 0: Fitur Dasar ----------
    private LinearLayout buildSec0() {
        LinearLayout c = section(0);
        // dial CPU & GPU + FPS
        FrameLayout dials = new FrameLayout(this);
        LinearLayout gr = new LinearLayout(this);
        gr.setOrientation(LinearLayout.HORIZONTAL);
        gr.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            gauge[i] = new RingGauge(this);
            gr.addView(gauge[i], lpwt(1f, 0, 14, 0, 0));
            gauge[i].getLayoutParams().height = dp(124);
        }
        dials.addView(gr, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout fl = new LinearLayout(this);
        fl.setOrientation(LinearLayout.HORIZONTAL);
        fl.setGravity(Gravity.BOTTOM);
        TextView ft = tv("FPS ", 9, MUTED, true);
        ft.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        fl.addView(ft, lpw(-2, -2, 0, 0, 2, 2));
        fpsNum = tv("--", 20, Color.WHITE, true);
        fpsNum.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        fl.addView(fpsNum);
        dials.addView(fl, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        c.addView(dials, lpw(-1, -2, 0, 0, 0, 0));
        infoTv = tv("", 9, MUTED, false);
        infoTv.setGravity(Gravity.CENTER);
        c.addView(infoTv, lpw(-1, -2, 0, 2, 0, 0));

        // mode (kontrol segmen, garis tepi)
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(rounded(Color.parseColor("#0D1015"), 8, LINE, 1));
        seg.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            TextView t = tv(MODE_NAMES[i], 10.5f, MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(2), dp(9), dp(2), dp(9));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeTx[i] = t;
            seg.addView(t, lpwt(1f, 0, 0, 0, 0));
        }
        c.addView(seg, lpw(-1, -2, 0, 12, 0, 0));

        // 4 tombol bulat
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
            b.setPadding(dp(14), dp(14), dp(14), dp(14));
            quickIc[i] = new IconView(this, qi[i]);
            b.addView(quickIc[i], new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { quick(k, v); } });
            quickBtn[i] = b;
            cell.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView l = tv(ql[i], 8.5f, MUTED, false);
            l.setGravity(Gravity.CENTER);
            quickLbl[i] = l;
            cell.addView(l, lpw(-2, -2, 0, 4, 0, 0));
            if (i == 1) recLbl = l;
            if (i == 2) hzLbl = l;
            qr.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        c.addView(qr, lpw(-1, -2, 0, 14, 0, 12));

        // kecerahan
        LinearLayout br = rowBase();
        br.setPadding(dp(14), dp(10), dp(14), dp(10));
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
        br.addView(bright, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView sun = new IconView(this, IconView.SUN);
        sun.setColor(TXT);
        br.addView(sun, lpw(dp(24), dp(24), 8, 0, 0, 0));
        c.addView(br, lpw(-1, -2, 0, 0, 0, 10));

        View line = new View(this);
        line.setBackgroundColor(LINE);
        c.addView(line, lpw(-1, Math.max(1, dp(1)), 0, 0, 0, 10));

        navRow(c, "Optimalisasi Performa", "perf");
        switchRow(c, "perfx", "Tweak Ekstra Performa", sws);
        switchRow(c, "nothermal", "Matikan Thermal", sws);
        navRow(c, "Peningkatan Jaringan", "net");
        return c;
    }

    // ---------- Seksi 1: Alat Akses Cepat ----------
    private LinearLayout buildSec1() {
        LinearLayout c = section(150);
        actionRow(c, "Tutup app di latar", new Runnable() { @Override public void run() { runCmd(Root.BIN + " ovact killbg", "APP LATAR DIHENTIKAN", null); } });
        actionRow(c, "Pangkas cache", new Runnable() { @Override public void run() { runCmd(Root.BIN + " ovact trim", "CACHE DIPANGKAS", null); } });
        actionRow(c, "Tangkapan layar", new Runnable() { @Override public void run() { shot(); } });
        actionRow(c, "Diagnosa FPS (simpan ke Download)", new Runnable() { @Override public void run() {
            runCmd(Root.BIN + " ovdiag > /sdcard/Download/comatoze_ovdiag.txt 2>&1; echo OK", "TERSIMPAN: Download/comatoze_ovdiag.txt", null);
        } });
        navRow(c, "Jendela Mengambang", "float");
        return c;
    }

    // ---------- Seksi 2: Anti-ganggu ----------
    private LinearLayout buildSec2() {
        LinearLayout c = section(150);
        switchRow(c, "dnd", "Jangan Ganggu (DND)", sws);
        switchRow(c, "gamednd", "DND otomatis saat game", sws);
        switchRow(c, "gmslimit", "Batasi Google Play Services", sws);
        navRow(c, "Pembatas Latar", "bg");
        return c;
    }

    // ---------- Seksi 3: Gameplay Tambahan ----------
    private LinearLayout buildSec3() {
        LinearLayout c = section(150);
        switchRow(c, "gameclean", "Bersihkan RAM otomatis", sws);
        switchRow(c, "gameprofile", "Profil Game otomatis", sws);
        switchRow(c, "gamebypass", "Pengisian Daya Bypass", sws);
        actionRow(c, "Tambah game yang sedang dibuka", new Runnable() { @Override public void run() { addCurrentGame(); } });
        return c;
    }

    // ---------- Sub halaman (lipatan fitur) ----------
    private FrameLayout buildSubPage() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(rounded(Color.parseColor("#F00E1015"), 12, 0, 0));
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        IconView back = new IconView(this, IconView.BACK);
        back.setColor(TXT);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { closeSub(); } });
        head.addView(back, new LinearLayout.LayoutParams(dp(32), dp(32)));
        subTitle = tv("", 14, Color.WHITE, true);
        head.addView(subTitle, lpw(-2, -2, 8, 0, 0, 0));
        v.addView(head, lpw(-1, -2, 0, 0, 0, 10));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        subBox = new LinearLayout(this);
        subBox.setOrientation(LinearLayout.VERTICAL);
        subBox.setPadding(0, 0, 0, dp(12));
        sv.addView(subBox, new FrameLayout.LayoutParams(-1, -2));
        v.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        f.addView(v, new FrameLayout.LayoutParams(-1, -1));
        f.setPadding(dp(4), dp(4), dp(4), dp(4));
        return f;
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
        subOpen = true;
        subPage.setVisibility(View.VISIBLE);
        loadState();
    }

    private void closeSub() {
        subOpen = false;
        subPage.setVisibility(View.GONE);
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
        if (logoIc != null) logoIc.setColor(a);
        if (bright != null) {
            bright.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
            bright.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            bright.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3E48")));
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
                modeTx[i].setBackground(rounded(alpha(accent, 40), 7, accent, 1.5f));
                modeTx[i].setTextColor(accent);
            } else {
                modeTx[i].setBackground(null);
                modeTx[i].setTextColor(MUTED);
            }
        }
        int hz = 0;
        try { hz = Integer.parseInt(curHz); } catch (Exception ignored) { }
        for (int i = 0; i < 4; i++) {
            if (quickBtn[i] == null) continue;
            boolean act = (i == 1 && recording) || (i == 2 && hz >= 90);
            int fill = (i == 1 && recording) ? BAD : accent;
            quickBtn[i].setBackground(act ? circle(fill, 0, 0) : circle(CARD2, LINE, 1));
            quickIc[i].setColor(act ? Color.WHITE : Color.parseColor("#BFC6D3"));
            quickLbl[i].setTextColor(act ? Color.WHITE : MUTED);
        }
        if (hzLbl != null) hzLbl.setText(curHz.isEmpty() ? "Hz" : curHz + " Hz");
        for (Sw s : sws) paintSw(s);
        for (Sw s : subSws) paintSw(s);
        paintRail();
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
        } else closeSub();
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        pill.setVisibility(on ? View.GONE : View.VISIBLE);
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        if (on) loadState();
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
        fpsNum.setText(fps.isEmpty() ? "--" : fps);
        hdrBat.setText(bat + "%");
        hdrClock.setText(clockFmt.format(new Date()));
        int tmp = ct.isEmpty() ? parse(bt) : parse(ct);
        float cmv = parse(cm), gmv = parse(gm);
        infoTv.setText("CPU " + (cmv > 0 ? String.format(Locale.US, "%.2f", cmv / 1000f) : "--") + "G  ·  GPU "
                + (gmv > 0 ? String.format(Locale.US, "%.2f", gmv / 1000f) : "--") + "G  ·  RAM " + ram + "%  ·  " + tmp + "°C  ·  "
                + (ping.isEmpty() ? "ping --" : ping + "ms") + "  ·  " + (curHz.isEmpty() ? "--" : curHz) + "Hz");
        gauge[0].set(parse(cpu), String.valueOf(parse(cpu)), "%", "CPU");
        if (gpu.isEmpty()) gauge[1].set(0, "n/a", "", "GPU");
        else gauge[1].set(parse(gpu), String.valueOf(parse(gpu)), "%", "GPU");
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
