package com.openterface.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.content.pm.ActivityInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;
import com.openterface.keymod.GamepadView.ComponentLongPressListener;
import com.openterface.keymod.GamepadView.DpadStateListener;

/**
 * Gamepad Fragment - Simple layout: left stick + right button
 */
public class GamepadFragment extends Fragment {

    private static final String TAG = "GamepadFragment";

    // Config preference keys
    private static final String PREF_STICK_MODE = "gamepad_stick_mode";
    private static final String PREF_STICK_UP = "gamepad_stick_up";
    private static final String PREF_STICK_LEFT = "gamepad_stick_left";
    private static final String PREF_STICK_DOWN = "gamepad_stick_down";
    private static final String PREF_STICK_RIGHT = "gamepad_stick_right";
    private static final String PREF_BUTTON_A_KEY = "gamepad_button_a_key";
    private static final String PREF_BUTTON_B_KEY = "gamepad_button_b_key";

    // Stick modes
    private static final String MODE_ANALOG = "analog";
    private static final String MODE_KEY = "key";

    // Key picker: label -> HID keycode (as string)
    private static final String[][] KEY_OPTIONS = {
        {"W", "26"}, {"A", "4"}, {"S", "22"}, {"D", "7"},
        {"J", "13"}, {"K", "14"}, {"L", "15"}, {"I", "12"},
        {"U", "24"}, {"O", "18"}, {"P", "19"}, {"H", "11"},
        {"G", "10"}, {"F", "9"}, {"Q", "20"}, {"E", "8"},
        {"R", "21"}, {"T", "23"}, {"Y", "28"}, {"Z", "29"},
        {"X", "27"}, {"C", "6"}, {"V", "25"}, {"B", "5"},
        {"N", "17"}, {"M", "16"},
        {"Space", "44"}, {"Enter", "40"}, {"Esc", "41"}, {"Tab", "43"},
    };

    // Default stick key codes
    private static final int DEFAULT_STICK_UP = 26;    // W
    private static final int DEFAULT_STICK_LEFT = 4;   // A
    private static final int DEFAULT_STICK_DOWN = 22;  // S
    private static final int DEFAULT_STICK_RIGHT = 7;  // D
    private static final int DEFAULT_BUTTON_A = 40;    // Enter
    private static final int DEFAULT_BUTTON_B = 41;    // Escape

    private GamepadView gamepadView;

    private Vibrator vibrator;
    private GamepadLayout currentLayout;
    private SharedPreferences prefs;
    private float mouseSensitivity = 1.0f;

    // Stick config
    private String stickMode = MODE_KEY;
    private int stickUpKey = DEFAULT_STICK_UP;
    private int stickLeftKey = DEFAULT_STICK_LEFT;
    private int stickDownKey = DEFAULT_STICK_DOWN;
    private int stickRightKey = DEFAULT_STICK_RIGHT;

    // Button config
    private int buttonAKey = DEFAULT_BUTTON_A;
    private int buttonBKey = 41;  // default: Escape

    // Track currently pressed keys to avoid repeat events
    private boolean keyUpPressed = false;
    private boolean keyLeftPressed = false;
    private boolean keyDownPressed = false;
    private boolean keyRightPressed = false;
    private boolean buttonAPressed = false;
    private boolean buttonBPressed = false;
    private boolean twoButtonMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gamepad, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        loadSavedSensitivity();
        loadStickConfig();
        loadButtonConfig();

        gamepadView = view.findViewById(R.id.gamepad_view);
        currentLayout = GamepadLayout.SIMPLE;
        gamepadView.setLayout(currentLayout);

