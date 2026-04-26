package com.openterface.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compose mode: long text via system IME, sent as HID keystrokes (ASCII only).
 */
public class ComposeFragment extends Fragment {

    public static final int MAX_TEXT_LENGTH = 10_000;
    public UsbSerialPort port;

    private EditText body;
    private TextView asciiWarning;
    private TextView charCount;
    private Button clearButton;
    private Button sendButton;
    private CustomKeyboardView keyboardView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelSend = new AtomicBoolean(false);
    private volatile boolean sending = false;

    private final List<MainActivity.OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_compose, container, false);
        body = view.findViewById(R.id.compose_body);
        asciiWarning = view.findViewById(R.id.compose_ascii_warning);
        charCount = view.findViewById(R.id.compose_char_count);
        clearButton = view.findViewById(R.id.compose_clear_button);
        sendButton = view.findViewById(R.id.compose_send_button);
        keyboardView = view.findViewById(R.id.compose_keyboard_view);

        body.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEXT_LENGTH)});

        if (keyboardView != null) {
            keyboardView.setShortcutsStripOnly(true);
            if (port != null) {
                keyboardView.setPort(port);
            }
            registerKeyboardOsListener(keyboardView);
        }

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (sending || body == null) {
                    return;
                }
                body.setText("");
                updateComposeValidationUi();
            });
        }
        sendButton.setOnClickListener(v -> onSendOrStopClicked());

        body.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateComposeValidationUi();
            }
        });

        setupImeInsets(view);

        updateComposeValidationUi();
        return view;
    }

    /**
     * Lifts the whole Compose column above the soft keyboard. {@link DrawerLayout} + weighted
     * {@code fragment_container} often prevent {@code adjustResize} alone from shrinking this
     * fragment, so we also apply IME bottom insets as padding (see MainActivity manifest
     * {@code windowSoftInputMode}).
     */
    private void setupImeInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);
            return windowInsets;
        });
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    private void registerKeyboardOsListener(CustomKeyboardView kbd) {
        if (kbd == null || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity.OnTargetOsChangeListener listener = os -> kbd.reloadForTargetOs();
        osChangeListeners.add(listener);
        ((MainActivity) requireActivity()).addOsChangeListener(listener);
    }

    @Override
    public void onDestroyView() {
        cancelSend.set(true);
        sendExecutor.shutdownNow();
        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            for (MainActivity.OnTargetOsChangeListener l : osChangeListeners) {
                a.removeOsChangeListener(l);
            }
        }
        osChangeListeners.clear();
        body = null;
        asciiWarning = null;
        charCount = null;
        clearButton = null;
        sendButton = null;
        keyboardView = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) {
            v.post(() -> ViewCompat.requestApplyInsets(v));
        }
        updateComposeValidationUi();
    }

    private void onSendOrStopClicked() {
        if (sending) {
            cancelSend.set(true);
            return;
        }

        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity ma = (MainActivity) requireActivity();
        ConnectionManager cm = ma.getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            Toast.makeText(requireContext(), R.string.compose_no_connection, Toast.LENGTH_SHORT).show();
            return;
        }

        String text = body.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), R.string.compose_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (containsNonAscii(text)) {
            Toast.makeText(requireContext(), R.string.compose_ascii_warning, Toast.LENGTH_LONG).show();
            return;
        }

        cancelSend.set(false);
        sending = true;
        body.setEnabled(false);
        if (keyboardView != null) {
            keyboardView.setEnabled(false);
        }
        updateComposeValidationUi();

        final String targetOs = ma.getTargetOs();
        final int sentLen = text.length();

        sendExecutor.execute(() -> {
            HidTextKeystrokeSender.Result result;
            try {
                result = HidTextKeystrokeSender.send(text, cm, targetOs, false, cancelSend);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = HidTextKeystrokeSender.Result.CANCELLED;
            }
            HidTextKeystrokeSender.Result finalResult = result;
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                sending = false;
                body.setEnabled(true);
                if (keyboardView != null) {
                    keyboardView.setEnabled(true);
                }
                updateComposeValidationUi();
                if (finalResult == HidTextKeystrokeSender.Result.CANCELLED) {
                    Toast.makeText(requireContext(), R.string.compose_cancelled, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.compose_sent, sentLen), Toast.LENGTH_SHORT)
                            .show();
                }
            });
        });
    }

    private static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp > 127) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private void updateComposeValidationUi() {
        if (body == null || sendButton == null || asciiWarning == null || charCount == null) {
            return;
        }
        String t = body.getText().toString();
        boolean bad = containsNonAscii(t);
        asciiWarning.setVisibility(bad ? View.VISIBLE : View.GONE);
        charCount.setText(getString(R.string.compose_char_count, t.length(), MAX_TEXT_LENGTH));

        boolean connected = false;
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            connected = cm != null && cm.isConnected();
        }

        if (clearButton != null) {
            boolean canClear = !sending && !t.isEmpty();
            clearButton.setEnabled(canClear);
            clearButton.setAlpha(canClear ? 1f : 0.45f);
        }

        if (sending) {
            sendButton.setText(R.string.compose_stop);
            sendButton.setEnabled(true);
            sendButton.setAlpha(1f);
        } else {
            sendButton.setText(R.string.compose_send);
            boolean canSend = connected && !bad && !t.isEmpty();
            sendButton.setEnabled(canSend);
            sendButton.setAlpha(canSend ? 1f : 0.45f);
        }
    }
}
