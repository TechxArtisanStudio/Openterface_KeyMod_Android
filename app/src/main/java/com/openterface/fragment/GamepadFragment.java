package com.openterface.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;

/**
 * Gamepad Fragment - Full gamepad emulation with multiple layouts
 * Matches iOS GamepadView.swift functionality
 */
public class GamepadFragment extends Fragment {

    private static final String TAG = "GamepadFragment";
    private static final String PREF_SELECTED_LAYOUT = "gamepad_selected_layout";

    private GamepadView gamepadView;
    private ImageButton layoutButton;
    private ImageButton configButton;
    private ImageButton editModeButton;

    private GamepadConfigManager configManager;
    private Vibrator vibrator;
    private GamepadLayout currentLayout;
    private SharedPreferences prefs;
    private boolean isEditMode = false;
    private float mouseSensitivity = 1.0f; // Default sensitivity

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gamepad, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        configManager = new GamepadConfigManager(requireContext());

        loadSavedSensitivity();
        initializeViews(view);
        loadSavedLayout();
        setupListeners();

        return view;
    }

    private void initializeViews(View view) {
        gamepadView = view.findViewById(R.id.gamepad_view);
        layoutButton = view.findViewById(R.id.layout_button);
        configButton = view.findViewById(R.id.config_button);
        editModeButton = view.findViewById(R.id.edit_mode_button);
    }

    private void loadSavedLayout() {
        int layoutIndex = prefs.getInt(PREF_SELECTED_LAYOUT, 0);
        currentLayout = GamepadLayout.values()[layoutIndex];
        gamepadView.setLayout(currentLayout);
        updateLayoutButtonIcon();
    }

    private void setupListeners() {
        // Layout selector
        layoutButton.setOnClickListener(v -> showLayoutSelector());

        // Config button
        configButton.setOnClickListener(v -> showConfigOptions());

        // Edit mode toggle
        editModeButton.setOnClickListener(v -> toggleEditMode());

        // Button press listener from gamepad view
        gamepadView.setButtonPressListener((buttonId, keyCode) -> {
            Log.d(TAG, "Button pressed: " + buttonId + " -> keyCode: " + keyCode);
            
            // Check for special L3/R3 mouse clicks
            if (keyCode == 1001) {
                // L3 = Left mouse button
                sendMouseClick(1, true); // 1 = left button
                gamepadView.postDelayed(() -> sendMouseClick(1, false), 100);
            } else if (keyCode == 1002) {
                // R3 = Right mouse button
                sendMouseClick(2, true); // 2 = right button
                gamepadView.postDelayed(() -> sendMouseClick(2, false), 100);
            } else {
                // Normal key event
                sendKeyEvent(keyCode);
                gamepadView.postDelayed(() -> sendKeyRelease(), 50);
            }
            
            // Haptic feedback
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        });

        // Analog stick listener
        gamepadView.setAnalogStickListener((stickId, x, y) -> {
            Log.d(TAG, "Analog stick: " + stickId + " -> x: " + x + ", y: " + y);
            // Convert to mouse movement or WASD keys
            sendAnalogInput(stickId.toString(), x, y);
        });
    }

    private void showLayoutSelector() {
        PopupMenu popup = new PopupMenu(requireContext(), layoutButton);
        popup.getMenu().add(0, 0, 0, "Xbox Layout");
        popup.getMenu().add(0, 1, 1, "PlayStation Layout");
        popup.getMenu().add(0, 2, 2, "NES Layout");

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            currentLayout = GamepadLayout.values()[itemId];
            gamepadView.setLayout(currentLayout);
            prefs.edit().putInt(PREF_SELECTED_LAYOUT, itemId).apply();
            updateLayoutButtonIcon();
            Toast.makeText(getContext(), "Layout: " + item.getTitle(), Toast.LENGTH_SHORT).show();
            return true;
        });

        popup.show();
    }

    private void showConfigOptions() {
        // TODO: Show button configuration dialog
        Toast.makeText(getContext(), "Button configuration coming soon", Toast.LENGTH_SHORT).show();
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        gamepadView.setEditMode(isEditMode);
        
        if (isEditMode) {
            editModeButton.setImageResource(R.drawable.ic_edit);
            Toast.makeText(getContext(), "Edit mode: Drag to reposition buttons", Toast.LENGTH_LONG).show();
        } else {
            editModeButton.setImageResource(R.drawable.ic_check);
            Toast.makeText(getContext(), "Edit mode: Saved", Toast.LENGTH_SHORT).show();
            // Save positions
            configManager.saveLayoutPositions(currentLayout, gamepadView.getComponentPositions());
        }
    }

    private void updateLayoutButtonIcon() {
        switch (currentLayout) {
            case XBOX:
                layoutButton.setImageResource(R.drawable.ic_xbox);
                break;
            case PLAYSTATION:
                layoutButton.setImageResource(R.drawable.ic_playstation);
                break;
            case NES:
                layoutButton.setImageResource(R.drawable.ic_nes);
                break;
        }
    }

    private void sendKeyEvent(int keyCode) {
        // Send HID key event via ConnectionManager
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            ConnectionManager connectionManager = activity.getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyEvent(0, keyCode);
                Log.d(TAG, "Sent key event: " + keyCode);
            } else {
                Log.w(TAG, "Not connected - cannot send key event");
            }
        }
    }

    private void sendKeyRelease() {
        // Send key release via ConnectionManager
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            ConnectionManager connectionManager = activity.getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyRelease();
                Log.d(TAG, "Sent key release");
            }
        }
    }

    private void sendMouseClick(int button, boolean press) {
        // Send mouse click via ConnectionManager
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            ConnectionManager connectionManager = activity.getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendMouseClick(button, press);
                Log.d(TAG, "Sent mouse " + (press ? "press" : "release") + ": button " + button);
            } else {
                Log.w(TAG, "Not connected - cannot send mouse click");
            }
        }
    }

    // Track currently pressed WASD keys to avoid repeat events
    private boolean wPressed = false;
    private boolean aPressed = false;
    private boolean sPressed = false;
    private boolean dPressed = false;

    private void sendAnalogInput(String stickId, float x, float y) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            ConnectionManager connectionManager = activity.getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                // Right stick = mouse movement
                if (stickId.equals("r")) {
                    sendRightStickMouse(connectionManager, x, y);
                } 
                // Left stick = WASD emulation
                else if (stickId.equals("l")) {
                    sendLeftStickWASD(connectionManager, x, y);
                }
            }
        }
    }

    /**
     * Send right stick as mouse movement
     */
    private void sendRightStickMouse(ConnectionManager connectionManager, float x, float y) {
        // Apply dead zone to prevent drift
        float deadZone = 0.15f;
        float xAdjusted = applyDeadZone(x, deadZone);
        float yAdjusted = applyDeadZone(y, deadZone);
        
        // Skip if both axes are in dead zone
        if (xAdjusted == 0 && yAdjusted == 0) {
            return;
        }
        
        // Apply sensitivity multiplier
        xAdjusted *= mouseSensitivity;
        yAdjusted *= mouseSensitivity;
        
        // Clamp to -1.0 to 1.0 range
        xAdjusted = Math.max(-1.0f, Math.min(1.0f, xAdjusted));
        yAdjusted = Math.max(-1.0f, Math.min(1.0f, yAdjusted));
        
        // Convert analog stick to mouse movement
        // Scale from -1.0..1.0 to -128..127
        int deltaX = (int) (xAdjusted * 127);
        int deltaY = (int) (yAdjusted * 127);
        
        connectionManager.sendMouseMovement(deltaX, deltaY, 0);
        Log.d(TAG, "Mouse movement: X=" + deltaX + ", Y=" + deltaY);
    }

    /**
     * Send left stick as WASD keys
     * W = Up, A = Left, S = Down, D = Right
     */
    private void sendLeftStickWASD(ConnectionManager connectionManager, float x, float y) {
        // Apply dead zone
        float deadZone = 0.2f; // Slightly larger dead zone for WASD
        
        float xAdjusted = applyDeadZone(x, deadZone);
        float yAdjusted = applyDeadZone(y, deadZone);
        
        // Determine which keys to press based on stick position
        boolean wantW = yAdjusted < -0.3f; // Up (negative Y)
        boolean wantS = yAdjusted > 0.3f;  // Down (positive Y)
        boolean wantA = xAdjusted < -0.3f; // Left (negative X)
        boolean wantD = xAdjusted > 0.3f;  // Right (positive X)
        
        // W key (Up)
        if (wantW && !wPressed) {
            connectionManager.sendKeyEvent(0, 26); // HID W key
            wPressed = true;
            Log.d(TAG, "WASD: W pressed");
        } else if (!wantW && wPressed) {
            connectionManager.sendKeyRelease();
            wPressed = false;
            Log.d(TAG, "WASD: W released");
        }
        
        // A key (Left)
        if (wantA && !aPressed) {
            connectionManager.sendKeyEvent(0, 4); // HID A key
            aPressed = true;
            Log.d(TAG, "WASD: A pressed");
        } else if (!wantA && aPressed) {
            connectionManager.sendKeyRelease();
            aPressed = false;
            Log.d(TAG, "WASD: A released");
        }
        
        // S key (Down)
        if (wantS && !sPressed) {
            connectionManager.sendKeyEvent(0, 22); // HID S key
            sPressed = true;
            Log.d(TAG, "WASD: S pressed");
        } else if (!wantS && sPressed) {
            connectionManager.sendKeyRelease();
            sPressed = false;
            Log.d(TAG, "WASD: S released");
        }
        
        // D key (Right)
        if (wantD && !dPressed) {
            connectionManager.sendKeyEvent(0, 7); // HID D key
            dPressed = true;
            Log.d(TAG, "WASD: D pressed");
        } else if (!wantD && dPressed) {
            connectionManager.sendKeyRelease();
            dPressed = false;
            Log.d(TAG, "WASD: D released");
        }
        
        // Log diagonal movement
        if (xAdjusted != 0 || yAdjusted != 0) {
            Log.d(TAG, "Left stick (WASD): X=" + String.format("%.2f", xAdjusted) + 
                  ", Y=" + String.format("%.2f", yAdjusted));
        }
    }

    /**
     * Apply dead zone to analog input
     * @param value Input value (-1.0 to 1.0)
     * @param deadZone Dead zone size (0.0 to 1.0)
     * @return Adjusted value with dead zone applied
     */
    private float applyDeadZone(float value, float deadZone) {
        // If within dead zone, return 0
        if (Math.abs(value) < deadZone) {
            return 0;
        }
        
        // Normalize to -1.0 to 1.0 range after removing dead zone
        // This ensures full range of motion is still usable
        return (value - Math.copySign(deadZone, value)) / (1.0f - deadZone);
    }

    /**
     * Set mouse sensitivity (0.5 = slow, 1.0 = normal, 2.0 = fast)
     */
    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.5f, Math.min(2.0f, sensitivity));
        prefs.edit().putFloat("mouse_sensitivity", this.mouseSensitivity).apply();
        Log.d(TAG, "Mouse sensitivity set to: " + this.mouseSensitivity);
    }

    /**
     * Get current mouse sensitivity
     */
    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    /**
     * Load saved sensitivity from preferences
     */
    private void loadSavedSensitivity() {
        mouseSensitivity = prefs.getFloat("mouse_sensitivity", 1.0f);
        Log.d(TAG, "Loaded mouse sensitivity: " + mouseSensitivity);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vibrator != null) {
            vibrator.cancel();
        }
        // Release all WASD keys to prevent stuck keys
        releaseAllWASDKeys();
    }

    /**
     * Release all WASD keys (cleanup on exit)
     */
    private void releaseAllWASDKeys() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager = 
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                if (wPressed || aPressed || sPressed || dPressed) {
                    connectionManager.sendKeyRelease();
                    Log.d(TAG, "Released all WASD keys");
                }
            }
        }
        wPressed = false;
        aPressed = false;
        sPressed = false;
        dPressed = false;
    }
}
