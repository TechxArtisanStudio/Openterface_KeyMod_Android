package com.openterface.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;
import com.openterface.keymod.ShortcutProfileManager.ProfileChangeListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Shortcut Hub Fragment - Profile management for app-specific shortcuts
 * Phase 3: Shortcut Hub
 */
public class ShortcutHubFragment extends Fragment implements ProfileChangeListener {

    private static final String TAG = "ShortcutHubFragment";

    private ShortcutProfileManager profileManager;
    private Vibrator vibrator;

    // UI Components
    private GridView profilesGridView;
    private TextView emptyTextView;
    private TextView activeProfileText;
    private Button createProfileButton;
    private Button importButton;
    private Button exportButton;

    private ProfilesAdapter adapter;
    private List<ShortcutProfile> profilesList;
    private ShortcutProfile activeProfile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shortcut_hub, container, false);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        profileManager = new ShortcutProfileManager(requireContext());
        profileManager.setListener(this);

        initializeViews(view);
        loadProfiles();
        setupListeners();

        return view;
    }

    private void initializeViews(View view) {
        profilesGridView = view.findViewById(R.id.profiles_gridview);
        emptyTextView = view.findViewById(R.id.empty_textview);
        activeProfileText = view.findViewById(R.id.active_profile_text);
        createProfileButton = view.findViewById(R.id.create_profile_button);
        importButton = view.findViewById(R.id.import_button);
        exportButton = view.findViewById(R.id.export_button);

        profilesList = new ArrayList<>();
        adapter = new ProfilesAdapter(requireContext(), profilesList);
        profilesGridView.setAdapter(adapter);
    }

    private void loadProfiles() {
        profilesList.clear();
        profilesList.addAll(profileManager.getAllProfiles());
        adapter.notifyDataSetChanged();
        
        activeProfile = profileManager.getActiveProfile();
        updateActiveProfileDisplay();
        updateEmptyState();
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

        // Grid item click - activate profile
        profilesGridView.setOnItemClickListener((parent, view, position, id) -> {
            ShortcutProfile profile = profilesList.get(position);
            activateProfile(profile);
        });

        // Grid item long click - show options
        profilesGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            ShortcutProfile profile = profilesList.get(position);
            showProfileOptionsDialog(profile);
            return true;
        });
    }

    private void activateProfile(ShortcutProfile profile) {
        profileManager.setActiveProfile(profile.id);
        activeProfile = profile;
        updateActiveProfileDisplay();
        
        // Haptic feedback
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        
        Toast.makeText(getContext(), "Activated: " + profile.name, Toast.LENGTH_SHORT).show();
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
            options = new String[]{"View Shortcuts", "Duplicate", "Export"};
        } else {
            options = new String[]{"View Shortcuts", "Duplicate", "Export", "Delete"};
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle(profile.name)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // View Shortcuts
                        viewShortcuts(profile);
                        break;
                    case 1: // Duplicate
                        duplicateProfile(profile);
                        break;
                    case 2: // Export
                        exportProfile(profile);
                        break;
                    case 3: // Delete (only for non-default)
                        if (!"default".equals(profile.id)) {
                            deleteProfile(profile);
                        }
                        break;
                }
            })
            .show();
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
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
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
        
        new android.app.AlertDialog.Builder(requireContext())
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
            activeProfileText.setText("Active: " + activeProfile.name + " (" + 
                                     activeProfile.getShortcutCount() + " shortcuts)");
        } else {
            activeProfileText.setText("No active profile");
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

        public ProfilesAdapter(Context context, List<ShortcutProfile> profiles) {
            this.context = context;
            this.profiles = profiles;
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

            nameText.setText(profile.name);
            descText.setText(profile.description);
            countText.setText(profile.getShortcutCount() + " shortcuts");

            return convertView;
        }
    }
}
