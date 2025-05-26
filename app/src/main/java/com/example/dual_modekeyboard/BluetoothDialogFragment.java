package com.example.dual_modekeyboard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import io.reactivex.disposables.Disposable;

public class BluetoothDialogFragment extends DialogFragment {

    private static final String TAG = "BluetoothDialogFragment";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1002;

    private BluetoothAdapter bluetoothAdapter;
    private Switch bluetoothSwitch;
    private ListView devicesListView;
    private Button closeButton;
    private CustomDeviceAdapter devicesAdapter;
    private ArrayList<DeviceItem> devicesList;
    private RxBleClient rxBleClient;
    private Disposable scanSubscription;

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
                String deviceName = item.bleDevice.getName() != null ? item.bleDevice.getName() : "Unknown";
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

    // Set RxBleClient from MainActivity
    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_bluetooth, container, false);
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
        Button scanButton = view.findViewById(R.id.scan_button);

        devicesList = new ArrayList<>();
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
                String deviceName = selectedBleDevice.getName() != null ? selectedBleDevice.getName() : "Unknown";
                Log.d(TAG, "Selected BLE device: " + deviceName + " (" + selectedBleDevice.getMacAddress() + ")");
                Toast.makeText(requireContext(), "Selected: " + deviceName, Toast.LENGTH_SHORT).show();
                // TODO: Implement connection logic here if needed
            }
        });

        closeButton.setOnClickListener(v -> dismiss());

        scanButton.setOnClickListener(v -> {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                startBleScan();
            } else {
                Toast.makeText(requireContext(), "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "Bluetooth not supported on this device");
            Toast.makeText(requireContext(), "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            bluetoothSwitch.setEnabled(false);
            return;
        }

        boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        boolean hasBluetoothScan = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Permissions - BLUETOOTH_CONNECT: " + hasBluetoothConnect + ", BLUETOOTH_SCAN: " + hasBluetoothScan);

        if (hasBluetoothConnect && hasBluetoothScan) {
            bluetoothSwitch.setEnabled(true);
            bluetoothSwitch.setChecked(bluetoothAdapter.isEnabled());
            if (bluetoothAdapter.isEnabled() && rxBleClient != null) {
                startBleScan();
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
        } else {
            startBleScan();
        }
    }

    private void disableBluetooth() {
        if (bluetoothAdapter == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            devicesList.clear();
            devicesList.add(new DeviceItem(DeviceItem.TYPE_HEADER, "Scanned BLE Devices", null));
            devicesAdapter.notifyDataSetChanged();
            if (scanSubscription != null && !scanSubscription.isDisposed()) {
                scanSubscription.dispose();
            }
            Toast.makeText(requireContext(), "Bluetooth disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBleScan() {
        if (rxBleClient == null) {
            Log.e(TAG, "RxBleClient is not initialized");
            Toast.makeText(requireContext(), "Bluetooth scanning not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_BLUETOOTH_PERMISSIONS);
            return;
        }

        // Clear previous devices, keep header
        while (devicesList.size() > 1) {
            devicesList.remove(1);
        }
        devicesAdapter.notifyDataSetChanged();

        // Configure scanning settings
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Start scanning
        scanSubscription = rxBleClient.scanBleDevices(scanSettings)
                .subscribe(
                        scanResult -> {
                            RxBleDevice device = scanResult.getBleDevice();
                            String deviceName = device.getName() != null ? device.getName() : "Unknown";
                            String deviceAddress = device.getMacAddress();
                            Log.d(TAG, "Found device: " + deviceName + " (" + deviceAddress + ")");

                            // Avoid duplicates
                            for (DeviceItem item : devicesList) {
                                if (item.bleDevice != null && item.bleDevice.getMacAddress().equals(deviceAddress)) {
                                    return;
                                }
                            }

                            devicesList.add(new DeviceItem(DeviceItem.TYPE_DEVICE, null, device));
                            devicesAdapter.notifyDataSetChanged();
                        },
                        throwable -> {
                            Log.e(TAG, "Scan error", throwable);
                            Toast.makeText(requireContext(), "Scan error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                );
        Toast.makeText(requireContext(), "Scanning for BLE devices...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean scanGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.BLUETOOTH_SCAN) && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    scanGranted = false;
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[i])) {
                        showPermissionSettingsDialog();
                    }
                }
            }
            if (scanGranted && bluetoothAdapter != null && bluetoothAdapter.isEnabled() && rxBleClient != null) {
                startBleScan();
            } else {
                Toast.makeText(requireContext(), "Bluetooth permissions denied", Toast.LENGTH_LONG).show();
                bluetoothSwitch.setEnabled(false);
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permissions Required")
                .setMessage("Bluetooth permissions are required to scan devices. Please grant them in the app settings.")
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
                Toast.makeText(requireContext(), "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        rxBleClient != null) {
                    startBleScan();
                } else {
                    checkPermissions();
                }
            } else {
                bluetoothSwitch.setChecked(false);
                Toast.makeText(requireContext(), "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scanSubscription != null && !scanSubscription.isDisposed()) {
            scanSubscription.dispose();
        }
        Log.d(TAG, "Scan subscription disposed");
    }
}