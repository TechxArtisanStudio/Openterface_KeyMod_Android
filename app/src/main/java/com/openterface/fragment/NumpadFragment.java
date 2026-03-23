package com.openterface.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * NumpadFragment — renders a physical numpad layout (NumLk / * - / 7 8 9 + /
 * 4 5 6 / 1 2 3 Enter / 0 · ) and sends CH9329 HID keyboard packets.
 *
 * USB HID numpad keycodes (decimal):
 *   NumLock=83, /=84, *=85, -=86, +=87, Enter=88
 *   1=89, 2=90, 3=91, 4=92, 5=93, 6=94
 *   7=95, 8=96, 9=97, 0=98, .=99
 */
public class NumpadFragment extends Fragment {

    private static final String TAG = "NumpadFragment";

    // USB HID numpad key codes (decimal)
    private static final int HID_NUMLOCK   = 0x53; // 83
    private static final int HID_NP_DIVIDE = 0x54; // 84
    private static final int HID_NP_MULT   = 0x55; // 85
    private static final int HID_NP_MINUS  = 0x56; // 86
    private static final int HID_NP_PLUS   = 0x57; // 87
    private static final int HID_NP_ENTER  = 0x58; // 88
    private static final int HID_NP_1      = 0x59; // 89
    private static final int HID_NP_2      = 0x5A; // 90
    private static final int HID_NP_3      = 0x5B; // 91
    private static final int HID_NP_4      = 0x5C; // 92
    private static final int HID_NP_5      = 0x5D; // 93
    private static final int HID_NP_6      = 0x5E; // 94
    private static final int HID_NP_7      = 0x5F; // 95
    private static final int HID_NP_8      = 0x60; // 96
    private static final int HID_NP_9      = 0x61; // 97
    private static final int HID_NP_0      = 0x62; // 98
    private static final int HID_NP_DOT    = 0x63; // 99
    private static final int HID_BACKSPACE  = 0x2A; // 42

    public UsbSerialPort port;
    private ConnectionManager connectionManager;

    public static NumpadFragment newInstance(UsbSerialPort port) {
        NumpadFragment fragment = new NumpadFragment();
        fragment.port = port;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_numpad, container, false);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        bindKey(view, R.id.btn_numlock,  HID_NUMLOCK);
        bindKey(view, R.id.btn_divide,   HID_NP_DIVIDE);
        bindKey(view, R.id.btn_multiply, HID_NP_MULT);
        bindKey(view, R.id.btn_subtract, HID_NP_MINUS);
        bindKey(view, R.id.btn_7,        HID_NP_7);
        bindKey(view, R.id.btn_8,        HID_NP_8);
        bindKey(view, R.id.btn_9,        HID_NP_9);
        bindKey(view, R.id.btn_add,      HID_NP_PLUS);
        bindKey(view, R.id.btn_4,        HID_NP_4);
        bindKey(view, R.id.btn_5,        HID_NP_5);
        bindKey(view, R.id.btn_6,        HID_NP_6);
        bindKey(view, R.id.btn_1,        HID_NP_1);
        bindKey(view, R.id.btn_2,        HID_NP_2);
        bindKey(view, R.id.btn_3,        HID_NP_3);
        bindKey(view, R.id.btn_enter,     HID_NP_ENTER);
        bindKey(view, R.id.btn_0,         HID_NP_0);
        bindKey(view, R.id.btn_dot,       HID_NP_DOT);
        bindKey(view, R.id.btn_backspace, HID_BACKSPACE);

        return view;
    }

    private void bindKey(View root, int viewId, int hidCode) {
        Button btn = root.findViewById(viewId);
        if (btn == null) return;
        btn.setOnClickListener(v -> sendKey(hidCode));
    }

    private void sendKey(int hidCode) {
        Log.d(TAG, "Numpad key pressed: 0x" + Integer.toHexString(hidCode));
        new Thread(() -> {
            if (connectionManager != null) {
                connectionManager.sendKeyEvent(0, hidCode);
                try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                connectionManager.sendKeyRelease();
            } else if (port != null) {
                // Fallback: build and send CH9329 packet directly
                try {
                    String data = String.format("57AB000208%02X00%02X0000000000", 0, hidCode);
                    data += makeChecksum(data);
                    port.write(hexToBytes(data), 20);
                    Thread.sleep(30);
                    String release = "57AB00020800000000000000000C";
                    port.write(hexToBytes(release), 20);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending numpad key: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "No connection available for numpad key 0x" + Integer.toHexString(hidCode));
            }
        }).start();
    }

    private static String makeChecksum(String hex) {
        int total = 0;
        for (int i = 0; i < hex.length(); i += 2) {
            total += Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return String.format("%02X", total % 256);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
