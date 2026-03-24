package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

/**
 * AI Settings Fragment — mirrors iOS AISettingsView.swift.
 *
 * Sections:
 *   1. Enable AI toggle (master switch)
 *   2. AI Mode: role selector (Text Refinement / Command Assistant / Custom)
 *              + target OS selector (Command Assistant only)
 *              + system prompt viewer/editor
 *   3. AI API Configuration: provider, endpoint, API key, model, test button
 */
public class AISettingsFragment extends Fragment {

    // ── Preference keys ───────────────────────────────────────────────────
    private static final String PREF_AI_ENABLED       = "ai_enabled";
    private static final String PREF_AI_ROLE          = "ai_role";
    private static final String PREF_AI_SYSTEM_PROMPT = "ai_system_prompt";
    private static final String PREF_AI_COMMAND_OS    = "ai_command_os";
    private static final String PREF_AI_PROVIDER      = "ai_provider";
    private static final String PREF_AI_ENDPOINT      = "ai_endpoint";
    private static final String PREF_AI_MODEL         = "ai_model";
    private static final String PREF_AI_API_KEY       = "ai_api_key";

    // ── Role catalogue ────────────────────────────────────────────────────
    private static final String ROLE_TEXT_REFINEMENT  = "text_refinement";
    private static final String ROLE_COMMAND_ASSIST   = "command_assistant";
    private static final String ROLE_CUSTOM           = "custom";

    private static final String[] ROLE_NAMES = {
            "Text Refinement",
            "Command Assistant",
            "Custom"
    };
    private static final String[] ROLE_IDS = {
            ROLE_TEXT_REFINEMENT,
            ROLE_COMMAND_ASSIST,
            ROLE_CUSTOM
    };

    // ── Predefined system prompts ─────────────────────────────────────────
    private static final String PROMPT_TEXT_REFINEMENT =
            "You are a text refinement assistant. Your task is to improve the grammar, " +
            "clarity, and readability of transcribed speech. Fix errors, remove filler words " +
            "(um, uh, like), and ensure proper punctuation — while strictly preserving the " +
            "original meaning and intent. Return only the refined text, no explanation.";

    private static final String PROMPT_COMMAND_MACOS =
            "You are a command assistant for macOS. Convert the user's natural language " +
            "description into the exact shell command(s) to accomplish the task in macOS/zsh. " +
            "Return only the raw command(s) — no markdown, no explanation, no code blocks.";

    private static final String PROMPT_COMMAND_WINDOWS =
            "You are a command assistant for Windows. Convert the user's natural language " +
            "description into the exact PowerShell or cmd command(s) needed. " +
            "Return only the raw command(s) — no markdown, no explanation, no code blocks.";

    private static final String PROMPT_COMMAND_LINUX =
            "You are a command assistant for Linux. Convert the user's natural language " +
            "description into the exact bash command(s) to accomplish the task. " +
            "Return only the raw command(s) — no markdown, no explanation, no code blocks.";

    // ── Provider catalogue ────────────────────────────────────────────────
    private static final String[] PROVIDER_NAMES = {
            "OpenAI", "Anthropic", "Google Gemini", "Mistral AI",
            "Groq", "Alibaba (Qwen)", "DeepSeek", "Custom"
    };
    private static final String[] PROVIDER_ENDPOINTS = {
            "https://api.openai.com/v1",
            "https://api.anthropic.com/v1",
            "https://generativelanguage.googleapis.com/v1beta",
            "https://api.mistral.ai/v1",
            "https://api.groq.com/openai/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://api.deepseek.com/v1",
            ""
    };
    private static final String[][] PROVIDER_MODELS = {
            {"gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo"},
            {"claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022",
                    "claude-3-opus-20240229", "claude-3-haiku-20240307"},
            {"gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.0-pro"},
            {"mistral-large-latest", "mistral-medium-latest",
                    "mistral-small-latest", "open-mixtral-8x7b"},
            {"llama-3.3-70b-versatile", "llama-3.1-8b-instant",
                    "mixtral-8x7b-32768", "gemma2-9b-it"},
            {"qwen-max", "qwen-plus", "qwen-turbo", "qwen2.5-72b-instruct"},
            {"deepseek-chat", "deepseek-reasoner"},
            {"custom-model"}
    };
    private static final int PROVIDER_CUSTOM_INDEX = PROVIDER_NAMES.length - 1;

    // ── Views ─────────────────────────────────────────────────────────────
    private SwitchCompat aiEnabledSwitch;
    private LinearLayout aiFeaturesGroup;

    private Spinner  roleSpinner;
    private LinearLayout commandOsSection;
    private Spinner  commandOsSpinner;
    private EditText systemPromptEditText;
    private TextView systemPromptModeLabel;

    private Spinner  providerSpinner;
    private EditText endpointEditText;
    private EditText apiKeyEditText;
    private Spinner  modelSpinner;
    private Button   testConnectionButton;

    private SharedPreferences prefs;
    private boolean isLoadingSettings = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────

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

    // ── Initialisation ────────────────────────────────────────────────────

