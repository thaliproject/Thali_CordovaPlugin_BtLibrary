/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Wi-Fi service advertiser to advertise our identity and service.
 */
class WifiServiceAdvertiser {
    private static final String TAG = WifiServiceAdvertiser.class.getName();
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;

    /**
     * Constructor.
     * @param p2pManager The Wi-Fi P2P manager instance.
     * @param p2pChannel The Wi-Fi P2P channel.
     */
    public WifiServiceAdvertiser(WifiP2pManager p2pManager, WifiP2pManager.Channel p2pChannel) {
        this.mP2pManager = p2pManager;
        this.mP2pChannel = p2pChannel;
    }

    /**
     * Starts advertising our identity in the local service with the given service type.
     * @param myIdentityString Our identity string.
     * @param serviceType The service type.
     */
    public void start(String myIdentityString, String serviceType) {
        Log.i(TAG, "start: Identity string: \"" + myIdentityString + "\", service type: \"" + serviceType + "\"");
        Map<String, String> record = new HashMap<String, String>();
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo service =
                WifiP2pDnsSdServiceInfo.newInstance(myIdentityString, serviceType, record);

        mP2pManager.addLocalService(mP2pChannel, service, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG,"Local service added successfully");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to add local service, got error code: " + reason);
            }
        });
    }

    /**
     * Stops advertising the service.
     */
    public void stop() {
        mP2pManager.clearLocalServices(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Local services cleared successfully");
            }

            public void onFailure(int reason) {
                Log.e(TAG, "Failed to clear local services, got error code: " + reason);
            }
        });
    }
}
