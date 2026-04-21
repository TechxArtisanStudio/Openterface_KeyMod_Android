package com.openterface.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

import java.util.concurrent.TimeUnit;

/**
 * Presentation Mode Fragment - Remote control for presentations.
 * Supports slide navigation (via arrow keys), timer, pace tracking, laser pointer, and black screen.
 */
public class PresentationFragment extends Fragment {

    private static final String TAG = "PresentationFragment";

    // Preference keys
    private static final String PREF_TIMER_DURATION = "presentation_timer_duration";
    private static final String PREF_TIMER_REMAINING = "presentation_timer_remaining";
    private static final String PREF_TIMER_RUNNING = "presentation_timer_running";
    private static final String PREF_BLACK_SCREEN = "presentation_black_screen";

    private static final int DEFAULT_TIMER_DURATION = 25 * 60; // 25 minutes in seconds

    // HID key codes
    private static final int KEY_RIGHT_ARROW = 79;
    private static final int KEY_LEFT_ARROW = 80;
    private static final int KEY_B = 5; // For black screen toggle
    private static final int KEY_TAB = 43;
    private static final int KEY_COMMAND = 227; // Left GUI/Cmd key

    // UI Views
    private TextView timerRemaining;
    private TextView timerTotal;
    private Button btnNext;
    private Button btnPrevious;
    private Button btnBlackScreen;
    private Button btnLaser;
    private Button btnPointer;
    private ImageButton btnEditTimer;
    private ImageButton btnTimerStart;
    private ImageButton btnTimerReset;
    private View settingsButton;
    private TextView connectionStatus;
    private TextView batteryLevel;

    // State
    private int timerDuration = DEFAULT_TIMER_DURATION; // in seconds
    private int timerRemainingSec = timerDuration;
    private boolean timerRunning = false;
    private boolean blackScreenActive = false;

    // Timer
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long timerStartTime = 0;

    // Vibrator
    private Vibrator vibrator;

    // Gesture detector for swipe support
    private GestureDetector gestureDetector;

    // Touchpad tracking
    private float lastPointerX = 0;
    private float lastPointerY = 0;
    private boolean isPointerMoving = false;

    // Root view for gesture handling
    private View rootView;

    private SharedPreferences prefs;

    // Touchpad dialog
    private AlertDialog touchpadDialog;
    private View touchpadView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Use custom theme with dark orange primary color for presentation mode
        ContextThemeWrapper themedContext = new ContextThemeWrapper(requireContext(), R.style.Theme_KeyMod_Presentation);
        LayoutInflater themedInflater = inflater.cloneInContext(themedContext);
        View view = themedInflater.inflate(R.layout.fragment_presentation, container, false);
        rootView = view;

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        timerHandler = new Handler(Looper.getMainLooper());

        // Load saved state
        loadState();

        // Initialize views
        initViews(view);
        setupListeners();
        setupGestureDetector(view);

        // Update UI
        updateTimerDisplay();

        // Resume timer if it was running
        if (timerRunning && timerRemainingSec > 0) {
            startTimer();
        }

