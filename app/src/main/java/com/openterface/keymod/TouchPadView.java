package com.openterface.keymod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchPadView extends View {
    private Paint paint;
    private float lastX, lastY;
    private boolean isMoving = false;
    private OnTouchPadListener listener;
    private float lastMoveMSX, lastMoveMSY;
    private float startMoveMSX, startMoveMSY;

    public interface OnTouchPadListener {
        void onTouchMove(float startMoveMSX, float startMoveMSY, float lastMoveMSX, float lastMoveMSY);
        void onTouchClick();
        void onTouchDoubleClick();
        void onTouchRightClick();
    }

    public TouchPadView(Context context) {
        super(context);
        init();
    }

    public TouchPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
    }

    public void setOnTouchPadListener(OnTouchPadListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Here, touch feedback is drawn, such as touch points or movement trajectories
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                lastMoveMSX = x;
                lastMoveMSY = y;
                isMoving = false;
                break;

            case MotionEvent.ACTION_MOVE:
                startMoveMSX = event.getX();
                startMoveMSY = event.getY();
                if (listener != null) {
                    isMoving = true;
                    listener.onTouchMove(startMoveMSX, startMoveMSY, lastMoveMSX, lastMoveMSY);
                }
                lastMoveMSX = startMoveMSX;
                lastMoveMSY = startMoveMSY;
                break;

            case MotionEvent.ACTION_UP:
                if (!isMoving) {
                    if (event.getEventTime() - event.getDownTime() < 200) {
                        if (listener != null) {
                            listener.onTouchClick();
                        }
                    }
                }
                lastMoveMSX = 0;
                lastMoveMSY = 0;
                break;
        }

        return true;
    }
} 