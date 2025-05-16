package com.example.fragment;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dual_modekeyboard.CustomKeyboardView;
import com.example.dual_modekeyboard.R;
import com.example.dual_modekeyboard.TouchPadView;
import com.example.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class CompositeFragment extends Fragment {

    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    public UsbSerialPort port;
    private Button leftClickButton, rightClickButton;
    private ImageButton slideUpButton, slideDownButton;

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

    public static void sendHexRelData(float StartMoveMSX, float StartMoveMSY, float LastMoveMSX, float LastMoveMSY, UsbSerialPort port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int xMovement = (int) (StartMoveMSX - LastMoveMSX);
                    int yMovement = (int) (StartMoveMSY - LastMoveMSY);

                    if (Math.abs(xMovement) < 2 && Math.abs(yMovement) < 2) {
                        return;
                    }

                    String xByte;
                    if (xMovement == 0) {
                        xByte = "00";
                    }else if(LastMoveMSX == 0){
                        xByte = "00";
                    } else if (xMovement > 0) {
                        xByte = String.format("%02X", Math.min(xMovement, 0x7F));
                    } else {
                        xByte = String.format("%02X", 0x100 + xMovement);
                    }

                    String yByte;
                    if (yMovement == 0) {
                        yByte = "00";
                    }else if(LastMoveMSY == 0){
                        yByte = "00";
                    } else if (yMovement > 0) {
                        yByte = String.format("%02X", Math.min(yMovement, 0x7F));
                    } else {
                        yByte = String.format("%02X", 0x100 + yMovement);
                    }

                    String sendMSData = "";
                    sendMSData =
                            CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                                    CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                                    CH9329MSKBMap.getKeyCodeMap().get("address") +
                                    CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                                    CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                                    CH9329MSKBMap.MSRelData().get("FirstData") +
                                    CH9329MSKBMap.MSAbsData().get("SecNullData") + //MS key
                                    xByte +
                                    yByte +
                                    CH9329MSKBMap.DataNull().get("DataNull");

                    sendMSData = sendMSData + makeChecksum(sendMSData);

                    if (sendMSData.length() % 2 != 0) {
                        sendMSData += "0";
                    }
                    checkSendLogData(sendMSData);

                    byte[] sendKBDataBytes = hexStringToByteArray(sendMSData);

                    try {
                        port.write(sendKBDataBytes, 20);
//                        Log.d(TAG, "send sendHexRelData successful");
                    } catch (IOException e) {
//                        Log.e(TAG, "Error writing to port: " + e.getMessage());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void releaseAllMSData(){
        String releaseSendMSData = "57AB00050501000000000D";
        byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
        try {
            Thread.sleep(10);
            port.write(releaseSendKBDataBytes, 20);
            System.out.println("Successfully sent release data");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_composite, container, false);
        leftClickButton = view.findViewById(R.id.leftClickButton);
        rightClickButton = view.findViewById(R.id.rightClickButton);
        slideDownButton = view.findViewById(R.id.slideDownButton);
        slideUpButton = view.findViewById(R.id.slideUpButton);

        leftClickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle left click button action
                Log.d("LeftClickButton", "Left Click Button Pressed");
                // Add your left click action here
                try {
                    String sendKBData = String.format("57AB0005050101000000");
                    sendKBData += makeChecksum(sendKBData);
                    byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                    port.write(sendKBDataBytes, 20);
                    releaseAllMSData();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        rightClickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle left click button action
                Log.d("rightClickButton", "Right Click Button Pressed");
                // Add your left click action here
                try {
                    String sendKBData = String.format("57AB0005050102000000");
                    sendKBData += makeChecksum(sendKBData);
                    byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                    port.write(sendKBDataBytes, 20);
                    releaseAllMSData();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        slideDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle left click button action
                Log.d("slideDownButton", "slideDown Click Button Pressed");
                // Add your left click action here
                try {
                    String sendKBData = String.format("57AB00050501000000FF");
                    sendKBData += makeChecksum(sendKBData);
                    byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                    port.write(sendKBDataBytes, 20);
                    releaseAllMSData();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        slideUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle left click button action
                Log.d("slideUpButton", "slideUp Click Button Pressed");
                // Add your left click action here
                try {
                    String sendKBData = String.format("57AB0005050100000001");
                    sendKBData += makeChecksum(sendKBData);
                    byte[] sendKBDataBytes = hexStringToByteArray(sendKBData);
                    port.write(sendKBDataBytes, 20);
                    releaseAllMSData();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
                    System.out.println("this is touchpad");
                    Log.d("TouchPad", "Move: " + startMoveMSX + ", " + startMoveMSY + ", " + lastMoveMSX + ", " + lastMoveMSY);
                    sendHexRelData(startMoveMSX, startMoveMSY, lastMoveMSX, lastMoveMSY, port);
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