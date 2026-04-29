package com.openterface.keymod;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Centralized connection manager for handling USB and BLE connections
 */
public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static final String PREFS_NAME = "ConnectionPrefs";
    private static final String KEY_LAST_CONNECTION_TYPE = "last_connection_type";
    private static final String KEY_LAST_BLE_DEVICE_MAC = "last_ble_device_mac";
    private static final String KEY_LAST_BLE_DEVICE_NAME = "last_ble_device_name";
    private static final String KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled";
    private static final String ACTION_USB_PERMISSION = "com.openterface.ch32v208serial.USB_PERMISSION";
    private static final int AUTO_CONNECT_TIMEOUT_MS = 5000;

    public enum ConnectionType {
        NONE,
        USB,
        BLUETOOTH
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private final Context context;
    private final SharedPreferences prefs;
    private ConnectionType currentConnectionType = ConnectionType.NONE;
    private ConnectionState currentConnectionState = ConnectionState.DISCONNECTED;
    private UsbSerialPort usbPort;
    private RxBleDevice bleDevice;
    private Integer latestBleRssi;
    private BluetoothService bluetoothService;
    private MainActivity mainActivity; // Reference to activity for retrieving services
    private final MacrosManager macrosManager;
    private final Set<ConnectionStateListener> stateListeners = new CopyOnWriteArraySet<>();
    private BluetoothService.ConnectionStateListener bluetoothServiceStateListener;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    public interface ConnectionStateListener {
        void onConnectionStateChanged(ConnectionType type, ConnectionState state);
        void onConnectionError(String error);
        default void onBluetoothRssiChanged(int rssi) {}
    }

    public ConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.macrosManager = MacrosManager.getInstance(this.context);
    }

    public void setConnectionStateListener(ConnectionStateListener listener) {
        stateListeners.clear();
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    public void addConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    public void removeConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            stateListeners.remove(listener);
        }
    }

    public ConnectionType getCurrentConnectionType() {
        return currentConnectionType;
    }

    public ConnectionState getCurrentConnectionState() {
        return currentConnectionState;
    }

    public UsbSerialPort getUsbPort() {
        return usbPort;
    }

    public RxBleDevice getBleDevice() {
        return bleDevice;
    }

    public Integer getLatestBleRssi() {
        return latestBleRssi;
    }

    public int getBleSignalDrawableRes() {
        if (currentConnectionType != ConnectionType.BLUETOOTH || currentConnectionState != ConnectionState.CONNECTED) {
            return R.drawable.signal_cellular_alt_off_24px;
        }
        if (latestBleRssi == null || latestBleRssi <= -90) {
            return R.drawable.signal_cellular_alt_off_24px;
        }
        if (latestBleRssi <= -76) {
            return R.drawable.signal_cellular_alt_1_bar_24px;
        }
        if (latestBleRssi <= -61) {
            return R.drawable.signal_cellular_alt_2_bar_24px;
        }
        return R.drawable.signal_cellular_alt_24px;
    }

    public boolean isConnected() {
        // Validate the actual connection matches the claimed type
        if (currentConnectionState != ConnectionState.CONNECTED) {
            return false;
        }
        
        // Check if the actual connection is available
        boolean hasValidConnection = false;
        if (currentConnectionType == ConnectionType.USB && usbPort != null) {
            hasValidConnection = true;
        } else if (currentConnectionType == ConnectionType.BLUETOOTH && bluetoothService != null && bluetoothService.isConnected()) {
            hasValidConnection = true;
        }
        
        if (!hasValidConnection) {
            Log.w(TAG, "Connection validation failed: type=" + currentConnectionType 
                    + " usbPort=" + (usbPort != null) 
                    + " bluetoothService=" + (bluetoothService != null ? bluetoothService.isConnected() : false));
            
            // Auto-correct if we found an active connection of different type
            if (usbPort != null && currentConnectionType != ConnectionType.USB) {
                Log.i(TAG, "Auto-correcting: actual USB connection found, updating connection type");
                updateConnectionState(ConnectionType.USB, ConnectionState.CONNECTED);
                return true;
            }
            if (bluetoothService != null && bluetoothService.isConnected() && currentConnectionType != ConnectionType.BLUETOOTH) {
                Log.i(TAG, "Auto-correcting: actual Bluetooth connection found, updating connection type");
                updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTED);
                return true;
            }
            
            return false;
        }
        
        return true;
    }

    /**
     * Attempt to auto-connect to last used device
     */
    public void autoConnect() {
        if (!isAutoConnectEnabled()) {
            Log.d(TAG, "Auto-connect is disabled");
            return;
        }

        String lastType = prefs.getString(KEY_LAST_CONNECTION_TYPE, "");
        Log.d(TAG, "Attempting auto-connect, last type: " + lastType);

        if ("USB".equals(lastType)) {
            connectUsb();
        } else if ("BLUETOOTH".equals(lastType)) {
            String macAddress = prefs.getString(KEY_LAST_BLE_DEVICE_MAC, null);
            if (macAddress != null) {
                // Bluetooth auto-connect will be handled by BluetoothService
                Log.d(TAG, "Last BLE device: " + macAddress);
            }
        }
    }

    /**
     * Connect to USB device
     */
    public boolean connectUsb() {
        Log.d(TAG, "Attempting USB connection");
        updateConnectionState(ConnectionType.USB, ConnectionState.CONNECTING);

        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found");
            updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
            notifyError("No USB device found");
            return false;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // Check USB permission
        if (!manager.hasPermission(device)) {
            Log.d(TAG, "Requesting USB permission");
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                permissionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPermission(device, pendingIntent);
            return false;
        }

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device connection");
            updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
            notifyError("Failed to open USB connection");
            return false;
        }

        usbPort = driver.getPorts().get(0);
        try {
            usbPort.open(connection);
            usbPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            
            // Save last connection
            saveLastConnection(ConnectionType.USB, null, null);
            
            updateConnectionState(ConnectionType.USB, ConnectionState.CONNECTED);
            Log.d(TAG, "USB connected successfully");
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to open USB serial port: " + e.getMessage());
            updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
            notifyError("Failed to open USB port: " + e.getMessage());
            return false;
        }
    }

    /**
     * Connect to BLE device
     */
    public void connectBluetooth(RxBleDevice device) {
        Log.d(TAG, "Connecting to BLE device: " + device.getMacAddress() 
                + ", bluetoothService=" + (bluetoothService != null ? "ready" : "NOT_READY"));
        updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTING);
        
        this.bleDevice = device;
        
        // Save last connection
        saveLastConnection(ConnectionType.BLUETOOTH, device.getMacAddress(), device.getName());
        
        // Check if BluetoothService is available before marking as connected
        if (bluetoothService != null && bluetoothService.isConnected()) {
            updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTED);
            Log.d(TAG, "Bluetooth service ready, marked as CONNECTED");
        } else {
            Log.w(TAG, "Bluetooth service not ready yet, service=" + (bluetoothService != null ? "exists" : "NULL"));
            // Service might connect later - listener will be updated by BluetoothService
        }
    }

    /**
     * Disconnect current connection
     */
    public void disconnect() {
        Log.d(TAG, "Disconnecting, type: " + currentConnectionType);
        
        if (currentConnectionType == ConnectionType.USB && usbPort != null) {
            try {
                usbPort.close();
                usbPort = null;
                Log.d(TAG, "USB port closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing USB port: " + e.getMessage());
            }
        } else if (currentConnectionType == ConnectionType.BLUETOOTH) {
            bleDevice = null;
            // BluetoothService will handle actual disconnection
        }
        
        updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
    }

    /**
     * Update connection state and notify listener
     */
    private void updateConnectionState(ConnectionType type, ConnectionState state) {
        this.currentConnectionType = type;
        this.currentConnectionState = state;
        
        for (ConnectionStateListener listener : stateListeners) {
            listener.onConnectionStateChanged(type, state);
        }
    }

    /**
     * Force-sync BLE connection status from UI/service callbacks.
     */
    public void syncBluetoothConnectionState(boolean connected) {
        if (connected) {
            updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTED);
        } else {
            latestBleRssi = null;
            if (currentConnectionType == ConnectionType.BLUETOOTH) {
                updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
            }
        }
    }

    private void updateBleRssi(int rssi) {
        latestBleRssi = rssi;
        for (ConnectionStateListener listener : stateListeners) {
            listener.onBluetoothRssiChanged(rssi);
        }
    }

    /**
     * Notify error to listener
     */
    private void notifyError(String error) {
        for (ConnectionStateListener listener : stateListeners) {
            listener.onConnectionError(error);
        }
    }

    /**
     * Save last connection info
     */
    private void saveLastConnection(ConnectionType type, String macAddress, String deviceName) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_CONNECTION_TYPE, type.name());
        
        if (type == ConnectionType.BLUETOOTH && macAddress != null) {
            editor.putString(KEY_LAST_BLE_DEVICE_MAC, macAddress);
            editor.putString(KEY_LAST_BLE_DEVICE_NAME, deviceName);
        }
        
        editor.apply();
        Log.d(TAG, "Saved last connection: " + type.name());
    }

    /**
     * Get last connected device info
     */
    public String getLastConnectionType() {
        return prefs.getString(KEY_LAST_CONNECTION_TYPE, "NONE");
    }

    public String getLastBleDeviceMac() {
        return prefs.getString(KEY_LAST_BLE_DEVICE_MAC, null);
    }

    public String getLastBleDeviceName() {
        return prefs.getString(KEY_LAST_BLE_DEVICE_NAME, null);
    }

    /**
     * Auto-connect settings
     */
    public boolean isAutoConnectEnabled() {
        return prefs.getBoolean(KEY_AUTO_CONNECT_ENABLED, true);
    }

    public void setAutoConnectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT_ENABLED, enabled).apply();
    }

    /**
     * Clear saved connection data
     */
    public void clearLastConnection() {
        prefs.edit()
            .remove(KEY_LAST_CONNECTION_TYPE)
            .remove(KEY_LAST_BLE_DEVICE_MAC)
            .remove(KEY_LAST_BLE_DEVICE_NAME)
            .apply();
    }

    /**
     * Set MainActivity reference for service access
     */
    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
        Log.d(TAG, "MainActivity reference set");
    }

    /**
     * Set BluetoothService for BLE HID sending
     */
    public void setBluetoothService(BluetoothService service) {
        if (this.bluetoothService != null && bluetoothServiceStateListener != null) {
            this.bluetoothService.removeConnectionStateListener(bluetoothServiceStateListener);
        }
        this.bluetoothService = service;
        if (this.bluetoothService != null) {
            bluetoothServiceStateListener = new BluetoothService.ConnectionStateListener() {
                @Override
                public void onBluetoothConnecting(RxBleDevice device) {
                    bleDevice = device;
                    updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTING);
                }

                @Override
                public void onBluetoothConnected(RxBleDevice device) {
                    bleDevice = device;
                    saveLastConnection(ConnectionType.BLUETOOTH, device.getMacAddress(), device.getName());
                    updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTED);
                }

                @Override
                public void onBluetoothDisconnected(RxBleDevice device) {
                    latestBleRssi = null;
                    if (currentConnectionType == ConnectionType.BLUETOOTH) {
                        updateConnectionState(ConnectionType.NONE, ConnectionState.DISCONNECTED);
                    }
                }

                @Override
                public void onBluetoothError(RxBleDevice device, String error) {
                    latestBleRssi = null;
                    updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.ERROR);
                    notifyError(error);
                }

                @Override
                public void onBluetoothRssiChanged(RxBleDevice device, int rssi) {
                    updateBleRssi(rssi);
                }
            };
            this.bluetoothService.addConnectionStateListener(bluetoothServiceStateListener);
        } else {
            bluetoothServiceStateListener = null;
        }
        Log.d(TAG, "BluetoothService set for HID sending: " + (service != null ? "valid" : "NULL"));
    }

    /**
     * Get or attempt to recover BluetoothService reference
     */
    private BluetoothService getBluetoothService() {
        if (bluetoothService != null) {
            return bluetoothService;
        }
        
        // Try to get from MainActivity context if available
        if (mainActivity != null) {
            Log.d(TAG, "BluetoothService reference is NULL, attempting recovery from MainActivity...");
            BluetoothService service = mainActivity.getBluetoothService();
            if (service != null) {
                this.bluetoothService = service;
                Log.d(TAG, "Successfully recovered BluetoothService from MainActivity");
                return service;
            }
        }
        
        Log.w(TAG, "BluetoothService reference is NULL and cannot be recovered");
        return null;
    }

    /**
     * Send HID keyboard event
     * @param modifiers Modifier keys (Ctrl, Shift, Alt, etc.)
     * @param keyCode HID key code
     */
    public void sendKeyEvent(int modifiers, int keyCode) {
        BluetoothService service = bluetoothService;
        
        Log.i(TAG, "KEY_ATTEMPT keyCode=" + keyCode
                + " modifiers=" + modifiers
                + " connected=" + isConnected()
                + " type=" + currentConnectionType
                + " state=" + currentConnectionState
                + " usbPort=" + (usbPort != null ? "valid" : "NULL")
                + " bluetoothService=" + (service != null ? (service.isConnected() ? "connected" : "disconnected") : "NULL"));

        if (!isConnected()) {
            Log.w(TAG, "Cannot send key event: not connected");
            return;
        }
        
        // For Bluetooth: try to recover service if null
        if (currentConnectionType == ConnectionType.BLUETOOTH && service == null) {
            service = getBluetoothService();
            if (service == null) {
                Log.e(TAG, "ERROR: Bluetooth type selected but bluetoothService is NULL and cannot be recovered!");
                return;
            }
        }
        
        HIDSender.sendKeyEvent(usbPort, service, modifiers, keyCode);

        // Record user-generated outbound key events for macro capture.
        if (macrosManager.shouldRecordKeyEvent()) {
            macrosManager.recordKeyEvent(keyCode, modifiers);
        }
    }

    /**
     * Send HID key release
     */
    public void sendKeyRelease() {
        BluetoothService service = bluetoothService;
        
        Log.i(TAG, "KEY_RELEASE_ATTEMPT connected=" + isConnected()
                + " type=" + currentConnectionType
                + " state=" + currentConnectionState
                + " usbPort=" + (usbPort != null ? "valid" : "NULL")
                + " bluetoothService=" + (service != null ? (service.isConnected() ? "connected" : "disconnected") : "NULL"));

        if (!isConnected()) {
            Log.w(TAG, "Cannot send key release: not connected");
            return;
        }
        
        // For Bluetooth: try to recover service if null
        if (currentConnectionType == ConnectionType.BLUETOOTH && service == null) {
            service = getBluetoothService();
            if (service == null) {
                Log.e(TAG, "ERROR: Bluetooth type selected but bluetoothService is NULL and cannot be recovered!");
                return;
            }
        }
        
        HIDSender.sendKeyRelease(usbPort, service);
    }

    /**
     * Send a full HID keyboard report with multiple simultaneous keys.
     * @param modifiers Modifier byte
     * @param keyCodes Key codes to include (up to 6; empty = release all)
     */
    public void sendKeyboardReport(int modifiers, int[] keyCodes) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send keyboard report: not connected");
            return;
        }
        BluetoothService service = bluetoothService;
        if (currentConnectionType == ConnectionType.BLUETOOTH && service == null) {
            service = getBluetoothService();
            if (service == null) {
                Log.e(TAG, "ERROR: Bluetooth type selected but bluetoothService is NULL!");
                return;
            }
        }
        HIDSender.sendKeyboardReport(usbPort, service, modifiers, keyCodes);
    }

    /**
     * Send a raw HID keyboard report with the given modifier byte and key code.
     * Unlike sendKeyEvent(), this does NOT skip keyCode=0, allowing pure modifier
     * press/release packets needed for macOS Unicode hex input (Option + hex digits).
     */
    public void sendRawHIDReport(int modifier, int keyCode) {
        if (!isConnected()) return;
        BluetoothService service = bluetoothService;
        if (currentConnectionType == ConnectionType.BLUETOOTH && service == null) {
            service = getBluetoothService();
            if (service == null) return;
        }
        // Build CH9329 keyboard packet: 5-byte header + modifier + reserved + 6 key slots + checksum
        byte[] header = {(byte)0x57, (byte)0xAB, 0x00, 0x02, 0x08};
        byte[] data = new byte[14];
        System.arraycopy(header, 0, data, 0, 5);
        data[5] = (byte) modifier;
        data[6] = 0;
        data[7] = (byte) keyCode;
        // slots 2-6 = 0
        int sum = 0;
        for (int i = 0; i < 13; i++) sum += (data[i] & 0xFF);
        data[13] = (byte)(sum & 0xFF);
        HIDSender.sendPacket(usbPort, service, data, "RawHID");
    }

    /**
     * Send HID mouse movement
     * @param deltaX X movement (-128 to 127)
     * @param deltaY Y movement (-128 to 127)
     * @param buttons Mouse buttons (bitmask)
     */
    public void sendMouseMovement(int deltaX, int deltaY, int buttons) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send mouse movement: not connected");
            return;
        }
        HIDSender.sendMouseMovement(usbPort, bluetoothService, deltaX, deltaY, buttons);
    }

    /**
     * Send touchpad scroll (wheel + horizontal pan), matching Keyboard and Mouse two-finger scroll.
     *
     * @param deltaX horizontal scroll delta (clamped to -127..127)
     * @param deltaY vertical wheel delta (clamped to -127..127)
     */
    public void sendScroll(int deltaX, int deltaY) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send scroll: not connected");
            return;
        }
        HIDSender.sendScroll(usbPort, bluetoothService, deltaX, deltaY);
    }

    /**
     * Send HID mouse click
     * @param button Button (1=left, 2=right, 4=middle)
     * @param press True for press, false for release
     */
    public void sendMouseClick(int button, boolean press) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send mouse click: not connected");
            return;
        }
        HIDSender.sendMouseClick(usbPort, bluetoothService, button, press);
    }

    /**
     * Send HID gamepad event
     * @param buttons Gamepad buttons bitmask
     * @param leftX Left stick X (-128 to 127)
     * @param leftY Left stick Y (-128 to 127)
     * @param rightX Right stick X (-128 to 127)
     * @param rightY Right stick Y (-128 to 127)
     */
    public void sendGamepadEvent(int buttons, int leftX, int leftY, int rightX, int rightY) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send gamepad event: not connected");
            return;
        }
        HIDSender.sendGamepadEvent(usbPort, bluetoothService, buttons, leftX, leftY, rightX, rightY);
    }
}
