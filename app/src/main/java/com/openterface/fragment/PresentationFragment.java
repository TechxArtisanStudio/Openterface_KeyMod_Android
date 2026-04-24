package com.openterface.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;

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
    private static final String PREF_TIMER_MODE = "presentation_timer_mode";
    private static final String PREF_TOOL_INDEX = "presentation_tool_index";

    private static final int DEFAULT_TIMER_DURATION = 25 * 60; // 25 minutes in seconds

    public static final int TIMER_MODE_COUNTDOWN = 0;
    public static final int TIMER_MODE_COUNTUP = 1;

    // HID key codes
    private static final int KEY_RIGHT_ARROW = 79;
    private static final int KEY_LEFT_ARROW = 80;
    private static final int KEY_B = 5; // For black screen toggle
    private static final int KEY_TAB = 43;
    private static final int KEY_ESCAPE = 41;
    private static final int KEY_P = 19;
    private static final int KEY_L = 15;
    private static final int KEY_RETURN = 40;
    private static final int KEY_F5 = 68;
    private static final int KEY_COMMAND = 227; // Left GUI/Cmd key

    // Presentation tools and their key mappings
    enum PresentationTool {
        KEYNOTE("Keynote",
                0x04 | 0x08, KEY_P,    // Opt+Cmd+P
                KEY_ESCAPE,
                "⌥⌘P / ESC"),
        POWERPOINT("PowerPoint",
                0x02 | 0x08, KEY_RETURN, // Cmd+Shift+Enter (macOS)
                KEY_ESCAPE,
                "⇧⌘↵ / ESC"),
        GOOGLE_SLIDES("Google Slides",
                0x08, KEY_RETURN,        // Cmd+Enter
                KEY_ESCAPE,
                "⌘↵ / ESC"),
        WORD("Word",
                0, 0,                    // No play key
                KEY_ESCAPE,
                "F5 / ESC"),
        ADOBE_READER("Adobe Reader",
                0x08, KEY_L,             // Cmd+L (toggle fullscreen)
                KEY_ESCAPE,
                "⌘L / ESC");

        final String name;
        final int playModifier;
        final int playKey;
        final int stopKey;
        final String shortcutHint;

        PresentationTool(String name, int playModifier, int playKey, int stopKey, String shortcutHint) {
            this.name = name;
            this.playModifier = playModifier;
            this.playKey = playKey;
            this.stopKey = stopKey;
            this.shortcutHint = shortcutHint;
        }
    }

    // UI Views
    private TextView timerRemaining;
    private TextView timerLabel;
    private TextView timerTotal;
    private Button btnNext;
    private Button btnPrevious;
    private Button btnPlay;
    private Button btnBlackScreen;
    private Button btnLaser;
    private Button btnPointer;
    private TextView hintPlay;
    private ImageButton btnEditTimer;
    private ImageButton btnTimerStart;
    private ImageButton btnTimerReset;

    // Tool switcher views
    private RecyclerView toolCarousel;
    private LinearSnapHelper toolSnapHelper;
    private ToolCarouselAdapter toolAdapter;
    private boolean isProgrammaticScroll = false;

    // State
    private int timerDuration = DEFAULT_TIMER_DURATION; // in seconds
    private int timerRemainingSec = timerDuration;
    private int timerMode = TIMER_MODE_COUNTDOWN; // 0=countdown, 1=countup
    private boolean timerRunning = false;
    private boolean blackScreenActive = false;
    private boolean appSwitcherActive = false; // Cmd held, app switcher is open
    private boolean playActive = false; // Presentation play toggled

    // Tool state
    private PresentationTool currentTool = PresentationTool.KEYNOTE;

    // Timer
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long timerStartTime = 0;

    // Vibrator
    private Vibrator vibrator;

    // Gesture detector for swipe support
    private GestureDetector gestureDetector;

    // Root view for gesture handling
    private View rootView;

    private SharedPreferences prefs;

    // Touchpad dialog
    private Dialog touchpadDialog;
    private View touchpadView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_presentation, container, false);
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
        timerLabel = view.findViewById(R.id.timer_label);
        timerTotal = view.findViewById(R.id.timer_total);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnPlay = view.findViewById(R.id.btn_play);
        btnBlackScreen = view.findViewById(R.id.btn_black_screen);
        btnLaser = view.findViewById(R.id.btn_laser);
        btnPointer = view.findViewById(R.id.btn_pointer);
        hintPlay = view.findViewById(R.id.hint_play);
        btnEditTimer = view.findViewById(R.id.btn_edit_timer);
        btnTimerStart = view.findViewById(R.id.btn_timer_start);
        btnTimerReset = view.findViewById(R.id.btn_timer_reset);

        toolCarousel = view.findViewById(R.id.tool_carousel);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        toolCarousel.setLayoutManager(layoutManager);

        toolAdapter = new ToolCarouselAdapter(PresentationTool.values(), this::selectTool);
        toolCarousel.setAdapter(toolAdapter);

        // SnapHelper to center the selected item
        toolSnapHelper = new LinearSnapHelper();
        toolSnapHelper.attachToRecyclerView(toolCarousel);

        // Scroll to initial position
        isProgrammaticScroll = true;
        int startPos = toolAdapter.positionForTool(currentTool.ordinal());
        toolCarousel.post(() -> {
            toolCarousel.scrollToPosition(startPos);
            isProgrammaticScroll = false;
        });

        // Update currentTool when user scrolls to a different tool
        toolCarousel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (isProgrammaticScroll) return;
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapView = toolSnapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        int toolIdx = position % PresentationTool.values().length;
                        if (toolIdx != currentTool.ordinal()) {
                            currentTool = PresentationTool.values()[toolIdx];
                            saveState();
                            updateToolHint();
                            toolAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        });

        // Set timer label and total text based on mode
        updateToolHint();
        updateTimerLabel();
        timerTotal.setText(" of " + formatTime(timerDuration));
        updateBlackScreenButton();
    }

    private void setupListeners() {
        // No separate listeners needed — tool selection handled by carousel snap + item clicks

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

        // Play button - Opt+Cmd+P for Keynote play, ESC to stop
        btnPlay.setOnClickListener(v -> {
            vibrate();
            togglePlay();
        });

        // Black screen toggle
        btnBlackScreen.setOnClickListener(v -> {
            vibrate();
            toggleBlackScreen();
        });

        // App switcher button (Cmd+Tab) — supports stateful macOS app switcher:
        //   1st tap  → Cmd down + Tab down + Tab up  (opens switcher, Cmd held)
        //   next taps → Tab down + Tab up              (cycles apps, Cmd still held)
        //   double tap → release all                    (confirms selection)
        GestureDetector switcherGesture = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (appSwitcherActive) {
                            cycleAppSwitcher();
                        } else {
                            openAppSwitcher();
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (appSwitcherActive) {
                            closeAppSwitcher();
                        }
                        return true;
                    }
                });

        btnLaser.setOnTouchListener((v, event) -> {
            switcherGesture.onTouchEvent(event);
            return true;
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

    private void sendKeyHID(int keyCode) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, keyCode);
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
    }

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

    // ── Application Switcher (Cmd+Tab state machine) ─────────────────

    private int getAppSwitcherModifier() {
        if (getActivity() instanceof MainActivity) {
            String os = ((MainActivity) getActivity()).getTargetOs();
            if ("windows".equals(os) || "linux".equals(os)) {
                return 0x04; // Alt modifier bitmask
            }
        }
        return 0x08; // Cmd modifier bitmask (macOS default)
    }

    private void openAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            // If presentation is playing, stop it first
            if (playActive) {
                togglePlay();
                timerHandler.postDelayed(() -> openAppSwitcher(), 300);
                return;
            }
            int mod = getAppSwitcherModifier();
            // Press Cmd + Tab
            cm.sendKeyEvent(mod, KEY_TAB);
            // Release Tab after short delay (Cmd stays held)
            timerHandler.postDelayed(() -> {
                ConnectionManager c = getConnectionManager();
                if (c != null && c.isConnected()) {
                    c.sendRawHIDReport(mod, 0);
                }
            }, 100);
            appSwitcherActive = true;
            btnLaser.setTextColor(Color.parseColor("#34D399")); // green = active
            Log.d(TAG, "App switcher opened (modifier=" + mod + " held)");
            // Safety timeout: auto-release after 10s of inactivity
            timerHandler.postDelayed(() -> {
                if (appSwitcherActive) {
                    closeAppSwitcher();
                    Log.d(TAG, "App switcher auto-released (timeout)");
                }
            }, 3000);
        }
    }

    private void cycleAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            int mod = getAppSwitcherModifier();
            // Press Tab (modifier already held from previous step)
            cm.sendKeyEvent(mod, KEY_TAB);
            // Release Tab after short delay (Cmd stays held)
            timerHandler.postDelayed(() -> {
                ConnectionManager c = getConnectionManager();
                if (c != null && c.isConnected()) {
                    c.sendRawHIDReport(mod, 0);
                }
            }, 100);
            Log.d(TAG, "App switcher cycled");
        }
    }

    private void closeAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            cm.sendKeyRelease();
            appSwitcherActive = false;
            btnLaser.setTextColor(ContextCompat.getColor(requireContext(), R.color.presentation_button_content)); // theme default = inactive
            Log.d(TAG, "App switcher closed (all released)");
        }
    }

    private ConnectionManager getConnectionManager() {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).getConnectionManager();
        }
        return null;
    }

    // ── Touchpad Dialog ───────────────────────────────────────────────

    private void showTouchpadDialog() {
        if (touchpadDialog != null && touchpadDialog.isShowing()) {
            touchpadDialog.dismiss();
            return;
        }

        touchpadView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_touchpad, null);
        TouchPadView touchSurface = touchpadView.findViewById(R.id.touch_surface);

        touchSurface.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float x, float y, float lastX, float lastY) {
                if (getActivity() instanceof MainActivity) {
                    ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
                    if (cm == null || !cm.isConnected()) return;
                    float dx = x - lastX;
                    float dy = y - lastY;
                    int clampedDx = (int) Math.max(-127, Math.min(127, dx));
                    int clampedDy = (int) Math.max(-127, Math.min(127, dy));
                    if (clampedDx != 0 || clampedDy != 0) {
                        cm.sendMouseMovement(clampedDx, clampedDy, 0);
                    }
                }
            }

            @Override
            public void onTouchClick() {
                sendMouseClick(1);
            }

            @Override
            public void onTouchDoubleClick() {
                sendMouseClick(1);
                timerHandler.postDelayed(() -> sendMouseClick(1), 150);
            }

            @Override
            public void onTouchRightClick() {
                sendMouseClick(2);
            }
        });

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(touchpadView);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.Button btnClose = touchpadView.findViewById(R.id.btn_touchpad_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.setOnDismissListener(d -> {
            sendKeyHID(6); // 'C' to hide pointer
            touchpadDialog = null;
        });
        touchpadDialog = dialog;
        dialog.show();

        // Send 'C' to show pointer cursor in presentation software
        sendKeyHID(6); // HID code for 'C'
        Log.d(TAG, "Touchpad dialog shown, pointer enabled");
    }

    private void sendMouseClick(int buttons) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm == null || !cm.isConnected()) return;
            cm.sendMouseMovement(0, 0, buttons);
            timerHandler.postDelayed(() -> {
                if (getActivity() instanceof MainActivity) {
                    ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                    if (c != null && c.isConnected()) {
                        c.sendMouseMovement(0, 0, 0);
                    }
                }
            }, 100);
        }
    }

    // ── Black Screen ─────────────────────────────────────────────────

    private void togglePlay() {
        playActive = !playActive;
        updatePlayButton();

        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                if (playActive) {
                    int mod = currentTool.playModifier;
                    int key = currentTool.playKey;
                    if (key != 0) {
                        cm.sendKeyEvent(mod, key);
                        timerHandler.postDelayed(() -> {
                            ConnectionManager c = getConnectionManager();
                            if (c != null && c.isConnected()) c.sendKeyRelease();
                        }, 100);
                    }
                    Log.d(TAG, "Play: " + currentTool.name + " keys sent");
                } else {
                    cm.sendKeyEvent(0, currentTool.stopKey);
                    timerHandler.postDelayed(() -> {
                        ConnectionManager c = getConnectionManager();
                        if (c != null && c.isConnected()) c.sendKeyRelease();
                    }, 100);
                    Log.d(TAG, "Stop: " + currentTool.stopKey + " sent");
                }
            }
        }
    }

    private void updatePlayButton() {
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.presentation_button_content);
        if (playActive) {
            btnPlay.setText(R.string.stop);
            btnPlay.setBackgroundColor(Color.parseColor("#000000"));
            btnPlay.setTextColor(textPrimary);
            btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            btnPlay.setText(R.string.play);
            btnPlay.setBackgroundResource(R.drawable.presentation_primary_button);
            btnPlay.setTextColor(textPrimary);
            btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private void selectTool(PresentationTool tool) {
        vibrate();
        currentTool = tool;
        saveState();
        updateToolHint();
        toolAdapter.notifyDataSetChanged();
        scrollToCurrentTool();
    }

    private void updateToolHint() {
        if (hintPlay != null) {
            hintPlay.setText(currentTool.shortcutHint);
        }
    }

    private void scrollToCurrentTool() {
        isProgrammaticScroll = true;
        toolCarousel.post(() -> {
            int target = toolAdapter.positionForTool(currentTool.ordinal());
            toolCarousel.scrollToPosition(target);
            // Use LinearSmoothScroller for center snap
            LinearLayoutManager lm = (LinearLayoutManager) toolCarousel.getLayoutManager();
            if (lm != null) {
                lm.smoothScrollToPosition(toolCarousel, new RecyclerView.State(), target);
            }
            toolCarousel.postDelayed(() -> isProgrammaticScroll = false, 500);
        });
    }

    /**
     * Infinite carousel adapter. Uses a large virtual range so scrolling
     * wraps seamlessly via modular indexing.
     */
    private class ToolCarouselAdapter extends RecyclerView.Adapter<ToolCarouselAdapter.ViewHolder> {
        private static final int VIRTUAL_REPEAT = 10000;
        private final PresentationTool[] tools;
        private final java.util.function.Consumer<PresentationTool> onSelect;

        ToolCarouselAdapter(PresentationTool[] tools, java.util.function.Consumer<PresentationTool> onSelect) {
            this.tools = tools;
            this.onSelect = onSelect;
        }

        int positionForTool(int toolOrdinal) {
            // Start near the middle of the virtual range, aligned to tool index
            return (VIRTUAL_REPEAT / 2) * tools.length + toolOrdinal;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(requireContext());
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(32, 8, 32, 8);
            tv.setAllCaps(false);
            tv.setBackgroundResource(android.R.color.transparent);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PresentationTool tool = tools[position % tools.length];
            TextView tv = (TextView) holder.itemView;
            tv.setText(tool.name);
            boolean selected = (tool == currentTool);
            tv.setTextSize(selected ? 20 : 16);
            tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            int colorRes = selected ? R.color.presentation_text_primary : R.color.presentation_text_muted;
            tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        }

        @Override
        public int getItemCount() {
            return tools.length * VIRTUAL_REPEAT;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        onSelect.accept(tools[pos % tools.length]);
                    }
                });
            }
        }
    }

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
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.presentation_button_content);
        if (blackScreenActive) {
            btnBlackScreen.setBackgroundColor(Color.parseColor("#000000"));
            btnBlackScreen.setTextColor(textPrimary);
            btnBlackScreen.setText(R.string.show_screen);
            btnBlackScreen.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            btnBlackScreen.setBackgroundResource(R.drawable.presentation_action_button);
            btnBlackScreen.setTextColor(textPrimary);
            btnBlackScreen.setText(R.string.hide_screen);
            btnBlackScreen.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    // ── Application Switcher (Command+Tab) already implemented above ──

    // ── Touchpad Dialog already implemented above ─────────────────────

    // ── Timer ────────────────────────────────────────────────────────

    private void showEditTimerDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_timer, null);
        EditText editText = dialogView.findViewById(R.id.edit_timer_minutes);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_timer_mode);

        int currentMinutes = timerDuration / 60;
        editText.setText(String.valueOf(currentMinutes));
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editText.setHint("Minutes");

        // Set current mode
        radioGroup.check(timerMode == TIMER_MODE_COUNTDOWN
                ? R.id.radio_countdown : R.id.radio_countup);

        new AlertDialog.Builder(requireContext())
                .setTitle("Timer Settings")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int newMinutes = Integer.parseInt(input);
                        if (newMinutes > 0) {
                            timerDuration = newMinutes * 60;
                            int selectedMode = radioGroup.getCheckedRadioButtonId() == R.id.radio_countdown
                                    ? TIMER_MODE_COUNTDOWN : TIMER_MODE_COUNTUP;
                            timerMode = selectedMode;

                            if (timerMode == TIMER_MODE_COUNTDOWN) {
                                timerRemainingSec = timerDuration;
                            } else {
                                timerRemainingSec = 0;
                            }
                            timerRunning = false;
                            timerHandler.removeCallbacks(timerRunnable);
                            updateTimerDisplay();
                            updateTimerLabel();
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
                if (timerRunning) {
                    if (timerMode == TIMER_MODE_COUNTDOWN) {
                        if (timerRemainingSec > 0) {
                            timerRemainingSec--;
                            updateTimerDisplay();
                            timerHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
                        } else {
                            timerRunning = false;
                            timerRemainingSec = 0;
                            updateTimerDisplay();
                            saveState();
                            if (vibrator != null && vibrator.hasVibrator()) {
                                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                        }
                    } else {
                        // Countup mode
                        timerRemainingSec++;
                        updateTimerDisplay();
                        timerHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
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
        timerRemainingSec = timerMode == TIMER_MODE_COUNTDOWN ? timerDuration : 0;
        updateTimerDisplay();
        updateTimerButton();
        saveState();
    }

    private void updateTimerDisplay() {
        timerRemaining.setText(formatTime(timerRemainingSec));
    }

    private void updateTimerLabel() {
        if (timerMode == TIMER_MODE_COUNTDOWN) {
            timerLabel.setText(R.string.remaining);
            timerTotal.setVisibility(View.VISIBLE);
            timerTotal.setText(" of " + formatTime(timerDuration));
        } else {
            timerLabel.setText(R.string.elapsed);
            timerTotal.setVisibility(View.GONE);
        }
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
        timerMode = prefs.getInt(PREF_TIMER_MODE, TIMER_MODE_COUNTDOWN);
        timerRunning = prefs.getBoolean(PREF_TIMER_RUNNING, false);
        blackScreenActive = prefs.getBoolean(PREF_BLACK_SCREEN, false);
        int toolIdx = prefs.getInt(PREF_TOOL_INDEX, 0);
        currentTool = PresentationTool.values()[toolIdx % PresentationTool.values().length];
    }

    private void saveState() {
        prefs.edit()
            .putInt(PREF_TIMER_DURATION, timerDuration)
            .putInt(PREF_TIMER_REMAINING, timerRemainingSec)
            .putInt(PREF_TIMER_MODE, timerMode)
            .putBoolean(PREF_TIMER_RUNNING, timerRunning)
            .putBoolean(PREF_BLACK_SCREEN, blackScreenActive)
            .putInt(PREF_TOOL_INDEX, currentTool.ordinal())
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
        // Clean up app switcher state if navigating away
        if (appSwitcherActive) {
            ConnectionManager cm = getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyRelease();
            }
            appSwitcherActive = false;
            btnLaser.setTextColor(ContextCompat.getColor(requireContext(), R.color.presentation_button_content));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (vibrator != null) vibrator.cancel();
        timerHandler.removeCallbacksAndMessages(null);
    }
}
