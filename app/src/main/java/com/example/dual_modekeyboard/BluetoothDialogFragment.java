package com.example.dual_modekeyboard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.disposables.Disposable;

public class BluetoothDialogFragment extends DialogFragment {

    private static final String TAG = "BluetoothDialogFragment";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1002;
    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_PAIRED_DEVICES = "paired_devices";

    private BluetoothAdapter bluetoothAdapter;
    private Switch bluetoothSwitch;
    private ListView devicesListView;
    private Button closeButton;
    private Button scanButton;
    private CustomDeviceAdapter devicesAdapter;
    private ArrayList<DeviceItem> devicesList;
    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private ArrayList<RxBleDevice> pairedDevices;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private final Object scanLock = new Object();
    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    // Callback interface to notify MainActivity of connection state changes
    public interface BluetoothConnectionListener {
        void onBluetoothConnectionChanged(boolean isConnected);
    }

    private BluetoothConnectionListener connectionListener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            boolean isConnected = bluetoothService.isConnected();
            ((MainActivity) requireActivity()).isBluetoothConnected = isConnected;
            // Notify MainActivity of initial connection state
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionChanged(isConnected);
            }
            // Check for connected device and update list
            if (bluetoothAdapter.isEnabled() && isServiceBound) {
                RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
                if (connectedDevice != null) {
                    // Ensure connected device is in pairedDevices
                    boolean exists = false;
                    for (RxBleDevice device : pairedDevices) {
                        if (device.getMacAddress().equals(connectedDevice.getMacAddress()) &&
                                sanitizeDeviceName(device.getName()).equals(sanitizeDeviceName(connectedDevice.getName()))) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        pairedDevices.add(connectedDevice);
                        savePairedDevice(connectedDevice);
                    }
                    updateDeviceList();
                } else if (!pairedDevices.isEmpty()) {
                    bluetoothService.connectToDevice(pairedDevices.get(0));
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            ((MainActivity) requireActivity()).isBluetoothConnected = false;
            // Notify MainActivity of disconnection
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionChanged(false);
            }
        }
    };

    private static class DeviceItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_DEVICE = 1;

        int type;
        String title;
        RxBleDevice bleDevice;

        DeviceItem(int type, String title, RxBleDevice bleDevice) {
            this.type = type;
            this.title = title;
            this.bleDevice = bleDevice;
        }
    }

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
//                convert comienza a partir de aquíconvertView.setOnClickListener(null);
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
            return 2;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).type == DeviceItem.TYPE_DEVICE;
        }
    }

    private String sanitizeDeviceName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    // Set the connection listener
    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_bluetooth, container, false);
        loadPairedDevices();
        initializeUIComponents(view);
        initializeBluetooth();
        bindService();
        if (!isServiceBound) {
            bindService(); // Retry binding if not already bound
        }
        // Refresh device list to show connected device
        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            updateDeviceList();
        }
        return view;
    }

    private void bindService() {
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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
                    if (isServiceBound) {
                        // Check if the device is already connected
                        if (bluetoothService.isConnected() &&
                                bluetoothService.getConnectedDevice() != null &&
                                bluetoothService.getConnectedDevice().getMacAddress().equals(selectedBleDevice.getMacAddress())) {
                            showToast("Device is already connected");
                            return;
                        }
                        // Verify name and MAC address match a paired device
                        boolean isPaired = false;
                        for (RxBleDevice device : pairedDevices) {
                            if (device.getMacAddress().equals(selectedBleDevice.getMacAddress()) &&
                                    sanitizeDeviceName(device.getName()).equals(deviceName)) {
                                isPaired = true;
                                break;
                            }
                        }
                        if (isPaired || item.type == DeviceItem.TYPE_DEVICE) {
                            bluetoothService.connectToDevice(selectedBleDevice);
                            // Add to pairedDevices if not already present
                            if (!isPaired) {
                                pairedDevices.add(selectedBleDevice);
                                savePairedDevice(selectedBleDevice);
                                updateDeviceList();
                            }
                            // Notify MainActivity of connection
                            if (connectionListener != null) {
                                connectionListener.onBluetoothConnectionChanged(true);
                            }
                        } else {
                            showToast("Device name or MAC address does not match paired device");
                        }
                    }
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

        if (hasBluetoothConnect && hasBluetoothScan) {
            bluetoothSwitch.setEnabled(true);
            bluetoothSwitch.setChecked(bluetoothAdapter.isEnabled());
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
        } else if (isServiceBound && bluetoothService.isConnected()) {
            // Skip scan if already connected
            showToast("Already connected to a device");
            updateDeviceList();
        } else if (!isScanning && isServiceBound && !pairedDevices.isEmpty()) {
            bluetoothService.connectToDevice(pairedDevices.get(0));
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
            // Notify MainActivity of disconnection
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionChanged(false);
            }
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
            }
            if (isScanning) {
                isScanning = false;
                mainHandler.post(() -> {
                    scanButton.setEnabled(true);
                    showToast("Scan stopped");
                });
            }
        }
    }

    private void showToast(String message) {
        if (isAdded() && getContext() != null) {
            mainHandler.post(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
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

        // Remove any existing entry with the same MAC address to avoid duplicates
        pairedDeviceSet.removeIf(entry -> entry.startsWith(device.getMacAddress() + ";"));
        pairedDeviceSet.add(deviceEntry);

        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, pairedDeviceSet).apply();
        Log.d(TAG, "Saved paired device: " + deviceEntry);
    }

    private void loadPairedDevices() {
        pairedDevices = new ArrayList<>();
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> pairedDeviceSet = prefs.getStringSet(KEY_PAIRED_DEVICES, new HashSet<>());
        Set<String> uniqueMacAddresses = new HashSet<>();

        for (String deviceEntry : pairedDeviceSet) {
            String[] parts = deviceEntry.split(";", 2);
            if (parts.length < 2) continue; // Skip invalid entries
            String macAddress = parts[0];
            String storedName = parts[1];

            if (!uniqueMacAddresses.add(macAddress)) {
                Log.w(TAG, "Duplicate MAC address found in SharedPreferences: " + macAddress);
                continue;
            }

            try {
                RxBleDevice device = rxBleClient.getBleDevice(macAddress);
                // Only add if the current device name matches the stored name
                if (sanitizeDeviceName(device.getName()).equals(storedName)) {
                    pairedDevices.add(device);
                    Log.d(TAG, "Loaded paired device: " + macAddress + ", name: " + storedName);
                } else {
                    Log.w(TAG, "Device name mismatch for MAC " + macAddress + ": expected " + storedName + ", got " + sanitizeDeviceName(device.getName()));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading paired device: " + macAddress + ", error: " + e.getMessage());
            }
        }

        // Update SharedPreferences to remove invalid entries
        if (pairedDeviceSet.size() != pairedDevices.size()) {
            Set<String> cleanedDeviceSet = new HashSet<>();
            for (RxBleDevice device : pairedDevices) {
                String deviceEntry = device.getMacAddress() + ";" + sanitizeDeviceName(device.getName());
                cleanedDeviceSet.add(deviceEntry);
            }
            prefs.edit().putStringSet(KEY_PAIRED_DEVICES, cleanedDeviceSet).apply();
            Log.d(TAG, "Cleaned up invalid entries in SharedPreferences");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[i])) {
                        showPermissionSettingsDialog();
                    }
                }
            }
            if (allGranted && isServiceBound && !pairedDevices.isEmpty()) {
                bluetoothService.connectToDevice(pairedDevices.get(0));
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
                if (isServiceBound && !pairedDevices.isEmpty()) {
                    bluetoothService.connectToDevice(pairedDevices.get(0));
                }
            } else {
                bluetoothSwitch.setChecked(false);
                showToast("Bluetooth not enabled");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        Log.d(TAG, "Dialog destroyed, connection maintained by BluetoothService");
    }
}