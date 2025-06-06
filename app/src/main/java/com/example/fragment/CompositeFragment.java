package com.example.fragment;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dual_modekeyboard.BluetoothService;
import com.example.dual_modekeyboard.CustomKeyboardView;
import com.example.dual_modekeyboard.R;
import com.example.dual_modekeyboard.TouchPadView;
import com.example.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class CompositeFragment extends Fragment {

    private static final String TAG = "CompositeFragment";
    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    public UsbSerialPort port;
    private Button leftClickButton, rightClickButton;
    private ImageButton slideUpButton, slideDownButton;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;

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
        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float startMoveMSX, float startMoveMSY, float lastMoveMSX, float lastMoveMSY) {
                    Log.d(TAG, "TouchPad Move: " + startMoveMSX + ", " + startMoveMSY + ", " + lastMoveMSX + ", " + lastMoveMSY);
                    sendHexRelData(startMoveMSX, startMoveMSY, lastMoveMSX, lastMoveMSY);
                }

                @Override
                public void onTouchClick() {
                    Log.d(TAG, "TouchPad Click");
                }

                @Override
                public void onTouchDoubleClick() {
                    Log.d(TAG, "TouchPad Double Click");
                }

                @Override
                public void onTouchRightClick() {
                    Log.d(TAG, "TouchPad Right Click");
                }
            });
        }

        return view;
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
