package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.ArrayList;
import java.util.List;

public class KeyboardFragment extends Fragment {

    private CustomKeyboardView keyboardView;
    public UsbSerialPort port;
    private final List<MainActivity.OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();

    public static KeyboardFragment newInstance(UsbSerialPort port) {
        KeyboardFragment fragment = new KeyboardFragment();
        fragment.port = port;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_keyboard, container, false);
        keyboardView = view.findViewById(R.id.keyboard_view);
        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }
        registerKeyboardOsListener(keyboardView);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (requireActivity() instanceof MainActivity) {
            for (MainActivity.OnTargetOsChangeListener listener : osChangeListeners) {
                ((MainActivity) requireActivity()).removeOsChangeListener(listener);
            }
            osChangeListeners.clear();
        }
    }

    private void registerKeyboardOsListener(CustomKeyboardView kbdView) {
        if (kbdView == null || !(requireActivity() instanceof MainActivity)) return;
        MainActivity.OnTargetOsChangeListener listener = os -> kbdView.reloadForTargetOs();
        osChangeListeners.add(listener);
        ((MainActivity) requireActivity()).addOsChangeListener(listener);
    }
}