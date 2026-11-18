package com.comatoze;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Bentuk "HUD" bersudut: sudut dipotong (chamfer) atau jajar genjang (skew), isi gradien, garis tepi + glow. */
public class HudShape extends Drawable {
    public static final int TL = 1, TR = 2, BR = 4, BL = 8;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int top, bottom, stroke, corners, glowColor;
    private float strokeW, cut, skew;
    private boolean glow;
    private int alpha = 255;

    public HudShape(int top, int bottom, int stroke, float strokeW, int corners, float cut) {
        this.top = top; this.bottom = bottom; this.stroke = stroke; this.strokeW = strokeW;
        this.corners = corners; this.cut = cut;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.MITER);
    }

    public void setStrokeColor(int c) { stroke = c; glowColor = c; invalidateSelf(); }

    public HudShape skew(float s) { skew = s; return this; }
    public HudShape glow(boolean g, int color) { glow = g; glowColor = color; return this; }

    @Override
    protected void onBoundsChange(Rect b) {
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
        build(b);
    }

    private void build(Rect b) {
        float h = strokeW / 2f;
        float l = b.left + h, t = b.top + h, r = b.right - h, bt = b.bottom - h;
        path.reset();
        if (skew > 0) {
            path.moveTo(l + skew, t);
            path.lineTo(r, t);
            path.lineTo(r - skew, bt);
            path.lineTo(l, bt);
            path.close();
            return;
        }
        float c = cut;
        path.moveTo((corners & TL) != 0 ? l + c : l, t);
        if ((corners & TR) != 0) { path.lineTo(r - c, t); path.lineTo(r, t + c); } else path.lineTo(r, t);
        if ((corners & BR) != 0) { path.lineTo(r, bt - c); path.lineTo(r - c, bt); } else path.lineTo(r, bt);
        if ((corners & BL) != 0) { path.lineTo(l + c, bt); path.lineTo(l, bt - c); } else path.lineTo(l, bt);
        if ((corners & TL) != 0) path.lineTo(l, t + c); else path.lineTo(l, t);
        path.close();
    }

    @Override
    public void draw(Canvas cv) {
        fill.setAlpha(alpha);
        cv.drawPath(path, fill);
        if (strokeW > 0) {
            if (glow) {
                line.setStrokeWidth(strokeW * 3.2f);
                line.setColor(glowColor);
                line.setAlpha((int) (alpha * 0.22f));
                cv.drawPath(path, line);
            }
            line.setStrokeWidth(strokeW);
            line.setColor(stroke);
            line.setAlpha(alpha);
            cv.drawPath(path, line);
        }
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
