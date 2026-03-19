package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Macros Manager - Handles recording, playback, and management of key sequences
 * Matches iOS MacrosManager.swift functionality
 */
public class MacrosManager {

    private static final String TAG = "MacrosManager";
    private static final String PREFS_NAME = "MacrosPrefs";
    private static final String KEY_MACROS_LIST = "macros_list";
    private static final String KEY_RECORDING = "is_recording";
    private static final String KEY_PLAYING = "is_playing";

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<Macro> macros;
    private Macro currentRecordingMacro;
    private boolean isRecording;
    private boolean isPlaying;
    private MacrosListener listener;

    public MacrosManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        macros = loadMacros();
        isRecording = false;
        isPlaying = false;
    }

    /**
     * Load macros from SharedPreferences
     */
    private List<Macro> loadMacros() {
        String json = prefs.getString(KEY_MACROS_LIST, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Macro>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Save macros to SharedPreferences
     */
    private void saveMacros() {
        String json = gson.toJson(macros);
        prefs.edit().putString(KEY_MACROS_LIST, json).apply();
        Log.d(TAG, "Saved " + macros.size() + " macros");
    }

    /**
     * Start recording a new macro
     */
    public void startRecording(String name) {
        if (isRecording) {
            Log.w(TAG, "Already recording a macro");
            return;
        }

        currentRecordingMacro = new Macro();
        currentRecordingMacro.name = name;
        currentRecordingMacro.id = System.currentTimeMillis();
        currentRecordingMacro.createdAt = System.currentTimeMillis();
        currentRecordingMacro.keyEvents = new ArrayList<>();
        isRecording = true;
        
        prefs.edit().putBoolean(KEY_RECORDING, true).apply();
        Log.d(TAG, "Started recording macro: " + name);
        
        if (listener != null) {
            listener.onRecordingStarted(currentRecordingMacro);
        }
    }

    /**
     * Stop recording and save the macro
     */
    public void stopRecording() {
        if (!isRecording || currentRecordingMacro == null) {
            return;
        }

        isRecording = false;
        prefs.edit().putBoolean(KEY_RECORDING, false).apply();
        
        if (currentRecordingMacro.keyEvents != null && !currentRecordingMacro.keyEvents.isEmpty()) {
            // Calculate total duration
            long lastTime = currentRecordingMacro.keyEvents.get(currentRecordingMacro.keyEvents.size() - 1).timestamp;
            currentRecordingMacro.duration = lastTime;
            
            macros.add(currentRecordingMacro);
            saveMacros();
            Log.d(TAG, "Saved macro: " + currentRecordingMacro.name + " with " + 
                  currentRecordingMacro.keyEvents.size() + " events");
            
            if (listener != null) {
                listener.onMacroSaved(currentRecordingMacro);
            }
        } else {
            Log.w(TAG, "Discarded empty macro");
        }
        
        currentRecordingMacro = null;
    }

    /**
     * Record a key event during macro recording
     */
    public void recordKeyEvent(int keyCode, int modifiers) {
        if (!isRecording || currentRecordingMacro == null) {
            return;
        }

        long timestamp = System.currentTimeMillis() - currentRecordingMacro.createdAt;
        KeyEvent event = new KeyEvent(keyCode, modifiers, timestamp);
        currentRecordingMacro.keyEvents.add(event);
        
        Log.d(TAG, "Recorded key event: keyCode=" + keyCode + ", timestamp=" + timestamp);
        
        if (listener != null) {
            listener.onKeyEventRecorded(event);
        }
    }

    /**
     * Play back a macro
     */
    public void playMacro(Macro macro, ConnectionManager connectionManager) {
        if (isPlaying) {
            Log.w(TAG, "Already playing a macro");
            return;
        }

        isPlaying = true;
        prefs.edit().putBoolean(KEY_PLAYING, true).apply();
        Log.d(TAG, "Playing macro: " + macro.name);
        
        if (listener != null) {
            listener.onPlaybackStarted(macro);
        }

        // Play back key events with timing
        playMacroEvents(macro, connectionManager, 0);
    }

    private void playMacroEvents(final Macro macro, final ConnectionManager connectionManager, 
                                  final int index) {
        if (!isPlaying || index >= macro.keyEvents.size()) {
            // Playback complete
            isPlaying = false;
            prefs.edit().putBoolean(KEY_PLAYING, false).apply();
            Log.d(TAG, "Macro playback complete: " + macro.name);
            
            if (listener != null) {
                listener.onPlaybackComplete(macro);
            }
            return;
        }

        final KeyEvent event = macro.keyEvents.get(index);
        
        // Schedule next event based on timing
        long delay;
        if (index == 0) {
            delay = event.timestamp;
        } else {
            long prevTimestamp = macro.keyEvents.get(index - 1).timestamp;
            delay = event.timestamp - prevTimestamp;
        }

        // Ensure minimum delay
        delay = Math.max(delay, 50);

        final long finalDelay = delay;
        if (connectionManager != null) {
            connectionManager.sendKeyEvent(event.modifiers, event.keyCode);
            
            // Release after short delay
            final long releaseDelay = finalDelay;
            new android.os.Handler().postDelayed(() -> {
                if (isPlaying) {
                    connectionManager.sendKeyRelease();
                    // Schedule next event
                    new android.os.Handler().postDelayed(() -> {
                        playMacroEvents(macro, connectionManager, index + 1);
                    }, releaseDelay);
                }
            }, 50);
        }
    }

    /**
     * Stop macro playback
     */
    public void stopPlayback() {
        isPlaying = false;
        prefs.edit().putBoolean(KEY_PLAYING, false).apply();
        Log.d(TAG, "Stopped macro playback");
        
        if (listener != null) {
            listener.onPlaybackStopped();
        }
    }

    /**
     * Delete a macro
     */
    public void deleteMacro(Macro macro) {
        macros.remove(macro);
        saveMacros();
        Log.d(TAG, "Deleted macro: " + macro.name);
        
        if (listener != null) {
            listener.onMacroDeleted(macro);
        }
    }

    /**
     * Get all macros
     */
    public List<Macro> getAllMacros() {
        return new ArrayList<>(macros);
    }

    /**
     * Get macro by ID
     */
    public Macro getMacroById(long id) {
        for (Macro macro : macros) {
            if (macro.id == id) {
                return macro;
            }
        }
        return null;
    }

    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Get current recording macro
     */
    public Macro getCurrentRecordingMacro() {
        return currentRecordingMacro;
    }

    /**
     * Set listener for macro events
     */
    public void setListener(MacrosListener listener) {
        this.listener = listener;
    }

    /**
     * Export macros to JSON string
     */
    public String exportMacros() {
        return gson.toJson(macros);
    }

    /**
     * Import macros from JSON string
     */
    public void importMacros(String json) {
        Type type = new TypeToken<List<Macro>>(){}.getType();
        List<Macro> importedMacros = gson.fromJson(json, type);
        
        if (importedMacros != null) {
            macros.addAll(importedMacros);
            saveMacros();
            Log.d(TAG, "Imported " + importedMacros.size() + " macros");
            
            if (listener != null) {
                listener.onMacrosImported(importedMacros);
            }
        }
    }

    /**
     * Clear all macros
     */
    public void clearAllMacros() {
        macros.clear();
        saveMacros();
        Log.d(TAG, "Cleared all macros");
        
        if (listener != null) {
            listener.onMacrosCleared();
        }
    }

    // Listener interface
    public interface MacrosListener {
        void onRecordingStarted(Macro macro);
        void onKeyEventRecorded(KeyEvent event);
        void onMacroSaved(Macro macro);
        void onPlaybackStarted(Macro macro);
        void onPlaybackComplete(Macro macro);
        void onPlaybackStopped();
        void onMacroDeleted(Macro macro);
        void onMacrosImported(List<Macro> macros);
        void onMacrosCleared();
    }

    // Data classes
    public static class Macro {
        public long id;
        public String name;
        public long createdAt;
        public long duration;
        public List<KeyEvent> keyEvents;

        public Macro() {}

        public int getKeyCount() {
            return keyEvents != null ? keyEvents.size() : 0;
        }

        public String getFormattedDuration() {
            long seconds = duration / 1000;
            long millis = duration % 1000;
            return String.format("%d.%03ds", seconds, millis);
        }
    }

    public static class KeyEvent {
        public int keyCode;
        public int modifiers;
        public long timestamp;

        public KeyEvent() {}

        public KeyEvent(int keyCode, int modifiers, long timestamp) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.timestamp = timestamp;
        }
    }
}
