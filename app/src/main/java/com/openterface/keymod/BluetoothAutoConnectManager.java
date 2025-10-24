package com.openterface.keymod;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

/**
 * Manages automatic Bluetooth connection on app launch
 * - Tries to connect to the last connected device first
 * - Falls back to scanning for the best signal device if last device is unavailable
 * - Retries up to 3 times on failure
 */
public class BluetoothAutoConnectManager {
    private static final String TAG = "BTAutoConnect";
    private static final int MAX_RETRIES = 3;
    private static final int SCAN_DURATION_MS = 5000; // 5 seconds scan time
    private static final int RETRY_DELAY_MS = 2000; // 2 seconds between retries
    private static final String PREFS_NAME = "ConnectionPrefs";
    private static final String KEY_LAST_BLE_DEVICE_MAC = "last_ble_device_mac";
    private static final String KEY_LAST_BLE_DEVICE_NAME = "last_ble_device_name";
    
    private final Context context;
    private final RxBleClient rxBleClient;
    private final BluetoothService bluetoothService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    
    private Disposable scanDisposable;
    private int currentRetry = 0;
    private boolean isConnecting = false;
    private AutoConnectListener listener;
    private String targetDeviceMac = null; // MAC address of device to connect to
    
    public interface AutoConnectListener {
        void onAutoConnectStarted();
        void onAutoConnectSuccess(RxBleDevice device);
        void onAutoConnectFailed(String reason);
        void onAutoConnectRetrying(int retryCount);
    }
    
    public BluetoothAutoConnectManager(Context context, RxBleClient rxBleClient, BluetoothService bluetoothService) {
        this.context = context;
        this.rxBleClient = rxBleClient;
        this.bluetoothService = bluetoothService;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void setAutoConnectListener(AutoConnectListener listener) {
        this.listener = listener;
    }
    
    /**
     * Start auto-connect process
     */
    public void startAutoConnect() {
        if (isConnecting) {
            Log.d(TAG, "Auto-connect already in progress");
            return;
        }
        
        // Check if Bluetooth is supported and enabled
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            notifyFailed("Bluetooth not supported");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            notifyFailed("Bluetooth is disabled");
            return;
        }
        
        // Check permissions
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions");
            notifyFailed("Missing Bluetooth permissions");
            return;
        }
        
        // Check if already connected
        if (bluetoothService != null && bluetoothService.isConnected()) {
            Log.d(TAG, "Already connected to a Bluetooth device");
            RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
            if (connectedDevice != null) {
                notifySuccess(connectedDevice);
            }
            return;
        }
        
        currentRetry = 0;
        isConnecting = true;
        if (listener != null) {
            listener.onAutoConnectStarted();
        }
        
        // Try to get last connected device
        targetDeviceMac = prefs.getString(KEY_LAST_BLE_DEVICE_MAC, null);
        String lastDeviceName = prefs.getString(KEY_LAST_BLE_DEVICE_NAME, null);
        
        if (targetDeviceMac != null && !targetDeviceMac.isEmpty()) {
            Log.d(TAG, "Starting auto-connect to last device: " + lastDeviceName + " (" + targetDeviceMac + ")");
        } else {
            Log.d(TAG, "No last device found, will connect to best signal device");
        }
        
