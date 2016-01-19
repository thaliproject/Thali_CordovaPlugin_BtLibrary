/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothGattServer;
import android.content.Context;

import java.util.UUID;

/**
 * Manages Bluetooth GATT services.
 */
public class BluetoothGattManager {
    private static final String TAG = BluetoothGattManager.class.getName();
    private BluetoothGattServer mBluetoothGattServer = null;

    public BluetoothGattManager(Context context) {

    }

    public boolean addServiceForBluetoothMacAddressRequest(UUID requestUuid) {
        return false;
    }
}
