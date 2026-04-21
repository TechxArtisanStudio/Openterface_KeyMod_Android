package com.openterface.keymod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Circular pace gauge that draws an arc indicating presentation pace status.
 */
public class PaceGaugeView extends View {

    private static final String TAG = "PaceGaugeView";

    private Paint arcPaint;
    private Paint bgPaint;
    private Paint textPaint;
    private Paint subTextPaint;
    private RectF oval;

    // Pace states: -1 = behind, 0 = on track, 1 = ahead
    private int paceState = 0;
    private float progress = 0.5f; // 0.0 to 1.0

    private static final int COLOR_BEHIND = 0xFFEF4444;   // Red
    private static final int COLOR_ON_TRACK = 0xFF34D399; // Green
    private static final int COLOR_AHEAD = 0xFF3B82F6;    // Blue
    private static final int COLOR_BG = 0xFF2A2F45;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUBTEXT = 0xFF9BA3C5;

    public PaceGaugeView(Context context) {
        super(context);
        init();
    }

    public PaceGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PaceGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(4));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(dp(4));
        bgPaint.setColor(COLOR_BG);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(dp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(COLOR_SUBTEXT);
        subTextPaint.setTextSize(dp(9));
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        oval = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = dp(4);
        oval.set(padding, padding, w - padding, h - padding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background arc
        canvas.drawArc(oval, 135, 270, false, bgPaint);

        // Determine color based on pace state
        int arcColor;
        switch (paceState) {
            case -1: arcColor = COLOR_BEHIND; break;
            case 1:  arcColor = COLOR_AHEAD; break;
            default: arcColor = COLOR_ON_TRACK; break;
        }
        arcPaint.setColor(arcColor);

        // Draw progress arc (270 degrees total, starting from 135 degrees)
        float sweepAngle = 270 * progress;
        canvas.drawArc(oval, 135, sweepAngle, false, arcPaint);

        // Draw "PACE" text
        String paceText = "PACE";
        String statusText;
        switch (paceState) {
            case -1: statusText = "SLOW"; break;
            case 1:  statusText = "FAST"; break;
            default: statusText = "OK"; break;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        canvas.drawText(paceText, centerX, centerY - dp(2), textPaint);
        canvas.drawText(statusText, centerX, centerY + dp(12), subTextPaint);
    }

    /**
     * Set the pace state: -1 = behind schedule, 0 = on track, 1 = ahead of schedule
     */
    public void setPaceState(int state) {
        this.paceState = state;
        invalidate();
    }

    /**
     * Set the progress (0.0 to 1.0)
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
