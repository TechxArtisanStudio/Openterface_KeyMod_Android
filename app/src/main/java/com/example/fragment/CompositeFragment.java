package com.example.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dual_modekeyboard.CustomKeyboardView;
import com.example.dual_modekeyboard.R;
import com.example.dual_modekeyboard.TouchPadView;
import com.hoho.android.usbserial.driver.UsbSerialPort;

public class CompositeFragment extends Fragment {

    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    public UsbSerialPort port;

    public static CompositeFragment newInstance(UsbSerialPort port) {
        CompositeFragment fragment = new CompositeFragment();
        fragment.port = port;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_composite, container, false);

        keyboardView = view.findViewById(R.id.keyboard_view);
        touchPad = view.findViewById(R.id.touchPad);
        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float deltaX, float deltaY) {
                    System.out.println("this is touchpad");
                    Log.d("TouchPad", "Move: " + deltaX + ", " + deltaY);
                }

                @Override
                public void onTouchClick() {
                    Log.d("TouchPad", "Click");
                }

                @Override
                public void onTouchDoubleClick() {
                    Log.d("TouchPad", "Double Click");
                }

                @Override
                public void onTouchRightClick() {
                    Log.d("TouchPad", "Right Click");
                }
            });
        }

        return view;
    }
}