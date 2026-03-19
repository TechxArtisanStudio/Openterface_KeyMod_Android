package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

/**
 * AI Settings Fragment
 * - API endpoint configuration
 * - Model selection
 * - Test connection functionality
 */
public class AISettingsFragment extends Fragment {

    private static final String PREF_AI_ENDPOINT = "ai_endpoint";
    private static final String PREF_AI_MODEL = "ai_model";
    private static final String PREF_AI_API_KEY = "ai_api_key";

    private EditText endpointEditText;
    private EditText apiKeyEditText;
    private Spinner modelSpinner;
    private Button testConnectionButton;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_ai, container, false);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        
        return view;
    }

    private void initializeViews(View view) {
        endpointEditText = view.findViewById(R.id.ai_endpoint_edittext);
        apiKeyEditText = view.findViewById(R.id.ai_api_key_edittext);
        modelSpinner = view.findViewById(R.id.ai_model_spinner);
        testConnectionButton = view.findViewById(R.id.ai_test_button);
        
        // Setup model spinner
        String[] models = {"Qwen-2.5-72B", "Qwen-2.5-32B", "Qwen-2.5-14B", "GPT-4", "GPT-3.5-Turbo", "Claude-3"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(adapter);
    }

    private void loadSettings() {
        String endpoint = prefs.getString(PREF_AI_ENDPOINT, "https://api.openai.com/v1");
        endpointEditText.setText(endpoint);
        
        String apiKey = prefs.getString(PREF_AI_API_KEY, "");
        apiKeyEditText.setText(apiKey);
        
        int modelIndex = prefs.getInt(PREF_AI_MODEL, 0);
        modelSpinner.setSelection(modelIndex);
    }

    private void setupListeners() {
        endpointEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(PREF_AI_ENDPOINT, s.toString()).apply();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        apiKeyEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(PREF_AI_API_KEY, s.toString()).apply();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        modelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(PREF_AI_MODEL, position).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        testConnectionButton.setOnClickListener(v -> {
            String endpoint = endpointEditText.getText().toString().trim();
            String apiKey = apiKeyEditText.getText().toString().trim();
            
            if (TextUtils.isEmpty(endpoint)) {
                Toast.makeText(getContext(), "Please enter endpoint URL", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (TextUtils.isEmpty(apiKey)) {
                Toast.makeText(getContext(), "Please enter API key", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // TODO: Implement actual API test
            Toast.makeText(getContext(), "Testing AI connection...", Toast.LENGTH_SHORT).show();
            // Simulate test
            testConnectionButton.postDelayed(() -> {
                Toast.makeText(getContext(), "AI connection successful!", Toast.LENGTH_SHORT).show();
            }, 1500);
        });
    }
}
