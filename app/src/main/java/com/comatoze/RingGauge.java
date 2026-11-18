package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Gauge cincin (busur 270 derajat) dengan angka di tengah. */
public class RingGauge extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float val;
    private String main = "--", sub = "";

    public RingGauge(Context c) {
        super(c);
        bg.setStyle(Paint.Style.STROKE); bg.setStrokeCap(Paint.Cap.ROUND); bg.setColor(0xFF2A2D33);
        fg.setStyle(Paint.Style.STROKE); fg.setStrokeCap(Paint.Cap.ROUND); fg.setColor(0xFF4A90FF);
        tMain.setColor(0xFFFFFFFF); tMain.setTextAlign(Paint.Align.CENTER); tMain.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tSub.setColor(0xFF8E95A3); tSub.setTextAlign(Paint.Align.CENTER); tSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setAccent(int a) { fg.setColor(a); invalidate(); }

    public void set(float pct, String m, String s) {
        val = Math.max(0f, Math.min(100f, pct));
        main = m; sub = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float sw = s * 0.09f;
        bg.setStrokeWidth(sw); fg.setStrokeWidth(sw);
        float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = s / 2f - sw;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        cv.drawArc(r, 135, 270, false, bg);
        if (val > 0.5f) cv.drawArc(r, 135, 270f * val / 100f, false, fg);
        tMain.setTextSize(s * 0.24f);
        tSub.setTextSize(s * 0.115f);
        cv.drawText(main, cx, cy + s * 0.04f, tMain);
        cv.drawText(sub, cx, cy + s * 0.22f, tSub);
    }
}
