package com.comatoze;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animasi pembuka game (layar penuh, ±2,2 dtk): latar gelap memudar masuk, cahaya radial melebar, kilau diagonal menyapu,
 * cincin gradasi tersapu 360°, petir logo muncul dengan efek membal, riak keluar dari cincin, nama "COMATOZE" + nama game
 * dan garis kemajuan, lalu memudar. Digambar manual di satu View supaya ringan.
 */
public class LaunchAnimView extends View {
    private static final int BLUE = Color.parseColor("#3B9BFF"), CYAN = Color.parseColor("#2EDCFF");
    private static final long DUR = 2200;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private final RectF rf = new RectF();
    private final float d;
    private float t;
    private String sub = "";
    private ValueAnimator anim;

    public LaunchAnimView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    public void setSub(String s) { sub = s == null ? "" : s; }

    public void play(final Runnable onEnd) {
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DUR);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { if (onEnd != null) onEnd.run(); }
        });
        anim.start();
    }

    public void cancel() { if (anim != null) anim.cancel(); }

    private static float cl(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static float seg(float x, float a, float b) { return cl((x - a) / (b - a)); }
    private static float easeOut(float x) { float k = 1 - x; return 1 - k * k * k; }
    private static float back(float x) { float k = x - 1; return 1 + 2.2f * k * k * k + 1.2f * k * k; }
    private static int al(int c, float a) { return (Math.round(255 * cl(a)) << 24) | (c & 0x00FFFFFF); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h * 0.46f;
        float m = Math.min(seg(t, 0f, 0.12f), 1f - seg(t, 0.82f, 1f));   // alfa keseluruhan

        // latar
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(al(Color.parseColor("#06080C"), 0.88f * m));
        c.drawRect(0, 0, w, h, p);

        // cahaya radial melebar
        float gr = Math.max(w, h) * 0.75f * easeOut(seg(t, 0f, 0.55f));
        if (gr > 1) {
            p.setShader(new RadialGradient(cx, cy, gr, new int[]{al(BLUE, 0.42f * m), al(BLUE, 0.12f * m), 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        // kilau diagonal menyapu
        float sw = seg(t, 0.08f, 0.55f);
        if (sw > 0 && sw < 1) {
            float x = -w * 0.3f + (w * 1.6f) * easeOut(sw);
            float bw = w * 0.16f, sk = h * 0.25f;
            Path q = new Path();
            q.moveTo(x, 0); q.lineTo(x + bw, 0); q.lineTo(x + bw - sk, h); q.lineTo(x - sk, h); q.close();
            p.setShader(new LinearGradient(x - sk, 0, x + bw, 0, new int[]{0, al(CYAN, 0.22f * m), 0}, null, Shader.TileMode.CLAMP));
            c.drawPath(q, p);
            p.setShader(null);
        }

        float R = Math.min(w, h) * 0.17f;

        // riak keluar dari cincin
        for (int k = 0; k < 3; k++) {
            float ph = seg(t, 0.32f + k * 0.12f, 0.86f + k * 0.04f);
            if (ph <= 0 || ph >= 1) continue;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(al(CYAN, (1f - ph) * 0.38f * m));
            c.drawCircle(cx, cy, R * (1.1f + easeOut(ph) * 1.5f), p);
        }

        // cincin gradasi tersapu
        float sweep = 360f * easeOut(seg(t, 0.1f, 0.62f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(al(Color.WHITE, 0.07f * m));
        c.drawCircle(cx, cy, R, p);
        rf.set(cx - R, cy - R, cx + R, cy + R);
        c.save();
        c.rotate(-90f + 360f * seg(t, 0.1f, 1f) * 0.35f, cx, cy);
        SweepGradient sg = new SweepGradient(cx, cy, new int[]{al(BLUE, m), al(CYAN, m), al(BLUE, m)}, new float[]{0f, 0.6f, 1f});
        p.setShader(sg);
        c.drawArc(rf, 0, sweep, false, p);
        p.setShader(null);
        c.restore();
        p.setStrokeCap(Paint.Cap.BUTT);

        // petir logo (membal)
        float s = back(seg(t, 0.16f, 0.5f));
        if (s > 0) {
            float u = R * 0.62f * s;
            bolt.reset();
            bolt.moveTo(cx + 0.18f * u, cy - 1.0f * u);
            bolt.lineTo(cx - 0.62f * u, cy + 0.12f * u);
            bolt.lineTo(cx - 0.06f * u, cy + 0.12f * u);
            bolt.lineTo(cx - 0.22f * u, cy + 1.0f * u);
            bolt.lineTo(cx + 0.62f * u, cy - 0.16f * u);
            bolt.lineTo(cx + 0.06f * u, cy - 0.16f * u);
            bolt.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(cx, cy - u, cx, cy + u, al(Color.WHITE, m), al(CYAN, m), Shader.TileMode.CLAMP));
            c.drawPath(bolt, p);
            p.setShader(null);
        }

        // judul
        float ta = seg(t, 0.36f, 0.58f);
        if (ta > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC));
            p.setTextAlign(Paint.Align.CENTER);
            p.setLetterSpacing(0.32f);
            p.setTextSize(24f * d);
            float ty = cy + R + 46f * d + (1f - easeOut(ta)) * 14f * d;
            p.setColor(al(Color.WHITE, ta * m));
            c.drawText("COMATOZE", cx, ty, p);

            float sa = seg(t, 0.46f, 0.66f);
            if (sa > 0 && sub.length() > 0) {
                p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                p.setLetterSpacing(0.12f);
                p.setTextSize(12f * d);
                p.setColor(al(Color.parseColor("#9AA5B5"), sa * m));
                c.drawText(sub, cx, ty + 26f * d, p);
            }
            p.setLetterSpacing(0f);
        }

        // garis kemajuan
        float pa = seg(t, 0.3f, 0.86f);
        if (pa > 0) {
            float bw = Math.min(w * 0.5f, 220f * d), bx = cx - bw / 2f, by = cy + R + 108f * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(al(Color.WHITE, 0.10f * m));
            rf.set(bx, by, bx + bw, by + 3f * d);
            c.drawRoundRect(rf, 2f * d, 2f * d, p);
            p.setShader(new LinearGradient(bx, 0, bx + bw, 0, al(BLUE, m), al(CYAN, m), Shader.TileMode.CLAMP));
            rf.set(bx, by, bx + bw * easeOut(pa), by + 3f * d);
            c.drawRoundRect(rf, 2f * d, 2f * d, p);
            p.setShader(null);
        }
    }
}
