package com.openterface.keymod;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * HID Sender - Sends keyboard/mouse/gamepad HID events via USB or Bluetooth
 * Implements CH9329 protocol for HID over serial
 */
public class HIDSender {

    private static final String TAG = "HIDSender";
    private static final byte[] HID_HEADER = {(byte) 0x57, (byte) 0xAB, 0x00, 0x02, 0x08};

    /**
     * Send keyboard event via USB or Bluetooth
     */
    public static void sendKeyEvent(UsbSerialPort usbPort, BluetoothService bluetoothService, 
                                    int modifiers, int keyCode) {
        if (keyCode == 0) return;
        
        byte[] data = buildKeyboardPacket(modifiers, keyCode);
        sendPacket(usbPort, bluetoothService, data, "Keyboard");
    }

    /**
     * Send key release event
     */
    public static void sendKeyRelease(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        byte[] data = buildKeyboardPacket(0, 0);
        sendPacket(usbPort, bluetoothService, data, "Key Release");
    }

    /**
     * Send mouse movement event
     */
    public static void sendMouseMovement(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                         int deltaX, int deltaY, int buttons) {
        byte[] data = buildMousePacket(deltaX, deltaY, buttons);
        sendPacket(usbPort, bluetoothService, data, "Mouse Move");
    }

    /**
     * Send mouse click event
     */
    public static void sendMouseClick(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                      int buttons, boolean press) {
        if (press) {
            sendMouseMovement(usbPort, bluetoothService, 0, 0, buttons);
        } else {
            sendMouseMovement(usbPort, bluetoothService, 0, 0, 0);
        }
    }

    /**
     * Send gamepad event (with analog stick positions)
     */
    public static void sendGamepadEvent(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                        int buttons, int leftX, int leftY, int rightX, int rightY) {
        // For now, send as keyboard events - full gamepad protocol TBD
        Log.d(TAG, "Gamepad event: buttons=" + buttons + ", sticks=(" + leftX + "," + leftY + "),(" + rightX + "," + rightY + ")");
    }

    /**
     * Send raw packet via USB or Bluetooth
     */
    public static void sendPacket(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                  byte[] data, String type) {
        // USB path
        if (usbPort != null) {
            try {
                // Split into 20-byte chunks for CH9329
                int offset = 0;
                while (offset < data.length) {
                    int chunkSize = Math.min(20, data.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(data, offset, chunk, 0, chunkSize);
                    
                    usbPort.write(chunk, 100);
                    Thread.sleep(10);
                    offset += chunkSize;
                }
                Log.d(TAG, "Sent " + type + " via USB: " + byteArrayToHexString(data));
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error sending USB data: " + e.getMessage());
            }
        }
        // Bluetooth path
        else if (bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(data);
            Log.d(TAG, "Sent " + type + " via BLE: " + byteArrayToHexString(data));
        } else {
            Log.w(TAG, "No connection available for sending " + type);
        }
    }

    /**
     * Build keyboard HID packet
     */
    private static byte[] buildKeyboardPacket(int modifiers, int keyCode) {
        byte[] data = new byte[16];
        System.arraycopy(HID_HEADER, 0, data, 0, 5);
        data[5] = (byte) modifiers;
        data[6] = 0; // Reserved
        data[7] = (byte) keyCode;
        data[8] = 0; // Key 2
        data[9] = 0; // Key 3
        data[10] = 0; // Key 4
        data[11] = 0; // Key 5
        data[12] = 0; // Key 6
        data[13] = 0; // Reserved
        data[14] = 0; // Reserved
        data[15] = calculateChecksum(data);
        return data;
    }

    /**
     * Build mouse HID packet
     */
    private static byte[] buildMousePacket(int deltaX, int deltaY, int buttons) {
        byte[] data = new byte[16];
        System.arraycopy(HID_HEADER, 0, data, 0, 5);
        data[5] = 0x01; // Mouse report ID
        data[6] = (byte) buttons;
        data[7] = (byte) deltaX;
        data[8] = (byte) deltaY;
        data[9] = 0; // Wheel X
        data[10] = 0; // Wheel Y
        data[11] = 0; // Reserved
        data[12] = 0; // Reserved
        data[13] = 0; // Reserved
        data[14] = 0; // Reserved
        data[15] = calculateChecksum(data);
        return data;
    }

    /**
     * Calculate packet checksum
     */
    private static byte calculateChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length - 1; i++) {
            sum += (data[i] & 0xFF);
        }
        return (byte) (sum & 0xFF);
    }

    /**
     * Convert byte array to hex string (for debugging)
     */
    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
