// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;

import android.util.Log;

import java.util.Collection;
import java.util.List;

/**
 * Created by juksilve on 22.06.2015.
 */
public class BTConnector_Discovery implements WifiServiceSearcher.DiscoveryInternalCallBack {

    private final BTConnector_Discovery that = this;

    public interface  DiscoveryCallback{
        void CurrentPeersList(List<ServiceItem> available);
        void PeerDiscovered(ServiceItem service);
        void DiscoveryStateChanged(State newState);
    }
    private final DiscoveryCallback mDiscoveryCallback;

    public enum State{
        DiscoveryIdle,
        DiscoveryNotInitialized,
        DiscoveryFindingPeers,
        DiscoveryFindingServices
    }

    private WifiServiceAdvertiser mWifiAccessPoint = null;
    private WifiServiceSearcher mWifiServiceSearcher = null;
    private final String mEncryptedInstance;

    private final Context context;
    private final String mSERVICE_TYPE;
    private final WifiP2pManager.Channel channel;
    private final WifiP2pManager p2p;

    private final CountDownTimer ServiceFoundTimeOutTimer = new CountDownTimer(600000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            if(that.mDiscoveryCallback != null) {
                //to clear any peers available status
                that.mDiscoveryCallback.CurrentPeersList(null);
            }
            Start();
        }
    };


    public BTConnector_Discovery(WifiP2pManager.Channel p2pChannel, WifiP2pManager p2pManager,Context Context, DiscoveryCallback selector, String ServiceType,String instanceLine){
        this.context = Context;
        this.mSERVICE_TYPE = ServiceType;
        this.mDiscoveryCallback = selector;
        this.channel = p2pChannel;
        this.p2p =p2pManager;
        this.mEncryptedInstance = instanceLine;
    }

     public void Start(){
         Stop();

         if (channel == null || p2p == null) {
             return;
         }

         print_debug("", "Starting services address: " + mEncryptedInstance);

         WifiServiceAdvertiser tmpAdvertiser = new WifiServiceAdvertiser(p2p, channel);
         tmpAdvertiser.Start(mEncryptedInstance, mSERVICE_TYPE);
         mWifiAccessPoint = tmpAdvertiser;

         WifiServiceSearcher tmpSearcher = new WifiServiceSearcher(this.context, p2p, channel, this, mSERVICE_TYPE);
         tmpSearcher.Start();
         mWifiServiceSearcher = tmpSearcher;

         setState(State.DiscoveryFindingPeers);
     }

    public void Stop() {
        print_debug("", "Stopping services");
        ServiceFoundTimeOutTimer.cancel();

        WifiServiceAdvertiser tmpAC = mWifiAccessPoint;
        mWifiAccessPoint = null;
        if (tmpAC != null) {
            tmpAC.Stop();
        }

        WifiServiceSearcher tmpSS = mWifiServiceSearcher;
        mWifiServiceSearcher = null;
        if (tmpSS != null) {
            tmpSS.Stop();
        }
        setState(State.DiscoveryIdle);
    }

    @Override
    public void gotPeersList(Collection<WifiP2pDevice> list) {

        if(list.size() > 0) {
            ServiceFoundTimeOutTimer.cancel();
            ServiceFoundTimeOutTimer.start();

            print_debug("SS", "Found " + list.size() + " peers.");
            int num = 0;
            for (WifiP2pDevice peer : list) {
                num++;
                print_debug("SS", "Peer(" + num + "): " + peer.deviceName + " " + peer.deviceAddress);
            }

            setState(State.DiscoveryFindingServices);
        }else{
            print_debug("SS", "We got empty peers list");
            this.mDiscoveryCallback.CurrentPeersList(null);
        }
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {
        if(list != null && list.size() > 0) {
            this.mDiscoveryCallback.CurrentPeersList(list);
        }
    }

    @Override
    public void foundService(ServiceItem item) {
        this.mDiscoveryCallback.PeerDiscovered(item);
    }

    private void setState(State newState) {
       mDiscoveryCallback.DiscoveryStateChanged(newState);

    }

    private void print_debug(String who, String line) {
        Log.d("BTConnector_Discovery" + who, line);
    }
}
