package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Ikon sederhana untuk rail overlay: 0 = gauge, 1 = slider/tweaks, 2 = kotak-kotak (apps). */
public class IconView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int type;
    private int color = 0xFFB8B8BE;

    public IconView(Context c, int type) {
        super(c);
        this.type = type;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColor(int c) { color = c; invalidate(); }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, u = s * 0.30f;
        p.setColor(color);
        p.setStrokeWidth(s * 0.07f);
        if (type == 0) {
            r.set(cx - u, cy - u, cx + u, cy + u);
            cv.drawArc(r, 150f, 240f, false, p);
            cv.drawLine(cx, cy + u * 0.1f, cx + u * 0.55f, cy - u * 0.5f, p);
            cv.drawLine(cx - u * 0.6f, cy + u * 0.95f, cx + u * 0.6f, cy + u * 0.95f, p);
        } else if (type == 1) {
            for (int i = 0; i < 3; i++) {
                float y = cy - u + i * u;
                cv.drawLine(cx - u, y, cx + u, y, p);
                float kx = cx + (i == 1 ? u * 0.45f : -u * 0.35f);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(kx, y, s * 0.075f, p);
                p.setStyle(Paint.Style.STROKE);
            }
        } else {
            float g = u * 0.18f, b = u * 0.95f;
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                float x0 = cx - b + i * (b + g), y0 = cy - b + j * (b + g);
                r.set(x0, y0, x0 + b - g, y0 + b - g);
                cv.drawRoundRect(r, s * 0.04f, s * 0.04f, p);
            }
        }
    }
}
