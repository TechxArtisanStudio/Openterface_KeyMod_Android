package com.openterface.keymod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * Launch Panel - Mode selection screen as app entry point
 * Matches iOS LaunchPanelView.swift functionality
 */
public class LaunchPanelActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "LaunchPanelPrefs";
    private static final String REMEMBER_CHOICE_KEY = "rememberChoice";
    private static final String LAST_MODE_KEY = "lastMode";

    // Mode constants
    public static final String MODE_KEYBOARD_MOUSE = "keyboard_mouse";
    public static final String MODE_NUMPAD = "numpad";
    public static final String MODE_SHORTCUTS = "shortcuts";
    public static final String MODE_MACROS = "macros";
    public static final String MODE_VOICE = "voice";

    private CheckBox rememberChoiceCheckBox;
    private Button startButton;
    private Button skipButton;
    private SharedPreferences prefs;
    private String selectedMode = MODE_KEYBOARD_MOUSE;

    // Mode cards
    private CardView keyboardMouseCard;
    private CardView numpadCard;
    private CardView shortcutsCard;
    private CardView macrosCard;
    private CardView voiceCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_panel);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupClickListeners();
        updateCardSelections();
        checkLastMode();
    }

    private void initializeViews() {
        rememberChoiceCheckBox = findViewById(R.id.remember_choice_checkbox);
        startButton = findViewById(R.id.start_button);
        skipButton = findViewById(R.id.skip_button);

        // Mode cards
        keyboardMouseCard = findViewById(R.id.keyboard_mouse_card);
        numpadCard = findViewById(R.id.numpad_card);
        shortcutsCard = findViewById(R.id.shortcuts_card);
        macrosCard = findViewById(R.id.macros_card);
        voiceCard = findViewById(R.id.voice_card);
    }

    private void updateCardSelections() {
        keyboardMouseCard.setSelected(selectedMode.equals(MODE_KEYBOARD_MOUSE));
        numpadCard.setSelected(selectedMode.equals(MODE_NUMPAD));
        shortcutsCard.setSelected(selectedMode.equals(MODE_SHORTCUTS));
        macrosCard.setSelected(selectedMode.equals(MODE_MACROS));
        voiceCard.setSelected(selectedMode.equals(MODE_VOICE));
    }

    private void setupClickListeners() {
        keyboardMouseCard.setOnClickListener(v -> {
            selectedMode = MODE_KEYBOARD_MOUSE;
            updateCardSelections();
        });

        numpadCard.setOnClickListener(v -> {
            selectedMode = MODE_NUMPAD;
            updateCardSelections();
        });

        shortcutsCard.setOnClickListener(v -> {
            selectedMode = MODE_SHORTCUTS;
            updateCardSelections();
        });

        macrosCard.setOnClickListener(v -> {
            selectedMode = MODE_MACROS;
            updateCardSelections();
        });

        voiceCard.setOnClickListener(v -> {
            selectedMode = MODE_VOICE;
            updateCardSelections();
        });

        startButton.setOnClickListener(v -> launchMode(selectedMode));

        skipButton.setOnClickListener(v -> {
            prefs.edit().putBoolean(REMEMBER_CHOICE_KEY, false).apply();
            launchModeInternal(MODE_KEYBOARD_MOUSE);
        });
    }

    private void checkLastMode() {
        boolean rememberChoice = prefs.getBoolean(REMEMBER_CHOICE_KEY, false);
        
        if (rememberChoice) {
            String lastMode = prefs.getString(LAST_MODE_KEY, MODE_KEYBOARD_MOUSE);
            
            // Show toast that we're auto-launching
            Toast.makeText(this, "Launching " + getModeDisplayName(lastMode), Toast.LENGTH_SHORT).show();
            
            // Small delay for user to see the toast
            new Handler().postDelayed(() -> {
                launchModeInternal(lastMode);
            }, 800);
        }
    }

    private void launchMode(String mode) {
        // Save preference if checkbox is checked
        if (rememberChoiceCheckBox.isChecked()) {
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, true)
                .putString(LAST_MODE_KEY, mode)
                .apply();
            
            Toast.makeText(this, "Will remember: " + getModeDisplayName(mode), Toast.LENGTH_SHORT).show();
        } else {
            // Clear remembered choice
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, false)
                .apply();
        }

        launchModeInternal(mode);
    }

    private void launchModeInternal(String mode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("launch_mode", mode);
        
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private String getModeDisplayName(String mode) {
        switch (mode) {
            case MODE_KEYBOARD_MOUSE:
                return "Keyboard & Mouse";
            case MODE_NUMPAD:
                return "Numpad";
            case MODE_SHORTCUTS:
                return "Shortcut Hub";
            case MODE_MACROS:
                return "Macros";
            case MODE_VOICE:
                return "Voice Input";
            default:
                return "Keyboard & Mouse";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
