package com.openterface.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.util.TouchPadHaptics;
import com.openterface.keymod.util.TouchPadHelpOverlay;
import com.openterface.keymod.util.TouchPadPointerPhase;
import com.openterface.keymod.util.TouchPadTipsFormatter;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class MouseFragment extends Fragment {

    private static final String TAG = "MouseFragment";
    private TouchPadView touchPad;
    public UsbSerialPort port;
    private TextView touchPadTips;
    private TextView touchPadHelpOverlay;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    private boolean isDragMode = false;

    private static final long POINTER_IDLE_AFTER_MS = 400L;
    private final Handler tipHandler = new Handler(Looper.getMainLooper());
    private TouchPadPointerPhase pointerPhase = TouchPadPointerPhase.IDLE;
    private final Runnable pointerIdleRunnable =
            () -> {
                pointerPhase = TouchPadPointerPhase.IDLE;
                updateTouchPadTips();
            };

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

    public static MouseFragment newInstance(UsbSerialPort port) {
        MouseFragment fragment = new MouseFragment();
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
        touchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    private void notePointerPhase(TouchPadPointerPhase phase) {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = phase;
        updateTouchPadTips();
        if (phase != TouchPadPointerPhase.IDLE) {
            tipHandler.postDelayed(pointerIdleRunnable, POINTER_IDLE_AFTER_MS);
        }
    }

    private void clearPointerPhaseForFingerUp() {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = TouchPadPointerPhase.IDLE;
        updateTouchPadTips();
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
                        buttonByte +
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
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending Bluetooth relative mouse data: " + e.getMessage());
                    }
                } else if (port != null) {
                    try {
                        port.write(sendKBDataBytes, 20);
                        Log.d(TAG, "Sent USB relative mouse data: " + sendMSData);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mouse, container, false);

        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        touchPadTips = view.findViewById(R.id.touchPadTips);
        updateTouchPadTips();

        touchPadHelpOverlay = view.findViewById(R.id.touchPadHelpOverlay);
        View touchPadInfo = view.findViewById(R.id.touchPadInfo);
        if (touchPadInfo != null) {
            touchPadInfo.setOnClickListener(v -> TouchPadHelpOverlay.onInfoPressed(touchPadHelpOverlay));
        }

        touchPad = view.findViewById(R.id.touchPad);
        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                    if (lastX == 0 && lastY == 0) {
                        notePointerPhase(TouchPadPointerPhase.SCROLL);
                        sendScrollData((int) startX, (int) startY);
                    } else {
                        notePointerPhase(TouchPadPointerPhase.MOVE);
                        sendHexRelData(startX, startY, lastX, lastY);
                    }
                }

                @Override
                public void onTouchClick() {
                    if (isDragMode) {
                        setDragMode(false);
                        return;
                    }
                    TouchPadHaptics.onLeftClick(touchPad.getContext());
                    Log.d(TAG, "TouchPad single tap -> left click");
                    new Thread(() -> {
                        try {
                            String data = "57AB0005050101000000";
                            data += makeChecksum(data);
                            byte[] bytes = hexStringToByteArray(data);
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
                    if (isDragMode) {
                        setDragMode(false);
                        return;
                    }
                    TouchPadHaptics.onDoubleClick(touchPad.getContext());
                    Log.d(TAG, "TouchPad double tap -> double click");
                    new Thread(() -> {
                        try {
                            String data = "57AB0005050101000000";
                            data += makeChecksum(data);
                            byte[] bytes = hexStringToByteArray(data);
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
                    TouchPadHaptics.onRightClick(touchPad.getContext());
                    Log.d(TAG, "TouchPad 2-finger tap -> right click");
                    new Thread(() -> {
                        try {
                            String data = "57AB0005050102000000";
                            data += makeChecksum(data);
                            byte[] bytes = hexStringToByteArray(data);
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
                    if (isDragMode) {
                        return;
                    }
                    TouchPadHaptics.onDragToggle(touchPad.getContext());
                    setDragMode(true);
                }

                @Override
                public void onTouchRelease() {
                    clearPointerPhaseForFingerUp();
                    // Match iOS behavior: release only when drag mode is off.
                    if (!isDragMode) {
                        releaseAllMSData();
                    }
                }
            });
            TouchPadHelpOverlay.wireDismissTouchTargets(touchPad, touchPadTips, touchPadHelpOverlay);
            if (savedInstanceState == null) {
                touchPad.post(() -> TouchPadHelpOverlay.show(touchPadHelpOverlay));
            }
        }

        return view;
    }

    public void setPort(UsbSerialPort port) {
        this.port = port;
    }

    @Override
    public void onDestroyView() {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        super.onDestroyView();
        TouchPadHelpOverlay.clear(touchPadHelpOverlay);
        setDragMode(false);
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
}
