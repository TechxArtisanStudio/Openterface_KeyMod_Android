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
    private ConnectionStateListener stateListener;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    public interface ConnectionStateListener {
        void onConnectionStateChanged(ConnectionType type, ConnectionState state);
        void onConnectionError(String error);
    }

    public ConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.stateListener = listener;
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

    public boolean isConnected() {
        return currentConnectionState == ConnectionState.CONNECTED;
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
        Log.d(TAG, "Connecting to BLE device: " + device.getMacAddress());
        updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTING);
        
        this.bleDevice = device;
        
        // Save last connection
        saveLastConnection(ConnectionType.BLUETOOTH, device.getMacAddress(), device.getName());
        
        // Connection will be handled by BluetoothService
        // Update state after successful connection
        updateConnectionState(ConnectionType.BLUETOOTH, ConnectionState.CONNECTED);
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
        
        if (stateListener != null) {
            stateListener.onConnectionStateChanged(type, state);
        }
    }

    /**
     * Notify error to listener
     */
    private void notifyError(String error) {
        if (stateListener != null) {
            stateListener.onConnectionError(error);
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
}
