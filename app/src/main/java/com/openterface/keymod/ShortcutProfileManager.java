package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shortcut Profile Manager - Manages app-specific shortcut profiles
 * Phase 3: Shortcut Hub
 */
public class ShortcutProfileManager {

    private static final String TAG = "ShortcutProfileManager";
    private static final String PREFS_NAME = "ShortcutProfiles";
    private static final String KEY_PROFILES = "profiles_list";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_id";

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<ShortcutProfile> profiles;
    private String activeProfileId;
    private ProfileChangeListener listener;

    public ShortcutProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        profiles = loadProfiles();
        activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE, "default");
        
        // Create default profile if none exist
        if (profiles.isEmpty()) {
            createDefaultProfiles();
        }
    }

    /**
     * Load profiles from SharedPreferences
     */
    private List<ShortcutProfile> loadProfiles() {
        String json = prefs.getString(KEY_PROFILES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<ShortcutProfile>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Save profiles to SharedPreferences
     */
    private void saveProfiles() {
        String json = gson.toJson(profiles);
        prefs.edit().putString(KEY_PROFILES, json).apply();
        Log.d(TAG, "Saved " + profiles.size() + " profiles");
    }

    /**
     * Create default profiles for common apps
     */
    private void createDefaultProfiles() {
        // Generic Default Profile
        ShortcutProfile defaultProfile = new ShortcutProfile();
        defaultProfile.id = "default";
        defaultProfile.name = "Default";
        defaultProfile.description = "Default shortcut mappings";
        defaultProfile.icon = "ic_default";
        defaultProfile.shortcuts = createDefaultShortcuts();
        defaultProfile.createdAt = System.currentTimeMillis();
        profiles.add(defaultProfile);

        // Blender Profile
        ShortcutProfile blenderProfile = new ShortcutProfile();
        blenderProfile.id = "blender";
        blenderProfile.name = "Blender 3D";
        blenderProfile.description = "Shortcuts for Blender 3D modeling";
        blenderProfile.icon = "ic_blender";
        blenderProfile.shortcuts = createBlenderShortcuts();
        blenderProfile.createdAt = System.currentTimeMillis();
        profiles.add(blenderProfile);

        // KiCAD Profile
        ShortcutProfile kicadProfile = new ShortcutProfile();
        kicadProfile.id = "kicad";
        kicadProfile.name = "KiCAD";
        kicadProfile.description = "Shortcuts for KiCAD PCB design";
        kicadProfile.icon = "ic_kicad";
        kicadProfile.shortcuts = createKiCADShortcuts();
        kicadProfile.createdAt = System.currentTimeMillis();
        profiles.add(kicadProfile);

        // Photoshop Profile
        ShortcutProfile photoshopProfile = new ShortcutProfile();
        photoshopProfile.id = "photoshop";
        photoshopProfile.name = "Photoshop";
        photoshopProfile.description = "Shortcuts for Adobe Photoshop";
        photoshopProfile.icon = "ic_photoshop";
        photoshopProfile.shortcuts = createPhotoshopShortcuts();
        photoshopProfile.createdAt = System.currentTimeMillis();
        profiles.add(photoshopProfile);

        // VS Code Profile
        ShortcutProfile vscodeProfile = new ShortcutProfile();
        vscodeProfile.id = "vscode";
        vscodeProfile.name = "VS Code";
        vscodeProfile.description = "Shortcuts for Visual Studio Code";
        vscodeProfile.icon = "ic_vscode";
        vscodeProfile.shortcuts = createVSCodeShortcuts();
        vscodeProfile.createdAt = System.currentTimeMillis();
        profiles.add(vscodeProfile);

        saveProfiles();
        Log.d(TAG, "Created " + profiles.size() + " default profiles");
    }

    /**
     * Create default shortcuts
     */
    private List<Shortcut> createDefaultShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new Shortcut("S1", "Copy", "Ctrl+C", 17, 67)); // Ctrl+C
        shortcuts.add(new Shortcut("S2", "Paste", "Ctrl+V", 17, 86)); // Ctrl+V
        shortcuts.add(new Shortcut("S3", "Cut", "Ctrl+X", 17, 88)); // Ctrl+X
        shortcuts.add(new Shortcut("S4", "Undo", "Ctrl+Z", 17, 90)); // Ctrl+Z
        shortcuts.add(new Shortcut("S5", "Redo", "Ctrl+Y", 17, 89)); // Ctrl+Y
        shortcuts.add(new Shortcut("S6", "Save", "Ctrl+S", 17, 83)); // Ctrl+S
        shortcuts.add(new Shortcut("S7", "Select All", "Ctrl+A", 17, 65)); // Ctrl+A
        shortcuts.add(new Shortcut("S8", "Find", "Ctrl+F", 17, 70)); // Ctrl+F
        return shortcuts;
    }

    /**
     * Create Blender shortcuts
     */
    private List<Shortcut> createBlenderShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new Shortcut("B1", "Grab/Move", "G", 0, 71)); // G
        shortcuts.add(new Shortcut("B2", "Rotate", "R", 0, 82)); // R
        shortcuts.add(new Shortcut("B3", "Scale", "S", 0, 83)); // S
        shortcuts.add(new Shortcut("B4", "Extrude", "E", 0, 69)); // E
        shortcuts.add(new Shortcut("B5", "Loop Cut", "Ctrl+R", 17, 82)); // Ctrl+R
        shortcuts.add(new Shortcut("B6", "Inset", "I", 0, 73)); // I
        shortcuts.add(new Shortcut("B7", "Bevel", "Ctrl+B", 17, 66)); // Ctrl+B
        shortcuts.add(new Shortcut("B8", "Knife", "K", 0, 75)); // K
        shortcuts.add(new Shortcut("B9", "Delete", "X", 0, 88)); // X
        shortcuts.add(new Shortcut("B10", "Duplicate", "Shift+D", 2, 68)); // Shift+D
        shortcuts.add(new Shortcut("B11", "Array Modifier", "Shift+A", 2, 65)); // Shift+A
        shortcuts.add(new Shortcut("B12", "Edit Mode", "TAB", 0, 61)); // Tab
        return shortcuts;
    }

    /**
     * Create KiCAD shortcuts
     */
    private List<Shortcut> createKiCADShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new Shortcut("K1", "Place Wire", "W", 0, 87)); // W
        shortcuts.add(new Shortcut("K2", "Place Component", "A", 0, 65)); // A
        shortcuts.add(new Shortcut("K3", "Rotate", "R", 0, 82)); // R
        shortcuts.add(new Shortcut("K4", "Mirror X", "X", 0, 88)); // X
        shortcuts.add(new Shortcut("K5", "Mirror Y", "Y", 0, 89)); // Y
        shortcuts.add(new Shortcut("K6", "Delete", "Delete", 0, 67)); // Delete
        shortcuts.add(new Shortcut("K7", "Drag", "G", 0, 71)); // G
        shortcuts.add(new Shortcut("K8", "Zoom Fit", "F", 0, 70)); // F
        shortcuts.add(new Shortcut("K9", "Zoom In", "+", 0, 76)); // +
        shortcuts.add(new Shortcut("K10", "Zoom Out", "-", 0, 77)); // -
        shortcuts.add(new Shortcut("K11", "Save", "Ctrl+S", 17, 83)); // Ctrl+S
        shortcuts.add(new Shortcut("K12", "Undo", "Ctrl+Z", 17, 90)); // Ctrl+Z
        return shortcuts;
    }

    /**
     * Create Photoshop shortcuts
     */
    private List<Shortcut> createPhotoshopShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new Shortcut("P1", "Move", "V", 0, 86)); // V
        shortcuts.add(new Shortcut("P2", "Brush", "B", 0, 66)); // B
        shortcuts.add(new Shortcut("P3", "Eraser", "E", 0, 69)); // E
        shortcuts.add(new Shortcut("P4", "Clone Stamp", "S", 0, 83)); // S
        shortcuts.add(new Shortcut("P5", "Healing Brush", "J", 0, 74)); // J
        shortcuts.add(new Shortcut("P6", "Text", "T", 0, 84)); // T
        shortcuts.add(new Shortcut("P7", "Pen", "P", 0, 80)); // P
        shortcuts.add(new Shortcut("P8", "Rectangle", "U", 0, 85)); // U
        shortcuts.add(new Shortcut("P9", "Hand", "H", 0, 72)); // H
        shortcuts.add(new Shortcut("P10", "Zoom", "Z", 0, 90)); // Z
        shortcuts.add(new Shortcut("P11", "Undo", "Ctrl+Z", 17, 90)); // Ctrl+Z
        shortcuts.add(new Shortcut("P12", "Free Transform", "Ctrl+T", 17, 84)); // Ctrl+T
        return shortcuts;
    }

    /**
     * Create VS Code shortcuts
     */
    private List<Shortcut> createVSCodeShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new Shortcut("V1", "Quick Open", "Ctrl+P", 17, 80)); // Ctrl+P
        shortcuts.add(new Shortcut("V2", "Command Palette", "Ctrl+Shift+P", 3, 80)); // Ctrl+Shift+P
        shortcuts.add(new Shortcut("V3", "Find", "Ctrl+F", 17, 70)); // Ctrl+F
        shortcuts.add(new Shortcut("V4", "Replace", "Ctrl+H", 17, 72)); // Ctrl+H
        shortcuts.add(new Shortcut("V5", "Go to Line", "Ctrl+G", 17, 71)); // Ctrl+G
        shortcuts.add(new Shortcut("V6", "Comment", "Ctrl+/", 17, 111)); // Ctrl+/
        shortcuts.add(new Shortcut("V7", "Copy Line Up", "Alt+Shift+↑", 6, 19)); // Alt+Shift+Up
        shortcuts.add(new Shortcut("V8", "Copy Line Down", "Alt+Shift+↓", 6, 20)); // Alt+Shift+Down
        shortcuts.add(new Shortcut("V9", "Move Line Up", "Alt+↑", 4, 19)); // Alt+Up
        shortcuts.add(new Shortcut("V10", "Move Line Down", "Alt+↓", 4, 20)); // Alt+Down
        shortcuts.add(new Shortcut("V11", "Save", "Ctrl+S", 17, 83)); // Ctrl+S
        shortcuts.add(new Shortcut("V12", "Format", "Shift+Alt+F", 6, 70)); // Shift+Alt+F
        return shortcuts;
    }

    /**
     * Get all profiles
     */
    public List<ShortcutProfile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    /**
     * Get profile by ID
     */
    public ShortcutProfile getProfileById(String id) {
        for (ShortcutProfile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Get active profile
     */
    public ShortcutProfile getActiveProfile() {
        return getProfileById(activeProfileId);
    }

    /**
     * Set active profile
     */
    public void setActiveProfile(String profileId) {
        if (getProfileById(profileId) != null) {
            activeProfileId = profileId;
            prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply();
            Log.d(TAG, "Active profile set to: " + profileId);
            
            if (listener != null) {
                listener.onProfileChanged(profileId);
            }
        }
    }

    /**
     * Create new profile
     */
    public ShortcutProfile createProfile(String name, String description) {
        ShortcutProfile profile = new ShortcutProfile();
        profile.id = "custom_" + System.currentTimeMillis();
        profile.name = name;
        profile.description = description;
        profile.icon = "ic_custom";
        profile.shortcuts = new ArrayList<>();
        profile.createdAt = System.currentTimeMillis();
        
        profiles.add(profile);
        saveProfiles();
        
        if (listener != null) {
            listener.onProfileCreated(profile);
        }
        
        return profile;
    }

    /**
     * Update profile
     */
    public void updateProfile(ShortcutProfile profile) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                saveProfiles();
                
                if (listener != null) {
                    listener.onProfileUpdated(profile);
                }
                return;
            }
        }
    }

    /**
     * Delete profile
     */
    public void deleteProfile(String profileId) {
        // Don't allow deleting default profile
        if ("default".equals(profileId)) {
            Log.w(TAG, "Cannot delete default profile");
            return;
        }
        
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profileId)) {
                profiles.remove(i);
                saveProfiles();
                
                // Switch to default if active profile was deleted
                if (activeProfileId.equals(profileId)) {
                    setActiveProfile("default");
                }
                
                if (listener != null) {
                    listener.onProfileDeleted(profileId);
                }
                return;
            }
        }
    }

    /**
     * Export profile to JSON
     */
    public String exportProfile(String profileId) {
        ShortcutProfile profile = getProfileById(profileId);
        if (profile != null) {
            return gson.toJson(profile);
        }
        return null;
    }

    /**
     * Import profile from JSON
     */
    public ShortcutProfile importProfile(String json) {
        try {
            Type type = new TypeToken<ShortcutProfile>(){}.getType();
            ShortcutProfile profile = gson.fromJson(json, type);
            
            // Generate new ID to avoid conflicts
            profile.id = "imported_" + System.currentTimeMillis();
            profile.createdAt = System.currentTimeMillis();
            
            profiles.add(profile);
            saveProfiles();
            
            if (listener != null) {
                listener.onProfileImported(profile);
            }
            
            return profile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to import profile: " + e.getMessage());
            return null;
        }
    }

    /**
     * Duplicate profile
     */
    public ShortcutProfile duplicateProfile(String profileId) {
        ShortcutProfile original = getProfileById(profileId);
        if (original != null) {
            ShortcutProfile duplicate = new ShortcutProfile();
            duplicate.id = "copy_" + System.currentTimeMillis();
            duplicate.name = original.name + " (Copy)";
            duplicate.description = original.description;
            duplicate.icon = original.icon;
            duplicate.shortcuts = new ArrayList<>(original.shortcuts);
            duplicate.createdAt = System.currentTimeMillis();
            
            profiles.add(duplicate);
            saveProfiles();
            
            if (listener != null) {
                listener.onProfileCreated(duplicate);
            }
            
            return duplicate;
        }
        return null;
    }

    /**
     * Set listener for profile changes
     */
    public void setListener(ProfileChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Profile change listener interface
     */
    public interface ProfileChangeListener {
        void onProfileChanged(String profileId);
        void onProfileCreated(ShortcutProfile profile);
        void onProfileUpdated(ShortcutProfile profile);
        void onProfileDeleted(String profileId);
        void onProfileImported(ShortcutProfile profile);
    }

    /**
     * Shortcut data class
     */
    public static class Shortcut {
        public String id;
        public String name;
        public String label;
        public int modifiers;
        public int keyCode;

        public Shortcut() {}

        public Shortcut(String id, String name, String label, int modifiers, int keyCode) {
            this.id = id;
            this.name = name;
            this.label = label;
            this.modifiers = modifiers;
            this.keyCode = keyCode;
        }
    }

    /**
     * Profile data class
     */
    public static class ShortcutProfile {
        public String id;
        public String name;
        public String description;
        public String icon;
        public List<Shortcut> shortcuts;
        public long createdAt;

        public ShortcutProfile() {
            shortcuts = new ArrayList<>();
        }

        public int getShortcutCount() {
            return shortcuts != null ? shortcuts.size() : 0;
        }
    }
}
