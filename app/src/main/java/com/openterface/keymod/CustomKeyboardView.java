package com.openterface.keymod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomKeyboardView extends LinearLayout {
    private static final String TAG = "CustomKeyboardView";
    private boolean isShiftLeftLocked = false;
    private boolean isCtrlLeftLocked = false;
    private boolean isAltLeftLocked = false;
    private boolean isWinLeftLocked = false;
    private boolean isRunning = true;
    private boolean isSymbolMode = false;
    private boolean showExtraPortraitKeys = false;
    private List<List<Key>> lowerKeys;
    private UsbSerialPort port;
    private Handler repeatHandler = new Handler();
    private Runnable repeatRunnable;
    private boolean isRepeating = false;

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
        int code;
        String codeStr;
        float widthPercent;
        int iconResId;
        float horizontalGap;
        boolean isRepeatable;

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable) {
            this.label = label;
            this.symbolLabel = symbolLabel;
            this.code = code;
            this.codeStr = codeStr;
            this.widthPercent = widthPercent;
            this.iconResId = iconResId;
            this.horizontalGap = horizontalGap;
            this.isRepeatable = isRepeatable;
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
        int keyboardResId = isLandscape ? R.xml.keyboard_lower_landscape : R.xml.keyboard_lower_portrait;
        
        Log.d(TAG, "Orientation changed: landscape=" + isLandscape + ", reloading keyboard");
        lowerKeys = parseKeyboard(getContext(), keyboardResId);
        removeAllViews();
        updateKeyboard();
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        
        // Load keyboard layout based on orientation (matching iOS behavior)
        reloadForCurrentOrientation();
        
        Log.d(TAG, "Parsed keyboard (landscape=" + isLandscape(context) + "): lowerKeys=" + lowerKeys.size());
        bindService(context);
        updateKeyboard();
    }

    public void reloadForCurrentOrientation() {
        Context context = getContext();
        int keyboardResId = isLandscape(context)
                ? R.xml.keyboard_lower_landscape
                : R.xml.keyboard_lower_portrait;
        lowerKeys = parseKeyboard(context, keyboardResId);
        removeAllViews();
        updateKeyboard();
    }

    public void setShowExtraPortraitKeys(boolean enabled) {
        if (showExtraPortraitKeys == enabled) return;
        showExtraPortraitKeys = enabled;
        removeAllViews();
        updateKeyboard();
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
                        currentRow.add(new Key(label, symbolLabel, code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable));
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
                float weight = key.widthPercent / 10.0f;
                LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
                params.setMargins(2, 2, 2, 2);

                if (key.horizontalGap > 0) {
                    params.setMargins((int)(key.horizontalGap * getContext().getResources().getDisplayMetrics().widthPixels / 100), 2, 2, 2);
                }

                boolean isFunctionalKey = false;
                for (String functionCode : functionalKeyCodes) {
                    if (key.codeStr.equals(functionCode)) {
                        isFunctionalKey = true;
                        Log.d(TAG, "Matched functional key: label=" + key.label + ", code=0x" + Integer.toHexString(key.code));
                        break;
                    }
                }

                if (key.label.equals("Win") || key.label.equals("BackSpace") ||
                        key.label.equals("Up_arrow") || key.label.equals("Down_arrow") ||
                        key.label.equals("Left_arrow") || key.label.equals("Right_arrow")) {
                    ImageButton imageButton = new ImageButton(getContext());
                    imageButton.setLayoutParams(params);
                    if (key.code == 0xE3 && isWinLeftLocked) {
                        imageButton.setBackgroundResource(R.drawable.press_button_background);
                    } else {
                        imageButton.setBackgroundResource(R.drawable.key_background);
                    }
                    if (key.iconResId != 0) {
                        imageButton.setImageResource(key.iconResId);
                        imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                    }
                    imageButton.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    button = imageButton;
                } else {
                    Button textButton = new Button(getContext());
                    textButton.setLayoutParams(params);
                    textButton.setBackgroundResource(R.drawable.key_background);
                    textButton.setGravity(Gravity.CENTER);
                    textButton.setTextSize(12);
                    textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

                    if (isFunctionalKey) {
                        textButton.setBackgroundResource(R.drawable.function_button_background);
                    } else if (key.code == 0xE1 && isShiftLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isSymbolMode);
                    } else if (key.code == 0xE0 && isCtrlLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 0xE2 && isAltLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 16) {
                        textButton.setBackgroundResource(R.drawable.key_background);
                    }

                    if (key.iconResId == 0 && !key.label.isEmpty()) {
                        String displayLabel = key.label;
                        String symbolLabel = key.symbolLabel;

                        if (!isSymbolMode && displayLabel.contains("\n")) {
                            String[] parts = displayLabel.split("\n");
                            if (parts.length == 2) {
                                symbolLabel = parts[0];
                                displayLabel = parts[1];
                            }
                            String combinedText = symbolLabel + "\n" + displayLabel;
                            SpannableString spannable = new SpannableString(combinedText);
                            textButton.setText(spannable);
                            Log.d(TAG, "Applied Spannable: combinedText=" + combinedText + ", symbolLabel=" + symbolLabel + ", displayLabel=" + displayLabel);
                        } else {
                            textButton.setText(isSymbolMode && !symbolLabel.isEmpty() ? symbolLabel : displayLabel);
                            textButton.setTextColor(Color.BLACK);
                        }
                    }

                    if (key.iconResId != 0) {
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, key.iconResId, 0, 0);
                        textButton.setCompoundDrawablePadding(dpToPx(4));
                    }
                    button = textButton;
                }

                button.setOnClickListener(v -> handleKeyPress(key));

                // Add touch listener to handle key release on all buttons
                button.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (key.isRepeatable) {
                            stopRepeatingDelete();
                        } else {
                            // Send key release after short delay for regular keys
                            repeatHandler.postDelayed(() -> {
                                sendReleaseData();
                                Log.d(TAG, "Sent key release for: " + key.label);
                            }, 30);
                        }
                    }
                    return false;
                });

                if (key.isRepeatable) {
                    button.setOnLongClickListener(v -> {
                        startRepeatingDelete(key);
                        return true;
                    });
                }
                rowLayout.addView(button);
            }
            addView(rowLayout);
        }

        if (showExtraPortraitKeys && !isLandscape(getContext())) {
            addExtraPortraitKeys();
        }
    }

    private void addExtraPortraitKeys() {
        // iOS parity: top 101-key cluster
        addExtraRow(new Key[]{
            new Key("PrtSc", "", 0x46, "46", 33.33f, 0, 0f, false),
            new Key("Scroll Lock", "", 0x47, "47", 33.33f, 0, 0f, false),
            new Key("Pause", "", 0x48, "48", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
            new Key("Insert", "", 0x49, "49", 33.33f, 0, 0f, false),
            new Key("Home", "", 0x4A, "4A", 33.33f, 0, 0f, false),
            new Key("PgUp", "", 0x4B, "4B", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
            new Key("Delete", "", 0x4C, "4C", 33.33f, 0, 0f, false),
            new Key("End", "", 0x4D, "4D", 33.33f, 0, 0f, false),
            new Key("PgDn", "", 0x4E, "4E", 33.34f, 0, 0f, false)
        });

        // Keep middle direction keys (iOS extra panel style)
        addExtraRow(new Key[]{
            null,
            new Key("↑", "", 0x52, "52", 33.33f, 0, 0f, false),
            null
        });

        addExtraRow(new Key[]{
            new Key("←", "", 0x50, "50", 33.33f, 0, 0f, false),
            new Key("↓", "", 0x51, "51", 33.33f, 0, 0f, false),
            new Key("→", "", 0x4F, "4F", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
                new Key("7", "", 0x5F, "5F", 33.33f, 0, 0f, false),
                new Key("8", "", 0x60, "60", 33.33f, 0, 0f, false),
                new Key("9", "", 0x61, "61", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
                new Key("4", "", 0x5C, "5C", 33.33f, 0, 0f, false),
                new Key("5", "", 0x5D, "5D", 33.33f, 0, 0f, false),
                new Key("6", "", 0x5E, "5E", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
                new Key("1", "", 0x59, "59", 33.33f, 0, 0f, false),
                new Key("2", "", 0x5A, "5A", 33.33f, 0, 0f, false),
                new Key("3", "", 0x5B, "5B", 33.34f, 0, 0f, false)
        });

        addExtraRow(new Key[]{
            new Key("0", "", 0x62, "62", 50f, 0, 0f, false),
            new Key(".", "", 0x63, "63", 50f, 0, 0f, false)
        });
    }

    private void addExtraRow(Key[] keys) {
        LinearLayout rowLayout = new LinearLayout(getContext());
        rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.9f));
        rowLayout.setOrientation(HORIZONTAL);

        for (Key key : keys) {
            float widthPercent = key != null ? key.widthPercent : (100f / keys.length);
            float weight = widthPercent / 10.0f;
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
            params.setMargins(2, 2, 2, 2);

            if (key == null) {
                View spacer = new View(getContext());
                spacer.setLayoutParams(params);
                rowLayout.addView(spacer);
                continue;
            }

            Button textButton = new Button(getContext());
            textButton.setLayoutParams(params);
            textButton.setBackgroundResource(R.drawable.function_button_background);
            textButton.setGravity(Gravity.CENTER);
            textButton.setTextSize(14);
            textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            textButton.setText(key.label);
            textButton.setTextColor(Color.BLACK);

            textButton.setOnClickListener(v -> handleKeyPress(key));
            textButton.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    repeatHandler.postDelayed(() -> {
                        sendReleaseData();
                        Log.d(TAG, "Sent key release for extra key: " + key.label);
                    }, 30);
                }
                return false;
            });

            rowLayout.addView(textButton);
        }

        addView(rowLayout);
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

        boolean updateRequired = false;
        switch (key.code) {
            case 0xE1: // Shift Left
                isSymbolMode = !isSymbolMode;
                isShiftLeftLocked = !isShiftLeftLocked;
                updateRequired = true;
                break;
            case 0xE0: // Ctrl Left
                isCtrlLeftLocked = !isCtrlLeftLocked;
                updateRequired = true;
                break;
            case 0xE2: // Alt Left
                isAltLeftLocked = !isAltLeftLocked;
                updateRequired = true;
                break;
            case 0xE3: // Win Left
                isWinLeftLocked = !isWinLeftLocked;
                updateRequired = true;
                break;
        }

        if (updateRequired) {
            updateKeyboard();
        }

        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += isShiftLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;

        String sendKBData = String.format("57AB000208%02X00%02X0000000000", combinedValue, key.code);
        sendKBData += makeChecksum(sendKBData);

        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
            bluetoothService.sendData(sendKBDataBytes);
            Log.d(TAG, "Sent Bluetooth data: " + sendKBData);
//            sendReleaseData();
        } else if (port != null) {
            try {
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                port.write(sendKBDataBytes, 20);
                Log.d(TAG, "Sent USB data: " + sendKBData);
//                sendReleaseData();
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isServiceBound) {
            getContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
}