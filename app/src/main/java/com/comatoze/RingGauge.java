package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Dial bundar: cincin beban (busur 270 derajat) berwarna sesuai mode, tanda skala, cakram gelap, angka di tengah. */
public class RingGauge extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float val;
    private int accent = 0xFF4A90FF;
    private String main = "--", sub = "";

    public RingGauge(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeCap(Paint.Cap.ROUND);
        track.setStyle(Paint.Style.STROKE); track.setStrokeCap(Paint.Cap.ROUND); track.setColor(0xFF23262D);
        tick.setStyle(Paint.Style.STROKE); tick.setColor(0x55FFFFFF);
        tMain.setColor(0xFFFFFFFF); tMain.setTextAlign(Paint.Align.CENTER);
        tMain.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tSub.setColor(0xFF8E95A3); tSub.setTextAlign(Paint.Align.CENTER);
        tSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setAccent(int a) { accent = a; invalidate(); }

    public void set(float pct, String m, String s) {
        val = Math.max(0f, Math.min(100f, pct));
        main = m; sub = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float sw = s * 0.075f, rad = s / 2f - sw * 1.6f;
        // cakram gelap
        disc.setShader(new RadialGradient(cx, cy, rad, 0xFF1B1E26, 0xFF08090C, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, rad - sw * 0.4f, disc);
        // jalur + busur beban (bercahaya)
        track.setStrokeWidth(sw);
        ring.setStrokeWidth(sw);
        ring.setColor(accent);
        ring.setShadowLayer(sw * 1.2f, 0, 0, accent);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        cv.drawArc(r, 135, 270, false, track);
        if (val > 0.5f) cv.drawArc(r, 135, 270f * val / 100f, false, ring);
        // tanda skala
        tick.setStrokeWidth(Math.max(1f, s * 0.012f));
        float r1 = rad - sw * 1.1f, r2 = rad - sw * 1.9f;
        for (int i = 0; i <= 18; i++) {
            double a = Math.toRadians(135 + 270.0 * i / 18);
            cv.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
        }
        tMain.setTextSize(s * 0.22f);
        tSub.setTextSize(s * 0.105f);
        cv.drawText(main, cx, cy + s * 0.05f, tMain);
        cv.drawText(sub, cx, cy + s * 0.2f, tSub);
    }
}
