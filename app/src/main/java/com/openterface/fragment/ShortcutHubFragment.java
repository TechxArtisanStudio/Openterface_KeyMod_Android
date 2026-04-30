package com.openterface.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;
import com.openterface.keymod.ShortcutProfileManager.ProfileChangeListener;
import com.openterface.keymod.util.KeyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shortcut Hub Fragment - Profile management for app-specific shortcuts
 * Phase 3: Shortcut Hub
 */
public class ShortcutHubFragment extends Fragment implements ProfileChangeListener {

    private static final String TAG = "ShortcutHubFragment";

    private ShortcutProfileManager profileManager;
    private Vibrator vibrator;
    private ConnectionManager connectionManager;

    // UI Components - Profile list panel
    private LinearLayout panelProfilesList;
    private GridView profilesGridView;
    private TextView emptyTextView;
    private TextView activeProfileText;
    private Button createProfileButton;
    private Button importButton;
    private Button exportButton;

    // UI Components - Shortcuts detail panel
    private LinearLayout panelShortcutsDetail;
    private Button backButton;
    private Button addShortcutButton;
    private TextView detailProfileName;
    private TextView detailProfileDescription;
    private Button tabMy;
    private LinearLayout tabsContainer;
    private GridView shortcutsGridView;
    private TextView emptyMyShortcuts;

    private static final String TAB_MY  = "my";
    private String currentTab = TAB_MY;  // Default to favorites
    private String currentCategoryId = null;  // Current category when in All tab

