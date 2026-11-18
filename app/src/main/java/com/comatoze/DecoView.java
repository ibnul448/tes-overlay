package com.comatoze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Garis hias tipis ala panel game. Mode 0 = bingkai header bersudut, mode 1 = garis pemisah bertakik. */
public class DecoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int mode;

    public DecoView(Context c, int mode) {
        super(c);
        this.mode = mode;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0x55FFFFFF);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        p.setStrokeWidth(Math.max(1f, h * (mode == 0 ? 0.025f : 0.09f)));
        path.reset();
        if (mode == 0) {
            path.moveTo(w * 0.0f, h * 0.95f);
            path.lineTo(w * 0.0f, h * 0.30f);
            path.lineTo(w * 0.05f, h * 0.05f);
            path.lineTo(w * 0.62f, h * 0.05f);
            path.lineTo(w * 0.67f, h * 0.30f);
            path.lineTo(w * 1.0f, h * 0.30f);
        } else {
            path.moveTo(0, h * 0.85f);
            path.lineTo(w * 0.52f, h * 0.85f);
            path.lineTo(w * 0.58f, h * 0.45f);
            path.lineTo(w, h * 0.45f);
        }
        cv.drawPath(path, p);
    }
}
