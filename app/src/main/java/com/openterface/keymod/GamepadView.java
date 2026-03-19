package com.openterface.keymod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

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

    // Component bounds (for touch detection)
    private Map<String, RectF> componentBounds;

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

        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Draw components based on layout
        drawComponents(canvas);
    }

    private void drawComponents(Canvas canvas) {
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
        }
    }

    private void drawXboxLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad
        drawDpad(canvas, w * 0.15f, h * 0.5f, 80);

        // Left Stick
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 60, "L");

        // Right Stick
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 60, "R");

        // ABXY Buttons
        drawButton(canvas, w * 0.85f, h * 0.55f, 35, "A", Color.parseColor("#4CAF50"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 35, "B", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 35, "X", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 35, "Y", Color.parseColor("#FFC107"));

        // Shoulders (visual only - actual buttons at top edge)
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 50, 20, "LB");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 50, 20, "RB");

        // Center buttons
        drawButton(canvas, w * 0.40f, h * 0.40f, 25, "≡", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 25, "⊞", Color.GRAY);
    }

    private void drawPlayStationLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad
        drawDpad(canvas, w * 0.15f, h * 0.5f, 80);

        // Left Stick
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 60, "L");

        // Right Stick
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 60, "R");

        // △○×□ Buttons
        drawButton(canvas, w * 0.85f, h * 0.55f, 35, "×", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 35, "○", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 35, "□", Color.parseColor("#9C27B0"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 35, "△", Color.parseColor("#4CAF50"));

        // L1/R1
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 50, 20, "L1");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 50, 20, "R1");

        // Center buttons
        drawButton(canvas, w * 0.40f, h * 0.40f, 25, "Select", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 25, "Start", Color.GRAY);
    }

    private void drawNESLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad
        drawDpad(canvas, w * 0.20f, h * 0.5f, 100);

        // A/B Buttons
        drawButton(canvas, w * 0.80f, h * 0.55f, 45, "A", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 45, "B", Color.parseColor("#F44336"));

        // Select/Start
        drawButton(canvas, w * 0.40f, h * 0.70f, 30, "SELECT", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.70f, 30, "START", Color.GRAY);
    }

    private void drawDpad(Canvas canvas, float cx, float cy, float size) {
        float half = size / 2;
        
        // Create separate bounds for each D-Pad direction
        // Up
        RectF upBounds = new RectF(cx - half * 0.3f, cy - half, cx + half * 0.3f, cy - half * 0.5f);
        componentBounds.put("dpad_up", upBounds);
        
        // Down
        RectF downBounds = new RectF(cx - half * 0.3f, cy + half * 0.5f, cx + half * 0.3f, cy + half);
        componentBounds.put("dpad_down", downBounds);
        
        // Left
        RectF leftBounds = new RectF(cx - half, cy - half * 0.3f, cx - half * 0.5f, cy + half * 0.3f);
        componentBounds.put("dpad_left", leftBounds);
        
        // Right
        RectF rightBounds = new RectF(cx + half * 0.5f, cy - half * 0.3f, cx + half, cy + half * 0.3f);
        componentBounds.put("dpad_right", rightBounds);
        
        // Center
        RectF centerBounds = new RectF(cx - half * 0.3f, cy - half * 0.3f, cx + half * 0.3f, cy + half * 0.3f);
        componentBounds.put("dpad_center", centerBounds);

        // Draw D-Pad cross with highlight for pressed state
        Paint dpadPaintToUse = new Paint(dpadPaint);
        if (pressedComponentId != null && pressedComponentId.startsWith("dpad_")) {
            dpadPaintToUse.setColor(Color.parseColor("#FF9800")); // Orange highlight
        }
        
        canvas.drawRect(cx - half * 0.4f, cy - half, cx + half * 0.4f, cy + half, dpadPaintToUse);
        canvas.drawRect(cx - half, cy - half * 0.4f, cx + half, cy + half * 0.4f, dpadPaintToUse);
        
        // Draw center circle
        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#666666"));
        canvas.drawCircle(cx, cy, half * 0.2f, centerPaint);
        
        // Draw direction indicators
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(size * 0.15f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        // Up arrow
        canvas.drawText("▲", cx, cy - half * 0.7f, textPaint);
        // Down arrow
        canvas.drawText("▼", cx, cy + half * 0.8f, textPaint);
        // Left arrow
        canvas.drawText("◀", cx - half * 0.75f, cy + half * 0.05f, textPaint);
        // Right arrow
        canvas.drawText("▶", cx + half * 0.75f, cy + half * 0.05f, textPaint);
    }

    private void drawAnalogStick(Canvas canvas, float cx, float cy, float radius, String label) {
        String id = "stick_" + label.toLowerCase();
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        componentBounds.put(id, bounds);
        
        // Also add L3/R3 click bounds (the stick itself can be clicked)
        String clickId = id + "_click";
        componentBounds.put(clickId, bounds);

        // Outer circle (housing)
        Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint.setColor(Color.parseColor("#444444"));
        canvas.drawCircle(cx, cy, radius, outerPaint);
        
        // Outer ring
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3);
        ringPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius, ringPaint);
        
        // Inner circle (stick top) - offset based on position if we had it
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        // Highlight if pressed (L3/R3 click)
        if (clickId.equals(pressedComponentId)) {
            innerPaint.setColor(Color.parseColor("#FF9800")); // Orange highlight
        } else {
            innerPaint.setColor(Color.parseColor("#666666"));
        }
        
        canvas.drawCircle(cx, cy, radius * 0.6f, innerPaint);
        
        // Inner highlight
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.parseColor("#888888"));
        canvas.drawCircle(cx, cy, radius * 0.3f, highlightPaint);
        
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

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int color) {
        String id = "button_" + label.toLowerCase().replace("(", "").replace(")", "");
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        componentBounds.put(id, bounds);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        // Highlight if pressed
        if (id.equals(pressedComponentId)) {
            paint.setColor(darkenColor(color));
        } else {
            paint.setColor(color);
        }
        
        canvas.drawCircle(cx, cy, radius, paint);
        
        // Draw white border
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        borderPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius, borderPaint);
        
        // Draw label
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(radius * 0.6f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + radius * 0.3f, textPaint);
    }

    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.7f; // Darken value
        return Color.HSVToColor(hsv);
    }

    private void drawShoulderButton(Canvas canvas, float cx, float cy, float width, float height, String label) {
        String id = label.toLowerCase().replace("1", "");
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
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                String componentId = getComponentAt(x, y);
                if (componentId != null) {
                    if (isEditMode) {
                        draggedComponentId = componentId;
                    } else {
                        pressedComponentId = componentId;
                        // Trigger button press
                        if (buttonPressListener != null) {
                            int keyCode = getKeyCodeForComponent(componentId);
                            buttonPressListener.onButtonPress(componentId, keyCode);
                        }
                        invalidate(); // Redraw for visual feedback
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggedComponentId != null && isEditMode) {
                    // Update position
                    GamepadConfigManager.ComponentPosition pos = componentPositions.get(draggedComponentId);
                    if (pos != null) {
                        pos.x = x / getWidth();
                        pos.y = y / getHeight();
                        invalidate();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggedComponentId != null) {
                    draggedComponentId = null;
                }
                if (pressedComponentId != null) {
                    pressedComponentId = null;
                    invalidate(); // Redraw to remove highlight
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    private String getComponentAt(float x, float y) {
        for (Map.Entry<String, RectF> entry : componentBounds.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int getKeyCodeForComponent(String componentId) {
        // Map component IDs to Android key codes
        // This is simplified - should use configManager.loadButtonMapping()
        switch (componentId) {
            // D-Pad - Arrow keys
            case "dpad_up": return 19; // KEYCODE_DPAD_UP
            case "dpad_down": return 20; // KEYCODE_DPAD_DOWN
            case "dpad_left": return 21; // KEYCODE_DPAD_LEFT
            case "dpad_right": return 22; // KEYCODE_DPAD_RIGHT
            
            // Xbox buttons (ABXY)
            case "button_a": return 66; // Enter
            case "button_b": return 4; // Back
            case "button_x": return 67; // Delete
            case "button_y": return 82; // Menu
            
            // PlayStation buttons (△○×□)
            case "button_cross": return 66; // Enter (×)
            case "button_circle": return 4; // Back (○)
            case "button_square": return 67; // Delete (□)
            case "button_triangle": return 82; // Menu (△)
            
            // NES buttons (A/B) - same IDs as Xbox
            case "nes_a": return 66; // Enter
            case "nes_b": return 4; // Back
            
            // Shoulder buttons (mapped to function keys)
            case "lb": return 131; // F1
            case "rb": return 132; // F2
            case "lt": return 133; // F3
            case "rt": return 134; // F4
            case "l1": return 131; // F1
            case "r1": return 132; // F2
            case "l2": return 133; // F3
            case "r2": return 134; // F4
            
            // Stick clicks (L3/R3 = mouse buttons)
            case "stick_l_click": return 1001; // Special: Left mouse click
            case "stick_r_click": return 1002; // Special: Right mouse click
            
            // Center buttons
            case "back": return 135; // F5 (Xbox Back)
            case "start": return 136; // F6 (Xbox Start)
            case "select": return 135; // F5
            default: return 0;
        }
    }

    public void setLayout(GamepadLayout layout) {
        this.currentLayout = layout;
        this.componentPositions = configManager.loadLayoutPositions(layout);
        invalidate();
    }

    public void setEditMode(boolean editMode) {
        isEditMode = editMode;
        invalidate();
    }

    public void setButtonPressListener(ButtonPressListener listener) {
        this.buttonPressListener = listener;
    }

    public void setAnalogStickListener(AnalogStickListener listener) {
        this.analogStickListener = listener;
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
}
