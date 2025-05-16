package com.example.dual_modekeyboard;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.fragment.CompositeFragment;
import com.example.fragment.KeyboardFragment;
import com.example.serial.UsbDeviceManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView textView;
    private UsbSerialPort port;

    private boolean isReading = false;
    private Handler mSerialAsyncHandler;
    private UsbDeviceManager.OnDataReadListener onDataReadListener;
    private static final String ACTION_USB_PERMISSION = "com.example.ch32v208serial.USB_PERMISSION";

    private Button keyBoard, mouse, keyBoardMouse, question, info;
    private Drawable keyBoardDrawable, mouseDrawable, keyBoardMouseDrawable, questionDrawable, infoDrawable;
    private Button activeButton; // Track the currently active button


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

        initializeUIComponents();

        setupUsbSerial();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        // Set default fragment and button state
        setActiveButton(keyBoardMouse, keyBoardMouseDrawable);
        showCompositeFragment();



//        RightClickButton.setOnClickListener(new View.OnClickListener() {
//           @Override
//           public void onClick(View v) {
//
//           }
//       });
//
//        leftClickButton.setOnClickListener(v -> {
//            // Handle left click action
//            String sendKBData = String.format("57AB00050501010000000E");
////            sendKBData += CompositeFragment.makeChecksum(sendKBData);
//            byte[] sendKBDataBytes = CompositeFragment.hexStringToByteArray(sendKBData);
//            try {
//                port.write(sendKBDataBytes, 20);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            CustomKeyboardView.releaseAllData();
//        });
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

        keyBoard = findViewById(R.id.keyBoard);
        mouse = findViewById(R.id.mouse);
        keyBoardMouse = findViewById(R.id.keyBoardMouse);
        question = findViewById(R.id.question);
        info = findViewById(R.id.info);

        if (keyBoard != null) keyBoardDrawable = keyBoard.getCompoundDrawables()[1];
        if (mouse != null) mouseDrawable = mouse.getCompoundDrawables()[1];
        if (keyBoardMouse != null) keyBoardMouseDrawable = keyBoardMouse.getCompoundDrawables()[1];
        if (question != null) questionDrawable = question.getCompoundDrawables()[1];
        if (info != null) infoDrawable = info.getCompoundDrawables()[1];

        setupButtonListeners();
    }

    private void setupButtonListeners() {
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
            resetButtonColors(); // Reset colors of keyBoard, mouse, and keyBoardMouse
            setActiveButton(button, drawable); // Set the clicked button as active
            onClickAction.run();
        });
    }

    private void setActiveButton(Button button, Drawable drawable) {
        if (button == null || drawable == null) return;
        int activeColor = getResources().getColor(android.R.color.holo_blue_light);
        button.setTextColor(activeColor);
        drawable.setColorFilter(activeColor, PorterDuff.Mode.SRC_IN);
        activeButton = button; // Update the active button
    }

    private void resetButtonColors() {
        // Reset only keyBoard, mouse, and keyBoardMouse to their initial colors
        int defaultColor = getResources().getColor(android.R.color.black); // Assuming white as default text color
        resetButton(keyBoard, keyBoardDrawable, defaultColor);
        resetButton(mouse, mouseDrawable, defaultColor);
        resetButton(keyBoardMouse, keyBoardMouseDrawable, defaultColor);
    }

    private void resetButton(Button button, Drawable drawable, int defaultColor) {
        if (button != null && drawable != null) {
            button.setTextColor(defaultColor);
            drawable.clearColorFilter(); // Reset drawable to original color
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }

    private void startReading() {
        new Thread(() -> {
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
                }
            } catch (IOException e) {
                e.printStackTrace();
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
            updateFragmentsWithPort(port);
            startReading();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open serial port", Toast.LENGTH_LONG).show();
        }
    }
}