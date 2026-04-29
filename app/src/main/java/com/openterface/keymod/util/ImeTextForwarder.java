package com.openterface.keymod.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.R;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Forwards {@link EditText} changes to the host PC as HID keystrokes via {@link HidTextKeystrokeSender}.
 */
public final class ImeTextForwarder {

    private static final int HID_BACKSPACE = 0x2A;

    public interface ConnectionSupplier {
        @Nullable ConnectionManager get();
    }

    public interface TargetOsSupplier {
        @Nullable String get();
    }

    private ImeTextForwarder() {
    }

    public static void attach(
            EditText edit,
            ConnectionSupplier connectionSupplier,
            TargetOsSupplier targetOsSupplier,
            Executor executor
    ) {
        detach(edit);
        AtomicReference<String> last = new AtomicReference<>(edit.getText() != null ? edit.getText().toString() : "");
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String now = s.toString();
                String prev = last.get();
                if (now.equals(prev)) {
                    return;
                }
                last.set(now);
                executor.execute(() -> {
                    try {
                        applyDiff(prev, now, connectionSupplier, targetOsSupplier);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        };
        edit.addTextChangedListener(watcher);
        edit.setTag(R.id.tag_ime_text_forwarder, watcher);
    }

    public static void detach(EditText edit) {
        if (edit == null) {
            return;
        }
        Object tag = edit.getTag(R.id.tag_ime_text_forwarder);
        if (tag instanceof TextWatcher) {
            edit.removeTextChangedListener((TextWatcher) tag);
            edit.setTag(R.id.tag_ime_text_forwarder, null);
        }
    }

    private static void applyDiff(
            String oldStr,
            String newStr,
            ConnectionSupplier connectionSupplier,
            TargetOsSupplier targetOsSupplier
    ) throws InterruptedException {
        ConnectionManager cm = connectionSupplier.get();
        if (cm == null || !cm.isConnected()) {
            return;
        }
        int p = 0;
        int max = Math.min(oldStr.length(), newStr.length());
        while (p < max && oldStr.charAt(p) == newStr.charAt(p)) {
            p++;
        }
        int deletes = oldStr.length() - p;
        String inserts = newStr.substring(p);
        for (int i = 0; i < deletes; i++) {
            cm.sendKeyEvent(0, HID_BACKSPACE);
            cm.sendKeyRelease();
            Thread.sleep(12);
        }
        if (!inserts.isEmpty()) {
            String os = targetOsSupplier.get();
            String targetOs = os != null ? os : "macos";
            HidTextKeystrokeSender.send(inserts, cm, targetOs, true, null);
        }
    }
}
