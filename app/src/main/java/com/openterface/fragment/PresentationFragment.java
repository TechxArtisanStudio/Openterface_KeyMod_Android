package com.openterface.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.PaceGaugeView;
import com.openterface.keymod.R;

import java.util.concurrent.TimeUnit;

/**
 * Presentation Mode Fragment - Remote control for presentations.
 * Supports slide navigation, timer, pace tracking, laser pointer, and black screen.
 */
public class PresentationFragment extends Fragment {

    private static final String TAG = "PresentationFragment";

    // Preference keys
    private static final String PREF_TOTAL_SLIDES = "presentation_total_slides";
    private static final String PREF_CURRENT_SLIDE = "presentation_current_slide";
    private static final String PREF_TIMER_DURATION = "presentation_timer_duration";
    private static final String PREF_TIMER_REMAINING = "presentation_timer_remaining";
    private static final String PREF_TIMER_RUNNING = "presentation_timer_running";
    private static final String PREF_BLACK_SCREEN = "presentation_black_screen";

    private static final int DEFAULT_TOTAL_SLIDES = 20;
    private static final int DEFAULT_TIMER_DURATION = 25 * 60; // 25 minutes in seconds

    // HID key codes
    private static final int KEY_RIGHT_ARROW = 79;
    private static final int KEY_LEFT_ARROW = 80;
    private static final int KEY_SPACE = 44;
    private static final int KEY_BACKSPACE = 42;
    private static final int KEY_B = 5; // For black screen toggle

    // UI Views
    private TextView slideNumber;
    private TextView slideTitle;
    private TextView slidePercentage;
    private LinearLayout slideProgressDots;
    private TextView timerRemaining;
    private TextView timerTotal;
    private PaceGaugeView paceGauge;
    private Button btnNext;
    private Button btnPrevious;
    private Button btnBlackScreen;
    private Button btnLaser;
    private Button btnPointer;
    private Button btnList;
    private ImageButton btnEditSlideCount;
    private ImageButton btnEditTimer;
    private ImageButton btnTimerStart;
    private ImageButton btnTimerReset;
    private View settingsButton;
    private TextView connectionStatus;
    private TextView batteryLevel;

    // State
    private int totalSlides = DEFAULT_TOTAL_SLIDES;
    private int currentSlide = 0;
    private int timerDuration = DEFAULT_TIMER_DURATION; // in seconds
    private int timerRemainingSec = DEFAULT_TIMER_DURATION;
    private boolean timerRunning = false;
    private boolean blackScreenActive = false;
    private boolean laserActive = false;
    private boolean pointerActive = false;

    // Timer
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long timerStartTime = 0;

    // Vibrator
    private Vibrator vibrator;

    // Gesture detector for swipe support
    private GestureDetector gestureDetector;

    // Long press tracking for laser
    private Handler longPressHandler;
    private Runnable longPressRunnable;
    private boolean isLongPressing = false;

    // Pointer movement tracking
    private float lastPointerX = 0;
    private float lastPointerY = 0;
    private boolean isPointerMoving = false;

