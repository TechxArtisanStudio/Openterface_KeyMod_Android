package com.example.dual_modekeyboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomKeyboardView extends LinearLayout {
    private EditText editText;
    private boolean isCaps = false;
    private boolean isCapsLocked = false;
    private boolean isShiftLeftLocked = false;
    private boolean isCtrlLeftLocked = false;
    private boolean isAltLeftLocked = false;
    private boolean isWinLeftLocked = false;
    private boolean isSymbolMode = false;
    private List<List<Key>> lowerKeys;
    private List<List<Key>> upperKeys;
    private List<List<Key>> symbolKeys;
    private List<List<Key>> shiftKeys;

    private static class Key {
        String label;
        String symbolLabel;
        int code;
        float widthPercent;
        int iconResId;
        float horizontalGap;
        boolean isRepeatable;

        Key(String label, String symbolLabel, int code, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable) {
            this.label = label;
            this.symbolLabel = symbolLabel;
            this.code = code;
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
        upperKeys = parseKeyboard(context, R.xml.keyboard_upper);
        symbolKeys = parseKeyboard(context, R.xml.keyboard_symbol);
        shiftKeys = parseKeyboard(context, R.xml.keyboard_shift);
        System.out.println("Parsed keyboards: lowerKeys=" + lowerKeys.size() + ", upperKeys=" + upperKeys.size() + ", symbolKeys=" + symbolKeys.size() + ", shiftKeys=" + shiftKeys.size());
        updateKeyboard();
    }

    public void setEditText(EditText editText) {
        this.editText = editText;
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
                            code = codeStr != null ? Integer.parseInt(codeStr) : 0;
                        } catch (NumberFormatException e) {
                            System.out.println("Warning: Invalid android:codes value: " + codeStr);
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
                        }

                        System.out.println("Parsed Key: label=" + label + ", symbolLabel=" + symbolLabel + ", code=" + code + ", width=" + widthPercent + ", icon=" + iconResId + ", gap=" + horizontalGap + ", repeatable=" + isRepeatable);
                        currentRow.add(new Key(label, symbolLabel, code, widthPercent, iconResId, horizontalGap, isRepeatable));
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
        System.out.println("Updating keyboard: isSymbolMode=" + isSymbolMode + ", isCaps=" + isCaps);
        removeAllViews();
        List<List<Key>> currentKeys;
        if (isSymbolMode) {
            currentKeys = shiftKeys;
        } else if (isCaps) {
            currentKeys = upperKeys;
        } else {
            currentKeys = lowerKeys;
        }

        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int keyboardHeight = screenHeight;
        int rowHeight = keyboardHeight / 10;

        System.out.println("Screen Height: " + screenHeight + ", Screen Width: " + screenWidth + ", Row Height: " + rowHeight);

        int[] functionalKeyCodes = {1001, 1002, 121, 124, 3, 92, 112, 1005, 93};

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
                for (int functionCode : functionalKeyCodes) {
                    if (key.code == functionCode) {
                        isFunctionalKey = true;
                        break;
                    }
                }

                if (key.label.equals("Win") || key.label.equals("BackSpace")) {
                    ImageButton imageButton = new ImageButton(getContext());
                    imageButton.setLayoutParams(params);
                    imageButton.setBackgroundResource(R.drawable.key_background);
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

                    if (isFunctionalKey) {
                        textButton.setBackgroundResource(R.drawable.function_button_background);
                    } else if (key.code == 16 && isShiftLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isSymbolMode);
                    } else if (key.code == 20 && isCapsLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isCaps);
                    } else if (key.code == 16) {
                        textButton.setBackgroundResource(R.drawable.key_background);
                    }

                    if (key.iconResId == 0 && !key.label.isEmpty()) {
                        String displayLabel = key.label;
                        if (!key.symbolLabel.isEmpty() && !isSymbolMode) {
                            String combinedText = key.symbolLabel + "\n" + displayLabel;
                            SpannableString spannable = new SpannableString(combinedText);
                            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), 0, key.symbolLabel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), key.symbolLabel.length() + 1, combinedText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            textButton.setText(spannable);
                        } else {
                            textButton.setText(isSymbolMode && !key.symbolLabel.isEmpty() ? key.symbolLabel : displayLabel);
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

    private void handleKeyPress(Key key) {
        System.out.println("Key pressed: label=" + key.label + ", code=" + key.code);
        if (editText == null) {
            System.out.println("Error: EditText is null");
            return;
        }
        Editable editable = editText.getText();
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();

        boolean resetSymbolMode = false;
        if (isSymbolMode && !key.symbolLabel.isEmpty()) {
            editable.insert(start, key.symbolLabel);
        } else {
            switch (key.code) {
                case -5: // BackSpace
                    if (start > 0 && start == end) {
                        editable.delete(start - 1, start);
                    } else if (start != end) {
                        editable.delete(start, end);
                    }
                    break;
                case 20: // Caps Lock
                    isCapsLocked = !isCapsLocked;
                    isCaps = isCapsLocked;
                    isSymbolMode = false; // Reset symbol mode when Caps Lock toggles
                    isShiftLeftLocked = false;
                    updateKeyboard();
                    break;
                case 16: // Shift
                    isSymbolMode = !isSymbolMode;
                    isShiftLeftLocked = !isShiftLeftLocked;
                    isCaps = false; // Reset Caps Lock when Shift toggles
                    isCapsLocked = false;
                    resetSymbolMode = isSymbolMode; // Reset symbol mode after one keypress
                    updateKeyboard();
                    break;
                case 1008: // Symbol toggle
                    isSymbolMode = !isSymbolMode;
                    isShiftLeftLocked = false;
                    updateKeyboard();
                    break;
                case -4: // Enter
                    editable.insert(start, "\n");
                    break;
                case 32: // Space
                    editable.insert(start, " ");
                    break;
                case 9: // Tab
                    editable.insert(start, "\t");
                    break;
                case 1001: // PrtSc
                    editable.insert(start, "[PrtSc]");
                    break;
                case 1002: // ScrLk
                    editable.insert(start, "[ScrLk]");
                    break;
                case 1005: // End
                    editable.insert(start, "[End]");
                    break;
                case 1007: // Fn
                    editable.insert(start, "[Fn]");
                    break;
                case 131: // F1
                    editable.insert(start, "[F1]");
                    break;
                case 132: // F2
                    editable.insert(start, "[F2]");
                    break;
                case 133: // F3
                    editable.insert(start, "[F3]");
                    break;
                case 134: // F4
                    editable.insert(start, "[F4]");
                    break;
                case 135: // F5
                    editable.insert(start, "[F5]");
                    break;
                case 136: // F6
                    editable.insert(start, "[F6]");
                    break;
                case 137: // F7
                    editable.insert(start, "[F7]");
                    break;
                case 138: // F8
                    editable.insert(start, "[F8]");
                    break;
                case 139: // F9
                    editable.insert(start, "[F9]");
                    break;
                case 140: // F10
                    editable.insert(start, "[F10]");
                    break;
                case 141: // F11
                    editable.insert(start, "[F11]");
                    break;
                case 142: // F12
                    editable.insert(start, "[F12]");
                    break;
                default:
                    String character = String.valueOf((char) key.code);
                    editable.insert(start, character);
                    if (resetSymbolMode && !isCapsLocked) {
                        isSymbolMode = false;
                        isShiftLeftLocked = false;
                        updateKeyboard();
                    }
                    break;
            }
        }
    }

    private Handler repeatHandler = new Handler();
    private Runnable repeatRunnable;
    private boolean isRepeating = false;

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