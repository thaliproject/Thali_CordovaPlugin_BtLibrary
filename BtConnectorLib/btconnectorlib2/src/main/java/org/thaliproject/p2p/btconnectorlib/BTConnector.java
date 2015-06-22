// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Created by juksilve on 13.3.2015.
 */
public class BTConnector implements BluetoothBase.BluetoothStatusChanged, WifiBase.WifiStatusCallBack, BTConnector_Discovery.DiscoveryCallback, BTConnector_BtConnection.ListenerCallback {

    BTConnector that = this;

    public enum State{
        Idle,
        NotInitialized,
        WaitingStateChange,
        FindingPeers,
        FindingServices,
        Connecting,
        Connected
    }

    public interface  Callback{
        public void Connected(BluetoothSocket socket, boolean incoming,String peerId,String peerName,String peerAddress);
        public void ConnectionFailed(String peerId,String peerName,String peerAddress);
        public void StateChanged(State newState);
    }

    public interface  ConnectSelector{
        public ServiceItem CurrentPeersList(List<ServiceItem> available);
        public void PeerDiscovered(ServiceItem service);
    }

    private State myState = State.NotInitialized;

    WifiBase mWifiBase = null;
    BTConnector_Discovery mBTConnector_Discovery = null;

    BluetoothBase mBluetoothBase = null;
    BTConnector_BtConnection mBTConnector_BtConnection = null;

    AESCrypt mAESCrypt = null;
    private String mEncryptedInstance = "";
    static String JSON_ID_PEERID   = "pi";
    static String JSON_ID_PEERNAME = "pn";
    static String JSON_ID_BTADRRES = "ra";


    boolean isStarting = false;
    private Callback callback = null;
    private ConnectSelector connectSelector = null;
    private Context context = null;
    private Handler mHandler = null;


    BTConnectorSettings ConSettings = new BTConnectorSettings();

    public BTConnector(Context Context, Callback Callback, ConnectSelector selector, BTConnectorSettings settings, String InstancePassword){
        this.context = Context;
        this.callback = Callback;
        this.mHandler = new Handler(this.context.getMainLooper());
        this.myState = State.NotInitialized;
        this.ConSettings = settings;
        this.connectSelector = selector;

        try{
            mAESCrypt = new AESCrypt(InstancePassword);
        }catch(Exception e){
            print_line("", "mAESCrypt instance creation failed: " + e.toString());
            mAESCrypt = null;
        }
    }

    public void Start(String peerIdentifier, String peerName) {
        //initialize the system, and
        // make sure BT & Wifi is enabled before we start running

        Stop();

        isStarting = true;
        mBluetoothBase = new BluetoothBase(this.context, this);

        Boolean btOk = mBluetoothBase.Start();
        Boolean btEnabled = mBluetoothBase.isBluetoothEnabled();

        if (mBluetoothBase != null) {
            String instanceLine = "{ \"" + JSON_ID_PEERID + "\": \"" + peerIdentifier + "\",";
            instanceLine = instanceLine + "\"" + JSON_ID_PEERNAME + "\": \"" + peerName + "\",";
            instanceLine = instanceLine + "\"" + JSON_ID_BTADRRES + "\": \"" + mBluetoothBase.getAddress() + "\"}";
            mEncryptedInstance = instanceLine;
            //mEncryptedInstance = mAESCrypt.encrypt(instanceLine);
        }

        print_line("", " mEncryptedInstance : " + mEncryptedInstance);

        mWifiBase = new WifiBase(this.context, this);
        Boolean WifiOk = mWifiBase.Start();
        Boolean WifiEnabled = mWifiBase.isWifiEnabled();

        if (!WifiOk || !btOk) {
            print_line("", "BT available: " + btOk + ", wifi available: " + WifiOk);
            setState(State.NotInitialized);
        } else if (btEnabled && WifiEnabled) {
             print_line("", "All stuf available and enabled");
             startAll();
        }else{
            //we wait untill both Wifi & BT are turned on
            setState(State.WaitingStateChange);
        }
    }

    public void Stop() {
        stopAll();
        if (mWifiBase != null) {
            mWifiBase.Stop();
            mWifiBase = null;
        }

        if (mBluetoothBase != null) {
            mBluetoothBase.Stop();
            mBluetoothBase = null;
        }
    }

    public boolean TryConnect(ServiceItem selectedDevice) {

        boolean ret = false;
        if(mBTConnector_BtConnection != null && selectedDevice != null)
        {
            BluetoothDevice device = mBluetoothBase.getRemoteDevice(selectedDevice.peerAddress);
            if(device != null) {
                ret = mBTConnector_BtConnection.TryConnect(device, this.ConSettings.MY_UUID, selectedDevice.peerId, selectedDevice.peerName, selectedDevice.peerAddress);
            }
        }
        return ret;
    }

    private void startServices() {
        stopServices();

        WifiP2pManager.Channel channel = null;
        WifiP2pManager p2p = null;
        if (mWifiBase != null) {
            channel = mWifiBase.GetWifiChannel();
            p2p = mWifiBase.GetWifiP2pManager();
        }

        if (channel != null && p2p != null) {
            print_line("", "Starting services address: " + mEncryptedInstance + ", " + ConSettings);

            mBTConnector_Discovery = new BTConnector_Discovery(channel,p2p,this.context,this,ConSettings.SERVICE_TYPE,mAESCrypt,mEncryptedInstance);
            mBTConnector_Discovery.Start();
        }
    }

