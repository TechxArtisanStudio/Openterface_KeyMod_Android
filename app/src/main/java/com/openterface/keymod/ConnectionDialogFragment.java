package com.openterface.keymod;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.polidea.rxandroidble2.RxBleClient;

/**
 * Dialog Fragment for managing device connections (USB and Bluetooth)
 */
public class ConnectionDialogFragment extends DialogFragment {
    
    private static final String TAG = "ConnectionDialog";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;

    private ConnectionManager connectionManager;
    private RxBleClient rxBleClient;
    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;

    // UI Components
    private TextView connectionStatus;
    private CardView usbCard;
    private CardView bluetoothCard;
    private TextView usbStatus;
    private TextView bluetoothStatus;
    private ImageView usbStatusIcon;
    private ImageView bluetoothStatusIcon;
    private ImageView usbSignal;
    private ImageView bluetoothSignal;
    private CheckBox autoConnectCheckbox;
    private TextView lastConnectedInfo;
    private ImageButton closeButton;

    public interface ConnectionDialogListener {
        void onConnectionChanged(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state);
    }

    private ConnectionDialogListener listener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            Log.d(TAG, "Bound to BluetoothService");
            updateBluetoothStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    public static ConnectionDialogFragment newInstance() {
        return new ConnectionDialogFragment();
    }

    public void setConnectionManager(ConnectionManager manager) {
        this.connectionManager = manager;
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    public void setConnectionDialogListener(ConnectionDialogListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set dialog width to 90% of screen width
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_connection, container, false);
        initViews(view);
        setupListeners();
        updateUI();
        
        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        return view;
    }

    private void initViews(View view) {
        connectionStatus = view.findViewById(R.id.connection_status);
        usbCard = view.findViewById(R.id.usb_card);
        bluetoothCard = view.findViewById(R.id.bluetooth_card);
        usbStatus = view.findViewById(R.id.usb_status);
        bluetoothStatus = view.findViewById(R.id.bluetooth_status);
        usbStatusIcon = view.findViewById(R.id.usb_status_icon);
        bluetoothStatusIcon = view.findViewById(R.id.bluetooth_status_icon);
        usbSignal = view.findViewById(R.id.usb_signal);
        bluetoothSignal = view.findViewById(R.id.bluetooth_signal);
        autoConnectCheckbox = view.findViewById(R.id.auto_connect_checkbox);
        lastConnectedInfo = view.findViewById(R.id.last_connected_info);
        closeButton = view.findViewById(R.id.close_button);

        // Set initial auto-connect state
        if (connectionManager != null) {
            autoConnectCheckbox.setChecked(connectionManager.isAutoConnectEnabled());
        }
    }

