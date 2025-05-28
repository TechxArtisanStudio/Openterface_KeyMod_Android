package com.example.dual_modekeyboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.example.dual_modekeyboard.R;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BluetoothDialogFragment extends DialogFragment {

    private static final String TAG = "BluetoothDialogFragment";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1002;
    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_PAIRED_DEVICES = "paired_devices";

    // UUIDs for openterface KM device
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private Switch bluetoothSwitch;
    private ListView devicesListView;
    private Button closeButton;
    private Button scanButton;
    private Button sendButton;
    private CustomDeviceAdapter devicesAdapter;
    private ArrayList<DeviceItem> devicesList;
    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private ArrayList<RxBleDevice> pairedDevices;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private final Object scanLock = new Object();
    private final CompositeDisposable connectionDisposables = new CompositeDisposable();
    private final Set<String> connectingDevices = new HashSet<>();
    private RxBleConnection activeConnection; // Store the active connection

    // Data structure for ListView items (devices or headers)
    private static class DeviceItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_DEVICE = 1;

        int type;
        String title; // For headers
        RxBleDevice bleDevice; // For BLE devices

        DeviceItem(int type, String title, RxBleDevice bleDevice) {
            this.type = type;
            this.title = title;
            this.bleDevice = bleDevice;
        }
    }

    // Custom adapter for BLE devices
    private class CustomDeviceAdapter extends ArrayAdapter<DeviceItem> {
        CustomDeviceAdapter(Context context, ArrayList<DeviceItem> devices) {
            super(context, 0, devices);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            DeviceItem item = getItem(position);
            if (item == null) return convertView;

            if (item.type == DeviceItem.TYPE_HEADER) {
                if (convertView == null || convertView.getTag() == null || !convertView.getTag().equals("HEADER")) {
                    convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                    convertView.setTag("HEADER");
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                textView.setText(item.title);
                textView.setTextSize(18);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
                convertView.setEnabled(false);
                convertView.setOnClickListener(null);
            } else {
                if (convertView == null || convertView.getTag() == null || !convertView.getTag().equals("DEVICE")) {
                    convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                    convertView.setTag("DEVICE");
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                String deviceName = sanitizeDeviceName(item.bleDevice.getName());
                String deviceAddress = item.bleDevice.getMacAddress();
                textView.setText(deviceName + "\n" + deviceAddress);
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).type;
        }

        @Override
        public int getViewTypeCount() {
            return 2; // HEADER and DEVICE
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).type == DeviceItem.TYPE_DEVICE;
        }
    }

    // Sanitize device name to remove non-printable characters
    private String sanitizeDeviceName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }

    // Set RxBleClient from MainActivity
    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_bluetooth, container, false);
        loadPairedDevices(); // Load paired devices from SharedPreferences
        initializeUIComponents(view);
        initializeBluetooth();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;

            int dialogWidth = (int) (screenWidth * 0.666);
            int dialogHeight = (int) (screenHeight * 0.666);

            getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            Log.d(TAG, "Dialog size set to width: " + dialogWidth + ", height: " + dialogHeight);
        }
    }

    private void initializeUIComponents(View view) {
        bluetoothSwitch = view.findViewById(R.id.bluetooth_switch);
        devicesListView = view.findViewById(R.id.devices_list);
        closeButton = view.findViewById(R.id.close_button);
        scanButton = view.findViewById(R.id.scan_button);
        sendButton = view.findViewById(R.id.send_button);

        devicesList = new ArrayList<>();
        pairedDevices = pairedDevices != null ? pairedDevices : new ArrayList<>();
        devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Paired Devices", null));
        for (RxBleDevice device : pairedDevices) {
            devicesList.add(new DeviceItem(DeviceItem.TYPE_DEVICE, null, device));
        }
        devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Scanned BLE Devices", null));
        devicesAdapter = new CustomDeviceAdapter(requireContext(), devicesList);
        devicesListView.setAdapter(devicesAdapter);

        bluetoothSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableBluetooth();
            } else {
                disableBluetooth();
            }
        });

        devicesListView.setOnItemClickListener((parent, v, position, id) -> {
            DeviceItem item = devicesList.get(position);
            if (item.type == DeviceItem.TYPE_DEVICE) {
                RxBleDevice selectedBleDevice = item.bleDevice;
                String deviceName = sanitizeDeviceName(selectedBleDevice.getName());
                if (deviceName.matches("(?i)openterface KM.*")) {
                    connectToDevice(selectedBleDevice);
                } else {
                    showToast("Please select an openterface KM device");
                }
            }
        });

        closeButton.setOnClickListener(v -> dismiss());

        scanButton.setOnClickListener(v -> {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                if (!isScanning) {
                    startBleScan();
                } else {
                    showToast("Scan already in progress");
                }
            } else {
                showToast("Please enable Bluetooth first");
            }
        });

        if (sendButton != null) {
            sendButton.setOnClickListener(v -> sendData("57AB0005050100FD00000A"));
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "Bluetooth not supported on this device");
            showToast("Bluetooth not supported on this device");
            bluetoothSwitch.setEnabled(false);
            return;
        }

        boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        boolean hasBluetoothScan = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Permissions - BLUETOOTH_CONNECT: " + hasBluetoothConnect + ", BLUETOOTH_SCAN: " + hasBluetoothScan);

        if (hasBluetoothConnect && hasBluetoothScan) {
            bluetoothSwitch.setEnabled(true);
            bluetoothSwitch.setChecked(bluetoothAdapter.isEnabled());
            if (bluetoothAdapter.isEnabled() && rxBleClient != null && !isScanning && pairedDevices != null && !pairedDevices.isEmpty()) {
                RxBleDevice device = pairedDevices.get(0);
                if (!connectingDevices.contains(device.getMacAddress())) {
                    Log.d(TAG, "Attempting auto-connect to paired device: " + sanitizeDeviceName(device.getName()) + " (" + device.getMacAddress() + ")");
                    connectToDevice(device);
                }
            }
        } else {
            checkPermissions();
        }
    }

    private void checkPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        };

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                Log.d(TAG, "Permission required: " + permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissions(permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            initializeBluetooth();
        }
    }

    private void enableBluetooth() {
        if (bluetoothAdapter == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            bluetoothSwitch.setChecked(false);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else if (!isScanning && pairedDevices != null && !pairedDevices.isEmpty()) {
            RxBleDevice device = pairedDevices.get(0);
            if (!connectingDevices.contains(device.getMacAddress())) {
                Log.d(TAG, "Attempting auto-connect to paired device: " + sanitizeDeviceName(device.getName()) + " (" + device.getMacAddress() + ")");
                connectToDevice(device);
            }
        } else if (!isScanning) {
            startBleScan();
        }
    }

    private void disableBluetooth() {
        if (bluetoothAdapter == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showToast("Bluetooth permissions required");
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            stopScan();
            devicesList.clear();
            devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Paired Devices", null));
            for (RxBleDevice device : pairedDevices) {
                devicesList.add(new DeviceItem(DeviceItem.TYPE_DEVICE, null, device));
            }
            devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Scanned BLE Devices", null));
            mainHandler.post(() -> devicesAdapter.notifyDataSetChanged());
            showToast("Bluetooth disabled");
        }
    }

    private void startBleScan() {
        if (rxBleClient == null) {
            Log.e(TAG, "RxBleClient is not initialized");
            showToast("Bluetooth scanning not available");
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_BLUETOOTH_PERMISSIONS);
            return;
        }

        synchronized (scanLock) {
            if (isScanning) {
                Log.d(TAG, "Scan already in progress, ignoring startBleScan");
                return;
            }
            isScanning = true;
            mainHandler.post(() -> scanButton.setEnabled(false));
            Log.d(TAG, "Starting BLE scan");
        }

        while (devicesList.size() > 2) {
            devicesList.remove(devicesList.size() - 1);
        }
        mainHandler.post(() -> devicesAdapter.notifyDataSetChanged());

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        synchronized (scanLock) {
            scanSubscription = rxBleClient.scanBleDevices(scanSettings)
                    .subscribe(
                            scanResult -> {
                                RxBleDevice device = scanResult.getBleDevice();
                                String deviceName = sanitizeDeviceName(device.getName());
                                String deviceAddress = device.getMacAddress();
                                Log.d(TAG, "Found device: " + deviceName + " (" + deviceAddress + ")");

                                if (!deviceName.matches("(?i)openterface KM.*")) {
                                    return;
                                }

                                synchronized (scanLock) {
                                    for (DeviceItem item : devicesList) {
                                        if (item.bleDevice != null && item.bleDevice.getMacAddress().equals(deviceAddress)) {
                                            return;
                                        }
                                    }

                                    devicesList.add(new DeviceItem(DeviceItem.TYPE_DEVICE, null, device));
                                    mainHandler.post(() -> {
                                        devicesAdapter.notifyDataSetChanged();
                                        showToast("Found openterface KM: " + deviceName);
                                    });

                                    stopScan();
                                }
                            },
                            throwable -> {
                                Log.e(TAG, "Scan error: " + throwable.getMessage(), throwable);
                                showToast("Scan error: " + throwable.getMessage());
                                synchronized (scanLock) {
                                    isScanning = false;
                                    mainHandler.post(() -> scanButton.setEnabled(true));
                                }
                            }
                    );
        }
        showToast("Scanning for openterface KM devices...");
    }

    private void stopScan() {
        synchronized (scanLock) {
            if (scanSubscription != null && !scanSubscription.isDisposed()) {
                scanSubscription.dispose();
                Log.d(TAG, "Scan subscription disposed");
            }
            if (isScanning) {
                isScanning = false;
                mainHandler.post(() -> {
                    scanButton.setEnabled(true);
                    showToast("Scan stopped");
                });
                Log.d(TAG, "BLE scan stopped");
            }
        }
    }

    @SuppressLint("CheckResult")
    private void connectToDevice(RxBleDevice device) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
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

        String deviceName = sanitizeDeviceName(device.getName());
        Log.d(TAG, "Attempting to connect to device: " + deviceName + " (" + deviceAddress + ")");
        showToast("Connecting to " + deviceName);

        Disposable connectionDisposable = device.establishConnection(false)
                .flatMapSingle(connection -> {
                    activeConnection = connection; // Store the connection
                    // Discover services and log UUIDs
                    return connection.discoverServices()
                            .doOnSuccess(services -> {
                                // Log all service and characteristic UUIDs
                                Log.d(TAG, "Discovered services for device: " + deviceName + " (" + deviceAddress + ")");
                                for (BluetoothGattService service : services.getBluetoothGattServices()) {
                                    UUID serviceUuid = service.getUuid();
                                    Log.d(TAG, "Service UUID: " + serviceUuid.toString());
                                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                        UUID characteristicUuid = characteristic.getUuid();
                                        Log.d(TAG, "  Characteristic UUID: " + characteristicUuid.toString());
                                    }
                                }
                            })
                            .map(services -> connection);
                })
                .subscribe(
                        connection -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            Log.d(TAG, "Connected to " + deviceName + " (" + deviceAddress + ")");
                            showToast("Connected to " + deviceName);
                            if (!pairedDevices.contains(device)) {
                                pairedDevices.add(device);
                                savePairedDevice(device);
                                updateDeviceList();
                            }
                            stopScan();
                            // Enable send button if present
                            if (sendButton != null) {
                                mainHandler.post(() -> sendButton.setEnabled(true));
                            }
                        },
                        throwable -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            Log.e(TAG, "Connection error for device " + deviceName + " (" + deviceAddress + "): " + throwable.getMessage());
                            showToast("Connection failed: " + throwable.getMessage());
                            activeConnection = null;
                            synchronized (scanLock) {
                                isScanning = false;
                                mainHandler.post(() -> {
                                    scanButton.setEnabled(true);
                                    if (sendButton != null) {
                                        sendButton.setEnabled(false);
                                    }
                                });
                            }
                            if (pairedDevices.contains(device)) {
                                Log.d(TAG, "Retrying connection to " + deviceName);
                                connectToDevice(device); // Retry connection for paired devices
                            } else if (pairedDevices.isEmpty()) {
                                startBleScan();
                            }
                        }
                );
        connectionDisposables.add(connectionDisposable);
    }

    // Send data to the fff1 characteristic
    @SuppressLint("CheckResult")
    private void sendData(String data) {
        if (activeConnection == null) {
            showToast("No active connection");
            Log.w(TAG, "Cannot send data: No active connection");
            return;
        }

        // Verify if the characteristic supports write operations
        activeConnection.discoverServices()
                .flatMap(services -> services.getCharacteristic(WRITE_CHARACTERISTIC_UUID))
                .subscribe(
                        characteristic -> {
                            int properties = characteristic.getProperties();
                            boolean supportsWrite = (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;

                            if (supportsWrite) {
                                byte[] dataBytes = CustomKeyboardView.hexStringToByteArray(data);
                                activeConnection.writeCharacteristic(WRITE_CHARACTERISTIC_UUID, dataBytes)
                                        .subscribe(
                                                writtenBytes -> {
                                                    Log.d(TAG, "Successfully sent data to fff1: " + data);
                                                    showToast("Sent to fff1: " + data);
                                                },
                                                throwable -> {
                                                    Log.e(TAG, "Write error to fff1: " + throwable.getMessage());
                                                    showToast("Write error: " + throwable.getMessage());
                                                }
                                        );
                            } else {
                                Log.w(TAG, "Characteristic " + WRITE_CHARACTERISTIC_UUID + " does not support write operations");
                                showToast("Characteristic does not support writing");
                            }
                        },
                        throwable -> {
                            Log.e(TAG, "Error retrieving characteristic fff1: " + throwable.getMessage());
                            showToast("Error retrieving characteristic fff1");
                        }
                );
    }

    private void showToast(String message) {
        if (isAdded() && getContext() != null) {
            mainHandler.post(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
        } else {
            Log.w(TAG, "Cannot show toast: Fragment not attached to context");
        }
    }

    private void updateDeviceList() {
        devicesList.clear();
        devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Paired Devices", null));
        for (RxBleDevice device : pairedDevices) {
            devicesList.add(new DeviceItem(DeviceItem.TYPE_DEVICE, null, device));
        }
        devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Scanned BLE Devices", null));
        mainHandler.post(() -> devicesAdapter.notifyDataSetChanged());
    }

    private void savePairedDevice(RxBleDevice device) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> pairedDeviceSet = new HashSet<>(prefs.getStringSet(KEY_PAIRED_DEVICES, new HashSet<>()));
        String deviceEntry = device.getMacAddress() + ";" + sanitizeDeviceName(device.getName());
        pairedDeviceSet.add(deviceEntry);
        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, pairedDeviceSet).commit();
        Log.d(TAG, "Saved paired device: " + deviceEntry);
    }

    private void loadPairedDevices() {
        pairedDevices = new ArrayList<>();
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> pairedDeviceSet = prefs.getStringSet(KEY_PAIRED_DEVICES, new HashSet<>());
        for (String deviceEntry : pairedDeviceSet) {
            String[] parts = deviceEntry.split(";", 2);
            if (parts.length == 0) continue;
            String macAddress = parts[0];
            try {
                RxBleDevice device = rxBleClient.getBleDevice(macAddress);
                pairedDevices.add(device);
                Log.d(TAG, "Loaded paired device: " + macAddress + " (" + (parts.length > 1 ? parts[1] : "Unknown") + ")");
            } catch (Exception e) {
                Log.e(TAG, "Error loading paired device: " + macAddress + ", error: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean scanGranted = true;
            boolean connectGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.BLUETOOTH_SCAN) && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    scanGranted = false;
                }
                if (permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    connectGranted = false;
                }
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED && !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[i])) {
                    showPermissionSettingsDialog();
                }
            }
            if (scanGranted && connectGranted && bluetoothAdapter != null && bluetoothAdapter.isEnabled() && rxBleClient != null && !isScanning && pairedDevices != null && !pairedDevices.isEmpty()) {
                RxBleDevice device = pairedDevices.get(0);
                if (!connectingDevices.contains(device.getMacAddress())) {
                    connectToDevice(device);
                }
            } else {
                showToast("Bluetooth permissions denied");
                bluetoothSwitch.setEnabled(false);
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permissions Required")
                .setMessage("Bluetooth permissions are required to scan and connect to devices. Please grant them in the settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dismiss())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == requireActivity().RESULT_OK) {
                showToast("Bluetooth enabled");
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                        rxBleClient != null && !isScanning && pairedDevices != null && !pairedDevices.isEmpty()) {
                    RxBleDevice device = pairedDevices.get(0);
                    if (!connectingDevices.contains(device.getMacAddress())) {
                        connectToDevice(device);
                    }
                } else {
                    checkPermissions();
                }
            } else {
                bluetoothSwitch.setChecked(false);
                showToast("Bluetooth not enabled");
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stopScan();
        connectionDisposables.clear();
        activeConnection = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
        connectionDisposables.dispose();
        activeConnection = null;
        Log.d(TAG, "Dialog destroyed, paired devices persisted in SharedPreferences");
    }
}