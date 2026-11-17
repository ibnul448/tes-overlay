package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Gauge melingkar gelap (busur 270°) dengan angka di tengah; warna busur mengikuti aksen mode. */
public class GaugeView extends View {
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ptr = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tv = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path tri = new Path();
    private float frac = 0f;
    private int accent = Color.parseColor("#4DA3FF");
    private String value = "--", unit = "", label = "";

    public GaugeView(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Color.parseColor("#232327"));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(Color.parseColor("#6A6A70"));
        tick.setStrokeCap(Paint.Cap.ROUND);
        ptr.setStyle(Paint.Style.FILL);
        ptr.setColor(Color.WHITE);
        tv.setColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tu.setColor(Color.WHITE);
        tu.setTypeface(Typeface.DEFAULT_BOLD);
        tl.setColor(Color.WHITE);
        tl.setTypeface(Typeface.DEFAULT_BOLD);
        tl.setTextAlign(Paint.Align.CENTER);
    }

    public void setAccent(int c) { accent = c; invalidate(); }

    public void setData(float fraction, String v, String u, String l) {
        frac = Math.max(0f, Math.min(1f, fraction));
        value = v; unit = u; label = l;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        if (s <= 0) s = Math.max(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = s / 2f - s * 0.02f;

        disc.setShader(new RadialGradient(cx, cy - r * 0.25f, r * 1.1f,
                new int[]{Color.parseColor("#34343A"), Color.parseColor("#0B0B0D")}, null, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, disc);

        float sw = s * 0.075f;
        float ar = r * 0.84f;
        box.set(cx - ar, cy - ar, cx + ar, cy + ar);
        track.setStrokeWidth(sw);
        cv.drawArc(box, 135f, 270f, false, track);

        if (frac > 0.005f) {
            glow.setStrokeWidth(sw * 1.9f);
            glow.setColor(accent);
            glow.setAlpha(55);
            cv.drawArc(box, 135f, 270f * frac, false, glow);
            arc.setStrokeWidth(sw);
            arc.setColor(accent);
            cv.drawArc(box, 135f, 270f * frac, false, arc);
        }

        // garis skala di dalam busur
        tick.setStrokeWidth(Math.max(1f, s * 0.012f));
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(135 + 27 * i);
            float r1 = r * 0.60f, r2 = r * (i % 5 == 0 ? 0.70f : 0.66f);
            cv.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
        }

        // penunjuk segitiga di ujung busur
        double pa = Math.toRadians(135 + 270 * frac);
        float tip = r * 0.74f, base = r * 0.93f, half = s * 0.035f;
        float tx = cx + (float) Math.cos(pa) * tip, ty = cy + (float) Math.sin(pa) * tip;
        float bx = cx + (float) Math.cos(pa) * base, by = cy + (float) Math.sin(pa) * base;
        float nx = -(float) Math.sin(pa), ny = (float) Math.cos(pa);
        tri.reset();
        tri.moveTo(tx, ty);
        tri.lineTo(bx + nx * half, by + ny * half);
        tri.lineTo(bx - nx * half, by - ny * half);
        tri.close();
        cv.drawPath(tri, ptr);

        // teks tengah
        tv.setTextSize(s * 0.20f);
        tu.setTextSize(s * 0.105f);
        tl.setTextSize(s * 0.13f);
        float vw = tv.measureText(value), uw = tu.measureText(unit);
        float gap = unit.isEmpty() ? 0 : s * 0.015f;
        float x0 = cx - (vw + gap + uw) / 2f;
        float by0 = cy + s * 0.015f;
        cv.drawText(value, x0, by0, tv);
        if (!unit.isEmpty()) cv.drawText(unit, x0 + vw + gap, by0, tu);
        cv.drawText(label, cx, cy + s * 0.17f, tl);
    }
}
