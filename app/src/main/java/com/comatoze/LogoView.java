package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/** Logo lambang Comatoze: cincin gradasi terbuka + titik inti, warna mengikuti mode. */
public class LogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int a1 = 0xFF2F8CFF, a2 = 0xFF25D0F0;

    public LogoView(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); }

    public void setColors(int c1, int c2) { a1 = c1; a2 = c2; invalidate(); }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.17f);
        p.setShader(new SweepGradient(cx, cy, new int[]{a1, a2, 0xFFFFFFFF, a2, a1}, null));
        p.setShadowLayer(s * 0.08f, 0, 0, a1);
        r.set(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f);
        cv.drawArc(r, -60, 300, false, p);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new SweepGradient(cx, cy, new int[]{a2, a1, a2}, null));
        cv.drawCircle(cx, cy, s * 0.13f, p);
        p.setShader(null);
    }
}
