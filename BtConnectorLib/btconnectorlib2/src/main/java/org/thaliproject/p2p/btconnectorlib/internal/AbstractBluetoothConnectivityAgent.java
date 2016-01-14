package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

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

        return bluetoothMacAddress;
    }

    /**
     * Verifies the validity of our identity string. If the not yet created, will try to create it.
     * If the identity string already exists, it won't be recreated.
     * @return True, if the identity string is OK (i.e. not empty). False otherwise.
     */
    protected boolean verifyIdentityString() {
        String bluetoothMacAddress = getBluetoothMacAddress();

        if ((mMyIdentityString == null || mMyIdentityString.length() == 0)
                && bluetoothMacAddress != null && mMyPeerId != null && mMyPeerName != null
                && mBluetoothManager.isBluetoothEnabled()) {
            try {
                mMyIdentityString = CommonUtils.createIdentityString(
                        mMyPeerId, mMyPeerName, getBluetoothMacAddress());
                Log.i(TAG, "verifyIdentityString: Identity string created: " + mMyIdentityString);
            } catch (JSONException e) {
                Log.e(TAG, "verifyIdentityString: Failed create an identity string: " + e.getMessage(), e);
            }
        }

        return (mMyIdentityString != null && mMyIdentityString.length() > 0);
    }

    @Override
    abstract public void onBluetoothAdapterScanModeChanged(int mode);
}
