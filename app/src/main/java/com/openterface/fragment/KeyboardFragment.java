package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.R;
import com.hoho.android.usbserial.driver.UsbSerialPort;

public class KeyboardFragment extends Fragment {

    private CustomKeyboardView keyboardView;
    public UsbSerialPort port;

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
        return view;
    }
}