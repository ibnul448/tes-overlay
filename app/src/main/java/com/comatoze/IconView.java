package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ikon vektor (digambar dengan Canvas) untuk seluruh fitur overlay: tanpa emoji. */
public class IconView extends View {
    public static final int GAUGE = 0, SLIDERS = 1, GRID = 2, PLUS = 3, CLOSE = 4, MINUS = 5, LEAF = 6, EQ = 7, FLAME = 8,
            TRASH = 9, CHIP = 10, DND = 11, HZ = 12, REC = 13, PULSE = 14, THERMO = 15, TOUCH = 16, BOLT = 17, BATTERY = 18,
            PAD = 19, CLOUD = 20, MOON = 21, TARGET = 22, RAM = 23, SHOT = 24, SUN = 25, BACK = 26, CHEV = 27, WIFI = 28, WINDOW = 29;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final RectF r = new RectF();
    private int type;
    private int color = 0xFFB8BEC9;
    private boolean filled;

    public IconView(Context c, int type) {
        super(c);
        this.type = type;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int t) { type = t; invalidate(); }
    public void setColor(int c) { color = c; invalidate(); }
    public void setFilled(boolean f) { filled = f; invalidate(); }

    private void stroke() { p.setStyle(Paint.Style.STROKE); }
    private void fill() { p.setStyle(Paint.Style.FILL); }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, u = s * 0.34f;
        p.setColor(color);
        p.setStrokeWidth(Math.max(1.5f, s * 0.075f));
        stroke();
        switch (type) {
            case GAUGE:
                r.set(cx - u, cy - u, cx + u, cy + u);
                cv.drawArc(r, 150f, 240f, false, p);
                cv.drawLine(cx, cy + u * 0.1f, cx + u * 0.55f, cy - u * 0.55f, p);
                fill(); cv.drawCircle(cx, cy + u * 0.1f, s * 0.06f, p);
                break;
            case SLIDERS:
                for (int i = 0; i < 3; i++) {
                    float y = cy + (i - 1) * u * 0.85f;
                    cv.drawLine(cx - u, y, cx + u, y, p);
                    float kx = cx + (i == 0 ? -0.35f : i == 1 ? 0.4f : -0.05f) * u;
                    fill(); cv.drawCircle(kx, y, s * 0.085f, p); stroke();
                }
                break;
            case GRID: {
                float g = u * 0.22f, b = u * 0.95f;
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                    float x0 = cx - b + i * (b + g), y0 = cy - b + j * (b + g);
                    r.set(x0, y0, x0 + b - g, y0 + b - g);
                    if (filled) fill(); else stroke();
                    cv.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
                }
                break; }
            case PLUS:
                cv.drawLine(cx - u, cy, cx + u, cy, p); cv.drawLine(cx, cy - u, cx, cy + u, p); break;
            case CLOSE:
                cv.drawLine(cx - u * 0.8f, cy - u * 0.8f, cx + u * 0.8f, cy + u * 0.8f, p);
                cv.drawLine(cx + u * 0.8f, cy - u * 0.8f, cx - u * 0.8f, cy + u * 0.8f, p); break;
            case MINUS:
                cv.drawLine(cx - u, cy, cx + u, cy, p); break;
            case LEAF:
                path.reset();
                path.moveTo(cx - 0.8f * u, cy + 0.8f * u);
                path.cubicTo(cx - 0.9f * u, cy - 0.2f * u, cx - 0.2f * u, cy - 0.9f * u, cx + 0.9f * u, cy - 0.9f * u);
                path.cubicTo(cx + 0.9f * u, cy + 0.1f * u, cx + 0.2f * u, cy + 0.9f * u, cx - 0.8f * u, cy + 0.8f * u);
                path.close();
                cv.drawPath(path, p);
                cv.drawLine(cx - 0.8f * u, cy + 0.8f * u, cx + 0.15f * u, cy - 0.15f * u, p);
                break;
            case EQ:
                fill();
                for (int i = 0; i < 3; i++) {
                    float x = cx + (i - 1) * u * 0.72f;
                    float top = cy + (i == 0 ? 0.0f : i == 1 ? -0.9f : -0.4f) * u;
                    r.set(x - u * 0.2f, top, x + u * 0.2f, cy + 0.9f * u);
                    cv.drawRoundRect(r, s * 0.03f, s * 0.03f, p);
                }
                break;
            case FLAME:
                path.reset();
                path.moveTo(cx, cy - u);
                path.cubicTo(cx + 0.2f * u, cy - 0.5f * u, cx + 0.95f * u, cy - 0.3f * u, cx + 0.8f * u, cy + 0.3f * u);
                path.cubicTo(cx + 0.7f * u, cy + 0.8f * u, cx + 0.3f * u, cy + u, cx, cy + u);
                path.cubicTo(cx - 0.4f * u, cy + u, cx - 0.9f * u, cy + 0.7f * u, cx - 0.8f * u, cy + 0.2f * u);
                path.cubicTo(cx - 0.75f * u, cy - 0.2f * u, cx - 0.4f * u, cy - 0.3f * u, cx - 0.3f * u, cy - 0.7f * u);
                path.cubicTo(cx - 0.15f * u, cy - 0.5f * u, cx - 0.05f * u, cy - 0.4f * u, cx, cy - u);
                path.close();
                cv.drawPath(path, p);
                break;
            case TRASH:
                cv.drawLine(cx - 0.9f * u, cy - 0.5f * u, cx + 0.9f * u, cy - 0.5f * u, p);
                path.reset();
                path.moveTo(cx - 0.3f * u, cy - 0.5f * u); path.lineTo(cx - 0.3f * u, cy - 0.9f * u);
                path.lineTo(cx + 0.3f * u, cy - 0.9f * u); path.lineTo(cx + 0.3f * u, cy - 0.5f * u);
                cv.drawPath(path, p);
                path.reset();
                path.moveTo(cx - 0.7f * u, cy - 0.5f * u); path.lineTo(cx - 0.55f * u, cy + 0.9f * u);
                path.lineTo(cx + 0.55f * u, cy + 0.9f * u); path.lineTo(cx + 0.7f * u, cy - 0.5f * u);
                cv.drawPath(path, p);
                cv.drawLine(cx - 0.2f * u, cy - 0.15f * u, cx - 0.2f * u, cy + 0.6f * u, p);
                cv.drawLine(cx + 0.2f * u, cy - 0.15f * u, cx + 0.2f * u, cy + 0.6f * u, p);
                break;
            case CHIP:
                r.set(cx - 0.6f * u, cy - 0.6f * u, cx + 0.6f * u, cy + 0.6f * u);
                cv.drawRoundRect(r, s * 0.03f, s * 0.03f, p);
                r.set(cx - 0.25f * u, cy - 0.25f * u, cx + 0.25f * u, cy + 0.25f * u);
                cv.drawRect(r, p);
                for (int i = -1; i <= 1; i += 2) {
                    float k = i * 0.3f * u;
                    cv.drawLine(cx + k, cy - 0.6f * u, cx + k, cy - 0.95f * u, p);
                    cv.drawLine(cx + k, cy + 0.6f * u, cx + k, cy + 0.95f * u, p);
                    cv.drawLine(cx - 0.6f * u, cy + k, cx - 0.95f * u, cy + k, p);
                    cv.drawLine(cx + 0.6f * u, cy + k, cx + 0.95f * u, cy + k, p);
                }
                break;
            case DND:
                cv.drawCircle(cx, cy, 0.85f * u, p);
                cv.drawLine(cx - 0.6f * u, cy - 0.6f * u, cx + 0.6f * u, cy + 0.6f * u, p);
                break;
            case HZ:
                r.set(cx - u, cy - 0.7f * u, cx + u, cy + 0.55f * u);
                cv.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
                cv.drawLine(cx, cy + 0.55f * u, cx, cy + 0.9f * u, p);
                cv.drawLine(cx - 0.4f * u, cy + 0.9f * u, cx + 0.4f * u, cy + 0.9f * u, p);
                path.reset();
                path.moveTo(cx - 0.6f * u, cy - 0.05f * u); path.lineTo(cx - 0.3f * u, cy - 0.4f * u);
                path.lineTo(cx, cy + 0.2f * u); path.lineTo(cx + 0.3f * u, cy - 0.25f * u); path.lineTo(cx + 0.6f * u, cy);
                cv.drawPath(path, p);
                break;
            case REC:
                cv.drawCircle(cx, cy, 0.85f * u, p);
                fill(); cv.drawCircle(cx, cy, 0.45f * u, p);
                break;
            case PULSE:
                path.reset();
                path.moveTo(cx - u, cy); path.lineTo(cx - 0.45f * u, cy); path.lineTo(cx - 0.2f * u, cy - 0.8f * u);
                path.lineTo(cx + 0.15f * u, cy + 0.8f * u); path.lineTo(cx + 0.4f * u, cy); path.lineTo(cx + u, cy);
                cv.drawPath(path, p);
                break;
            case THERMO:
                r.set(cx - 0.22f * u, cy - 0.95f * u, cx + 0.22f * u, cy + 0.4f * u);
                cv.drawRoundRect(r, u * 0.22f, u * 0.22f, p);
                cv.drawCircle(cx, cy + 0.62f * u, 0.38f * u, p);
                cv.drawLine(cx, cy - 0.3f * u, cx, cy + 0.55f * u, p);
                break;
            case TOUCH:
                fill(); cv.drawCircle(cx, cy, 0.22f * u, p); stroke();
                cv.drawCircle(cx, cy, 0.62f * u, p);
                p.setAlpha(120);
                cv.drawCircle(cx, cy, 0.98f * u, p);
                break;
            case BOLT:
                fill();
                path.reset();
                path.moveTo(cx + 0.2f * u, cy - u); path.lineTo(cx - 0.6f * u, cy + 0.15f * u); path.lineTo(cx - 0.05f * u, cy + 0.15f * u);
                path.lineTo(cx - 0.25f * u, cy + u); path.lineTo(cx + 0.65f * u, cy - 0.2f * u); path.lineTo(cx + 0.05f * u, cy - 0.2f * u);
                path.close();
                cv.drawPath(path, p);
                break;
            case BATTERY:
                r.set(cx - 0.95f * u, cy - 0.5f * u, cx + 0.7f * u, cy + 0.5f * u);
                cv.drawRoundRect(r, s * 0.04f, s * 0.04f, p);
                fill(); r.set(cx + 0.7f * u, cy - 0.2f * u, cx + 0.95f * u, cy + 0.2f * u); cv.drawRect(r, p);
                path.reset();
                path.moveTo(cx - 0.05f * u, cy - 0.35f * u); path.lineTo(cx - 0.45f * u, cy + 0.05f * u); path.lineTo(cx - 0.1f * u, cy + 0.05f * u);
                path.lineTo(cx - 0.25f * u, cy + 0.35f * u); path.lineTo(cx + 0.25f * u, cy - 0.1f * u); path.lineTo(cx - 0.02f * u, cy - 0.1f * u);
                path.close();
                cv.drawPath(path, p);
                break;
            case PAD:
                r.set(cx - u, cy - 0.55f * u, cx + u, cy + 0.55f * u);
                cv.drawRoundRect(r, u * 0.5f, u * 0.5f, p);
                cv.drawLine(cx - 0.55f * u, cy, cx - 0.15f * u, cy, p);
                cv.drawLine(cx - 0.35f * u, cy - 0.2f * u, cx - 0.35f * u, cy + 0.2f * u, p);
                fill();
                cv.drawCircle(cx + 0.3f * u, cy - 0.12f * u, s * 0.05f, p);
                cv.drawCircle(cx + 0.58f * u, cy + 0.12f * u, s * 0.05f, p);
                break;
            case CLOUD:
                fill();
                cv.drawCircle(cx - 0.45f * u, cy + 0.2f * u, 0.45f * u, p);
                cv.drawCircle(cx + 0.05f * u, cy - 0.1f * u, 0.62f * u, p);
                cv.drawCircle(cx + 0.55f * u, cy + 0.25f * u, 0.42f * u, p);
                r.set(cx - 0.45f * u, cy + 0.2f * u, cx + 0.55f * u, cy + 0.67f * u);
                cv.drawRect(r, p);
                break;
            case MOON:
                fill();
                path.reset(); path.addCircle(cx, cy, 0.9f * u, Path.Direction.CW);
                path2.reset(); path2.addCircle(cx + 0.45f * u, cy - 0.3f * u, 0.75f * u, Path.Direction.CW);
                path.op(path2, Path.Op.DIFFERENCE);
                cv.drawPath(path, p);
                break;
            case TARGET:
                cv.drawCircle(cx, cy, 0.7f * u, p);
                cv.drawLine(cx, cy - u, cx, cy - 0.4f * u, p); cv.drawLine(cx, cy + u, cx, cy + 0.4f * u, p);
                cv.drawLine(cx - u, cy, cx - 0.4f * u, cy, p); cv.drawLine(cx + u, cy, cx + 0.4f * u, cy, p);
                break;
            case RAM:
                r.set(cx - u, cy - 0.5f * u, cx + u, cy + 0.5f * u);
                cv.drawRoundRect(r, s * 0.03f, s * 0.03f, p);
                for (int i = -1; i <= 1; i++) {
                    cv.drawLine(cx + i * 0.5f * u, cy - 0.2f * u, cx + i * 0.5f * u, cy + 0.2f * u, p);
                    cv.drawLine(cx + i * 0.6f * u, cy + 0.5f * u, cx + i * 0.6f * u, cy + 0.85f * u, p);
                }
                break;
            case SHOT:
                r.set(cx - 0.9f * u, cy - 0.6f * u, cx + 0.9f * u, cy + 0.7f * u);
                cv.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                cv.drawCircle(cx, cy + 0.05f * u, 0.35f * u, p);
                cv.drawLine(cx - 0.3f * u, cy - 0.6f * u, cx - 0.15f * u, cy - 0.9f * u, p);
                cv.drawLine(cx - 0.15f * u, cy - 0.9f * u, cx + 0.15f * u, cy - 0.9f * u, p);
                cv.drawLine(cx + 0.15f * u, cy - 0.9f * u, cx + 0.3f * u, cy - 0.6f * u, p);
                break;
            case SUN:
                cv.drawCircle(cx, cy, 0.42f * u, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    cv.drawLine(cx + (float) Math.cos(a) * 0.7f * u, cy + (float) Math.sin(a) * 0.7f * u,
                            cx + (float) Math.cos(a) * 1.0f * u, cy + (float) Math.sin(a) * 1.0f * u, p);
                }
                break;
            case BACK:
                cv.drawLine(cx + 0.9f * u, cy, cx - 0.9f * u, cy, p);
                cv.drawLine(cx - 0.9f * u, cy, cx - 0.2f * u, cy - 0.7f * u, p);
                cv.drawLine(cx - 0.9f * u, cy, cx - 0.2f * u, cy + 0.7f * u, p);
                break;
            case CHEV:
                cv.drawLine(cx - 0.35f * u, cy - 0.8f * u, cx + 0.45f * u, cy, p);
                cv.drawLine(cx + 0.45f * u, cy, cx - 0.35f * u, cy + 0.8f * u, p);
                break;
            case WIFI:
                for (int i = 1; i <= 3; i++) {
                    float rr = 0.4f * u * i;
                    r.set(cx - rr, cy + 0.7f * u - rr, cx + rr, cy + 0.7f * u + rr);
                    cv.drawArc(r, 225f, 90f, false, p);
                }
                fill(); cv.drawCircle(cx, cy + 0.7f * u, s * 0.05f, p);
                break;
            case WINDOW:
                r.set(cx - 0.95f * u, cy - 0.75f * u, cx + 0.95f * u, cy + 0.75f * u);
                cv.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                cv.drawLine(cx - 0.95f * u, cy - 0.3f * u, cx + 0.95f * u, cy - 0.3f * u, p);
                break;
            default:
                break;
        }
    }
}