    // Root view for gesture handling
    private View rootView;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_presentation, container, false);
        rootView = view;

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        timerHandler = new Handler(Looper.getMainLooper());
        longPressHandler = new Handler(Looper.getMainLooper());

        // Load saved state
        loadState();

        // Initialize views
        initViews(view);
        setupListeners();
        setupGestureDetector(view);

        // Update UI
        updateSlideDisplay();
        updateTimerDisplay();
        updatePaceGauge();

        // Resume timer if it was running
        if (timerRunning && timerRemainingSec > 0) {
            startTimer();
        }

        return view;
    }

    private void initViews(View view) {
        slideNumber = view.findViewById(R.id.slide_number);
        slideTitle = view.findViewById(R.id.slide_title);
        slidePercentage = view.findViewById(R.id.slide_percentage);
        slideProgressDots = view.findViewById(R.id.slide_progress_dots);
        timerRemaining = view.findViewById(R.id.timer_remaining);
        timerTotal = view.findViewById(R.id.timer_total);
        paceGauge = view.findViewById(R.id.pace_gauge);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnBlackScreen = view.findViewById(R.id.btn_black_screen);
        btnLaser = view.findViewById(R.id.btn_laser);
        btnPointer = view.findViewById(R.id.btn_pointer);
        btnList = view.findViewById(R.id.btn_list);
        btnEditSlideCount = view.findViewById(R.id.btn_edit_slide_count);
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
        // Next button - send space or right arrow
        btnNext.setOnClickListener(v -> {
            vibrate();
            nextSlide();
        });

        // Previous button - send backspace or left arrow
        btnPrevious.setOnClickListener(v -> {
            vibrate();
            previousSlide();
        });

        // Black screen toggle
        btnBlackScreen.setOnClickListener(v -> {
            vibrate();
            toggleBlackScreen();
        });

        // Laser pointer toggle
        btnLaser.setOnClickListener(v -> {
            vibrate();
            toggleLaser();
        });

        // Pointer mode toggle
        btnPointer.setOnClickListener(v -> {
            vibrate();
            togglePointer();
        });

        // List button (placeholder)
        btnList.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Slide list coming soon", Toast.LENGTH_SHORT).show();
        });

        // Edit slide count button
        btnEditSlideCount.setOnClickListener(v -> {
            showEditSlideCountDialog();
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

        // Long press detection for laser (on the main content area)
        if (rootView != null) {
            longPressRunnable = () -> {
                isLongPressing = true;
                if (!laserActive) {
                    laserActive = true;
                    updateLaserButton();
                    Log.d(TAG, "Laser activated via long press");
                }
            };

            rootView.setOnLongClickListener(v -> {
                longPressHandler.postDelayed(longPressRunnable, 500);
                return true;
            });

            rootView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (isLongPressing) {
                        isLongPressing = false;
                    }
                }
                return false;
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
                            // Swipe right = previous slide
                            previousSlide();
                        } else {
                            // Swipe left = next slide
                            nextSlide();
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Tap = next slide
                if (!pointerActive) {
                    nextSlide();
                    return true;
                }
                return false;
            }
        });

        // Attach gesture detector to the root view for swipe and tap
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                if (pointerActive) {
                    handlePointerMovement(event);
                }
                return gestureDetector.onTouchEvent(event);
            });
        }
    }

    private void handlePointerMovement(MotionEvent event) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm == null || !cm.isConnected()) return;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastPointerX = event.getX();
                    lastPointerY = event.getY();
                    isPointerMoving = true;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isPointerMoving) {
                        float deltaX = (event.getX() - lastPointerX) * 0.5f;
                        float deltaY = (event.getY() - lastPointerY) * 0.5f;

                        // Clamp to -127 to 127
                        int dx = (int) Math.max(-127, Math.min(127, deltaX));
                        int dy = (int) Math.max(-127, Math.min(127, deltaY));

                        if (dx != 0 || dy != 0) {
                            cm.sendMouseMovement(dx, dy, 0);
                        }

                        lastPointerX = event.getX();
                        lastPointerY = event.getY();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    isPointerMoving = false;
                    break;
            }
        }
    }

    // ── Slide Navigation ─────────────────────────────────────────────

    private void nextSlide() {
        if (currentSlide < totalSlides) {
            currentSlide++;
            updateSlideDisplay();
            updatePaceGauge();
            sendNextSlideKey();
            saveState();
        }
    }

    private void previousSlide() {
        if (currentSlide > 0) {
            currentSlide--;
            updateSlideDisplay();
            updatePaceGauge();
            sendPreviousSlideKey();
            saveState();
        }
    }

    private void sendNextSlideKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                // Send right arrow key (most universal for next slide)
                cm.sendKeyEvent(0, KEY_RIGHT_ARROW);
                // Release after a brief delay
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.d(TAG, "Sent next slide key (Right Arrow)");
            } else {
                Log.w(TAG, "Not connected - cannot send next slide key");
            }
        }
    }

    private void sendPreviousSlideKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                // Send left arrow key
                cm.sendKeyEvent(0, KEY_LEFT_ARROW);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.d(TAG, "Sent previous slide key (Left Arrow)");
            } else {
                Log.w(TAG, "Not connected - cannot send previous slide key");
            }
        }
    }

    // ── Slide Display ────────────────────────────────────────────────

    private void updateSlideDisplay() {
        slideNumber.setText("Slide " + currentSlide + " " + getString(R.string.of) + " " + totalSlides);
        slideTitle.setText(currentSlide == 0 ? "Ready to present" : "Slide " + currentSlide);

        // Update percentage
        int percentage = totalSlides > 0 ? (currentSlide * 100 / totalSlides) : 0;
        slidePercentage.setText(percentage + "%");

        // Update progress dots
        updateProgressDots();
    }

    private void showEditSlideCountDialog() {
        EditText editText = new EditText(requireContext());
        editText.setText(String.valueOf(totalSlides));
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editText.setHint("Total slides");

        new AlertDialog.Builder(requireContext())
                .setTitle("Total Slides")
                .setView(editText)
                .setPositiveButton("OK", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int newTotal = Integer.parseInt(input);
                        if (newTotal > 0) {
                            totalSlides = newTotal;
                            if (currentSlide > totalSlides) {
                                currentSlide = totalSlides;
                            }
                            updateSlideDisplay();
                            saveState();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
                            updatePaceGauge();
                            saveState();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateProgressDots() {
        slideProgressDots.removeAllViews();

        int maxDots = 20; // Maximum number of dots to show
        int dotCount = Math.min(totalSlides, maxDots);

        for (int i = 0; i < dotCount; i++) {
            View dot = new View(requireContext());
            int dotSize = dp(8);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(params);

            // Determine dot color based on position
            float position = (float) i / dotCount;
            float currentPos = totalSlides > 0 ? (float) currentSlide / totalSlides : 0;

            if (position < currentPos) {
                dot.setBackgroundResource(R.drawable.progress_dot_filled);
            } else {
                dot.setBackgroundResource(R.drawable.progress_dot_empty);
            }

            slideProgressDots.addView(dot);
        }
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

    // ── Laser Pointer ────────────────────────────────────────────────

    private void toggleLaser() {
        laserActive = !laserActive;
        updateLaserButton();
        Log.d(TAG, "Laser: " + laserActive);
    }

    private void updateLaserButton() {
        if (laserActive) {
            btnLaser.setTextColor(Color.parseColor("#EF4444"));
            btnLaser.getCompoundDrawables()[1].setTint(Color.parseColor("#EF4444"));
        } else {
            btnLaser.setTextColor(Color.parseColor("#FFFFFFFF"));
            btnLaser.getCompoundDrawables()[1].setTint(Color.parseColor("#FFEF4444"));
        }
    }

    // ── Pointer Mode ─────────────────────────────────────────────────

    private void togglePointer() {
        pointerActive = !pointerActive;
        updatePointerButton();
        Log.d(TAG, "Pointer: " + pointerActive);
    }

    private void updatePointerButton() {
        if (pointerActive) {
            btnPointer.setTextColor(Color.parseColor("#A855F7"));
            btnPointer.getCompoundDrawables()[1].setTint(Color.parseColor("#A855F7"));
        } else {
            btnPointer.setTextColor(Color.parseColor("#FFFFFFFF"));
            btnPointer.getCompoundDrawables()[1].setTint(Color.parseColor("#FFA855F7"));
        }
    }

    // ── Timer ────────────────────────────────────────────────────────

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
                    updatePaceGauge();
                    timerHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
                } else if (timerRemainingSec <= 0) {
                    timerRunning = false;
                    timerRemainingSec = 0;
                    updateTimerDisplay();
                    updatePaceGauge();
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
        updatePaceGauge();
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

    // ── Pace Gauge ───────────────────────────────────────────────────

    private void updatePaceGauge() {
        float progress = totalSlides > 0 ? (float) currentSlide / totalSlides : 0;
        float timeProgress = timerDuration > 0 ? (float) (timerDuration - timerRemainingSec) / timerDuration : 0;

        // Compare slide progress vs time progress
        // If slides > time = ahead (green), slides < time = behind (red)
        int paceState;
        float difference = progress - timeProgress;

        if (difference > 0.1) {
            paceState = 1; // Ahead of schedule
        } else if (difference < -0.1) {
            paceState = -1; // Behind schedule
        } else {
            paceState = 0; // On track
        }

        paceGauge.setPaceState(paceState);
        paceGauge.setProgress(progress);
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
        totalSlides = prefs.getInt(PREF_TOTAL_SLIDES, DEFAULT_TOTAL_SLIDES);
        currentSlide = prefs.getInt(PREF_CURRENT_SLIDE, 0);
        timerDuration = prefs.getInt(PREF_TIMER_DURATION, DEFAULT_TIMER_DURATION);
        timerRemainingSec = prefs.getInt(PREF_TIMER_REMAINING, DEFAULT_TIMER_DURATION);
        timerRunning = prefs.getBoolean(PREF_TIMER_RUNNING, false);
        blackScreenActive = prefs.getBoolean(PREF_BLACK_SCREEN, false);
    }

    private void saveState() {
        prefs.edit()
            .putInt(PREF_TOTAL_SLIDES, totalSlides)
            .putInt(PREF_CURRENT_SLIDE, currentSlide)
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
        longPressHandler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
    }
}
