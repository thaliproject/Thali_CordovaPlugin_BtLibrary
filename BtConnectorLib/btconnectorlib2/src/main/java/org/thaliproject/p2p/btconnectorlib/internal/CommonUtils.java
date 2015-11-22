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
    private static final String JSON_ID_PEER_ID   = "pi";
    private static final String JSON_ID_PEER_NAME = "pn";
    private static final String JSON_ID_BLUETOOTH_ADDRESS = "ra";

    private static final String TAG = CommonUtils.class.getName();

    /**
     * A struct like class for peer properties: ID, name and Bluetooth address.
     */
    public static class PeerProperties {
        public String peerId = "";
        public String peerName = "";
        public String bluetoothAddress = "";
    }

    /**
     * Creates an instance string based on the given arguments.
     * @param peerId The peer ID.
     * @param peerName The peer name.
     * @param bluetoothAddress The Bluetooth address of the peer.
     * @return An instance string or null in case of a failure.
     * @throws JSONException
     */
    public static String createInstanceString(
            String peerId, String peerName, String bluetoothAddress)
            throws JSONException {

        String instanceString = null;
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put(CommonUtils.JSON_ID_PEER_ID, peerId);
            jsonObject.put(CommonUtils.JSON_ID_PEER_NAME, peerName);
            jsonObject.put(CommonUtils.JSON_ID_BLUETOOTH_ADDRESS, bluetoothAddress);
            instanceString = jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "createInstanceString: Failed to construct a JSON object (from data "
                + peerId + " " + peerName + " " + bluetoothAddress + "): " + e.getMessage(), e);
            throw e;
        }

        return instanceString;
    }

    /**
     * Creates an instance string based on the given properties.
     * @param peerProperties The peer properties.
     * @return An instance string or null in case of a failure.
     * @throws JSONException
     */
    public static String createInstanceString(PeerProperties peerProperties) throws JSONException {
        return createInstanceString(
                peerProperties.peerId, peerProperties.peerName, peerProperties.bluetoothAddress);
    }

    /**
     * Resolves the peer properties from the given instance string.
     * @param instanceString The instance string.
     * @param peerProperties The peer properties to contain the resolved values.
     * @return True, if all the properties contain data (not validated though). False otherwise.
     * @throws JSONException
     */
    public static boolean getPropertiesFromInstanceString(
            String instanceString, PeerProperties peerProperties)
            throws JSONException {

        JSONObject jsonObject = new JSONObject(instanceString);
        peerProperties.peerId = jsonObject.getString(JSON_ID_PEER_ID);
        peerProperties.peerName = jsonObject.getString(JSON_ID_PEER_NAME);
        peerProperties.bluetoothAddress = jsonObject.getString(JSON_ID_BLUETOOTH_ADDRESS);

        return (peerProperties.peerId != null && !peerProperties.peerId.isEmpty()
                && peerProperties.peerName != null && !peerProperties.peerName.isEmpty()
                && peerProperties.bluetoothAddress != null && !peerProperties.bluetoothAddress.isEmpty());
    }
}
