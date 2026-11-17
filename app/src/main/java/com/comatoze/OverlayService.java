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
import android.graphics.drawable.LayerDrawable;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Overlay in-game bergaya dashboard gelap. Warna aksen mengikuti mode: Eco hijau, Balance biru, Performa merah.
 * Data: `ultrapower ostat` (1 baris/detik). Status tombol: `ultrapower ovstate`. Rekam layar: `ultrapower ovrec`
 * (resolusi / bitrate / fps diatur di manager, langsung dipakai tombol REC).
 */
public class OverlayService extends Service {
    private static final int ACC_ECO = Color.parseColor("#3DDC84");
    private static final int ACC_BAL = Color.parseColor("#4DA3FF");
    private static final int ACC_PERF = Color.parseColor("#FF4B3A");
    private static final int TXT = Color.parseColor("#ECECEF");
    private static final int MUTED = Color.parseColor("#8E8E96");
    private static final int WARN = Color.parseColor("#FFB74D");
    private static final int BAD = Color.parseColor("#FF5252");
    private static final int GOOD = Color.parseColor("#3DDC84");
    private static final int PAGES = 3;
    private static final int CONTENT_H = 352;
    private static final int MAIN_W = 304;

    private static final String[][] TOGGLES = {
            {"🎯", "FPSGO off", "fpsgooff"}, {"🌡", "No Thermal", "nothermal"},
            {"👆", "Touch Boost", "touchboost"}, {"🚀", "Tweak Ekstra", "perfx"},
            {"🔕", "Game DND", "gamednd"}, {"🔋", "Bypass Charge", "gamebypass"},
            {"🧹", "Auto Clean", "gameclean"}, {"🎮", "Profil Game", "gameprofile"},
            {"☁", "GMS Limiter", "gmslimit"}, {"💤", "Aggr. Doze", "aggrdoze"}};
    // urutan field OVS|mode|hz|...
    private static final String[] OVS_ORDER = {"fpsgooff", "nothermal", "touchboost", "gamednd", "gamebypass", "perfx", "gmslimit", "gameclean", "gameprofile", "aggrdoze", "dnd"};
    private static final String[] MODE_KEYS = {"eco", "balance", "perf"};
    private static final String[] MODE_NAMES = {"ECO", "BALANCE", "PERFORMA"};
    private static final int[] HZ = {60, 90, 120};

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout pill;
    private TextView pillNum;
    private LinearLayout panel;
    private GradientDrawable panelBg;
    private final View[] pages = new View[PAGES];
    private final FrameLayout[] railBtn = new FrameLayout[PAGES];
    private final IconView[] railIcon = new IconView[PAGES];
    private TextView status, recBtn, pacingBtn, fpsLine, statLine, engine, clockTv, battTv, hdrPkg;
    private GaugeView gCpu, gGpu;
    private final TextView[] modeBtn = new TextView[3];
    private final FrameLayout[] quickBtn = new FrameLayout[4];
    private TextView hzGlyph;
    private LinearLayout modeBar;
    private final List<TextView> rowPills = new ArrayList<TextView>();
    private final HashMap<String, Boolean> tstate = new HashMap<String, Boolean>();
    private String curMode = "", curHz = "", curPkg = "";
    private int accent = ACC_BAL;
    private int page = 0;
    private float sc = 1f, density = 1f;
    private float cpuMax = 0, gpuMax = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Process proc;
    private volatile boolean running;
    private boolean expanded;
    private long recStart;
    private volatile boolean recording;
    private LinearLayout appsBox;
    private boolean appsLoaded;
    private int tick = 0;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH.mm", Locale.US);

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------- util ----------
    private int dp(int v) { return (int) (v * density * sc + 0.5f); }
    private float sp(float v) { return v * sc; }

    private GradientDrawable rect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable grad(int top, int bottom, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static int alpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }

    private static int accentFor(String mode) {
        if ("perf".equals(mode)) return ACC_PERF;
        if ("eco".equals(mode)) return ACC_ECO;
        return ACC_BAL;
    }

