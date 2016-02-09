/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;

/**
 * An abstract base class for classes utilizing Bluetooth connectivity and need to validate the
 * identity string. For internal use of the library only.
 */
public abstract class AbstractBluetoothConnectivityAgent implements BluetoothManager.BluetoothManagerListener {
    protected static String TAG = AbstractBluetoothConnectivityAgent.class.getName();
    protected final BluetoothManager mBluetoothManager;
    protected String mMyPeerId = null;
    protected String mMyPeerName = null;
    protected String mMyIdentityString = "";
    protected boolean mEmulateMarshmallow = false;

    /**
     * Constructor.
     * @param context The application context.
     */
    public AbstractBluetoothConnectivityAgent(Context context) {
        mBluetoothManager = BluetoothManager.getInstance(context);
    }

    /**
     * Releases resources.
     *
     * Should be called when getting rid of the instance. Note that after calling this method you
     * should not use the instance anymore. Instead, if needed again, you must reconstruct the
     * instance.
     */
    public void dispose() {
        // No default implementation
        Log.d(TAG, "dispose");
    }

    /**
     * Used for testing purposes.
     *
     * Turns Marshmallow emulation on/off. Basically what this does is that if enabled, will not be
     * able to resolve the Bluetooth MAC address of the device from the Bluetooth adapter.
     *
     * @param emulate If true, will turn on Marshmallow emulation.
     */
    public void setEmulateMarshmallow(boolean emulate) {
        if (mEmulateMarshmallow != emulate) {
            mEmulateMarshmallow = emulate;
            Log.i(TAG, "setEmulateMarshmallow: " + mEmulateMarshmallow);
        }
    }

    /**
     * @return The Bluetooth MAC address or null, if not available.
     */
    public String getBluetoothMacAddress() {
        String bluetoothMacAddress = mEmulateMarshmallow ? null : mBluetoothManager.getBluetoothMacAddress();
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(null);

        if (settings != null) {
            if (bluetoothMacAddress == null) {
                bluetoothMacAddress = settings.getBluetoothMacAddress();
            } else {
                // Store the address just to be on the safe side
                settings.setBluetoothMacAddress(bluetoothMacAddress);
            }
        } else {
            Log.e(TAG, "getBluetoothMacAddress: Failed to get the discovery manager settings instance");
        }

        if (!BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
            bluetoothMacAddress = null;
        }

        return bluetoothMacAddress;
    }

    /**
     * Clears the identity string. This method is considered to be used for testing purposes.
     */
    public void clearIdentityString() {
        Log.i(TAG, "clearIdentityString");
        mMyIdentityString = null;
    }

    /**
     * Verifies the validity of our identity string. If not yet created, will try to create it.
     * If the identity string already exists, it won't be recreated.
     * @return True, if the identity string is OK (i.e. not empty). False otherwise.
     */
    protected boolean verifyIdentityString() {
        String bluetoothMacAddress = getBluetoothMacAddress();

        if (mMyIdentityString == null || mMyIdentityString.length() == 0) {
            if (CommonUtils.isNonEmptyString(mMyPeerId) && CommonUtils.isNonEmptyString(mMyPeerName)
                    && BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
                try {
                    mMyIdentityString = CommonUtils.createIdentityString(
                            mMyPeerId, mMyPeerName, bluetoothMacAddress);
                    Log.i(TAG, "verifyIdentityString: Identity string created: " + mMyIdentityString);
                } catch (JSONException e) {
                    Log.e(TAG, "verifyIdentityString: Failed create an identity string: " + e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "verifyIdentityString: One or more of the following values are invalid: "
                        + "Peer ID: \"" + mMyPeerId
                        + "\", peer name: \"" + mMyPeerName
                        + "\", Bluetooth MAC address: \"" + bluetoothMacAddress + "\"");
            }
        }

        return (mMyIdentityString != null && mMyIdentityString.length() > 0);
    }

    @Override
    abstract public void onBluetoothAdapterScanModeChanged(int mode);
}
