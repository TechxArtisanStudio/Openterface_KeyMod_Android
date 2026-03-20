package com.openterface.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

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
    private LinearLayout touchpadSection;
    private LinearLayout toggleHandle;
    public UsbSerialPort port;
    private Button leftClickButton, rightClickButton;
    private ImageButton slideUpButton, slideDownButton;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;

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

                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                                CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                                CH9329MSKBMap.getKeyCodeMap().get("address") +
                                CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                                CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                                CH9329MSKBMap.MSRelData().get("FirstData") +
                                CH9329MSKBMap.MSAbsData().get("SecNullData") + // MS key
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
                // CH9329 relative mouse: byte[9] = scroll wheel
                String scrollByte = deltaY == 0 ? "00"
                        : deltaY > 0 ? String.format("%02X", Math.min(deltaY, 0x7F))
                        : String.format("%02X", 0x100 + Math.max(deltaY, -0x7F));
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        "00" + // no button
                        "00" + // x
                        "00" + // y
                        scrollByte;
                sendMSData += makeChecksum(sendMSData);
                byte[] bytes = hexStringToByteArray(sendMSData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
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

        leftClickButton = view.findViewById(R.id.leftClickButton);
        rightClickButton = view.findViewById(R.id.rightClickButton);
        slideDownButton = view.findViewById(R.id.slideDownButton);
        slideUpButton = view.findViewById(R.id.slideUpButton);

        leftClickButton.setOnClickListener(v -> {
            Log.d(TAG, "Left Click Button Pressed");
            try {
                String sendKBData = "57AB0005050101000000";
                sendKBData += makeChecksum(sendKBData);
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(sendKBDataBytes);
                    Log.d(TAG, "Sent Bluetooth left click data: " + sendKBData);
                } else if (port != null) {
                    port.write(sendKBDataBytes, 20);
                    Log.d(TAG, "Sent USB left click data: " + sendKBData);
                } else {
                    Log.w(TAG, "No connection available for left click");
                }
//                releaseAllMSData();
            } catch (IOException e) {
                Log.e(TAG, "Error sending left click data: " + e.getMessage());
            }
        });

        rightClickButton.setOnClickListener(v -> {
            Log.d(TAG, "Right Click Button Pressed");
            try {
                String sendKBData = "57AB0005050102000000";
                sendKBData += makeChecksum(sendKBData);
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(sendKBDataBytes);
                    Log.d(TAG, "Sent Bluetooth right click data: " + sendKBData);
                } else if (port != null) {
                    port.write(sendKBDataBytes, 20);
                    Log.d(TAG, "Sent USB right click data: " + sendKBData);
                } else {
                    Log.w(TAG, "No connection available for right click");
                }
//                releaseAllMSData();
            } catch (IOException e) {
                Log.e(TAG, "Error sending right click data: " + e.getMessage());
            }
        });

        slideDownButton.setOnClickListener(v -> {
            Log.d(TAG, "Slide Down Button Pressed");
            try {
                String sendKBData = "57AB00050501000000FF";
                sendKBData += makeChecksum(sendKBData);
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(sendKBDataBytes);
                    Log.d(TAG, "Sent Bluetooth slide down data: " + sendKBData);
                } else if (port != null) {
                    port.write(sendKBDataBytes, 20);
                    Log.d(TAG, "Sent USB slide down data: " + sendKBData);
                } else {
                    Log.w(TAG, "No connection available for slide down");
                }
//                releaseAllMSData();
            } catch (IOException e) {
                Log.e(TAG, "Error sending slide down data: " + e.getMessage());
            }
        });

        slideUpButton.setOnClickListener(v -> {
            Log.d(TAG, "Slide Up Button Pressed");
            try {
                String sendKBData = "57AB0005050100000001";
                sendKBData += makeChecksum(sendKBData);
                byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(sendKBDataBytes);
                    Log.d(TAG, "Sent Bluetooth slide up data: " + sendKBData);
                } else if (port != null) {
                    port.write(sendKBDataBytes, 20);
                    Log.d(TAG, "Sent USB slide up data: " + sendKBData);
                } else {
                    Log.w(TAG, "No connection available for slide up");
                }
//                releaseAllMSData();
            } catch (IOException e) {
                Log.e(TAG, "Error sending slide up data: " + e.getMessage());
            }
        });

        keyboardView = view.findViewById(R.id.keyboard_view);
        touchPad = view.findViewById(R.id.touchPad);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        toggleHandle = view.findViewById(R.id.toggle_handle);

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
                    Log.d(TAG, "TouchPad long press -> drag mode (no-op placeholder)");
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
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
}
