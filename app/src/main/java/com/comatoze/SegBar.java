package com.comatoze;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Bar meter bersegmen miring (gaya HUD game). Segmen akhir berubah kuning/merah saat nilai tinggi. */
public class SegBar extends View {
    private static final int N = 20;
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path seg = new Path();
    private float cur = 0f, target = 0f;
    private int accent = Color.parseColor("#2EA8FF");
    private ValueAnimator anim;

    public SegBar(Context c) {
        super(c);
        off.setColor(Color.parseColor("#1A1F2B"));
    }

    public void setAccent(int a) { accent = a; invalidate(); }

    public void setValue(float v) {
        target = Math.max(0f, Math.min(100f, v));
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(cur, target);
        anim.setDuration(280);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { cur = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float gap = w * 0.012f, sw = (w - gap * (N - 1)) / N, sk = h * 0.35f;
        int lit = Math.round(cur / 100f * N);
        for (int i = 0; i < N; i++) {
            float x = i * (sw + gap);
            seg.reset();
            seg.moveTo(x + sk, 0);
            seg.lineTo(x + sw + sk, 0);
            seg.lineTo(x + sw, h);
            seg.lineTo(x, h);
            seg.close();
            if (i < lit) {
                on.setColor(i >= N * 0.9f ? Color.parseColor("#FF4D4D") : i >= N * 0.72f ? Color.parseColor("#FFB347") : accent);
                cv.drawPath(seg, on);
            } else cv.drawPath(seg, off);
        }
    }
}
