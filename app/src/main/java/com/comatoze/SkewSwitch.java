package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Saklar jajar genjang: OFF = blok putih di kiri, ON = blok warna aksen (gradasi) di kiri + putih di kanan. */
public class SkewSwitch extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean on;
    private int accent = 0xFF4A90FF;

    public SkewSwitch(Context c) { super(c); }

    public void setState(boolean o, int a) { on = o; accent = a; invalidate(); }

    private void para(float x0, float x1, float h, float k) {
        path.reset();
        path.moveTo(x0 + k, 0); path.lineTo(x1, 0); path.lineTo(x1 - k, h); path.lineTo(x0, h); path.close();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), k = h * 0.45f, mid = w * 0.5f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFF2B2E36);
        para(0, w, h, k); cv.drawPath(path, p);
        float in = h * 0.12f;
        if (on) {
            p.setShader(new LinearGradient(0, 0, mid, 0, 0xFFFFB04D, accent, Shader.TileMode.CLAMP));
            para(in, mid + k * 0.3f, h - 0f, k); cv.drawPath(path, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            para(mid + k * 0.3f + in, w - in, h, k); cv.drawPath(path, p);
        } else {
            p.setColor(0xFFFFFFFF);
            para(in, mid, h, k); cv.drawPath(path, p);
        }
    }
}
