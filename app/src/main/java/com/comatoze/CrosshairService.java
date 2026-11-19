package com.comatoze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.WindowManager;

/** Menampilkan crosshair layar penuh yang tidak bisa disentuh. Pengaturan dibaca dari `ultrapower crosshair` (CH|...). */
public class CrosshairService extends Service {
    private WindowManager wm;
    private CrosshairView view;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("ch", "Crosshair Comatoze", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "ch")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Crosshair aktif")
                .setContentText("Matikan dari Comatoze › Crosshair")
                .setOngoing(true)
                .build();
        startForeground(3, n);
        new Thread(new Runnable() { @Override public void run() {
            final String out = Root.sh(Root.BIN + " crosshair");
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() { @Override public void run() { show(out); } });
        } }).start();
        return START_NOT_STICKY;
    }

    private static float num(String[] a, int i, float def) {
        try { return Float.parseFloat(a[i].trim()); } catch (Exception e) { return def; }
    }

    private void show(String out) {
        // CH|on|style|color|size|th|gap|op|glow|spd|ox|oy|out|dot
        int k = out.indexOf("CH|");
        if (k < 0) { stopSelf(); return; }
        String[] a = out.substring(k).split("\\|");
        if (a.length < 14 || !"on".equals(a[1])) { stopSelf(); return; }
        try {
            if (wm == null) wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            boolean fresh = view == null;
            if (fresh) view = new CrosshairView(this);
            view.style = a[2];
            try { view.color = Color.parseColor("#" + a[3].trim()); } catch (Exception e) { view.color = 0xFFFF3B30; }
            view.size = num(a, 4, 30); view.thick = num(a, 5, 2); view.gap = num(a, 6, 6);
            view.opacity = num(a, 7, 90) / 100f; view.glow = num(a, 8, 50) / 100f; view.speed = num(a, 9, 1);
            view.ox = num(a, 10, 0); view.oy = num(a, 11, 0);
            view.outline = "1".equals(a[12].trim()); view.dot = "1".equals(a[13].trim());
            view.apply();
            if (fresh) {
                WindowManager.LayoutParams l = new WindowManager.LayoutParams(-1, -1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                wm.addView(view, l);
            }
        } catch (Throwable t) { stopSelf(); }
    }

    @Override
    public void onDestroy() {
        if (view != null) {
            view.stop();
            try { wm.removeView(view); } catch (Exception ignored) { }
            view = null;
        }
        super.onDestroy();
    }
}
