package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Dial bercahaya: halo, dua cincin tipis, cakram gelap, busur beban, jarum di bawah, angka % miring di tengah. */
public class RingGauge extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tUnit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private float val;
    private int accent = 0xFF4A90FF;
    private String main = "--", unit = "%", label = "";

    public RingGauge(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        tMain.setColor(0xFFFFFFFF);
        tMain.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tUnit.setColor(0xFFFFFFFF);
        tUnit.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tSub.setColor(0xFF9AA3B2); tSub.setTextAlign(Paint.Align.CENTER);
        tSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setAccent(int a) { accent = a; invalidate(); }

    /** pct 0..100; text = angka tampil (mis. "27"), unit = "%" atau "" bila tidak tersedia. */
    public void set(float pct, String text, String unit, String label) {
        val = Math.max(0f, Math.min(100f, pct));
        main = text; this.unit = unit; this.label = label;
        invalidate();
    }

    private static int al(int c, int a) { return (a << 24) | (c & 0xFFFFFF); }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        // halo
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, s * 0.5f, new int[]{al(accent, 120), al(accent, 50), al(accent, 0)}, new float[]{0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.5f, p);
        p.setShader(null);
        // cincin luar tipis
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.012f));
        p.setColor(al(accent, 210));
        cv.drawCircle(cx, cy, s * 0.43f, p);
        p.setColor(al(accent, 110));
        cv.drawCircle(cx, cy, s * 0.395f, p);
        // cakram gelap
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, s * 0.36f, 0xFF18222E, 0xFF070A0F, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.36f, p);
        p.setShader(null);
        // busur beban
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.035f);
        r.set(cx - s * 0.33f, cy - s * 0.33f, cx + s * 0.33f, cy + s * 0.33f);
        p.setColor(0xFF1E2530);
        cv.drawArc(r, 135, 270, false, p);
        if (val > 0.5f) {
            p.setColor(accent);
            p.setShadowLayer(s * 0.03f, 0, 0, accent);
            cv.drawArc(r, 135, 270f * val / 100f, false, p);
            p.clearShadowLayer();
        }
        // jarum di bawah (tinggi mengikuti beban)
        float base = cy + s * 0.3f, len = s * (0.13f + 0.0017f * val), hw = s * 0.022f;
        path.reset();
        path.moveTo(cx - hw, base); path.lineTo(cx, base - len); path.lineTo(cx + hw, base); path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        p.setShadowLayer(s * 0.03f, 0, 0, accent);
        cv.drawPath(path, p);
        p.clearShadowLayer();
        // angka
        tMain.setTextSize(s * 0.19f);
        tUnit.setTextSize(s * 0.095f);
        float wm = tMain.measureText(main), wu = unit.isEmpty() ? 0 : tUnit.measureText(unit);
        float x0 = cx - (wm + wu) / 2f, y = cy - s * 0.02f;
        cv.drawText(main, x0, y, tMain);
        if (wu > 0) cv.drawText(unit, x0 + wm, y, tUnit);
        tSub.setTextSize(s * 0.085f);
        cv.drawText(label, cx, cy + s * 0.12f, tSub);
    }
}