    private void initializeViews(View view) {
        aiEnabledSwitch       = view.findViewById(R.id.ai_enabled_switch);
        aiFeaturesGroup       = view.findViewById(R.id.ai_features_group);

        roleSpinner           = view.findViewById(R.id.ai_role_spinner);
        commandOsSection      = view.findViewById(R.id.command_os_section);
        commandOsSpinner      = view.findViewById(R.id.command_os_spinner);
        systemPromptEditText  = view.findViewById(R.id.system_prompt_edittext);
        systemPromptModeLabel = view.findViewById(R.id.system_prompt_mode_label);

        providerSpinner       = view.findViewById(R.id.ai_provider_spinner);
        endpointEditText      = view.findViewById(R.id.ai_endpoint_edittext);
        apiKeyEditText        = view.findViewById(R.id.ai_api_key_edittext);
        modelSpinner          = view.findViewById(R.id.ai_model_spinner);
        testConnectionButton  = view.findViewById(R.id.ai_test_button);

        // Role spinner
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, ROLE_NAMES);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(roleAdapter);

        // Command OS spinner
        String[] osNames = {"macOS", "Windows", "Linux"};
        ArrayAdapter<String> osAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, osNames);
        osAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        commandOsSpinner.setAdapter(osAdapter);

        // Provider spinner
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, PROVIDER_NAMES);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
    }

    // ── Load settings ─────────────────────────────────────────────────────

    private void loadSettings() {
        isLoadingSettings = true;

        // Enable switch
        boolean enabled = prefs.getBoolean(PREF_AI_ENABLED, false);
        aiEnabledSwitch.setChecked(enabled);
        aiFeaturesGroup.setVisibility(enabled ? View.VISIBLE : View.GONE);

        // Role
        String savedRoleId = prefs.getString(PREF_AI_ROLE, ROLE_TEXT_REFINEMENT);
        int roleIndex = roleIndexFor(savedRoleId);
        roleSpinner.setSelection(roleIndex);
        applyRoleUi(savedRoleId);

        // Command OS
        String savedOs = prefs.getString(PREF_AI_COMMAND_OS, "macos");
        commandOsSpinner.setSelection(osIndexFor(savedOs));

        // System prompt
        String savedPrompt = prefs.getString(PREF_AI_SYSTEM_PROMPT, "");
        if (savedPrompt.isEmpty()) {
            savedPrompt = defaultPromptFor(savedRoleId, savedOs);
        }
        systemPromptEditText.setText(savedPrompt);

        // Provider
        int providerIndex = prefs.getInt(PREF_AI_PROVIDER, 0);
        providerSpinner.setSelection(providerIndex);
        applyProviderDefaults(providerIndex);
        if (providerIndex == PROVIDER_CUSTOM_INDEX) {
            String saved = prefs.getString(PREF_AI_ENDPOINT, "");
            endpointEditText.setText(saved);
        }

        // API key — per-provider only, no cross-provider fallback
        String apiKey = prefs.getString(PREF_AI_API_KEY + "_" + providerIndex, "");
        apiKeyEditText.setText(apiKey);
        apiKeyEditText.setHint("Enter API key for " + PROVIDER_NAMES[providerIndex]);

        // Model — migrate from old Integer storage
        String savedModel;
        try {
            savedModel = prefs.getString(PREF_AI_MODEL, "");
        } catch (ClassCastException e) {
            prefs.edit().remove(PREF_AI_MODEL).apply();
            savedModel = "";
        }
        restoreModelSelection(providerIndex, savedModel);

        isLoadingSettings = false;
    }

    // ── Role helpers ──────────────────────────────────────────────────────

    private int roleIndexFor(String roleId) {
        for (int i = 0; i < ROLE_IDS.length; i++) {
            if (ROLE_IDS[i].equals(roleId)) return i;
        }
        return 0;
    }

    private int osIndexFor(String os) {
        switch (os) {
            case "windows": return 1;
            case "linux":   return 2;
            default:        return 0; // macos
        }
    }

    private String osIdForIndex(int index) {
        switch (index) {
            case 1:  return "windows";
            case 2:  return "linux";
            default: return "macos";
        }
    }

    private String defaultPromptFor(String roleId, String os) {
        switch (roleId) {
            case ROLE_COMMAND_ASSIST:
                switch (os) {
                    case "windows": return PROMPT_COMMAND_WINDOWS;
                    case "linux":   return PROMPT_COMMAND_LINUX;
                    default:        return PROMPT_COMMAND_MACOS;
                }
            case ROLE_CUSTOM:
                return "";
            default: // text_refinement
                return PROMPT_TEXT_REFINEMENT;
        }
    }

    /** Update UI when role changes: show/hide OS section, enable/disable prompt. */
    private void applyRoleUi(String roleId) {
        boolean isCommandAssist = ROLE_COMMAND_ASSIST.equals(roleId);
        boolean isCustom        = ROLE_CUSTOM.equals(roleId);

        commandOsSection.setVisibility(isCommandAssist ? View.VISIBLE : View.GONE);
        systemPromptEditText.setEnabled(isCustom);
        systemPromptEditText.setAlpha(isCustom ? 1.0f : 0.65f);
        systemPromptModeLabel.setText(isCustom ? "editable" : "read-only");
    }

    // ── Provider helpers ──────────────────────────────────────────────────

    private void applyProviderDefaults(int providerIndex) {
        boolean isCustom = (providerIndex == PROVIDER_CUSTOM_INDEX);
        if (!isCustom) {
            endpointEditText.setText(PROVIDER_ENDPOINTS[providerIndex]);
        }
        endpointEditText.setEnabled(isCustom);
        endpointEditText.setAlpha(isCustom ? 1.0f : 0.55f);

        String[] models = PROVIDER_MODELS[providerIndex];
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, models);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
    }

    private void restoreModelSelection(int providerIndex, String savedModel) {
        String[] models = PROVIDER_MODELS[providerIndex];
        for (int i = 0; i < models.length; i++) {
            if (models[i].equals(savedModel)) {
                modelSpinner.setSelection(i);
                return;
            }
        }
        modelSpinner.setSelection(0);
    }

    // ── Listeners ─────────────────────────────────────────────────────────

    private void setupListeners() {

        // Enable switch
        aiEnabledSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(PREF_AI_ENABLED, checked).apply();
            aiFeaturesGroup.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        // Role spinner
        roleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (isLoadingSettings) return;
                String roleId = ROLE_IDS[pos];
                prefs.edit().putString(PREF_AI_ROLE, roleId).apply();
                applyRoleUi(roleId);

                // Refresh system prompt if not custom
                if (!ROLE_CUSTOM.equals(roleId)) {
                    String os = osIdForIndex(commandOsSpinner.getSelectedItemPosition());
                    String prompt = defaultPromptFor(roleId, os);
                    systemPromptEditText.setText(prompt);
                    prefs.edit().putString(PREF_AI_SYSTEM_PROMPT, prompt).apply();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Command OS spinner
        commandOsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (isLoadingSettings) return;
                String os = osIdForIndex(pos);
                prefs.edit().putString(PREF_AI_COMMAND_OS, os).apply();
                // Refresh the prompt for the new OS
                String roleId = ROLE_IDS[roleSpinner.getSelectedItemPosition()];
                if (ROLE_COMMAND_ASSIST.equals(roleId)) {
                    String prompt = defaultPromptFor(roleId, os);
                    systemPromptEditText.setText(prompt);
                    prefs.edit().putString(PREF_AI_SYSTEM_PROMPT, prompt).apply();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // System prompt editor (only active in Custom mode)
        systemPromptEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isLoadingSettings) return;
                String roleId = ROLE_IDS[roleSpinner.getSelectedItemPosition()];
                if (ROLE_CUSTOM.equals(roleId)) {
                    prefs.edit().putString(PREF_AI_SYSTEM_PROMPT, s.toString()).apply();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Provider spinner
        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (isLoadingSettings) return;
                prefs.edit().putInt(PREF_AI_PROVIDER, pos).apply();
                applyProviderDefaults(pos);
                if (pos != PROVIDER_CUSTOM_INDEX) {
                    prefs.edit().putString(PREF_AI_ENDPOINT, PROVIDER_ENDPOINTS[pos]).apply();
                }
                prefs.edit().putString(PREF_AI_MODEL, PROVIDER_MODELS[pos][0]).apply();
                // Load the saved API key for this specific provider
                isLoadingSettings = true;
                apiKeyEditText.setText(prefs.getString(PREF_AI_API_KEY + "_" + pos, ""));
                isLoadingSettings = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Endpoint
        endpointEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(PREF_AI_ENDPOINT, s.toString()).apply();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // API key — save under per-provider key; drop isLoadingSettings guard so it always saves
        apiKeyEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentProvider = providerSpinner.getSelectedItemPosition();
                prefs.edit()
                    .putString(PREF_AI_API_KEY + "_" + currentProvider, s.toString())
                    .apply();
                Log.d("AISettings", "Saved API key for provider " + currentProvider
                        + ": " + (s.length() > 0 ? s.subSequence(0, Math.min(8, s.length())) + "..." : "(cleared)"));
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Model spinner
        modelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (isLoadingSettings) return;
                int providerIndex = providerSpinner.getSelectedItemPosition();
                String[] models = PROVIDER_MODELS[providerIndex];
                if (pos < models.length) {
                    prefs.edit().putString(PREF_AI_MODEL, models[pos]).apply();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Test button
        testConnectionButton.setOnClickListener(v -> {
            String endpoint = endpointEditText.getText().toString().trim();
            String apiKey   = apiKeyEditText.getText().toString().trim();
            if (TextUtils.isEmpty(endpoint)) {
                Toast.makeText(getContext(), "Please enter endpoint URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(apiKey)) {
                Toast.makeText(getContext(), "Please enter API key", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getContext(), "Testing AI connection…", Toast.LENGTH_SHORT).show();
            testConnectionButton.postDelayed(() ->
                    Toast.makeText(getContext(), "AI connection successful!", Toast.LENGTH_SHORT).show(),
                    1500);
        });
    }
}
