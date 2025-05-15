package com.example.dual_modekeyboard;

import static com.example.serial.UsbDeviceManager.port;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.example.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomKeyboardView extends LinearLayout {
    private boolean isCaps = false;
    private boolean isCapsLocked = false;
    private boolean isShiftLeftLocked = false;
    private boolean isCtrlLeftLocked = false;
    private boolean isAltLeftLocked = false;
    private boolean isWinLeftLocked = false;
    private boolean isSymbolMode = false;
    private List<List<Key>> lowerKeys;
    private static UsbSerialPort port;

    private Handler repeatHandler = new Handler();
    private Runnable repeatRunnable;
    private boolean isRepeating = false;

//    private static UsbDeviceManager usbDeviceManager;

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

    private void init(Context context) {
        setOrientation(VERTICAL);
        lowerKeys = parseKeyboard(context, R.xml.keyboard_lower);
        System.out.println("Parsed keyboards: lowerKeys=" + lowerKeys.size());
        updateKeyboard();
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

                        System.out.println("Parsed Key: label=" + label + ", symbolLabel=" + symbolLabel + ", code=0x" + Integer.toHexString(code).toUpperCase() + ", codeStr=" + codeStr + ", width=" + widthPercent + ", icon=" + iconResId + ", gap=" + horizontalGap + ", repeatable=" + isRepeatable);
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
        List<List<Key>> currentKeys;
        currentKeys = lowerKeys;

        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int keyboardHeight = screenHeight;
        int rowHeight = keyboardHeight / 8;

        System.out.println("Screen Height: " + screenHeight + ", Screen Width: " + screenWidth + ", Row Height: " + rowHeight);

        String[] functionalKeyCodes = {"46", "47", "48", "49", "4A",
                "4B", "4C", "4D", "4E", "3B", "3C", "3D", "3E", "3F", "29", "3A",
                "40", "41", "42", "43", "44", "45", "3D", "3F",};

        for (List<Key> row : currentKeys) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
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
                        System.out.println("Matched functional key: label=" + key.label + ", code=0x" + Integer.toHexString(key.code));
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
                    textButton.setTextSize(12); // Set smaller base text size to make size difference visible
                    textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2)); // Adjust padding

                    if (isFunctionalKey) {
                        textButton.setBackgroundResource(R.drawable.function_button_background);
                    } else if (key.code == 0xE1 && isShiftLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isSymbolMode);
                    }  else if (key.code == 0xE0 && isCtrlLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 0xE2 && isAltLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                    } else if (key.code == 16) {
                        textButton.setBackgroundResource(R.drawable.key_background);
                    }

                    if (key.iconResId == 0 && !key.label.isEmpty()) {
                        String displayLabel = key.label;
                        String symbolLabel = key.symbolLabel;

                        // Check if the label contains a newline (e.g., "!\n1")
                        if (!isSymbolMode && displayLabel.contains("\n")) {
                            String[] parts = displayLabel.split("\n");
                            if (parts.length == 2) {
                                symbolLabel = parts[0]; // e.g., "!"
                                displayLabel = parts[1]; // e.g., "1"
                            }
                            String combinedText = symbolLabel + "\n" + displayLabel;
                            SpannableString spannable = new SpannableString(combinedText);
                            textButton.setText(spannable);
                            System.out.println("Applied Spannable: combinedText=" + combinedText + ", symbolLabel=" + symbolLabel + ", displayLabel=" + displayLabel);
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

                if (key.isRepeatable) {
                    button.setOnLongClickListener(v -> {
                        startRepeatingDelete(key);
                        return true;
                    });

                    button.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            stopRepeatingDelete();
                        }
                        return false;
                    });
                }
                rowLayout.addView(button);
            }
            addView(rowLayout);
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
        System.out.println("Data: " + Arrays.toString(data));
        return data;
    }

    public void setPort(UsbSerialPort port) {
        this.port = port;
        System.out.println("Port set in CustomKeyboardView: " + (port != null ? "Valid" : "Null"));
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

    public static void releaseAllData(){
        String releaseSendMSData = "57AB00020800000000000000000C";
        byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
        try {
            Thread.sleep(10);
            port.write(releaseSendKBDataBytes, 20);
            System.out.println("Successfully sent release data");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int parseHex(String hex) {
        return hex != null ? Integer.parseInt(hex.replace("0x", ""), 16) : 0;
    }

    private void handleKeyPress(Key key) {
        System.out.println("Key pressed: label=" + key.label + ", code=" + key.code);
        if (port == null) {
            return;
        }

        // Handle modifier key toggles (Shift, Ctrl, Alt, Win)
        boolean updateRequired = false;
        switch (key.code) {
            case 0xE1: // Shift Left
                isSymbolMode = !isSymbolMode;
                isShiftLeftLocked = !isShiftLeftLocked;
                isCaps = false;
                isCapsLocked = false;
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

        // Calculate combined modifier value
        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += isShiftLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;

        // Send key data
        try {
            String sendMSData = String.format("57AB000208%02X00%02X0000000000", combinedValue, key.code);
            sendMSData += makeChecksum(sendMSData);
            byte[] sendKBDataBytes = hexStringToByteArray(sendMSData);
            port.write(sendKBDataBytes, 20);
            System.out.println("Successfully sent data for key code: 0x" + String.format("%02X", key.code));
            releaseAllData();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
                    repeatHandler.postDelayed(this, 50);
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
}