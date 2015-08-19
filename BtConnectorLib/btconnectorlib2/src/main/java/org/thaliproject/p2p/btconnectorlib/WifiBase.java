// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by juksilve on 6.3.2015.
 */
public class WifiBase implements WifiP2pManager.ChannelListener{

    public interface  WifiStatusCallBack{
        public void WifiStateChanged(int state);
    }

    private List<ServiceItem> connectedArray = new ArrayList<ServiceItem>();
    private WifiP2pManager p2p = null;
    private WifiP2pManager.Channel channel = null;
    private Context context = null;

    WifiStatusCallBack callback= null;
    MainBCReceiver mBRReceiver = null;
    private IntentFilter filter= null;

    public WifiBase(Context Context, WifiStatusCallBack handler){
        this.context = Context;
        this.callback = handler;
    }

    public boolean Start(){

        if(mBRReceiver == null) {
            try {
                mBRReceiver = new MainBCReceiver();
                filter = new IntentFilter();
                filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
                this.context.registerReceiver((mBRReceiver), filter);
            } catch (Exception e) {e.printStackTrace();}
        }

        p2p = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2p == null) {
            Log.d("WifiBase", "This device does not support Wi-Fi Direct");
            return false;
        }

        channel = p2p.initialize(this.context, this.context.getMainLooper(), this);


        return true;
    }
    public void Stop(){

        BroadcastReceiver tmpRec = mBRReceiver;
        mBRReceiver = null;
        if(tmpRec != null) {
            try {
                this.context.unregisterReceiver(tmpRec);
            } catch (Exception e) {e.printStackTrace();}
        }
    }

    public WifiP2pManager.Channel GetWifiChannel(){
        return channel;
    }
    public WifiP2pManager  GetWifiP2pManager(){
        return p2p;
    }

    public boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return false;
        }
        return wifiManager.isWifiEnabled();
    }

    public boolean setWifiEnabled(boolean enabled) {
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return false;
        }
        return wifiManager.setWifiEnabled(enabled);
    }

    @Override
    public void onChannelDisconnected() {
        // we might need to do something in here !
    }

    private void debug_print(String buffer) {
        Log.i("Service searcher", buffer);
    }

    private class MainBCReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                if(callback != null) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    callback.WifiStateChanged(state);
                }
            }
        }
    }
}
