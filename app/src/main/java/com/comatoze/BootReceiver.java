package com.comatoze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menyalakan notifikasi status begitu HP selesai boot (tanpa harus membuka aplikasi). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try { c.startForegroundService(new Intent(c, StatusService.class)); } catch (Throwable ignored) { }
    }
}
