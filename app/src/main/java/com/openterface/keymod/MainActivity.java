package com.openterface.keymod;

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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.openterface.fragment.CompositeFragment;
import com.openterface.fragment.GamepadFragment;
import com.openterface.fragment.KeyboardFragment;
import com.openterface.fragment.MacrosFragment;
import com.openterface.fragment.MouseFragment;
import com.openterface.fragment.NumpadFragment;
import com.openterface.fragment.ShortcutFragment;
import com.openterface.fragment.ShortcutHubFragment;
import com.openterface.fragment.VoiceInputFragment;
import com.openterface.serial.UsbDeviceManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;
import android.Manifest;
import com.polidea.rxandroidble2.scan.ScanSettings;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity implements BluetoothDialogFragment.BluetoothConnectionListener {

    private static final String TAG = "MainActivity";
    private UsbSerialPort port;
    private boolean isReading = false;
    private Handler mSerialAsyncHandler;
    private UsbDeviceManager.OnDataReadListener onDataReadListener;
    private static final String ACTION_USB_PERMISSION = "com.openterface.ch32v208serial.USB_PERMISSION";

    // New UI components for Phase 1
    private ImageView connectionButton;
    private ImageView signalBars;
    private ImageButton menuButton;
    private DrawerLayout drawerLayout;
    private String currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;

    // Sidebar nav item views
    private LinearLayout navKeyboardMouse;
    private LinearLayout navGamepad;
    private LinearLayout navNumpad;
    private LinearLayout navShortcuts;
    private LinearLayout navMacros;
    private LinearLayout navVoice;
    private ImageButton navOsMacosButton;
    private ImageButton navOsWindowsButton;
    private ImageButton navOsLinuxButton;

    /** Global target OS preference key */
    private static final String PREF_TARGET_OS = "target_os";

    /** Callback notified when target OS changes in the sidebar */
    public interface OnTargetOsChangeListener {
        void onTargetOsChanged(String targetOs);
    }
    private final List<OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();

    public void addOsChangeListener(OnTargetOsChangeListener listener) {
        osChangeListeners.add(listener);
    }
    public void removeOsChangeListener(OnTargetOsChangeListener listener) {
        osChangeListeners.remove(listener);
    }
    public String getTargetOs() {
        return getSharedPreferences("AppPrefs", MODE_PRIVATE).getString(PREF_TARGET_OS, "macos");
    }
    public void setTargetOs(String os) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString(PREF_TARGET_OS, os).apply();
        for (OnTargetOsChangeListener listener : osChangeListeners) {
            listener.onTargetOsChanged(os);
        }
    }
    
    // Old button bar components (now hidden but kept for backward compatibility)
    private Button usbConnect, bluetooth, keyBoard, mouse, keyBoardMouse, question, info, shortcut;
    private Drawable usbConnectDrawable, bluetoothDrawable, keyBoardDrawable, mouseDrawable, keyBoardMouseDrawable, questionDrawable, infoDrawable, shortcutDrawable;
    private Button activeButton;

    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    // Connection Manager
    private ConnectionManager connectionManager;
    
    // Bluetooth Auto Connect Manager
    private BluetoothAutoConnectManager autoConnectManager;
    
    private boolean isUsbConnected = false; // Track USB connection state
    public boolean isBluetoothConnected = false; // Track Bluetooth connection state

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            Log.d(TAG, "Bound to BluetoothService");

            // Keep ConnectionManager in sync with the newly bound service.
            if (connectionManager != null) {
                connectionManager.setBluetoothService(bluetoothService);
            }
            
            // Initialize auto-connect manager after service is bound
            initializeAutoConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");

            if (connectionManager != null) {
                connectionManager.setBluetoothService(null);
            }
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // Use ConnectionManager for USB setup
                    if (connectionManager != null) {
                        connectionManager.connectUsb();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (connectionManager != null && 
                    connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.USB) {
                    connectionManager.disconnect();
                    Toast.makeText(context, "USB device disconnected", Toast.LENGTH_SHORT).show();
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
                        if (device != null && connectionManager != null) {
                            // Reinitialize after the permission is granted
                            connectionManager.connectUsb();
                        }
                    } else {
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Initialize Connection Manager
        connectionManager = new ConnectionManager(this);
        connectionManager.setMainActivity(this); // Pass MainActivity reference
        setupConnectionStateListener();
        
        initializeUIComponents();

        rxBleClient = RxBleClient.create(this);
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // Set BluetoothService in ConnectionManager once bound
        if (connectionManager != null) {
            connectionManager.setBluetoothService(bluetoothService);
        }

        // Handle launch mode from LaunchPanelActivity
        String launchMode = getIntent().getStringExtra("launch_mode");
        if (launchMode != null) {
            handleLaunchMode(launchMode);
        } else {
            // Default to Composite mode
            currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
            showCompositeFragment();
        }
        
        // Note: Bluetooth auto-connect will be initialized after service is bound
        // USB auto-connect is still handled by ConnectionManager
        new Handler().postDelayed(() -> {
            // Only auto-connect USB here, Bluetooth will be handled by autoConnectManager
            String lastType = getSharedPreferences("ConnectionPrefs", MODE_PRIVATE)
                    .getString("last_connection_type", "");
            if ("USB".equals(lastType)) {
                connectionManager.autoConnect();
            }
        }, 500);

        // Show beginner tutorial on first launch
        if (!TutorialOverlay.isShown(this)) {
            new Handler().postDelayed(this::showTutorial, 800);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);
        // Don't auto-setup USB here, ConnectionManager handles it
        
        // Re-apply immersive mode
        setImmersiveMode();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }
    
    private void setImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
        
        // Stop auto-connect manager
        if (autoConnectManager != null) {
            autoConnectManager.stopAutoConnect();
        }
        
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        Intent intent = new Intent(this, BluetoothService.class);
        stopService(intent); // stop service
    }

    private void initializeUIComponents() {
        // Set immersive mode
        setImmersiveMode();

        // Initialize new header buttons
        connectionButton = findViewById(R.id.connection_button);
        signalBars = findViewById(R.id.signal_bars);
        menuButton = findViewById(R.id.menu_button);

        View connectionContainer = findViewById(R.id.connection_container);
        connectionContainer.setOnClickListener(v -> showConnectionDialog());
        
        // Initialize drawer layout and nav items
        drawerLayout = findViewById(R.id.drawer_layout);
        navKeyboardMouse = findViewById(R.id.nav_keyboard_mouse);
        navGamepad = findViewById(R.id.nav_gamepad);
        navNumpad = findViewById(R.id.nav_numpad);
        navShortcuts = findViewById(R.id.nav_shortcuts);
        navMacros = findViewById(R.id.nav_macros);
        navVoice = findViewById(R.id.nav_voice);
        navOsMacosButton = findViewById(R.id.nav_os_macos_button);
        navOsWindowsButton = findViewById(R.id.nav_os_windows_button);
        navOsLinuxButton = findViewById(R.id.nav_os_linux_button);
        updateNavOsButtonState();
        
        // Initialize old buttons (kept for backward compatibility, but hidden - may be null)
        // These are in hidden layout, won't be found
        keyBoard = findViewById(R.id.keyBoard);
        mouse = findViewById(R.id.mouse);
        keyBoardMouse = findViewById(R.id.keyBoardMouse);
        question = findViewById(R.id.question);
        info = findViewById(R.id.info);
        shortcut = findViewById(R.id.shortcut);

        if (keyBoard != null) keyBoardDrawable = keyBoard.getCompoundDrawables()[1];
        if (mouse != null) mouseDrawable = mouse.getCompoundDrawables()[1];
        if (keyBoardMouse != null) keyBoardMouseDrawable = keyBoardMouse.getCompoundDrawables()[1];
        if (shortcut != null) shortcutDrawable = shortcut.getCompoundDrawables()[1];

        setupButtonListeners();
    }
    
    private void setupConnectionStateListener() {
        connectionManager.setConnectionStateListener(new ConnectionManager.ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
                runOnUiThread(() -> {
                    updateConnectionButton(type, state);
                    
                    // Update fragments with new port if USB connected
                    if (type == ConnectionManager.ConnectionType.USB && 
                        state == ConnectionManager.ConnectionState.CONNECTED) {
                        port = connectionManager.getUsbPort();
                        isUsbConnected = true;
                        isBluetoothConnected = false;
                        updateFragmentsWithPort(port);
                        startReading();
                    } else if (type == ConnectionManager.ConnectionType.BLUETOOTH && 
                               state == ConnectionManager.ConnectionState.CONNECTED) {
                        isUsbConnected = false;
                        isBluetoothConnected = true;
                        if (port != null) {
                            try {
                                port.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Error closing port: " + e.getMessage());
                            }
                            port = null;
                            updateFragmentsWithPort(null);
                        }
                    } else if (state == ConnectionManager.ConnectionState.DISCONNECTED) {
                        isUsbConnected = false;
                        isBluetoothConnected = false;
                        if (port != null) {
                            try {
                                port.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Error closing port: " + e.getMessage());
                            }
                            port = null;
                            updateFragmentsWithPort(null);
                        }
                    }
                });
            }

            @Override
            public void onConnectionError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void initializeAutoConnect() {
        if (bluetoothService == null || rxBleClient == null) {
            Log.w(TAG, "Cannot initialize auto-connect: service or client not ready");
            return;
        }
        
        autoConnectManager = new BluetoothAutoConnectManager(this, rxBleClient, bluetoothService);
        autoConnectManager.setAutoConnectListener(new BluetoothAutoConnectManager.AutoConnectListener() {
            @Override
            public void onAutoConnectStarted() {
                Log.d(TAG, "Auto-connect started");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Auto-connecting to Bluetooth...", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAutoConnectSuccess(RxBleDevice device) {
                Log.d(TAG, "Auto-connect successful: " + device.getName());
                runOnUiThread(() -> {
                    isBluetoothConnected = true;
                    if (connectionManager != null) {
                        connectionManager.connectBluetooth(device);
                    }
                    
                    // Dismiss any open dialogs (Connection dialog or Bluetooth dialog)
                    dismissAllDialogs();
                    
                    Toast.makeText(MainActivity.this, "Auto-connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAutoConnectFailed(String reason) {
                Log.e(TAG, "Auto-connect failed: " + reason);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Auto-connect failed: " + reason, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAutoConnectRetrying(int retryCount) {
                Log.d(TAG, "Auto-connect retrying: attempt " + retryCount);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Retrying Bluetooth connection (" + retryCount + "/3)...", Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        // Start auto-connect with a slight delay to ensure everything is ready
        new Handler().postDelayed(() -> {
            if (autoConnectManager != null) {
                autoConnectManager.startAutoConnect();
            }
        }, 1000); // 1 second delay
    }
    
    private void updateConnectionButton(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
        if (connectionButton == null) return;

        switch (state) {
            case CONNECTED:
                connectionButton.setImageResource(R.drawable.ic_bluetooth);
                connectionButton.setColorFilter(0xFF4CAF50);
                if (signalBars != null) {
                    signalBars.setVisibility(View.VISIBLE);
                    signalBars.setColorFilter(0xFF4CAF50);
                }
                break;
            case CONNECTING:
                connectionButton.setImageResource(R.drawable.ic_bluetooth);
                connectionButton.setColorFilter(0xFFFF9800);
                if (signalBars != null) signalBars.setVisibility(View.GONE);
                break;
            case DISCONNECTED:
                connectionButton.setImageResource(R.drawable.ic_bluetooth);
                connectionButton.setColorFilter(0xFF9E9E9E);
                if (signalBars != null) signalBars.setVisibility(View.GONE);
                break;
        }
    }

    private void showConnectionDialog() {
        ConnectionDialogFragment dialog = ConnectionDialogFragment.newInstance();
        dialog.setConnectionManager(connectionManager);
        dialog.setRxBleClient(rxBleClient);
        dialog.setConnectionDialogListener((type, state) -> updateConnectionButton(type, state));
        dialog.show(getSupportFragmentManager(), "ConnectionDialog");
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
                dialog.setConnectionListener(this); // Set the listener
                dialog.show(getSupportFragmentManager(), "BluetoothDialog");
            } else {
                Log.e("Permission", "location permission denied");
            }
        }
    }

    private void setupButtonListeners() {
        // Connection button handler removed - click handled by connection_container
        
        // Menu button handler - toggle drawer
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
                        drawerLayout.closeDrawer(android.view.Gravity.START);
                    } else {
                        drawerLayout.openDrawer(android.view.Gravity.START);
                    }
                }
            });
        }

        // Sidebar close button (mirrors iOS ellipsis button inside sidebar)
        View sidebarCloseButton = findViewById(R.id.sidebar_close_button);
        if (sidebarCloseButton != null) {
            sidebarCloseButton.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }

        // Nav item click listeners
        if (navKeyboardMouse != null) {
            navKeyboardMouse.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
                updateNavSelection();
                showCompositeFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }
        if (navGamepad != null) {
            navGamepad.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_GAMEPAD;
                updateNavSelection();
                showGamepadFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }
        if (navNumpad != null) {
            navNumpad.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_NUMPAD;
                updateNavSelection();
                showNumpadFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }
        if (navShortcuts != null) {
            navShortcuts.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_SHORTCUTS;
                updateNavSelection();
                showShortcutHubFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }
        if (navMacros != null) {
            navMacros.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_MACROS;
                updateNavSelection();
                showMacrosFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }
        if (navVoice != null) {
            navVoice.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_VOICE;
                updateNavSelection();
                showVoiceInputFragment();
                drawerLayout.closeDrawer(android.view.Gravity.START);
            });
        }

        // Target OS buttons in sidebar
        if (navOsMacosButton != null) {
            navOsMacosButton.setOnClickListener(v -> {
                setTargetOs("macos");
                updateNavOsButtonState();
            });
        }
        if (navOsWindowsButton != null) {
            navOsWindowsButton.setOnClickListener(v -> {
                setTargetOs("windows");
                updateNavOsButtonState();
            });
        }
        if (navOsLinuxButton != null) {
            navOsLinuxButton.setOnClickListener(v -> {
                setTargetOs("linux");
                updateNavOsButtonState();
            });
        }

        // LaunchPad button - returns to LaunchPanelActivity
        View chooseModeButton = findViewById(R.id.choose_mode_button);
        if (chooseModeButton != null) {
            chooseModeButton.setOnClickListener(v -> {
                drawerLayout.closeDrawer(android.view.Gravity.START);
                Intent intent = new Intent(MainActivity.this, LaunchPanelActivity.class);
                intent.putExtra(LaunchPanelActivity.SHOW_PANEL, true);
                startActivity(intent);
                finish();
            });
        }

        // Settings button handler (in nav menu)
        View settingsButton = findViewById(R.id.settings_button);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
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

        if (shortcut != null) {
            setOnClickListener(shortcut, shortcutDrawable, this::showShortCutFragment);
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
        resetButton(shortcut, shortcutDrawable, defaultColor);
    }

    private void resetButton(Button button, Drawable drawable, int defaultColor) {
        if (button != null && drawable != null) {
            button.setTextColor(defaultColor);
            drawable.clearColorFilter();
        }
    }

    // Note: resetConnectionButtons() removed as USB/Bluetooth buttons are now in the connection dialog
    
    private void dismissAllDialogs() {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            
            // Dismiss BluetoothDialogFragment if open
            Fragment bluetoothDialog = fragmentManager.findFragmentByTag("BluetoothDialog");
            if (bluetoothDialog instanceof BluetoothDialogFragment) {
                ((BluetoothDialogFragment) bluetoothDialog).dismiss();
                Log.d(TAG, "Dismissed BluetoothDialogFragment");
            }
            
            // Dismiss ConnectionDialogFragment if open
            Fragment connectionDialog = fragmentManager.findFragmentByTag("ConnectionDialog");
            if (connectionDialog instanceof ConnectionDialogFragment) {
                ((ConnectionDialogFragment) connectionDialog).dismiss();
                Log.d(TAG, "Dismissed ConnectionDialogFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing dialogs: " + e.getMessage());
        }
    }

    private void updateNavSelection() {
        if (navKeyboardMouse != null) navKeyboardMouse.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_KEYBOARD_MOUSE));
        if (navGamepad != null) navGamepad.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_GAMEPAD));
        if (navNumpad != null) navNumpad.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_NUMPAD));
        if (navShortcuts != null) navShortcuts.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_SHORTCUTS));
        if (navMacros != null) navMacros.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_MACROS));
        if (navVoice != null) navVoice.setSelected(currentNavMode.equals(LaunchPanelActivity.MODE_VOICE));
    }

    private void updateNavOsButtonState() {
        String targetOs = getTargetOs();
        int activeColor = getColor(R.color.primary);
        int inactiveColor = getColor(R.color.text_secondary);
        if (navOsMacosButton != null) {
            navOsMacosButton.setImageTintList(
                android.content.res.ColorStateList.valueOf("macos".equals(targetOs) ? activeColor : inactiveColor));
        }
        if (navOsWindowsButton != null) {
            navOsWindowsButton.setImageTintList(
                android.content.res.ColorStateList.valueOf("windows".equals(targetOs) ? activeColor : inactiveColor));
        }
        if (navOsLinuxButton != null) {
            navOsLinuxButton.setImageTintList(
                android.content.res.ColorStateList.valueOf("linux".equals(targetOs) ? activeColor : inactiveColor));
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

    private void showGamepadFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new GamepadFragment());
        transaction.commit();
    }

    private void showMouseFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, MouseFragment.newInstance(port));
        transaction.commit();
    }

    private void showShortCutFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, ShortcutFragment.newInstance());
        transaction.commit();
    }

    private void showMacrosFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new MacrosFragment());
        transaction.commit();
    }

    private void showShortcutHubFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new ShortcutHubFragment());
        transaction.commit();
    }

    private void showNumpadFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, NumpadFragment.newInstance(port));
        transaction.commit();
    }

    private void showVoiceInputFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new VoiceInputFragment());
        transaction.commit();
    }

    /**
     * Handle launch mode from LaunchPanelActivity
     */
    private void handleLaunchMode(String mode) {
        currentNavMode = mode;
        updateNavSelection();
        switch (mode) {
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE:
                showCompositeFragment();
                break;
            case LaunchPanelActivity.MODE_GAMEPAD:
                showGamepadFragment();
                break;
            case LaunchPanelActivity.MODE_NUMPAD:
                showNumpadFragment();
                break;
            case LaunchPanelActivity.MODE_SHORTCUTS:
                showShortcutHubFragment();
                break;
            case LaunchPanelActivity.MODE_MACROS:
                showMacrosFragment();
                break;
            case LaunchPanelActivity.MODE_VOICE:
                showVoiceInputFragment();
                break;
            default:
                showCompositeFragment();
                break;
        }
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
        } else if (currentFragment instanceof MouseFragment) {
            ((MouseFragment) currentFragment).setPort(newPort);
        } else if (currentFragment instanceof NumpadFragment) {
            ((NumpadFragment) currentFragment).port = newPort;
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
            usbConnect.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.usb_plug_disconnect, 0, 0);
            usbConnect.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            usbConnectDrawable.setColorFilter(getResources().getColor(android.R.color.holo_red_light), PorterDuff.Mode.SRC_IN);
            isUsbConnected = false;
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
            isUsbConnected = false;
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(manager.openDevice(device));
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            Toast.makeText(this, "Successful to open serial port", Toast.LENGTH_LONG).show();

            Drawable usbConnectDrawableTint = ContextCompat.getDrawable(this, R.drawable.usb_plug);
            if (usbConnectDrawableTint != null) {
                DrawableCompat.setTint(usbConnectDrawableTint, ContextCompat.getColor(this, android.R.color.holo_blue_light));
                usbConnect.setCompoundDrawablesWithIntrinsicBounds(null, usbConnectDrawableTint, null, null);
            }

            usbConnect.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
            usbConnectDrawable.setColorFilter(getResources().getColor(android.R.color.holo_blue_light), PorterDuff.Mode.SRC_IN);
            isReading = true;
            isUsbConnected = true;
            updateFragmentsWithPort(port);
            startReading();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open serial port: " + e.getMessage());
            Toast.makeText(this, "Failed to open serial port", Toast.LENGTH_LONG).show();
            isUsbConnected = false;
        }
    }

    @Override
    public void onBluetoothConnectionChanged(boolean isConnected) {
        isBluetoothConnected = isConnected;
        Log.d(TAG, "Bluetooth connection state updated: " + isConnected);

        if (connectionManager != null) {
            connectionManager.setBluetoothService(bluetoothService);
            connectionManager.syncBluetoothConnectionState(isConnected);
        }

        // Ensure top connection icon reflects actual BLE callback state immediately.
        updateConnectionButton(
                isConnected ? ConnectionManager.ConnectionType.BLUETOOTH : ConnectionManager.ConnectionType.NONE,
                isConnected ? ConnectionManager.ConnectionState.CONNECTED : ConnectionManager.ConnectionState.DISCONNECTED
        );
        
        // Update ConnectionManager if Bluetooth is connected
        if (isConnected && bluetoothService != null && connectionManager != null) {
            com.polidea.rxandroidble2.RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
            if (connectedDevice != null) {
                connectionManager.connectBluetooth(connectedDevice);
                Log.d(TAG, "Updated ConnectionManager with BLE device");
            } else {
                Log.d(TAG, "Bluetooth connected but connectedDevice is null; state synced from callback");
            }
        } else if (!isConnected && connectionManager != null && 
                   connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH) {
            connectionManager.disconnect();
            Log.d(TAG, "Disconnected Bluetooth in ConnectionManager");
        }
        
        if (isConnected) {
            Toast.makeText(this, "Bluetooth connected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Bluetooth disconnected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get ConnectionManager instance for fragments
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    private void showTutorial() {
        TutorialOverlay overlay = new TutorialOverlay(this);

        overlay.setSteps(new TutorialOverlay.Step[]{
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.menu_button}; }
                public String description() { return "Tap here to open the navigation menu where you can switch modes and settings."; }
                public String buttonText() { return "Next"; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.connection_container}; }
                public String description() { return "Tap the Bluetooth icon to connect via USB or Bluetooth. Green signal bars appear when connected."; }
                public String buttonText() { return "Next"; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.nav_keyboard_mouse}; }
                public String description() { return "Open the menu to choose your preferred input mode: Keyboard & Mouse, Numpad, Shortcuts, Macros, or Voice."; }
                public String buttonText() { return "Next"; }
                public void onShow(android.content.Context context) {
                    androidx.drawerlayout.widget.DrawerLayout drawer = ((android.app.Activity) context).findViewById(R.id.drawer_layout);
                    if (drawer != null) drawer.openDrawer(android.view.Gravity.START);
                }
                public int delayMs() { return 400; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.target_os_section}; }
                public String description() { return "Select the target OS (macOS, Windows, or Linux) for correct key mappings."; }
                public String buttonText() { return "Next"; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.keyboard_view, R.id.keyboard_view_left}; }
                public String description() { return "Tap ABC, !?#, or 123 keys on the keyboard to switch between letter, number, and symbol layouts."; }
                public String buttonText() { return "Next"; }
                public void onShow(android.content.Context context) {
                    androidx.drawerlayout.widget.DrawerLayout drawer = ((android.app.Activity) context).findViewById(R.id.drawer_layout);
                    if (drawer != null && drawer.isDrawerOpen(android.view.Gravity.START)) drawer.closeDrawer(android.view.Gravity.START);
                }
                public int delayMs() { return 400; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.keyboard_view}; }
                public String description() { return "The keyboard shortcuts row at the top can be scrolled left or right to reveal more options."; }
                public String buttonText() { return "Next"; }
                public int insetTopDp() { return 0; }
                public int insetBottomDp() { return -200; }
            },
            new TutorialOverlay.Step() {
                public int[] targetViewIds() { return new int[]{R.id.touchPad}; }
                public String description() { return "Use the touchpad to control the cursor. Long press to toggle drag mode."; }
                public String buttonText() { return "Done"; }
            }
        });

        // Add overlay to activity root so it appears above both content and drawer
        ViewGroup root = findViewById(android.R.id.content);
        root.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}