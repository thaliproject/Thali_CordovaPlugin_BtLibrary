/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
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
    protected static final String JSON_ID_PEER_NAME = "name";
    protected static final String JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS = "address";
    protected final Context mContext;
    protected final BluetoothManager mBluetoothManager;
    protected String mMyPeerName = PeerProperties.NO_PEER_NAME_STRING;
    protected String mMyIdentityString = "";
    protected boolean mEmulateMarshmallow = false;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public AbstractBluetoothConnectivityAgent(Context context) {
        if (context == null) {
            throw new NullPointerException("Context is null");
        }

        mContext = context;
        mBluetoothManager = BluetoothManager.getInstance(mContext);
    }

    /**
     * @return The Bluetooth manager instance. Guaranteed not to be null.
     */
    public BluetoothManager getBluetoothManager() {
        return mBluetoothManager;
    }

    /**
     * Sets the peer name. Not mandatory - the functionality is 100 % even when not set.
     * The name is used in the identity string.
     *
     * @param myPeerName Our peer name.
     */
    public void setPeerName(String myPeerName) {
        if (!mMyPeerName.equals(myPeerName)) {
            Log.i(TAG, "setPeerName: " + myPeerName);
            mMyPeerName = myPeerName;

            // Recreate the identity string
            mMyIdentityString = null;
            verifyIdentityString();
        }
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
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(mContext);

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

    /**
     * Resolves the peer properties from the given identity string.
     *
     * @param identityString The identity string.
     * @param peerProperties The peer properties to contain the resolved values.
     * @return True, if all the properties contain data (not validated though). False otherwise.
     * @throws JSONException
     */
    public static boolean getPropertiesFromIdentityString(
            String identityString, PeerProperties peerProperties)
            throws JSONException {

        JSONObject jsonObject = new JSONObject(identityString);
        peerProperties.setName(jsonObject.getString(JSON_ID_PEER_NAME));
        peerProperties.setBluetoothMacAddress(jsonObject.getString(JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS));

        return peerProperties.isValid();
    }

    /**
     * Verifies the validity of our identity string. If not yet created, will try to create it.
     * If the identity string already exists, it won't be recreated.
     *
     * @return True, if the identity string is OK (i.e. not empty). False otherwise.
     */
    protected boolean verifyIdentityString() {
        String bluetoothMacAddress = getBluetoothMacAddress();

        if (!CommonUtils.isNonEmptyString(mMyIdentityString)) {
            if (CommonUtils.isNonEmptyString(mMyPeerName)
                    && !mMyPeerName.equals(PeerProperties.NO_PEER_NAME_STRING)
                    && BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
                try {
                    mMyIdentityString = createIdentityString(mMyPeerName, bluetoothMacAddress);
                    Log.i(TAG, "verifyIdentityString: Identity string created: " + mMyIdentityString);
                } catch (JSONException e) {
                    Log.e(TAG, "verifyIdentityString: Failed create an identity string: " + e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "verifyIdentityString: One or more of the following values are invalid: "
                        + "Peer name: \"" + mMyPeerName
                        + "\", Bluetooth MAC address: \"" + bluetoothMacAddress + "\"");
            }
        }

        return (mMyIdentityString != null && mMyIdentityString.length() > 0);
    }

    /**
     * Creates an identity string based on the given arguments.
     *
     * @param peerName The peer name.
     * @param bluetoothMacAddress The Bluetooth MAC address of the peer.
     * @return An identity string or null in case of a failure.
     * @throws JSONException
     */
    private String createIdentityString(String peerName, String bluetoothMacAddress)
            throws JSONException {

        String identityString = null;
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put(JSON_ID_PEER_NAME, peerName);
            jsonObject.put(JSON_ID_PEER_BLUETOOTH_MAC_ADDRESS, bluetoothMacAddress);
            identityString = jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "createIdentityString: Failed to construct a JSON object (from data "
                    + peerName + " " + bluetoothMacAddress + "): " + e.getMessage(), e);
            throw e;
        }

        return identityString;
    }

    /**
     * Creates an identity string based on the given properties.
     *
     * @param peerProperties The peer properties.
     * @return An identity string or null in case of a failure.
     * @throws JSONException
     */
    public String createIdentityString(PeerProperties peerProperties) throws JSONException {
        return createIdentityString(peerProperties.getName(), peerProperties.getBluetoothMacAddress());
    }
}
