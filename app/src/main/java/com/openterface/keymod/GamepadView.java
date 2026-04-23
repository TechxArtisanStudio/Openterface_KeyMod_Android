package com.openterface.keymod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.openterface.keymod.GamepadConfigManager.ComponentPosition;

/**
 * Gamepad View - Custom view for rendering and interacting with gamepad components
 * Supports Xbox, PlayStation, and NES layouts
 * Draggable components in edit mode
 */
public class GamepadView extends View {

    private static final String TAG = "GamepadView";

    // Paint objects
    private Paint bgPaint;
    private Paint buttonPaint;
    private Paint textPaint;
    private Paint stickPaint;
    private Paint dpadPaint;

    // Current layout
    private GamepadLayout currentLayout;
    private Map<String, GamepadConfigManager.ComponentPosition> componentPositions;
    private GamepadConfigManager configManager;

    // Interaction
    private boolean isEditMode = false;
    private String draggedComponentId = null;
    private String pressedComponentId = null;
    private ButtonPressListener buttonPressListener;
    private AnalogStickListener analogStickListener;

    // Multi-touch tracking for D-pad hold behavior
    private Map<Integer, String> pointerComponents = new HashMap<>(); // pointerId -> componentId
    private Set<String> dpadPressedSet = new HashSet<>(); // currently held D-pad components
    private DpadStateListener dpadStateListener;
    private Set<String> buttonsPressedSet = new HashSet<>(); // currently held non-D-pad buttons
    private ButtonReleaseListener buttonReleaseListener;

    // Analog stick drag state
    private String  activeStickId      = null;
    private int     activeStickPointerId = -1;  // pointerId of the finger on the stick
    private float   activeStickCenterX = 0;
    private float   activeStickCenterY = 0;
    private float   activeStickRadius  = 0;
    private float   stickOffsetX       = 0;
    private float   stickOffsetY       = 0;

    // Long press detection
    private static final long LONG_PRESS_THRESHOLD = 600; // ms
    private static final float LONG_PRESS_MOVE_THRESHOLD = 15; // pixels
    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;
    private String longPressComponentId = null;
    private float longPressDownX = 0;
    private float longPressDownY = 0;
    private boolean longPressCancelled = false;
    private ComponentLongPressListener longPressListener;
    private EmptyAreaLongPressListener emptyAreaLongPressListener;

    // Component bounds (for touch detection)
    private Map<String, RectF> componentBounds;

    // Disabled components (visual only, no touch response)
    private Map<String, Boolean> disabledComponents = new HashMap<>();

    // Display labels for components (can differ from default hardcoded labels)
    private Map<String, String> componentDisplayLabels = new HashMap<>();

    // Configurable keycode for the main button (button_a)
    private int buttonAKeyCode = 40; // default: HID Enter
    private boolean showTwoButtons = false;
    private boolean longPressEnabled = true;

    // Button size scale (0.5 = 50%, 1.0 = 100%, 2.0 = 200%)
    private float buttonSizeScale = 1.0f;
    private float stickSizeScale = 1.0f;

    // Currently active stick directions (for highlighting labels)
    private Set<String> activeStickDirections = new java.util.HashSet<>();

    // Background image
    private android.graphics.Bitmap backgroundBitmap = null;
    private Runnable onBackgroundChanged;

    // Background viewport (pan and zoom)
    private float bgScale = 1.0f;
    private float bgOffsetX = 0f;
    private float bgOffsetY = 0f;
    private float bgInitialScale = 1.0f;

    // Two-finger background manipulation
    private boolean isManipulatingBg = false;
    private float bgLastDistance = -1f;
    private float bgLastCenterX = 0f;
    private float bgLastCenterY = 0f;
    private float bgStartOffsetX = 0f;
    private float bgStartOffsetY = 0f;
    private float bgStartScale = 1.0f;

    public GamepadView(Context context) {
        super(context);
        init();
    }

    public GamepadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamepadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        configManager = new GamepadConfigManager(getContext());
        componentBounds = new HashMap<>();

        // Initialize paints
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1A1A1A"));

        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(Color.parseColor("#2196F3"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.parseColor("#333333"));

        dpadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dpadPaint.setColor(Color.parseColor("#444444"));

        // Default layout
        currentLayout = GamepadLayout.XBOX;
        componentPositions = configManager.loadLayoutPositions(currentLayout);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background image if set, otherwise solid color
        if (backgroundBitmap != null) {
            drawBackgroundWithPanZoom(canvas);
        } else {
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        }

        // Draw components based on layout
        drawComponents(canvas);
    }

