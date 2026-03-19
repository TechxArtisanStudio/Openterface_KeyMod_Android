package com.openterface.fragment;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    // UI Components
    private Button recordButton;
    private Button pasteButton;
    private Button clearButton;
    private EditText transcribedText;
    private TextView statusText;
    private ProgressBar progressBar;
    private ImageView waveformView;

    // State
    private boolean isRecording = false;
    private boolean isProcessing = false;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_input, container, false);

        prefs = requireContext().getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        initializeTTS();

        return view;
    }

    private void initializeViews(View view) {
        recordButton = view.findViewById(R.id.record_button);
        pasteButton = view.findViewById(R.id.paste_button);
        clearButton = view.findViewById(R.id.clear_button);
        transcribedText = view.findViewById(R.id.transcribed_text);
        statusText = view.findViewById(R.id.status_text);
        progressBar = view.findViewById(R.id.progress_bar);
        waveformView = view.findViewById(R.id.waveform_view);
    }

    private void loadSettings() {
        apiKey = prefs.getString("whisper_api_key", "");
        language = prefs.getString("voice_language", "en");
        
        Log.d(TAG, "Loaded settings - API Key: " + (apiKey.isEmpty() ? "not set" : "set") + 
              ", Language: " + language);
    }

    private void setupListeners() {
        // Record button
        recordButton.setOnClickListener(v -> {
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
            String text = transcribedText.getText().toString().trim();
            if (!text.isEmpty()) {
                sendTextAsKeystrokes(text);
                Toast.makeText(getContext(), "Sent: " + text.length() + " characters", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No text to send", Toast.LENGTH_SHORT).show();
            }
        });

        // Clear button
        clearButton.setOnClickListener(v -> {
            transcribedText.setText("");
            statusText.setText("Ready");
        });
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
            } else {
                Toast.makeText(getContext(), "Microphone permission required", Toast.LENGTH_LONG).show();
            }
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
            recordButton.setText("⏹ Stop");
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
        recordButton.setText("🎤 Record");
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
                        transcribedText.setText(result);
                        statusText.setText("✅ Transcribed");
                        Log.d(TAG, "Transcription: " + result);
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
        // TODO: Integrate with ConnectionManager to send actual keystrokes
        // For now, just show toast
        Toast.makeText(getContext(), "Would send: " + text, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Sending text: " + text);
        
        // Future implementation:
        // For each character in text, send KeyEvent via ConnectionManager
        // Need to handle special characters, spaces, etc.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
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
