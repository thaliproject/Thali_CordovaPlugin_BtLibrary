/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Commonly used utils and constants.
 */
public class CommonUtils {
    private static final String TAG = CommonUtils.class.getName();
    private static final String JSON_ID_PEER_ID   = "pi";
    private static final String JSON_ID_PEER_NAME = "pn";
    private static final String JSON_ID_PEER_BLUETOOTH_ADDRESS = "ra";

    /**
     * A struct like class for peer properties: ID, name and Bluetooth address.
     */
    public static class PeerProperties {
        public String id = "";
        public String name = "";
        public String bluetoothAddress = "";

        @Override
        public String toString() {
            return "[" + id + " " + name + " " + bluetoothAddress + "]";
        }
    }

    /**
     * Creates an identity string based on the given arguments.
     * @param peerId The peer ID.
     * @param peerName The peer name.
     * @param peerBluetoothAddress The Bluetooth address of the peer.
     * @return An identity string or null in case of a failure.
     * @throws JSONException
     */
    public static String createIdentityString(
            String peerId, String peerName, String peerBluetoothAddress)
            throws JSONException {

        String identityString = null;
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put(CommonUtils.JSON_ID_PEER_ID, peerId);
            jsonObject.put(CommonUtils.JSON_ID_PEER_NAME, peerName);
            jsonObject.put(CommonUtils.JSON_ID_PEER_BLUETOOTH_ADDRESS, peerBluetoothAddress);
            identityString = jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "createIdentityString: Failed to construct a JSON object (from data "
                + peerId + " " + peerName + " " + peerBluetoothAddress + "): " + e.getMessage(), e);
            throw e;
        }

        return identityString;
    }

    /**
     * Creates an identity string based on the given properties.
     * @param peerProperties The peer properties.
     * @return An identity string or null in case of a failure.
     * @throws JSONException
     */
    public static String createIdentityString(PeerProperties peerProperties) throws JSONException {
        return createIdentityString(peerProperties.id, peerProperties.name, peerProperties.bluetoothAddress);
    }

    /**
     * Resolves the peer properties from the given identity string.
     * @param identityString The identity string.
     * @param peerProperties The peer properties to contain the resolved values.
     * @return True, if all the properties contain data (not validated though). False otherwise.
     * @throws JSONException
     */
    public static boolean getPropertiesFromIdentityString(
            String identityString, PeerProperties peerProperties)
            throws JSONException {

        JSONObject jsonObject = new JSONObject(identityString);
        peerProperties.id = jsonObject.getString(JSON_ID_PEER_ID);
        peerProperties.name = jsonObject.getString(JSON_ID_PEER_NAME);
        peerProperties.bluetoothAddress = jsonObject.getString(JSON_ID_PEER_BLUETOOTH_ADDRESS);

        return (peerProperties.id != null && !peerProperties.id.isEmpty()
                && peerProperties.name != null && !peerProperties.name.isEmpty()
                && peerProperties.bluetoothAddress != null && !peerProperties.bluetoothAddress.isEmpty());
    }
}
