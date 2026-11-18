package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Grafik riwayat FPS 40 detik terakhir (area + garis) dengan warna aksen mode. */
public class FpsGraph extends View {
    private static final int N = 40;
    private final float[] buf = new float[N];
    private int count = 0;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pl = new Path();
    private final Path pa = new Path();
    private int accent = Color.parseColor("#2EA8FF");

    public FpsGraph(Context c) {
        super(c);
        bg.setColor(Color.parseColor("#0A0C11"));
        grid.setColor(Color.parseColor("#1B202C"));
        grid.setStyle(Paint.Style.STROKE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        txt.setColor(Color.parseColor("#6B7385"));
        txt.setTypeface(android.graphics.Typeface.MONOSPACE);
    }

    public void setAccent(int a) { accent = a; invalidate(); }

    public void push(float v) {
        if (count < N) buf[count++] = v;
        else { System.arraycopy(buf, 1, buf, 0, N - 1); buf[N - 1] = v; }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        cv.drawRect(0, 0, w, h, bg);
        grid.setStrokeWidth(Math.max(1f, h * 0.012f));
        for (int i = 1; i < 4; i++) cv.drawLine(0, h * i / 4f, w, h * i / 4f, grid);
        if (count < 2) return;
        float mx = 60f;
        for (int i = 0; i < count; i++) mx = Math.max(mx, buf[i]);
        mx = (float) Math.ceil(mx / 30f) * 30f;
        float pad = h * 0.08f, ih = h - pad * 2, step = w / (N - 1f);
        float x0 = w - (count - 1) * step;
        pl.reset(); pa.reset();
        for (int i = 0; i < count; i++) {
            float x = x0 + i * step, y = pad + ih * (1f - buf[i] / mx);
            if (i == 0) { pl.moveTo(x, y); pa.moveTo(x, h); pa.lineTo(x, y); } else { pl.lineTo(x, y); pa.lineTo(x, y); }
        }
        pa.lineTo(w, h);
        pa.close();
        area.setShader(new LinearGradient(0, 0, 0, h, (accent & 0x00FFFFFF) | 0x80000000, accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawPath(pa, area);
        line.setColor(accent);
        line.setStrokeWidth(Math.max(1.5f, h * 0.03f));
        cv.drawPath(pl, line);
        float lx = x0 + (count - 1) * step, ly = pad + ih * (1f - buf[count - 1] / mx);
        dot.setColor(Color.WHITE);
        cv.drawCircle(lx - 1f, ly, Math.max(2f, h * 0.045f), dot);
        txt.setTextSize(h * 0.15f);
        cv.drawText(String.valueOf((int) mx), 4f, h * 0.17f, txt);
    }
}