    private SpannableStringBuilder dotText(int dotColor, String text, int textColor) {
        SpannableStringBuilder b = new SpannableStringBuilder("● " + text);
        b.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(textColor), 1, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
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
        float need = CONTENT_H + 40, needW = MAIN_W + 60;
        sc = Math.max(0.6f, Math.min(1f, Math.min((hDp - 24) / need, (wDp - 12) / needW)));

        root = new FrameLayout(this);

        // chip FPS: kotak membulat hitam, angka putih
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        pill.setBackground(rect(Color.parseColor("#F2000000"), 12));
        pillNum = new TextView(this);
        pillNum.setText("--");
        pillNum.setTextColor(Color.WHITE);
        pillNum.setTypeface(Typeface.DEFAULT_BOLD);
        pillNum.setTextSize(sp(17));
        pillNum.setGravity(Gravity.CENTER);
        TextView pl = new TextView(this);
        pl.setText("FPS");
        pl.setTextColor(Color.WHITE);
        pl.setTextSize(sp(8));
        pl.setGravity(Gravity.CENTER);
        pill.addView(pillNum, new LinearLayout.LayoutParams(-2, -2));
        pill.addView(pl, new LinearLayout.LayoutParams(-2, -2));
        pill.setMinimumWidth(dp(46));
        root.addView(pill, new FrameLayout.LayoutParams(-2, -2));

        // panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panelBg = grad(Color.parseColor("#232326"), Color.parseColor("#0C0C0E"), 28);
        panelBg.setStroke(Math.max(1, dp(2)), alpha(accent, 120));
        panel.setBackground(panelBg);
        panel.setVisibility(View.GONE);

        panel.addView(buildRail(), new LinearLayout.LayoutParams(dp(56), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(8), dp(10), dp(12), dp(10));

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

        status = new TextView(this);
        status.setTextSize(sp(11));
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp0 = new LinearLayout.LayoutParams(-1, -2);
        sp0.setMargins(0, dp(6), 0, 0);
        right.addView(status, sp0);
        panel.addView(right, new LinearLayout.LayoutParams(-2, -2));
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));

        showPage(0);
        applyAccent(accent);

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

