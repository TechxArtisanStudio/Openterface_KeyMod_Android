package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

/**
 * General Settings Fragment
 * - Connection preferences
 * - Display options
 * - Auto-connect settings
 */
public class GeneralSettingsFragment extends Fragment {

    private static final String PREF_AUTO_CONNECT = "auto_connect";
    private static final String PREF_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String PREF_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String PREF_ORIENTATION_LOCK = "orientation_lock";

    private CheckBox autoConnectCheckBox;
    private CheckBox keepScreenOnCheckBox;
    private CheckBox hapticFeedbackCheckBox;
    private CheckBox orientationLockCheckBox;
    private Spinner languageSpinner;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_general, container, false);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        
        return view;
    }

    private void initializeViews(View view) {
        autoConnectCheckBox = view.findViewById(R.id.auto_connect_checkbox);
        keepScreenOnCheckBox = view.findViewById(R.id.keep_screen_on_checkbox);
        hapticFeedbackCheckBox = view.findViewById(R.id.haptic_feedback_checkbox);
        orientationLockCheckBox = view.findViewById(R.id.orientation_lock_checkbox);
        languageSpinner = view.findViewById(R.id.language_spinner);
        
        // Setup language spinner
        String[] languages = {"English", "中文 (Chinese)", "Español", "Français"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
    }

    private void loadSettings() {
        autoConnectCheckBox.setChecked(prefs.getBoolean(PREF_AUTO_CONNECT, false));
        keepScreenOnCheckBox.setChecked(prefs.getBoolean(PREF_KEEP_SCREEN_ON, true));
        hapticFeedbackCheckBox.setChecked(prefs.getBoolean(PREF_HAPTIC_FEEDBACK, true));
        orientationLockCheckBox.setChecked(prefs.getBoolean(PREF_ORIENTATION_LOCK, false));
        
        // Load language preference
        int languageIndex = prefs.getInt("language_index", 0);
        languageSpinner.setSelection(languageIndex);
    }

    private void setupListeners() {
        autoConnectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_AUTO_CONNECT, isChecked).apply();
        });

        keepScreenOnCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_KEEP_SCREEN_ON, isChecked).apply();
            // Apply immediately if needed
            if (getActivity() != null) {
                getActivity().getWindow().setFlags(
                    isChecked ? android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0,
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            }
        });

        hapticFeedbackCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_HAPTIC_FEEDBACK, isChecked).apply();
        });

        orientationLockCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_ORIENTATION_LOCK, isChecked).apply();
        });

        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("language_index", position).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }
}