        scanAndConnect();
    }
    
    /**
     * Stop auto-connect process
     */
    public void stopAutoConnect() {
        isConnecting = false;
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Auto-connect stopped");
    }
    
    private boolean hasRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 and above
            boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            return hasBluetoothConnect && hasBluetoothScan;
        } else {
            // Android 11 and below - need location permission
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void scanAndConnect() {
        String attemptMsg = "Starting BLE scan for Openterface devices (attempt " + (currentRetry + 1) + "/" + MAX_RETRIES + ")";
        if (targetDeviceMac != null) {
            attemptMsg += " - Looking for last device: " + targetDeviceMac;
        }
        Log.d(TAG, attemptMsg);
        
        final List<ScanResult> foundDevices = new ArrayList<>();
        final ScanResult[] targetDeviceResult = new ScanResult[1]; // To store last connected device if found
        
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        
        scanDisposable = rxBleClient.scanBleDevices(scanSettings)
                .timeout(SCAN_DURATION_MS, TimeUnit.MILLISECONDS)
                .subscribe(
                        scanResult -> {
                            RxBleDevice device = scanResult.getBleDevice();
                            String deviceName = sanitizeDeviceName(device.getName());
                            String deviceMac = device.getMacAddress();
                            int rssi = scanResult.getRssi();
                            
                            // Only consider Openterface devices
                            if (deviceName != null && deviceName.matches("(?i)openterface KM.*")) {
                                Log.d(TAG, "Found Openterface device: " + deviceName + " (" + deviceMac + ", RSSI: " + rssi + ")");
                                
                                // Check if this is the target device (last connected)
                                if (targetDeviceMac != null && deviceMac.equals(targetDeviceMac)) {
                                    Log.d(TAG, "Found target device (last connected): " + deviceName);
                                    targetDeviceResult[0] = scanResult;
                                    // Stop scan immediately when we find the target device
                                    if (scanDisposable != null && !scanDisposable.isDisposed()) {
                                        scanDisposable.dispose();
                                    }
                                    handleScanComplete(foundDevices, targetDeviceResult[0]);
                                    return;
                                }
                                
                                // Check if device already in list
                                boolean exists = false;
                                for (ScanResult existingResult : foundDevices) {
                                    if (existingResult.getBleDevice().getMacAddress().equals(deviceMac)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                
                                if (!exists) {
                                    foundDevices.add(scanResult);
                                }
                            }
                        },
                        throwable -> {
                            // Scan completed (timeout or error)
                            Log.d(TAG, "Scan completed. Found " + foundDevices.size() + " Openterface devices");
                            handleScanComplete(foundDevices, targetDeviceResult[0]);
                        },
                        () -> {
                            // Scan completed normally
                            Log.d(TAG, "Scan completed normally. Found " + foundDevices.size() + " Openterface devices");
                            handleScanComplete(foundDevices, targetDeviceResult[0]);
                        }
                );
    }
    
    private void handleScanComplete(List<ScanResult> foundDevices) {
        handleScanComplete(foundDevices, null);
    }
    
    private void handleScanComplete(List<ScanResult> foundDevices, ScanResult targetDevice) {
        // Priority 1: Connect to last connected device if found
        if (targetDevice != null) {
            RxBleDevice device = targetDevice.getBleDevice();
            String deviceName = sanitizeDeviceName(device.getName());
            Log.d(TAG, "Connecting to last connected device: " + deviceName + " (RSSI: " + targetDevice.getRssi() + ")");
            connectToDevice(device);
            return;
        }
        
        // Priority 2: If last device not found but other devices exist, connect to best signal
        if (!foundDevices.isEmpty()) {
            // Find device with best signal strength (highest RSSI)
            ScanResult bestDevice = null;
            int bestRssi = Integer.MIN_VALUE;
            
            for (ScanResult result : foundDevices) {
                if (result.getRssi() > bestRssi) {
                    bestRssi = result.getRssi();
                    bestDevice = result;
                }
            }
            
            if (bestDevice != null) {
                RxBleDevice device = bestDevice.getBleDevice();
                String deviceName = sanitizeDeviceName(device.getName());
                if (targetDeviceMac != null) {
                    Log.d(TAG, "Last device not found, connecting to best signal device: " + deviceName + " (RSSI: " + bestRssi + ")");
                } else {
                    Log.d(TAG, "Connecting to best signal device: " + deviceName + " (RSSI: " + bestRssi + ")");
                }
                connectToDevice(device);
                return;
            }
        }
        
        // No devices found at all
        String failureMsg = targetDeviceMac != null 
            ? "Last connected device not found and no other devices available"
            : "No Openterface devices found";
        Log.d(TAG, failureMsg);
        handleConnectionFailure(failureMsg);
    }
    
    private void connectToDevice(RxBleDevice device) {
        Log.d(TAG, "Attempting to connect to " + sanitizeDeviceName(device.getName()));
        
        if (bluetoothService == null) {
            Log.e(TAG, "BluetoothService is null");
            handleConnectionFailure("BluetoothService not available");
            return;
        }
        
        bluetoothService.connectToDevice(device);
        
        // Wait and check if connection was successful
        handler.postDelayed(() -> {
            if (bluetoothService.isConnected() && 
                bluetoothService.getConnectedDevice() != null &&
                bluetoothService.getConnectedDevice().getMacAddress().equals(device.getMacAddress())) {
                Log.d(TAG, "Successfully connected to " + sanitizeDeviceName(device.getName()));
                isConnecting = false;
                notifySuccess(device);
            } else {
                Log.w(TAG, "Failed to connect to " + sanitizeDeviceName(device.getName()));
                handleConnectionFailure("Connection failed");
            }
        }, 3000); // Wait 3 seconds to verify connection
    }
    
    private void handleConnectionFailure(String reason) {
        currentRetry++;
        
        if (currentRetry < MAX_RETRIES) {
            Log.d(TAG, "Connection failed: " + reason + ". Retrying... (" + currentRetry + "/" + MAX_RETRIES + ")");
            if (listener != null) {
                listener.onAutoConnectRetrying(currentRetry);
            }
            
            // Retry after delay
            handler.postDelayed(this::scanAndConnect, RETRY_DELAY_MS);
        } else {
            Log.e(TAG, "Auto-connect failed after " + MAX_RETRIES + " attempts: " + reason);
            isConnecting = false;
            notifyFailed("Failed after " + MAX_RETRIES + " attempts: " + reason);
        }
    }
    
    private void notifySuccess(RxBleDevice device) {
        if (listener != null) {
            handler.post(() -> listener.onAutoConnectSuccess(device));
        }
    }
    
    private void notifyFailed(String reason) {
        if (listener != null) {
            handler.post(() -> listener.onAutoConnectFailed(reason));
        }
    }
    
    private String sanitizeDeviceName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }
}