        startStats();
    }

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(5), dp(12), dp(5), dp(10));
        GradientDrawable rb = rect(Color.parseColor("#0A0A0B"), 0);
        float r = dp(28);
        rb.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        rail.setBackground(rb);
        for (int i = 0; i < PAGES; i++) {
            final int idx = i;
            FrameLayout b = new FrameLayout(this);
            IconView ic = new IconView(this, i);
            b.addView(ic, new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
            railBtn[i] = b;
            railIcon[i] = ic;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
            p.setMargins(0, dp(4), 0, dp(4));
            rail.addView(b, p);
        }
        View spacer = new View(this);
        rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView add = railText("+", 26);
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addCurrentGame(); } });
        rail.addView(add, new LinearLayout.LayoutParams(dp(44), dp(40)));
        TextView close = railText("✕", 22);
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            new Thread(new Runnable() { @Override public void run() { Root.exec(Root.BIN + " overlay close"); } }).start();
            stopSelf();
        } });
        rail.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        attachDrag(rail, false);
        return rail;
    }

    private TextView railText(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(sp(size));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    // ---------- Dashboard ----------
    private TextView darkPill(SpannableStringBuilder s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp(11));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = grad(Color.parseColor("#2C2C30"), Color.parseColor("#141416"), 16);
        g.setStroke(Math.max(1, dp(1)), Color.parseColor("#3A3A40"));
        t.setBackground(g);
        return t;
    }

    private View buildDash() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // header: logo + baterai/jam
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout logoCol = new LinearLayout(this);
        logoCol.setOrientation(LinearLayout.VERTICAL);
        TextView logo = new TextView(this);
        logo.setText("COMATOZE");
        logo.setTextColor(Color.WHITE);
        logo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        logo.setTextSize(sp(16));
        logoCol.addView(logo);
        hdrPkg = new TextView(this);
        hdrPkg.setTextColor(MUTED);
        hdrPkg.setTextSize(sp(9));
        hdrPkg.setSingleLine(true);
        hdrPkg.setEllipsize(TextUtils.TruncateAt.END);
        logoCol.addView(hdrPkg);
        hdr.addView(logoCol, new LinearLayout.LayoutParams(0, -2, 1f));
        battTv = new TextView(this);
        battTv.setTextColor(Color.WHITE);
        battTv.setTextSize(sp(13));
        battTv.setTypeface(Typeface.DEFAULT_BOLD);
        battTv.setText("🔋 --%");
        hdr.addView(battTv);
        clockTv = new TextView(this);
        clockTv.setTextColor(Color.WHITE);
        clockTv.setTextSize(sp(15));
        clockTv.setTypeface(Typeface.DEFAULT_BOLD);
        clockTv.setPadding(dp(8), 0, 0, 0);
        hdr.addView(clockTv);
        col.addView(hdr, new LinearLayout.LayoutParams(-1, -2));
        attachDrag(hdr, false);

        // engine + REC + PACING
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        engine = new TextView(this);
        engine.setTextColor(TXT);
        engine.setTextSize(sp(11));
        engine.setText("Engine Active");
        r2.addView(engine, new LinearLayout.LayoutParams(0, -2, 1f));
        recBtn = darkPill(dotText(BAD, "REC", MUTED));
        recBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleRecord(); } });
        r2.addView(recBtn);
        pacingBtn = darkPill(dotText(BAD, "PACING", MUTED));
        pacingBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleFeat("fpsgooff", "Frame pacing (FPSGO off)"); } });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.setMargins(dp(6), 0, 0, 0);
        r2.addView(pacingBtn, pp);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.setMargins(0, dp(6), 0, 0);
        col.addView(r2, r2p);

        // gauge CPU & GPU
        LinearLayout gr = new LinearLayout(this);
        gr.setOrientation(LinearLayout.HORIZONTAL);
        gr.setGravity(Gravity.CENTER);
        gCpu = new GaugeView(this);
        gGpu = new GaugeView(this);
        gCpu.setData(0f, "--", "GHz", "CPU");
        gGpu.setData(0f, "--", "GHz", "GPU");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(120), dp(120));
        gp.setMargins(dp(8), 0, dp(8), 0);
        gr.addView(gCpu, gp);
        gr.addView(gGpu, new LinearLayout.LayoutParams(gp));
        LinearLayout.LayoutParams grp = new LinearLayout.LayoutParams(-1, -2);
        grp.setMargins(0, dp(4), 0, 0);
        col.addView(gr, grp);

        // FPS • Hz
        LinearLayout fb = new LinearLayout(this);
        fb.setOrientation(LinearLayout.VERTICAL);
        fb.setGravity(Gravity.CENTER);
        fb.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable fg = grad(Color.parseColor("#151517"), Color.parseColor("#0B0B0C"), 18);
        fg.setStroke(Math.max(1, dp(1)), Color.parseColor("#34343A"));
        fb.setBackground(fg);
        fpsLine = new TextView(this);
        fpsLine.setText("-- FPS • -- Hz");
        fpsLine.setTextColor(Color.WHITE);
        fpsLine.setTypeface(Typeface.DEFAULT_BOLD);
        fpsLine.setTextSize(sp(14));
        fpsLine.setGravity(Gravity.CENTER);
        fb.addView(fpsLine, new LinearLayout.LayoutParams(-1, -2));
        statLine = new TextView(this);
        statLine.setTextColor(MUTED);
        statLine.setTextSize(sp(9));
        statLine.setGravity(Gravity.CENTER);
        statLine.setSingleLine(true);
        fb.addView(statLine, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(-1, -2);
        fbp.setMargins(0, dp(6), 0, 0);
        col.addView(fb, fbp);

        // mode
        modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable mb = rect(Color.parseColor("#0A0A0C"), 24);
        mb.setStroke(Math.max(1, dp(1)), Color.parseColor("#2A2A2E"));
        modeBar.setBackground(mb);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView b = new TextView(this);
            b.setText(MODE_NAMES[i]);
            b.setTextSize(sp(10));
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setPadding(dp(4), dp(11), dp(4), dp(11));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(k, v); } });
            modeBtn[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
            bp.setMargins(dp(3), 0, dp(3), 0);
            modeBar.addView(b, bp);
        }
        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(-1, -2);
        mbp.setMargins(0, dp(8), 0, 0);
        col.addView(modeBar, mbp);

        // aksi cepat
        LinearLayout qr = new LinearLayout(this);
        qr.setOrientation(LinearLayout.HORIZONTAL);
        String[] ql = {"Clean", "RAM", "DND", "Hz"};
        String[] qg = {"🧹", "🚀", "🚫", "Hz"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout b = new FrameLayout(this);
            TextView g = new TextView(this);
            g.setText(qg[i]);
            g.setTextSize(sp(i == 3 ? 13 : 17));
            g.setTypeface(Typeface.DEFAULT_BOLD);
            g.setTextColor(MUTED);
            g.setGravity(Gravity.CENTER);
            if (i == 3) hzGlyph = g;
            b.addView(g, new FrameLayout.LayoutParams(-1, -1));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { quick(k, v); } });
            quickBtn[i] = b;
            cell.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView l = new TextView(this);
            l.setText(ql[i]);
            l.setTextColor(TXT);
            l.setTextSize(sp(10));
            l.setGravity(Gravity.CENTER);
            cell.addView(l, new LinearLayout.LayoutParams(-2, -2));
            qr.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams qrp = new LinearLayout.LayoutParams(-1, -2);
        qrp.setMargins(0, dp(8), 0, 0);
        col.addView(qr, qrp);
        return col;
    }

    // ---------- Tweaks ----------
    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(sp(10));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(2), 0, dp(6));
        return t;
    }

    private View buildTweaks() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(title("TWEAKS • ketuk untuk nyala / mati"));
        for (int i = 0; i < TOGGLES.length; i++) {
            final String id = TOGGLES[i][2];
            final String lbl = TOGGLES[i][1];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(10), dp(10));
            GradientDrawable g = grad(Color.parseColor("#26262A"), Color.parseColor("#151517"), 18);
            g.setStroke(Math.max(1, dp(1)), Color.parseColor("#33333A"));
            row.setBackground(g);
            TextView ic = new TextView(this);
            ic.setText(TOGGLES[i][0]);
            ic.setTextSize(sp(17));
            ic.setGravity(Gravity.CENTER);
            row.addView(ic, new LinearLayout.LayoutParams(dp(30), -2));
            TextView name = new TextView(this);
            name.setText(lbl);
            name.setTextColor(Color.WHITE);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextSize(sp(13));
            name.setPadding(dp(8), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView st = new TextView(this);
            st.setTextSize(sp(11));
            st.setTypeface(Typeface.DEFAULT_BOLD);
            st.setPadding(dp(10), dp(5), dp(10), dp(5));
            st.setBackground(rect(Color.parseColor("#0C0C0E"), 14));
            row.addView(st);
            rowPills.add(st);
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleFeat(id, lbl); } });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, dp(3), 0, dp(3));
            col.addView(row, rp);
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
                appsBox.addView(title("GAME TERDAFTAR • ketuk untuk membuka"));
                int n = 0;
                for (String l : out.split("\n")) {
                    final String p = l.trim();
                    if (!p.matches("[A-Za-z0-9_.]+")) continue;
                    n++;
                    LinearLayout row = new LinearLayout(OverlayService.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(10), dp(8), dp(10), dp(8));
                    GradientDrawable g = grad(Color.parseColor("#26262A"), Color.parseColor("#151517"), 18);
                    g.setStroke(Math.max(1, dp(1)), Color.parseColor("#33333A"));
                    row.setBackground(g);
                    ImageView iv = new ImageView(OverlayService.this);
                    String lbl = p;
                    try {
                        Drawable d = getPackageManager().getApplicationIcon(p);
                        iv.setImageDrawable(d);
                        lbl = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0)));
                    } catch (Exception ignored) { }
                    row.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
                    TextView t = new TextView(OverlayService.this);
                    t.setText(lbl);
                    t.setTextColor(Color.WHITE);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    t.setTextSize(sp(13));
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(dp(10), 0, 0, 0);
                    row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                    row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        runCmd("monkey -p " + p + " -c android.intent.category.LAUNCHER 1", "Membuka", null);
                    } });
                    LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(-1, -2);
                    q.setMargins(0, dp(3), 0, dp(3));
                    appsBox.addView(row, q);
                }
                if (n == 0) {
                    TextView t = new TextView(OverlayService.this);
                    t.setText("Belum ada game terdaftar.\nBuka game lalu tekan + di sisi kiri untuk menambahkannya.");
                    t.setTextColor(MUTED);
                    t.setTextSize(sp(12));
                    t.setPadding(dp(8), dp(12), dp(8), dp(12));
                    appsBox.addView(t);
                }
            } });
        } }).start();
    }

    private void addCurrentGame() {
        final String p = curPkg;
        if (p == null || p.isEmpty() || p.equals("com.comatoze") || !p.matches("[A-Za-z0-9_.]+")) {
            flash("Buka game dulu, lalu tekan +", Color.parseColor("#3A3320"), WARN, 2500);
            return;
        }
        runCmd(Root.BIN + " games add " + p, "Game ditambahkan: " + p, new Runnable() { @Override public void run() { appsLoaded = false; if (page == 2) loadApps(); } });
    }

    // ---------- tema / status tombol ----------
    private void applyAccent(int c) {
        accent = c;
        if (panelBg != null) panelBg.setStroke(Math.max(1, dp(2)), alpha(c, 120));
        if (gCpu != null) { gCpu.setAccent(c); gGpu.setAccent(c); }
        paintState();
        paintRail();
    }

    private void animateAccent(int to) {
        if (to == accent) return;
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), accent, to);
        va.setDuration(420);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { applyAccent((Integer) a.getAnimatedValue()); }
        });
        va.start();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(180).start(); }
        }).start();
    }

    private Drawable modeBg(boolean selected) {
        if (!selected) {
            GradientDrawable g = grad(Color.parseColor("#2E2E33"), Color.parseColor("#18181B"), 20);
            g.setStroke(Math.max(1, dp(1)), Color.parseColor("#3A3A40"));
            return g;
        }
        GradientDrawable glow = rect(Color.TRANSPARENT, 22);
        glow.setStroke(Math.max(2, dp(4)), alpha(accent, 70));
        GradientDrawable ring = rect(Color.parseColor("#0E0E10"), 20);
        ring.setStroke(Math.max(1, dp(2)), accent);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, ring});
        int in = Math.max(1, dp(1));
        ld.setLayerInset(1, in, in, in, in);
        return ld;
    }

    private Drawable circleBg(boolean active) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#34343A"), Color.parseColor("#17171A")});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Math.max(1, dp(active ? 2 : 1)), active ? accent : Color.parseColor("#3C3C42"));
        return g;
    }

    private void paintState() {
        for (int i = 0; i < 3; i++) {
            boolean sel = MODE_KEYS[i].equals(curMode);
            modeBtn[i].setBackground(modeBg(sel));
            modeBtn[i].setTextColor(sel ? Color.WHITE : MUTED);
        }
        boolean dnd = Boolean.TRUE.equals(tstate.get("dnd"));
        for (int i = 0; i < 4; i++) {
            boolean act = (i == 2 && dnd);
            quickBtn[i].setBackground(circleBg(act));
        }
        if (hzGlyph != null) {
            hzGlyph.setText(curHz.isEmpty() ? "Hz" : curHz);
            hzGlyph.setTextColor(accent);
        }
        boolean pacing = Boolean.TRUE.equals(tstate.get("fpsgooff"));
        if (pacingBtn != null) pacingBtn.setText(dotText(pacing ? GOOD : BAD, "PACING", pacing ? Color.WHITE : MUTED));
        if (engine != null) engine.setText(dotText(accent, "Engine Active", TXT));
        for (int i = 0; i < rowPills.size() && i < TOGGLES.length; i++) {
            boolean on = Boolean.TRUE.equals(tstate.get(TOGGLES[i][2]));
            TextView st = rowPills.get(i);
            st.setText(dotText(on ? GOOD : BAD, on ? "ON" : "OFF", Color.WHITE));
        }
    }

    private void paintRail() {
        for (int i = 0; i < PAGES; i++) {
            boolean sel = i == page;
            railBtn[i].setBackground(sel ? railSel() : null);
            railIcon[i].setColor(sel ? Color.WHITE : Color.parseColor("#9A9AA2"));
        }
    }

    private Drawable railSel() {
        GradientDrawable g = grad(Color.parseColor("#4A4A50"), Color.parseColor("#2A2A2E"), 16);
        g.setStroke(Math.max(1, dp(1)), alpha(accent, 150));
        return g;
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
        if (i == 2 && !appsLoaded) loadApps();
    }

    private void setMode(final int k, View v) {
        final String key = MODE_KEYS[k];
        final String prevMode = curMode;
        final int prevAcc = accent;
        pulse(v);
        curMode = key;
        animateAccent(accentFor(key));
        runCmd(Root.BIN + " mode " + key, "Mode " + MODE_NAMES[k], new Runnable() { @Override public void run() { } }, new Runnable() {
            @Override public void run() { curMode = prevMode; animateAccent(prevAcc); paintState(); }
        });
    }

    private void toggleFeat(final String id, final String lbl) {
        final boolean to = !Boolean.TRUE.equals(tstate.get(id));
        runCmd(Root.BIN + " feat " + id + " " + (to ? "on" : "off"), lbl + (to ? " aktif" : " mati"),
                new Runnable() { @Override public void run() { tstate.put(id, to); paintState(); loadState(); } });
    }

    private void quick(int k, View v) {
        pulse(v);
        if (k == 0) runCmd(Root.BIN + " ovact killbg", "App latar dihentikan", null);
        else if (k == 1) runCmd(Root.BIN + " ovact clean", "RAM dibersihkan", null);
        else if (k == 2) toggleFeat("dnd", "Jangan ganggu");
        else {
            int cur = 0;
            try { cur = Integer.parseInt(curHz); } catch (Exception ignored) { }
            int next = HZ[0];
            for (int i = 0; i < HZ.length; i++) if (HZ[i] == cur) next = HZ[(i + 1) % HZ.length];
            final int to = next;
            runCmd(Root.BIN + " refresh " + to, to + " Hz", new Runnable() { @Override public void run() { curHz = String.valueOf(to); paintState(); } });
        }
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
                animateAccent(accentFor(curMode));
                paintState();
            } });
        } }).start();
    }

    // ---------- umpan balik ----------
    private void flash(String s, int bgc, int fg, long ms) {
        status.setText(s);
        status.setTextColor(fg);
        status.setBackground(rect(bgc, 12));
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        status.setVisibility(View.VISIBLE);
        ui.removeCallbacks(clearStatus);
        if (ms > 0) ui.postDelayed(clearStatus, ms);
    }
    private final Runnable clearStatus = new Runnable() { @Override public void run() { if (status != null) status.setVisibility(View.GONE); } };

    private void runCmd(String cmd, String okMsg, Runnable onOk) { runCmd(cmd, okMsg, onOk, null); }

    private void runCmd(final String cmd, final String okMsg, final Runnable onOk, final Runnable onFail) {
        flash("⏳ Menerapkan…", Color.parseColor("#3A3320"), WARN, 0);
        new Thread(new Runnable() { @Override public void run() {
            final Root.Res r = Root.exec(cmd);
            final String o = r.out().trim();
            final boolean fail = (r.code != 0 && r.code != 141) || o.startsWith("ERR|");
            ui.post(new Runnable() { @Override public void run() {
                if (fail) {
                    String why = o.startsWith("ERR|") ? o.substring(4) : (r.code == 124 ? "waktu habis" : "kode " + r.code);
                    int nl = why.indexOf('\n'); if (nl > 0) why = why.substring(0, nl);
                    flash("✕ Gagal: " + why, Color.parseColor("#3A2020"), BAD, 3500);
                    if (onFail != null) onFail.run();
                } else {
                    flash("✓ " + okMsg, Color.parseColor("#1E3A2A"), GOOD, 2200);
                    if (onOk != null) onOk.run();
                }
            } });
        } }).start();
    }

    // ---------- Record (resolusi/bitrate/fps dari pengaturan manager) ----------
    private void toggleRecord() {
        if (!recording) {
            recording = true;
            recStart = System.currentTimeMillis();
            flash("● Merekam…", Color.parseColor("#3A2020"), BAD, 2000);
            new Thread(new Runnable() { @Override public void run() {
                final Root.Res r = Root.exec(Root.BIN + " ovrec start", 7200);
                final String o = r.out().trim();
                ui.post(new Runnable() { @Override public void run() {
                    recording = false;
                    recBtn.setText(dotText(BAD, "REC", MUTED));
                    if (o.startsWith("OK|")) {
                        String f = o.substring(3);
                        flash("✓ Tersimpan: " + f.substring(f.lastIndexOf('/') + 1), Color.parseColor("#1E3A2A"), GOOD, 4500);
                    } else {
                        flash("✕ Rekam gagal: " + (o.startsWith("ERR|") ? o.substring(4) : "kode " + r.code), Color.parseColor("#3A2020"), BAD, 4000);
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

        float cmv = parse(cm), gmv = parse(gm);
        cpuMax = Math.max(cpuMax, Math.max(parse(f(a, 12)), cmv));
        gpuMax = Math.max(gpuMax, Math.max(parse(f(a, 13)), gmv));
        if (cmv > 0) gCpu.setData(cpuMax > 0 ? cmv / cpuMax : 0f, String.format(Locale.US, "%.2f", cmv / 1000f), "GHz", "CPU");
        else gCpu.setData(0f, "--", "GHz", "CPU");
        if (gmv > 0) gGpu.setData(gpuMax > 0 ? gmv / gpuMax : 0f, String.format(Locale.US, "%.2f", gmv / 1000f), "GHz", "GPU");
        else gGpu.setData(0f, "--", "GHz", "GPU");

        fpsLine.setText((fps.isEmpty() ? "--" : fps) + " FPS  •  " + (curHz.isEmpty() ? "--" : curHz) + " Hz");
        int fv = parse(fps);
        fpsLine.setTextColor(fps.isEmpty() ? MUTED : (fv >= 55 ? Color.WHITE : fv >= 30 ? WARN : BAD));
        StringBuilder sb = new StringBuilder();
        sb.append("CPU ").append(cpu).append("%");
        if (!ct.isEmpty()) sb.append(" ").append(ct).append("°");
        sb.append(" · GPU ").append(gpu.isEmpty() ? "n/a" : gpu + "%");
        sb.append(" · RAM ").append(ram).append("%");
        if (!ping.isEmpty()) sb.append(" · ").append(ping).append(" ms");
        statLine.setText(sb.toString());
        battTv.setText("🔋 " + bat + "%");
        clockTv.setText(clock.format(new Date()));
        hdrPkg.setText(pkg);
        if (recording) {
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recBtn.setText(dotText((tick % 2 == 0) ? BAD : Color.parseColor("#661515"),
                    String.format(Locale.US, "%02d:%02d", s / 60, s % 60), Color.WHITE));
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
