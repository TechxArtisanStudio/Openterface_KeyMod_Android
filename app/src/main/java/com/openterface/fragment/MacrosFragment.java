package com.openterface.fragment;

import android.content.Context;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MacrosManager;
import com.openterface.keymod.MacrosManager.KeyEvent;
import com.openterface.keymod.MacrosManager.Macro;
import com.openterface.keymod.MacrosManager.MacrosListener;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * iOS-aligned Macro screen:
 * - clean macro button grid (3 columns, wrapping)
 * - top-right edit and add icons
 * - long press menu for edit/delete
 */
public class MacrosFragment extends Fragment implements MacrosListener {

    private static final String TAG = "MacrosFragment";

    private MacrosManager macrosManager;
    private ConnectionManager connectionManager;

    private ImageButton editModeButton;
    private ImageButton addMacroButton;
    private GridView macrosGridView;
    private TextView emptyTextView;

    private final List<Macro> macrosList = new ArrayList<>();
    private MacroGridAdapter adapter;
    private boolean editMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_macros, container, false);

        macrosManager = MacrosManager.getInstance(requireContext());
        macrosManager.setListener(this);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        setupListeners();
        loadMacros();
        updateEditModeUi();

        return view;
    }

    private void initializeViews(View view) {
        editModeButton = view.findViewById(R.id.edit_mode_button);
        addMacroButton = view.findViewById(R.id.add_macro_button);
        macrosGridView = view.findViewById(R.id.macros_gridview);
        emptyTextView = view.findViewById(R.id.empty_textview);

        adapter = new MacroGridAdapter(requireContext(), macrosList);
        macrosGridView.setAdapter(adapter);
    }

    private void setupListeners() {
        addMacroButton.setOnClickListener(v -> showMacroEditorDialog(null));

        editModeButton.setOnClickListener(v -> {
            editMode = !editMode;
            updateEditModeUi();
        });

        macrosGridView.setOnItemClickListener((parent, view, position, id) -> {
            Macro macro = macrosList.get(position);
            if (editMode) {
                showMacroEditorDialog(macro);
            } else {
                playMacro(macro);
            }
        });

        macrosGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            Macro macro = macrosList.get(position);
            showMacroActionsMenu(macro);
            return true;
        });
    }

    private void updateEditModeUi() {
        int activeColor = requireContext().getResources().getColor(R.color.primary);
        int normalColor = requireContext().getResources().getColor(R.color.text_secondary);
        editModeButton.setColorFilter(editMode ? activeColor : normalColor);
    }

    private void loadMacros() {
        macrosList.clear();
        macrosList.addAll(macrosManager.getAllMacros());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = macrosList.isEmpty();
        macrosGridView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void playMacro(Macro macro) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(getContext(), "Not connected - cannot play macro", Toast.LENGTH_SHORT).show();
            return;
        }
        macrosManager.playMacro(macro, connectionManager);
        Toast.makeText(getContext(), "Playing: " + macro.name, Toast.LENGTH_SHORT).show();
    }

    private void showMacroActionsMenu(Macro macro) {
        String[] options = {"Edit", "Delete"};
        new AlertDialog.Builder(requireContext())
            .setTitle(macro.name)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showMacroEditorDialog(macro);
                } else {
                    showDeleteConfirmDialog(macro);
                }
            })
            .show();
    }

    private void showDeleteConfirmDialog(Macro macro) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Macro")
            .setMessage("Delete '" + macro.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                macrosManager.deleteMacro(macro);
                loadMacros();
                Toast.makeText(getContext(), "Macro deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMacroEditorDialog(@Nullable Macro macro) {
        final boolean isEditing = macro != null;

        int padding = dp(16);
        final long[] scheduledAtMillis = {isEditing ? macro.scheduledAtMillis : 0L};

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        final EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Macro Name (Label)");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);

        TextView dataTitle = new TextView(requireContext());
        dataTitle.setText("Macro Data");
        dataTitle.setTextColor(requireContext().getResources().getColor(R.color.text_secondary));
        dataTitle.setPadding(0, dp(8), 0, dp(4));

        final EditText dataInput = new EditText(requireContext());
        dataInput.setHint("e.g. <CTRL>C</CTRL><DELAY1S><ENTER>");
        dataInput.setMinLines(4);
        dataInput.setMaxLines(8);
        dataInput.setGravity(Gravity.TOP | Gravity.START);

        HorizontalScrollView keyScroll = new HorizontalScrollView(requireContext());
        LinearLayout keyRow = new LinearLayout(requireContext());
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyScroll.addView(keyRow);

        String[][] keyEntries = {
            {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
            {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
            {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
            {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
            {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"},
            {"⏱ 1s", "<DELAY1S>"}, {"⏱ 2s", "<DELAY2S>"}, {"⏱ 5s", "<DELAY5S>"}, {"⏱ 10s", "<DELAY10S>"}
        };

        for (String[] keyEntry : keyEntries) {
            Button chip = new Button(requireContext());
            chip.setText(keyEntry[0]);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setPadding(dp(8), dp(2), dp(8), dp(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> insertToken(dataInput, keyEntry[1]));
            keyRow.addView(chip);
        }

        final TextView intervalText = new TextView(requireContext());
        intervalText.setPadding(0, dp(10), 0, dp(4));

        final SeekBar intervalSeek = new SeekBar(requireContext());
        intervalSeek.setMax(99); // 10..1000 by step 10

        TextView schedulerTitle = new TextView(requireContext());
        schedulerTitle.setText("Scheduler (Optional)");
        schedulerTitle.setTextColor(requireContext().getResources().getColor(R.color.text_secondary));
        schedulerTitle.setPadding(0, dp(10), 0, dp(4));

        final SwitchCompat schedulerSwitch = new SwitchCompat(requireContext());
        schedulerSwitch.setText("Enable Scheduler");

        final TextView scheduleAtText = new TextView(requireContext());
        final Button pickTimeButton = new Button(requireContext());
        pickTimeButton.setText("Pick Run Time");

        final EditText repeatCountInput = new EditText(requireContext());
        repeatCountInput.setHint("Repeat count (optional)");
        repeatCountInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        final EditText repeatIntervalInput = new EditText(requireContext());
        repeatIntervalInput.setHint("Repeat interval seconds (optional)");
        repeatIntervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        int initialIntervalMs = isEditing && macro.intervalMs > 0 ? macro.intervalMs : 100;
        intervalSeek.setProgress(Math.max(0, Math.min(99, (initialIntervalMs / 10) - 1)));
        intervalText.setText("Send Char Interval (ms): " + ((intervalSeek.getProgress() + 1) * 10));
        intervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intervalText.setText("Send Char Interval (ms): " + ((progress + 1) * 10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        pickTimeButton.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (scheduledAtMillis[0] > 0L) {
                c.setTimeInMillis(scheduledAtMillis[0]);
            }

            DatePickerDialog datePicker = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    TimePickerDialog timePicker = new TimePickerDialog(requireContext(),
                        (tpView, hour, minute) -> {
                            Calendar selected = Calendar.getInstance();
                            selected.set(year, month, dayOfMonth, hour, minute, 0);
                            scheduledAtMillis[0] = selected.getTimeInMillis();
                            scheduleAtText.setText("Run At: " + formatScheduleTime(scheduledAtMillis[0]));
                        },
                        c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE),
                        true);
                    timePicker.show();
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            datePicker.show();
        });

        if (isEditing) {
            nameInput.setText(macro.name);
            nameInput.setSelection(macro.name != null ? macro.name.length() : 0);
            dataInput.setText(macro.data != null ? macro.data : "");
            schedulerSwitch.setChecked(macro.isScheduled);
            if (macro.scheduledAtMillis > 0L) {
                scheduleAtText.setText("Run At: " + formatScheduleTime(macro.scheduledAtMillis));
            } else {
                scheduleAtText.setText("Run At: not set");
            }
            if (macro.repeatCount > 0) {
                repeatCountInput.setText(String.valueOf(macro.repeatCount));
            }
            if (macro.repeatIntervalSeconds > 0) {
                repeatIntervalInput.setText(String.valueOf(macro.repeatIntervalSeconds));
            }
        } else {
            schedulerSwitch.setChecked(false);
            scheduleAtText.setText("Run At: not set");
            dataInput.setText("");
        }

        container.addView(nameInput);
        container.addView(dataTitle);
        container.addView(dataInput);
        container.addView(keyScroll);
        container.addView(intervalText);
        container.addView(intervalSeek);
        container.addView(schedulerTitle);
        container.addView(schedulerSwitch);
        container.addView(scheduleAtText);
        container.addView(pickTimeButton);
        container.addView(repeatCountInput);
        container.addView(repeatIntervalInput);

        Runnable syncSchedulerVisibility = () -> {
            int v = schedulerSwitch.isChecked() ? View.VISIBLE : View.GONE;
            scheduleAtText.setVisibility(v);
            pickTimeButton.setVisibility(v);
            repeatCountInput.setVisibility(v);
            repeatIntervalInput.setVisibility(v);
        };
        schedulerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> syncSchedulerVisibility.run());
        syncSchedulerVisibility.run();

        new AlertDialog.Builder(requireContext())
            .setTitle(isEditing ? "Edit Macro" : "Add Macro")
            .setView(container)
            .setPositiveButton("Save", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                String data = dataInput.getText().toString();
                if (name.isEmpty()) {
                    Toast.makeText(getContext(), "Macro name is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (data.trim().isEmpty()) {
                    Toast.makeText(getContext(), "Macro data is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                int intervalMs = (intervalSeek.getProgress() + 1) * 10;
                boolean isScheduled = schedulerSwitch.isChecked();
                int repeatCount = parseIntOrZero(repeatCountInput.getText().toString());
                long repeatIntervalSeconds = parseLongOrZero(repeatIntervalInput.getText().toString());

                List<KeyEvent> events = buildKeyEventsFromMacroData(data, intervalMs);
                if (events.isEmpty()) {
                    Toast.makeText(getContext(), "No valid key events generated from macro data", Toast.LENGTH_LONG).show();
                    return;
                }

                boolean saved;
                if (isEditing) {
                    saved = macrosManager.updateMacro(
                        macro.id,
                        name,
                        data,
                        intervalMs,
                        isScheduled,
                        scheduledAtMillis[0],
                        repeatCount,
                        repeatIntervalSeconds,
                        events
                    );
                } else {
                    macrosManager.createMacro(
                        name,
                        data,
                        intervalMs,
                        isScheduled,
                        scheduledAtMillis[0],
                        repeatCount,
                        repeatIntervalSeconds,
                        events
                    );
                    saved = true;
                }

                if (saved) {
                    loadMacros();
                    Toast.makeText(getContext(), isEditing ? "Macro updated" : "Macro added", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to save macro", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void insertToken(EditText editText, String token) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        editText.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
    }

    private List<KeyEvent> buildKeyEventsFromMacroData(String data, int intervalMs) {
        List<KeyEvent> events = new ArrayList<>();
        Pattern p = Pattern.compile("<[^>]+>|.", Pattern.DOTALL);
        Matcher m = p.matcher(data);

        int activeModifiers = 0;
        long timestamp = 0;

        while (m.find()) {
            String token = m.group();
            if (token == null || token.isEmpty()) {
                continue;
            }

            switch (token) {
                case "<CTRL>": activeModifiers |= 0x01; continue;
                case "<SHIFT>": activeModifiers |= 0x02; continue;
                case "<ALT>": activeModifiers |= 0x04; continue;
                case "<CMD>": activeModifiers |= 0x08; continue;
                case "</CTRL>": activeModifiers &= ~0x01; continue;
                case "</SHIFT>": activeModifiers &= ~0x02; continue;
                case "</ALT>": activeModifiers &= ~0x04; continue;
                case "</CMD>": activeModifiers &= ~0x08; continue;
                case "<DELAY1S>": timestamp += 1000; continue;
                case "<DELAY2S>": timestamp += 2000; continue;
                case "<DELAY5S>": timestamp += 5000; continue;
                case "<DELAY10S>": timestamp += 10000; continue;
                default: break;
            }

            int keyCode = mapTokenToHidCode(token);
            int modifiers = activeModifiers;

            if (keyCode < 0 && token.length() == 1) {
                char c = token.charAt(0);
                keyCode = mapCharToHidCode(c);
                if (needsShift(c)) {
                    modifiers |= 0x02;
                }
            }

            if (keyCode >= 0) {
                events.add(new KeyEvent(keyCode, modifiers, timestamp));
                timestamp += intervalMs;
            } else {
                Log.w(TAG, "Unsupported macro token: " + token);
            }
        }

        return events;
    }

    private int mapTokenToHidCode(String token) {
        switch (token) {
            case "<ESC>": return 41;
            case "<BACK>": return 42;
            case "<ENTER>": return 40;
            case "<SPACE>": return 44;
            case "<LEFT>": return 80;
            case "<RIGHT>": return 79;
            case "<UP>": return 82;
            case "<DOWN>": return 81;
            case "<HOME>": return 74;
            case "<END>": return 77;
            case "\n": return 40;
            case "\t": return 43;
            default: return -1;
        }
    }

    private int mapCharToHidCode(char c) {
        if (c >= 'a' && c <= 'z') {
            return 4 + (c - 'a');
        }
        if (c >= 'A' && c <= 'Z') {
            return 4 + (c - 'A');
        }
        if (c >= '1' && c <= '9') {
            return 30 + (c - '1');
        }
        if (c == '0') {
            return 39;
        }

        switch (c) {
            case ' ': return 44;
            case '-': case '_': return 45;
            case '=': case '+': return 46;
            case '[': case '{': return 47;
            case ']': case '}': return 48;
            case '\\': case '|': return 49;
            case ';': case ':': return 51;
            case '\'': case '"': return 52;
            case ',': case '<': return 54;
            case '.': case '>': return 55;
            case '/': case '?': return 56;
            case '`': case '~': return 53;
            case '!': return 30;
            case '@': return 31;
            case '#': return 32;
            case '$': return 33;
            case '%': return 34;
            case '^': return 35;
            case '&': return 36;
            case '*': return 37;
            case '(': return 38;
            case ')': return 39;
            default: return -1;
        }
    }

    private boolean needsShift(char c) {
        if (Character.isUpperCase(c)) {
            return true;
        }
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatScheduleTime(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return String.format(
            Locale.getDefault(),
            "%04d-%02d-%02d %02d:%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE)
        );
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRecordingStarted(Macro macro) {
        // Not used in this iOS-style grid view.
    }

    @Override
    public void onKeyEventRecorded(KeyEvent event) {
        // Not used in this iOS-style grid view.
    }

    @Override
    public void onMacroSaved(Macro macro) {
        loadMacros();
    }

    @Override
    public void onPlaybackStarted(Macro macro) {
        // Optional future UI state.
    }

    @Override
    public void onPlaybackComplete(Macro macro) {
        Toast.makeText(getContext(), "Macro playback complete", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPlaybackStopped() {
        // Optional future UI state.
    }

    @Override
    public void onMacroDeleted(Macro macro) {
        loadMacros();
    }

    @Override
    public void onMacrosImported(List<Macro> importedMacros) {
        loadMacros();
    }

    @Override
    public void onMacrosCleared() {
        loadMacros();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        macrosManager.setListener(null);
    }

    private static class MacroGridAdapter extends BaseAdapter {
        private final Context context;
        private final List<Macro> macros;

        MacroGridAdapter(Context context, List<Macro> macros) {
            this.context = context;
            this.macros = macros;
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
                convertView = LayoutInflater.from(context).inflate(R.layout.item_macro_button, parent, false);
            }

            Macro macro = macros.get(position);
            TextView title = convertView.findViewById(R.id.macro_button_text);
            MaterialCardView card = (MaterialCardView) convertView;

            title.setText(macro.name);
            card.setCardBackgroundColor(context.getResources().getColor(R.color.card_unselected_bg));

            return convertView;
        }
    }
}
