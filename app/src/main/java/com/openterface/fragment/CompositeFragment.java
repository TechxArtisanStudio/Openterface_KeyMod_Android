package com.openterface.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.util.TouchPadHaptics;
import com.openterface.keymod.util.TouchPadHelpOverlay;
import com.openterface.keymod.util.TouchPadPointerPhase;
import com.openterface.keymod.util.TouchPadTipsFormatter;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompositeFragment extends Fragment {

    private static final String TAG = "CompositeFragment";
    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    private LinearLayout rootLayout;
    private LinearLayout touchpadSection;
    private LinearLayout toggleHandle;
    private View toggleHandlePill;
    private TextView touchPadTips;
    private TextView touchPadHelpOverlay;
    /** Split mode views */
    private View splitRoot;
    private CustomKeyboardView keyboardViewLeft;
    private CustomKeyboardView keyboardViewRight;
    private ViewGroup splitTouchpadSection;
    private TouchPadView splitTouchPad;
    private TextView splitTouchPadTips;
    private TextView splitTouchPadHelpOverlay;
    /** Container to swap between normal and split layouts */
    private FrameLayout contentContainer;
    /** Track registered keyboard views for OS change listener cleanup */
    private final List<MainActivity.OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();
    public UsbSerialPort port;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    private boolean isDragMode = false;

    private static final long POINTER_IDLE_AFTER_MS = 400L;
    private final Handler tipHandler = new Handler(Looper.getMainLooper());
    private TouchPadPointerPhase pointerPhase = TouchPadPointerPhase.IDLE;
    private final Runnable pointerIdleRunnable =
            () -> {
                pointerPhase = TouchPadPointerPhase.IDLE;
                updateTouchPadTips();
                updateSplitTouchPadTips();
            };

    private enum DisplayMode { BOTH, KEYBOARD, TOUCHPAD, SPLIT }
    private DisplayMode displayMode = DisplayMode.BOTH;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Bound to BluetoothService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    public static CompositeFragment newInstance(UsbSerialPort port) {
        CompositeFragment fragment = new CompositeFragment();
        fragment.port = port;
        return fragment;
    }

    public static String makeChecksum(String data) {
        int total = 0;

        for (int i = 0; i < data.length(); i += 2) {
            String byteStr = data.substring(i, Math.min(i + 2, data.length()));
            total += Integer.parseInt(byteStr, 16);
        }

        int mod = total % 256;

        return String.format("%02X", mod);
    }

    public static void checkSendLogData(String sendKBData) {
        StringBuilder check_send_data = new StringBuilder();
        for (int i = 0; i < sendKBData.length(); i += 2) {
            if (i + 2 <= sendKBData.length()) {
                check_send_data.append(sendKBData.substring(i, i + 2)).append(" ");
            } else {
                check_send_data.append(sendKBData.substring(i)).append(" ");
            }
        }
        Log.d(TAG, "sendKBData: " + check_send_data.toString().trim());
    }

    public static byte[] hexStringToByteArray(String ByteData) {
        if (ByteData.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }

        int len = ByteData.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(ByteData.charAt(i), 16) << 4)
                    + Character.digit(ByteData.charAt(i + 1), 16));
        }
        return data;
    }

    private void releaseAllMSData() {
        String releaseSendMSData = "57AB00050501000000000D";
        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                bluetoothService.sendData(releaseSendKBDataBytes);
                Log.d(TAG, "Sent Bluetooth release data");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error sending Bluetooth release data: " + e.getMessage());
            }
        } else if (port != null) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                port.write(releaseSendKBDataBytes, 20);
                Log.d(TAG, "Sent USB release data");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error sending USB release data: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "No connection available for release data");
        }
    }

    private void setDragMode(boolean enabled) {
        isDragMode = enabled;
        sendMouseButtonState(enabled ? 0x01 : 0x00);
        updateTouchPadTips();
        updateSplitTouchPadTips();
        Log.d(TAG, "Drag mode " + (enabled ? "ON" : "OFF"));
    }

    private void updateTouchPadTips() {
        if (touchPadTips == null) return;
        touchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    private void updateSplitTouchPadTips() {
        if (splitTouchPadTips == null) return;
        splitTouchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    private void notePointerPhase(TouchPadPointerPhase phase) {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = phase;
        updateTouchPadTips();
        updateSplitTouchPadTips();
        if (phase != TouchPadPointerPhase.IDLE) {
            tipHandler.postDelayed(pointerIdleRunnable, POINTER_IDLE_AFTER_MS);
        }
    }

    private void clearPointerPhaseForFingerUp() {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = TouchPadPointerPhase.IDLE;
        updateTouchPadTips();
        updateSplitTouchPadTips();
    }

    private void sendMouseButtonState(int buttonMask) {
        new Thread(() -> {
            try {
                String buttonByte = String.format("%02X", buttonMask & 0xFF);
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        buttonByte + "00" + "00" + "00";
                sendMSData += makeChecksum(sendMSData);
                byte[] bytes = hexStringToByteArray(sendMSData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending mouse button state: " + e.getMessage());
            }
        }).start();
    }

    public void sendHexRelData(float StartMoveMSX, float StartMoveMSY, float LastMoveMSX, float LastMoveMSY) {
        new Thread(() -> {
            try {
                int xMovement = (int) (StartMoveMSX - LastMoveMSX);
                int yMovement = (int) (StartMoveMSY - LastMoveMSY);

                if (Math.abs(xMovement) < 2 && Math.abs(yMovement) < 2) {
                    return;
                }

                String xByte;
                if (xMovement == 0) {
                    xByte = "00";
                } else if (LastMoveMSX == 0) {
                    xByte = "00";
                } else if (xMovement > 0) {
                    xByte = String.format("%02X", Math.min(xMovement, 0x7F));
                } else {
                    xByte = String.format("%02X", 0x100 + xMovement);
                }

                String yByte;
                if (yMovement == 0) {
                    yByte = "00";
                } else if (LastMoveMSY == 0) {
                    yByte = "00";
                } else if (yMovement > 0) {
                    yByte = String.format("%02X", Math.min(yMovement, 0x7F));
                } else {
                    yByte = String.format("%02X", 0x100 + yMovement);
                }

                String buttonByte = isDragMode ? "01" : CH9329MSKBMap.MSAbsData().get("SecNullData");
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                                CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                                CH9329MSKBMap.getKeyCodeMap().get("address") +
                                CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                                CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                                CH9329MSKBMap.MSRelData().get("FirstData") +
                        buttonByte + // MS key
                                xByte +
                                yByte +
                                CH9329MSKBMap.DataNull().get("DataNull");

                sendMSData = sendMSData + makeChecksum(sendMSData);

                if (sendMSData.length() % 2 != 0) {
                    sendMSData += "0";
                }
                checkSendLogData(sendMSData);

                byte[] sendKBDataBytes = hexStringToByteArray(sendMSData);

                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    try {
                        bluetoothService.sendData(sendKBDataBytes);
                        Log.d(TAG, "Sent Bluetooth relative mouse data: " + sendMSData);
//                        releaseAllMSData();
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending Bluetooth relative mouse data: " + e.getMessage());
                    }
                } else if (port != null) {
                    try {
                        port.write(sendKBDataBytes, 20);
                        Log.d(TAG, "Sent USB relative mouse data: " + sendMSData);
//                        releaseAllMSData();
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending USB relative mouse data: " + e.getMessage());
                    }
                } else {
                    Log.w(TAG, "No connection available for relative mouse data");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing relative mouse data: " + e.getMessage());
            }
        }).start();
    }

    /** Send a scroll-wheel packet. deltaY>0 scrolls up, deltaY<0 scrolls down (natural). */
    public void sendScrollData(int deltaX, int deltaY) {
        new Thread(() -> {
            try {
                // Skip empty scroll events.
                if (deltaX == 0 && deltaY == 0) return;

                String base =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        "00"; // no button

                // Vertical scroll (wheel byte)
                if (deltaY != 0) {
                    String wheelByte = deltaY > 0
                            ? String.format("%02X", Math.min(deltaY, 0x7F))
                            : String.format("%02X", 0x100 + Math.max(deltaY, -0x7F));
                    String packet = base + "00" + "00" + wheelByte;
                    packet += makeChecksum(packet);
                    byte[] bytes = hexStringToByteArray(packet);
                    if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.sendData(bytes);
                    } else if (port != null) {
                        port.write(bytes, 20);
                    }
                }

                // Horizontal scroll fallback (put deltaX in X byte, wheel=0), matching iOS behavior.
                if (deltaX != 0) {
                    int boundedX = Math.max(-127, Math.min(127, deltaX));
                    String xByte = boundedX >= 0
                            ? String.format("%02X", boundedX)
                            : String.format("%02X", 0x100 + boundedX);
                    String packet = base + xByte + "00" + "00";
                    packet += makeChecksum(packet);
                    byte[] bytes = hexStringToByteArray(packet);
                    if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.sendData(bytes);
                    } else if (port != null) {
                        port.write(bytes, 20);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending scroll data: " + e.getMessage());
            }
        }).start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Create container to swap between normal and split layouts
        contentContainer = new FrameLayout(requireContext());
        contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Initially inflate normal layout
        View normalView = inflater.inflate(R.layout.fragment_composite, contentContainer, false);
        contentContainer.addView(normalView);

        setupNormalViews(normalView);
        applyOrientationLayout();
        applyDisplayMode();

        if (toggleHandle != null) {
            toggleHandle.setOnClickListener(v -> cycleDisplayMode());
        }

        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        setupTouchPad(touchPad, touchPadTips, normalView.findViewById(R.id.touchPadInfo));

        // Register keyboard view for OS change updates
        registerKeyboardOsListener(keyboardView);

        if (savedInstanceState == null && touchPad != null) {
            touchPad.post(() -> TouchPadHelpOverlay.show(helpOverlayForPad(touchPad)));
        }

        return contentContainer;
    }

    @Override
    public void onDestroyView() {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        super.onDestroyView();
        if (requireActivity() instanceof MainActivity) {
            for (MainActivity.OnTargetOsChangeListener listener : osChangeListeners) {
                ((MainActivity) requireActivity()).removeOsChangeListener(listener);
            }
            osChangeListeners.clear();
        }
        setDragMode(false);
        TouchPadHelpOverlay.clear(touchPadHelpOverlay);
        TouchPadHelpOverlay.clear(splitTouchPadHelpOverlay);
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
    private void registerKeyboardOsListener(CustomKeyboardView kbdView) {
        if (kbdView == null || !(requireActivity() instanceof MainActivity)) return;
        MainActivity.OnTargetOsChangeListener listener = os -> {
            kbdView.reloadForTargetOs();
        };
        osChangeListeners.add(listener);
        ((MainActivity) requireActivity()).addOsChangeListener(listener);
    }

    private void setupNormalViews(View view) {
        rootLayout = view.findViewById(R.id.composite_root);
        keyboardView = view.findViewById(R.id.keyboard_view);
        touchPad = view.findViewById(R.id.touchPad);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        toggleHandle = view.findViewById(R.id.toggle_handle);
        toggleHandlePill = view.findViewById(R.id.toggle_handle_pill);
        touchPadTips = view.findViewById(R.id.touchPadTips);
        touchPadHelpOverlay = view.findViewById(R.id.touchPadHelpOverlay);
        updateTouchPadTips();
    }

    private void setupSplitViews(View view) {
        splitRoot = view.findViewById(R.id.split_root);
        FrameLayout topPanelContainer = view.findViewById(R.id.split_top_panel);
        keyboardViewLeft = view.findViewById(R.id.keyboard_view_left);
        keyboardViewRight = view.findViewById(R.id.keyboard_view_right);
        splitTouchpadSection = view.findViewById(R.id.touchpad_section);
        splitTouchPad = view.findViewById(R.id.touchPad);
        splitTouchPadTips = view.findViewById(R.id.touchPadTips);
        splitTouchPadHelpOverlay = view.findViewById(R.id.touchPadHelpOverlay);
        View splitToggleHandle = view.findViewById(R.id.toggle_handle);

        if (splitTouchPadTips != null) {
            updateSplitTouchPadTips();
        }

        if (splitToggleHandle != null) {
            splitToggleHandle.setOnClickListener(v -> cycleDisplayMode());
        }

        // Suppress top panels inside each keyboard half by setting split part
        if (keyboardViewLeft != null) {
            keyboardViewLeft.setPort(port);
            keyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
            // Link to right keyboard for modifier state syncing
            if (keyboardViewRight != null) {
                keyboardViewLeft.setSplitPartner(keyboardViewRight);
            }
            // Register for OS change updates
            registerKeyboardOsListener(keyboardViewLeft);
            // Create shared top panel from the left keyboard
            if (topPanelContainer != null) {
                View topPanel = keyboardViewLeft.createTopPanel();
                if (topPanel != null) {
                    topPanelContainer.addView(topPanel);
                }
            }
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.setPort(port);
            keyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
            // Link to left keyboard for modifier state syncing
            if (keyboardViewLeft != null) {
                keyboardViewRight.setSplitPartner(keyboardViewLeft);
            }
            // Register for OS change updates
            registerKeyboardOsListener(keyboardViewRight);
        }

        setupTouchPad(splitTouchPad, splitTouchPadTips, view.findViewById(R.id.touchPadInfo));
    }

    private TextView helpOverlayForPad(TouchPadView pad) {
        if (pad != null && pad == splitTouchPad) {
            return splitTouchPadHelpOverlay;
        }
        return touchPadHelpOverlay;
    }

    private void setupTouchPad(TouchPadView pad, TextView tips, View infoButton) {
        if (pad == null) return;
        if (infoButton != null) {
            infoButton.setOnClickListener(v -> TouchPadHelpOverlay.onInfoPressed(helpOverlayForPad(pad)));
        }
        pad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                if (lastX == 0 && lastY == 0) {
                    notePointerPhase(TouchPadPointerPhase.SCROLL);
                    sendScrollData((int) startX, (int) startY);
                } else {
                    notePointerPhase(TouchPadPointerPhase.MOVE);
                    sendHexRelData(startX, startY, lastX, lastY);
                }
            }

            @Override
            public void onTouchClick() {
                if (isDragMode) {
                    setDragMode(false);
                    return;
                }
                TouchPadHaptics.onLeftClick(pad.getContext());
                new Thread(() -> {
                    try {
                        String sendKBData = "57AB0005050101000000";
                        sendKBData += makeChecksum(sendKBData);
                        byte[] bytes = hexStringToByteArray(sendKBData);
                        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                            bluetoothService.sendData(bytes);
                        } else if (port != null) {
                            port.write(bytes, 20);
                        }
                        Thread.sleep(30);
                        releaseAllMSData();
                    } catch (IOException | InterruptedException e) {
                        Log.e(TAG, "Error sending tap left click: " + e.getMessage());
                    }
                }).start();
            }

            @Override
            public void onTouchDoubleClick() {
                if (isDragMode) {
                    setDragMode(false);
                    return;
                }
                TouchPadHaptics.onDoubleClick(pad.getContext());
                new Thread(() -> {
                    try {
                        String clickData = "57AB0005050101000000";
                        clickData += makeChecksum(clickData);
                        byte[] bytes = hexStringToByteArray(clickData);
                        for (int i = 0; i < 2; i++) {
                            if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                                bluetoothService.sendData(bytes);
                            } else if (port != null) {
                                port.write(bytes, 20);
                            }
                            Thread.sleep(30);
                            releaseAllMSData();
                            Thread.sleep(30);
                        }
                    } catch (IOException | InterruptedException e) {
                        Log.e(TAG, "Error sending double click: " + e.getMessage());
                    }
                }).start();
            }

            @Override
            public void onTouchRightClick() {
                TouchPadHaptics.onRightClick(pad.getContext());
                new Thread(() -> {
                    try {
                        String sendKBData = "57AB0005050102000000";
                        sendKBData += makeChecksum(sendKBData);
                        byte[] bytes = hexStringToByteArray(sendKBData);
                        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                            bluetoothService.sendData(bytes);
                        } else if (port != null) {
                            port.write(bytes, 20);
                        }
                        Thread.sleep(30);
                        releaseAllMSData();
                    } catch (IOException | InterruptedException e) {
                        Log.e(TAG, "Error sending right click: " + e.getMessage());
                    }
                }).start();
            }

            @Override
            public void onTouchLongPress() {
                if (isDragMode) {
                    return;
                }
                TouchPadHaptics.onDragToggle(pad.getContext());
                setDragMode(true);
            }

            @Override
            public void onTouchRelease() {
                clearPointerPhaseForFingerUp();
                if (!isDragMode) {
                    releaseAllMSData();
                }
            }
        });
        TouchPadHelpOverlay.wireDismissTouchTargets(pad, tips, helpOverlayForPad(pad));
    }

    private void cycleDisplayMode() {
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        switch (displayMode) {
            case BOTH:     displayMode = DisplayMode.KEYBOARD;  break;
            case KEYBOARD: displayMode = isPortrait ? DisplayMode.BOTH : DisplayMode.TOUCHPAD;  break;
            case TOUCHPAD: displayMode = isPortrait ? DisplayMode.BOTH : DisplayMode.SPLIT;     break;
            case SPLIT:    displayMode = DisplayMode.BOTH;      break;
        }
        applyDisplayMode();
    }

    private void applyDisplayMode() {
        boolean isInSplit = displayMode == DisplayMode.SPLIT;

        if (isInSplit) {
            ensureSplitLayout();
            return;
        }

        // Ensure we have the normal layout
        ensureNormalLayout();

        if (touchpadSection != null) {
            touchpadSection.setVisibility(
                displayMode != DisplayMode.KEYBOARD ? View.VISIBLE : View.GONE);
        }
        if (keyboardView != null) {
            keyboardView.setVisibility(
                displayMode != DisplayMode.TOUCHPAD ? View.VISIBLE : View.GONE);

            keyboardView.reloadForCurrentOrientation();
            keyboardView.setShowExtraPortraitKeys(displayMode == DisplayMode.KEYBOARD);
        }
    }

    private void ensureSplitLayout() {
        if (splitRoot == null) {
            TouchPadHelpOverlay.clear(touchPadHelpOverlay);
            touchPadHelpOverlay = null;
            View normal = contentContainer.getChildAt(0);
            if (normal != null) {
                contentContainer.removeView(normal);
            }
            View splitView = LayoutInflater.from(requireContext()).inflate(
                    R.layout.fragment_composite_split, contentContainer, false);
            contentContainer.addView(splitView);
            setupSplitViews(splitView);
        }

        // Update split keyboard views
        if (keyboardViewLeft != null) {
            keyboardViewLeft.reloadForCurrentOrientation();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.reloadForCurrentOrientation();
        }
    }

    private void ensureNormalLayout() {
        if (splitRoot != null) {
            TouchPadHelpOverlay.clear(splitTouchPadHelpOverlay);
            splitTouchPadHelpOverlay = null;
            View split = contentContainer.getChildAt(0);
            if (split != null) {
                contentContainer.removeView(split);
            }
            splitRoot = null;
            keyboardViewLeft = null;
            keyboardViewRight = null;

            View normalView = LayoutInflater.from(requireContext()).inflate(
                    R.layout.fragment_composite, contentContainer, false);
            contentContainer.addView(normalView);
            setupNormalViews(normalView);
            if (toggleHandle != null) {
                toggleHandle.setOnClickListener(v -> cycleDisplayMode());
            }
            if (keyboardView != null && port != null) {
                keyboardView.setPort(port);
            }
            setupTouchPad(touchPad, touchPadTips, normalView.findViewById(R.id.touchPadInfo));
        }
        applyOrientationLayout();
    }

    private void applyOrientationLayout() {
        if (rootLayout == null || touchpadSection == null || toggleHandle == null || keyboardView == null) {
            return;
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            rootLayout.setOrientation(LinearLayout.HORIZONTAL);

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    getResources().getDimensionPixelSize(R.dimen.toggle_handle_width_landscape),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 2.0f));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), dpToPx(40)));
            }
        } else {
            rootLayout.setOrientation(LinearLayout.VERTICAL);

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getDimensionPixelSize(R.dimen.toggle_handle_height)));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(4)));
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;

        if (displayMode == DisplayMode.SPLIT && isPortrait) {
            displayMode = DisplayMode.BOTH;
            ensureNormalLayout();
            applyDisplayMode();
            return;
        }

        if (displayMode == DisplayMode.SPLIT) {
            // Reconfigure split keyboard views for new orientation
            if (keyboardViewLeft != null) {
                keyboardViewLeft.reloadForCurrentOrientation();
                keyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
            }
            if (keyboardViewRight != null) {
                keyboardViewRight.reloadForCurrentOrientation();
                keyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
            }
        } else {
            applyOrientationLayout();
            applyDisplayMode();
        }
    }
}
