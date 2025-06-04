package com.example.dual_modekeyboard;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.fragment.CompositeFragment;
import com.example.fragment.KeyboardFragment;
import com.example.serial.UsbDeviceManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.polidea.rxandroidble2.RxBleClient;

import java.io.IOException;
import java.util.List;

import io.reactivex.disposables.Disposable;
import android.Manifest;
import com.polidea.rxandroidble2.scan.ScanSettings;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView textView;
    private UsbSerialPort port;
    private boolean isReading = false;
    private Handler mSerialAsyncHandler;
    private UsbDeviceManager.OnDataReadListener onDataReadListener;
    private static final String ACTION_USB_PERMISSION = "com.example.ch32v208serial.USB_PERMISSION";

    private Button bluetooth, keyBoard, mouse, keyBoardMouse, question, info;
    private Drawable bluetoothDrawable, keyBoardDrawable, mouseDrawable, keyBoardMouseDrawable, questionDrawable, infoDrawable;
    private Button activeButton;

    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            Log.d(TAG, "Bound to BluetoothService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    setupUsbSerial();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (port != null) {
                    try {
                        port.close();
                        port = null;
                        updateFragmentsWithPort(null);
                        Toast.makeText(context, "USB device disconnected", Toast.LENGTH_SHORT).show();
                        textView.setText("USB device disconnected");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing USB port: " + e.getMessage());
                    }
                }
            }
        }
    };

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            setupUsbSerial(); // Reinitialize after the permission is granted
                        }
                    } else {
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show();
                        textView.setText("USB permission denied");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUIComponents();

        rxBleClient = RxBleClient.create(this);
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        setActiveButton(keyBoardMouse, keyBoardMouseDrawable);
        showCompositeFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        setupUsbSerial(); // Try to initialize the USB device
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(usbReceiver);
        unregisterReceiver(usbPermissionReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanSubscription != null && !scanSubscription.isDisposed()) {
            scanSubscription.dispose();
        }
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        Intent intent = new Intent(this, BluetoothService.class);
        stopService(intent); // stop servic
    }

    private void initializeUIComponents() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        textView = findViewById(R.id.textView);

        bluetooth = findViewById(R.id.bluetooth);
        keyBoard = findViewById(R.id.keyBoard);
        mouse = findViewById(R.id.mouse);
        keyBoardMouse = findViewById(R.id.keyBoardMouse);
        question = findViewById(R.id.question);
        info = findViewById(R.id.info);

        if (bluetooth != null) bluetoothDrawable = bluetooth.getCompoundDrawables()[1];
        if (keyBoard != null) keyBoardDrawable = keyBoard.getCompoundDrawables()[1];
        if (mouse != null) mouseDrawable = mouse.getCompoundDrawables()[1];
        if (keyBoardMouse != null) keyBoardMouseDrawable = keyBoardMouse.getCompoundDrawables()[1];
        if (question != null) questionDrawable = question.getCompoundDrawables()[1];
        if (info != null) infoDrawable = info.getCompoundDrawables()[1];

        setupButtonListeners();
    }

    private void startScan() {
        ScanSettings scanSettings = new ScanSettings.Builder().build();
        scanSubscription = rxBleClient.scanBleDevices(scanSettings)
                .subscribe(
                        (scanResult) -> {
                            String deviceName = scanResult.getBleDevice().getName();
                            Log.d("ScanResult", "find device: " + (deviceName != null ? deviceName : "unknown device"));
                        },
                        (Throwable throwable) -> {
                            Log.e("ScanError", "scan error", throwable);
                        }
                );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                BluetoothDialogFragment dialog = new BluetoothDialogFragment();
                dialog.setRxBleClient(rxBleClient);
                dialog.show(getSupportFragmentManager(), "BluetoothDialog");
            } else {
                Log.e("Permission", "location permission denied");
            }
        }
    }

    private void setupButtonListeners() {
        if (bluetooth != null) {
            setOnClickListener(bluetooth, bluetoothDrawable, () -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                            PERMISSION_REQUEST_CODE);
                } else {
                    BluetoothDialogFragment dialog = new BluetoothDialogFragment();
                    dialog.setRxBleClient(rxBleClient);
                    dialog.show(getSupportFragmentManager(), "BluetoothDialog");
                }
            });
        }

        if (keyBoard != null) {
            setOnClickListener(keyBoard, keyBoardDrawable, this::showKeyboardFragment);
        }

        if (keyBoardMouse != null) {
            setOnClickListener(keyBoardMouse, keyBoardMouseDrawable, this::showCompositeFragment);
        }

        if (mouse != null) {
            setOnClickListener(mouse, mouseDrawable, () -> {
                // Toast.makeText(this, "Mouse button clicked", Toast.LENGTH_SHORT).show();
            });
        }

        if (question != null) {
            question.setOnClickListener(v -> {
                // Toast.makeText(this, "Question button clicked", Toast.LENGTH_SHORT).show();
            });
        }

        if (info != null) {
            info.setOnClickListener(v -> {
                // Toast.makeText(this, "Info button clicked", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setOnClickListener(Button button, Drawable drawable, Runnable onClickAction) {
        if (button == null || drawable == null) return;
        button.setOnClickListener(v -> {
            resetButtonColors();
            setActiveButton(button, drawable);
            onClickAction.run();
        });
    }

    private void setActiveButton(Button button, Drawable drawable) {
        if (button == null || drawable == null) return;
        int activeColor = getResources().getColor(android.R.color.holo_blue_light);
        button.setTextColor(activeColor);
        drawable.setColorFilter(activeColor, PorterDuff.Mode.SRC_IN);
        activeButton = button;
    }

    private void resetButtonColors() {
        int defaultColor = getResources().getColor(android.R.color.black);
        resetButton(keyBoard, keyBoardDrawable, defaultColor);
        resetButton(mouse, mouseDrawable, defaultColor);
        resetButton(keyBoardMouse, keyBoardMouseDrawable, defaultColor);
    }

    private void resetButton(Button button, Drawable drawable, int defaultColor) {
        if (button != null && drawable != null) {
            button.setTextColor(defaultColor);
            drawable.clearColorFilter();
        }
    }

    private void showKeyboardFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, KeyboardFragment.newInstance(port));
        transaction.commit();
    }

    private void showCompositeFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, CompositeFragment.newInstance(port));
        transaction.commit();
    }

    private void updateFragmentsWithPort(UsbSerialPort newPort) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof KeyboardFragment) {
            ((KeyboardFragment) currentFragment).port = newPort;
            CustomKeyboardView keyboardView = currentFragment.getView() != null ?
                    currentFragment.getView().findViewById(R.id.keyboard_view) : null;
            if (keyboardView != null) {
                keyboardView.setPort(newPort);
            }
        } else if (currentFragment instanceof CompositeFragment) {
            ((CompositeFragment) currentFragment).port = newPort;
            CustomKeyboardView keyboardView = currentFragment.getView() != null ?
                    currentFragment.getView().findViewById(R.id.keyboard_view) : null;
            if (keyboardView != null) {
                keyboardView.setPort(newPort);
            }
        }
    }

    private void startReading() {
        new Thread(() -> {
            try {
                while (isReading) {
                    byte[] buffer = new byte[1024];
                    int numBytesRead = port.read(buffer, 5);
                    if (numBytesRead > 0) {
                        StringBuilder allReadData = new StringBuilder();
                        for (int i = 0; i < numBytesRead; i++) {
                            allReadData.append(String.format("%02X ", buffer[i]));
                        }
                        Log.d(TAG, "Read data: " + allReadData.toString().trim());

                        if (onDataReadListener != null) {
                            onDataReadListener.onDataRead();
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading USB data: " + e.getMessage());
            }
        }).start();
    }

    private void setupUsbSerial() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "No USB serial devices found", Toast.LENGTH_LONG).show();
            textView.setText("No USB serial devices found");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // check USB permission
        if (!manager.hasPermission(device)) {
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(device, pendingIntent);
            return;
        }

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            Toast.makeText(this, "Failed to open USB device connection", Toast.LENGTH_LONG).show();
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(manager.openDevice(device));
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            Toast.makeText(this, "Successful to open serial port", Toast.LENGTH_LONG).show();
            textView.setText("Successful to open serial port");
            isReading = true;
            updateFragmentsWithPort(port);
            startReading();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open serial port: " + e.getMessage());
            Toast.makeText(this, "Failed to open serial port", Toast.LENGTH_LONG).show();
        }
    }
}