    private  void stopServices() {
        print_line("", "Stoppingservices");
        if (mBTConnector_Discovery != null) {
            mBTConnector_Discovery.Stop();
            mBTConnector_Discovery = null;
        }
    }

    private  void startBluetooth() {
        stopBluetooth();
        BluetoothAdapter tmp = null;
        if (mBluetoothBase != null) {
            tmp = mBluetoothBase.getAdapter();
        }
        print_line("", "StartBluetooth listener");
        mBTConnector_BtConnection = new BTConnector_BtConnection(this.context,this,tmp,this.ConSettings.MY_UUID, this.ConSettings.MY_NAME,this.mAESCrypt,this.mEncryptedInstance);
        mBTConnector_BtConnection.Start();
    }

    private  void stopBluetooth() {
        print_line("", "Stop Bluetooth");

        if(mBTConnector_BtConnection != null){
            mBTConnector_BtConnection.Stop();
            mBTConnector_BtConnection = null;
        }
    }

    private void stopAll() {
        print_line("", "Stoping All");
        stopServices();
        stopBluetooth();
    }

    private void startAll() {
        stopAll();
        print_line("", "Starting All");
        startServices();
        startBluetooth();
    }

    @Override
    public void BluetoothStateChanged(int state) {
        if (state == BluetoothAdapter.SCAN_MODE_NONE) {
            print_line("BT", "Bluetooth DISABLED, stopping");
            stopAll();
            // indicate the waiting with state change
            setState(State.WaitingStateChange);
        } else if (state == BluetoothAdapter.SCAN_MODE_CONNECTABLE
                || state == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            if (mWifiBase != null && mWifiBase.isWifiEnabled()) {
                print_line("BT", "Bluetooth enabled, re-starting");

                if(mBTConnector_Discovery != null) {
                    print_line("WB", "We already were running, thus doing nothing");
                }else{
                    startAll();
                }
            }
        }
    }

    @Override
    public void WifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            if (mBluetoothBase != null && mBluetoothBase.isBluetoothEnabled()) {
                // we got wifi back, so we can re-start now
                print_line("WB", "Wifi is now enabled !");
                if(mBTConnector_Discovery != null) {
                    print_line("WB", "We already were running, thus doing nothing");
                }else{
                    startAll();
                }
            }
        } else {
            //no wifi availavble, thus we need to stop doing anything;
            print_line("WB", "Wifi is DISABLEd !!");
            stopAll();
            // indicate the waiting with state change
            setState(State.WaitingStateChange);
        }
    }

    @Override
    public void CurrentPeersList(List<ServiceItem> available) {
        if (this.connectSelector != null) {
            final List<ServiceItem> availableTmp = available;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ServiceItem retItem = that.connectSelector.CurrentPeersList(availableTmp);
                    //we could checkl if the item is non-null and try connect

                }
            });
        }
    }

    @Override
    public void PeerDiscovered(ServiceItem service) {
        if (this.connectSelector != null) {
            final ServiceItem serviceTmp = service;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.connectSelector.PeerDiscovered(serviceTmp);
                }
            });
        }
    }

    @Override
    public void DiscoveryStateChanged(BTConnector_Discovery.State newState) {

        if(callback != null) {
            switch (newState) {
                case DiscoveryIdle:
                    setState(State.Idle);
                    break;
                case DiscoveryNotInitialized:
                    setState(State.NotInitialized);
                    break;
                case DiscoveryFindingPeers:
                    setState(State.FindingPeers);
                    break;
                case DiscoveryFindingServices:
                    setState(State.FindingServices);
                    break;
            }
        }
    }

    @Override
    public void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress) {
        if (callback != null) {
            final BluetoothSocket socketTmp = socket;
            final boolean incomingTmp = incoming;
            final String peerIdTmp = peerId;
            final String peerNameTmp = peerName;
            final String peerAddressTmp = peerAddress;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.Connected(socketTmp, incomingTmp, peerIdTmp, peerNameTmp, peerAddressTmp);
                }
            });
        }
    }

    @Override
    public void ConnectionFailed(String peerId, String peerName, String peerAddress) {
        if(callback != null) {
            final String peerIdTmp = peerId;
            final String peerNameTmp = peerName;
            final String peerAddressTmp = peerAddress;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.ConnectionFailed(peerIdTmp, peerNameTmp, peerAddressTmp);
                }
            });
        }
    }

    @Override
    public void ConnectionStateChanged(BTConnector_BtConnection.State newState) {
        if(callback != null) {
            switch (newState) {
                case ConnectionConnecting:
                    setState(State.Connecting);
                    break;
                case ConnectionConnected:
                    setState(State.Connected);
                    break;
            }
        }
    }

    private void setState(State newState) {
        final State tmpState = newState;
        myState = tmpState;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.callback.StateChanged(tmpState);
            }
        });
    }

    public void print_line(String who, String line) {
        Log.i("BTConnector" + who, line);
    }
}
