package com.openterface.keymod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class CustomKeyboardView extends LinearLayout {
    private static final String TAG = "CustomKeyboardView";
    private static final int TOP_PANEL_COLUMNS = 7;
    private static final int TOP_PANEL_ROWS = 3;
    private static final int TOP_PANEL_PAGE_SIZE = TOP_PANEL_COLUMNS * TOP_PANEL_ROWS;
    private static final float TOP_PANEL_ROW_WEIGHT = 0.8f;
    private static final float TOP_PANEL_TOTAL_WEIGHT = TOP_PANEL_ROWS * TOP_PANEL_ROW_WEIGHT;
    /** Extra numpad Fn-arrow overlay (dp); larger than top strip 24dp icons for the taller grid cells. */
    private static final int EXTRA_NUMPAD_FN_ARROW_ICON_DP = 36;
    /** Fn-layer Save / Undo / Tab icons — match top shortcut row visual weight (24dp assets, modest cell size). */
    private static final int EXTRA_NUMPAD_FN_ACTION_ICON_DP = 28;
    /**
     * Shared text size (sp) for extra-grid digits 0–9, operators / * - + =, comma/dot,
     * and Fn-layer substitutes ($ ¥ € £ % 000) so one visual block.
     */
    private static final float EXTRA_NUMPAD_GRID_KEY_TEXT_SP = 20f;
    private static final int KEY_MODE_FN = 0xF005;
    /** Local Fn latch for keyboard-only extra numpad grid (does not affect global QWERTY Fn layer). */
    private static final int KEY_EXTRA_NUMPAD_FN = 0xF006;
    private static final int KEY_NOOP_PLACEHOLDER = -1;
    private static final int MOD_CTRL = 1;
    private static final int MOD_SHIFT = 2;
    private static final int MOD_ALT = 4;
    private static final int MOD_WIN = 8;
    private boolean isShiftLeftLocked = false;
    private boolean isCtrlLeftLocked = false;
    private boolean isAltLeftLocked = false;
    private boolean isWinLeftLocked = false;
    private boolean isRunning = true;
    private boolean isSymbolMode = false;
    private boolean isFnLocked = false;
    private boolean extraNumpadFnLocked = false;
    private GridLayout extraNumpadGrid;
    private ImageButton extraNumpadFnButton;
    private boolean showExtraPortraitKeys = false;

    /** Split keyboard mode: which half to render (for landscape split mode with touchpad in middle) */
    public static final int SPLIT_NONE = 0;
    public static final int SPLIT_LEFT = 1;
    public static final int SPLIT_RIGHT = 2;
    private int splitPart = SPLIT_NONE;
    /** The paired keyboard view in split mode, for syncing modifier states */
    private CustomKeyboardView splitPartner;
    private List<List<Key>> lowerKeys;
    private UsbSerialPort port;
    private Handler repeatHandler = new Handler();
    private Runnable repeatRunnable;
    private boolean isRepeating = false;
    private static final int ALT_LONG_PRESS_TIMEOUT_MS = ViewConfiguration.getLongPressTimeout();
    private static final int ALT_CANCEL_VERTICAL_DP = 72;
    private static final int ALT_POPUP_VERTICAL_OFFSET_DP = 72;
    private static final int KEY_OUTER_MARGIN_DP = 2;
    private final Handler longPressHandler = new Handler();
    private PopupWindow alternatePopupWindow;
    private LinearLayout alternatePopupContainer;
    private View alternateAnchorView;
    private final List<TextView> alternateOptionViews = new ArrayList<>();
    private List<AlternateOption> currentAlternateOptions = new ArrayList<>();
    private int currentAlternateSelection = -1;
    private int topPanelPageIndex = 0;
    private ShortcutProfileManager shortcutProfileManager;
    private final List<TopShortcutPanel> topShortcutPanels = new ArrayList<>();
    private FrameLayout topPanelViewport;
    private LinearLayout previousTopPanelContainer;
    private LinearLayout activeTopPanelContainer;
    private LinearLayout nextTopPanelContainer;

    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Bound to BluetoothService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    private static class Key {
        String label;
        String symbolLabel;
        String alternates;
        String cornerHint;
        int code;
        String codeStr;
        float widthPercent;
        int iconResId;
        float horizontalGap;
        boolean isRepeatable;
        boolean requiresShift;
        int shortcutModifiers;
        boolean isTopPanelKey;

        Key(String label, String symbolLabel, String alternates, String cornerHint, int code, String codeStr, float widthPercent, int iconResId,
            float horizontalGap, boolean isRepeatable, boolean requiresShift, int shortcutModifiers, boolean isTopPanelKey) {
            this.label = label;
            this.symbolLabel = symbolLabel;
            this.alternates = alternates;
            this.cornerHint = cornerHint;
            this.code = code;
            this.codeStr = codeStr;
            this.widthPercent = widthPercent;
            this.iconResId = iconResId;
            this.horizontalGap = horizontalGap;
            this.isRepeatable = isRepeatable;
            this.requiresShift = requiresShift;
            this.shortcutModifiers = shortcutModifiers;
            this.isTopPanelKey = isTopPanelKey;
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId,
            float horizontalGap, boolean isRepeatable, boolean requiresShift, int shortcutModifiers, boolean isTopPanelKey) {
            this(label, symbolLabel, "", "", code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, shortcutModifiers, isTopPanelKey);
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable, boolean requiresShift) {
            this(label, symbolLabel, "", "", code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, -1, false);
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable) {
            this(label, symbolLabel, code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, false);
        }
    }

    private static class AlternateOption {
        final String display;
        final int keyCode;
        final boolean requiresShift;

        AlternateOption(String display, int keyCode, boolean requiresShift) {
            this.display = display;
            this.keyCode = keyCode;
            this.requiresShift = requiresShift;
        }
    }

    private static class TopShortcutPanel {
        String title;
        List<Key> keys;

        TopShortcutPanel(String title, List<Key> keys) {
            this.title = title;
            this.keys = keys;
        }
    }

    private static class ExtraGridKey {
        final Key key;
        final int row;
        final int col;
        final int rowSpan;
        final int colSpan;

        ExtraGridKey(Key key, int row, int col) {
            this(key, row, col, 1, 1);
        }

        ExtraGridKey(Key key, int row, int col, int rowSpan, int colSpan) {
            this.key = key;
            this.row = row;
            this.col = col;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
        }
    }

    public CustomKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Reload keyboard layout when orientation changes
        boolean isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        Log.d(TAG, "Orientation changed: landscape=" + isLandscape + ", reloading keyboard");
        loadKeyboardForCurrentState(getContext());
        removeAllViews();
        updateKeyboard();
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        shortcutProfileManager = new ShortcutProfileManager(context.getApplicationContext());
        
        // Load keyboard layout based on orientation (matching iOS behavior)
        reloadForCurrentOrientation();
        
        Log.d(TAG, "Parsed keyboard (landscape=" + isLandscape(context) + "): lowerKeys=" + lowerKeys.size());
        bindService(context);
        updateKeyboard();
    }

    public void reloadForCurrentOrientation() {
        Context context = getContext();
        loadKeyboardForCurrentState(context);
        removeAllViews();
        updateKeyboard();
    }

    private void loadKeyboardForCurrentState(Context context) {
        int keyboardResId = isLandscape(context)
                ? R.xml.keyboard_lower_landscape
                : R.xml.keyboard_lower_portrait;
        lowerKeys = parseKeyboard(context, keyboardResId);
        applyTargetOsLabels(context);
    }

    /** Update key labels based on target OS (Win → Cmd for macOS) */
    public void reloadForTargetOs() {
        Context context = getContext();
        if (context == null) return;
        loadKeyboardForCurrentState(context);
        removeAllViews();
        updateKeyboard();
    }

    private void applyTargetOsLabels(Context context) {
        String targetOs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");

        String newLabel;
        int newIconResId;
        if ("macos".equals(targetOs)) {
            newLabel = context.getString(R.string.Cmd);
            newIconResId = R.drawable.ic_os_macos;
        } else if ("linux".equals(targetOs)) {
            newLabel = context.getString(R.string.Super);
            newIconResId = R.drawable.ic_os_linux;
        } else {
            newLabel = context.getString(R.string.Win);
            newIconResId = R.drawable.windows;
        }

        for (List<Key> row : lowerKeys) {
            for (Key key : row) {
                if ("Win".equals(key.label)) {
                    key.label = newLabel;
                    key.iconResId = newIconResId;
                }
            }
        }
    }

    public void setShowExtraPortraitKeys(boolean enabled) {
        if (showExtraPortraitKeys == enabled) return;
        if (!enabled) {
            extraNumpadFnLocked = false;
        }
        showExtraPortraitKeys = enabled;
        removeAllViews();
        updateKeyboard();
    }

    /** Set which half of the keyboard to render (for landscape split mode). */
    public void setSplitPart(int part) {
        if (splitPart == part) return;
        splitPart = part;
        // Reload keys to restore original widths before re-filtering
        loadKeyboardForCurrentState(getContext());
        removeAllViews();
        updateKeyboard();
    }

    /** Set the paired keyboard view in split mode for syncing modifier states. */
    public void setSplitPartner(CustomKeyboardView partner) {
        splitPartner = partner;
    }

    /** Sync modifier lock states to the paired keyboard. */
    private void syncModifierStates() {
        if (splitPartner == null) return;
        splitPartner.isShiftLeftLocked = isShiftLeftLocked;
        splitPartner.isCtrlLeftLocked = isCtrlLeftLocked;
        splitPartner.isAltLeftLocked = isAltLeftLocked;
        splitPartner.isWinLeftLocked = isWinLeftLocked;
        splitPartner.post(() -> splitPartner.updateKeyboard());
    }

    /** Create a shared top scrolling panel view for split mode. */
    public FrameLayout createTopPanel() {
        rebuildTopShortcutPanels();
        if (topShortcutPanels.isEmpty()) {
            return null;
        }

        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }

        FrameLayout viewport = new FrameLayout(getContext());
        int rowHeightPx = dpToPx(24);
        int panelHeight = rowHeightPx * 2;
        viewport.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, panelHeight));

        previousTopPanelContainer = createTopShortcutPanelView();
        activeTopPanelContainer = createTopShortcutPanelView();
        nextTopPanelContainer = createTopShortcutPanelView();
        viewport.addView(previousTopPanelContainer);
        viewport.addView(activeTopPanelContainer);
        viewport.addView(nextTopPanelContainer);

        syncTopPanelViewportContent();

        // Post to wait for layout, then set initial positions
        viewport.post(() -> {
            float width = viewport.getWidth();
            if (width > 0) {
                activeTopPanelContainer.setTranslationX(0);
                if (previousTopPanelContainer != null) {
                    previousTopPanelContainer.setTranslationX(-width);
                }
                if (nextTopPanelContainer != null) {
                    nextTopPanelContainer.setTranslationX(width);
                }
            }
        });

        return viewport;
    }
    
    /**
     * Check if device is in landscape orientation
     * Matches iOS OrientationManager.isLandscape behavior
     */
    private boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation 
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void bindService(Context context) {
        Intent intent = new Intent(context, BluetoothService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private List<List<Key>> parseKeyboard(Context context, int resourceId) {
        List<List<Key>> rows = new ArrayList<>();
        List<Key> currentRow = null;
        final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
        final String CUSTOM_NS = "http://schemas.android.com/apk/res-auto";

        try {
            XmlPullParser parser = context.getResources().getXml(resourceId);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("Row".equals(tag)) {
                        currentRow = new ArrayList<>();
                        System.out.println("Parsing new Row for resource ID: " + resourceId);
                    } else if ("Key".equals(tag) && currentRow != null) {
                        String label = parser.getAttributeValue(ANDROID_NS, "keyLabel");
                        if (label == null) {
                            label = "";
                            System.out.println("Warning: android:keyLabel is missing");
                        } else {
                            try {
                                if (label.startsWith("@") && label.length() > 1) {
                                    String idStr = label.substring(1);
                                    try {
                                        int resId = Integer.parseInt(idStr);
                                        label = context.getResources().getString(resId);
                                        System.out.println("Resolved resource ID " + resId + " to: " + label);
                                    } catch (NumberFormatException e) {
                                        System.out.println("Warning: Invalid resource ID format: " + label);
                                    } catch (Resources.NotFoundException e) {
                                        System.out.println("Warning: Resource ID not found: " + label);
                                        e.printStackTrace();
                                    }
                                } else {
                                    String resourceName = label.startsWith("@string/") ? label.substring("@string/".length()) : label;
                                    int resId = context.getResources().getIdentifier(resourceName, "string", context.getPackageName());
                                    if (resId != 0) {
                                        label = context.getResources().getString(resId);
                                        System.out.println("Resolved string resource " + resourceName + " to: " + label);
                                    } else {
                                        System.out.println("Warning: String resource not found for keyLabel: " + label);
                                    }
                                }
                            } catch (Resources.NotFoundException e) {
                                System.out.println("Warning: Failed to resolve string resource: " + label);
                                e.printStackTrace();
                            }
                        }

                        String symbolLabel = parser.getAttributeValue(CUSTOM_NS, "keySymbolLabel");
                        if (symbolLabel == null) {
                            symbolLabel = "";
                            System.out.println("Warning: custom:keySymbolLabel is missing");
                        }
                        String alternates = parser.getAttributeValue(CUSTOM_NS, "keyAlternates");
                        if (alternates == null) {
                            alternates = "";
                        }
                        String cornerHint = parser.getAttributeValue(CUSTOM_NS, "keyCornerHint");
                        if (cornerHint == null) {
                            cornerHint = "";
                        }

                        String codeStr = parser.getAttributeValue(ANDROID_NS, "codes");
                        int code = 0;
                        try {
                            code = codeStr != null ? Integer.parseInt(codeStr, 16) : 0;
                        } catch (NumberFormatException e) {
                            System.out.println("Warning: Invalid android:codes value: " + codeStr);
                            e.printStackTrace();
                        }

                        String widthStr = parser.getAttributeValue(ANDROID_NS, "keyWidth");
                        float widthPercent = 10.0f;
                        if (widthStr != null) {
                            try {
                                if (widthStr.endsWith("%p")) {
                                    widthPercent = Float.parseFloat(widthStr.replace("%p", ""));
                                } else {
                                    widthPercent = Float.parseFloat(widthStr);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Warning: Invalid android:keyWidth value: " + widthStr);
                            }
                        }

                        String gapStr = parser.getAttributeValue(ANDROID_NS, "horizontalGap");
                        float horizontalGap = 0.0f;
                        if (gapStr != null) {
                            try {
                                if (gapStr.endsWith("%p")) {
                                    horizontalGap = Float.parseFloat(gapStr.replace("%p", ""));
                                } else {
                                    horizontalGap = Float.parseFloat(gapStr);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Warning: Invalid android:horizontalGap value: " + gapStr);
                            }
                        }

                        String isRepeatableStr = parser.getAttributeValue(ANDROID_NS, "isRepeatable");
                        boolean isRepeatable = false;
                        if (isRepeatableStr != null) {
                            isRepeatable = Boolean.parseBoolean(isRepeatableStr);
                        }

                        String requiresShiftStr = parser.getAttributeValue(CUSTOM_NS, "keyRequiresShift");
                        boolean requiresShift = false;
                        if (requiresShiftStr != null) {
                            requiresShift = Boolean.parseBoolean(requiresShiftStr);
                        }

                        int iconResId = 0;
                        if (label.equals("Win")) {
                            iconResId = R.drawable.windows;
                            System.out.println("Hardcoded icon for Win: " + iconResId);
                        } else if (label.equals("BackSpace")) {
                            iconResId = R.drawable.backspace;
                            System.out.println("Hardcoded icon for BackSpace: " + iconResId);
                        } else if (label.equals("Up_arrow")) {
                            iconResId = R.drawable.caret_up_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Down_arrow")) {
                            iconResId = R.drawable.caret_down_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Left_arrow")) {
                            iconResId = R.drawable.caret_left_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Right_arrow")) {
                            iconResId = R.drawable.caret_right_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        }

//                        Log.d(TAG,"Parsed Key: label=" + label + ", symbolLabel=" + symbolLabel + ", code=0x" + Integer.toHexString(code).toUpperCase() + ", codeStr=" + codeStr + ", width=" + widthPercent + ", icon=" + iconResId + ", gap=" + horizontalGap + ", repeatable=" + isRepeatable);
                        currentRow.add(new Key(label, symbolLabel, alternates, cornerHint, code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, -1, false));
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("Row".equals(parser.getName()) && currentRow != null) {
                        if (!currentRow.isEmpty()) {
                            rows.add(currentRow);
                            System.out.println("Row added with " + currentRow.size() + " keys");
                        }
                        currentRow = null;
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            System.out.println("Error parsing keyboard XML: " + e.getMessage());
            e.printStackTrace();
        }

        if (rows.isEmpty()) {
            System.out.println("Warning: No rows parsed for keyboard resource ID: " + resourceId);
        }
        return rows;
    }

    private void updateKeyboard() {
        removeAllViews();

        List<List<Key>> currentKeys = lowerKeys;

        // In split keyboard mode, divide each row into left or right half
        if (splitPart != SPLIT_NONE) {
            List<List<Key>> splitKeys = new ArrayList<>();
            for (List<Key> row : currentKeys) {
                int mid = (row.size() + 1) / 2; // round up for odd rows
                List<Key> sideKeys = new ArrayList<>();
                if (splitPart == SPLIT_LEFT) {
                    for (int i = 0; i < mid; i++) sideKeys.add(row.get(i));
                } else {
                    for (int i = mid; i < row.size(); i++) sideKeys.add(row.get(i));
                }
                // Rescale widths to fill half of the row
                float sideWeightSum = 0;
                for (Key k : sideKeys) sideWeightSum += k.widthPercent;
                if (sideWeightSum > 0) {
                    for (Key k : sideKeys) {
                        k.widthPercent = (k.widthPercent / sideWeightSum) * 50f;
                    }
                }
                if (!sideKeys.isEmpty()) {
                    splitKeys.add(sideKeys);
                }
            }
            currentKeys = splitKeys;
        }

        // In fullscreen keyboard mode, hide arrow keys from the main keyboard area.
        // Direction keys are provided by the extra middle panel in this mode.
        if (showExtraPortraitKeys) {
            List<List<Key>> filtered = new ArrayList<>();
            for (List<Key> row : currentKeys) {
                List<Key> out = new ArrayList<>();
                for (Key k : row) {
                    if ("Up_arrow".equals(k.label)
                            || "Down_arrow".equals(k.label)
                            || "Left_arrow".equals(k.label)
                            || "Right_arrow".equals(k.label)) {
                        continue;
                    }
                    out.add(k);
                }
                if (!out.isEmpty()) {
                    filtered.add(out);
                }
            }
            currentKeys = filtered;
        }

        // Show two shortcut rows above letter keyboard in portrait normal mode
        if (!showExtraPortraitKeys && splitPart == SPLIT_NONE) {
            addTopFunctionRows();
        }

        // In landscape fullscreen mode, always show the top scrolling panel
        if (showExtraPortraitKeys && isLandscape(getContext()) && splitPart == SPLIT_NONE) {
            addTopFunctionRows();
        }

        String[] functionalKeyCodes = {"46", "47", "48", "49", "4A", "4B", "4C", "4D", "4E", "3B", "3C", "3D", "3E", "3F", "29", "3A", "40", "41", "42", "43", "44", "45", "3D", "3F"};

        for (List<Key> row : currentKeys) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            // Use weight=1 so each row shares the available height equally,
            // regardless of the keyboard container's actual pixel height.
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
            rowLayout.setOrientation(HORIZONTAL);

            float totalWeight = 0;
            for (Key key : row) {
                totalWeight += key.widthPercent / 10.0f;
            }

            for (Key key : row) {
                View button;
                View listenerTarget;
                float weight = key.widthPercent / 10.0f;
                LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
                int keyMargin = dpToPx(KEY_OUTER_MARGIN_DP);
                params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin);

                if (key.horizontalGap > 0) {
                    params.setMargins((int)(key.horizontalGap * getContext().getResources().getDisplayMetrics().widthPixels / 100), keyMargin, keyMargin, keyMargin);
                }

                boolean isFunctionalKey = false;
                for (String functionCode : functionalKeyCodes) {
                    if (key.codeStr.equals(functionCode)) {
                        isFunctionalKey = true;
                        Log.d(TAG, "Matched functional key: label=" + key.label + ", code=0x" + Integer.toHexString(key.code));
                        break;
                    }
                }

                if (key.label.equals("Win") || key.label.equals("Cmd") || key.label.equals("Super") || key.label.equals("BackSpace") ||
                        key.label.equals("Up_arrow") || key.label.equals("Down_arrow") ||
                        key.label.equals("Left_arrow") || key.label.equals("Right_arrow")) {
                    ImageButton imageButton = new ImageButton(getContext());
                    applyFlatKeyStyle(imageButton);
                    imageButton.setLayoutParams(params);
                    if (key.code == 0xE3 && isWinLeftLocked) {
                        imageButton.setBackgroundResource(R.drawable.press_button_background);
                    } else {
                        imageButton.setBackgroundResource(R.drawable.key_background);
                    }
                    if (key.iconResId != 0) {
                        imageButton.setImageResource(key.iconResId);
                        imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                        if (isBackspaceKey(key)) {
                            imageButton.setScaleX(isShiftLeftLocked ? -1f : 1f);
                        }
                        if ("Win".equals(key.label) || "Cmd".equals(key.label) || "Super".equals(key.label) || "BackSpace".equals(key.label)) {
                            imageButton.setColorFilter(resolveThemeTextColor());
                        }
                    }
                    imageButton.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    button = imageButton;
                    listenerTarget = imageButton;
                } else {
                    Button textButton = new Button(getContext());
                    applyFlatKeyStyle(textButton);
                    textButton.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    textButton.setBackgroundResource(R.drawable.key_background);
                    textButton.setGravity(Gravity.CENTER);
                    textButton.setTextSize(14);
                    textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

                    if (isFunctionalKey) {
                        textButton.setBackgroundResource(R.drawable.function_button_background);
                    } else if (key.code == 0xE1 && isShiftLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isShiftLeftLocked);
                    } else if (key.code == KEY_MODE_FN && isFnLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 0xE0 && isCtrlLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 0xE2 && isAltLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 16) {
                        textButton.setBackgroundResource(R.drawable.key_background);
                    }

                    String fnDisplayLabel = getFnDisplayLabel(key);
                    int fnDisplayIconResId = getFnDisplayIconResId(key);
                    if (fnDisplayIconResId != 0) {
                        textButton.setText("");
                        textButton.setSingleLine(true);
                        textButton.setMaxLines(1);
                        textButton.setEllipsize(null);
                        textButton.setGravity(Gravity.CENTER);
                        textButton.setPadding(0, 0, 0, 0);
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, fnDisplayIconResId, 0, 0);
                        textButton.setCompoundDrawablePadding(0);
                        textButton.setTextColor(resolveThemeTextColor());
                        android.graphics.drawable.Drawable topDrawable = textButton.getCompoundDrawables()[1];
                        if (topDrawable != null) {
                            topDrawable.setTint(resolveThemeTextColor());
                        }
                    } else if (!TextUtils.isEmpty(fnDisplayLabel)) {
                        textButton.setText(fnDisplayLabel);
                        textButton.setSingleLine(true);
                        textButton.setMaxLines(1);
                        textButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        textButton.setTextSize(getFnLabelTextSizeSp(fnDisplayLabel));
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        textButton.setTextColor(resolveThemeTextColor());
                    } else if (key.iconResId == 0 && !key.label.isEmpty()) {
                        String displayLabel = key.label;
                        String symbolLabel = key.symbolLabel;
                        boolean showAlternateLabel = isShiftLeftLocked || isSymbolMode;

                        if (!showAlternateLabel && displayLabel.contains("\n")) {
                            String[] parts = displayLabel.split("\n");
                            if (parts.length == 2) {
                                symbolLabel = parts[0];
                                displayLabel = parts[1];
                            }
                            String combinedText = symbolLabel + "\n" + displayLabel;
                            SpannableString spannable = new SpannableString(combinedText);
                            textButton.setText(spannable);
                            textButton.setTextColor(resolveThemeTextColor());
                            Log.d(TAG, "Applied Spannable: combinedText=" + combinedText + ", symbolLabel=" + symbolLabel + ", displayLabel=" + displayLabel);
                        } else {
                            textButton.setText(showAlternateLabel && !symbolLabel.isEmpty() ? symbolLabel : displayLabel);
                            textButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                            textButton.setTextColor(resolveThemeTextColor());
                        }
                    }

                    if (key.iconResId != 0) {
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, key.iconResId, 0, 0);
                        textButton.setCompoundDrawablePadding(dpToPx(4));
                    }
                    if (!TextUtils.isEmpty(key.cornerHint)
                            && TextUtils.isEmpty(fnDisplayLabel)
                            && fnDisplayIconResId == 0) {
                        FrameLayout keyContainer = new FrameLayout(getContext());
                        keyContainer.setLayoutParams(params);
                        keyContainer.addView(textButton);

                        TextView cornerHintView = new TextView(getContext());
                        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
                        hintParams.setMargins(0, dpToPx(3), dpToPx(6), 0);
                        cornerHintView.setLayoutParams(hintParams);
                        cornerHintView.setText(key.cornerHint);
                        cornerHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                        cornerHintView.setTextColor(resolveThemeTextColor());
                        cornerHintView.setAlpha(0.2f);
                        cornerHintView.setTypeface(cornerHintView.getTypeface(), android.graphics.Typeface.BOLD);
                        cornerHintView.setIncludeFontPadding(false);
                        cornerHintView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cornerHintView.setPadding(0, 0, 0, 0);
                        cornerHintView.setClickable(false);
                        cornerHintView.setFocusable(false);
                        keyContainer.addView(cornerHintView);
                        cornerHintView.bringToFront();

                        button = keyContainer;
                    } else {
                        textButton.setLayoutParams(params);
                        button = textButton;
                    }
                    listenerTarget = textButton;
                }

                attachKeyListeners(listenerTarget, key);
                rowLayout.addView(button);
            }
            addView(rowLayout);
        }

        if (showExtraPortraitKeys && !isLandscape(getContext())) {
            addExtraPortraitKeys();
        }
    }

    /** Attaches click + touch + long-click listeners to a key view. */
    private void attachKeyListeners(View btn, Key key) {
        final boolean[] longPressConsumed = new boolean[]{false};
        btn.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    longPressConsumed[0] = false;
                    performKeyHapticFeedback(v);
                    if (shouldEnableAlternates(key)) {
                        final float downRawX = event.getRawX();
                        final float downRawY = event.getRawY();
                        final Runnable openAlternates = () -> showAlternatesPopup(v, key, downRawX);
                        v.setTag(openAlternates);
                        longPressHandler.postDelayed(openAlternates, ALT_LONG_PRESS_TIMEOUT_MS);
                    }
                    return false;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (isAlternatePopupVisible()) {
                        updateAlternateSelection(event.getRawX(), event.getRawY());
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP: {
                    v.setPressed(false);
                    Runnable pending = (Runnable) v.getTag();
                    if (pending != null) {
                        longPressHandler.removeCallbacks(pending);
                        v.setTag(null);
                    }
                    if (isAlternatePopupVisible()) {
                        commitCurrentAlternateSelection();
                        dismissAlternatesPopup();
                        repeatHandler.postDelayed(this::sendReleaseData, 30);
                        return true;
                    }
                    if (shouldRepeatOnLongPress(key)) stopRepeatingDelete();
                    if (!longPressConsumed[0] && isTouchInsideView(v, event)) {
                        handleKeyPress(key);
                    }
                    repeatHandler.postDelayed(this::sendReleaseData, 30);
                    return false;
                }
                case MotionEvent.ACTION_CANCEL: {
                    v.setPressed(false);
                    Runnable pending = (Runnable) v.getTag();
                    if (pending != null) {
                        longPressHandler.removeCallbacks(pending);
                        v.setTag(null);
                    }
                    if (isAlternatePopupVisible()) {
                        dismissAlternatesPopup();
                        return true;
                    }
                    if (shouldRepeatOnLongPress(key)) stopRepeatingDelete();
                    repeatHandler.postDelayed(this::sendReleaseData, 30);
                    return false;
                }
                default:
                    return false;
            }
        });
        if (shouldRepeatOnLongPress(key)) {
            btn.setOnLongClickListener(v -> {
                longPressConsumed[0] = true;
                startRepeatingDelete(key);
                return true;
            });
        }
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x <= view.getWidth() && y >= 0 && y <= view.getHeight();
    }

    private boolean shouldEnableAlternates(Key key) {
        if (key == null || key.isRepeatable || key.isTopPanelKey) {
            return false;
        }
        if (isFnLocked && resolveFnMapping(key) != null) {
            return false;
        }
        if (extraNumpadFnLocked && resolveExtraNumpadFnMapping(key) != null) {
            return false;
        }
        if (key.code >= 0xE0 && key.code <= 0xE7) {
            return false;
        }
        if (key.code >= 0xF001 && key.code <= 0xF006) {
            return false;
        }
        if ("Space".equalsIgnoreCase(key.label) || "Enter".equalsIgnoreCase(key.label)) {
            return false;
        }
        return key.label.length() == 1 || key.symbolLabel.length() == 1 || !TextUtils.isEmpty(key.alternates);
    }

    private static class FnMapping {
        final String label;
        final int keyCode;
        final int modifierMask;
        final int iconResId;

        FnMapping(String label, int keyCode, int modifierMask) {
            this(label, keyCode, modifierMask, 0);
        }

        FnMapping(String label, int keyCode, int modifierMask, int iconResId) {
            this.label = label;
            this.keyCode = keyCode;
            this.modifierMask = modifierMask;
            this.iconResId = iconResId;
        }
    }

    private String getFnDisplayLabel(Key key) {
        FnMapping mapping = resolveFnMapping(key);
        return mapping != null ? mapping.label : null;
    }

    private int getFnDisplayIconResId(Key key) {
        FnMapping mapping = resolveFnMapping(key);
        return mapping != null ? mapping.iconResId : 0;
    }

    private FnMapping resolveFnMapping(Key key) {
        if (!isFnLocked || key == null) {
            return null;
        }
        if (key.code >= 0xE0 && key.code <= 0xE7) {
            return null;
        }
        if (key.code == KEY_MODE_FN || key.code == 0x2A || key.code == 0x2C || key.code == 0x28 || key.code == 0x2B) {
            return null;
        }

        // Function-row mapping.
        switch (key.code) {
            case 0x14: return new FnMapping("F1", 0x3A, 0);  // q
            case 0x1A: return new FnMapping("F2", 0x3B, 0);  // w
            case 0x08: return new FnMapping("F3", 0x3C, 0);  // e
            case 0x15: return new FnMapping("F4", 0x3D, 0);  // r
            case 0x17: return new FnMapping("F5", 0x3E, 0);  // t
            case 0x1C: return new FnMapping("F6", 0x3F, 0);  // y
            case 0x18: return new FnMapping("F7", 0x40, 0);  // u
            case 0x0C: return new FnMapping("F8", 0x41, 0);  // i
            case 0x12: return new FnMapping("F9", 0x42, 0);  // o
            case 0x13: return new FnMapping("F10", 0x43, 0); // p
            case 0x04: return new FnMapping("F11", 0x44, 0); // a
            case 0x16: return new FnMapping("F12", 0x45, 0); // s (adjacent to F11)
            default: return null;
        }
    }

    private float getFnLabelTextSizeSp(String fnLabel) {
        if (fnLabel == null) {
            return 14f;
        }
        switch (fnLabel) {
            case "INS":
            case "PGUP":
            case "PGDN":
            case "HOME":
            case "END":
                return 11f;
            default:
                return 14f;
        }
    }

    private boolean shouldRepeatOnLongPress(Key key) {
        return key != null && (key.isRepeatable || isArrowKey(key));
    }

    private boolean isArrowKey(Key key) {
        if (key == null) {
            return false;
        }
        if (key.code == 0x52 || key.code == 0x51 || key.code == 0x50 || key.code == 0x4F) {
            return true;
        }
        FnMapping extra = resolveExtraNumpadFnMapping(key);
        return extra != null
                && (extra.keyCode == 0x52 || extra.keyCode == 0x51 || extra.keyCode == 0x50 || extra.keyCode == 0x4F);
    }

    private boolean isAlternatePopupVisible() {
        return alternatePopupWindow != null && alternatePopupWindow.isShowing();
    }

    private void showAlternatesPopup(View anchor, Key key, float initialRawX) {
        List<AlternateOption> options = buildAlternateOptions(key);
        if (options.size() < 2) {
            return;
        }

        dismissAlternatesPopup();
        alternateAnchorView = anchor;
        currentAlternateOptions = options;
        currentAlternateSelection = findDefaultAlternateSelection(options, key);

        alternatePopupContainer = new LinearLayout(getContext());
        alternatePopupContainer.setOrientation(LinearLayout.HORIZONTAL);
        alternatePopupContainer.setBackgroundResource(R.drawable.alternate_popup_background);
        alternatePopupContainer.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        alternateOptionViews.clear();
        for (AlternateOption option : options) {
            TextView optionView = new TextView(getContext());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            p.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            optionView.setLayoutParams(p);
            optionView.setText(option.display);
            optionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            optionView.setPadding(dpToPx(8), dpToPx(5), dpToPx(8), dpToPx(5));
            optionView.setTextColor(resolveThemeTextColor());
            alternatePopupContainer.addView(optionView);
            alternateOptionViews.add(optionView);
        }

        highlightAlternateSelection(currentAlternateSelection);

        alternatePopupWindow = new PopupWindow(alternatePopupContainer,
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false);
        alternatePopupWindow.setOutsideTouchable(false);
        alternatePopupWindow.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alternatePopupWindow.setElevation(dpToPx(12));
        }
        alternatePopupContainer.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        anchor.post(() -> {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            int popupX = (int) (loc[0] + (anchor.getWidth() / 2f) - (alternatePopupContainer.getMeasuredWidth() / 2f));
            int popupY = loc[1] - dpToPx(ALT_POPUP_VERTICAL_OFFSET_DP);
            alternatePopupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY);
            updateAlternateSelection(initialRawX, loc[1]);
        });
    }

    private List<AlternateOption> buildAlternateOptions(Key key) {
        // Keep alternates ordering predictable: symbol -> alternates -> base label.
        // Example: h with symbol H and alternate "-" => H - h.
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(key.symbolLabel) && key.symbolLabel.length() == 1) {
            labels.add(key.symbolLabel);
        }
        if (!TextUtils.isEmpty(key.alternates)) {
            String[] extra = key.alternates.split(",");
            for (String token : extra) {
                String trimmed = token.trim();
                if (trimmed.length() == 1) {
                    labels.add(trimmed);
                }
            }
        }
        if (!TextUtils.isEmpty(key.label) && key.label.length() == 1) {
            labels.add(key.label);
        }

        List<AlternateOption> result = new ArrayList<>();
        for (String label : labels) {
            AlternateOption mapped = mapAsciiAlternate(label);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private int findDefaultAlternateSelection(List<AlternateOption> options, Key key) {
        if (!TextUtils.isEmpty(key.alternates)) {
            String[] extra = key.alternates.split(",");
            for (String token : extra) {
                String trimmed = token.trim();
                if (trimmed.length() != 1) {
                    continue;
                }
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).display.equals(trimmed)) {
                        return i;
                    }
                }
            }
        }
        String baseLabel = key.label;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).display.equals(baseLabel)) {
                return i;
            }
        }
        return 0;
    }

    private void updateAlternateSelection(float rawX, float rawY) {
        if (alternatePopupContainer == null || currentAlternateOptions.isEmpty()) {
            return;
        }
        int[] loc = new int[2];
        alternatePopupContainer.getLocationOnScreen(loc);
        float localX = rawX - loc[0];
        float localY = rawY - loc[1];
        if (localY < -dpToPx(ALT_CANCEL_VERTICAL_DP) || localY > alternatePopupContainer.getHeight() + dpToPx(ALT_CANCEL_VERTICAL_DP)) {
            currentAlternateSelection = -1;
            highlightAlternateSelection(-1);
            return;
        }
        int nextIndex = -1;
        for (int i = 0; i < alternateOptionViews.size(); i++) {
            View option = alternateOptionViews.get(i);
            if (localX >= option.getLeft() && localX <= option.getRight()) {
                nextIndex = i;
                break;
            }
        }
        if (nextIndex == -1 && !alternateOptionViews.isEmpty()) {
            if (localX < alternateOptionViews.get(0).getLeft()) {
                nextIndex = 0;
            } else if (localX > alternateOptionViews.get(alternateOptionViews.size() - 1).getRight()) {
                nextIndex = alternateOptionViews.size() - 1;
            }
        }
        currentAlternateSelection = nextIndex;
        highlightAlternateSelection(nextIndex);
    }

    private void highlightAlternateSelection(int selectedIndex) {
        for (int i = 0; i < alternateOptionViews.size(); i++) {
            TextView view = alternateOptionViews.get(i);
            if (i == selectedIndex) {
                view.setBackgroundResource(R.drawable.alternate_popup_option_selected_background);
            } else {
                view.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private void commitCurrentAlternateSelection() {
        if (currentAlternateSelection < 0 || currentAlternateSelection >= currentAlternateOptions.size()) {
            return;
        }
        sendAlternateOption(currentAlternateOptions.get(currentAlternateSelection));
    }

    private void sendAlternateOption(AlternateOption option) {
        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += isShiftLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;
        if (option.requiresShift) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        sendKeyData(combinedValue, option.keyCode);
    }

    private void dismissAlternatesPopup() {
        if (alternatePopupWindow != null) {
            alternatePopupWindow.dismiss();
            alternatePopupWindow = null;
        }
        if (alternateAnchorView != null) {
            alternateAnchorView.setPressed(false);
            alternateAnchorView = null;
        }
        alternatePopupContainer = null;
        alternateOptionViews.clear();
        currentAlternateOptions = new ArrayList<>();
        currentAlternateSelection = -1;
    }

    private void performKeyHapticFeedback(View view) {
        if (view == null || getContext() == null) {
            return;
        }
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("haptic_feedback", true);
        if (!enabled) {
            return;
        }
        view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        );
    }

    private AlternateOption mapAsciiAlternate(String token) {
        if (TextUtils.isEmpty(token) || token.length() != 1) {
            return null;
        }
        char c = token.charAt(0);
        if (c >= 'a' && c <= 'z') {
            return new AlternateOption(token, 0x04 + (c - 'a'), false);
        }
        if (c >= 'A' && c <= 'Z') {
            return new AlternateOption(token, 0x04 + (c - 'A'), true);
        }
        switch (c) {
            case '1': return new AlternateOption(token, 0x1E, false);
            case '!': return new AlternateOption(token, 0x1E, true);
            case '2': return new AlternateOption(token, 0x1F, false);
            case '@': return new AlternateOption(token, 0x1F, true);
            case '3': return new AlternateOption(token, 0x20, false);
            case '#': return new AlternateOption(token, 0x20, true);
            case '4': return new AlternateOption(token, 0x21, false);
            case '$': return new AlternateOption(token, 0x21, true);
            case '5': return new AlternateOption(token, 0x22, false);
            case '%': return new AlternateOption(token, 0x22, true);
            case '6': return new AlternateOption(token, 0x23, false);
            case '^': return new AlternateOption(token, 0x23, true);
            case '7': return new AlternateOption(token, 0x24, false);
            case '&': return new AlternateOption(token, 0x24, true);
            case '8': return new AlternateOption(token, 0x25, false);
            case '*': return new AlternateOption(token, 0x25, true);
            case '9': return new AlternateOption(token, 0x26, false);
            case '(': return new AlternateOption(token, 0x26, true);
            case '0': return new AlternateOption(token, 0x27, false);
            case ')': return new AlternateOption(token, 0x27, true);
            case '-': return new AlternateOption(token, 0x2D, false);
            case '_': return new AlternateOption(token, 0x2D, true);
            case '=': return new AlternateOption(token, 0x2E, false);
            case '+': return new AlternateOption(token, 0x2E, true);
            case '[': return new AlternateOption(token, 0x2F, false);
            case '{': return new AlternateOption(token, 0x2F, true);
            case ']': return new AlternateOption(token, 0x30, false);
            case '}': return new AlternateOption(token, 0x30, true);
            case ',': return new AlternateOption(token, 0x36, false);
            case '<': return new AlternateOption(token, 0x36, true);
            case '.': return new AlternateOption(token, 0x37, false);
            case '>': return new AlternateOption(token, 0x37, true);
            case '/': return new AlternateOption(token, 0x38, false);
            case '?': return new AlternateOption(token, 0x38, true);
            case ';': return new AlternateOption(token, 0x33, false);
            case ':': return new AlternateOption(token, 0x33, true);
            case '\'': return new AlternateOption(token, 0x34, false);
            case '"': return new AlternateOption(token, 0x34, true);
            case '`': return new AlternateOption(token, 0x35, false);
            case '~': return new AlternateOption(token, 0x35, true);
            default: return null;
        }
    }

    /**
     * Builds the number-pad subview as a custom nested layout:
     *   Top section (weight 3): left operator panel (+,-,*,/) + number grid (1-9 + right column)
     *   Bottom row (weight 1): ABC , !?# 0 = . Enter
     */
    private void buildNumberPadLayout() {
        int m = dpToPx(KEY_OUTER_MARGIN_DP);

        // Single horizontal section: op panel (left, weight 1) + num grid (right, weight 9)
        LinearLayout mainSection = new LinearLayout(getContext());
        mainSection.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
        mainSection.setOrientation(LinearLayout.HORIZONTAL);

        // ── Left panel: +, -, *, ABC (4 equal rows) ──────────────────
        LinearLayout opPanel = new LinearLayout(getContext());
        opPanel.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));
        opPanel.setOrientation(LinearLayout.VERTICAL);

        // Row 4 of op panel is ABC (function); rows 1-3 are +, -, *
        String[] opLabels   = {"+", "-", "*", "ABC"};
        int[]    opCodes    = {0x2E, 0x2D, 0x25, 0xF002};
        String[] opCodeStrs = {"2E",  "2D",  "25",  "F002"};
        boolean[] opShift   = {true,  false, true,  false};
        boolean[] opIsFn    = {false, false, false, true};
        for (int i = 0; i < opLabels.length; i++) {
            Key k = new Key(opLabels[i], "", opCodes[i], opCodeStrs[i], 10.0f, 0, 0f, false, opShift[i]);
            Button btn = new Button(getContext());
            applyFlatKeyStyle(btn);
            LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
            p.setMargins(m, m, m, m);
            btn.setLayoutParams(p);
            btn.setBackgroundResource(opIsFn[i]
                    ? R.drawable.function_button_background
                    : R.drawable.key_background);
            btn.setGravity(Gravity.CENTER);
            btn.setTextSize(12);
            btn.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            btn.setText(k.label);
            btn.setTextColor(resolveThemeTextColor());
            attachKeyListeners(btn, k);
            opPanel.addView(btn);
        }
        mainSection.addView(opPanel);

        // ── Number grid: 4 equal rows ─────────────────────────────────
        // Right column (%, Space, ⌫, Enter) all have weight 1 → same column width.
        // Number keys have weight 2. Row 4: [/(w2), 0(w2), !?#(w2), Enter(w1)]
        // so 0 sits directly under 8.
        LinearLayout numGrid = new LinearLayout(getContext());
        numGrid.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 9.0f));
        numGrid.setOrientation(LinearLayout.VERTICAL);

        Key[][] numRows = {
            {
                new Key("1", "", 0x1E, "1E", 20.0f, 0, 0f, false),
                new Key("2", "", 0x1F, "1F", 20.0f, 0, 0f, false),
                new Key("3", "", 0x20, "20", 20.0f, 0, 0f, false),
                new Key("%", "", 0x22, "22", 10.0f, 0, 0f, false, true)
            },
            {
                new Key("4", "", 0x21, "21", 20.0f, 0, 0f, false),
                new Key("5", "", 0x22, "22", 20.0f, 0, 0f, false),
                new Key("6", "", 0x23, "23", 20.0f, 0, 0f, false),
                new Key("Space", "", 0x2C, "2C", 10.0f, 0, 0f, false)
            },
            {
                new Key("7", "", 0x24, "24", 20.0f, 0, 0f, false),
                new Key("8", "", 0x25, "25", 20.0f, 0, 0f, false),
                new Key("9", "", 0x26, "26", 20.0f, 0, 0f, false),
                new Key("BackSpace", "", 0x2A, "2A", 10.0f, R.drawable.backspace, 0f, true)
            },
            {
                // / under 7, 0 under 8 (same weight=2), !?# under 9, Enter in right col
                new Key("/",    "", 0x38,   "38",   20.0f, 0, 0f, false),
                new Key("0",    "", 0x27,   "27",   20.0f, 0, 0f, false),
                new Key("!?#",  "", 0xF004, "F004", 20.0f, 0, 0f, false),
                new Key("Enter","", 0x28,   "28",   10.0f, 0, 0f, false)
            }
        };

        for (Key[] rowKeys : numRows) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (Key k : rowKeys) {
                float weight = k.widthPercent / 10.0f;
                LayoutParams p = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
                p.setMargins(m, m, m, m);
                View btn;
                if ("BackSpace".equals(k.label)) {
                    ImageButton ib = new ImageButton(getContext());
                    applyFlatKeyStyle(ib);
                    ib.setLayoutParams(p);
                    ib.setBackgroundResource(R.drawable.key_background);
                    ib.setImageResource(k.iconResId);
                    ib.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                    ib.setScaleX(isShiftLeftLocked ? -1f : 1f);
                    ib.setColorFilter(resolveThemeTextColor());
                    ib.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    attachKeyListeners(ib, k);
                    btn = ib;
                } else {
                    Button b = new Button(getContext());
                    applyFlatKeyStyle(b);
                    b.setLayoutParams(p);
                    b.setGravity(Gravity.CENTER);
                    b.setTextSize(12);
                    b.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                    b.setText(k.label);
                    b.setTextColor(resolveThemeTextColor());
                    if ("Space".equals(k.label) || k.code == 0xF004 || k.code == 0x28) {
                        b.setBackgroundResource(R.drawable.function_button_background);
                    } else {
                        b.setBackgroundResource(R.drawable.key_background);
                    }
                    attachKeyListeners(b, k);
                    btn = b;
                }
                rowLayout.addView(btn);
            }
            numGrid.addView(rowLayout);
        }
        mainSection.addView(numGrid);
        addView(mainSection);
    }

    /**
     * Adds compact shortcut rows above the letter keyboard.
     */
    private void addTopFunctionRows() {
        rebuildTopShortcutPanels();
        if (topShortcutPanels.isEmpty()) {
            return;
        }

        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }

        FrameLayout viewport = new FrameLayout(getContext());
        viewport.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, TOP_PANEL_TOTAL_WEIGHT));
        topPanelViewport = viewport;
        initializeTopPanelViewport();
        addView(viewport);
    }

    private void initializeTopPanelViewport() {
        if (topPanelViewport == null) {
            return;
        }

        topPanelViewport.removeAllViews();
        previousTopPanelContainer = createTopShortcutPanelView();
        activeTopPanelContainer = createTopShortcutPanelView();
        nextTopPanelContainer = createTopShortcutPanelView();
        topPanelViewport.addView(previousTopPanelContainer);
        topPanelViewport.addView(activeTopPanelContainer);
        topPanelViewport.addView(nextTopPanelContainer);
        syncTopPanelViewportContent();
        topPanelViewport.post(() -> resetTopPanelPositions(0f));
    }

    private void syncTopPanelViewportContent() {
        bindTopShortcutPanelView(previousTopPanelContainer,
                topPanelPageIndex > 0 ? topShortcutPanels.get(topPanelPageIndex - 1) : null);
        bindTopShortcutPanelView(activeTopPanelContainer, topShortcutPanels.get(topPanelPageIndex));
        bindTopShortcutPanelView(nextTopPanelContainer,
                topPanelPageIndex < topShortcutPanels.size() - 1 ? topShortcutPanels.get(topPanelPageIndex + 1) : null);
        activeTopPanelContainer.bringToFront();
    }

    private LinearLayout createTopShortcutPanelView() {
        LinearLayout topPanelContainer = new LinearLayout(getContext());
        topPanelContainer.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
        topPanelContainer.setOrientation(VERTICAL);
        topPanelContainer.setOnTouchListener(createTopPanelTouchListener(null));
        return topPanelContainer;
    }

    private void bindTopShortcutPanelView(LinearLayout topPanelContainer, TopShortcutPanel panel) {
        if (topPanelContainer == null) {
            return;
        }
        topPanelContainer.removeAllViews();
        if (panel == null) {
            topPanelContainer.setVisibility(View.INVISIBLE);
            return;
        }
        topPanelContainer.setVisibility(View.VISIBLE);
        addShortcutPanelRows(topPanelContainer, panel.keys);
    }

    private void rebuildTopShortcutPanels() {
        topShortcutPanels.clear();
        topShortcutPanels.add(new TopShortcutPanel("Standard 1/2", buildStandardTopPanelPage1Keys()));
        topShortcutPanels.add(new TopShortcutPanel("Standard 2/2", buildStandardTopPanelPage2Keys()));
    }

    private void addProfilePanels(ShortcutProfileManager.ShortcutProfile profile, String panelName) {
        if (profile == null) {
            return;
        }

        List<ShortcutProfileManager.Shortcut> source = shortcutProfileManager.getMyShortcuts(profile.id);
        if (source == null || source.isEmpty()) {
            source = profile.getAllShortcutsFlat();
        }
        if (source == null || source.isEmpty()) {
            return;
        }

        for (int i = 0; i < source.size(); i += TOP_PANEL_PAGE_SIZE) {
            int end = Math.min(i + TOP_PANEL_PAGE_SIZE, source.size());
            List<Key> panelKeys = new ArrayList<>();
            for (int j = i; j < end; j++) {
                ShortcutProfileManager.Shortcut shortcut = source.get(j);
                if (shortcut == null) {
                    continue;
                }
                String label = compactShortcutName(shortcut);
                String symbol = compactShortcutSymbol(shortcut);
                panelKeys.add(new Key(
                        label,
                        symbol,
                        shortcut.keyCode,
                        String.format("%02X", shortcut.keyCode),
                        1f,
                        0,
                        0f,
                        false,
                        false,
                        shortcut.modifiers,
                        true
                ));
            }

            if (!panelKeys.isEmpty()) {
                int page = (i / TOP_PANEL_PAGE_SIZE) + 1;
                int totalPages = (int) Math.ceil((double) source.size() / TOP_PANEL_PAGE_SIZE);
                String title = totalPages > 1
                        ? panelName + " " + page + "/" + totalPages
                        : panelName;
                topShortcutPanels.add(new TopShortcutPanel(title, panelKeys));
            }
        }
    }

    private List<Key> buildStandardTopPanelPage1Keys() {
        List<Key> keys = new ArrayList<>(TOP_PANEL_PAGE_SIZE);
        int primaryModifier = "macos".equals(getTargetOs()) ? MOD_WIN : MOD_CTRL;
        keys.add(new Key("ALL",    "", 0x04, "04", 1f, R.drawable.select_all_24, 0f, false, false, primaryModifier, true));
        keys.add(new Key("COPY",   "", 0x06, "06", 1f, R.drawable.content_copy_24, 0f, false, false, primaryModifier, true));
        keys.add(new Key("CUT",    "", 0x1B, "1B", 1f, R.drawable.content_cut_24, 0f, false, false, primaryModifier, true));
        keys.add(new Key("PASTE",  "", 0x19, "19", 1f, R.drawable.content_paste_24, 0f, false, false, primaryModifier, true));
        keys.add(new Key("SAVE",   "", 0x16, "16", 1f, R.drawable.save_24, 0f, false, false, primaryModifier, true));
        keys.add(new Key("UP",     "", 0x52, "52", 1f, R.drawable.keyboard_arrow_up_24, 0f, false, false, -1, true));
        keys.add(new Key("UNDO",   "", 0x1D, "1D", 1f, R.drawable.undo_24, 0f, false, false, primaryModifier, true));

        keys.add(new Key("HOME",   "", 0x4A, "4A", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("END",    "", 0x4D, "4D", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PGUP",   "", 0x4B, "4B", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PGDN",   "", 0x4E, "4E", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("LEFT",   "", 0x50, "50", 1f, R.drawable.keyboard_arrow_left_24, 0f, false, false, -1, true));
        keys.add(new Key("DOWN",   "", 0x51, "51", 1f, R.drawable.keyboard_arrow_down_24, 0f, false, false, -1, true));
        keys.add(new Key("RIGHT",  "", 0x4F, "4F", 1f, R.drawable.keyboard_arrow_right_24, 0f, false, false, -1, true));
        keys.add(new Key("ESC",    "", 0x29, "29", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("CTRL",   "", 0xE0, "E0", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("ALT",    "", 0xE2, "E2", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("TAB",    "", 0x2B, "2B", 1f, R.drawable.keyboard_tab_24, 0f, false, false, -1, true));
        keys.add(new Key("PH1",    "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH2",    "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH3",    "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        return keys;
    }

    private List<Key> buildStandardTopPanelPage2Keys() {
        List<Key> keys = new ArrayList<>(TOP_PANEL_PAGE_SIZE);
        keys.add(new Key("PH1",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH2",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH3",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH4",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH5",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH6",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH7",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH8",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH9",   "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH10",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH11",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH12",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH13",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH14",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH15",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH16",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH17",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH18",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH19",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH20",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        keys.add(new Key("PH21",  "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true));
        return keys;
    }

    private String compactShortcutName(ShortcutProfileManager.Shortcut shortcut) {
        String label = shortcut.name != null ? shortcut.name : shortcut.label;
        if (label == null || label.trim().isEmpty()) {
            label = "Key";
        }
        label = label.trim();
        return label.length() > 8 ? label.substring(0, 8) : label;
    }

    private String compactShortcutSymbol(ShortcutProfileManager.Shortcut shortcut) {
        String symbol = shortcut.label != null ? shortcut.label.trim() : "";
        if (symbol.isEmpty()) {
            return "";
        }
        symbol = com.openterface.keymod.util.KeyParser.displayLabel(symbol, getTargetOs());
        return symbol.length() > 10 ? symbol.substring(0, 10) : symbol;
    }

    private String getTargetOs() {
        Context ctx = getContext();
        if (ctx == null) return "macos";
        return ctx.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
    }

    private void addShortcutPanelRows(LinearLayout parent, List<Key> panelKeys) {
        int m = dpToPx(KEY_OUTER_MARGIN_DP);

        for (int rowIndex = 0; rowIndex < TOP_PANEL_ROWS; rowIndex++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, TOP_PANEL_ROW_WEIGHT));
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setOnTouchListener(createTopPanelTouchListener(null));

            for (int col = 0; col < TOP_PANEL_COLUMNS; col++) {
                int index = rowIndex * TOP_PANEL_COLUMNS + col;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
                p.setMargins(m, m, m, m);

                if (index >= panelKeys.size()) {
                    View spacer = new View(getContext());
                    spacer.setLayoutParams(p);
                    spacer.setOnTouchListener(createTopPanelTouchListener(null));
                    rowLayout.addView(spacer);
                    continue;
                }

                Key k = panelKeys.get(index);
                boolean modifierLocked = (k.code == 0xE0 && isCtrlLeftLocked)
                    || (k.code == 0xE1 && isShiftLeftLocked)
                    || (k.code == 0xE2 && isAltLeftLocked)
                    || (k.code == 0xE3 && isWinLeftLocked);
                if (k.iconResId != 0) {
                    ImageButton ib = new ImageButton(getContext());
                    applyFlatKeyStyle(ib);
                    ib.setLayoutParams(p);
                    ib.setBackgroundResource(modifierLocked
                            ? R.drawable.press_button_background
                            : R.drawable.function_button_background);
                    ib.setSelected(modifierLocked);
                    ib.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                    ib.setPadding(0, 0, 0, 0);
                    ib.setImageResource(k.iconResId);
                    ib.setColorFilter(resolveThemeTextColor());
                    ib.setTag(k);
                    ib.setOnTouchListener(createTopPanelTouchListener(k));
                    rowLayout.addView(ib);
                } else {
                    Button b = new Button(getContext());
                    applyFlatKeyStyle(b);
                    b.setLayoutParams(p);
                    b.setBackgroundResource(modifierLocked
                        ? R.drawable.press_button_background
                        : R.drawable.function_button_background);
                    b.setSelected(modifierLocked);
                    b.setGravity(Gravity.CENTER);
                    b.setTextSize(10);
                    b.setPadding(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
                    String topButtonText = k.symbolLabel != null && !k.symbolLabel.isEmpty()
                        ? k.symbolLabel + "\n" + k.label
                        : k.label;
                    b.setText(topButtonText);
                    b.setTextColor(resolveThemeTextColor());
                    b.setAllCaps(false);
                    if ("ESC".equals(k.label)
                            || "CTRL".equals(k.label)
                            || "ALT".equals(k.label)
                            || "HOME".equals(k.label)
                            || "END".equals(k.label)
                            || "PGUP".equals(k.label)
                            || "PGDN".equals(k.label)) {
                        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
                    }
                    b.setTag(k);
                    b.setOnTouchListener(createTopPanelTouchListener(k));
                    rowLayout.addView(b);
                }
            }
            parent.addView(rowLayout);
        }
    }

    private OnTouchListener createTopPanelTouchListener(Key key) {
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final boolean[] isDragging = new boolean[1];
        final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final int swipeThreshold = dpToPx(56);

        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
                    cancelTopPanelAnimations();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    if (!isDragging[0] && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        isDragging[0] = true;
                    }
                    if (isDragging[0] && activeTopPanelContainer != null) {
                        updateTopPanelDrag(applyTopPanelEdgeResistance(dx));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    float totalDx = event.getRawX() - startX[0];
                    if (isDragging[0]) {
                        finishTopPanelSwipe(totalDx, swipeThreshold);
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && key != null) {
                        performKeyHapticFeedback(v);
                        v.performClick();
                        handleKeyPress(key);
                        repeatHandler.postDelayed(this::sendReleaseData, 30);
                    }
                    return true;
                default:
                    return false;
            }
        };
    }

    private float applyTopPanelEdgeResistance(float translationX) {
        boolean draggingPastFirst = topPanelPageIndex == 0 && translationX > 0;
        boolean draggingPastLast = topPanelPageIndex == topShortcutPanels.size() - 1 && translationX < 0;
        if (draggingPastFirst || draggingPastLast) {
            return translationX * 0.35f;
        }
        return translationX;
    }

    private void finishTopPanelSwipe(float translationX, int swipeThreshold) {
        if (activeTopPanelContainer == null || topPanelViewport == null) {
            return;
        }

        boolean moveToNext = translationX < -swipeThreshold && topPanelPageIndex < topShortcutPanels.size() - 1;
        boolean moveToPrevious = translationX > swipeThreshold && topPanelPageIndex > 0;
        if (!moveToNext && !moveToPrevious) {
            animateTopPanelToRest();
            return;
        }

        int targetPage = moveToNext ? topPanelPageIndex + 1 : topPanelPageIndex - 1;
        float width = getTopPanelWidth();
        if (moveToNext && nextTopPanelContainer != null) {
            LinearLayout recycledContainer = previousTopPanelContainer;
            activeTopPanelContainer.animate().translationX(-width).setDuration(140).start();
            nextTopPanelContainer.animate()
                    .translationX(0f)
                    .setDuration(140)
                    .withEndAction(() -> {
                        topPanelPageIndex = targetPage;
                        previousTopPanelContainer = activeTopPanelContainer;
                        activeTopPanelContainer = nextTopPanelContainer;
                        nextTopPanelContainer = recycledContainer;
                        bindTopShortcutPanelView(nextTopPanelContainer,
                                topPanelPageIndex < topShortcutPanels.size() - 1
                                        ? topShortcutPanels.get(topPanelPageIndex + 1)
                                        : null);
                        resetTopPanelPositions(0f);
                        activeTopPanelContainer.bringToFront();
                    })
                    .start();
            if (previousTopPanelContainer != null) {
                previousTopPanelContainer.animate().translationX(-2f * width).setDuration(140).start();
            }
            return;
        }
        if (moveToPrevious && previousTopPanelContainer != null) {
            LinearLayout recycledContainer = nextTopPanelContainer;
            activeTopPanelContainer.animate().translationX(width).setDuration(140).start();
            previousTopPanelContainer.animate()
                    .translationX(0f)
                    .setDuration(140)
                    .withEndAction(() -> {
                        topPanelPageIndex = targetPage;
                        nextTopPanelContainer = activeTopPanelContainer;
                        activeTopPanelContainer = previousTopPanelContainer;
                        previousTopPanelContainer = recycledContainer;
                        bindTopShortcutPanelView(previousTopPanelContainer,
                                topPanelPageIndex > 0
                                        ? topShortcutPanels.get(topPanelPageIndex - 1)
                                        : null);
                        resetTopPanelPositions(0f);
                        activeTopPanelContainer.bringToFront();
                    })
                    .start();
            if (nextTopPanelContainer != null) {
                nextTopPanelContainer.animate().translationX(2f * width).setDuration(140).start();
            }
            return;
        }

        animateTopPanelToRest();
    }

    private void cancelTopPanelAnimations() {
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.animate().cancel();
        }
        if (activeTopPanelContainer != null) {
            activeTopPanelContainer.animate().cancel();
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.animate().cancel();
        }
    }

    private void updateTopPanelDrag(float translationX) {
        if (activeTopPanelContainer == null) {
            return;
        }
        float width = getTopPanelWidth();
        activeTopPanelContainer.setTranslationX(translationX);
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.setTranslationX(translationX - width);
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.setTranslationX(translationX + width);
        }
    }

    private void resetTopPanelPositions(float activeTranslationX) {
        if (activeTopPanelContainer == null) {
            return;
        }
        float width = getTopPanelWidth();
        activeTopPanelContainer.setTranslationX(activeTranslationX);
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.setTranslationX(activeTranslationX - width);
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.setTranslationX(activeTranslationX + width);
        }
    }

    private void animateTopPanelToRest() {
        if (activeTopPanelContainer == null) {
            return;
        }
        float width = getTopPanelWidth();
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.animate().translationX(-width).setDuration(140).start();
        }
        activeTopPanelContainer.animate().translationX(0f).setDuration(140).start();
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.animate().translationX(width).setDuration(140).start();
        }
    }

    private void refreshVisibleTopPanelButtonStates() {
        refreshTopPanelButtonStates(previousTopPanelContainer);
        refreshTopPanelButtonStates(activeTopPanelContainer);
        refreshTopPanelButtonStates(nextTopPanelContainer);
    }

    private void refreshTopPanelButtonStates(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof Button) {
            Object tag = view.getTag();
            if (tag instanceof Key) {
                Key key = (Key) tag;
                boolean modifierLocked = (key.code == 0xE0 && isCtrlLeftLocked)
                        || (key.code == 0xE1 && isShiftLeftLocked)
                        || (key.code == 0xE2 && isAltLeftLocked)
                        || (key.code == 0xE3 && isWinLeftLocked);
                view.setBackgroundResource(modifierLocked
                        ? R.drawable.press_button_background
                        : R.drawable.function_button_background);
                view.setSelected(modifierLocked);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshTopPanelButtonStates(group.getChildAt(i));
            }
        }
    }

    private float getTopPanelWidth() {
        if (topPanelViewport != null && topPanelViewport.getWidth() > 0) {
            return topPanelViewport.getWidth();
        }
        if (activeTopPanelContainer != null && activeTopPanelContainer.getWidth() > 0) {
            return activeTopPanelContainer.getWidth();
        }
        return getWidth();
    }

    private void addExtraPortraitKeys() {
        int primaryModifier = "macos".equals(getTargetOs()) ? MOD_WIN : MOD_CTRL;
        List<ExtraGridKey> gridKeys = new ArrayList<>();

        // Row 1 (was row 3)
        gridKeys.add(new ExtraGridKey(new Key("ALL", "", 0x04, "04", 25f, R.drawable.select_all_24, 0f, false, false, primaryModifier, false), 0, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("COPY", "", 0x06, "06", 25f, R.drawable.content_copy_24, 0f, false, false, primaryModifier, false), 0, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("CUT", "", 0x1B, "1B", 25f, R.drawable.content_cut_24, 0f, false, false, primaryModifier, false), 0, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("PASTE", "", 0x19, "19", 25f, R.drawable.content_paste_24, 0f, false, false, primaryModifier, false), 0, 6, 1, 2));

        // Row 2 (was row 4): ESC (Fn → NumLock), # (Fn → |), Undo (Fn → Redo), Backspace
        gridKeys.add(new ExtraGridKey(new Key("ESC", "", 0x29, "29", 25f, 0, 0f, false), 1, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("#", "", 0x20, "20", 25f, 0, 0f, false, false, MOD_SHIFT, false), 1, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("UNDO", "", 0x1D, "1D", 25f, R.drawable.undo_24, 0f, false, false, primaryModifier, false), 1, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("BKSP", "", 0x2A, "2A", 25f, R.drawable.backspace, 0f, false), 1, 6, 1, 2));

        // Row 3 (was row 5)
        gridKeys.add(new ExtraGridKey(new Key("/", "", 0x54, "54", 25f, 0, 0f, false), 2, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("*", "", 0x55, "55", 25f, 0, 0f, false), 2, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("-", "", 0x56, "56", 25f, 0, 0f, false), 2, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("+", "", 0x57, "57", 25f, 0, 0f, false), 2, 6, 1, 2));

        // Row 4 (was row 6)
        gridKeys.add(new ExtraGridKey(new Key("7", "", 0x5F, "5F", 25f, 0, 0f, false), 3, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("8", "", 0x60, "60", 25f, 0, 0f, false), 3, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("9", "", 0x61, "61", 25f, 0, 0f, false), 3, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("=", "", 0x67, "67", 25f, 0, 0f, false), 3, 6, 1, 2));

        // Row 5 (was row 7)
        gridKeys.add(new ExtraGridKey(new Key("4", "", 0x5C, "5C", 25f, 0, 0f, false), 4, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("5", "", 0x5D, "5D", 25f, 0, 0f, false), 4, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("6", "", 0x5E, "5E", 25f, 0, 0f, false), 4, 4, 1, 2));
        // Tab (icon) primary; local Fn → Save (icon)
        gridKeys.add(new ExtraGridKey(new Key("TAB", "", 0x2B, "2B", 25f, 0, 0f, false), 4, 6, 1, 2));

        // Row 6 (was row 8)
        gridKeys.add(new ExtraGridKey(new Key("1", "", 0x59, "59", 25f, 0, 0f, false), 5, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("2", "", 0x5A, "5A", 25f, 0, 0f, false), 5, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("3", "", 0x5B, "5B", 25f, 0, 0f, false), 5, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("ENTER", "", 0x28, "28", 25f, 0, 0f, false), 5, 6, 2, 2));

        // Row 7 (was row 9)
        gridKeys.add(new ExtraGridKey(new Key("FN", "", KEY_EXTRA_NUMPAD_FN, "F006", 25f, R.drawable.ic_swap_horiz_24, 0f, false), 6, 0, 1, 1));
        gridKeys.add(new ExtraGridKey(new Key(".", "", 0x63, "63", 25f, 0, 0f, false), 6, 1, 1, 1));
        gridKeys.add(new ExtraGridKey(new Key("0", "", 0x62, "62", 25f, 0, 0f, false), 6, 2, 1, 4));

        addExtraGrid(7, 8, gridKeys);
    }

    private void addExtraGrid(int rows, int columns, List<ExtraGridKey> gridKeys) {
        extraNumpadGrid = new GridLayout(getContext());
        GridLayout gridLayout = extraNumpadGrid;
        extraNumpadFnButton = null;
        gridLayout.setColumnCount(columns);
        gridLayout.setRowCount(rows);
        gridLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 9.6f));

        for (ExtraGridKey entry : gridKeys) {
            GridLayout.Spec rowSpec = GridLayout.spec(entry.row, entry.rowSpan, getExtraGridRowSpanWeight(entry.row, entry.rowSpan));
            GridLayout.Spec colSpec = GridLayout.spec(entry.col, entry.colSpan, (float) entry.colSpan);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
            params.width = 0;
            params.height = 0;
            params.setGravity(Gravity.FILL);
            int keyMargin = dpToPx(KEY_OUTER_MARGIN_DP);
            params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin);
            if (entry.key.iconResId != 0) {
                ImageButton iconButton = new ImageButton(getContext());
                applyFlatKeyStyle(iconButton);
                iconButton.setBackgroundResource(R.drawable.function_button_background);
                iconButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                int iconPadDp = entry.key.code == 0x2A ? 4 : 6;
                iconButton.setPadding(dpToPx(iconPadDp), dpToPx(iconPadDp), dpToPx(iconPadDp), dpToPx(iconPadDp));
                iconButton.setImageResource(entry.key.iconResId);
                iconButton.setColorFilter(resolveThemeTextColor());
                iconButton.setLayoutParams(params);
                iconButton.setTag(entry.key);
                if (entry.key.code == KEY_EXTRA_NUMPAD_FN) {
                    iconButton.setContentDescription("Fn");
                    extraNumpadFnButton = iconButton;
                    iconButton.setSelected(extraNumpadFnLocked);
                }
                iconButton.setOnClickListener(v -> handleKeyPress(entry.key));
                iconButton.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        performKeyHapticFeedback(v);
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        repeatHandler.postDelayed(() -> {
                            sendReleaseData();
                            Log.d(TAG, "Sent key release for extra key: " + entry.key.label);
                        }, 30);
                    }
                    return false;
                });
                gridLayout.addView(iconButton);
            } else {
                Button textButton = new Button(getContext());
                applyFlatKeyStyle(textButton);
                textButton.setBackgroundResource(R.drawable.function_button_background);
                textButton.setGravity(Gravity.CENTER);
                textButton.setTextSize(14);
                textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                textButton.setText(entry.key.label);
                textButton.setTextColor(resolveThemeTextColor());
                textButton.setTag(entry.key);
                styleExtraNumpadGridKeyButton(textButton, entry.key.label);
                textButton.setLayoutParams(params);
                textButton.setOnClickListener(v -> handleKeyPress(entry.key));
                textButton.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        performKeyHapticFeedback(v);
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        repeatHandler.postDelayed(() -> {
                            sendReleaseData();
                            Log.d(TAG, "Sent key release for extra key: " + entry.key.label);
                        }, 30);
                    }
                    return false;
                });
                gridLayout.addView(textButton);
            }
        }

        addView(gridLayout);
        refreshExtraNumpadGridUi();
    }

    /**
     * Row-height ratio for extra numpad grid (1-based UI rows 1–2 vs 3–7 map to row indices 0–1 vs 2–6):
     * total(row0–row1) : total(row2–row6) = 1 : 5
     * -> top two rows weight 0.5 each; lower rows weight 1.0 each.
     */
    private float getExtraGridRowSpanWeight(int row, int rowSpan) {
        float total = 0f;
        for (int i = 0; i < rowSpan; i++) {
            int r = row + i;
            total += r <= 1 ? 0.5f : 1.0f;
        }
        return total;
    }

    private static boolean isExtraNumpadArrowDirectionLabel(String label) {
        return "UP".equals(label) || "LEFT".equals(label) || "DOWN".equals(label) || "RIGHT".equals(label);
    }

    /**
     * Centers an icon in the key face (arrows use larger dp; Save/Undo/Tab match top shortcut row assets).
     */
    private void applyExtraNumpadGridIconOverlay(Button b, int iconResId, String contentDescription, int iconSizeDp) {
        b.setContentDescription(contentDescription);
        b.setText("");
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setCompoundDrawables(null, null, null, null);
        b.setCompoundDrawablePadding(0);
        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        if (icon == null) {
            b.setForeground(null);
            return;
        }
        icon = icon.mutate();
        icon.setTint(resolveThemeTextColor());
        int sizePx = dpToPx(iconSizeDp);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{
                new ColorDrawable(Color.TRANSPARENT),
                icon
        });
        layers.setLayerGravity(1, Gravity.CENTER);
        layers.setLayerWidth(1, sizePx);
        layers.setLayerHeight(1, sizePx);
        b.setForeground(layers);
        b.setTextColor(resolveThemeTextColor());
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.NORMAL);
    }

    private void clearExtraNumpadArrowIconOverlay(Button b) {
        if (b != null) {
            b.setForeground(null);
        }
    }

    private boolean isExtraNumpadDenseGlyphLabel(String label) {
        if (label == null) {
            return false;
        }
        if (label.length() == 1) {
            char c = label.charAt(0);
            if (c >= '0' && c <= '9') {
                return true;
            }
            switch (c) {
                case '/':
                case '*':
                case '-':
                case '+':
                case '=':
                case ',':
                case '.':
                case '$':
                case '¥':
                case '€':
                case '£':
                case '%':
                case '#':
                case '|':
                    return true;
                default:
                    return false;
            }
        }
        return "000".equals(label);
    }

    /** Typography for one extra numpad text key (digits, ops, punctuation, shortcuts). */
    private void styleExtraNumpadGridKeyButton(Button b, String displayLabel) {
        if (isExtraNumpadDenseGlyphLabel(displayLabel)) {
            b.setTextSize(EXTRA_NUMPAD_GRID_KEY_TEXT_SP);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        } else if ("HOME".equals(displayLabel)
                || "END".equals(displayLabel)
                || "PGUP".equals(displayLabel)
                || "PGDN".equals(displayLabel)
                || "INS".equals(displayLabel)
                || "NUM".equals(displayLabel)
                || "REDO".equals(displayLabel)
                || "ESC".equals(displayLabel)
                || "ENTER".equals(displayLabel)) {
            b.setTextSize(14);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            b.setTextSize(14);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private FnMapping resolveExtraNumpadFnMapping(Key key) {
        if (!extraNumpadFnLocked || key == null) {
            return null;
        }
        // Row 2 (ESC / # / Undo / Bksp): gate on label — UNDO shares 0x1D with Mac redo chord, use label to disambiguate.
        if ("ESC".equals(key.label) && key.code == 0x29) {
            return new FnMapping("NUM", 0x53, 0);
        }
        if ("#".equals(key.label) && key.code == 0x20) {
            return new FnMapping("|", 0x64, MOD_SHIFT, 0);
        }
        if ("BKSP".equals(key.label) && key.code == 0x2A) {
            return new FnMapping("DEL", 0x4C, 0, R.drawable.backspace);
        }
        if ("UNDO".equals(key.label) && key.code == 0x1D) {
            if ("macos".equals(getTargetOs())) {
                return new FnMapping("REDO", 0x1D, MOD_WIN | MOD_SHIFT, R.drawable.redo_24);
            }
            return new FnMapping("REDO", 0x1C, MOD_CTRL, R.drawable.redo_24);
        }
        if ("TAB".equals(key.label) && key.code == 0x2B) {
            int primaryMod = "macos".equals(getTargetOs()) ? MOD_WIN : MOD_CTRL;
            return new FnMapping("Save", 0x16, primaryMod, R.drawable.save_24);
        }
        switch (key.code) {
            // Numpad operators / = → currency & % (US HID + macOS-style Option combos for ¥ € £)
            case 0x54: return new FnMapping("$", 0x21, MOD_SHIFT);
            case 0x55: return new FnMapping("¥", 0x1C, MOD_ALT);
            case 0x56: return new FnMapping("€", 0x1F, MOD_ALT | MOD_SHIFT);
            case 0x57: return new FnMapping("£", 0x20, MOD_ALT);
            case 0x67: return new FnMapping("%", 0x22, MOD_SHIFT);
            case 0x62: return new FnMapping("000", 0x62, 0);
            case 0x60: return new FnMapping("UP", 0x52, 0, R.drawable.keyboard_arrow_up_24);
            case 0x5C: return new FnMapping("LEFT", 0x50, 0, R.drawable.keyboard_arrow_left_24);
            case 0x5A: return new FnMapping("DOWN", 0x51, 0, R.drawable.keyboard_arrow_down_24);
            case 0x5E: return new FnMapping("RIGHT", 0x4F, 0, R.drawable.keyboard_arrow_right_24);
            case 0x5F: return new FnMapping("HOME", 0x4A, 0);
            case 0x59: return new FnMapping("END", 0x4D, 0);
            case 0x61: return new FnMapping("PGUP", 0x4B, 0);
            case 0x5B: return new FnMapping("PGDN", 0x4E, 0);
            case 0x5D: return new FnMapping("INS", 0x49, 0);
            case 0x63: return new FnMapping(",", 0x36, 0);
            default:
                return null;
        }
    }

    private void refreshExtraNumpadGridUi() {
        if (extraNumpadGrid == null) {
            return;
        }
        if (extraNumpadFnButton != null) {
            extraNumpadFnButton.setSelected(extraNumpadFnLocked);
            extraNumpadFnButton.setBackgroundResource(extraNumpadFnLocked
                    ? R.drawable.press_button_background
                    : R.drawable.function_button_background);
        }
        for (int i = 0; i < extraNumpadGrid.getChildCount(); i++) {
            View child = extraNumpadGrid.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof Key)) {
                continue;
            }
            Key baseKey = (Key) tag;
            if (baseKey.code == KEY_EXTRA_NUMPAD_FN) {
                continue;
            }
            FnMapping mapping = resolveExtraNumpadFnMapping(baseKey);
            if (child instanceof ImageButton) {
                ImageButton ib = (ImageButton) child;
                if (mapping != null && mapping.iconResId != 0) {
                    ib.setImageResource(mapping.iconResId);
                    ib.setColorFilter(resolveThemeTextColor());
                } else if (baseKey.iconResId != 0) {
                    ib.setImageResource(baseKey.iconResId);
                    ib.setColorFilter(resolveThemeTextColor());
                }
                if (baseKey.code == 0x2A) {
                    boolean forwardDel = mapping != null && mapping.keyCode == 0x4C;
                    ib.setScaleX(forwardDel ? -1f : 1f);
                }
            } else if (child instanceof Button) {
                Button b = (Button) child;
                if (mapping != null) {
                    if (mapping.iconResId != 0) {
                        int iconDp = isExtraNumpadArrowDirectionLabel(mapping.label)
                                ? EXTRA_NUMPAD_FN_ARROW_ICON_DP
                                : EXTRA_NUMPAD_FN_ACTION_ICON_DP;
                        applyExtraNumpadGridIconOverlay(b, mapping.iconResId, mapping.label, iconDp);
                    } else {
                        clearExtraNumpadArrowIconOverlay(b);
                        b.setContentDescription(null);
                        b.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        b.setCompoundDrawablePadding(0);
                        int pad = dpToPx(2);
                        b.setPadding(pad, pad, pad, pad);
                        b.setText(mapping.label);
                        styleExtraNumpadGridKeyButton(b, mapping.label);
                    }
                } else {
                    clearExtraNumpadArrowIconOverlay(b);
                    b.setContentDescription(null);
                    b.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    b.setCompoundDrawablePadding(0);
                    int pad = dpToPx(2);
                    b.setPadding(pad, pad, pad, pad);
                    if ("TAB".equals(baseKey.label) && baseKey.code == 0x2B) {
                        applyExtraNumpadGridIconOverlay(b, R.drawable.keyboard_tab_24, "Tab",
                                EXTRA_NUMPAD_FN_ACTION_ICON_DP);
                    } else {
                        b.setText(baseKey.label);
                        styleExtraNumpadGridKeyButton(b, baseKey.label);
                    }
                }
            }
        }
    }

    public static byte[] hexStringToByteArray(String ByteData) {
        if (ByteData.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }

        int len = ByteData.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(ByteData.charAt(i), 16) << 4)
                    + Character.digit(ByteData.charAt(i + 1), 16));
        }
        Log.d(TAG, "Data: " + Arrays.toString(data));
        return data;
    }

    public void setPort(UsbSerialPort port) {
        this.port = port;
        Log.d(TAG, "Port set in CustomKeyboardView: " + (port != null ? "Valid" : "Null"));
    }

    public static String makeChecksum(String data) {
        int total = 0;

        for (int i = 0; i < data.length(); i += 2) {
            String byteStr = data.substring(i, Math.min(i + 2, data.length()));
            total += Integer.parseInt(byteStr, 16);
        }

        int mod = total % 256;

        return String.format("%02X", mod);
    }

    public void sendReleaseData() {
        new Thread(() -> {
            String releaseSendMSData = "57AB00020800000000000000000C";
            if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                try {
                    byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                    Thread.sleep(10);
                    bluetoothService.sendData(releaseSendKBDataBytes);
                    Log.d(TAG, "Sent Bluetooth release data");
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error sending Bluetooth release data: " + e.getMessage());
                }
            } else if (port != null) {
                try {
                    byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                    Thread.sleep(10);
                    port.write(releaseSendKBDataBytes, 20);
                    Log.d(TAG, "Sent USB release data");
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "Error sending USB release data: " + e.getMessage());
                }
            }
        }).start();
    }

    private int parseHex(String hex) {
        return hex != null ? Integer.parseInt(hex.replace("0x", ""), 16) : 0;
    }

    private void handleKeyPress(Key key) {
        Log.d(TAG, "Key pressed: label=" + key.label + ", code=" + key.code);

        if (key.code == KEY_NOOP_PLACEHOLDER) {
            return;
        }

        if (key.code == KEY_EXTRA_NUMPAD_FN) {
            extraNumpadFnLocked = !extraNumpadFnLocked;
            refreshExtraNumpadGridUi();
            if (splitPartner != null) {
                splitPartner.extraNumpadFnLocked = extraNumpadFnLocked;
                splitPartner.refreshExtraNumpadGridUi();
            }
            return;
        }

        if (key.shortcutModifiers >= 0) {
            FnMapping fnOverride = extraNumpadFnLocked ? resolveExtraNumpadFnMapping(key) : null;
            if (fnOverride == null) {
                sendShortcutWithModifiers(key.shortcutModifiers, key.code);
                return;
            }
            // Local Fn overrides row-2 # (→|) and Tab cell (→Save), etc. — continue to merged HID send below.
        }

        boolean updateRequired = false;
        switch (key.code) {
            case KEY_MODE_FN:
                isFnLocked = !isFnLocked;
                updateRequired = true;
                break;
            case 0xE1: // Shift Left
                isShiftLeftLocked = !isShiftLeftLocked;
                syncModifierStates();
                updateRequired = true;
                break;
            case 0xE0: // Ctrl Left
                isCtrlLeftLocked = !isCtrlLeftLocked;
                syncModifierStates();
                updateRequired = true;
                break;
            case 0xE2: // Alt Left
                isAltLeftLocked = !isAltLeftLocked;
                syncModifierStates();
                updateRequired = true;
                break;
            case 0xE3: // Win Left
                isWinLeftLocked = !isWinLeftLocked;
                syncModifierStates();
                updateRequired = true;
                break;
        }

        if (updateRequired) {
            if (key.isTopPanelKey && (key.code == 0xE0 || key.code == 0xE1 || key.code == 0xE2 || key.code == 0xE3)) {
                refreshVisibleTopPanelButtonStates();
            } else {
                updateKeyboard();
            }
        }

        if (key.code == KEY_MODE_FN
            || key.code == 0xE0
            || key.code == 0xE1
            || key.code == 0xE2
            || key.code == 0xE3) {
            return;
        }

        FnMapping extraNumpadFn = resolveExtraNumpadFnMapping(key);
        FnMapping fnMapping = extraNumpadFn != null ? extraNumpadFn : resolveFnMapping(key);
        int effectiveKeyCode = fnMapping != null ? fnMapping.keyCode : key.code;
        boolean effectiveShiftLocked = isShiftLeftLocked;
        int fnModifierMask = fnMapping != null ? fnMapping.modifierMask : 0;
        if (isBackspaceKey(key) && isShiftLeftLocked) {
            // Shift+Backspace switches to forward delete behavior.
            effectiveKeyCode = 0x4C;
            effectiveShiftLocked = false;
        }

        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += effectiveShiftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;
        // Extra numpad Fn mapping replaces the keycap meaning (e.g. Tab→Save); do not add base requiresShift
        // or we would send Shift+Tab instead of Tab, Shift+# alongside Fn modifiers, etc.
        if (key.requiresShift && extraNumpadFn == null) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((fnModifierMask & 0x01) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((fnModifierMask & 0x02) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((fnModifierMask & 0x04) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((fnModifierMask & 0x08) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }

        if (extraNumpadFn != null && "000".equals(extraNumpadFn.label)) {
            sendExtraNumpadTripleNumpadZero(combinedValue);
            return;
        }

        sendKeyData(combinedValue, effectiveKeyCode);
    }

    /** Fn-layer numpad 0 → three keypad-zero presses (with release between each). */
    private void sendExtraNumpadTripleNumpadZero(int modifiers) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    sendKeyData(modifiers, 0x62);
                    Thread.sleep(40);
                    sendKeyboardAllKeysReleasedSync();
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "extraNumpadTriple0").start();
    }

    private void sendKeyboardAllKeysReleasedSync() {
        final String releasePacket = "57AB00020800000000000000000C";
        byte[] bytes = hexStringToByteArray(releasePacket);
        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
        } else if (port != null) {
            try {
                port.write(bytes, 20);
            } catch (IOException e) {
                Log.e(TAG, "Keyboard release write failed: " + e.getMessage());
            }
        }
    }

    private boolean isBackspaceKey(Key key) {
        return key != null && (key.code == 0x2A || "BackSpace".equals(key.label));
    }

    private void sendShortcutWithModifiers(int shortcutModifiers, int keyCode) {
        int combinedValue = 0;
        if ((shortcutModifiers & MOD_CTRL) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((shortcutModifiers & MOD_SHIFT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((shortcutModifiers & MOD_ALT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((shortcutModifiers & MOD_WIN) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }
        sendKeyData(combinedValue, keyCode);
    }

    private void sendKeyData(int modifiers, int keyCode) {
        String sendKBData = String.format("57AB000208%02X00%02X0000000000", modifiers, keyCode);
        sendKBData += makeChecksum(sendKBData);

        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
            bluetoothService.sendData(sendKBDataBytes);
            Log.d(TAG, "Sent Bluetooth data: " + sendKBData);
        } else if (port != null) {
            try {
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                port.write(sendKBDataBytes, 20);
                Log.d(TAG, "Sent USB data: " + sendKBData);
            } catch (IOException e) {
                Log.e(TAG, "Error sending USB data: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "No connection available (Bluetooth or USB)");
        }
    }

    private void startRepeatingDelete(Key key) {
        if (isRepeating) return;
        isRepeating = true;

        repeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRepeating) {
                    handleKeyPress(key);
                    repeatHandler.postDelayed(this, 10);
                }
            }
        };

        repeatHandler.post(repeatRunnable);
    }

    private void stopRepeatingDelete() {
        isRepeating = false;
        if (repeatRunnable != null) {
            repeatHandler.removeCallbacks(repeatRunnable);
        }
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void applyFlatKeyStyle(View view) {
        if (view == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setStateListAnimator(null);
            view.setElevation(0f);
            view.setTranslationZ(0f);
        }
    }

    private int resolveThemeTextColor() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 0xFFFFFFFF : 0xFF000000;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        longPressHandler.removeCallbacksAndMessages(null);
        dismissAlternatesPopup();
        if (isServiceBound) {
            getContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
}