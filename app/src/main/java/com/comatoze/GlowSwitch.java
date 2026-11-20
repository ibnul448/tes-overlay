package com.comatoze;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Saklar kapsul ala game-booster: ON = kapsul hijau tua + titik hijau + tulisan "ON" + kenop gelap beralur di kanan;
 * OFF = kapsul abu gelap + tulisan "OFF" + kenop di kiri. Warna tetap (tidak ikut warna mode).
 */
public class GlowSwitch extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean on, first = true;
    private float pos; // 0 = kiri (OFF), 1 = kanan (ON)
    private ValueAnimator anim;

    public GlowSwitch(Context c) { super(c); p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); }

    /** parameter warna dipertahankan demi kompatibilitas; tampilan memakai warna tetap. */
    public void setState(boolean o, int a) {
        if (first || o == on) {
            on = o; pos = o ? 1f : 0f; first = false;
            invalidate();
            return;
        }
        on = o;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, o ? 1f : 0f);
        anim.setDuration(170);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { pos = (Float) va.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = pos, line = Math.max(1f, h * 0.045f), rad = h / 2f;

        // badan kapsul
        r.set(line / 2f, line / 2f, w - line / 2f, h - line / 2f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h,
                t > 0.5f ? 0xFF2E8B3D : 0xFF30333B, t > 0.5f ? 0xFF1B5E26 : 0xFF1E2127, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(line);
        p.setColor(t > 0.5f ? 0xFF5BC06A : 0x33FFFFFF);
        cv.drawRoundRect(r, rad, rad, p);

        // kenop
        float gap = h * 0.11f, d = h - 2 * gap;
        float x0 = gap + d / 2f, x1 = w - gap - d / 2f, cx = x0 + (x1 - x0) * t, cy = h / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, cy - d / 2f, 0, cy + d / 2f,
                t > 0.5f ? 0xFF4A5A4E : 0xFF59606C, t > 0.5f ? 0xFF28322B : 0xFF353A44, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, d / 2f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(line);
        p.setColor(0x33FFFFFF);
        cv.drawCircle(cx, cy, d / 2f - line / 2f, p);
        // dua alur kecil di kenop
        p.setColor(0x66FFFFFF);
        p.setStrokeCap(Paint.Cap.ROUND);
        float gh = d * 0.26f, gx = d * 0.09f;
        cv.drawLine(cx - gx, cy - gh, cx - gx, cy + gh, p);
        cv.drawLine(cx + gx, cy - gh, cx + gx, cy + gh, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // tulisan: ON di kiri (titik hijau + teks), OFF di kanan
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(h * 0.36f);
        p.setTextAlign(Paint.Align.CENTER);
        float ty = cy - (p.descent() + p.ascent()) / 2f;
        if (t > 0.5f) {
            float tx = (gap * 2f + (cx - d / 2f)) / 2f;                         // area di kiri kenop
            p.setColor(0xFF3DFF6E);
            cv.drawCircle(tx - h * 0.27f, cy, h * 0.075f, p);
            p.setColor(0xFFFFFFFF);
            cv.drawText("ON", tx + h * 0.10f, ty, p);
        } else {
            float tx = ((cx + d / 2f) + (w - gap * 2f)) / 2f;                   // area di kanan kenop
            p.setColor(0xFF5A606B);
            cv.drawCircle(tx - h * 0.33f, cy, h * 0.075f, p);
            p.setColor(0xFF8E96A3);
            cv.drawText("OFF", tx + h * 0.10f, ty, p);
        }
    }
}