    private void drawBackgroundWithPanZoom(Canvas canvas) {
        canvas.save();
        int bw = backgroundBitmap.getWidth();
        int bh = backgroundBitmap.getHeight();
        int vw = getWidth();
        int vh = getHeight();

        // Compute initial fit scale (center-crop to fill screen while maintaining aspect ratio)
        float fitScale = Math.max((float) vw / bw, (float) vh / bh);
        bgInitialScale = fitScale;

        // Apply current transform: pan then scale, centered on viewport
        float cx = vw / 2f + bgOffsetX;
        float cy = vh / 2f + bgOffsetY;
        canvas.translate(cx, cy);
        canvas.scale(bgScale, bgScale);
        canvas.translate(-bw / 2f, -bh / 2f);
        canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        canvas.restore();
    }

    private void drawComponents(Canvas canvas) {
        // Clear stale bounds from previous draw before re-registering all hit areas
        componentBounds.clear();
        switch (currentLayout) {
            case XBOX:
                drawXboxLayout(canvas);
                break;
            case PLAYSTATION:
                drawPlayStationLayout(canvas);
                break;
            case NES:
                drawNESLayout(canvas);
                break;
            case SIMPLE:
                drawSimpleLayout(canvas);
                break;
        }
    }

    private void drawXboxLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.15f, h * 0.5f, 120);

        // Left Stick - 150% larger
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 90, "L");

        // Right Stick - 150% larger
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 90, "R");

        // ABXY Buttons - 150% larger
        drawButton(canvas, w * 0.85f, h * 0.55f, 52.5f, "A", Color.parseColor("#4CAF50"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 52.5f, "B", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 52.5f, "X", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 52.5f, "Y", Color.parseColor("#FFC107"));

        // Shoulders - 150% larger
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 75, 30, "LB");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 75, 30, "RB");

        // Center buttons - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.40f, 37.5f, "≡", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 37.5f, "⊞", Color.GRAY);
    }

    private void drawPlayStationLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.15f, h * 0.5f, 120);

        // Left Stick - 150% larger
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 90, "L");

        // Right Stick - 150% larger
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 90, "R");

        // △○×□ Buttons - 150% larger
        drawButton(canvas, w * 0.85f, h * 0.55f, 52.5f, "×", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 52.5f, "○", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 52.5f, "□", Color.parseColor("#9C27B0"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 52.5f, "△", Color.parseColor("#4CAF50"));

        // L1/R1 - 150% larger
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 75, 30, "L1");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 75, 30, "R1");

        // Center buttons - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.40f, 37.5f, "Select", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 37.5f, "Start", Color.GRAY);
    }

    private void drawNESLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.20f, h * 0.5f, 150);

        // A/B Buttons - 150% larger
        drawButton(canvas, w * 0.80f, h * 0.55f, 67.5f, "A", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 67.5f, "B", Color.parseColor("#F44336"));

        // Select/Start - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.70f, 45, "SELECT", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.70f, 45, "START", Color.GRAY);
    }

    private void drawSimpleLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // Get configured display labels (default to "L" and "Enter")
        String stickLabel = componentDisplayLabels.getOrDefault("stick_l", "L");
        String stickUpLabel = componentDisplayLabels.getOrDefault("stick_up", "W");
        String stickDownLabel = componentDisplayLabels.getOrDefault("stick_down", "S");
        String stickLeftLabel = componentDisplayLabels.getOrDefault("stick_left", "A");
        String stickRightLabel = componentDisplayLabels.getOrDefault("stick_right", "D");
        String buttonLabel = componentDisplayLabels.getOrDefault("button_a", "Enter");
        String buttonBLabel = componentDisplayLabels.getOrDefault("button_b", "Esc");

        // Use saved positions with defaults
        float stickX = getPositionX("stick_left", 0.20f) * w;
        float stickY = getPositionY("stick_left", 0.50f) * h;
        float buttonAX = getPositionX("button_a", 0.85f) * w;
        float buttonAY = getPositionY("button_a", 0.50f) * h;
        float buttonBX = getPositionX("button_b", 0.93f) * w;
        float buttonBY = getPositionY("button_b", 0.40f) * h;

        // Analog stick on the left - 150% larger
        drawAnalogStick(canvas, stickX, stickY, 180, stickLabel,
                stickUpLabel, stickDownLabel, stickLeftLabel, stickRightLabel);

        if (showTwoButtons) {
            // Two buttons side by side on the right
            drawButton(canvas, buttonAX, buttonAY, 100, "A", Color.parseColor("#4CAF50"), buttonLabel);
            drawButton(canvas, buttonBX, buttonBY, 100, "B", Color.parseColor("#F44336"), buttonBLabel);
        } else {
            // Single big button on the far right - 150% larger, with configured label
            drawButton(canvas, buttonAX, buttonAY, 135, "A", Color.parseColor("#4CAF50"), buttonLabel);
        }
    }

    private void drawDpad(Canvas canvas, float cx, float cy, float size) {
        float half = size / 2;
        float barHalf = half * 0.4f;

        // Touch bounds matching each arm
        componentBounds.put("dpad_up",
                new RectF(cx - barHalf, cy - half, cx + barHalf, cy));
        componentBounds.put("dpad_down",
                new RectF(cx - barHalf, cy, cx + barHalf, cy + half));
        componentBounds.put("dpad_left",
                new RectF(cx - half, cy - barHalf, cx, cy + barHalf));
        componentBounds.put("dpad_right",
                new RectF(cx, cy - barHalf, cx + half, cy + barHalf));

        // Draw each arm separately with its own highlight
        int normalColor = Color.parseColor("#444444");
        int pressedColor = Color.parseColor("#FF9800");

        drawDpadArm(canvas, cx, cy, "up", barHalf, half, normalColor, pressedColor);
        drawDpadArm(canvas, cx, cy, "down", barHalf, half, normalColor, pressedColor);
        drawDpadArm(canvas, cx, cy, "left", barHalf, half, normalColor, pressedColor);
        drawDpadArm(canvas, cx, cy, "right", barHalf, half, normalColor, pressedColor);

        // Draw center circle (no highlight, always normal)
        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#666666"));
        canvas.drawCircle(cx, cy, half * 0.2f, centerPaint);

        // Direction arrows
        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.WHITE);
        arrowPaint.setTextSize(size * 0.15f);
        arrowPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("▲", cx, cy - half * 0.7f, arrowPaint);
        canvas.drawText("▼", cx, cy + half * 0.8f, arrowPaint);
        canvas.drawText("◀", cx - half * 0.75f, cy + half * 0.05f, arrowPaint);
        canvas.drawText("▶", cx + half * 0.75f, cy + half * 0.05f, arrowPaint);
    }

    private void drawDpadArm(Canvas canvas, float cx, float cy, String dir,
                             float barHalf, float half, int normalColor, int pressedColor) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(("dpad_" + dir).equals(pressedComponentId) ? pressedColor : normalColor);

        switch (dir) {
            case "up":
                canvas.drawRect(cx - barHalf, cy - half, cx + barHalf, cy, paint);
                break;
            case "down":
                canvas.drawRect(cx - barHalf, cy, cx + barHalf, cy + half, paint);
                break;
            case "left":
                canvas.drawRect(cx - half, cy - barHalf, cx, cy + barHalf, paint);
                break;
            case "right":
                canvas.drawRect(cx, cy - barHalf, cx + half, cy + barHalf, paint);
                break;
        }
    }

    private void drawAnalogStick(Canvas canvas, float cx, float cy, float radius, String label) {
        drawAnalogStick(canvas, cx, cy, radius, label, null, null, null, null);
    }

    private void drawAnalogStick(Canvas canvas, float cx, float cy, float radius, String label,
                                 String upLabel, String downLabel, String leftLabel, String rightLabel) {
        float scaledRadius = radius * stickSizeScale;
        String id = "stick_" + label.toLowerCase();
        RectF bounds = new RectF(cx - scaledRadius, cy - scaledRadius, cx + scaledRadius, cy + scaledRadius);
        componentBounds.put(id, bounds);
        // L3/R3 click is detected via minimal-movement tap in onTouchEvent — no separate bounds needed

        // Outer circle (housing)
        Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint.setColor(Color.parseColor("#444444"));
        canvas.drawCircle(cx, cy, scaledRadius, outerPaint);

        // Outer ring
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3);
        ringPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, scaledRadius, ringPaint);

        // Inner circle (stick top) — offset toward where the user's thumb is
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float innerCx = cx;
        float innerCy = cy;
        if (id.equals(activeStickId)) {
            innerCx += stickOffsetX;
            innerCy += stickOffsetY;
        }

        // Highlight if pressed (L3/R3 click)
        if (id.equals(activeStickId) && (Math.abs(stickOffsetX) < activeStickRadius * 0.15f)
                && (Math.abs(stickOffsetY) < activeStickRadius * 0.15f)) {
            innerPaint.setColor(Color.parseColor("#FF9800")); // Orange highlight
        } else {
            innerPaint.setColor(Color.parseColor("#666666"));
        }

        canvas.drawCircle(innerCx, innerCy, scaledRadius * 0.6f, innerPaint);

        // Inner highlight
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.parseColor("#888888"));
        canvas.drawCircle(innerCx, innerCy, scaledRadius * 0.3f, highlightPaint);

        // If directional labels are provided, draw them instead of center label
        if (upLabel != null) {
            Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dirPaint.setTextAlign(Paint.Align.CENTER);
            dirPaint.setFakeBoldText(true);
            dirPaint.setTextSize(scaledRadius * 0.22f);
            dirPaint.setColor(activeStickDirections.contains(id + "_up") ? Color.parseColor("#FF9800") : Color.WHITE);
            canvas.drawText(upLabel, cx, cy - scaledRadius * 0.65f, dirPaint);
            dirPaint.setColor(activeStickDirections.contains(id + "_down") ? Color.parseColor("#FF9800") : Color.WHITE);
            canvas.drawText(downLabel, cx, cy + scaledRadius * 0.75f, dirPaint);
            dirPaint.setColor(activeStickDirections.contains(id + "_left") ? Color.parseColor("#FF9800") : Color.WHITE);
            canvas.drawText(leftLabel, cx - scaledRadius * 0.7f, cy + scaledRadius * 0.12f, dirPaint);
            dirPaint.setColor(activeStickDirections.contains(id + "_right") ? Color.parseColor("#FF9800") : Color.WHITE);
            canvas.drawText(rightLabel, cx + scaledRadius * 0.7f, cy + scaledRadius * 0.12f, dirPaint);
        } else {
            // Label (L or R)
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(radius * 0.5f);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setFakeBoldText(true);
            canvas.drawText(label, cx, cy + radius * 0.3f, labelPaint);

            // L3/R3 indicator
            Paint l3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            l3Paint.setColor(Color.parseColor("#AAAAAA"));
            l3Paint.setTextSize(radius * 0.25f);
            l3Paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("CLICK", cx, cy + radius * 0.6f, l3Paint);
        }
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int color) {
        drawButton(canvas, cx, cy, radius, label, color, null);
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int color, String displayLabel) {
        float scaledRadius = radius * buttonSizeScale;
        String id = "button_" + label.toLowerCase().replace("(", "").replace(")", "");
        RectF bounds = new RectF(cx - scaledRadius, cy - scaledRadius, cx + scaledRadius, cy + scaledRadius);
        componentBounds.put(id, bounds);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Highlight if pressed
        if (id.equals(pressedComponentId)) {
            paint.setColor(darkenColor(color));
        } else {
            paint.setColor(color);
        }

        canvas.drawCircle(cx, cy, scaledRadius, paint);

        // Draw white border
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        borderPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, scaledRadius, borderPaint);

        // Draw label (use display label if provided)
        String textLabel = displayLabel != null ? displayLabel : label;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        // Scale text size based on label length to fit within button
        float textScale = textLabel.length() > 6 ? 0.4f : textLabel.length() > 4 ? 0.5f : 0.6f;
        textPaint.setTextSize(scaledRadius * textScale);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        canvas.drawText(textLabel, cx, cy + scaledRadius * 0.3f, textPaint);
    }

    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.7f; // Darken value
        return Color.HSVToColor(hsv);
    }

    private void drawShoulderButton(Canvas canvas, float cx, float cy, float width, float height, String label) {
        String id = label.toLowerCase();
        RectF bounds = new RectF(cx - width/2, cy - height/2, cx + width/2, cy + height/2);
        componentBounds.put(id, bounds);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        // Highlight if pressed
        if (id.equals(pressedComponentId)) {
            paint.setColor(Color.parseColor("#FF9800")); // Orange highlight
        } else {
            paint.setColor(Color.parseColor("#666666"));
        }
        
        canvas.drawRoundRect(new RectF(cx - width/2, cy - height/2, cx + width/2, cy + height/2), 10, 10, paint);
        
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + 5, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);

                    // Start long press timer only when enabled
                    if (longPressEnabled) {
                        longPressComponentId = componentId;
                        longPressDownX = x;
                        longPressDownY = y;
                        longPressCancelled = false;
                        longPressRunnable = () -> {
                            if (longPressListener != null && longPressComponentId != null && !longPressCancelled) {
                                longPressListener.onComponentLongPress(longPressComponentId);
                            }
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD);
                    }

                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                    } else if (componentId.startsWith("stick_") && !isEditMode) {
                        activeStickId = componentId;
                        activeStickPointerId = pointerId;
                        RectF stickBounds = componentBounds.get(componentId);
                        activeStickCenterX = stickBounds.centerX();
                        activeStickCenterY = stickBounds.centerY();
                        activeStickRadius  = stickBounds.width() / 2f;
                        stickOffsetX = 0;
                        stickOffsetY = 0;
                        invalidate();
                    } else if (isEditMode) {
                        draggedComponentId = componentId;
                    } else {
                        pressedComponentId = componentId;
                        buttonsPressedSet.add(componentId);
                        if (buttonPressListener != null) {
                            int keyCode = getKeyCodeForComponent(componentId);
                            buttonPressListener.onButtonPress(componentId, keyCode);
                        }
                        invalidate();
                    }
                } else if (longPressEnabled) {
                    // Long press on empty area when edit mode is enabled
                    longPressDownX = x;
                    longPressDownY = y;
                    longPressCancelled = false;
                    Runnable emptyAreaRunnable = () -> {
                        if (!longPressCancelled && emptyAreaLongPressListener != null) {
                            emptyAreaLongPressListener.onEmptyAreaLongPress();
                        }
                    };
                    longPressRunnable = emptyAreaRunnable;
                    longPressHandler.postDelayed(emptyAreaRunnable, LONG_PRESS_THRESHOLD);
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);

                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                    } else if (!isEditMode && buttonPressListener != null) {
                        buttonsPressedSet.add(componentId);
                        int keyCode = getKeyCodeForComponent(componentId);
                        buttonPressListener.onButtonPress(componentId, keyCode);
                    }
                } else if (event.getPointerCount() >= 2 && backgroundBitmap != null) {
                    // Start two-finger background manipulation if this pointer is on empty area
                    int otherIdx = pointerIndex == 0 ? 1 : 0;
                    float ox = event.getX(otherIdx);
                    float oy = event.getY(otherIdx);
                    String otherComponent = getComponentAt(ox, oy);
                    if (otherComponent == null) {
                        isManipulatingBg = true;
                        float dx = x - ox;
                        float dy = y - oy;
                        bgLastDistance = (float) Math.sqrt(dx * dx + dy * dy);
                        bgLastCenterX = (x + ox) / 2f;
                        bgLastCenterY = (y + oy) / 2f;
                        bgStartOffsetX = bgOffsetX;
                        bgStartOffsetY = bgOffsetY;
                        bgStartScale = bgScale;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                // Cancel long press only if user moved significantly
                if (!longPressCancelled && longPressRunnable != null) {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        float mx = event.getX(i);
                        float my = event.getY(i);
                        float dx = mx - longPressDownX;
                        float dy = my - longPressDownY;
                        if (Math.sqrt(dx * dx + dy * dy) > LONG_PRESS_MOVE_THRESHOLD) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            longPressCancelled = true;
                        }
                    }
                }
                if (isManipulatingBg && event.getPointerCount() >= 2) {
                    // Two-finger pinch-to-zoom and pan on background
                    float x0 = event.getX(0), y0 = event.getY(0);
                    float x1 = event.getX(1), y1 = event.getY(1);
                    float dist = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                    float cx = (x0 + x1) / 2f;
                    float cy = (y0 + y1) / 2f;

                    // Zoom: scale relative to initial fit scale
                    if (bgLastDistance > 0) {
                        bgScale = Math.max(0.1f, bgStartScale * dist / bgLastDistance);
                    }

                    // Pan: offset change in display pixel coords
                    int vw = getWidth();
                    int vh = getHeight();
                    int bw = backgroundBitmap.getWidth();
                    int bh = backgroundBitmap.getHeight();
                    float s = bgInitialScale * bgScale;
                    float dx = (cx - bgLastCenterX) / (s != 0 ? s : 1f);
                    float dy = (cy - bgLastCenterY) / (s != 0 ? s : 1f);
                    bgOffsetX = bgStartOffsetX + dx;
                    bgOffsetY = bgStartOffsetY + dy;

                    bgLastCenterX = cx;
                    bgLastCenterY = cy;
                    bgLastDistance = dist;
                    bgStartOffsetX = bgOffsetX;
                    bgStartOffsetY = bgOffsetY;
                    bgStartScale = bgScale;
                    invalidate();
                    notifyBackgroundViewportChanged();
                } else if (activeStickId != null) {
                    // Find the pointer that's on the stick
                    float stickX = x, stickY = y; // fallback to event pointer
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (event.getPointerId(i) == activeStickPointerId) {
                            stickX = event.getX(i);
                            stickY = event.getY(i);
                            break;
                        }
                    }
                    float dx   = stickX - activeStickCenterX;
                    float dy   = stickY - activeStickCenterY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > activeStickRadius) {
                        dx = dx * activeStickRadius / dist;
                        dy = dy * activeStickRadius / dist;
                    }
                    stickOffsetX = dx;
                    stickOffsetY = dy;
                    invalidate();
                    if (analogStickListener != null) {
                        String label = activeStickId.replace("stick_", "");
                        analogStickListener.onAnalogStickMoved(
                                label, dx / activeStickRadius, dy / activeStickRadius);
                    }
                } else if (draggedComponentId != null && isEditMode) {
                    GamepadConfigManager.ComponentPosition pos =
                            componentPositions.get(draggedComponentId);
                    if (pos != null) {
                        pos.x = x / getWidth();
                        pos.y = y / getHeight();
                        invalidate();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                // Cancel long press
                if (longPressHandler != null && longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    longPressComponentId = null;
                }
                if (activeStickId != null) {
                    float moved = (float) Math.sqrt(
                            stickOffsetX * stickOffsetX + stickOffsetY * stickOffsetY);
                    if (moved < activeStickRadius * 0.15f && buttonPressListener != null) {
                        int keyCode = activeStickId.equals("stick_l") ? 1001 : 1002;
                        buttonPressListener.onButtonPress(activeStickId + "_click", keyCode);
                    }
                    String label = activeStickId.replace("stick_", "");
                    activeStickId = null;
                    activeStickPointerId = -1;
                    stickOffsetX  = 0;
                    stickOffsetY  = 0;
                    invalidate();
                    if (analogStickListener != null) {
                        analogStickListener.onAnalogStickMoved(label, 0, 0);
                    }
                }
                if (draggedComponentId != null) {
                    draggedComponentId = null;
                }

                // Release component for this pointer
                releasePointerComponent(pointerId);

                // For ACTION_UP (last finger), clear all remaining state
                if (action == MotionEvent.ACTION_UP) {
                    pointerComponents.clear();
                    dpadPressedSet.clear();
                    buttonsPressedSet.clear();
                    if (pressedComponentId != null) {
                        pressedComponentId = null;
                    }
                }

                if (pressedComponentId != null) {
                    pressedComponentId = null;
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                // Release component for the lifted pointer
                releasePointerComponent(pointerId);
                return true;
            }
        }
        return true;
    }

    private void releasePointerComponent(int pointerId) {
        String releasedComponent = pointerComponents.remove(pointerId);
        if (releasedComponent != null) {
            if (isDpadComponent(releasedComponent)) {
                dpadPressedSet.remove(releasedComponent);
                if (dpadStateListener != null) {
                    dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                }
            } else if (buttonsPressedSet.remove(releasedComponent)) {
                int keyCode = getKeyCodeForComponent(releasedComponent);
                if (buttonReleaseListener != null) {
                    buttonReleaseListener.onButtonRelease(releasedComponent, keyCode);
                }
            }
        }
    }

    private boolean isDpadComponent(String componentId) {
        return componentId != null && componentId.startsWith("dpad_");
    }

    /**
     * Get the current set of pressed D-pad key codes, in a deterministic order.
     * Returns an empty array if no D-pad directions are held.
     */
    private int[] getCurrentDpadKeys() {
        // Build in deterministic order so the HID report is stable
        String[] order = {"dpad_up", "dpad_left", "dpad_down", "dpad_right"};
        int count = 0;
        for (String id : order) {
            if (dpadPressedSet.contains(id)) count++;
        }
        int[] keys = new int[count];
        int idx = 0;
        for (String id : order) {
            if (dpadPressedSet.contains(id)) {
                keys[idx++] = getKeyCodeForComponent(id);
            }
        }
        return keys;
    }

    private String getComponentAt(float x, float y) {
        // Check D-pad in deterministic order first (they share edges at center)
        String[] dpadOrder = {"dpad_up", "dpad_right", "dpad_down", "dpad_left"};
        for (String id : dpadOrder) {
            RectF bounds = componentBounds.get(id);
            if (bounds != null && bounds.contains(x, y)) {
                return id;
            }
        }
        // Check remaining components
        for (Map.Entry<String, RectF> entry : componentBounds.entrySet()) {
            if (entry.getKey().startsWith("dpad_")) continue;
            if (entry.getValue().contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int getKeyCodeForComponent(String componentId) {
        // Map component IDs to USB HID usage IDs (USB HID Keyboard/Keypad Usage Page)
        switch (componentId) {
            // D-Pad - Arrow keys (HID: Up=0x52, Down=0x51, Left=0x50, Right=0x4F)
            case "dpad_up":    return 82; // HID Up Arrow
            case "dpad_down":  return 81; // HID Down Arrow
            case "dpad_left":  return 80; // HID Left Arrow
            case "dpad_right": return 79; // HID Right Arrow

            // Xbox/PS/NES face buttons
            case "button_a": return buttonAKeyCode;  // configurable, default: HID Enter
            case "button_b": return 41;  // HID Escape
            case "button_x": return 42;  // HID Backspace/Delete
            case "button_y": return 101; // HID Application (Menu)

            // PlayStation buttons (same mapping as Xbox equivalents)
            // IDs from drawButton using UTF-8 symbol labels
            case "button_×":        // fall-through
            case "button_cross":    return 40;  // HID Return/Enter
            case "button_○":        // fall-through
            case "button_circle":   return 41;  // HID Escape
            case "button_□":        // fall-through
            case "button_square":   return 42;  // HID Backspace/Delete
            case "button_△":        // fall-through
            case "button_triangle": return 101; // HID Application (Menu)

            // NES buttons (B = Escape)
            case "nes_b": return 41;  // HID Escape

            // Shoulder buttons → F1-F4 (HID: F1=0x3A=58 … F4=0x3D=61)
            case "lb": case "l1": return 58; // HID F1
            case "rb": case "r1": return 59; // HID F2
            case "lt": case "l2": return 60; // HID F3
            case "rt": case "r2": return 61; // HID F4

            // Stick clicks (L3/R3 = mouse buttons, handled specially)
            case "stick_l_click": return 1001;
            case "stick_r_click": return 1002;

            // Center buttons → F5/F6 (HID: F5=0x3E=62, F6=0x3F=63)
            // Xbox "≡" drawButton generates id "button_≡"; "⊞" generates "button_⊞"
            case "button_≡": case "back":   case "button_back":
            case "button_select": case "select": return 62; // HID F5
            case "button_⊞": case "start":  case "button_start": return 63; // HID F6

            default: return 0;
        }
    }

    public void setLayout(GamepadLayout layout) {
        this.currentLayout = layout;
        this.componentPositions = configManager.loadLayoutPositions(layout);
        invalidate();
    }

    /**
     * Get component X position, or return default if not found.
     */
    private float getPositionX(String componentId, float defaultX) {
        ComponentPosition pos = componentPositions.get(componentId);
        return pos != null ? pos.x : defaultX;
    }

    private float getPositionY(String componentId, float defaultY) {
        ComponentPosition pos = componentPositions.get(componentId);
        return pos != null ? pos.y : defaultY;
    }

    /**
     * Get current component positions (for saving).
     */
    public Map<String, ComponentPosition> getPositions() {
        return componentPositions;
    }

    /**
     * Listener notified when edit mode is exited (so positions can be saved).
     */
    public interface EditModeExitListener {
        void onEditModeExit(Map<String, ComponentPosition> positions);
    }

    public void setEditModeExitListener(EditModeExitListener listener) {
        this.editModeExitListener = listener;
    }

    public void savePositions() {
        configManager.saveLayoutPositions(currentLayout, componentPositions);
    }

    private EditModeExitListener editModeExitListener;

    public void setEditMode(boolean editMode) {
        if (!editMode && editModeExitListener != null) {
            editModeExitListener.onEditModeExit(componentPositions);
        }
        isEditMode = editMode;
        invalidate();
    }

    public void setButtonPressListener(ButtonPressListener listener) {
        this.buttonPressListener = listener;
    }

    public void setAnalogStickListener(AnalogStickListener listener) {
        this.analogStickListener = listener;
    }

    public void setComponentLongPressListener(ComponentLongPressListener listener) {
        this.longPressListener = listener;
    }

    public void setDpadStateListener(DpadStateListener listener) {
        this.dpadStateListener = listener;
    }

    public void setButtonReleaseListener(ButtonReleaseListener listener) {
        this.buttonReleaseListener = listener;
    }

    public void setEmptyAreaLongPressListener(EmptyAreaLongPressListener listener) {
        this.emptyAreaLongPressListener = listener;
    }

    public void setComponentDisabled(String componentId, boolean disabled) {
        disabledComponents.put(componentId, disabled);
        invalidate();
    }

    public boolean isComponentDisabled(String componentId) {
        return Boolean.TRUE.equals(disabledComponents.get(componentId));
    }

    public void setComponentDisplayLabel(String componentId, String label) {
        componentDisplayLabels.put(componentId, label);
        invalidate();
    }

    public void setComponentDisplayLabels(Map<String, String> labels) {
        componentDisplayLabels.putAll(labels);
        invalidate();
    }

    public void setButtonAKeyCode(int keyCode) {
        this.buttonAKeyCode = keyCode;
    }

    public void setShowTwoButtons(boolean show) {
        this.showTwoButtons = show;
        invalidate();
    }

    public void setLongPressEnabled(boolean enabled) {
        this.longPressEnabled = enabled;
        // Cancel any pending long press when disabling
        if (!enabled && longPressHandler != null && longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
        }
    }

    public void setButtonSizeScale(float scale) {
        this.buttonSizeScale = scale;
        invalidate();
    }

    public void setStickSizeScale(float scale) {
        this.stickSizeScale = scale;
        invalidate();
    }

    public void setBackgroundBitmap(android.graphics.Bitmap bitmap) {
        this.backgroundBitmap = bitmap;
        if (bitmap != null) {
            // Reset viewport to center-fit
            bgScale = 1.0f;
            bgOffsetX = 0f;
            bgOffsetY = 0f;
        }
        invalidate();
        if (onBackgroundChanged != null) onBackgroundChanged.run();
    }

    public void resetBackgroundViewport() {
        bgScale = 1.0f;
        bgOffsetX = 0f;
        bgOffsetY = 0f;
        invalidate();
    }

    public android.graphics.Bitmap getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public void setBackgroundChangedCallback(Runnable callback) {
        this.onBackgroundChanged = callback;
    }

    public void setBackgroundViewportCallback(Runnable callback) {
        this.onBackgroundViewportChanged = callback;
    }

    public float getBackgroundScale() { return bgScale; }
    public float getBackgroundOffsetX() { return bgOffsetX; }
    public float getBackgroundOffsetY() { return bgOffsetY; }

    public void setBackgroundViewport(float scale, float offsetX, float offsetY) {
        this.bgScale = scale;
        this.bgOffsetX = offsetX;
        this.bgOffsetY = offsetY;
        invalidate();
    }

    private Runnable onBackgroundViewportChanged;

    void notifyBackgroundViewportChanged() {
        if (onBackgroundViewportChanged != null) onBackgroundViewportChanged.run();
    }

    public void setActiveStickDirections(String stickLabel, Set<String> directions) {
        String stickId = "stick_" + stickLabel.toLowerCase();
        // Remove old directions for this stick
        activeStickDirections.removeIf(d -> d.startsWith(stickId + "_"));
        // Add new directions
        for (String dir : directions) {
            activeStickDirections.add(stickId + "_" + dir);
        }
        invalidate();
    }

    public void clearStickDirections(String stickLabel) {
        String stickId = "stick_" + stickLabel.toLowerCase();
        activeStickDirections.removeIf(d -> d.startsWith(stickId + "_"));
        invalidate();
    }

    private void drawDisabledButton(Canvas canvas, float cx, float cy, float radius, String label) {
        String id = "button_" + label.toLowerCase().replace("(", "").replace(")", "");
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        componentBounds.put(id, bounds);

        // Disabled appearance: dimmed gray with reduced alpha
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha(100);
        paint.setColor(Color.GRAY);
        canvas.drawCircle(cx, cy, radius, paint);

        // Dashed white border
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setAlpha(80);
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // Dimmed label
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(radius * 0.45f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAlpha(100);
        textPaint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + radius * 0.2f, textPaint);
    }

    public Map<String, GamepadConfigManager.ComponentPosition> getComponentPositions() {
        return componentPositions;
    }

    // Listener interfaces
    public interface ButtonPressListener {
        void onButtonPress(String buttonId, int keyCode);
    }

    public interface AnalogStickListener {
        void onAnalogStickMoved(String stickId, float x, float y);
    }

    public interface ComponentLongPressListener {
        void onComponentLongPress(String componentId);
    }

    public interface DpadStateListener {
        /**
         * Called whenever D-pad touch state changes.
         * @param keyCodes HID key codes for currently held D-pad directions (empty = all released)
         */
        void onDpadStateChanged(int[] keyCodes);
    }

    public interface ButtonReleaseListener {
        void onButtonRelease(String buttonId, int keyCode);
    }

    public interface EmptyAreaLongPressListener {
        void onEmptyAreaLongPress();
    }
}
