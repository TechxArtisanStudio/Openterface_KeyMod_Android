package com.openterface.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MacrosManager;
import com.openterface.keymod.MacrosManager.Macro;
import com.openterface.keymod.MacrosManager.MacrosListener;
import com.openterface.keymod.MacrosManager.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Macros Fragment - Record and playback key sequences
 * Matches iOS MacrosManager.swift functionality
 */
public class MacrosFragment extends Fragment implements MacrosListener {

    private static final String TAG = "MacrosFragment";

    private MacrosManager macrosManager;
    private Vibrator vibrator;
    private ConnectionManager connectionManager;

    // UI Components
    private Button recordButton;
    private Button stopButton;
    private Button playButton;
    private Button importButton;
    private Button exportButton;
    private Button clearButton;
    private ListView macrosListView;
    private TextView emptyTextView;
    private TextView statusTextView;
    private EditText macroNameEditText;

    private MacrosAdapter adapter;
    private List<Macro> macrosList;
    private Macro selectedMacro;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_macros, container, false);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        macrosManager = new MacrosManager(requireContext());
        macrosManager.setListener(this);

        // Get ConnectionManager from MainActivity
        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        setupListeners();
        loadMacros();

        return view;
    }

    private void initializeViews(View view) {
        recordButton = view.findViewById(R.id.record_button);
        stopButton = view.findViewById(R.id.stop_button);
        playButton = view.findViewById(R.id.play_button);
        importButton = view.findViewById(R.id.import_button);
        exportButton = view.findViewById(R.id.export_button);
        clearButton = view.findViewById(R.id.clear_button);
        macrosListView = view.findViewById(R.id.macros_listview);
        emptyTextView = view.findViewById(R.id.empty_textview);
        statusTextView = view.findViewById(R.id.status_textview);
        macroNameEditText = view.findViewById(R.id.macro_name_edittext);

        macrosList = new ArrayList<>();
        adapter = new MacrosAdapter(requireContext(), macrosList);
        macrosListView.setAdapter(adapter);
    }

    private void setupListeners() {
        // Record button
        recordButton.setOnClickListener(v -> {
            String name = macroNameEditText.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a macro name", Toast.LENGTH_SHORT).show();
                return;
            }
            startRecording(name);
        });

        // Stop button
        stopButton.setOnClickListener(v -> {
            if (macrosManager.isRecording()) {
                stopRecording();
            } else if (macrosManager.isPlaying()) {
                macrosManager.stopPlayback();
            }
        });

        // Play button
        playButton.setOnClickListener(v -> {
            if (selectedMacro != null) {
                playSelectedMacro();
            } else {
                Toast.makeText(getContext(), "Please select a macro", Toast.LENGTH_SHORT).show();
            }
        });

        // Import button
        importButton.setOnClickListener(v -> showImportDialog());

        // Export button
        exportButton.setOnClickListener(v -> exportMacros());

        // Clear button
        clearButton.setOnClickListener(v -> showClearConfirmDialog());

        // List item click
        macrosListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedMacro = macrosList.get(position);
            adapter.setSelectedPosition(position);
            adapter.notifyDataSetChanged();
            
            // Haptic feedback
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            }
            
            Toast.makeText(getContext(), "Selected: " + selectedMacro.name, Toast.LENGTH_SHORT).show();
        });

        // List item long click (delete)
        macrosListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Macro macro = macrosList.get(position);
            showDeleteConfirmDialog(macro);
            return true;
        });
    }

    private void loadMacros() {
        macrosList.clear();
        macrosList.addAll(macrosManager.getAllMacros());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void startRecording(String name) {
        macrosManager.startRecording(name);
        updateUI();
        Toast.makeText(getContext(), "Recording started...", Toast.LENGTH_SHORT).show();
        statusTextView.setText("⏺ Recording: " + name);
    }

    private void stopRecording() {
        macrosManager.stopRecording();
        updateUI();
        loadMacros();
        Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Ready");
        macroNameEditText.setText("");
    }

    private void playSelectedMacro() {
        if (!connectionManager.isConnected()) {
            Toast.makeText(getContext(), "Not connected - cannot play macro", Toast.LENGTH_SHORT).show();
            return;
        }
        macrosManager.playMacro(selectedMacro, connectionManager);
        updateUI();
        Toast.makeText(getContext(), "Playing: " + selectedMacro.name, Toast.LENGTH_SHORT).show();
        statusTextView.setText("▶ Playing: " + selectedMacro.name);
    }

    private void showImportDialog() {
        // TODO: Implement file picker for JSON import
        Toast.makeText(getContext(), "Import feature coming soon", Toast.LENGTH_SHORT).show();
    }

    private void exportMacros() {
        String json = macrosManager.exportMacros();
        // TODO: Save to file or share
        Toast.makeText(getContext(), "Exported " + macrosList.size() + " macros", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Exported macros: " + json);
    }

    private void showClearConfirmDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Clear All Macros")
            .setMessage("Are you sure you want to delete all macros? This cannot be undone.")
            .setPositiveButton("Clear All", (dialog, which) -> {
                macrosManager.clearAllMacros();
                loadMacros();
                Toast.makeText(getContext(), "All macros cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showDeleteConfirmDialog(Macro macro) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Macro")
            .setMessage("Delete macro '" + macro.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                macrosManager.deleteMacro(macro);
                loadMacros();
                if (selectedMacro == macro) {
                    selectedMacro = null;
                }
                Toast.makeText(getContext(), "Macro deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateUI() {
        boolean isRecording = macrosManager.isRecording();
        boolean isPlaying = macrosManager.isPlaying();

        recordButton.setEnabled(!isRecording && !isPlaying);
        stopButton.setEnabled(isRecording || isPlaying);
        playButton.setEnabled(!isRecording && !isPlaying && selectedMacro != null);

        if (isRecording) {
            recordButton.setText("⏺ Recording...");
        } else {
            recordButton.setText("⏺ Record");
        }

        if (isPlaying) {
            playButton.setText("▶ Playing...");
        } else {
            playButton.setText("▶ Play");
        }
    }

    private void updateEmptyState() {
        if (macrosList.isEmpty()) {
            macrosListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            macrosListView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
        }
    }

    // MacrosListener callbacks
    @Override
    public void onRecordingStarted(Macro macro) {
        updateUI();
    }

    @Override
    public void onKeyEventRecorded(KeyEvent event) {
        // Update UI with recorded key count
        Macro current = macrosManager.getCurrentRecordingMacro();
        if (current != null) {
            statusTextView.setText("⏺ Recording: " + current.name + 
                                   " (" + current.getKeyCount() + " keys)");
        }
    }

    @Override
    public void onMacroSaved(Macro macro) {
        updateUI();
    }

    @Override
    public void onPlaybackStarted(Macro macro) {
        updateUI();
    }

    @Override
    public void onPlaybackComplete(Macro macro) {
        updateUI();
        statusTextView.setText("✓ Complete");
        Toast.makeText(getContext(), "Macro playback complete", Toast.LENGTH_SHORT).show();
        
        new android.os.Handler().postDelayed(() -> {
            statusTextView.setText("Ready");
        }, 2000);
    }

    @Override
    public void onPlaybackStopped() {
        updateUI();
        statusTextView.setText("Stopped");
        
        new android.os.Handler().postDelayed(() -> {
            statusTextView.setText("Ready");
        }, 2000);
    }

    @Override
    public void onMacroDeleted(Macro macro) {
        updateEmptyState();
    }

    @Override
    public void onMacrosImported(List<Macro> importedMacros) {
        loadMacros();
        Toast.makeText(getContext(), "Imported " + importedMacros.size() + " macros", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMacrosCleared() {
        updateEmptyState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (macrosManager.isRecording()) {
            macrosManager.stopRecording();
        }
        if (macrosManager.isPlaying()) {
            macrosManager.stopPlayback();
        }
    }

    /**
     * Macros List Adapter
     */
    private static class MacrosAdapter extends android.widget.BaseAdapter {
        private final Context context;
        private final List<Macro> macros;
        private int selectedPosition = -1;
        private final SimpleDateFormat dateFormat;

        public MacrosAdapter(Context context, List<Macro> macros) {
            this.context = context;
            this.macros = macros;
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        }

        public void setSelectedPosition(int position) {
            this.selectedPosition = position;
        }

        @Override
        public int getCount() {
            return macros.size();
        }

        @Override
        public Object getItem(int position) {
            return macros.get(position);
        }

        @Override
        public long getItemId(int position) {
            return macros.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            Macro macro = macros.get(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            text1.setText(macro.name);
            text2.setText(macro.getKeyCount() + " keys • " + 
                         macro.getFormattedDuration() + " • " +
                         dateFormat.format(new Date(macro.createdAt)));

            // Highlight selected
            if (position == selectedPosition) {
                convertView.setBackgroundColor(context.getResources().getColor(R.color.sidebar_item_selected));
                text1.setTextColor(context.getResources().getColor(R.color.white));
                text2.setTextColor(context.getResources().getColor(R.color.white));
            } else {
                convertView.setBackgroundColor(context.getResources().getColor(R.color.white));
                text1.setTextColor(context.getResources().getColor(R.color.text_primary));
                text2.setTextColor(context.getResources().getColor(R.color.text_secondary));
            }

            return convertView;
        }
    }
}
