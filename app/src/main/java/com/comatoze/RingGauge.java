package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Dial bercahaya ala panel game: halo, dua cincin luar, cakram gelap, busur beban, cincin dalam dengan angka %,
 * jarum runcing di bawah, dan garis kecepatan memudar ke arah dial pasangannya.
 */
public class RingGauge extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tUnit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private float val;
    private int accent = 0xFF2F8CFF;
    private int side = 0;     // 1 = garis ke kanan, -1 = garis ke kiri, 0 = tanpa garis
    private String main = "--", unit = "%", label = "";

    public RingGauge(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        tMain.setColor(0xFFFFFFFF);
        tMain.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tUnit.setColor(0xFFFFFFFF);
        tUnit.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tSub.setColor(0xFFB4BCCB); tSub.setTextAlign(Paint.Align.CENTER);
        tSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setAccent(int a) { accent = a; invalidate(); }
    public void setSide(int s) { side = s; invalidate(); }

    /** pct 0..100; text = angka tampil (mis. "27"), unit = "%" atau "" bila tidak tersedia. */
    public void set(float pct, String text, String unit, String label) {
        val = Math.max(0f, Math.min(100f, pct));
        main = text; this.unit = unit; this.label = label;
        invalidate();
    }

    private static int al(int c, int a) { return (a << 24) | (c & 0xFFFFFF); }

    private static int dark(int c, float f) {
        int rr = (int) (((c >> 16) & 0xFF) * f), gg = (int) (((c >> 8) & 0xFF) * f), bb = (int) ((c & 0xFF) * f);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        // garis kecepatan di sisi dalam (ke arah dial pasangan)
        if (side != 0) {
            float x0 = cx + side * s * 0.46f, x1 = side > 0 ? getWidth() : 0;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.008f));
            for (int i = -4; i <= 4; i++) {
                float y = cy + i * s * 0.065f;
                float ex = x1 - side * Math.abs(i) * s * 0.03f;
                p.setShader(new LinearGradient(x0, 0, ex, 0, al(accent, 120 - Math.abs(i) * 14), al(accent, 0), Shader.TileMode.CLAMP));
                cv.drawLine(x0, y, ex, y, p);
            }
            p.setShader(null);
        }
        // halo
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, s * 0.52f, new int[]{al(accent, 0), al(accent, 105), al(accent, 55), al(accent, 0)}, new float[]{0.5f, 0.72f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.52f, p);
        p.setShader(null);
        // dua cincin luar
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, s * 0.026f));
        p.setColor(al(accent, 235));
        p.setShadowLayer(s * 0.04f, 0, 0, accent);
        cv.drawCircle(cx, cy, s * 0.455f, p);
        p.clearShadowLayer();
        p.setStrokeWidth(Math.max(1f, s * 0.011f));
        p.setColor(al(accent, 150));
        cv.drawCircle(cx, cy, s * 0.405f, p);
        // cakram gelap
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, s * 0.4f, dark(accent, 0.28f), 0xFF05080C, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.395f, p);
        p.setShader(null);
        // busur beban
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.045f);
        r.set(cx - s * 0.335f, cy - s * 0.335f, cx + s * 0.335f, cy + s * 0.335f);
        p.setColor(al(accent, 45));
        cv.drawArc(r, 135, 270, false, p);
        if (val > 0.5f) {
            p.setColor(accent);
            p.setShadowLayer(s * 0.035f, 0, 0, accent);
            cv.drawArc(r, 135, 270f * val / 100f, false, p);
            p.clearShadowLayer();
        }
        // cincin dalam + cakram dalam
        p.setStrokeWidth(Math.max(1f, s * 0.013f));
        p.setColor(al(accent, 210));
        cv.drawCircle(cx, cy, s * 0.255f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF0070B11);
        cv.drawCircle(cx, cy, s * 0.248f, p);
        // jarum runcing di bawah (tinggi mengikuti beban)
        float base = cy + s * 0.245f, len = s * (0.13f + 0.0011f * val), hw = s * 0.02f;
        path.reset();
        path.moveTo(cx - hw, base); path.lineTo(cx, base - len); path.lineTo(cx + hw, base); path.close();
        p.setColor(accent);
        p.setShadowLayer(s * 0.03f, 0, 0, accent);
        cv.drawPath(path, p);
        p.clearShadowLayer();
        // angka
        tMain.setTextSize(s * 0.17f);
        tUnit.setTextSize(s * 0.085f);
        float wm = tMain.measureText(main), wu = unit.isEmpty() ? 0 : tUnit.measureText(unit);
        float x0 = cx - (wm + wu) / 2f, y = cy - s * 0.005f;
        cv.drawText(main, x0, y, tMain);
        if (wu > 0) cv.drawText(unit, x0 + wm, y, tUnit);
        tSub.setTextSize(s * 0.072f);
        cv.drawText(label, cx, cy + s * 0.085f, tSub);
    }
}