    private ProfilesAdapter adapter;
    private ShortcutsAdapter shortcutsAdapter;
    private List<ShortcutProfile> profilesList;
    private List<ShortcutProfileManager.Shortcut> myShortcutsList = new ArrayList<>();
    private ShortcutProfile activeProfile;
    private ShortcutProfile selectedProfile;  // profile whose shortcuts are shown

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shortcut_hub, container, false);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        profileManager = new ShortcutProfileManager(requireContext());
        profileManager.setListener(this);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        loadProfiles();
        setupListeners();

        return view;
    }

    private final MainActivity.OnTargetOsChangeListener osChangeListener = os -> {
        if (shortcutsAdapter != null) {
            shortcutsAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).addOsChangeListener(osChangeListener);
        }
        if (selectedProfile != null && panelShortcutsDetail.getVisibility() == View.VISIBLE) {
            profileManager.reloadProfilesFromPreferences();
            loadProfiles();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).removeOsChangeListener(osChangeListener);
        }
    }

    private void initializeViews(View view) {
        // Profile list panel
        panelProfilesList = view.findViewById(R.id.panel_profiles_list);
        profilesGridView = view.findViewById(R.id.profiles_gridview);
        emptyTextView = view.findViewById(R.id.empty_textview);
        activeProfileText = view.findViewById(R.id.active_profile_text);
        createProfileButton = view.findViewById(R.id.create_profile_button);
        importButton = view.findViewById(R.id.import_button);
        exportButton = view.findViewById(R.id.export_button);

        // Shortcuts detail panel
        panelShortcutsDetail = view.findViewById(R.id.panel_shortcuts_detail);
        backButton = view.findViewById(R.id.back_button);
        addShortcutButton = view.findViewById(R.id.add_shortcut_button);
        detailProfileName = view.findViewById(R.id.detail_profile_name);
        detailProfileDescription = view.findViewById(R.id.detail_profile_description);
        tabMy = view.findViewById(R.id.tab_my);
        tabsContainer = view.findViewById(R.id.tabs_container);
        shortcutsGridView = view.findViewById(R.id.shortcuts_gridview);
        emptyMyShortcuts = view.findViewById(R.id.empty_my_shortcuts);

        profilesList = new ArrayList<>();
        adapter = new ProfilesAdapter(requireContext(), profilesList);
        profilesGridView.setAdapter(adapter);

        shortcutsAdapter = new ShortcutsAdapter(requireContext(), new ArrayList<>());
        shortcutsGridView.setAdapter(shortcutsAdapter);
    }

    private void loadProfiles() {
        profilesList.clear();
        profilesList.addAll(profileManager.getAllProfiles());
        
        activeProfile = profileManager.getActiveProfile();
        adapter.setActiveProfileId(activeProfile != null ? activeProfile.id : null);
        adapter.notifyDataSetChanged();
        updateActiveProfileDisplay();
        updateEmptyState();
        if (selectedProfile != null && panelShortcutsDetail != null
                && panelShortcutsDetail.getVisibility() == View.VISIBLE) {
            myShortcutsList = profileManager.getMyShortcuts(selectedProfile.id);
            ShortcutProfile updated = profileManager.getProfileById(selectedProfile.id);
            if (updated != null) {
                selectedProfile = updated;
                rebuildCategoryTabs(selectedProfile);
            }
            refreshShortcutsGrid();
        }
    }

    private void setupListeners() {
        // Create profile button
        createProfileButton.setOnClickListener(v -> showCreateProfileDialog());

        // Import button
        importButton.setOnClickListener(v -> {
            showImportDialog();
        });

        // Export button
        exportButton.setOnClickListener(v -> {
            if (activeProfile != null) {
                exportProfileToFile(activeProfile);
            } else {
                Toast.makeText(getContext(), "No active profile", Toast.LENGTH_SHORT).show();
            }
        });

        // Grid item click - show profile shortcuts
        profilesGridView.setOnItemClickListener((parent, view, position, id) -> {
            ShortcutProfile profile = profilesList.get(position);
            showShortcutsDetail(profile);
        });

        // Grid item long click - show options
        profilesGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            ShortcutProfile profile = profilesList.get(position);
            showProfileOptionsDialog(profile);
            return true;
        });

        // Back button - return to profile list
        backButton.setOnClickListener(v -> showProfileList());

        // Add shortcut button
        addShortcutButton.setOnClickListener(v -> {
            if (selectedProfile != null) {
                showAddShortcutDialog(selectedProfile);
            }
        });

        // Tab: ⭐ My
        tabMy.setOnClickListener(v -> switchTab(TAB_MY, null));

        // Shortcut tap - execute the shortcut
        shortcutsGridView.setOnItemClickListener((parent, view, position, id) -> {
            if (shortcutsAdapter.getCount() > 0) {
                ShortcutProfileManager.Shortcut shortcut =
                        (ShortcutProfileManager.Shortcut) shortcutsAdapter.getItem(position);
                executeShortcut(shortcut);
            }
        });

        // Shortcut long-press - show edit/remove menu
        shortcutsGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (shortcutsAdapter.getCount() > 0) {
                ShortcutProfileManager.Shortcut shortcut =
                        (ShortcutProfileManager.Shortcut) shortcutsAdapter.getItem(position);
                showShortcutActionsMenu(shortcut);
            }
            return true;
        });
    }

    private void showShortcutsDetail(ShortcutProfile profile) {
        selectedProfile = profile;
        currentTab = TAB_MY;  // Default to My Shortcuts
        currentCategoryId = null;

        detailProfileName.setText(profile.name);
        detailProfileDescription.setText(profile.description);

        // Load persisted My Shortcuts for this profile
        myShortcutsList = profileManager.getMyShortcuts(profile.id);

        // Build dynamic category tabs
        rebuildCategoryTabs(profile);
        updateTabUI();
        refreshShortcutsGrid();

        panelProfilesList.setVisibility(View.GONE);
        panelShortcutsDetail.setVisibility(View.VISIBLE);
    }

    private void showProfileList() {
        selectedProfile = null;
        panelShortcutsDetail.setVisibility(View.GONE);
        panelProfilesList.setVisibility(View.VISIBLE);
    }

    private void switchTab(String tab, String categoryId) {
        currentTab = tab;
        currentCategoryId = categoryId;
        updateTabUI();
        refreshShortcutsGrid();
    }

    private void switchToCategory(String categoryId) {
        currentCategoryId = categoryId;
        updateTabUI();
        refreshShortcutsGrid();
    }

    private void updateTabUI() {
        int primaryColor   = requireContext().getColor(R.color.primary);
        int whiteColor     = requireContext().getColor(R.color.white);
        int secondaryColor = requireContext().getColor(R.color.text_secondary);

        // Update My Shortcuts tab
        boolean mySelected = TAB_MY.equals(currentTab);
        if (mySelected) {
            tabMy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            tabMy.setTextColor(whiteColor);
        } else {
            tabMy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            tabMy.setTextColor(secondaryColor);
        }

        // Update category tabs (all children in tabsContainer)
        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            View child = tabsContainer.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                String catId = (String) btn.getTag();
                boolean selected = catId != null && catId.equals(currentCategoryId);
                if (selected) {
                    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                    btn.setTextColor(whiteColor);
                } else {
                    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                    btn.setTextColor(secondaryColor);
                }
            }
        }
    }

    private void rebuildCategoryTabs(ShortcutProfileManager.ShortcutProfile profile) {
        // Clear all existing category tabs
        tabsContainer.removeAllViews();

        // Add buttons for each category
        if (profile.categories != null && !profile.categories.isEmpty()) {
            for (ShortcutProfileManager.ShortcutCategory cat : profile.categories) {
                Button btn = new Button(requireContext());
                btn.setText(cat.name);
                btn.setTag(cat.id);  // Store category ID in tag
                btn.setTextSize(12);
                btn.setAllCaps(false);
                btn.setMinWidth(0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dpToPx(36)
                );
                lp.setMarginEnd(dpToPx(8));
                btn.setLayoutParams(lp);
                btn.setPadding(dpToPx(16), 0, dpToPx(16), 0);
                btn.setOnClickListener(v -> switchToCategory(cat.id));
                tabsContainer.addView(btn);
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void refreshShortcutsGrid() {
        if (selectedProfile == null) return;

        boolean hasCategories = selectedProfile.categories != null && !selectedProfile.categories.isEmpty();

        List<ShortcutProfileManager.Shortcut> toShow = new ArrayList<>();
        
        // Priority 1: Show category shortcuts if a category is selected
        if (currentCategoryId != null && hasCategories) {
            for (ShortcutProfileManager.ShortcutCategory cat : selectedProfile.categories) {
                if (cat.id.equals(currentCategoryId)) {
                    toShow = new ArrayList<>(cat.shortcuts);
                    break;
                }
            }
        } 
        // Priority 2: Show My Shortcuts favorites (top strip uses this list only)
        else if (TAB_MY.equals(currentTab)) {
            toShow = myShortcutsList;
            if (toShow.isEmpty()) {
                shortcutsGridView.setVisibility(View.GONE);
                emptyMyShortcuts.setVisibility(View.VISIBLE);
                return;
            }
        } 
        // Priority 3: Show all flat shortcuts (for profiles without categories)
        else if (!hasCategories) {
            toShow = selectedProfile.shortcuts != null ? selectedProfile.shortcuts : new ArrayList<>();
        }
        
        shortcutsGridView.setVisibility(View.VISIBLE);
        emptyMyShortcuts.setVisibility(View.GONE);
        sortShortcutsForDisplay(toShow);
        shortcutsAdapter.setShortcuts(toShow);
        shortcutsAdapter.notifyDataSetChanged();
    }

    private void sortShortcutsForDisplay(List<ShortcutProfileManager.Shortcut> shortcuts) {
        if (shortcuts == null || shortcuts.size() < 2) {
            return;
        }
        final java.util.HashMap<String, Integer> existing = new java.util.HashMap<>();
        for (int i = 0; i < shortcuts.size(); i++) {
            ShortcutProfileManager.Shortcut shortcut = shortcuts.get(i);
            existing.put(shortcut.id != null ? shortcut.id : ("idx_" + i), i);
        }
        Collections.sort(shortcuts, Comparator
                .comparingInt((ShortcutProfileManager.Shortcut s) -> s.displayOrder > 0 ? s.displayOrder : Integer.MAX_VALUE)
                .thenComparingInt(s -> existing.getOrDefault(s.id != null ? s.id : "", Integer.MAX_VALUE)));
    }

    private void addToMyFavorites(ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null) return;
        for (ShortcutProfileManager.Shortcut s : myShortcutsList) {
            if (s.id.equals(shortcut.id)) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Already in ⭐ My")
                        .setMessage("'" + shortcut.name + "' is already in your favorites.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Add to ⭐ My Shortcuts")
                .setMessage("Add '" + shortcut.name + "' (" + shortcut.label + ") to your favorites?")
                .setPositiveButton("Add", (d, w) -> {
                    myShortcutsList.add(shortcut);
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    Toast.makeText(getContext(), "Added to ⭐ My: " + shortcut.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveFromFavorites(ShortcutProfileManager.Shortcut shortcut) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Favorite")
                .setMessage("Remove '" + shortcut.name + "' from My Shortcuts?")
                .setPositiveButton("Remove", (d, w) -> {
                    myShortcutsList.removeIf(s -> s.id.equals(shortcut.id));
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
                    refreshShortcutsGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShortcutActionsMenu(ShortcutProfileManager.Shortcut shortcut) {
        final boolean isFavorite = myShortcutsList.stream()
                .anyMatch(s -> s.id.equals(shortcut.id));

        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        options.add("Edit");
        options.add("Delete");
        if (isFavorite) {
            options.add("Remove from My");
        } else {
            options.add("Add to My");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(shortcut.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showEditShortcutDialog(shortcut);
                            break;
                        case 1:
                            confirmDeleteShortcut(shortcut);
                            break;
                        case 2:
                            if (isFavorite) {
                                confirmRemoveFromFavorites(shortcut);
                            } else {
                                addToMyFavorites(shortcut);
                            }
                            break;
                    }
                })
                .show();
    }

    private void showEditShortcutDialog(ShortcutProfileManager.Shortcut shortcut) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_shortcut, null, false);

        EditText nameInput = dialogView.findViewById(R.id.shortcut_name_input);
        EditText dataInput = dialogView.findViewById(R.id.shortcut_data_input);
        EditText iconInput = dialogView.findViewById(R.id.shortcut_icon_input);
        EditText orderInput = dialogView.findViewById(R.id.shortcut_order_input);
        LinearLayout chipsRow = dialogView.findViewById(R.id.key_chips_row);
        TextView previewText = dialogView.findViewById(R.id.shortcut_preview);

        nameInput.setText(shortcut.name);
        String dataToken = KeyParser.toToken(shortcut.keyCode, shortcut.modifiers);
        dataInput.setText(dataToken);
        iconInput.setText(shortcut.icon != null ? shortcut.icon : "");
        if (shortcut.displayOrder > 0) {
            orderInput.setText(String.valueOf(shortcut.displayOrder));
        }

        String[][] tokens = {
            {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
            {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
            {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
            {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
            {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"}, {"⇥ Tab", "<TAB>"}, {"⌦ Del", "<DEL>"},
            {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
            {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
            {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };

        for (String[] entry : tokens) {
            Button chip = new Button(requireContext());
            chip.setText(entry[0]);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            int pad = dpToPx(8);
            chip.setPadding(pad, dpToPx(2), pad, dpToPx(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dpToPx(6), 0);
            chip.setLayoutParams(lp);
            final String token = entry[1];
            chip.setOnClickListener(v -> {
                int start = Math.max(dataInput.getSelectionStart(), 0);
                int end = Math.max(dataInput.getSelectionEnd(), 0);
                dataInput.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
            });
            chipsRow.addView(chip);
        }

        dataInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String d = s.toString().trim();
                if (d.isEmpty()) {
                    previewText.setText("Preview: ...");
                    return;
                }
                KeyParser.ParsedKey parsed = KeyParser.parse(d);
                if (parsed.keyCode >= 0) {
                    previewText.setText("Preview: " + KeyParser.toLabel(parsed.keyCode, parsed.modifiers));
                } else {
                    previewText.setText("Preview: no valid key detected");
                }
            }
        });
        previewText.setText("Preview: " + shortcut.label);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Shortcut")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String inputData = dataInput.getText().toString().trim();
                    if (name.isEmpty() || inputData.isEmpty()) {
                        Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    KeyParser.ParsedKey parsed = KeyParser.parse(inputData);
                    if (parsed.keyCode < 0) {
                        Toast.makeText(getContext(), "No valid key found in shortcut data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String label = KeyParser.toLabel(parsed.keyCode, parsed.modifiers);
                    shortcut.name = name;
                    shortcut.label = label;
                    shortcut.modifiers = parsed.modifiers;
                    shortcut.keyCode = parsed.keyCode;
                    shortcut.icon = iconInput.getText().toString().trim();
                    shortcut.displayOrder = parseDisplayOrder(orderInput.getText().toString().trim(), shortcut.displayOrder);

                    profileManager.updateProfile(selectedProfile);
                    loadProfiles();
                    refreshSelectedProfileAndGrid();
                    Toast.makeText(getContext(), "Saved: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteShortcut(ShortcutProfileManager.Shortcut shortcut) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Shortcut")
                .setMessage("Delete '" + shortcut.name + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean removed = false;

                    // Try removing from flat list
                    if (selectedProfile.shortcuts != null) {
                        removed = selectedProfile.shortcuts.removeIf(s -> s.id.equals(shortcut.id));
                    }

                    // Also try removing from categories
                    if (selectedProfile.categories != null) {
                        for (ShortcutProfileManager.ShortcutCategory cat : selectedProfile.categories) {
                            if (cat.shortcuts != null) {
                                removed = cat.shortcuts.removeIf(s -> s.id.equals(shortcut.id)) || removed;
                            }
                        }
                    }

                    // Also remove from favorites if present
                    myShortcutsList.removeIf(s -> s.id.equals(shortcut.id));
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);

                    if (removed) {
                        profileManager.updateProfile(selectedProfile);
                        loadProfiles();
                        refreshSelectedProfileAndGrid();
                    }
                    Toast.makeText(getContext(), "Deleted: " + shortcut.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshSelectedProfileAndGrid() {
        for (ShortcutProfile p : profilesList) {
            if (p.id.equals(selectedProfile.id)) {
                selectedProfile = p;
                break;
            }
        }
        if (selectedProfile.categories != null && !selectedProfile.categories.isEmpty()
                && currentCategoryId == null && TAB_MY.equals(currentTab)) {
            currentCategoryId = selectedProfile.categories.get(0).id;
        }
        rebuildCategoryTabs(selectedProfile);
        updateTabUI();
        refreshShortcutsGrid();
    }

    private void executeShortcut(ShortcutProfileManager.Shortcut shortcut) {
        if (connectionManager == null) {
            Toast.makeText(getContext(), "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionManager.sendKeyEvent(normalizeModifiersForTargetOs(shortcut.modifiers), shortcut.keyCode);
        // Small delay then release key
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (connectionManager != null) {
                connectionManager.sendKeyRelease();
            }
        }, 80);

        // Haptic feedback
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        Log.d(TAG, "Sent shortcut: " + shortcut.name + " (" + shortcut.label + ")"
                + " modifiers=" + shortcut.modifiers + " key=" + shortcut.keyCode);
    }

    private int normalizeModifiersForTargetOs(int modifiers) {
        String targetOs = getTargetOs();
        final int modCtrl = 0x01;
        final int modCmd = 0x08;
        if ("macos".equals(targetOs)) {
            boolean hasCtrl = (modifiers & modCtrl) != 0;
            boolean hasCmd = (modifiers & modCmd) != 0;
            if (hasCtrl && !hasCmd) {
                return (modifiers & ~modCtrl) | modCmd;
            }
        }
        return modifiers;
    }

    private String getTargetOs() {
        return requireContext()
                .getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
    }

    private void showCreateProfileDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Profile name (e.g., 'My App')");
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Create New Profile")
            .setMessage("Enter profile name:")
            .setView(input)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    ShortcutProfile profile = profileManager.createProfile(name, "Custom profile");
                    loadProfiles();
                    Toast.makeText(getContext(), "Created: " + profile.name, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showProfileOptionsDialog(ShortcutProfile profile) {
        String[] options;
        if ("default".equals(profile.id)) {
            options = new String[]{"View Shortcuts", "Add Shortcut", "Duplicate", "Export"};
        } else {
            options = new String[]{"View Shortcuts", "Add Shortcut", "Duplicate", "Export", "Delete"};
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(profile.name)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // View Shortcuts
                        viewShortcuts(profile);
                        break;
                    case 1: // Add Shortcut
                        showAddShortcutDialog(profile);
                        break;
                    case 2: // Duplicate
                        duplicateProfile(profile);
                        break;
                    case 3: // Export
                        exportProfile(profile);
                        break;
                    case 4: // Delete (only for non-default)
                        if (!"default".equals(profile.id)) {
                            deleteProfile(profile);
                        }
                        break;
                }
            })
            .show();
    }

    private void showAddShortcutDialog(ShortcutProfile profile) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_shortcut, null, false);

        EditText nameInput = dialogView.findViewById(R.id.shortcut_name_input);
        EditText dataInput = dialogView.findViewById(R.id.shortcut_data_input);
        EditText iconInput = dialogView.findViewById(R.id.shortcut_icon_input);
        EditText orderInput = dialogView.findViewById(R.id.shortcut_order_input);
        LinearLayout chipsRow = dialogView.findViewById(R.id.key_chips_row);
        TextView previewText = dialogView.findViewById(R.id.shortcut_preview);

        String[][] tokens = {
            {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
            {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
            {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
            {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
            {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"}, {"⇥ Tab", "<TAB>"}, {"⌦ Del", "<DEL>"},
            {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
            {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
            {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };

        for (String[] entry : tokens) {
            Button chip = new Button(requireContext());
            chip.setText(entry[0]);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            int pad = dpToPx(8);
            chip.setPadding(pad, dpToPx(2), pad, dpToPx(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dpToPx(6), 0);
            chip.setLayoutParams(lp);
            final String token = entry[1];
            chip.setOnClickListener(v -> {
                int start = Math.max(dataInput.getSelectionStart(), 0);
                int end = Math.max(dataInput.getSelectionEnd(), 0);
                dataInput.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
            });
            chipsRow.addView(chip);
        }

        // Live preview: parse data input and show computed label
        dataInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String data = s.toString().trim();
                if (data.isEmpty()) {
                    previewText.setText("Preview: ...");
                    return;
                }
                KeyParser.ParsedKey parsed = KeyParser.parse(data);
                if (parsed.keyCode >= 0) {
                    String label = KeyParser.toLabel(parsed.keyCode, parsed.modifiers);
                    previewText.setText("Preview: " + label);
                } else {
                    previewText.setText("Preview: no valid key detected");
                }
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Shortcut to " + profile.name)
                .setView(dialogView)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String data = dataInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (data.isEmpty()) {
                        Toast.makeText(getContext(), "Shortcut data is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    KeyParser.ParsedKey parsed = KeyParser.parse(data);
                    if (parsed.keyCode < 0) {
                        Toast.makeText(getContext(), "No valid key found in shortcut data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String label = KeyParser.toLabel(parsed.keyCode, parsed.modifiers);
                    ShortcutProfileManager.Shortcut shortcut = new ShortcutProfileManager.Shortcut(
                            "user-" + System.currentTimeMillis(), name, label, parsed.modifiers, parsed.keyCode);
                    shortcut.icon = iconInput.getText().toString().trim();
                    shortcut.displayOrder = parseDisplayOrder(
                            orderInput.getText().toString().trim(),
                            nextDisplayOrder(profile));

                    profile.shortcuts.add(shortcut);
                    profileManager.updateProfile(profile);
                    loadProfiles();

                    // Rebind selectedProfile to the refreshed copy
                    for (ShortcutProfile p : profilesList) {
                        if (p.id.equals(profile.id)) {
                            selectedProfile = p;
                            break;
                        }
                    }

                    // If profile has categories, switch to the last category to show the new shortcut
                    if (selectedProfile.categories != null && !selectedProfile.categories.isEmpty()) {
                        String lastCatId = selectedProfile.categories.get(
                                selectedProfile.categories.size() - 1).id;
                        currentTab = null;
                        currentCategoryId = lastCatId;
                        rebuildCategoryTabs(selectedProfile);
                    }
                    updateTabUI();
                    refreshShortcutsGrid();

                    Toast.makeText(getContext(), "Added shortcut: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int nextDisplayOrder(ShortcutProfile profile) {
        int max = 0;
        List<ShortcutProfileManager.Shortcut> all = profile.getAllShortcutsFlat();
        for (ShortcutProfileManager.Shortcut shortcut : all) {
            if (shortcut != null && shortcut.displayOrder > max) {
                max = shortcut.displayOrder;
            }
        }
        return max + 1;
    }

    private int parseDisplayOrder(String raw, int fallback) {
        if (TextUtils.isEmpty(raw)) {
            return fallback > 0 ? fallback : 0;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(value, 0);
        } catch (NumberFormatException ignored) {
            return fallback > 0 ? fallback : 0;
        }
    }

    private void viewShortcuts(ShortcutProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(profile.name).append("\n\n");
        for (ShortcutProfileManager.Shortcut shortcut : profile.shortcuts) {
            sb.append(shortcut.name).append(": ").append(shortcut.label).append("\n");
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Shortcuts")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void duplicateProfile(ShortcutProfile profile) {
        ShortcutProfile duplicate = profileManager.duplicateProfile(profile.id);
        if (duplicate != null) {
            loadProfiles();
            Toast.makeText(getContext(), "Duplicated: " + duplicate.name, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportProfile(ShortcutProfile profile) {
        exportProfileToFile(profile);
    }

    private void exportProfileToFile(ShortcutProfile profile) {
        String json = profileManager.exportProfile(profile.id);
        if (json == null) {
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to Downloads folder
        try {
            String filename = "keymod_profile_" + profile.name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase() + ".json";
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File outputFile = new java.io.File(downloadsDir, filename);
            
            java.io.FileWriter writer = new java.io.FileWriter(outputFile);
            writer.write(json);
            writer.close();
            
            Toast.makeText(getContext(), "Saved to: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "Exported profile to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage());
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Import Profile");
        builder.setMessage("Select import method:");
        
        // Option 1: Paste JSON
        builder.setPositiveButton("📋 Paste JSON", (dialog, which) -> {
            showPasteJsonDialog();
        });
        
        // Option 2: Browse files
        builder.setNegativeButton("📁 Browse Files", (dialog, which) -> {
            openFilePicker();
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void showPasteJsonDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Paste profile JSON here");
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Import from JSON")
            .setView(input)
            .setPositiveButton("Import", (dialog, which) -> {
                String json = input.getText().toString().trim();
                if (!json.isEmpty()) {
                    importProfileFromJson(json);
                } else {
                    Toast.makeText(getContext(), "Please paste JSON", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openFilePicker() {
        // Request storage permission first
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), 
                android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            // Permission already granted, open file picker
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 200);
        }
    }

    private void importProfileFromJson(String json) {
        ShortcutProfileManager.ShortcutProfile profile = profileManager.importProfile(json);
        if (profile != null) {
            loadProfiles();
            Toast.makeText(getContext(), "Imported: " + profile.name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Import failed - Invalid JSON", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                importProfileFromUri(uri);
            }
        }
    }

    private void importProfileFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.util.Scanner scanner = new java.util.Scanner(inputStream);
                scanner.useDelimiter("\\A");
                String json = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                inputStream.close();
                
                importProfileFromJson(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Import from URI failed: " + e.getMessage());
            Toast.makeText(getContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteProfile(ShortcutProfile profile) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Delete '" + profile.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                profileManager.deleteProfile(profile.id);
                loadProfiles();
                Toast.makeText(getContext(), "Deleted: " + profile.name, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateActiveProfileDisplay() {
        if (activeProfile != null) {
            activeProfileText.setText(activeProfile.name);
        } else {
            activeProfileText.setText("");
        }
    }

    private void updateEmptyState() {
        if (profilesList.isEmpty()) {
            profilesGridView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            profilesGridView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
        }
    }

    // ProfileChangeListener callbacks
    @Override
    public void onProfileChanged(String profileId) {
        loadProfiles();
    }

    @Override
    public void onProfileCreated(ShortcutProfile profile) {
        loadProfiles();
    }

    @Override
    public void onProfileUpdated(ShortcutProfile profile) {
        loadProfiles();
    }

    @Override
    public void onProfileDeleted(String profileId) {
        loadProfiles();
    }

    @Override
    public void onProfileImported(ShortcutProfile profile) {
        loadProfiles();
    }

    /**
     * Profiles Grid Adapter
     */
    private static class ProfilesAdapter extends android.widget.BaseAdapter {
        private final Context context;
        private final List<ShortcutProfile> profiles;
        private String activeProfileId;

        public ProfilesAdapter(Context context, List<ShortcutProfile> profiles) {
            this.context = context;
            this.profiles = profiles;
        }

        public void setActiveProfileId(String activeProfileId) {
            this.activeProfileId = activeProfileId;
        }

        @Override
        public int getCount() {
            return profiles.size();
        }

        @Override
        public Object getItem(int position) {
            return profiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return profiles.get(position).id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                    .inflate(R.layout.grid_item_profile, parent, false);
            }

            ShortcutProfile profile = profiles.get(position);
            TextView nameText = convertView.findViewById(R.id.profile_name);
            TextView descText = convertView.findViewById(R.id.profile_description);
            TextView countText = convertView.findViewById(R.id.profile_count);
            boolean isActive = profile.id != null && profile.id.equals(activeProfileId);

            nameText.setText(isActive ? profile.name + " (Active)" : profile.name);
            descText.setText(profile.description);
            if (isActive) {
                countText.setText("ACTIVE - " + profile.getShortcutCount() + " shortcuts");
                countText.setBackgroundColor(ContextCompat.getColor(context, R.color.primary));
            } else {
                countText.setText(profile.getShortcutCount() + " shortcuts");
                countText.setBackgroundColor(ContextCompat.getColor(context, R.color.primary));
            }

            return convertView;
        }
    }

    /**
     * Shortcuts Grid Adapter
     */
    private static class ShortcutsAdapter extends android.widget.BaseAdapter {
        private final Context context;
        private List<ShortcutProfileManager.Shortcut> shortcuts;

        public ShortcutsAdapter(Context context, List<ShortcutProfileManager.Shortcut> shortcuts) {
            this.context = context;
            this.shortcuts = shortcuts;
        }

        private String getTargetOs() {
            return context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .getString("target_os", "macos");
        }

        public void setShortcuts(List<ShortcutProfileManager.Shortcut> shortcuts) {
            this.shortcuts = shortcuts;
        }

        @Override
        public int getCount() {
            return shortcuts.size();
        }

        @Override
        public Object getItem(int position) {
            return shortcuts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return shortcuts.get(position).id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.grid_item_shortcut, parent, false);
            }

            ShortcutProfileManager.Shortcut shortcut = shortcuts.get(position);
            TextView iconText = convertView.findViewById(R.id.shortcut_icon);
            TextView nameText = convertView.findViewById(R.id.shortcut_name);
            TextView labelText = convertView.findViewById(R.id.shortcut_label);

            String icon = shortcut.icon != null ? shortcut.icon.trim() : "";
            if (!icon.isEmpty() && !icon.matches(".*[A-Za-z0-9_].*")) {
                iconText.setText(icon);
                iconText.setVisibility(View.VISIBLE);
            } else if (!icon.isEmpty() && icon.length() <= 2) {
                iconText.setText(icon);
                iconText.setVisibility(View.VISIBLE);
            } else {
                iconText.setVisibility(View.GONE);
            }
            nameText.setText(shortcut.name);
            labelText.setText(KeyParser.displayLabel(shortcut.label, getTargetOs()));

            return convertView;
        }
    }
}
