/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * Commonly used utils and constants.
 */
public class CommonUtils {
    private static final String TAG = CommonUtils.class.getName();
    private static final String JSON_ID_PEER_ID   = "pi";
    private static final String JSON_ID_PEER_NAME = "pn";
    private static final String JSON_ID_PEER_BLUETOOTH_ADDRESS = "ra";

    /**
     * @return True, if we are running on Lollipop (Android version 5.x, API level 21) or higher.
     * False otehrwise.
     */
    public static boolean isLollipopOrHigher() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    /**
     * @return True, if we are running on Marshmallow (Android version 6.x) or higher. False otehrwise.
     */
    public static boolean isMarshmallowOrHigher() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    /**
     * Checks whether the given permission is granted (by the user) for the given activity.
     * @param permission The permission to check.
     * @param activity The activity.
     * @return True, if granted. False otherwise.
     */
    @TargetApi(23)
    public static boolean isPermissionGranted(String permission, Activity activity) {
        int permissionCheck = PackageManager.PERMISSION_DENIED;

        if (activity != null) {
            permissionCheck = ContextCompat.checkSelfPermission(activity, permission);
            Log.i(TAG, "isPermissionGranted: " + permission + ": " + permissionCheck);
        } else {
            throw new NullPointerException("The given activity is null");
        }

        return (permissionCheck == PackageManager.PERMISSION_GRANTED);
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
     * @param stringToCheck The string to check.
     * @return True, if the given string is not null and not empty.
     */
    public static boolean isNonEmptyString(String stringToCheck) {
        return (stringToCheck != null && stringToCheck.length() > 0);
    }

    /**
     * Creates an identity string based on the given properties.
     * @param peerProperties The peer properties.
     * @return An identity string or null in case of a failure.
     * @throws JSONException
     */
    public static String createIdentityString(PeerProperties peerProperties) throws JSONException {
        return createIdentityString(peerProperties.getId(), peerProperties.getName(), peerProperties.getBluetoothAddress());
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
        peerProperties.setId(jsonObject.getString(JSON_ID_PEER_ID));
        peerProperties.setName(jsonObject.getString(JSON_ID_PEER_NAME));
        peerProperties.setBluetoothAddress(jsonObject.getString(JSON_ID_PEER_BLUETOOTH_ADDRESS));

        return peerProperties.isValid();
    }
}
