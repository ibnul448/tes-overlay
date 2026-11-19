package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Dial modern: cincin tick bergradasi (tick menyala sesuai beban, ujungnya lebih terang), cakram kaca gelap di tengah dengan
 * angka besar + label, cahaya lembut di belakang. Tanpa jarum/garis tebal supaya terasa halus, bukan kaku.
 */
public class RingGauge extends View {
    private static final int N = 54;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tUnit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float val;
    private int accent = 0xFF3B9BFF, accent2 = 0xFF2EDCFF;
    private String main = "--", unit = "%", label = "";

    public RingGauge(Context c) {
        super(c);
        tMain.setColor(0xFFFFFFFF);
        tMain.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tUnit.setColor(0xFFC9D3E3);
        tUnit.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
        tSub.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tSub.setTextAlign(Paint.Align.CENTER);
    }

    public void setAccent(int a, int b) { accent = a; accent2 = b; invalidate(); }
    public void setAccent(int a) { setAccent(a, a); }
    public void setSide(int s) { }   // dipertahankan agar kompatibel; garis kecepatan sudah dihapus

    /** pct 0..100; text = angka tampil (mis. "27"), unit = "%" atau "" bila tidak tersedia. */
    public void set(float pct, String text, String unit, String label) {
        val = Math.max(0f, Math.min(100f, pct));
        main = text; this.unit = unit; this.label = label;
        invalidate();
    }

    private static int al(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }

    private static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;

        // cahaya lembut
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, s * 0.52f, new int[]{al(accent, 70), al(accent2, 34), al(accent, 0)}, new float[]{0.45f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.52f, p);
        p.setShader(null);

        // cincin tick
        float v = val;
        int active = Math.round(N * v / 100f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1.6f, s * 0.026f));
        for (int i = 0; i < N; i++) {
            double ang = Math.toRadians(135.0 + 270.0 * i / (N - 1));
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            boolean on = i < active, head = on && i == active - 1;
            float r1 = s * 0.455f, r2 = s * (head ? 0.355f : 0.385f);
            if (on) p.setColor(mix(accent, accent2, i / (float) (N - 1)));
            else p.setColor(0x22FFFFFF);
            if (head) p.setColor(0xFFFFFFFF);
            cv.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * r2, cy + sa * r2, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);

        // cakram kaca
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy - s * 0.08f, s * 0.34f, 0xFF1D2735, 0xFF0A0E14, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, s * 0.315f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.010f));
        p.setColor(al(accent2, 90));
        cv.drawCircle(cx, cy, s * 0.315f, p);
        // kilap kaca di sisi atas
        r.set(cx - s * 0.285f, cy - s * 0.285f, cx + s * 0.285f, cy + s * 0.285f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0x2EFFFFFF);
        cv.drawArc(r, 205, 80, false, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // angka + label
        p.setStyle(Paint.Style.FILL);
        tMain.setTextSize(s * 0.18f);
        tUnit.setTextSize(s * 0.082f);
        float wm = tMain.measureText(main), wu = unit.isEmpty() ? 0 : tUnit.measureText(unit);
        float x0 = cx - (wm + wu) / 2f, y = cy + s * 0.045f;
        cv.drawText(main, x0, y, tMain);
        if (wu > 0) cv.drawText(unit, x0 + wm + s * 0.005f, y, tUnit);
        tSub.setColor(al(accent2, 235));
        tSub.setLetterSpacing(0.16f);
        tSub.setTextSize(s * 0.068f);
        cv.drawText(label, cx, cy + s * 0.16f, tSub);
    }
}
