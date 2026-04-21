package com.openterface.keymod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
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
    public static final String SHOW_PANEL = "show_panel";

    // Mode constants
    public static final String MODE_KEYBOARD_MOUSE = "keyboard_mouse";
    public static final String MODE_GAMEPAD = "gamepad";
    public static final String MODE_NUMPAD = "numpad";
    public static final String MODE_SHORTCUTS = "shortcuts";
    public static final String MODE_MACROS = "macros";
    public static final String MODE_VOICE = "voice";
    public static final String MODE_PRESENTATION = "presentation";

    private CheckBox rememberChoiceCheckBox;
    private Button startButton;
    private Button skipButton;
    private TextView showTutorialLink;
    private SharedPreferences prefs;
    private String selectedMode = MODE_KEYBOARD_MOUSE;

    // Mode cards
    private CardView keyboardMouseCard;
    private CardView gamepadCard;
    private CardView numpadCard;
    private CardView shortcutsCard;
    private CardView macrosCard;
    private CardView voiceCard;
    private CardView presentationCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Skip launch panel only if auto-launch is enabled AND not explicitly requesting the panel
        boolean rememberChoice = prefs.getBoolean(REMEMBER_CHOICE_KEY, false);
        boolean showPanel = getIntent().getBooleanExtra(SHOW_PANEL, false);
        if (rememberChoice && !showPanel) {
            String lastMode = prefs.getString(LAST_MODE_KEY, MODE_KEYBOARD_MOUSE);
            launchModeInternal(lastMode);
            return;
        }

        setContentView(R.layout.activity_launch_panel);

        initializeViews();
        setupClickListeners();
        updateCardSelections();
    }

    private void initializeViews() {
        rememberChoiceCheckBox = findViewById(R.id.remember_choice_checkbox);
        startButton = findViewById(R.id.start_button);
        skipButton = findViewById(R.id.skip_button);
        showTutorialLink = findViewById(R.id.show_tutorial_link);

        // Mode cards
        keyboardMouseCard = findViewById(R.id.keyboard_mouse_card);
        gamepadCard = findViewById(R.id.gamepad_card);
        numpadCard = findViewById(R.id.numpad_card);
        shortcutsCard = findViewById(R.id.shortcuts_card);
        macrosCard = findViewById(R.id.macros_card);
        voiceCard = findViewById(R.id.voice_card);
        presentationCard = findViewById(R.id.presentation_card);
    }

    private void updateCardSelections() {
        keyboardMouseCard.setSelected(selectedMode.equals(MODE_KEYBOARD_MOUSE));
        gamepadCard.setSelected(selectedMode.equals(MODE_GAMEPAD));
        numpadCard.setSelected(selectedMode.equals(MODE_NUMPAD));
        shortcutsCard.setSelected(selectedMode.equals(MODE_SHORTCUTS));
        macrosCard.setSelected(selectedMode.equals(MODE_MACROS));
        voiceCard.setSelected(selectedMode.equals(MODE_VOICE));
        presentationCard.setSelected(selectedMode.equals(MODE_PRESENTATION));
    }

    private void setupClickListeners() {
        keyboardMouseCard.setOnClickListener(v -> {
            selectedMode = MODE_KEYBOARD_MOUSE;
            updateCardSelections();
        });

        gamepadCard.setOnClickListener(v -> {
            selectedMode = MODE_GAMEPAD;
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

        presentationCard.setOnClickListener(v -> {
            selectedMode = MODE_PRESENTATION;
            updateCardSelections();
        });

        startButton.setOnClickListener(v -> launchMode(selectedMode));

        skipButton.setOnClickListener(v -> {
            prefs.edit().putBoolean(REMEMBER_CHOICE_KEY, false).apply();
            launchModeInternal(MODE_KEYBOARD_MOUSE);
        });

        if (showTutorialLink != null) {
            showTutorialLink.setOnClickListener(v -> {
                getSharedPreferences(TutorialOverlay.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(TutorialOverlay.KEY_TUTORIAL_SHOWN, false).apply();
                finish();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
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
            case MODE_GAMEPAD:
                return "Gamepad";
            case MODE_NUMPAD:
                return "Numpad";
            case MODE_SHORTCUTS:
                return "Shortcut Hub";
            case MODE_MACROS:
                return "Macros";
            case MODE_VOICE:
                return "Voice Input";
            case MODE_PRESENTATION:
                return "Presentation";
            default:
                return "Keyboard & Mouse";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
