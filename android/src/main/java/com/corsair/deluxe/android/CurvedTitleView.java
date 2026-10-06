package com.corsair.deluxe.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class CurvedTitleView extends View {
    private static final String TITLE = "Corsair Deluxe!";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path titlePath = new Path();
    private float textOffset;

    public CurvedTitleView(Context context) {
        this(context, null);
    }

    public CurvedTitleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurvedTitleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setColor(Color.parseColor("#F7D98B"));
        paint.setTextSize(spToPx(46));
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        paint.setStyle(Paint.Style.FILL);
        paint.setTextScaleX(1.6f);
        paint.setLetterSpacing(0.1f);
        paint.setShadowLayer(6f, 2f, 2f, Color.argb(80, 0, 0, 0));
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        titlePath.reset();

        float margin = w * 0.04f;
        float bottomY = h * 0.88f;
        float controlY = -h * 0.25f;

        titlePath.moveTo(margin, bottomY);
        titlePath.quadTo(w * 0.5f, controlY, w - margin, bottomY);

        float pathLength = new PathMeasure(titlePath, false).getLength();
        textOffset = Math.max(0f, (pathLength - paint.measureText(TITLE)) * 0.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawTextOnPath(TITLE, titlePath, textOffset, 0f, paint);
    }
}
