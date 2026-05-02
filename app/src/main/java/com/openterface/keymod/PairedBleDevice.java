package com.openterface.keymod;

import androidx.annotation.NonNull;

/**
 * A BLE device the user has successfully connected to at least once (paired in-app).
 */
public final class PairedBleDevice {
    public final String mac;
    public final String name;
    public final long lastConnectedAtMs;

    public PairedBleDevice(@NonNull String mac, @NonNull String name, long lastConnectedAtMs) {
        this.mac = mac;
        this.name = name;
        this.lastConnectedAtMs = lastConnectedAtMs;
    }
}
