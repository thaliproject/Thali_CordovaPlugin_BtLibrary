// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by juksilve on 28.2.2015.
 */
class WifiServiceAdvertiser {

    private final WifiP2pManager p2p;
    private final WifiP2pManager.Channel channel;

    public WifiServiceAdvertiser(WifiP2pManager Manager, WifiP2pManager.Channel Channel) {
        this.p2p = Manager;
        this.channel = Channel;
    }

    public void Start(String instance,String service_type) {

        Map<String, String> record = new HashMap<String, String>();
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(instance, service_type, record);

        Log.i("", "Add local service :" + instance + ", length : " + instance.length());
        p2p.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                Log.i("","Added local service");
            }

            public void onFailure(int reason) {
                Log.i("","Adding local service failed, error code " + reason);
            }
        });
    }

    public void Stop() {
        p2p.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                Log.i("","Cleared local services");
            }

            public void onFailure(int reason) {
                Log.i("","Clearing local services failed, error code " + reason);
            }
        });
    }
}