        // Button mode toggle
        ToggleButton buttonModeToggle = view.findViewById(R.id.button_mode_toggle);
        buttonModeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            twoButtonMode = isChecked;
            if (gamepadView != null) {
                gamepadView.setShowTwoButtons(isChecked);
            }
            Log.d(TAG, "Button mode: " + (twoButtonMode ? "2 buttons (A+B)" : "1 button (A)"));
        });

        // Done button for edit mode
        Button editDoneBtn = view.findViewById(R.id.edit_done_btn);
        editDoneBtn.setOnClickListener(v -> {
            gamepadView.setEditMode(false);
            editDoneBtn.setVisibility(View.GONE);
            buttonModeToggle.setVisibility(View.VISIBLE);
            Log.d(TAG, "Exited edit mode");
        });

        // Pass configured keycode and labels to the gamepad view
        if (gamepadView != null) {
            gamepadView.setButtonAKeyCode(buttonAKey);
            updateGamepadLabels();
        }

        setupListeners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void setupListeners() {
        // Button press listener (face buttons only, D-pad handled by dpadStateListener)
        gamepadView.setButtonPressListener((buttonId, keyCode) -> {
            Log.d(TAG, "Button pressed: " + buttonId + " -> keyCode: " + keyCode);

            if (keyCode == 1001) {
                sendMouseClick(1, true);
                gamepadView.postDelayed(() -> sendMouseClick(1, false), 100);
            } else if (keyCode == 1002) {
                sendMouseClick(2, true);
                gamepadView.postDelayed(() -> sendMouseClick(2, false), 100);
            } else {
                // Track button press and send combined report with D-pad keys
                buttonAPressed = true;
                if (twoButtonMode) {
                    buttonBPressed = true;
                }
                sendCombinedKeyReport();
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        });

        // Button release listener (face buttons, D-pad handled by dpadStateListener)
        gamepadView.setButtonReleaseListener((buttonId, keyCode) -> {
            if (keyCode == 1001 || keyCode == 1002) return;
            Log.d(TAG, "Button released: " + buttonId);
            // Track button release and send combined report with remaining keys
            buttonAPressed = false;
            if (twoButtonMode) {
                buttonBPressed = false;
            }
            sendCombinedKeyReport();
        });

        // D-pad state listener for hold behavior (multi-key support)
        gamepadView.setDpadStateListener(new DpadStateListener() {
            @Override
            public void onDpadStateChanged(int[] keyCodes) {
                // Update D-pad state booleans from the incoming keys
                keyUpPressed = false;
                keyLeftPressed = false;
                keyDownPressed = false;
                keyRightPressed = false;
                if (keyCodes != null) {
                    for (int kc : keyCodes) {
                        if (kc == stickUpKey) keyUpPressed = true;
                        else if (kc == stickLeftKey) keyLeftPressed = true;
                        else if (kc == stickDownKey) keyDownPressed = true;
                        else if (kc == stickRightKey) keyRightPressed = true;
                    }
                }
                // Send combined report including button A if pressed
                sendCombinedKeyReport();
            }
        });

        // Analog stick listener
        gamepadView.setAnalogStickListener((stickId, x, y) -> {
            Log.d(TAG, "Analog stick: " + stickId + " -> x: " + x + ", y: " + y);
            sendAnalogInput(stickId.toString(), x, y);
        });

        // Long press listener for config
        gamepadView.setComponentLongPressListener(componentId -> {
            Log.d(TAG, "Long press on: " + componentId);
            showLongPressMenu(componentId);
        });

        // Save positions when exiting edit mode
        gamepadView.setEditModeExitListener(positions -> {
            gamepadView.savePositions();
            Log.d(TAG, "Saved component positions");
        });
    }

    /**
     * Send a keyboard report with all currently pressed keys (D-pad + button A).
     */
    private void sendCombinedKeyReport() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm == null || !cm.isConnected()) {
                Log.w(TAG, "Not connected - cannot send combined key report");
                return;
            }

            // Build list of all currently pressed keys
            java.util.List<Integer> keys = new java.util.ArrayList<>();
            if (keyUpPressed) keys.add(stickUpKey);
            if (keyLeftPressed) keys.add(stickLeftKey);
            if (keyDownPressed) keys.add(stickDownKey);
            if (keyRightPressed) keys.add(stickRightKey);
            if (buttonAPressed) keys.add(buttonAKey);
            if (buttonBPressed) keys.add(buttonBKey);

            if (keys.isEmpty()) {
                cm.sendKeyRelease();
            } else {
                int[] keyCodeArray = new int[keys.size()];
                for (int i = 0; i < keys.size(); i++) {
                    keyCodeArray[i] = keys.get(i);
                }
                cm.sendKeyboardReport(0, keyCodeArray);
            }
            Log.d(TAG, "Combined keys: " + keys +
                " (U=" + keyUpPressed + " L=" + keyLeftPressed +
                " D=" + keyDownPressed + " R=" + keyRightPressed +
                " A=" + buttonAPressed + " B=" + buttonBPressed + ")");
        }
    }

    // ── Config Dialogs ──────────────────────────────────────────────

    private void showLongPressMenu(String componentId) {
        String componentName;
        final boolean hasKeyMapping;
        if (componentId.startsWith("stick_")) {
            componentName = "Stick";
            hasKeyMapping = true;
        } else if (componentId.equals("button_a")) {
            componentName = "Button A";
            hasKeyMapping = true;
        } else if (componentId.equals("button_b")) {
            componentName = "Button B";
            hasKeyMapping = true;
        } else {
            componentName = componentId;
            hasKeyMapping = false;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(componentName);
        String[] options = {"Move", "Edit Key Mapping"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Move mode - enter edit mode briefly
                enterMoveMode(componentId);
            } else if (which == 1 && hasKeyMapping) {
                showConfigDialog(componentId);
            }
        });
        builder.show();
    }

    private void enterMoveMode(String componentId) {
        if (gamepadView != null) {
            gamepadView.setEditMode(true);
            // Hide the toggle, show the done button
            View toggle = getView().findViewById(R.id.button_mode_toggle);
            View doneBtn = getView().findViewById(R.id.edit_done_btn);
            if (toggle != null) toggle.setVisibility(View.GONE);
            if (doneBtn != null) doneBtn.setVisibility(View.VISIBLE);
            gamepadView.invalidate();
            Toast.makeText(requireContext(), "Drag components to reposition, tap Done when finished",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showConfigDialog(String componentId) {
        if (componentId.startsWith("stick_")) {
            showStickConfigDialog();
        } else if (componentId.equals("button_a")) {
            showButtonConfigDialog(buttonAKey, DEFAULT_BUTTON_A, key -> {
                buttonAKey = key;
                saveButtonConfig();
                updateGamepadLabels();
            });
        } else if (componentId.equals("button_b")) {
            showButtonConfigDialog(buttonBKey, DEFAULT_BUTTON_B, key -> {
                buttonBKey = key;
                saveButtonConfig();
            });
        }
    }

    private void showStickConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stick_config, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final RadioGroup modeGroup = dialogView.findViewById(R.id.stick_mode_group);
        final LinearLayout keySection = dialogView.findViewById(R.id.key_mapping_section);
        final Button keyUp = dialogView.findViewById(R.id.key_up);
        final Button keyLeft = dialogView.findViewById(R.id.key_left);
        final Button keyRight = dialogView.findViewById(R.id.key_right);
        final Button keyDown = dialogView.findViewById(R.id.key_down);

        // Set initial mode and key labels
        if (MODE_KEY.equals(stickMode)) {
            modeGroup.check(R.id.stick_mode_key);
        } else {
            modeGroup.check(R.id.stick_mode_analog);
        }
        updateKeySectionVisibility(modeGroup, keySection);
        updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);

        // Mode change
        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
            updateKeySectionVisibility(group, keySection));

        // Cardinal key pickers
        keyUp.setOnClickListener(v -> showKeyPicker(keyUp, stickUpKey, code -> {
            stickUpKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyLeft.setOnClickListener(v -> showKeyPicker(keyLeft, stickLeftKey, code -> {
            stickLeftKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyDown.setOnClickListener(v -> showKeyPicker(keyDown, stickDownKey, code -> {
            stickDownKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyRight.setOnClickListener(v -> showKeyPicker(keyRight, stickRightKey, code -> {
            stickRightKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));

        // Reset
        dialogView.findViewById(R.id.stick_reset_btn).setOnClickListener(v -> {
            stickMode = MODE_KEY;
            stickUpKey = DEFAULT_STICK_UP;
            stickLeftKey = DEFAULT_STICK_LEFT;
            stickDownKey = DEFAULT_STICK_DOWN;
            stickRightKey = DEFAULT_STICK_RIGHT;
            modeGroup.check(R.id.stick_mode_key);
            updateKeySectionVisibility(modeGroup, keySection);
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        });

        // Done
        dialogView.findViewById(R.id.stick_done_btn).setOnClickListener(v -> {
            stickMode = modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key ? MODE_KEY : MODE_ANALOG;
            saveStickConfig();
            updateGamepadLabels();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateKeyLabels(Button up, Button left, Button right, Button down) {
        up.setText(keyCodeToLabel(stickUpKey));
        left.setText(keyCodeToLabel(stickLeftKey));
        down.setText(keyCodeToLabel(stickDownKey));
        right.setText(keyCodeToLabel(stickRightKey));
    }

    private void showButtonConfigDialog(int currentKey, int defaultKey, java.util.function.IntConsumer onSave) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_button_config, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final int[] selectedKey = { currentKey };

        final Button keyLabel = dialogView.findViewById(R.id.button_key_label);
        keyLabel.setText(keyCodeToLabel(currentKey));

        keyLabel.setOnClickListener(v -> showKeyPicker(keyLabel, selectedKey[0], code -> {
            selectedKey[0] = code;
            keyLabel.setText(keyCodeToLabel(code));
        }));

        dialogView.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            selectedKey[0] = defaultKey;
            keyLabel.setText(keyCodeToLabel(defaultKey));
        });

        dialogView.findViewById(R.id.btn_done).setOnClickListener(v -> {
            onSave.accept(selectedKey[0]);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showKeyPicker(Button displayButton, int currentKeyCode, java.util.function.IntConsumer listener) {
        buildKeyPickerDialog(currentKeyCode, keyInfo -> {
            listener.accept(keyInfo.keyCode);
            displayButton.setText(keyInfo.label);
        });
    }

    private void buildKeyPickerDialog(int initialKeyCode, final KeySelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Key");

        final int[] allKeyCodes = new int[KEY_OPTIONS.length];
        final Button[] allButtons = new Button[KEY_OPTIONS.length];
        final int[] selectedKeyCode = { initialKeyCode };
        final String[] selectedLabel = { keyCodeToLabel(initialKeyCode) };

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        grid.setPadding(pad, pad, pad, pad);

        for (int i = 0; i < KEY_OPTIONS.length; i += 4) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = 0; j < 4; j++) {
                if (i + j >= KEY_OPTIONS.length) break;
                String[] opt = KEY_OPTIONS[i + j];
                final int idx = i + j;
                Button btn = new Button(requireContext());
                btn.setText(opt[0]);
                btn.setTextColor(0xFFFFFFFF);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                final int keyCode = Integer.parseInt(opt[1]);
                allKeyCodes[idx] = keyCode;
                bg.setColor(keyCode == initialKeyCode ? 0xFF4CAF50 : 0xFF444444);
                bg.setCornerRadius(dp(6));
                btn.setBackground(bg);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
                int dp8 = dp(8);
                params.setMargins(dp8, dp8, dp8, dp8);
                btn.setLayoutParams(params);
                allButtons[idx] = btn;

                btn.setOnClickListener(v -> {
                    selectedKeyCode[0] = keyCode;
                    selectedLabel[0] = opt[0];
                    for (int k = 0; k < allButtons.length; k++) {
                        if (allButtons[k] == null) continue;
                        android.graphics.drawable.GradientDrawable b =
                            (android.graphics.drawable.GradientDrawable) allButtons[k].getBackground();
                        b.setColor(allKeyCodes[k] == keyCode ? 0xFF4CAF50 : 0xFF444444);
                    }
                });
                row.addView(btn);
            }
            grid.addView(row);
        }

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(grid);
        builder.setView(scrollView);

        builder.setPositiveButton("OK", (d, w) -> {
            listener.onKeySelected(new KeyInfo(selectedKeyCode[0], selectedLabel[0]));
        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateKeySectionVisibility(RadioGroup modeGroup, LinearLayout keySection) {
        keySection.setVisibility(modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key
            ? View.VISIBLE : View.GONE);
    }

    // ── Persistence ─────────────────────────────────────────────────

    private void loadStickConfig() {
        stickMode = prefs.getString(PREF_STICK_MODE, MODE_KEY);
        stickUpKey = prefs.getInt(PREF_STICK_UP, DEFAULT_STICK_UP);
        stickLeftKey = prefs.getInt(PREF_STICK_LEFT, DEFAULT_STICK_LEFT);
        stickDownKey = prefs.getInt(PREF_STICK_DOWN, DEFAULT_STICK_DOWN);
        stickRightKey = prefs.getInt(PREF_STICK_RIGHT, DEFAULT_STICK_RIGHT);
    }

    private void saveStickConfig() {
        prefs.edit()
            .putString(PREF_STICK_MODE, stickMode)
            .putInt(PREF_STICK_UP, stickUpKey)
            .putInt(PREF_STICK_LEFT, stickLeftKey)
            .putInt(PREF_STICK_DOWN, stickDownKey)
            .putInt(PREF_STICK_RIGHT, stickRightKey)
            .apply();
    }

    private void loadButtonConfig() {
        buttonAKey = prefs.getInt(PREF_BUTTON_A_KEY, DEFAULT_BUTTON_A);
        buttonBKey = prefs.getInt(PREF_BUTTON_B_KEY, DEFAULT_BUTTON_B);
    }

    private void saveButtonConfig() {
        prefs.edit()
            .putInt(PREF_BUTTON_A_KEY, buttonAKey)
            .putInt(PREF_BUTTON_B_KEY, buttonBKey)
            .apply();
        updateGamepadLabels();
    }

    // ── Label Sync ──────────────────────────────────────────────────

    private void updateGamepadLabels() {
        if (gamepadView == null) return;
        Map<String, String> labels = new HashMap<>();
        labels.put("stick_up", keyCodeToLabel(stickUpKey));
        labels.put("stick_down", keyCodeToLabel(stickDownKey));
        labels.put("stick_left", keyCodeToLabel(stickLeftKey));
        labels.put("stick_right", keyCodeToLabel(stickRightKey));
        labels.put("button_a", keyCodeToLabel(buttonAKey));
        labels.put("button_b", keyCodeToLabel(buttonBKey));
        gamepadView.setComponentDisplayLabels(labels);
        gamepadView.setButtonAKeyCode(buttonAKey);
    }

    // ── Input handling ──────────────────────────────────────────────

    private void sendKeyEvent(int keyCode) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyEvent(0, keyCode);
                Log.d(TAG, "Sent key event: " + keyCode);
            } else {
                Log.w(TAG, "Not connected - cannot send key event");
            }
        }
    }

    private void sendKeyRelease() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyRelease();
                Log.d(TAG, "Sent key release");
            }
        }
    }

    private void sendMouseClick(int button, boolean press) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendMouseClick(button, press);
            }
        }
    }

    private void sendAnalogInput(String stickId, float x, float y) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            boolean isConnected = connectionManager != null && connectionManager.isConnected();

            if (stickId.equals("r")) {
                if (isConnected) sendRightStickMouse(connectionManager, x, y);
            } else if (stickId.equals("l")) {
                if (MODE_KEY.equals(stickMode)) {
                    sendLeftStickKeys(connectionManager, x, y, isConnected);
                } else {
                    if (isConnected) sendLeftStickMouse(connectionManager, x, y);
                }
            }
        }
    }

    private void sendRightStickMouse(ConnectionManager cm, float x, float y) {
        float deadZone = 0.15f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) return;

        xAdj *= mouseSensitivity;
        yAdj *= mouseSensitivity;
        xAdj = Math.max(-1.0f, Math.min(1.0f, xAdj));
        yAdj = Math.max(-1.0f, Math.min(1.0f, yAdj));

        cm.sendMouseMovement((int)(xAdj * 127), (int)(yAdj * 127), 0);
    }

    private void sendLeftStickMouse(ConnectionManager cm, float x, float y) {
        float deadZone = 0.15f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) return;

        xAdj *= mouseSensitivity * 0.6f;
        yAdj *= mouseSensitivity * 0.6f;
        xAdj = Math.max(-1.0f, Math.min(1.0f, xAdj));
        yAdj = Math.max(-1.0f, Math.min(1.0f, yAdj));

        cm.sendMouseMovement((int)(xAdj * 127), (int)(yAdj * 127), 0);
    }

    private void sendLeftStickKeys(ConnectionManager cm, float x, float y, boolean isConnected) {
        float deadZone = 0.2f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);

        boolean wantUp = yAdj < -0.3f;
        boolean wantDown = yAdj > 0.3f;
        boolean wantLeft = xAdj < -0.3f;
        boolean wantRight = xAdj > 0.3f;

        boolean changed = false;

        // Up key
        if (wantUp != keyUpPressed) { keyUpPressed = wantUp; changed = true; }
        // Left key
        if (wantLeft != keyLeftPressed) { keyLeftPressed = wantLeft; changed = true; }
        // Down key
        if (wantDown != keyDownPressed) { keyDownPressed = wantDown; changed = true; }
        // Right key
        if (wantRight != keyRightPressed) { keyRightPressed = wantRight; changed = true; }

        // Send combined report with all active keys (including button A)
        if (changed) {
            sendCombinedKeyReport();
        }

        // Update stick direction labels highlighting
        java.util.Set<String> activeDirs = new java.util.HashSet<>();
        if (keyUpPressed) activeDirs.add("up");
        if (keyDownPressed) activeDirs.add("down");
        if (keyLeftPressed) activeDirs.add("left");
        if (keyRightPressed) activeDirs.add("right");
        if (activeDirs.isEmpty()) {
            gamepadView.clearStickDirections("l");
        } else {
            gamepadView.setActiveStickDirections("l", activeDirs);
        }

        if (xAdj != 0 || yAdj != 0) {
            Log.d(TAG, "Stick keys pressed: " +
                (wantUp ? "U " : ". ") + (wantDown ? "D " : ". ") +
                (wantLeft ? "L " : ". ") + (wantRight ? "R" : "."));
        }
    }

    private float applyDeadZone(float value, float deadZone) {
        if (Math.abs(value) < deadZone) return 0;
        return (value - Math.copySign(deadZone, value)) / (1.0f - deadZone);
    }

    // ── Mouse sensitivity ───────────────────────────────────────────

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.5f, Math.min(2.0f, sensitivity));
        prefs.edit().putFloat("mouse_sensitivity", this.mouseSensitivity).apply();
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    private void loadSavedSensitivity() {
        mouseSensitivity = prefs.getFloat("mouse_sensitivity", 1.0f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vibrator != null) vibrator.cancel();
        releaseAllKeys();
    }

    private void releaseAllKeys() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected() && (keyUpPressed || keyLeftPressed || keyDownPressed || keyRightPressed || buttonAPressed)) {
                cm.sendKeyRelease();
            }
        }
        keyUpPressed = false;
        keyLeftPressed = false;
        keyDownPressed = false;
        keyRightPressed = false;
        buttonAPressed = false;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String keyCodeToLabel(int keyCode) {
        for (String[] opt : KEY_OPTIONS) {
            if (Integer.parseInt(opt[1]) == keyCode) return opt[0];
        }
        return String.valueOf(keyCode);
    }

    private interface KeySelectedListener {
        void onKeySelected(KeyInfo keyInfo);
    }

    private interface DualKeySelectedListener {
        void onKeysSelected(int key1, int key2);
    }

    private static class KeyInfo {
        final int keyCode;
        final String label;
        KeyInfo(int keyCode, String label) {
            this.keyCode = keyCode;
            this.label = label;
        }
    }
}
