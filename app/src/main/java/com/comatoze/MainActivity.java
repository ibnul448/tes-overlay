package com.comatoze;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manager Comatoze: WebView yang memuat webroot modul (index.html) langsung dari /data/adb/modules/ultrapower/webroot
 * lewat root, dengan jembatan `ksu` yang kompatibel (exec + getPackagesInfo + ksu://icon/<paket>).
 * Jadi tidak perlu lagi KsuWebUI.
 */
public class MainActivity extends Activity {
    private static final String HOST = "comatoze.local";
    private WebView wv;
    private TextView msg;
    private FrameLayout rootView;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.parseColor("#101412"));
        setContentView(rootView);

        msg = new TextView(this);
        msg.setTextColor(Color.parseColor("#C8E6D0"));
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(dp(32), dp(32), dp(32), dp(32));
        msg.setText("Memeriksa akses root...");
        rootView.addView(msg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pool.execute(new Runnable() {
            @Override public void run() {
                final boolean root = Root.available();
                final boolean mod = root && Root.exec("test -f " + Root.MOD + "/webroot/index.html").code == 0;
                if (root && mod) Root.exec(Root.BIN + " overlay perm");
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!root) msg.setText("Aplikasi ini butuh akses root.\n\nBerikan izin Superuser ke Comatoze (KernelSU/Magisk) lalu buka lagi.");
                        else if (!mod) msg.setText("Modul Comatoze belum terpasang / belum aktif.\n\nFlash modulnya lalu reboot.");
                        else startWeb();
                    }
                });
            }
        });
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void startWeb() {
        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        wv.setBackgroundColor(Color.TRANSPARENT);
        wv.addJavascriptInterface(new Bridge(), "ksu");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return serve(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc) || "tg".equals(sc)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                }
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                bars();
                ui.postDelayed(new Runnable() { @Override public void run() { bars(); } }, 700);
            }
        });
        rootView.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        msg.setVisibility(View.GONE);
        wv.loadUrl("https://" + HOST + "/index.html");
    }

    /** Warna status bar / nav bar mengikuti latar halaman (terang/gelap). */
    private void bars() {
        if (wv == null) return;
        wv.evaluateJavascript("getComputedStyle(document.body).backgroundColor", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (v == null) return;
                Matcher m = Pattern.compile("(\\d+)\\D+(\\d+)\\D+(\\d+)").matcher(v);
                if (!m.find()) return;
                int r = Integer.parseInt(m.group(1)), g = Integer.parseInt(m.group(2)), bl = Integer.parseInt(m.group(3));
                int c = Color.rgb(r, g, bl);
                boolean light = (0.299 * r + 0.587 * g + 0.114 * bl) > 150;
                getWindow().setStatusBarColor(c);
                getWindow().setNavigationBarColor(c);
                int f = getWindow().getDecorView().getSystemUiVisibility();
                if (light) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        });
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
    }

    private WebResourceResponse serve(Uri u) {
        try {
            String sc = u.getScheme();
            if ("ksu".equals(sc) && "icon".equals(u.getHost())) {
                String pkg = u.getLastPathSegment();
                if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return notFound();
                byte[] png = icon(pkg);
                if (png == null) return notFound();
                return new WebResourceResponse("image/png", null, new ByteArrayInputStream(png));
            }
            if ("https".equals(sc) && HOST.equals(u.getHost())) {
                String p = u.getPath();
                if (p == null || p.equals("/") || p.isEmpty()) p = "/index.html";
                if (p.contains("..") || !p.matches("/[A-Za-z0-9_./-]+")) return notFound();
                Root.Res r = Root.exec("cat " + Root.MOD + "/webroot" + p);
                if (r.code != 0 || r.raw.length == 0) return notFound();
                return new WebResourceResponse(mime(p), "utf-8", new ByteArrayInputStream(r.raw));
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private byte[] icon(String pkg) {
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            int sz = 96;
            Bitmap bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, sz, sz);
            d.draw(c);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, bo);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Jembatan kompatibel KernelSU WebUI: ksu.exec(cmd, optsJson, callbackName) + ksu.getPackagesInfo(json). */
    private final class Bridge {
        @JavascriptInterface
        public void exec(final String cmd, String opts, final String cb) {
            if (cb == null || !cb.matches("[A-Za-z0-9_]+")) return;
            pool.execute(new Runnable() {
                @Override public void run() {
                    Root.Res r = Root.exec(cmd);
                    final String js = cb + "(" + r.code + "," + JSONObject.quote(r.out()) + "," + JSONObject.quote(r.err) + ")";
                    ui.post(new Runnable() { @Override public void run() { if (wv != null) wv.evaluateJavascript(js, null); } });
                }
            });
        }

        @JavascriptInterface
        public String getPackagesInfo(String json) {
            JSONArray out = new JSONArray();
            try {
                JSONArray in = new JSONArray(json);
                PackageManager pm = getPackageManager();
                for (int i = 0; i < in.length(); i++) {
                    String p = in.getString(i);
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                        JSONObject o = new JSONObject();
                        o.put("packageName", p);
                        o.put("appLabel", String.valueOf(pm.getApplicationLabel(ai)));
                        out.put(o);
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
            return out.toString();
        }

        @JavascriptInterface
        public void toast(final String t) {
            ui.post(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, t, Toast.LENGTH_SHORT).show(); } });
        }

        @JavascriptInterface
        public void fullScreen(boolean on) { }
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        pool.shutdown();
        if (wv != null) { wv.destroy(); wv = null; }
        super.onDestroy();
    }
}