        return view;
    }

    private void initViews(View view) {
        timerRemaining = view.findViewById(R.id.timer_remaining);
        timerTotal = view.findViewById(R.id.timer_total);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnBlackScreen = view.findViewById(R.id.btn_black_screen);
        btnLaser = view.findViewById(R.id.btn_laser);
        btnPointer = view.findViewById(R.id.btn_pointer);
        btnEditTimer = view.findViewById(R.id.btn_edit_timer);
        btnTimerStart = view.findViewById(R.id.btn_timer_start);
        btnTimerReset = view.findViewById(R.id.btn_timer_reset);
        settingsButton = view.findViewById(R.id.settings_button);
        connectionStatus = view.findViewById(R.id.connection_status);
        batteryLevel = view.findViewById(R.id.battery_level);

        // Update connection status
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                connectionStatus.setText("Connected");
            } else {
                connectionStatus.setText("Disconnected");
            }
        }

        // Set timer total text
        timerTotal.setText(" of " + formatTime(timerDuration));
    }

    private void setupListeners() {
        // Next button - send right arrow
        btnNext.setOnClickListener(v -> {
            vibrate();
            sendRightArrowKey();
        });

        // Previous button - send left arrow
        btnPrevious.setOnClickListener(v -> {
            vibrate();
            sendLeftArrowKey();
        });

        // Black screen toggle
        btnBlackScreen.setOnClickListener(v -> {
            vibrate();
            toggleBlackScreen();
        });

        // Laser pointer - Command+Tab to switch applications
        btnLaser.setOnClickListener(v -> {
            vibrate();
            sendCommandTab();
        });

        // Pointer mode - open touchpad dialog
        btnPointer.setOnClickListener(v -> {
            vibrate();
            showTouchpadDialog();
        });

        // Edit timer button
        btnEditTimer.setOnClickListener(v -> {
            showEditTimerDialog();
        });

        // Timer start/pause button
        btnTimerStart.setOnClickListener(v -> {
            vibrate();
            if (timerRunning) {
                stopTimer();
            } else {
                startTimer();
            }
            updateTimerButton();
        });

        // Timer reset button
        btnTimerReset.setOnClickListener(v -> {
            vibrate();
            resetTimer();
            updateTimerButton();
        });

        updateTimerButton();

        // Settings button
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                // Could open presentation settings dialog
                Toast.makeText(requireContext(), "Presentation settings coming soon", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupGestureDetector(View view) {
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Horizontal swipe
                    if (Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right = previous
                            sendLeftArrowKey();
                        } else {
                            // Swipe left = next
                            sendRightArrowKey();
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Tap = next
                sendRightArrowKey();
                return true;
            }
        });

        // Attach gesture detector to the root view for swipe and tap
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                return gestureDetector.onTouchEvent(event);
            });
        }
    }

    // ── Slide Navigation (simplified) ─────────────────────────────────

    private void sendRightArrowKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, KEY_RIGHT_ARROW);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.d(TAG, "Sent right arrow key");
            } else {
                Log.w(TAG, "Not connected - cannot send right arrow key");
            }
        }
    }

    private void sendLeftArrowKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, KEY_LEFT_ARROW);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.d(TAG, "Sent left arrow key");
            } else {
                Log.w(TAG, "Not connected - cannot send left arrow key");
            }
        }
    }

    // ── Application Switcher (Command+Tab) ────────────────────────────

    private void sendCommandTab() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                // Press Command key
                cm.sendKeyEvent(KEY_COMMAND, 0);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            // Press Tab key
                            c.sendKeyEvent(KEY_COMMAND, KEY_TAB);
                            timerHandler.postDelayed(() -> {
                                if (getActivity() instanceof MainActivity) {
                                    ConnectionManager conn = ((MainActivity) getActivity()).getConnectionManager();
                                    if (conn != null && conn.isConnected()) {
                                        // Release all keys
                                        conn.sendKeyRelease();
                                    }
                                }
                            }, 100);
                        }
                    }
                }, 100);
                Log.d(TAG, "Sent Command+Tab");
            } else {
                Log.w(TAG, "Not connected - cannot send Command+Tab");
            }
        }
    }

    // ── Touchpad Dialog ───────────────────────────────────────────────

    private void showTouchpadDialog() {
        if (touchpadDialog != null && touchpadDialog.isShowing()) {
            touchpadDialog.dismiss();
            return;
        }

        // Create touchpad view
        touchpadView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_touchpad, null);
        View touchSurface = touchpadView.findViewById(R.id.touch_surface);

        // Set up touch handling
        touchSurface.setOnTouchListener((v, event) -> {
            if (getActivity() instanceof MainActivity) {
                ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
                if (cm == null || !cm.isConnected()) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastPointerX = event.getX();
                        lastPointerY = event.getY();
                        isPointerMoving = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isPointerMoving) {
                            float deltaX = (event.getX() - lastPointerX) * 0.8f;
                            float deltaY = (event.getY() - lastPointerY) * 0.8f;

                            // Clamp to -127 to 127
                            int dx = (int) Math.max(-127, Math.min(127, deltaX));
                            int dy = (int) Math.max(-127, Math.min(127, deltaY));

                            if (dx != 0 || dy != 0) {
                                cm.sendMouseMovement(dx, dy, 0);
                            }

                            lastPointerX = event.getX();
                            lastPointerY = event.getY();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        isPointerMoving = false;
                        return true;
                }
            }
            return false;
        });

        // Build dialog
        touchpadDialog = new AlertDialog.Builder(requireContext())
                .setView(touchpadView)
                .setNegativeButton("Close", (dialog, which) -> {
                    dialog.dismiss();
                    touchpadDialog = null;
                })
                .create();

        touchpadDialog.show();
    }

    // ── Black Screen ─────────────────────────────────────────────────

    private void toggleBlackScreen() {
        blackScreenActive = !blackScreenActive;
        updateBlackScreenButton();

        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                // Send 'B' key to toggle black screen in most presentation software
                cm.sendKeyEvent(0, KEY_B);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
            }
        }

        Log.d(TAG, "Black screen: " + blackScreenActive);
    }

    private void updateBlackScreenButton() {
        if (blackScreenActive) {
            btnBlackScreen.setTextColor(Color.parseColor("#34D399"));
            btnBlackScreen.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_black_screen, 0, 0);
            // Tint the icon green
            btnBlackScreen.getCompoundDrawables()[1].setTint(Color.parseColor("#34D399"));
        } else {
            btnBlackScreen.setTextColor(Color.parseColor("#FFFFFFFF"));
            btnBlackScreen.getCompoundDrawables()[1].setTint(Color.parseColor("#FF9BA3C5"));
        }
    }

    // ── Application Switcher (Command+Tab) already implemented above ──

    // ── Touchpad Dialog already implemented above ─────────────────────

    // ── Timer ────────────────────────────────────────────────────────

    private void showEditTimerDialog() {
        EditText editText = new EditText(requireContext());
        int currentMinutes = timerDuration / 60;
        editText.setText(String.valueOf(currentMinutes));
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editText.setHint("Minutes");

        new AlertDialog.Builder(requireContext())
                .setTitle("Timer Duration (minutes)")
                .setView(editText)
                .setPositiveButton("OK", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int newMinutes = Integer.parseInt(input);
                        if (newMinutes > 0) {
                            timerDuration = newMinutes * 60;
                            timerRemainingSec = timerDuration;
                            timerRunning = false;
                            timerHandler.removeCallbacks(timerRunnable);
                            updateTimerDisplay();
                            timerTotal.setText(" of " + formatTime(timerDuration));
                            updateTimerButton();
                            saveState();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTimer() {
        if (timerRunning) return;
        timerRunning = true;
        timerStartTime = System.currentTimeMillis();
        updateTimerButton();
        saveState();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (timerRemainingSec > 0 && timerRunning) {
                    timerRemainingSec--;
                    updateTimerDisplay();
                    timerHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
                } else if (timerRemainingSec <= 0) {
                    timerRunning = false;
                    timerRemainingSec = 0;
                    updateTimerDisplay();
                    saveState();
                    // Alert user that time is up
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
            }
        };

        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerRunning = false;
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        updateTimerButton();
        saveState();
    }

    private void resetTimer() {
        stopTimer();
        timerRemainingSec = timerDuration;
        updateTimerDisplay();
        updateTimerButton();
        saveState();
    }

    private void updateTimerDisplay() {
        timerRemaining.setText(formatTime(timerRemainingSec));
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private void updateTimerButton() {
        if (timerRunning) {
            btnTimerStart.setImageResource(R.drawable.ic_pause);
            btnTimerStart.setContentDescription(getString(R.string.timer_pause));
        } else {
            btnTimerStart.setImageResource(R.drawable.ic_play);
            btnTimerStart.setContentDescription(getString(R.string.timer_start));
        }
    }

    // ── Persistence ──────────────────────────────────────────────────

    private void loadState() {
        timerDuration = prefs.getInt(PREF_TIMER_DURATION, DEFAULT_TIMER_DURATION);
        timerRemainingSec = prefs.getInt(PREF_TIMER_REMAINING, DEFAULT_TIMER_DURATION);
        timerRunning = prefs.getBoolean(PREF_TIMER_RUNNING, false);
        blackScreenActive = prefs.getBoolean(PREF_BLACK_SCREEN, false);
    }

    private void saveState() {
        prefs.edit()
            .putInt(PREF_TIMER_DURATION, timerDuration)
            .putInt(PREF_TIMER_REMAINING, timerRemainingSec)
            .putBoolean(PREF_TIMER_RUNNING, timerRunning)
            .putBoolean(PREF_BLACK_SCREEN, blackScreenActive)
            .apply();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (vibrator != null) vibrator.cancel();
        timerHandler.removeCallbacksAndMessages(null);
    }
}
