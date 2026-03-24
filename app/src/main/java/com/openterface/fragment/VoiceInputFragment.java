package com.openterface.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.R;
import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Voice Input Fragment - Speech-to-text with Whisper API
 * Phase 4: Voice Input Mode
 */
public class VoiceInputFragment extends Fragment implements TextToSpeech.OnInitListener {

    private static final String TAG = "VoiceInputFragment";
    private static final int SAMPLE_RATE = 16000;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREF_STT_ENGINE = "stt_engine";
    private static final String STT_ENGINE_SYSTEM = "system";
    private static final String STT_ENGINE_WHISPER = "whisper";
    private static final String PREF_AUTO_SEND = "voice_auto_send_to_target";
    private static final String PREF_AUTO_LINE_RETURN = "voice_auto_line_return";
    private static final long SEND_BUTTON_REFRESH_INTERVAL_MS = 1000L;
    private static final String PREF_HISTORY = "voice_input_history";

    // UI Components
    private ImageButton recordButton;
    private Button pasteButton;
    private Button clearButton;
    private ImageButton copyButton;
    private ImageButton clearTopButton;
    private EditText transcribedText;
    private TextView statusText;
    private ProgressBar progressBar;
    private ImageView waveformView;
    private ImageButton autoSendButton;
    private ImageButton autoLineReturnButton;

    // History UI
    private RecyclerView historyRecyclerView;
    private TextView historyEmptyText;
    private Button clearHistoryButton;
    private List<HistoryItem> sentHistory = new ArrayList<>();
    private VoiceHistoryAdapter historyAdapter;

    // State
    private boolean isRecording = false;
    private boolean isProcessing = false;
    private boolean autoSendToTarget = false;
    private boolean autoLineReturn = false;

    // Audio Recording
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean shouldStopRecording = false;

    // Whisper API
    private SharedPreferences prefs;
    private String apiKey = "";
    private String language = "en";
    
    // TTS (for reading back text)
    private TextToSpeech tts;

    // Thread pool for async operations
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;
    private final Runnable sendButtonStateUpdater = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                updateSendButtonState();
                mainHandler.postDelayed(this, SEND_BUTTON_REFRESH_INTERVAL_MS);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_input, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        initSpeechRecognizerLauncher();
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        initializeTTS();
        updateSendButtonState();

