package com.example.dual_modekeyboard;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.serial.UsbDeviceManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private EditText editText;
    private TouchPadView touchPad;
    private CustomKeyboardView keyboardView;
    private Spinner spinner;
    private TextView textView;
    private UsbSerialPort port;

    private boolean isReading = false;
    private Handler mSerialAsyncHandler;
    private UsbDeviceManager.OnDataReadListener onDataReadListener;
    private static final String ACTION_USB_PERMISSION = "com.example.ch32v208serial.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    setupUsbSerial(); // Call your setup method to handle the new device
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (port != null) {
                    try {
                        port.close(); // Close the port when device is detached
                        port = null;
                        if (keyboardView != null) {
                            keyboardView.setPort(null); // Clear port in keyboardView
                        }
                        Toast.makeText(context, "USB device disconnected", Toast.LENGTH_SHORT).show();
                        textView.setText("USB device disconnected");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUIComponents(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        setupUsbSerial();

        // Register the BroadcastReceiver for USB device events
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver); // Unregister the receiver
    }

    private void startReading() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
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

//
//                        if (numBytesRead > 0) {
//                            // 处理接收到的数据
//                            String receivedData = new String(buffer, 0, numBytesRead);
//                            // 在主线程中更新UI
//                            runOnUiThread(() -> {
//                                // 例如更新TextView显示接收到的数据
//                                textView.setText(receivedData);
//                            });
//                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
//
//    private void startReading() {
//        isReading = true;
//        mSerialAsyncHandler.post(() -> {
//            byte[] buffer = new byte[1024];
//            while (isReading) {
//                try {
//                    int numBytesRead = port.read(buffer, 5);
//                    if (numBytesRead > 0) {
//                        StringBuilder allReadData = new StringBuilder();
//                        for (int i = 0; i < numBytesRead; i++) {
//                            allReadData.append(String.format("%02X ", buffer[i]));
//                        }
//                        Log.d(TAG, "Read data: " + allReadData.toString().trim());
//
//                        if (onDataReadListener != null) {
//                            onDataReadListener.onDataRead();
//                        }
//                    }
//                } catch (IOException e) {
//                    Log.e(TAG, "Error reading from port", e);
//                    break;
//                }
//            }
//        });
//    }

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
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        manager.requestPermission(device, permissionIntent);

        if (!manager.hasPermission(device)) {
            Toast.makeText(this, "Permission not granted for USB device", Toast.LENGTH_LONG).show();
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
            keyboardView.setPort(port);
            startReading();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open serial port", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUIComponents(int layoutResId) {
        // Fullscreen and Immersive mode setup
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Initialize UI components
        editText = findViewById(R.id.editText);
        keyboardView = findViewById(R.id.keyboard_view);
        spinner = findViewById(R.id.planets_spinner);

        // Initialize TouchPad only if it exists in the layout
        touchPad = findViewById(R.id.touchPad);

        // Setup TouchPad if present
        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float deltaX, float deltaY) {
                    Log.d("TouchPad", "Move: " + deltaX + ", " + deltaY);
                }

                @Override
                public void onTouchClick() {
                    Log.d("TouchPad", "Click");
                }

                @Override
                public void onTouchDoubleClick() {
                    Log.d("TouchPad", "Double Click");
                }

                @Override
                public void onTouchRightClick() {
                    Log.d("TouchPad", "Right Click");
                }
            });
        }

        // Setup keyboard and EditText if present
        if (keyboardView != null && editText != null) {
            keyboardView.setEditText(editText);
            editText.requestFocus();
        }

        // Setup Spinner
        if (spinner != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this,
                    R.array.planets_array,
                    android.R.layout.simple_spinner_item
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(this);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedPlanet = parent.getItemAtPosition(position).toString();
//        Toast.makeText(this, "Selected: " + selectedPlanet, Toast.LENGTH_SHORT).show();
        Log.d("Spinner", "Selected planet: " + selectedPlanet);

        if (selectedPlanet.equals("Touchpad + Keyboard")) {
            // Already on activity_main, no need to change
            if (getContentViewLayoutId() != R.layout.activity_main) {
                setContentView(R.layout.activity_main);
                initializeUIComponents(R.layout.activity_main);
                spinner.setSelection(position); // Restore spinner selection
            }
        } else if (selectedPlanet.equals("Full KeyBoard")) {
            // Switch to layout_keyboard_only
            if (getContentViewLayoutId() != R.layout.layout_keyboard_only) {
                setContentView(R.layout.layout_keyboard_only);
                initializeUIComponents(R.layout.layout_keyboard_only);
                spinner.setSelection(position); // Restore spinner selection
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d("Spinner", "Nothing selected");
    }

    // Helper method to get current layout ID (not directly available, so we assume based on logic)
    private int getContentViewLayoutId() {
        // Since Android doesn't provide a direct way to get the current layout ID,
        // we assume the layout based on the presence of touchPad (unique to activity_main)
        return (touchPad != null) ? R.layout.activity_main : R.layout.layout_keyboard_only;
    }
}