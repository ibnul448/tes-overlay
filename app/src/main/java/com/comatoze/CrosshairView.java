package com.comatoze;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Crosshair kustom. Gaya statis: dot, cross, crossdot, tcross, x, circle, brackets, chevron, diamond.
 * Animasi: pulse (cincin berdenyut), spin (reticle berputar). Size = radius (px dp), glow = lapisan stroke (bukan blur).
 */
public class CrosshairView extends View {
    public String style = "cross";
    public int color = 0xFFFF3B30;
    public float size = 30, thick = 2f, gap = 6, opacity = 0.9f, glow = 0.5f, speed = 1f, ox = 0, oy = 0;
    public boolean outline = true, dot = false;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private float phase;           // 0..1 berulang
    private ValueAnimator anim;

    public CrosshairView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    public void apply() {
        boolean animated = "pulse".equals(style) || "spin".equals(style);
        if (animated && anim == null) {
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1800);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { phase = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        } else if (!animated && anim != null) { anim.cancel(); anim = null; }
        if (anim != null) anim.setDuration((long) (1800 / Math.max(0.3f, speed)));
        invalidate();
    }

    public void stop() { if (anim != null) { anim.cancel(); anim = null; } }

    private int al(int c, float a) { return (Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24) | (c & 0xFFFFFF); }

    /** Satu lapis gambar dengan warna/lebar tertentu. */
    private void layer(Canvas c, float cx, float cy, int col, float w) {
        p.setColor(col);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        float r = size * d, g = Math.min(gap * d, r - 1f);
        switch (style) {
            case "dot":
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, Math.max(w * 1.6f, 2f * d), p);
                break;
            case "cross": case "crossdot":
                c.drawLine(cx - r, cy, cx - g, cy, p); c.drawLine(cx + g, cy, cx + r, cy, p);
                c.drawLine(cx, cy - r, cx, cy - g, p); c.drawLine(cx, cy + g, cx, cy + r, p);
                break;
            case "tcross":
                c.drawLine(cx - r, cy, cx - g, cy, p); c.drawLine(cx + g, cy, cx + r, cy, p);
                c.drawLine(cx, cy + g, cx, cy + r, p);
                break;
            case "x": {
                float a = r * 0.7071f, b = g * 0.7071f;
                c.drawLine(cx - a, cy - a, cx - b, cy - b, p); c.drawLine(cx + b, cy + b, cx + a, cy + a, p);
                c.drawLine(cx + a, cy - a, cx + b, cy - b, p); c.drawLine(cx - b, cy + b, cx - a, cy + a, p);
                break; }
            case "circle":
                c.drawCircle(cx, cy, r * 0.6f, p);
                break;
            case "brackets": {
                float q = r * 0.8f, l = r * 0.4f;
                c.drawLine(cx - q, cy - q, cx - q + l, cy - q, p); c.drawLine(cx - q, cy - q, cx - q, cy - q + l, p);
                c.drawLine(cx + q, cy - q, cx + q - l, cy - q, p); c.drawLine(cx + q, cy - q, cx + q, cy - q + l, p);
                c.drawLine(cx - q, cy + q, cx - q + l, cy + q, p); c.drawLine(cx - q, cy + q, cx - q, cy + q - l, p);
                c.drawLine(cx + q, cy + q, cx + q - l, cy + q, p); c.drawLine(cx + q, cy + q, cx + q, cy + q - l, p);
                break; }
            case "chevron":
                path.reset(); path.moveTo(cx - r * 0.7f, cy + r * 0.5f); path.lineTo(cx, cy - g * 0.2f); path.lineTo(cx + r * 0.7f, cy + r * 0.5f);
                c.drawPath(path, p);
                break;
            case "diamond":
                path.reset(); path.moveTo(cx, cy - r * 0.8f); path.lineTo(cx + r * 0.8f, cy); path.lineTo(cx, cy + r * 0.8f); path.lineTo(cx - r * 0.8f, cy); path.close();
                c.drawPath(path, p);
                break;
            case "pulse": {
                float k = 0.55f + 0.45f * phase;
                int a = (col >>> 24);
                p.setColor((Math.round(a * (1f - phase)) << 24) | (col & 0xFFFFFF));
                c.drawCircle(cx, cy, r * k, p);
                p.setColor(col);
                c.drawCircle(cx, cy, r * 0.28f, p);
                break; }
            case "spin": {
                c.save(); c.rotate(360f * phase, cx, cy);
                c.drawCircle(cx, cy, r * 0.62f, p);
                c.drawLine(cx - r, cy, cx - r * 0.62f, cy, p); c.drawLine(cx + r * 0.62f, cy, cx + r, cy, p);
                c.drawLine(cx, cy - r, cx, cy - r * 0.62f, p); c.drawLine(cx, cy + r * 0.62f, cx, cy + r, p);
                c.restore();
                break; }
            default:
                c.drawLine(cx - r, cy, cx - g, cy, p); c.drawLine(cx + g, cy, cx + r, cy, p);
                c.drawLine(cx, cy - r, cx, cy - g, p); c.drawLine(cx, cy + g, cx, cy + r, p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f + ox * d, cy = getHeight() / 2f + oy * d;
        float w = Math.max(1f, thick * d);
        int base = al(color, opacity);
        if (glow > 0.02f) {
            layer(c, cx, cy, al(color, opacity * glow * 0.18f), w * 4.2f);
            layer(c, cx, cy, al(color, opacity * glow * 0.32f), w * 2.6f);
        }
        if (outline) layer(c, cx, cy, al(Color.BLACK, 0.7f * opacity), w + 2f * d);
        layer(c, cx, cy, base, w);
        boolean pointy = "crossdot".equals(style) || (dot && !"dot".equals(style));
        if (pointy) {
            p.setStyle(Paint.Style.FILL);
            if (outline) { p.setColor(al(Color.BLACK, 0.7f * opacity)); c.drawCircle(cx, cy, w * 1.5f + d, p); }
            p.setColor(base); c.drawCircle(cx, cy, w * 1.5f, p);
        }
    }
}
