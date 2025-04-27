package com.example.dual_modekeyboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
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
                        System.out.println("Parsing new Row");
                    } else if ("Key".equals(tag) && currentRow != null) {
                        // Get android:keyLabel
                        String label = parser.getAttributeValue(ANDROID_NS, "keyLabel");
                        if (label == null) {
                            label = "";
                            System.out.println("Warning: android:keyLabel is missing");
                        } else {
                            try {
                                // Check if label is a resource ID (e.g., "@2131689646")
                                if (label.startsWith("@") && label.length() > 1) {
                                    String idStr = label.substring(1); // Remove "@"
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
                                    // Treat as literal string or unresolved resource name
                                    String resourceName = label.startsWith("@string/") ? label.substring("@string/".length()) : label;
                                    int resId = context.getResources().getIdentifier(resourceName, "string", context.getPackageName());
                                    if (resId != 0) {
                                        label = context.getResources().getString(resId);
                                        System.out.println("Resolved string resource " + resourceName + " to: " + label);
                                    } else {
                                        System.out.println("Warning: String resource not found for keyLabel: " + label);
                                        // Use label as is (literal string)
                                    }
                                }
                            } catch (Resources.NotFoundException e) {
                                System.out.println("Warning: Failed to resolve string resource: " + label);
                                e.printStackTrace();
                            }
                        }

                        // Get custom:keySymbolLabel
                        String symbolLabel = parser.getAttributeValue(CUSTOM_NS, "keySymbolLabel");
                        if (symbolLabel == null) {
                            symbolLabel = "";
                            System.out.println("Warning: custom:keySymbolLabel is missing");
                        }

                        // Get android:codes
                        String codeStr = parser.getAttributeValue(ANDROID_NS, "codes");
                        int code = 0;
                        try {
                            code = codeStr != null ? Integer.parseInt(codeStr) : 0;
                        } catch (NumberFormatException e) {
                            System.out.println("Warning: Invalid android:codes value: " + codeStr);
                        }

                        // Get android:keyWidth
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

                        // Get android:horizontalGap
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

                        // Get android:isRepeatable
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
        removeAllViews();
        List<List<Key>> currentKeys = isSymbolMode ? shiftKeys : (isCaps ? upperKeys : lowerKeys);

        // Obtain the screen height
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int keyboardHeight = (int)(screenHeight * 1);
        //Calculate the height of each row (10% of the total keyboard height)
        int rowHeight = keyboardHeight / 10;

        System.out.println("Screen Height: " + screenHeight + ", Screen Width: " + screenWidth + ", Row Height: " + rowHeight);

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

                // Add horizontal spacing
                if (key.horizontalGap > 0) {
                    params.setMargins((int)(key.horizontalGap * getContext().getResources().getDisplayMetrics().widthPixels / 100), 2, 2, 2);
                }

                if (key.label.equals("Win") || key.label.equals("BackSpace")) {
                    // use ImageButton display the centered icon
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
                    // Use Button for other keys
                    Button textButton = new Button(getContext());
                    textButton.setLayoutParams(params);
                    textButton.setBackgroundResource(R.drawable.key_background);
                    textButton.setGravity(Gravity.CENTER);

                    if (key.code == 16 && isShiftLeftLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isSymbolMode || isCaps);
                    } else if (key.code == 20 && isCapsLocked) {
                        textButton.setBackgroundResource(R.drawable.press_button_background);
                        textButton.setSelected(isSymbolMode || isCaps);
                    } else {
                        textButton.setBackgroundResource(R.drawable.key_background);
                    }
                    textButton.setGravity(Gravity.CENTER);

                    if (key.iconResId == 0 && !key.label.isEmpty()) {
                        if (!key.symbolLabel.isEmpty() && !isSymbolMode) {
                            String combinedText = key.symbolLabel + "\n" + key.label;
                            SpannableString spannable = new SpannableString(combinedText);
                            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), 0, key.symbolLabel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), key.symbolLabel.length() + 1, combinedText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            textButton.setText(spannable);
                        } else {
                            textButton.setText(isSymbolMode && !key.symbolLabel.isEmpty() ? key.symbolLabel : key.label);
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

                // Add long press processing
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
        if (editText == null) return;
        Editable editable = editText.getText();
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();

        if (isSymbolMode && !key.symbolLabel.isEmpty()) {
            // In symbol mode, insert the symbolLabel
            editable.insert(start, key.symbolLabel);
        } else {
            switch (key.code) {
                case 8: // BackSpace
                    if (start > 0 && start == end) {
                        editable.delete(start - 1, start);
                    } else if (start != end) {
                        editable.delete(start, end);
                    }
                    break;
                case 20: // Caps Lock
                    isCapsLocked = !isCapsLocked;
                    isCaps = isCapsLocked;
                    updateKeyboard();
                    break;
                case 16: // Shift
                    isSymbolMode = !isSymbolMode;
                    isShiftLeftLocked = !isShiftLeftLocked;
                    updateKeyboard();
                    break;
                case 1001: // Symbol/Numeric toggle
                    isSymbolMode = !isSymbolMode;
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
                default:
                    editable.insert(start, String.valueOf((char) key.code));
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
                    repeatHandler.postDelayed(this, 50); // Deletion is performed every 50 milliseconds
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