package com.openterface.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class CompositeFragment extends Fragment {

    private static final String TAG = "CompositeFragment";
    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    private LinearLayout rootLayout;
    private LinearLayout touchpadSection;
    private LinearLayout toggleHandle;
    private View toggleHandlePill;
    private TextView touchPadTips;
    public UsbSerialPort port;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    private boolean isDragMode = false;

    private enum DisplayMode { BOTH, KEYBOARD, TOUCHPAD }
    private DisplayMode displayMode = DisplayMode.BOTH;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Bound to BluetoothService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    public static CompositeFragment newInstance(UsbSerialPort port) {
        CompositeFragment fragment = new CompositeFragment();
        fragment.port = port;
        return fragment;
    }

    public static String makeChecksum(String data) {
        int total = 0;

        for (int i = 0; i < data.length(); i += 2) {
            String byteStr = data.substring(i, Math.min(i + 2, data.length()));
            total += Integer.parseInt(byteStr, 16);
        }

        int mod = total % 256;

        return String.format("%02X", mod);
    }

    public static void checkSendLogData(String sendKBData) {
        StringBuilder check_send_data = new StringBuilder();
        for (int i = 0; i < sendKBData.length(); i += 2) {
            if (i + 2 <= sendKBData.length()) {
                check_send_data.append(sendKBData.substring(i, i + 2)).append(" ");
            } else {
                check_send_data.append(sendKBData.substring(i)).append(" ");
            }
        }
        Log.d(TAG, "sendKBData: " + check_send_data.toString().trim());
    }

    public static byte[] hexStringToByteArray(String ByteData) {
        if (ByteData.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }

        int len = ByteData.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(ByteData.charAt(i), 16) << 4)
                    + Character.digit(ByteData.charAt(i + 1), 16));
        }
        return data;
    }

    private void releaseAllMSData() {
        String releaseSendMSData = "57AB00050501000000000D";
        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                bluetoothService.sendData(releaseSendKBDataBytes);
                Log.d(TAG, "Sent Bluetooth release data");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error sending Bluetooth release data: " + e.getMessage());
            }
        } else if (port != null) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                port.write(releaseSendKBDataBytes, 20);
                Log.d(TAG, "Sent USB release data");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error sending USB release data: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "No connection available for release data");
        }
    }

    private void setDragMode(boolean enabled) {
        isDragMode = enabled;
        sendMouseButtonState(enabled ? 0x01 : 0x00);
        updateTouchPadTips();
        Log.d(TAG, "Drag mode " + (enabled ? "ON" : "OFF"));
    }

    private void updateTouchPadTips() {
        if (touchPadTips == null) return;
        String status = isDragMode ? "Drag Mode ON" : "Drag Mode OFF";
        String tips = "Touch Pad\n"
                + status + "\n"
                + "Single tap -> Click\n"
                + "Double tap -> Double click\n"
                + "Two finger tap -> Right click\n"
                + "Two finger drag -> Scroll\n"
                + "Long press -> Toggle drag mode";
        touchPadTips.setText(tips);
    }

    private void sendMouseButtonState(int buttonMask) {
        new Thread(() -> {
            try {
                String buttonByte = String.format("%02X", buttonMask & 0xFF);
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        buttonByte + "00" + "00" + "00";
                sendMSData += makeChecksum(sendMSData);
                byte[] bytes = hexStringToByteArray(sendMSData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending mouse button state: " + e.getMessage());
            }
        }).start();
    }

    public void sendHexRelData(float StartMoveMSX, float StartMoveMSY, float LastMoveMSX, float LastMoveMSY) {
        new Thread(() -> {
            try {
                int xMovement = (int) (StartMoveMSX - LastMoveMSX);
                int yMovement = (int) (StartMoveMSY - LastMoveMSY);

                if (Math.abs(xMovement) < 2 && Math.abs(yMovement) < 2) {
                    return;
                }

                String xByte;
                if (xMovement == 0) {
                    xByte = "00";
                } else if (LastMoveMSX == 0) {
                    xByte = "00";
                } else if (xMovement > 0) {
                    xByte = String.format("%02X", Math.min(xMovement, 0x7F));
                } else {
                    xByte = String.format("%02X", 0x100 + xMovement);
                }

                String yByte;
                if (yMovement == 0) {
                    yByte = "00";
                } else if (LastMoveMSY == 0) {
                    yByte = "00";
                } else if (yMovement > 0) {
                    yByte = String.format("%02X", Math.min(yMovement, 0x7F));
                } else {
                    yByte = String.format("%02X", 0x100 + yMovement);
                }

                String buttonByte = isDragMode ? "01" : CH9329MSKBMap.MSAbsData().get("SecNullData");
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                                CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                                CH9329MSKBMap.getKeyCodeMap().get("address") +
                                CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                                CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                                CH9329MSKBMap.MSRelData().get("FirstData") +
                        buttonByte + // MS key
                                xByte +
                                yByte +
                                CH9329MSKBMap.DataNull().get("DataNull");

                sendMSData = sendMSData + makeChecksum(sendMSData);

                if (sendMSData.length() % 2 != 0) {
                    sendMSData += "0";
                }
                checkSendLogData(sendMSData);

                byte[] sendKBDataBytes = hexStringToByteArray(sendMSData);

                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    try {
                        bluetoothService.sendData(sendKBDataBytes);
                        Log.d(TAG, "Sent Bluetooth relative mouse data: " + sendMSData);
//                        releaseAllMSData();
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending Bluetooth relative mouse data: " + e.getMessage());
                    }
                } else if (port != null) {
                    try {
                        port.write(sendKBDataBytes, 20);
                        Log.d(TAG, "Sent USB relative mouse data: " + sendMSData);
//                        releaseAllMSData();
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending USB relative mouse data: " + e.getMessage());
                    }
                } else {
                    Log.w(TAG, "No connection available for relative mouse data");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing relative mouse data: " + e.getMessage());
            }
        }).start();
    }

    /** Send a scroll-wheel packet. deltaY>0 scrolls up, deltaY<0 scrolls down (natural). */
    public void sendScrollData(int deltaX, int deltaY) {
        new Thread(() -> {
            try {
                // Skip empty scroll events.
                if (deltaX == 0 && deltaY == 0) return;

                String base =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        "00"; // no button

                // Vertical scroll (wheel byte)
                if (deltaY != 0) {
                    String wheelByte = deltaY > 0
                            ? String.format("%02X", Math.min(deltaY, 0x7F))
                            : String.format("%02X", 0x100 + Math.max(deltaY, -0x7F));
                    String packet = base + "00" + "00" + wheelByte;
                    packet += makeChecksum(packet);
                    byte[] bytes = hexStringToByteArray(packet);
                    if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.sendData(bytes);
                    } else if (port != null) {
                        port.write(bytes, 20);
                    }
                }

                // Horizontal scroll fallback (put deltaX in X byte, wheel=0), matching iOS behavior.
                if (deltaX != 0) {
                    int boundedX = Math.max(-127, Math.min(127, deltaX));
                    String xByte = boundedX >= 0
                            ? String.format("%02X", boundedX)
                            : String.format("%02X", 0x100 + boundedX);
                    String packet = base + xByte + "00" + "00";
                    packet += makeChecksum(packet);
                    byte[] bytes = hexStringToByteArray(packet);
                    if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.sendData(bytes);
                    } else if (port != null) {
                        port.write(bytes, 20);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending scroll data: " + e.getMessage());
            }
        }).start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_composite, container, false);

        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        rootLayout = view.findViewById(R.id.composite_root);
        keyboardView = view.findViewById(R.id.keyboard_view);
        touchPad = view.findViewById(R.id.touchPad);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        toggleHandle = view.findViewById(R.id.toggle_handle);
        toggleHandlePill = view.findViewById(R.id.toggle_handle_pill);
        touchPadTips = view.findViewById(R.id.touchPadTips);
        updateTouchPadTips();

        applyOrientationLayout();
        applyDisplayMode();

        if (toggleHandle != null) {
            toggleHandle.setOnClickListener(v -> cycleDisplayMode());
        }

        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                    // 2-finger scroll is signalled with lastX==0 && lastY==0
                    if (lastX == 0 && lastY == 0) {
                        Log.d(TAG, "TouchPad 2-finger scroll: dx=" + startX + " dy=" + startY);
                        sendScrollData((int) startX, (int) startY);
                    } else {
                        Log.d(TAG, "TouchPad Move: " + startX + ", " + startY + " -> " + lastX + ", " + lastY);
                        sendHexRelData(startX, startY, lastX, lastY);
                    }
                }

                @Override
                public void onTouchClick() {
                    Log.d(TAG, "TouchPad single tap -> left click");
                    new Thread(() -> {
                        try {
                            String sendKBData = "57AB0005050101000000";
                            sendKBData += makeChecksum(sendKBData);
                            byte[] bytes = hexStringToByteArray(sendKBData);
                            if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                                bluetoothService.sendData(bytes);
                            } else if (port != null) {
                                port.write(bytes, 20);
                            }
                            Thread.sleep(30);
                            releaseAllMSData();
                        } catch (IOException | InterruptedException e) {
                            Log.e(TAG, "Error sending tap left click: " + e.getMessage());
                        }
                    }).start();
                }

                @Override
                public void onTouchDoubleClick() {
                    Log.d(TAG, "TouchPad double tap -> double click");
                    new Thread(() -> {
                        try {
                            String clickData = "57AB0005050101000000";
                            clickData += makeChecksum(clickData);
                            byte[] bytes = hexStringToByteArray(clickData);
                            for (int i = 0; i < 2; i++) {
                                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                                    bluetoothService.sendData(bytes);
                                } else if (port != null) {
                                    port.write(bytes, 20);
                                }
                                Thread.sleep(30);
                                releaseAllMSData();
                                Thread.sleep(30);
                            }
                        } catch (IOException | InterruptedException e) {
                            Log.e(TAG, "Error sending double click: " + e.getMessage());
                        }
                    }).start();
                }

                @Override
                public void onTouchRightClick() {
                    Log.d(TAG, "TouchPad 2-finger tap -> right click");
                    new Thread(() -> {
                        try {
                            String sendKBData = "57AB0005050102000000";
                            sendKBData += makeChecksum(sendKBData);
                            byte[] bytes = hexStringToByteArray(sendKBData);
                            if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                                bluetoothService.sendData(bytes);
                            } else if (port != null) {
                                port.write(bytes, 20);
                            }
                            Thread.sleep(30);
                            releaseAllMSData();
                        } catch (IOException | InterruptedException e) {
                            Log.e(TAG, "Error sending right click: " + e.getMessage());
                        }
                    }).start();
                }

                @Override
                public void onTouchLongPress() {
                    setDragMode(!isDragMode);
                }

                @Override
                public void onTouchRelease() {
                    // Match iOS behavior: release only when drag mode is off.
                    if (!isDragMode) {
                        releaseAllMSData();
                    }
                }
            });
        }

        return view;
    }

    private void cycleDisplayMode() {
        switch (displayMode) {
            case BOTH:     displayMode = DisplayMode.KEYBOARD;  break;
            case KEYBOARD: displayMode = DisplayMode.TOUCHPAD;  break;
            case TOUCHPAD: displayMode = DisplayMode.BOTH;      break;
        }
        applyDisplayMode();
    }

    private void applyDisplayMode() {
        if (touchpadSection != null) {
            touchpadSection.setVisibility(
                displayMode != DisplayMode.KEYBOARD ? View.VISIBLE : View.GONE);
        }
        if (keyboardView != null) {
            keyboardView.setVisibility(
                displayMode != DisplayMode.TOUCHPAD ? View.VISIBLE : View.GONE);

            // Ensure we always use the correct orientation keyboard keyset when mode changes.
            keyboardView.reloadForCurrentOrientation();
            keyboardView.setShowExtraPortraitKeys(displayMode == DisplayMode.KEYBOARD);
        }
    }

    private void applyOrientationLayout() {
        if (rootLayout == null || touchpadSection == null || toggleHandle == null || keyboardView == null) {
            return;
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            rootLayout.setOrientation(LinearLayout.HORIZONTAL);

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    dpToPx(36), ViewGroup.LayoutParams.MATCH_PARENT));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 2.0f));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), dpToPx(40)));
            }
        } else {
            rootLayout.setOrientation(LinearLayout.VERTICAL);

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.0f));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44)));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(4)));
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationLayout();
        applyDisplayMode();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setDragMode(false);
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
}