    private void setupListeners() {
        closeButton.setOnClickListener(v -> dismiss());

        usbCard.setOnClickListener(v -> {
            Log.d(TAG, "USB card clicked");
            Toast.makeText(requireContext(), "USB card clicked", Toast.LENGTH_SHORT).show();
            handleUsbConnection();
        });

        bluetoothCard.setOnClickListener(v -> {
            Log.d(TAG, "Bluetooth card clicked");
            Toast.makeText(requireContext(), "Bluetooth card clicked", Toast.LENGTH_SHORT).show();
            handleBluetoothConnection();
        });

        autoConnectCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (connectionManager != null) {
                connectionManager.setAutoConnectEnabled(isChecked);
            }
        });

        // Setup connection state listener
        if (connectionManager != null) {
            connectionManager.setConnectionStateListener(new ConnectionManager.ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            updateUI();
                            if (listener != null) {
                                listener.onConnectionChanged(type, state);
                            }
                        });
                    }
                }

                @Override
                public void onConnectionError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> 
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            });
        }
    }

    private void handleUsbConnection() {
        if (connectionManager == null) return;

        ConnectionManager.ConnectionType currentType = connectionManager.getCurrentConnectionType();
        
        if (currentType == ConnectionManager.ConnectionType.USB && 
            connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED) {
            // Disconnect USB
            connectionManager.disconnect();
            Toast.makeText(requireContext(), "USB disconnected", Toast.LENGTH_SHORT).show();
        } else {
            // Disconnect Bluetooth if connected
            if (currentType == ConnectionManager.ConnectionType.BLUETOOTH && isServiceBound && bluetoothService != null) {
                bluetoothService.disconnect();
            }
            
            // Connect USB
            boolean success = connectionManager.connectUsb();
            if (success) {
                Toast.makeText(requireContext(), "USB connected", Toast.LENGTH_SHORT).show();
            }
        }
        
        updateUI();
    }

    private void handleBluetoothConnection() {
        if (connectionManager == null) return;

        Log.d(TAG, "handleBluetoothConnection called");
        ConnectionManager.ConnectionType currentType = connectionManager.getCurrentConnectionType();
        
        if (currentType == ConnectionManager.ConnectionType.BLUETOOTH && 
            connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED) {
            // Disconnect Bluetooth
            Log.d(TAG, "Disconnecting Bluetooth");
            if (isServiceBound && bluetoothService != null) {
                bluetoothService.disconnect();
                connectionManager.disconnect();
                Toast.makeText(requireContext(), "Bluetooth disconnected", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Disconnect USB if connected
            if (currentType == ConnectionManager.ConnectionType.USB) {
                Log.d(TAG, "Disconnecting USB first");
                connectionManager.disconnect();
            }
            
            // Check permissions and show Bluetooth dialog
            Log.d(TAG, "Checking Bluetooth permissions");
            if (checkBluetoothPermissions()) {
                Log.d(TAG, "Permissions granted, showing Bluetooth dialog");
                showBluetoothDeviceDialog();
            } else {
                Log.d(TAG, "Requesting Bluetooth permissions");
                requestBluetoothPermissions();
            }
        }
        
        updateUI();
    }

    private void showBluetoothDeviceDialog() {
        Log.d(TAG, "showBluetoothDeviceDialog called");
        // Show the existing BluetoothDialogFragment
        BluetoothDialogFragment dialog = new BluetoothDialogFragment();
        dialog.setRxBleClient(rxBleClient);
        dialog.setConnectionListener(isConnected -> {
            Log.d(TAG, "Bluetooth connection state changed: " + isConnected);
            if (isConnected) {
                // Wait a bit for the service to update
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isServiceBound && bluetoothService != null && connectionManager != null) {
                        com.polidea.rxandroidble2.RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
                        if (connectedDevice != null) {
                            Log.d(TAG, "Updating ConnectionManager with BLE device: " + connectedDevice.getMacAddress());
                            connectionManager.connectBluetooth(connectedDevice);
                            Toast.makeText(requireContext(), "Bluetooth connected", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.w(TAG, "Connected device is null");
                        }
                    } else {
                        Log.w(TAG, "Service not bound or ConnectionManager is null - isServiceBound: " + isServiceBound + ", bluetoothService: " + (bluetoothService != null) + ", connectionManager: " + (connectionManager != null));
                    }
                    updateUI();
                }, 500);
            } else {
                if (connectionManager != null && 
                    connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH) {
                    connectionManager.disconnect();
                }
                updateUI();
            }
        });
        
        try {
            Log.d(TAG, "Showing BluetoothDialogFragment");
            dialog.show(getParentFragmentManager(), "BluetoothDialog");
        } catch (Exception e) {
            Log.e(TAG, "Error showing BluetoothDialogFragment: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error opening Bluetooth dialog: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 and above
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 and below - need location permission for BLE scanning
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        Log.d(TAG, "Requesting permissions from Fragment");
        Toast.makeText(requireContext(), "Requesting Bluetooth permissions...", Toast.LENGTH_SHORT).show();
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 and above
            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // Android 11 and below
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void updateUI() {
        if (connectionManager == null) return;

        ConnectionManager.ConnectionType type = connectionManager.getCurrentConnectionType();
        ConnectionManager.ConnectionState state = connectionManager.getCurrentConnectionState();

        // Update main status text
        String statusText = "";
        switch (state) {
            case CONNECTED:
                statusText = getString(R.string.status_connected) + " - " + 
                            (type == ConnectionManager.ConnectionType.USB ? "USB" : "Bluetooth");
                break;
            case CONNECTING:
                statusText = getString(R.string.status_connecting);
                break;
            case DISCONNECTED:
                statusText = getString(R.string.status_disconnected);
                break;
        }
        connectionStatus.setText(statusText);

        // Update USB status
        updateUsbStatus();

        // Update Bluetooth status
        updateBluetoothStatus();

        // Update last connected info
        updateLastConnectedInfo();
    }

    private void updateUsbStatus() {
        if (connectionManager == null) return;

        boolean isUsbConnected = connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.USB &&
                                connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED;

        if (isUsbConnected) {
            usbStatus.setText(R.string.connected);
            usbStatusIcon.setImageResource(R.drawable.ic_connection_connected);
            usbStatusIcon.setColorFilter(0xFF4CAF50);
            if (usbSignal != null) {
                usbSignal.setVisibility(View.VISIBLE);
                usbSignal.setColorFilter(0xFF4CAF50);
            }
        } else {
            usbStatus.setText(R.string.not_connected);
            usbStatusIcon.setImageResource(R.drawable.ic_connection_disconnected);
            usbStatusIcon.setColorFilter(0xFF9E9E9E);
            if (usbSignal != null) usbSignal.setVisibility(View.GONE);
        }
    }

    private void updateBluetoothStatus() {
        if (connectionManager == null) return;

        boolean isBluetoothConnected = connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH &&
                                      connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED;

        if (isBluetoothConnected) {
            String deviceName = connectionManager.getLastBleDeviceName();
            bluetoothStatus.setText(deviceName != null ? deviceName : getString(R.string.connected));
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth);
            bluetoothStatusIcon.setColorFilter(0xFF4CAF50);
            if (bluetoothSignal != null) {
                bluetoothSignal.setVisibility(View.VISIBLE);
                bluetoothSignal.setColorFilter(0xFF4CAF50);
            }
        } else {
            bluetoothStatus.setText(R.string.not_connected);
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth);
            bluetoothStatusIcon.setColorFilter(0xFF9E9E9E);
            if (bluetoothSignal != null) bluetoothSignal.setVisibility(View.GONE);
        }
    }

    private void updateLastConnectedInfo() {
        if (connectionManager == null) return;

        String lastType = connectionManager.getLastConnectionType();
        
        if ("NONE".equals(lastType) || lastType.isEmpty()) {
            lastConnectedInfo.setText(R.string.last_connected_none);
            lastConnectedInfo.setVisibility(View.VISIBLE);
        } else if ("USB".equals(lastType)) {
            lastConnectedInfo.setText(R.string.last_connected_usb);
            lastConnectedInfo.setVisibility(View.VISIBLE);
        } else if ("BLUETOOTH".equals(lastType)) {
            String deviceName = connectionManager.getLastBleDeviceName();
            if (deviceName != null) {
                lastConnectedInfo.setText(getString(R.string.last_connected_bluetooth, deviceName));
                lastConnectedInfo.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult called with requestCode: " + requestCode);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            Log.d(TAG, "Permission result - length: " + grantResults.length + ", result: " + (grantResults.length > 0 ? grantResults[0] : "none"));
            
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }
            
            if (allGranted) {
                Log.d(TAG, "Bluetooth permissions GRANTED, showing device dialog");
                Toast.makeText(requireContext(), "Bluetooth permission granted!", Toast.LENGTH_SHORT).show();
                showBluetoothDeviceDialog();
            } else {
                Log.d(TAG, "Bluetooth permissions DENIED");
                Toast.makeText(requireContext(), 
                    "Bluetooth permission is required to scan and connect to devices. Please grant permission in app settings.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
}