        return view;
    }

    private void initializeViews(View view) {
        recordButton = view.findViewById(R.id.record_button);
        pasteButton = view.findViewById(R.id.paste_button);
        clearButton = view.findViewById(R.id.clear_button);
        copyButton = view.findViewById(R.id.copy_button);
        clearTopButton = view.findViewById(R.id.clear_top_button);
        transcribedText = view.findViewById(R.id.transcribed_text);
        statusText = view.findViewById(R.id.status_text);
        progressBar = view.findViewById(R.id.progress_bar);
        waveformView = view.findViewById(R.id.waveform_view);
        autoSendButton = view.findViewById(R.id.auto_send_button);
        autoLineReturnButton = view.findViewById(R.id.auto_line_return_button);

        // History views
        historyRecyclerView = view.findViewById(R.id.history_recycler_view);
        historyEmptyText = view.findViewById(R.id.history_empty_text);
        clearHistoryButton = view.findViewById(R.id.clear_history_button);

        loadHistory();
        historyAdapter = new VoiceHistoryAdapter(sentHistory);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyRecyclerView.setAdapter(historyAdapter);
        refreshHistoryUi();

        clearHistoryButton.setOnClickListener(v -> {
            sentHistory.clear();
            saveHistory();
            refreshHistoryUi();
        });
    }

    private void loadSettings() {
        apiKey = prefs.getString("whisper_api_key", "");
        language = getLanguageCodeFromSettings();
        autoSendToTarget = prefs.getBoolean(PREF_AUTO_SEND, false);
        autoLineReturn = prefs.getBoolean(PREF_AUTO_LINE_RETURN, false);
        updateMiniToolbarState();
        
        Log.d(TAG, "Loaded settings - API Key: " + (apiKey.isEmpty() ? "not set" : "set") + 
              ", Language: " + language);
    }

    private void setupListeners() {
        // Record button
        recordButton.setOnClickListener(v -> {
            if (isSystemSttEnabled()) {
                startSystemSpeechRecognition();
                return;
            }

            if (checkPermission()) {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            } else {
                requestPermission();
            }
        });

        // Paste button - send to keyboard
        pasteButton.setOnClickListener(v -> {
            sendTranscribedText(false);
        });

        // Clear button
        clearButton.setOnClickListener(v -> {
            transcribedText.setText("");
            statusText.setText("Ready");
        });

        clearTopButton.setOnClickListener(v -> {
            transcribedText.setText("");
            statusText.setText("Ready");
        });

        // Mini toolbar copy button
        copyButton.setOnClickListener(v -> {
            String text = transcribedText.getText().toString();
            if (text.trim().isEmpty()) {
                Toast.makeText(getContext(), "No text to copy", Toast.LENGTH_SHORT).show();
                return;
            }

            ClipboardManager clipboardManager =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                ClipData clip = ClipData.newPlainText("voice_transcript", text);
                clipboardManager.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Mini toolbar icon toggles
        autoSendButton.setOnClickListener(v -> {
            autoSendToTarget = !autoSendToTarget;
            prefs.edit().putBoolean(PREF_AUTO_SEND, autoSendToTarget).apply();
            updateMiniToolbarState();
        });

        autoLineReturnButton.setOnClickListener(v -> {
            autoLineReturn = !autoLineReturn;
            prefs.edit().putBoolean(PREF_AUTO_LINE_RETURN, autoLineReturn).apply();
            updateMiniToolbarState();
        });
    }

    private void updateMiniToolbarState() {
        if (getContext() == null) {
            return;
        }

        int neutralColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        int sendActive = ContextCompat.getColor(requireContext(), R.color.primary);
        int returnActive = ContextCompat.getColor(requireContext(), R.color.connecting);

        if (copyButton != null) {
            copyButton.setColorFilter(neutralColor);
            copyButton.setBackgroundColor(Color.TRANSPARENT);
        }

        if (autoSendButton != null) {
            autoSendButton.setColorFilter(autoSendToTarget ? sendActive : neutralColor);
            autoSendButton.setAlpha(autoSendToTarget ? 1.0f : 0.7f);
            autoSendButton.setBackgroundColor(Color.TRANSPARENT);
        }

        if (autoLineReturnButton != null) {
            autoLineReturnButton.setColorFilter(autoLineReturn ? returnActive : neutralColor);
            autoLineReturnButton.setAlpha(autoLineReturn ? 1.0f : 0.7f);
            autoLineReturnButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void sendTranscribedText(boolean fromAutoSend) {
        if (!isBluetoothConnected()) {
            pasteButton.setEnabled(false);
            pasteButton.setAlpha(0.45f);
            if (!fromAutoSend) {
                Toast.makeText(getContext(), "Bluetooth not connected", Toast.LENGTH_SHORT).show();
            }
            statusText.setText("Bluetooth not connected");
            return;
        }

        String text = transcribedText.getText().toString().trim();
        if (text.isEmpty()) {
            if (!fromAutoSend) {
                Toast.makeText(getContext(), "No text to send", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String payload = autoLineReturn ? text + "\n" : text;
        sendTextAsKeystrokes(payload);
        addToHistory(text);

        if (fromAutoSend) {
            statusText.setText("✅ Transcribed and sent");
        }
        Toast.makeText(getContext(), "Sent: " + text.length() + " characters", Toast.LENGTH_SHORT).show();
    }

    private boolean isBluetoothConnected() {
        if (!(requireActivity() instanceof MainActivity)) {
            return false;
        }

        MainActivity mainActivity = (MainActivity) requireActivity();
        BluetoothService bluetoothService = mainActivity.getBluetoothService();
        if (bluetoothService != null && bluetoothService.isConnected()) {
            return true;
        }

        // Fallback to activity-tracked state in case service binding is delayed.
        return mainActivity.isBluetoothConnected;
    }

    private void updateSendButtonState() {
        boolean connected = isAdded() && isBluetoothConnected();
        if (pasteButton != null) {
            pasteButton.setEnabled(connected);
            pasteButton.setAlpha(connected ? 1.0f : 0.45f);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-read language in case it was changed in Settings
        language = getLanguageCodeFromSettings();
        updateSendButtonState();
        mainHandler.post(sendButtonStateUpdater);
    }

    private void initializeTTS() {
        tts = new TextToSpeech(requireContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not supported");
            }
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), 
               Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "Microphone permission granted", Toast.LENGTH_SHORT).show();
                if (isSystemSttEnabled()) {
                    startSystemSpeechRecognition();
                } else {
                    startRecording();
                }
            } else {
                Toast.makeText(getContext(), "Microphone permission required", Toast.LENGTH_LONG).show();
                statusText.setText("Microphone permission required");
            }
        }
    }

    private void initSpeechRecognizerLauncher() {
        speechRecognizerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                updateRecordButtonUi(false);
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    java.util.ArrayList<String> results = result.getData()
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty()) {
                        String recognizedText = results.get(0).trim();
                        appendTranscribedText(recognizedText);
                        statusText.setText("✅ Transcribed");

                        if (autoSendToTarget && !recognizedText.isEmpty()) {
                            sendTranscribedText(true);
                        }
                        return;
                    }
                }

                statusText.setText("No speech recognized");
            }
        );
    }

    private boolean isSystemSttEnabled() {
        String engine = prefs.getString(PREF_STT_ENGINE, STT_ENGINE_SYSTEM);
        return STT_ENGINE_SYSTEM.equals(engine);
    }

    private String getLanguageCodeFromSettings() {
        Object value = prefs.getAll().get("voice_language");

        if (value instanceof String) {
            String code = (String) value;
            if (!code.isEmpty()) {
                return code;
            }
        }

        if (value instanceof Integer) {
            int index = (Integer) value;
            String[] languageCodes = {"en", "zh", "es", "fr", "de", "ja"};
            if (index >= 0 && index < languageCodes.length) {
                return languageCodes[index];
            }
        }

        return "en";
    }

    private String toRecognizerLocaleTag(String code) {
        if ("zh".equalsIgnoreCase(code)) return "zh-CN";
        if ("es".equalsIgnoreCase(code)) return "es-ES";
        if ("fr".equalsIgnoreCase(code)) return "fr-FR";
        if ("de".equalsIgnoreCase(code)) return "de-DE";
        if ("ja".equalsIgnoreCase(code)) return "ja-JP";
        return "en-US";
    }

    private void startSystemSpeechRecognition() {
        if (!checkPermission()) {
            requestPermission();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, toRecognizerLocaleTag(language));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");

        if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
            Toast.makeText(getContext(), "No speech recognizer available on this device", Toast.LENGTH_LONG).show();
            statusText.setText("Speech recognizer unavailable");
            return;
        }

        updateRecordButtonUi(true);
        statusText.setText("🎤 Listening...");
        speechRecognizerLauncher.launch(intent);
    }

    private void updateRecordButtonUi(boolean listening) {
        if (recordButton == null) return;

        if (listening) {
            recordButton.setImageResource(R.drawable.ic_toolbar_stop);
            recordButton.setBackgroundResource(R.drawable.bg_record_button_active);
            recordButton.setContentDescription("Stop recording");
        } else {
            recordButton.setImageResource(R.drawable.ic_toolbar_mic);
            recordButton.setBackgroundResource(R.drawable.bg_record_button_idle);
            recordButton.setContentDescription("Start recording");
        }
    }

    private void startRecording() {
        if (apiKey.isEmpty()) {
            Toast.makeText(getContext(), "Please set Whisper API key in Settings", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT);

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(getContext(), "Failed to initialize audio recorder", Toast.LENGTH_SHORT).show();
                return;
            }

            audioRecord.startRecording();
            isRecording = true;
            shouldStopRecording = false;

            // Update UI
            updateRecordButtonUi(true);
            statusText.setText("🎤 Listening...");
            progressBar.setVisibility(View.VISIBLE);

            // Start recording thread
            recordingThread = new Thread(this::recordAudio);
            recordingThread.start();

            Log.d(TAG, "Recording started");

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied", e);
            Toast.makeText(getContext(), "Microphone permission required", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(getContext(), "Failed to start: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        shouldStopRecording = true;
        isRecording = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio record", e);
            }
            audioRecord = null;
        }

        // Update UI
        updateRecordButtonUi(false);
        statusText.setText("⏳ Processing...");
        progressBar.setVisibility(View.VISIBLE);

        Log.d(TAG, "Recording stopped");
    }

    private void recordAudio() {
        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        try {
            while (!shouldStopRecording && audioRecord != null) {
                bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioStream.write(buffer, 0, bytesRead);
                }
            }

            // Recording complete, send to Whisper
            byte[] audioData = audioStream.toByteArray();
            Log.d(TAG, "Recorded " + audioData.length + " bytes");

            if (audioData.length > 0) {
                sendToWhisper(audioData);
            } else {
                mainHandler.post(() -> {
                    statusText.setText("No audio recorded");
                    progressBar.setVisibility(View.GONE);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Recording error", e);
            mainHandler.post(() -> {
                statusText.setText("Error: " + e.getMessage());
                progressBar.setVisibility(View.GONE);
            });
        } finally {
            try {
                audioStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing stream", e);
            }
        }
    }

    private void sendToWhisper(byte[] audioData) {
        executor.execute(() -> {
            try {
                // Convert to WAV format
                byte[] wavData = convertToWav(audioData);
                
                // Send to Whisper API
                String result = callWhisperAPI(wavData);
                
                mainHandler.post(() -> {
                    if (result != null) {
                        appendTranscribedText(result);
                        statusText.setText("✅ Transcribed");
                        Log.d(TAG, "Transcription: " + result);
                        if (autoSendToTarget && !result.trim().isEmpty()) {
                            sendTranscribedText(true);
                        }
                    } else {
                        statusText.setText("❌ Transcription failed");
                    }
                    progressBar.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Whisper API error", e);
                mainHandler.post(() -> {
                    statusText.setText("❌ Error: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void appendTranscribedText(String newText) {
        if (newText == null) {
            return;
        }

        String addition = newText.trim();
        if (addition.isEmpty()) {
            return;
        }

        String existing = transcribedText.getText() == null ? "" : transcribedText.getText().toString();
        if (existing.trim().isEmpty()) {
            transcribedText.setText(addition);
        } else {
            String separator = existing.endsWith(" ") || existing.endsWith("\n") ? "" : " ";
            transcribedText.setText(existing + separator + addition);
        }

        transcribedText.setSelection(transcribedText.getText().length());
    }

    private byte[] convertToWav(byte[] pcmData) throws Exception {
        int sampleRate = SAMPLE_RATE;
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(wavStream);

        // RIFF header
        out.writeBytes("RIFF");
        out.writeInt(chunkSize);
        out.writeBytes("WAVE");

        // fmt subchunk
        out.writeBytes("fmt ");
        out.writeInt(16); // Subchunk1Size
        out.writeShort(1); // AudioFormat (PCM)
        out.writeShort(channels);
        out.writeInt(sampleRate);
        out.writeInt(byteRate);
        out.writeShort(blockAlign);
        out.writeShort(bitsPerSample);

        // data subchunk
        out.writeBytes("data");
        out.writeInt(dataSize);
        out.write(pcmData);

        out.close();
        return wavStream.toByteArray();
    }

    private String callWhisperAPI(byte[] wavData) throws Exception {
        URL url = new URL("https://api.openai.com/v1/audio/transcriptions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        // Set headers
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        // Create multipart form data
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());

        // Add file
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n");
        outputStream.writeBytes("Content-Type: audio/wav\r\n\r\n");
        outputStream.write(wavData);
        outputStream.writeBytes("\r\n");

        // Add model
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        outputStream.writeBytes("whisper-1\r\n");

        // Add language
        if (!language.isEmpty()) {
            outputStream.writeBytes("--" + boundary + "\r\n");
            outputStream.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            outputStream.writeBytes(language + "\r\n");
        }

        outputStream.writeBytes("--" + boundary + "--\r\n");
        outputStream.flush();
        outputStream.close();

        // Read response
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Whisper API response code: " + responseCode);

        if (responseCode == 200) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse JSON response
            JSONObject json = new JSONObject(response.toString());
            String text = json.optString("text", "").trim();
            
            return text;
        } else {
            java.io.BufferedReader errorReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            
            Log.e(TAG, "Whisper API error: " + errorResponse.toString());
            throw new Exception("API error: " + responseCode);
        }
    }

    private void sendTextAsKeystrokes(String text) {
        Log.d(TAG, "Sending text: " + text);

        if (!(requireActivity() instanceof MainActivity)) return;
        MainActivity mainActivity = (MainActivity) requireActivity();
        ConnectionManager connectionManager = mainActivity.getConnectionManager();
        if (connectionManager == null || !connectionManager.isConnected()) {
            Log.w(TAG, "ConnectionManager not available or not connected");
            return;
        }

        executor.execute(() -> {
            // Iterate by Unicode code point to support supplementary chars (e.g. emoji)
            int i = 0;
            while (i < text.length()) {
                int codePoint = text.codePointAt(i);
                i += Character.charCount(codePoint);

                if (codePoint > 0x7E) {
                    // Non-ASCII (e.g. Chinese, emoji): use macOS Unicode Hex Input
                    // Requires "Unicode Hex Input" keyboard enabled on the target macOS
                    try {
                        sendUnicodeCharMacOS(codePoint, connectionManager);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    char c = (char) codePoint;
                    int hidCode = mapCharToHidCode(c);
                    if (hidCode < 0) {
                        Log.w(TAG, "No HID code for char: '" + c + "' (U+" + Integer.toHexString(c) + ")");
                        continue;
                    }
                    int modifiers = needsShift(c) ? 0x02 : 0x00;
                    connectionManager.sendKeyEvent(modifiers, hidCode);
                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                    connectionManager.sendKeyRelease();
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }
            // Clear the text field on the main thread after all keystrokes are sent
            mainHandler.post(() -> {
                if (transcribedText != null) {
                    transcribedText.setText("");
                }
            });
        });
    }

    private int mapCharToHidCode(char c) {
        if (c >= 'a' && c <= 'z') return 4 + (c - 'a');
        if (c >= 'A' && c <= 'Z') return 4 + (c - 'A');
        if (c >= '1' && c <= '9') return 30 + (c - '1');
        if (c == '0') return 39;
        switch (c) {
            case ' ':  return 44;
            case '\n': return 40;
            case '\t': return 43;
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
            default:   return -1;
        }
    }

    private boolean needsShift(char c) {
        if (Character.isUpperCase(c)) return true;
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }

    /**
     * Send a non-ASCII Unicode character to a macOS target using the
     * "Unicode Hex Input" method: hold Option (Alt), type the 4-hex-digit
     * code point on the regular keyboard row, then release Option.
     *
     * Requires the target macOS to have "Unicode Hex Input" enabled in
     * System Settings → Keyboard → Input Sources.
     */
    private void sendUnicodeCharMacOS(int codePoint, ConnectionManager cm)
            throws InterruptedException {
        String hex = String.format("%04x", codePoint);
        Log.d(TAG, "Unicode hex input: U+" + hex.toUpperCase() + " for codePoint=" + codePoint);

        final int kAlt = 0x04; // Left Alt / Option modifier bit

        // 1. Alt (Option) down — no key, just modifier
        cm.sendRawHIDReport(kAlt, 0x00);
        Thread.sleep(50);

        // 2. Type each hex digit while Alt is held
        for (char c : hex.toCharArray()) {
            int code = hexCharToHidCode(c);
            if (code < 0) continue;
            cm.sendRawHIDReport(kAlt, code);  // digit pressed with Alt held
            Thread.sleep(50);
            cm.sendRawHIDReport(kAlt, 0x00);  // digit released, Alt still held
            Thread.sleep(50);
        }

        // 3. Release Alt → macOS commits the character
        cm.sendRawHIDReport(0x00, 0x00);
        Thread.sleep(100); // commit delay
    }

    /** Maps lowercase hex chars (0-9, a-f) to their HID key codes on the regular keyboard row. */
    private int hexCharToHidCode(char c) {
        switch (c) {
            case '0': return 0x27;
            case '1': return 0x1E;
            case '2': return 0x1F;
            case '3': return 0x20;
            case '4': return 0x21;
            case '5': return 0x22;
            case '6': return 0x23;
            case '7': return 0x24;
            case '8': return 0x25;
            case '9': return 0x26;
            case 'a': return 0x04;
            case 'b': return 0x05;
            case 'c': return 0x06;
            case 'd': return 0x07;
            case 'e': return 0x08;
            case 'f': return 0x09;
            default:  return -1;
        }
    }

    // ─── History ────────────────────────────────────────────────────────────

    private static class HistoryItem {
        final String text;
        final long timestamp;
        HistoryItem(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    private void addToHistory(String text) {
        if (text == null || text.trim().isEmpty()) return;
        // Avoid consecutive duplicates
        if (!sentHistory.isEmpty() && sentHistory.get(sentHistory.size() - 1).text.equals(text)) return;
        sentHistory.add(new HistoryItem(text, System.currentTimeMillis()));
        saveHistory();
        mainHandler.post(this::refreshHistoryUi);
    }

    private void refreshHistoryUi() {
        if (historyAdapter == null) return;
        historyAdapter.notifyDataSetChanged();
        if (sentHistory.isEmpty()) {
            historyEmptyText.setVisibility(View.VISIBLE);
            historyRecyclerView.setVisibility(View.GONE);
        } else {
            historyEmptyText.setVisibility(View.GONE);
            historyRecyclerView.setVisibility(View.VISIBLE);
            // Scroll to newest (shown at top via reversed index in adapter)
            historyRecyclerView.scrollToPosition(0);
        }
    }

    private void loadHistory() {
        try {
            String json = prefs.getString(PREF_HISTORY, "[]");
            JSONArray arr = new JSONArray(json);
            sentHistory.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                sentHistory.add(new HistoryItem(obj.getString("text"), obj.getLong("ts")));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load history: " + e.getMessage());
        }
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (HistoryItem item : sentHistory) {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("ts", item.timestamp);
                arr.put(obj);
            }
            prefs.edit().putString(PREF_HISTORY, arr.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save history: " + e.getMessage());
        }
    }

    private String formatTimestamp(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private void resendHistoryItem(String text) {
        if (!isBluetoothConnected()) {
            Toast.makeText(getContext(), "Bluetooth not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        String payload = autoLineReturn ? text + "\n" : text;
        sendTextAsKeystrokes(payload);
    }

    private class VoiceHistoryAdapter extends RecyclerView.Adapter<VoiceHistoryAdapter.VH> {

        private final List<HistoryItem> items;

        VoiceHistoryAdapter(List<HistoryItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_voice_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Show newest first
            HistoryItem item = items.get(items.size() - 1 - position);
            holder.textView.setText(item.text);
            holder.timestampView.setText(formatTimestamp(item.timestamp));
            holder.editButton.setOnClickListener(v -> {
                transcribedText.setText(item.text);
                transcribedText.setSelection(item.text.length());
                transcribedText.requestFocus();
            });
            holder.resendButton.setOnClickListener(v -> resendHistoryItem(item.text));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView textView, timestampView;
            Button editButton, resendButton;

            VH(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.history_item_text);
                timestampView = itemView.findViewById(R.id.history_item_timestamp);
                editButton = itemView.findViewById(R.id.history_edit_button);
                resendButton = itemView.findViewById(R.id.history_resend_button);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(sendButtonStateUpdater);
        
        // Stop recording if active
        if (isRecording) {
            stopRecording();
        }
        
        // Shutdown TTS
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        
        // Shutdown executor
        executor.shutdown();
    }
}
