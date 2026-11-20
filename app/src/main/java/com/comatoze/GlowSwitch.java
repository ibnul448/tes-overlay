package com.comatoze;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Saklar kapsul gelap bergaya tombol mode: OFF = kapsul abu gelap + bulatan abu di kiri;
 * ON = cincin berpendar warna aksen + bulatan berwarna di kanan + titik LED kecil.
 */
public class GlowSwitch extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean on, first = true;
    private int accent = 0xFF4A90FF;
    private float pos; // 0 = kiri (OFF), 1 = kanan (ON)
    private ValueAnimator anim;

    public GlowSwitch(Context c) { super(c); }

    public void setState(boolean o, int a) {
        accent = a;
        if (first || o == on) {
            on = o; pos = o ? 1f : 0f; first = false;
            invalidate();
            return;
        }
        on = o;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, o ? 1f : 0f);
        anim.setDuration(180);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { pos = (Float) va.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    private static int alpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float g = h * 0.11f;                 // tebal cincin pendar
        float k = Math.max(1f, h * 0.05f);   // garis tipis
        float t = pos;                       // 0..1

        // cincin pendar (hanya saat ON, memudar mengikuti animasi)
        if (t > 0.01f) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(g);
            p.setColor(alpha(accent, (int) (95 * t)));
            r.set(g / 2f, g / 2f, w - g / 2f, h - g / 2f);
            cv.drawRoundRect(r, h / 2f, h / 2f, p);
        }

        // badan kapsul
        float in = g;
        r.set(in, in, w - in, h - in);
        float rad = (h - 2 * in) / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, in, 0, h - in,
                on ? 0xFF14171D : 0xFF2E3138, on ? 0xFF0C0E12 : 0xFF1C1E24, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(k);
        p.setColor(t > 0.5f ? accent : 0x33FFFFFF);
        cv.drawRoundRect(r, rad, rad, p);

        // bulatan geser
        float d = (h - 2 * in) - 2 * k * 1.6f;
        float cy = h / 2f;
        float x0 = in + k * 1.6f + d / 2f, x1 = w - in - k * 1.6f - d / 2f;
        float cx = x0 + (x1 - x0) * t;
        p.setStyle(Paint.Style.FILL);
        if (t > 0.5f) {
            p.setShader(new LinearGradient(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f,
                    0xFFFFB04D, accent, Shader.TileMode.CLAMP));
        } else {
            p.setShader(new LinearGradient(cx, cy - d / 2f, cx, cy + d / 2f, 0xFFB5BCC8, 0xFF6F7684, Shader.TileMode.CLAMP));
        }
        cv.drawCircle(cx, cy, d / 2f, p);
        p.setShader(null);
        // kilau kecil
        p.setColor(alpha(Color.WHITE, t > 0.5f ? 110 : 70));
        cv.drawCircle(cx - d * 0.14f, cy - d * 0.16f, d * 0.14f, p);
    }
}
