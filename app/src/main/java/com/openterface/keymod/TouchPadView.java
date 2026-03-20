package com.openterface.keymod;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * TouchPadView — gesture mapping aligned with iOS TouchpadUIView:
 *
 *  1-finger pan      → mouse move
 *  single tap        → left click  (delayed 150 ms to distinguish from double-tap)
 *  double tap        → double click
 *  long press        → drag-mode toggle
 *  2-finger tap      → right click
 *  2-finger pan      → scroll (natural direction, reduced sensitivity)
 */
public class TouchPadView extends View {

    // Thresholds — mirror iOS defaults
    private static final long   TAP_DELAY_MS       = 150;   // wait before confirming single tap
    private static final long   TAP_DURATION_MAX_MS = 400;  // finger must lift within this time
    private static final float  TAP_MOVE_THRESHOLD  = 10f;  // px — movement cancels tap

    public interface OnTouchPadListener {
        void onTouchMove(float startX, float startY, float lastX, float lastY);
        void onTouchClick();
        void onTouchDoubleClick();
        void onTouchRightClick();
        /** Called when a long-press is detected — host can toggle drag mode */
        default void onTouchLongPress() {}
    }

    private OnTouchPadListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 1-finger move state
    private float lastMoveX, lastMoveY;
    private boolean isDragging = false;

    // Tap detection state
    private float tapDownX, tapDownY;
    private long  tapDownTime;
    private boolean tapCancelled = false;
    private Runnable pendingSingleTap = null;

    // 2-finger scroll state
    private float twoFingerPrevX, twoFingerPrevY;
    private boolean isTwoFingerScrolling = false;

    private final GestureDetector gestureDetector;

    public TouchPadView(Context context) {
        super(context);
        gestureDetector = buildGestureDetector(context);
        setClickable(true);
    }

    public TouchPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = buildGestureDetector(context);
        setClickable(true);
    }

    private GestureDetector buildGestureDetector(Context context) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                cancelPendingSingleTap();
                if (listener != null) listener.onTouchDoubleClick();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (e.getPointerCount() == 1) {
                    cancelPendingSingleTap();
                    if (listener != null) listener.onTouchLongPress();
                }
            }
        });
    }

    public void setOnTouchPadListener(OnTouchPadListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Let GestureDetector handle double-tap and long-press on the raw events
        gestureDetector.onTouchEvent(event);

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        // ---- 2-finger gestures ----
        if (pointerCount == 2) {
            cancelPendingSingleTap();
            isDragging = false;

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float cx = (event.getX(0) + event.getX(1)) / 2f;
                    float cy = (event.getY(0) + event.getY(1)) / 2f;
                    if (!isTwoFingerScrolling) {
                        // First move with 2 fingers — initialise scroll
                        twoFingerPrevX = cx;
                        twoFingerPrevY = cy;
                        isTwoFingerScrolling = true;
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float dx = cx - twoFingerPrevX;
                        float dy = cy - twoFingerPrevY;
                        // Scroll: invert Y for natural scrolling, reduce sensitivity /3
                        int scrollX = (int) (dx / 3f);
                        int scrollY = (int) (-dy / 3f);
                        if (listener != null && (scrollX != 0 || scrollY != 0)) {
                            // Re-use onTouchMove with sentinel values to signal scroll,
                            // OR add a dedicated scroll callback if available.
                            // For now signal scroll via onTouchMove with a special marker
                            // by passing deltaX/deltaY directly as startX/Y with 0 lastX/Y:
                            // Actually: we'll call onTouchMove(dx, dy, 0, 0) — caller checks 0,0
                            // Better: declare scroll via a separate path — use onTouchMove
                            // with recognisable sentinel. Keep it simple: pass delta only.
                            listener.onTouchMove(scrollX, scrollY, 0, 0);
                        }
                        twoFingerPrevX = cx;
                        twoFingerPrevY = cy;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    // Both fingers up counts as 2-finger tap if no scroll happened
                    if (!isTwoFingerScrolling) {
                        if (listener != null) listener.onTouchRightClick();
                    }
                    isTwoFingerScrolling = false;
                    break;
            }
            return true;
        }

        isTwoFingerScrolling = false;

        // ---- 1-finger gestures ----
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastMoveX = x;
                lastMoveY = y;
                tapDownX = x;
                tapDownY = y;
                tapDownTime = event.getEventTime();
                tapCancelled = false;
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float distMoved = dist(x, y, tapDownX, tapDownY);
                if (distMoved > TAP_MOVE_THRESHOLD) {
                    cancelPendingSingleTap();
                    tapCancelled = true;
                    isDragging = true;
                }
                // Always forward move events for smooth cursor tracking
                if (listener != null) {
                    listener.onTouchMove(x, y, lastMoveX, lastMoveY);
                }
                lastMoveX = x;
                lastMoveY = y;
                break;

            case MotionEvent.ACTION_UP:
                long duration = event.getEventTime() - tapDownTime;
                float distLifted = dist(x, y, tapDownX, tapDownY);
                boolean validTap = !tapCancelled
                        && duration < TAP_DURATION_MAX_MS
                        && distLifted <= TAP_MOVE_THRESHOLD;

                if (validTap) {
                    // Schedule a delayed single-tap; GestureDetector will fire
                    // onDoubleTap before this fires if a second tap comes in time.
                    pendingSingleTap = () -> {
                        if (listener != null) listener.onTouchClick();
                        pendingSingleTap = null;
                    };
                    mainHandler.postDelayed(pendingSingleTap, TAP_DELAY_MS);
                }

                lastMoveX = 0;
                lastMoveY = 0;
                isDragging = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingSingleTap();
                isDragging = false;
                break;
        }
        return true;
    }

    private void cancelPendingSingleTap() {
        if (pendingSingleTap != null) {
            mainHandler.removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
