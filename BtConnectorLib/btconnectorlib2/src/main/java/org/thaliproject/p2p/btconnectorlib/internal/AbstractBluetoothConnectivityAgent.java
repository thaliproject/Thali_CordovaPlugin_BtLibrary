/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;

/**
 * An abstract base class for classes utilizing Bluetooth connectivity that need to validate the
 * identity string. For internal use only.
 */
public abstract class AbstractBluetoothConnectivityAgent implements BluetoothManager.BluetoothManagerListener {
    protected static String TAG = AbstractBluetoothConnectivityAgent.class.getName();
    protected static final String JSON_ID_PEER_GENERATION = "generation";
    protected static final String JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS = "address";
    protected final Context mContext;
    protected final BluetoothManager mBluetoothManager;
    protected String mMyIdentityString = "";
    protected boolean mEmulateMarshmallow = false;
    protected int myExtraInfo = PeerProperties.NO_EXTRA_INFORMATION;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public AbstractBluetoothConnectivityAgent(Context context) {
        this(context, BluetoothManager.getInstance(context));
    }

    /**
     * Constructor.
     *
     * @param context          The application context.
     * @param bluetoothManager The bluetooth manager.
     */
    public AbstractBluetoothConnectivityAgent(Context context, BluetoothManager bluetoothManager) {
        if (context == null) {
            throw new NullPointerException("Context is null");
        }

        mContext = context;
        mBluetoothManager = bluetoothManager;
    }

    /**
     * @return The Bluetooth manager instance. Guaranteed not to be null.
     */
    public BluetoothManager getBluetoothManager() {
        return mBluetoothManager;
    }

    public int getExtraInfo() {
        return myExtraInfo;
    }

    public void setExtraInfo(int extraInfo) {
        if (extraInfo != PeerProperties.NO_EXTRA_INFORMATION) {
            this.myExtraInfo = extraInfo;
            clearIdentityString();
            tryToCreateIdentityString();
        }
    }

    /**
     * Releases resources.
     * <p>
     * Should be called when getting rid of the instance. Note that after calling this method you
     * should not use the instance anymore. Instead, if needed again, you must reconstruct the
     * instance.
     */
    public void dispose() {
        // No default implementation
    }

    /**
     * Used for testing purposes.
     * <p>
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
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(mContext);

        return verifyBluetoothMacAddress(settings);
    }

    /**
     * @param preferences The shared preferences.
     * @return The Bluetooth MAC address or null, if not available.
     */
    public String getBluetoothMacAddress(SharedPreferences preferences) {

        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(mContext, preferences);

        return verifyBluetoothMacAddress(settings);
    }

    @Nullable
    private String verifyBluetoothMacAddress(DiscoveryManagerSettings settings) {
        String bluetoothMacAddress = mEmulateMarshmallow ? null : mBluetoothManager.getBluetoothMacAddress();
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

    @Override
    abstract public void onBluetoothAdapterScanModeChanged(int mode);

    public static PeerProperties getPropertiesFromIdentityString(String identityString)
            throws JSONException {
        JSONObject jsonObject = new JSONObject(identityString);
        return new PeerProperties(jsonObject.getString(JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS),
                jsonObject.getInt(JSON_ID_PEER_GENERATION));
    }

    /**
     * Verifies the validity of our identity string.
     *
     * @return True, if the identity string is OK (i.e. not empty). False otherwise.
     */
    protected boolean verifyIdentityString() {
        return verifyIdentityStringImp();
    }

    protected boolean tryToCreateIdentityString() {
        String bluetoothMacAddress = getBluetoothMacAddress();
        return tryToCreateIdentityString(bluetoothMacAddress);
    }

    private boolean verifyIdentityStringImp() {
        return CommonUtils.isNonEmptyString(mMyIdentityString);
    }

    protected boolean tryToCreateIdentityString(SharedPreferences preferences) {
        String bluetoothMacAddress = getBluetoothMacAddress(preferences);
        return tryToCreateIdentityString(bluetoothMacAddress);
    }

    private boolean tryToCreateIdentityString(String bluetoothMacAddress) {
        if (verifyIdentityStringImp()) {
            return true;
        }
        if (canCreateIdentityString(bluetoothMacAddress)) {
            try {
                mMyIdentityString = createIdentityString(bluetoothMacAddress, myExtraInfo);
                Log.i(TAG, "verifyIdentityString: Identity string created: " + mMyIdentityString);
                return true;
            } catch (JSONException e) {
                Log.e(TAG, "verifyIdentityString: Failed create an identity string: " + e.getMessage(), e);
            }
        }
        return false;
    }

    private boolean canCreateIdentityString(String bluetoothMacAddress) {
        if (myExtraInfo != PeerProperties.NO_EXTRA_INFORMATION
                && BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
            return true;
        } else {
            Log.d(TAG, "verifyIdentityString: One or more of the following values are invalid: " +
                    "\", Bluetooth MAC address: \"" + bluetoothMacAddress + "\"" +
                    "Peer extra info: \"" + myExtraInfo);
        }
        return false;
    }

    private String createIdentityString(String bluetoothMacAddress, int extraInformation) throws JSONException {
        String identityString;
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put(JSON_ID_PEER_GENERATION, extraInformation);
            jsonObject.put(JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS, bluetoothMacAddress);
            identityString = jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "createIdentityString: Failed to construct a JSON object (from data "
                    + extraInformation + " " + bluetoothMacAddress + "): " + e.getMessage(), e);
            throw e;
        }

        return identityString;
    }
}
