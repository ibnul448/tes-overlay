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
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
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
 * Overlay in-game: header (logo, sinyal, baterai, jam), baris app jendela mengambang,
 * rail 4 menu di kiri yang ikut berpindah saat daftar digulir (Fitur Dasar, Alat Cepat, Anti-ganggu, Gameplay),
 * dial CPU/GPU bercahaya, mode, tombol bulat, kecerahan, dan saklar fitur. Baris berpanah membuka sub-halaman
 * penyesuaian tweaks. Ikon vektor, tanpa emoji. Data: `ultrapower ostat`; status tombol: `ultrapower ovstate`.
 */
public class OverlayService extends Service {
    private static final int ECO_C = Color.parseColor("#22E37E");
    private static final int BAL_C = Color.parseColor("#3B9BFF");
    private static final int PERF_C = Color.parseColor("#FF4B4B");
    private static final int PANEL = Color.parseColor("#F4121620");
    private static final int PANEL2 = Color.parseColor("#F40A0C11");
    private static final int CARD = Color.parseColor("#181B23");
    private static final int CARD2 = Color.parseColor("#252932");
    private static final int LINE = Color.parseColor("#2AFFFFFF");
    private static final int TXT = Color.parseColor("#ECEFF4");
    private static final int MUTED = Color.parseColor("#8A91A0");
    private static final int WARN = Color.parseColor("#F2B24C");
    private static final int BAD = Color.parseColor("#FF4B4B");
    private static final int GOOD = Color.parseColor("#22E37E");
    private static final int TABS = 5;
    private static final int ECO2_C = Color.parseColor("#9BF05A");
    private static final int BAL2_C = Color.parseColor("#2EDCFF");
    private static final int PERF2_C = Color.parseColor("#FF9A3D");
    private static final int RAIL_W = 80, BODY_W = 384, BODY_H = 330, HEAD_H = 34, APPS_H = 56;
    private static final int PANEL_W = 8 + BODY_W + 8, PANEL_H = 8 + HEAD_H + 4 + BODY_H + 8;

    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd", "wifilatency", "hzforce", "lessdbg", "datasaver", "def",
            "bkt", "smooth", "preload", "gamethermal", "doze", "gamefps", "fastcharge", "autocut", "bypass", "ecox"};
    private static final String[] MODE_KEYS = {"default", "eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"Default", "Eco", "Balance", "Performa"};
    private static final String[] TAB_NAMES = {"Fitur\nDasar", "Alat Akses\nCepat", "Anti-\nganggu", "Gameplay\nTambahan", "Daftar\nGame"};
    private static final int[] TAB_ICONS = {IconView.GRID, IconView.SLIDERS, IconView.DND, IconView.PAD, IconView.BOLT};
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
    private TextView pillNum, hdrClock, hdrBat, status, logo, infoTv, subTitle, hzLbl, recLbl, timerLbl;
    private LinearLayout rail;
    private final LinearLayout[] railItem = new LinearLayout[TABS];
    private final IconView[] railIc = new IconView[TABS];
    private final TextView[] railTx = new TextView[TABS];
    private final RingGauge[] gauge = new RingGauge[2];
    private final TextView[] modeTx = new TextView[4];
    private final HashMap<String, LinearLayout> gridBox = new HashMap<String, LinearLayout>();
    private final HashMap<String, IconView> gridIc = new HashMap<String, IconView>();
    private final HashMap<String, TextView> gridLbl = new HashMap<String, TextView>();
    private TextView fpsCap;
    private static final int[] PILL_C = {Color.parseColor("#4C8DFF"), Color.parseColor("#22E37E"), Color.parseColor("#2EDCFF"), Color.parseColor("#FF7A2B")};
    private LinearLayout cardNet, cardMem;
    private boolean xhOn;
    private IconView cardNetIc, cardMemIc;
    private LinearLayout gamesBox;
    private boolean wifiOn, timerRun;
    private long timerStart, timerAcc;
    private int briteStep = 0;
    private SeekBar bright;
    private final ArrayList<Sw> sws = new ArrayList<Sw>();
    private final ArrayList<Sw> subSws = new ArrayList<Sw>();
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private String curMode = "", curHz = "", curPkg = "";
    private boolean curDef = true, subOpen;
    private int accent = BAL_C, accent2 = BAL2_C, curTab = 0;
    private LogoView logoView;
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
    private static int accent2For(int a) { return a == PERF_C ? PERF2_C : a == ECO_C ? ECO2_C : BAL2_C; }

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
        final boolean animOnly = intent != null && intent.getBooleanExtra("animonly", false);
        final boolean wantAnim = intent != null && intent.getBooleanExtra("anim", false);
        final String apkg = intent != null ? intent.getStringExtra("pkg") : null;
        if (root != null) { if (wantAnim) showLaunchAnim(apkg, false); return START_STICKY; }
        startFg();
        if (animOnly) { showLaunchAnim(apkg, true); return START_NOT_STICKY; }
        if (wantAnim) showLaunchAnim(apkg, false);
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

    // ---------- animasi pembuka game ----------
    private LaunchAnimView animView;

    private void showLaunchAnim(String pkg, final boolean stopAfter) {
        try {
            if (animView != null) { animView.cancel(); try { getSystemService(WindowManager.class).removeView(animView); } catch (Exception ignored) { } animView = null; }
            String label = "";
            if (pkg != null && !pkg.isEmpty()) {
                try { label = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))); }
                catch (Exception e) { label = pkg; }
            }
            final WindowManager w = getSystemService(WindowManager.class);
            final LaunchAnimView v = new LaunchAnimView(this);
            v.setSub(label.isEmpty() ? "GAME MODE AKTIF" : "MEMUAT " + label.toUpperCase(Locale.US));
            WindowManager.LayoutParams l = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            w.addView(v, l);
            animView = v;
            v.play(new Runnable() { @Override public void run() {
                try { w.removeView(v); } catch (Exception ignored) { }
                if (animView == v) animView = null;
                if (stopAfter) stopSelf();
            } });
        } catch (Throwable t) {
            animView = null;
            if (stopAfter) stopSelf();
        }
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

        // chip FPS: kotak membulat hitam, hanya angka putih
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(14), dp(9), dp(14), dp(9));
        pillBg = rounded(Color.parseColor("#F2000000"), 18, 0, 0);
        pill.setBackground(pillBg);
        pillNum = tv("--", 22, Color.WHITE, true);
        pillNum.setGravity(Gravity.CENTER);
        pill.addView(pillNum, new LinearLayout.LayoutParams(-2, -2));
        pill.setMinimumWidth(dp(60));
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
        sec[4] = buildSec4();
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
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(new DecoView(this, 0), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(12), 0, dp(6), 0);
        logoView = new LogoView(this);
        h.addView(logoView, new LinearLayout.LayoutParams(dp(26), dp(26)));
        logo = tv("COMATOZE", 15, Color.WHITE, true);
        logo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        logo.setLetterSpacing(0.14f);
        logo.getPaint().setShader(new android.graphics.LinearGradient(0, 0, dp(110), 0, Color.WHITE, Color.parseColor("#9FB4D6"), android.graphics.Shader.TileMode.CLAMP));
        h.addView(logo, lpw(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) logo.getLayoutParams()).weight = 1f;
        IconView wf = new IconView(this, IconView.WIFI);
        wf.setColor(TXT);
        h.addView(wf, lpw(dp(16), dp(16), 0, 0, 8, 0));
        IconView bt = new IconView(this, IconView.BATTERY);
        bt.setColor(GOOD);
        h.addView(bt, lpw(dp(18), dp(18), 0, 0, 1, 0));
        hdrBat = tv("--%", 9, TXT, true);
        h.addView(hdrBat, lpw(-2, -2, 0, 0, 8, 0));
        hdrClock = tv("--:--", 12, TXT, true);
        hdrClock.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        h.addView(hdrClock, lpw(-2, -2, 0, 0, 8, 0));
        IconView mn = new IconView(this, IconView.MINUS);
        mn.setColor(MUTED);
        mn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(false); } });
        h.addView(mn, new LinearLayout.LayoutParams(dp(24), dp(24)));
        wrap.addView(h, new FrameLayout.LayoutParams(-1, -1));
        attachDrag(wrap, false);
        return wrap;
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
        seedApps();
        return hs;
    }

    private ArrayList<String> floatList() {
        ArrayList<String> l = new ArrayList<String>();
        String s = prefs().getString("fl", "");
        for (String p : s.split(",")) if (p.matches("[A-Za-z0-9_.]+")) l.add(p);
        return l;
    }

    /** Pertama kali: isi baris app dengan game yang terdaftar di manager (maks 8). */
    private void seedApps() {
        if (prefs().contains("fl")) return;
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh("cat /data/adb/ultrapower/games.txt 2>/dev/null");
            ui.post(new Runnable() { @Override public void run() {
                if (root == null || prefs().contains("fl")) return;
                ArrayList<String> l = new ArrayList<String>();
                PackageManager pm = getPackageManager();
                for (String p : out.split("\n")) {
                    p = p.trim();
                    if (!p.matches("[A-Za-z0-9_.]+") || l.size() >= 8) continue;
                    try { pm.getApplicationInfo(p, 0); l.add(p); } catch (Exception ignored) { }
                }
                saveFloat(l);
                refreshApps();
            } });
        } }).start();
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
            iv.setBackground(rounded(Color.TRANSPARENT, 12, 0, 0));
            iv.setClipToOutline(true);
            appsRow.addView(iv, lpw(dp(46), dp(46), 0, 0, 14, 0));
        }
        FrameLayout add = new FrameLayout(this);
        add.setPadding(dp(12), dp(12), dp(12), dp(12));
        add.setBackground(rounded(CARD2, 14, 0, 0));
        IconView plus = new IconView(this, IconView.PLUS);
        plus.setColor(TXT);
        add.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSub("float"); } });
        appsRow.addView(add, lpw(dp(46), dp(46), 0, 0, 4, 0));
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
            it.setPadding(dp(1), dp(6), dp(1), dp(6));
            railIc[i] = new IconView(this, TAB_ICONS[i]);
            it.addView(railIc[i], new LinearLayout.LayoutParams(dp(26), dp(26)));
            railTx[i] = tv(TAB_NAMES[i], 8.5f, MUTED, false);
            railTx[i].setGravity(Gravity.CENTER);
            it.addView(railTx[i], lpw(-2, -2, 0, 3, 0, 0));
            it.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goSection(idx); } });
            railItem[i] = it;
            r.addView(it, lpw(-1, -2, 0, 0, 0, 2));
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
        int appsBottom = dp(APPS_H + 14);
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
            railIc[i].setFilled(sel);
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
        // hero: dua dial (CPU | GPU) + kapsul FPS
        LinearLayout gr = new LinearLayout(this);
        gr.setOrientation(LinearLayout.HORIZONTAL);
        gr.setGravity(Gravity.CENTER);
        gauge[0] = new RingGauge(this); gauge[0].setSide(1);
        gauge[1] = new RingGauge(this); gauge[1].setSide(-1);
        gr.addView(gauge[0], lpwt(1f, 4, 0, 4, 0));
        gr.addView(gauge[1], lpwt(1f, 4, 0, 4, 0));
        gauge[0].getLayoutParams().height = dp(128);
        gauge[1].getLayoutParams().height = dp(128);
        c.addView(gr, lpw(-1, -2, 0, 2, 0, 0));
        fpsCap = tv("-- FPS   \u2022   -- Hz", 13, Color.WHITE, true);
        fpsCap.setGravity(Gravity.CENTER);
        fpsCap.setPadding(dp(12), dp(10), dp(12), dp(10));
        fpsCap.setBackground(capsuleBg(accent));
        c.addView(fpsCap, lpw(-1, -2, 4, 8, 4, 0));
        infoTv = tv("", 9, MUTED, false);
        infoTv.setGravity(Gravity.CENTER);
        c.addView(infoTv, lpw(-1, -2, 0, 2, 0, 0));

        // mode: empat tombol kapsul, yang aktif berpendar sesuai warna modenya
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable tray = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#0B0D11"), Color.parseColor("#171A20")});
        tray.setCornerRadius(dp(22));
        tray.setStroke(Math.max(1, dp(1)), LINE);
        seg.setBackground(tray);
        seg.setPadding(dp(6), dp(7), dp(6), dp(7));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            TextView t = tv(MODE_NAMES[i].toUpperCase(Locale.US), 9.5f, MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(1), dp(11), dp(1), dp(11));
            t.setBackground(pillBg(false, PILL_C[i]));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeTx[i] = t;
            seg.addView(t, lpwt(1f, i == 0 ? 0 : 5, 0, 0, 0));
        }
        c.addView(seg, lpw(-1, -2, 0, 10, 0, 0));

        // dua kartu fitur: boost jaringan & optimasi memori
        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.HORIZONTAL);
        cardNet = featCard("Boost jaringan", "Kurangi lag & jitter", IconView.WIFI, true);
        cardMem = featCard("Optimasi memori", "Bebaskan RAM sistem", IconView.RAM, false);
        cr.addView(cardNet, lpwt(1f, 0, 0, 0, 0));
        cr.addView(cardMem, lpwt(1f, 8, 0, 0, 0));
        c.addView(cr, lpw(-1, -2, 0, 10, 0, 0));
        cardNet.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); toggleFeat("wifilatency", "Boost jaringan"); } });
        cardMem.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); runCmd(Root.BIN + " ovact clean", "RAM DIBERSIHKAN", null); } });

        // kisi 4 x 3 alat cepat
        String[][] g = {
                {"shot", "Tangkapan"}, {"rec", "Rekam"}, {"touch", "Touch boost"}, {"xh", "Crosshair"},
                {"dnd", "DND"}, {"wifi", "Wi-Fi"}, {"timer", "Timer"}, {"hz", "Hz"},
                {"kill", "Tutup latar"}, {"float", "Mengambang"}, {"mgr", "Pengaturan"}, {"diag", "Diagnosa"}};
        int[] gi = {IconView.SHOT, IconView.REC, IconView.TOUCH, IconView.TARGET,
                IconView.DND, IconView.WIFI, IconView.PULSE, IconView.HZ,
                IconView.TRASH, IconView.WINDOW, IconView.SLIDERS, IconView.GAUGE};
        LinearLayout row = null;
        for (int i = 0; i < g.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                c.addView(row, lpw(-1, -2, 0, i == 0 ? 12 : 6, 0, 0));
            }
            row.addView(gridCell(g[i][0], g[i][1], gi[i]), lpwt(1f, 0, 0, 0, 0));
        }
        timerLbl = gridLbl.get("timer");
        recLbl = gridLbl.get("rec");
        hzLbl = gridLbl.get("hz");

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
        c.addView(br, lpw(-1, -2, 0, 12, 0, 10));

        View line = new View(this);
        line.setBackgroundColor(LINE);
        c.addView(line, lpw(-1, Math.max(1, dp(1)), 0, 0, 0, 10));

        navRow(c, "Optimalisasi Performa", "perf");
        switchRow(c, "perfx", "Tweak Ekstra Performa", sws);
        switchRow(c, "nothermal", "Matikan Thermal", sws);
        return c;
    }

    private LinearLayout featCard(String title, String sub, int icon, boolean isNet) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), dp(9), dp(10), dp(9));
        c.setBackground(rounded(CARD, 12, 0, 0));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView a = tv(title, 11, Color.WHITE, true);
        a.setSingleLine(true);
        t.addView(a);
        TextView b = tv(sub, 8, MUTED, false);
        b.setMaxLines(2);
        t.addView(b, lpw(-2, -2, 0, 2, 0, 0));
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        IconView ic = new IconView(this, icon);
        ic.setColor(TXT);
        c.addView(ic, lpw(dp(24), dp(24), 4, 0, 0, 0));
        if (isNet) cardNetIc = ic; else cardMemIc = ic;
        return c;
    }

    private LinearLayout gridCell(final String id, String label, int icon) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(7), dp(2), dp(6));
        IconView ic = new IconView(this, icon);
        cell.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView l = tv(label, 8.5f, MUTED, false);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(l, lpw(-1, -2, 0, 4, 0, 0));
        cell.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pulse(v); gridAction(id); } });
        if ("timer".equals(id)) cell.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { timerRun = false; timerAcc = 0; timerLbl.setText("Timer"); paintState(); return true; }
        });
        gridBox.put(id, cell); gridIc.put(id, ic); gridLbl.put(id, l);
        return cell;
    }

    private void gridAction(String id) {
        if ("shot".equals(id)) shot();
        else if ("rec".equals(id)) toggleRecord();
        else if ("touch".equals(id)) toggleFeat("touchboost", "Touch Boost");
        else if ("dnd".equals(id)) toggleFeat("dnd", "DND");
        else if ("wifi".equals(id)) {
            final boolean to = !wifiOn;
            wifiOn = to; paintState();
            runCmd("svc wifi " + (to ? "enable" : "disable"), "WI-FI " + (to ? "ON" : "OFF"), null, new Runnable() { @Override public void run() { wifiOn = !to; paintState(); } });
        } else if ("timer".equals(id)) {
            if (timerRun) { timerAcc += System.currentTimeMillis() - timerStart; timerRun = false; }
            else { timerStart = System.currentTimeMillis(); timerRun = true; }
            paintState();
        } else if ("xh".equals(id)) {
            new Thread(new Runnable() { @Override public void run() {
                String o = Root.sh(Root.BIN + " crosshair");
                final boolean to = !o.contains("CH|on|");
                Root.exec(Root.BIN + " crosshair " + (to ? "on" : "off"));
                ui.post(new Runnable() { @Override public void run() { xhOn = to; flash(to ? "CROSSHAIR AKTIF" : "CROSSHAIR MATI", to ? GOOD : MUTED, 1400); paintState(); } });
            } }).start();
        } else if ("hz".equals(id)) {
            int cur = parse(curHz);
            int next = HZ[0];
            for (int i = 0; i < HZ.length; i++) if (HZ[i] == cur) next = HZ[(i + 1) % HZ.length];
            final int to = next;
            runCmd(Root.BIN + " refresh " + to, to + " HZ", new Runnable() { @Override public void run() { curHz = String.valueOf(to); paintState(); } });
        } else if ("kill".equals(id)) runCmd(Root.BIN + " ovact killbg", "APP LATAR DIHENTIKAN", null);
        else if ("float".equals(id)) openSub("float");
        else if ("mgr".equals(id)) runCmd("am start -n com.comatoze/.MainActivity", "MEMBUKA PENGATURAN", new Runnable() { @Override public void run() { setExpanded(false); } });
        else if ("diag".equals(id)) runCmd(Root.BIN + " ovdiag > /sdcard/Download/comatoze_ovdiag.txt 2>&1; echo OK", "TERSIMPAN: Download/comatoze_ovdiag.txt", null);
    }

    // ---------- Seksi 1: Alat Akses Cepat ----------
    private LinearLayout buildSec1() {
        LinearLayout c = section(150);
        actionRow(c, "Pangkas cache", new Runnable() { @Override public void run() { runCmd(Root.BIN + " ovact trim", "CACHE DIPANGKAS", null); } });
        actionRow(c, "Ganti gaya crosshair", new Runnable() { @Override public void run() { runCmd(Root.BIN + " crosshair next style", "GAYA CROSSHAIR DIGANTI", null); } });
        actionRow(c, "Ganti warna crosshair", new Runnable() { @Override public void run() { runCmd(Root.BIN + " crosshair next color", "WARNA CROSSHAIR DIGANTI", null); } });
        return c;
    }

    // ---------- Seksi 2: Anti-ganggu ----------
    private LinearLayout buildSec2() {
        LinearLayout c = section(150);
        switchRow(c, "gamednd", "DND otomatis saat game", sws);
        switchRow(c, "gmslimit", "Batasi Google Play Services", sws);
        switchRow(c, "datasaver", "Hemat Data", sws);
        navRow(c, "Pembatas Latar", "bg");
        return c;
    }

    // ---------- Seksi 3: Gameplay Tambahan ----------
    private LinearLayout buildSec3() {
        LinearLayout c = section(150);
        switchRow(c, "gameclean", "Bersihkan RAM otomatis", sws);
        switchRow(c, "gameprofile", "Profil Game otomatis", sws);
        switchRow(c, "gamebypass", "Pengisian Daya Bypass", sws);
        switchRow(c, "gamethermal", "Thermal otomatis saat game", sws);
        switchRow(c, "preload", "Preload game ke cache", sws);
        navRow(c, "Daya & Pengisian", "chg");
        actionRow(c, "Tambah game yang sedang dibuka", new Runnable() { @Override public void run() { addCurrentGame(); } });
        return c;
    }

    // ---------- Seksi 4: Daftar Game ----------
    private LinearLayout buildSec4() {
        LinearLayout c = section(170);
        gamesBox = new LinearLayout(this);
        gamesBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(gamesBox, new LinearLayout.LayoutParams(-1, -2));
        loadGames();
        return c;
    }

    private void loadGames() {
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh("cat /data/adb/ultrapower/games.txt 2>/dev/null");
            ui.post(new Runnable() { @Override public void run() {
                if (root == null) return;
                gamesBox.removeAllViews();
                PackageManager pm = getPackageManager();
                LinearLayout row = null;
                int n = 0;
                for (String l : out.split("\n")) {
                    final String p = l.trim();
                    if (!p.matches("[A-Za-z0-9_.]+")) continue;
                    Drawable d;
                    String lbl;
                    try {
                        d = pm.getApplicationIcon(p);
                        lbl = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
                    } catch (Exception e) { continue; }
                    if (n % 4 == 0) {
                        row = new LinearLayout(OverlayService.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        gamesBox.addView(row, lpw(-1, -2, 0, 0, 0, 8));
                    }
                    n++;
                    LinearLayout t = new LinearLayout(OverlayService.this);
                    t.setOrientation(LinearLayout.VERTICAL);
                    t.setGravity(Gravity.CENTER_HORIZONTAL);
                    t.setPadding(dp(2), dp(6), dp(2), dp(6));
                    t.setBackground(rounded(CARD, 12, 0, 0));
                    ImageView iv = new ImageView(OverlayService.this);
                    iv.setImageDrawable(d);
                    t.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38)));
                    TextView tx = tv(lbl, 8, TXT, false);
                    tx.setGravity(Gravity.CENTER);
                    tx.setSingleLine(true);
                    tx.setEllipsize(TextUtils.TruncateAt.END);
                    t.addView(tx, lpw(-1, -2, 0, 4, 0, 0));
                    t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        pulse(v);
                        runCmd("monkey -p " + p + " -c android.intent.category.LAUNCHER 1", "MEMBUKA", new Runnable() { @Override public void run() { setExpanded(false); } });
                    } });
                    t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                        runCmd(Root.BIN + " ovfloat " + p, "JENDELA MENGAMBANG", new Runnable() { @Override public void run() { setExpanded(false); } });
                        return true;
                    } });
                    row.addView(t, lpwt(1f, n % 4 == 1 ? 0 : 6, 0, 0, 0));
                }
                if (n == 0) {
                    TextView t = tv("Belum ada game terdaftar. Buka game lalu tambahkan lewat Gameplay Tambahan.", 10, MUTED, false);
                    gamesBox.addView(t);
                } else {
                    TextView t = tv("Ketuk untuk membuka, tekan lama untuk jendela mengambang.", 8.5f, MUTED, false);
                    gamesBox.addView(t, lpw(-1, -2, 0, 2, 0, 0));
                    for (int i = n % 4 == 0 ? 4 : n % 4; i < 4 && row != null; i++) row.addView(new View(OverlayService.this), lpwt(1f, 6, 0, 0, 0));
                }
            } });
        } }).start();
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
            switchRow(subBox, "hzforce", "Kunci Refresh Rate", subSws);
            switchRow(subBox, "gamefps", "Buka batas 60 FPS game", subSws);
            switchRow(subBox, "bkt", "Kernel Tweaks (BKT)", subSws);
            switchRow(subBox, "smooth", "Anti kill proses latar", subSws);
            switchRow(subBox, "ecox", "Tweak Ekstra Eco", subSws);
        } else if ("chg".equals(key)) {
            subTitle.setText("Daya & Pengisian");
            switchRow(subBox, "fastcharge", "Fast Charging", subSws);
            switchRow(subBox, "autocut", "Auto Cut Charging", subSws);
            switchRow(subBox, "bypass", "Bypass Charging", subSws);
        } else if ("bg".equals(key)) {
            subTitle.setText("Pembatas Latar");
            switchRow(subBox, "aggrdoze", "Doze agresif", subSws);
            switchRow(subBox, "lessdbg", "Kurangi log debug", subSws);
            switchRow(subBox, "doze", "Doze Mode", subSws);
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

    // Overlay disembunyikan total sebelum menangkap layar, lalu dimunculkan lagi (panel yang lebar ikut diciutkan).
    private void shot() {
        if (root == null) return;
        if (expanded) setExpanded(false);
        root.setVisibility(View.INVISIBLE);
        ui.postDelayed(new Runnable() { @Override public void run() {
            new Thread(new Runnable() { @Override public void run() {
                final Root.Res r = Root.exec(Root.BIN + " ovact shot");
                final boolean ok = r.code == 0 && !r.out().trim().startsWith("ERR|");
                ui.postDelayed(new Runnable() { @Override public void run() {
                    if (root == null) return;
                    root.setVisibility(View.VISIBLE);
                    flash(ok ? "TANGKAPAN TERSIMPAN" : "GAGAL MENANGKAP LAYAR", ok ? GOOD : BAD, 2200);
                } }, 250);
            } }).start();
        } }, 450);
    }

    private void loadBrightness() {
        new Thread(new Runnable() { @Override public void run() {
            final int v = parse(Root.sh("settings get system screen_brightness").trim());
            if (v > 0) ui.post(new Runnable() { @Override public void run() { if (bright != null) bright.setProgress(Math.min(255, v)); } });
        } }).start();
    }



    // ---------- tema ----------
    private void applyAccent(int a) { applyAccent(a, accent2For(a)); }

    private void applyAccent(int a, int b) {
        accent = a; accent2 = b;
        if (gauge[0] != null) { gauge[0].setAccent(a, b); gauge[1].setAccent(b, a); if (fpsCap != null) fpsCap.setBackground(capsuleBg(a)); }
        if (logoView != null) logoView.setColors(a, b);
        if (bright != null) {
            bright.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
            bright.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            bright.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3E48")));
        }
        paintState();
    }

    private void animateAccent(final int to) {
        final int to2 = accent2For(to);
        if (to == accent && to2 == accent2) return;
        final int from = accent, from2 = accent2;
        final ArgbEvaluator ev = new ArgbEvaluator();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(350);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                float t = (Float) an.getAnimatedValue();
                applyAccent((Integer) ev.evaluate(t, from, to), (Integer) ev.evaluate(t, from2, to2));
            }
        });
        va.start();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); }
        }).start();
    }

    /** Kapsul gelap bergradasi; bila aktif diberi cincin berpendar warna c. */
    private Drawable pillBg(boolean sel, int c) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                sel ? new int[]{Color.parseColor("#14171D"), Color.parseColor("#0C0E12")} : new int[]{Color.parseColor("#2E3138"), Color.parseColor("#1C1E24")});
        base.setCornerRadius(dp(18));
        base.setStroke(Math.max(1, dp(sel ? 2 : 1)), sel ? c : Color.parseColor("#26FFFFFF"));
        if (!sel) return base;
        GradientDrawable glow = new GradientDrawable();
        glow.setCornerRadius(dp(21));
        glow.setStroke(dp(3), alpha(c, 90));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, base});
        ld.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        return ld;
    }

    private Drawable capsuleBg(int c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#0D0F13"), Color.parseColor("#181B21")});
        g.setCornerRadius(dp(22));
        g.setStroke(Math.max(1, dp(1.5f)), alpha(c, 170));
        return g;
    }

    private void paintState() {
        if (modeTx[0] == null) return;
        for (int i = 0; i < 4; i++) {
            boolean sel = i == 0 ? curDef : (!curDef && MODE_KEYS[i].equals(curMode));
            modeTx[i].setBackground(pillBg(sel, PILL_C[i]));
            modeTx[i].setTextColor(sel ? Color.WHITE : Color.parseColor("#9AA1AE"));
        }
        int hz = parse(curHz);
        boolean[] act = new boolean[12];
        String[] ids = {"shot", "rec", "touch", "xh", "dnd", "wifi", "timer", "hz", "kill", "float", "mgr", "diag"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            boolean on = ("rec".equals(id) && recording) || ("touch".equals(id) && Boolean.TRUE.equals(tstate.get("touchboost")))
                    || ("dnd".equals(id) && Boolean.TRUE.equals(tstate.get("dnd"))) || ("wifi".equals(id) && wifiOn)
                    || ("timer".equals(id) && timerRun) || ("hz".equals(id) && hz >= 90) || ("xh".equals(id) && xhOn);
            int col = ("rec".equals(id) && recording) ? BAD : accent;
            LinearLayout box = gridBox.get(id);
            if (box == null) continue;
            box.setBackground(on ? rounded(alpha(col, 34), 12, alpha(col, 190), 1.2f) : null);
            gridIc.get(id).setColor(on ? col : Color.parseColor("#C3CAD6"));
            gridLbl.get(id).setTextColor(on ? Color.WHITE : MUTED);
        }
        boolean netOn = Boolean.TRUE.equals(tstate.get("wifilatency"));
        if (cardNet != null) {
            cardNet.setBackground(netOn ? rounded(alpha(accent, 34), 12, alpha(accent, 190), 1.2f) : rounded(CARD, 12, 0, 0));
            cardNetIc.setColor(netOn ? accent : TXT);
            cardMemIc.setColor(accent);
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

    private void loadState() {
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh(Root.BIN + " ovstate");
            final boolean wf = "1".equals(Root.sh("settings get global wifi_on").trim());
            ui.post(new Runnable() { @Override public void run() {
                wifiOn = wf;
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
                    recLbl.setText("Rekam");
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

    /** Jaga chip/panel tetap di dalam layar (mis. setelah game memutar layar ke landscape, koordinat lama bisa di luar layar). */
    private void clampPos() {
        if (wm == null || lp == null || root == null) return;
        try {
            int w, h;
            if (Build.VERSION.SDK_INT >= 30) { android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds(); w = b.width(); h = b.height(); }
            else { android.util.DisplayMetrics m = new android.util.DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(m); w = m.widthPixels; h = m.heightPixels; }
            if (w <= 0 || h <= 0) return;
            scrW = w; scrH = h;
            int vw = root.getWidth() > 0 ? root.getWidth() : dp(expanded ? PANEL_W : 64);
            int vh = root.getHeight() > 0 ? root.getHeight() : dp(expanded ? PANEL_H : 44);
            int nx = Math.max(0, Math.min(lp.x, Math.max(0, w - vw))), ny = Math.max(0, Math.min(lp.y, Math.max(0, h - vh)));
            if (nx != lp.x || ny != lp.y) { lp.x = nx; lp.y = ny; wm.updateViewLayout(root, lp); }
        } catch (Throwable ignored) { }
    }

    private void onStat(String line) {
        if (root == null) return;
        // OV|fps|cpu|gpu|bat|batTemp|ram|cpuMHz|gpuMHz|ping|pkg|cpuTemp|cpuMax|gpuMax
        String[] a = line.split("\\|", -1);
        String fps = f(a, 1), cpu = f(a, 2), gpu = f(a, 3), bat = f(a, 4), bt = f(a, 5), ram = f(a, 6);
        String cm = f(a, 7), gm = f(a, 8), ping = f(a, 9), pkg = f(a, 10), ct = f(a, 11);
        curPkg = pkg;
        pillNum.setText(fps.isEmpty() ? "--" : fps);
        clampPos();
        if (!expanded) return;
        tick++;
        int fv = parse(fps);
        int hzN = Math.max(60, parse(curHz));
        fpsCap.setText((fps.isEmpty() ? "--" : fps) + " FPS   \u2022   " + (curHz.isEmpty() ? "--" : curHz) + " Hz");
        if (timerRun && timerLbl != null) {
            long ts = (timerAcc + System.currentTimeMillis() - timerStart) / 1000;
            timerLbl.setText(String.format(Locale.US, "%02d:%02d", ts / 60, ts % 60));
        } else if (timerLbl != null && timerAcc > 0) {
            long ts = timerAcc / 1000;
            timerLbl.setText(String.format(Locale.US, "%02d:%02d", ts / 60, ts % 60));
        }
        hdrBat.setText(bat + "%");
        hdrClock.setText(clockFmt.format(new Date()));
        int tmp = ct.isEmpty() ? parse(bt) : parse(ct);
        float cmv = parse(cm), gmv = parse(gm);
        infoTv.setText("RAM " + ram + "%  ·  " + tmp + "°C  ·  " + (ping.isEmpty() ? "ping --" : ping + "ms") + "  ·  " + (curHz.isEmpty() ? "--" : curHz) + "Hz");
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
        if (animView != null) { animView.cancel(); try { getSystemService(WindowManager.class).removeView(animView); } catch (Exception ignored) { } animView = null; }
        if (proc != null) proc.destroy();
        if (root != null) { try { wm.removeView(root); } catch (Exception ignored) { } root = null; }
        super.onDestroy();
    }
}
