package com.example.dual_modekeyboard;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final long RECONNECT_DELAY_MS = 5000; // Reconnect delay: 5 seconds

    private final IBinder binder = new BluetoothBinder();
    private RxBleClient rxBleClient;
    private RxBleConnection activeConnection;
    private final CompositeDisposable connectionDisposables = new CompositeDisposable();
    private final Set<String> connectingDevices = new HashSet<>();
    private RxBleDevice connectedDevice;
    private Disposable reconnectDisposable;

    public class BluetoothBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    public void connectToDevice(RxBleDevice device) {
        if (rxBleClient == null) {
            Log.e(TAG, "RxBleClient is not initialized");
            return;
        }

        String deviceAddress = device.getMacAddress();
        synchronized (connectingDevices) {
            if (connectingDevices.contains(deviceAddress)) {
                Log.d(TAG, "Already connecting to device: " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + ")");
                return;
            }
            connectingDevices.add(deviceAddress);
        }

        stopReconnect(); // Clear previous reconnect attempts
        connectedDevice = device;

        // Use autoConnect=false and handle reconnection via retry logic
        Disposable connectionDisposable = device.establishConnection(false)
                .doOnDispose(() -> {
                    synchronized (connectingDevices) {
                        connectingDevices.remove(deviceAddress);
                    }
                    Log.d(TAG, "Connection disposed for device: " + sanitizeDeviceName(device.getName()));
                })
                .subscribe(
                        connection -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            activeConnection = connection;
                            Log.d(TAG, "Connected to " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + ")");
                            // Connection established, no need for separate monitoring
                        },
                        throwable -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            Log.e(TAG, "Connection error for device " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + "): " + throwable.toString());
                            activeConnection = null;
                            scheduleReconnect(device);
                        }
                );
        connectionDisposables.add(connectionDisposable);
    }

    private void scheduleReconnect(RxBleDevice device) {
        stopReconnect();
        reconnectDisposable = Observable.timer(RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
                .subscribe(
                        aLong -> {
                            if (connectedDevice != null && connectedDevice.getMacAddress().equals(device.getMacAddress())) {
                                Log.d(TAG, "Attempting to reconnect to " + sanitizeDeviceName(device.getName()));
                                connectToDevice(device);
                            }
                        },
                        throwable -> Log.e(TAG, "Reconnect scheduling error: " + throwable.toString())
                );
        connectionDisposables.add(reconnectDisposable);
    }

    private void stopReconnect() {
        if (reconnectDisposable != null && !reconnectDisposable.isDisposed()) {
            reconnectDisposable.dispose();
            connectionDisposables.remove(reconnectDisposable);
        }
    }

    @SuppressLint("CheckResult")
    public void sendData(byte[] keyBoardData) {
        if (activeConnection == null) {
            Log.w(TAG, "Cannot send data: No active connection");
            if (connectedDevice != null) {
                Log.d(TAG, "Attempting to reconnect before sending data");
                connectToDevice(connectedDevice);
            }
            return;
        }

        activeConnection.discoverServices()
                .flatMap(services -> services.getCharacteristic(WRITE_CHARACTERISTIC_UUID))
                .subscribe(
                        characteristic -> {
                            int properties = characteristic.getProperties();
                            boolean supportsWrite = (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
                            if (supportsWrite) {
//                                byte[] dataBytes = CustomKeyboardView.hexStringToByteArray(keyBoardData);
                                activeConnection.writeCharacteristic(WRITE_CHARACTERISTIC_UUID, keyBoardData)
                                        .subscribe(
                                                writtenBytes -> Log.d(TAG, "Successfully sent data: " + Arrays.toString(keyBoardData)),
                                                throwable -> Log.e(TAG, "Write error: " + throwable.toString())
                                        );
                            } else {
                                Log.w(TAG, "Characteristic " + WRITE_CHARACTERISTIC_UUID + " does not support write operations");
                            }
                        },
                        throwable -> Log.e(TAG, "Error retrieving characteristic: " + throwable.toString())
                );
    }

    public RxBleDevice getConnectedDevice() {
        return connectedDevice;
    }

    public boolean isConnected() {
        return activeConnection != null;
    }

    private String sanitizeDeviceName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connectionDisposables.dispose();
        activeConnection = null;
        connectedDevice = null;
        stopReconnect();
        Log.d(TAG, "BluetoothService destroyed");
    }
}