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
public class BTConnector implements BluetoothBase.BluetoothStatusChanged, WifiBase.WifiStatusCallBack{

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

    static String JSON_ID_PEERID   = "pi";
    static String JSON_ID_PEERNAME = "pn";
    static String JSON_ID_BTADRRES = "ra";


    WifiBase mWifiBase = null;
    WifiServiceAdvertiser mWifiAccessPoint = null;
    WifiServiceSearcher mWifiServiceSearcher = null;
    private String mEncryptedInstance = "";
    BluetoothBase mBluetoothBase = null;
    BTListenerThread mBTListenerThread = null;
    BTConnectToThread mBTConnectToThread = null;
    BTHandShaker mBTHandShaker = null;

    AESCrypt mAESCrypt = null;

    boolean isStarting = false;
    private Callback callback = null;
    private ConnectSelector connectSelector = null;
    private Context context = null;
    private Handler mHandler = null;

    CountDownTimer ServiceFoundTimeOutTimer = new CountDownTimer(600000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            if(that.connectSelector != null) {
                //to clear any peers available status
                that.connectSelector.CurrentPeersList(null);
            }
            startAll();
        }
    };

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

    /*
        if (mBluetoothBase != null) {
            if(mAESCrypt != null){
                try {
                    String instanceLine = "{ \"" + JSON_ID_PEERID + "\": \"" + peerIdentifier + "\",";
                    instanceLine = instanceLine +"\"" + JSON_ID_PEERNAME + "\": \"" + peerName+ "\",";
                    instanceLine = instanceLine +"\"" + JSON_ID_BTADRRES + "\": \"" + mBluetoothBase.getAddress() + "\"}";
                    mEncryptedInstance = instanceLine;
                //    mEncryptedInstance = mAESCrypt.encrypt(instanceLine);
                    print_line("", instanceLine + " encrypted to : " + mEncryptedInstance);
                }catch (Exception e){
                    print_line("", "mAESCrypt.encrypt failed: " + e.toString());
                }
            }
        }*/


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

            mWifiAccessPoint = new WifiServiceAdvertiser(p2p, channel);
            mWifiAccessPoint.Start(mEncryptedInstance,ConSettings.SERVICE_TYPE);

            mWifiServiceSearcher = new WifiServiceSearcher(this.context, p2p, channel, this,ConSettings.SERVICE_TYPE,mAESCrypt);
            mWifiServiceSearcher.Start();
            setState(State.FindingPeers);
        }
    }

    private  void stopServices() {
        print_line("", "Stoppingservices");
        setState(State.Idle);
        if (mWifiAccessPoint != null) {
            mWifiAccessPoint.Stop();
            mWifiAccessPoint = null;
        }

        if (mWifiServiceSearcher != null) {
            mWifiServiceSearcher.Stop();
            mWifiServiceSearcher = null;
        }
    }

    private  void startBluetooth() {

        BluetoothAdapter tmp = null;
        if (mBluetoothBase != null) {
            tmp = mBluetoothBase.getAdapter();
        }

        if (mBTListenerThread == null && tmp != null) {
            print_line("", "StartBluetooth listener");
            mBTListenerThread = new BTListenerThread(that, tmp,ConSettings);
            mBTListenerThread.start();
        }
    }

    private  void stopBluetooth() {
        print_line("", "Stop Bluetooth");

        if(mBTHandShaker != null){
            mBTHandShaker.Stop();
            mBTHandShaker = null;
        }

        if (mBTListenerThread != null) {
            mBTListenerThread.Stop();
            mBTListenerThread = null;
        }

        if (mBTConnectToThread != null) {
            mBTConnectToThread.Stop();
            mBTConnectToThread = null;
        }
    }

    private void stopAll() {
        print_line("", "Stoping All");
        ServiceFoundTimeOutTimer.cancel();
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
    public void Connected(BluetoothSocket socket,ServiceItem toDevice) {
        //make sure we do not close the socket,
        if(mBTHandShaker == null) {

            final ServiceItem toDeviceTmp = toDevice;
            final BluetoothSocket tmp = socket;
            mBTConnectToThread = null;
            stopBluetooth();
            stopServices();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBTHandShaker = new BTHandShaker(tmp, that, false,that.mAESCrypt);
                    // we crreated the connection, thus
                    // - we need to store our target device information for future use
                    // - we also need to sent our information to the other side
                    mBTHandShaker.Start(mEncryptedInstance, toDeviceTmp);
                }
            });
        }
    }

    @Override
    public void GotConnection(BluetoothSocket socket) {
        if(mBTHandShaker == null) {
            final BluetoothSocket tmp = socket;
            stopBluetooth();
            stopServices();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBTHandShaker = new BTHandShaker(tmp, that, true,that.mAESCrypt);
                    // we got incoming connection, thus we expet to get device information from them
                    // and thus do not supply any values in here
                    mBTHandShaker.Start(mEncryptedInstance,null);
                }
            });
        }
    }

    @Override
    public void HandShakeOk(BluetoothSocket socket, boolean incoming,String peerId,String peerName,String peerAddress) {
        final BluetoothSocket tmp = socket;
        final boolean incomingTmp = incoming;

        print_line("HS", "HandShakeOk for incoming = " + incoming);

        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        if(mBTHandShaker != null) {
            mBTHandShaker.Stop();
            mBTHandShaker = null;
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tmp.isConnected()) {
                    setState(State.Connected);
                    that.callback.Connected(tmp, incomingTmp,peerIdTmp,peerNaTmp,peerAdTmp);
                } else {
                    if(incomingTmp) {
                        ListeningFailed("Disconnected");
                    }else{
                        ConnectionFailed("Disconnected",peerIdTmp,peerNaTmp,peerAdTmp);
                    }
                }
            }
        });
    }

    @Override
    public void HandShakeFailed(String reason, boolean incoming,String peerId,String peerName,String peerAddress) {

        final String reasontmp = reason;
        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                print_line("HS", "HandShakeFailed: " + reasontmp);

                if(peerIdTmp.length() > 0 && peerNaTmp.length() > 0) {
                    that.callback.ConnectionFailed(peerIdTmp, peerNaTmp, peerAdTmp);
                }
                //only care if we have not stoppeed & nulled the instance
                if(mBTHandShaker != null) {
                    mBTHandShaker.tryCloseSocket();
                    mBTHandShaker.Stop();
                    mBTHandShaker = null;

                    startServices();
                    startBluetooth();
                }
            }
        });
    }

    @Override
    public void ConnectionFailed(String reason,String peerId,String peerName,String peerAddress) {
        final String tmp = reason;
        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                print_line("CONNEC", "Error: " + tmp);

                that.callback.ConnectionFailed(peerIdTmp,peerNaTmp,peerAdTmp);

                //only care if we have not stoppeed & nulled the instance
                if (mBTConnectToThread != null) {
                    mBTConnectToThread.Stop();
                    mBTConnectToThread = null;

                    startServices();
                }
            }
        });
    }

    @Override
    public void ListeningFailed(String reason) {
        final String tmp = reason;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                print_line("LISTEN", "Error: " + tmp);

                //only care if we have not stoppeed & nulled the instance
                if (mBTListenerThread != null) {
                    mBTListenerThread.Stop();
                    mBTListenerThread = null;

                    startBluetooth();
                }
            }
        });
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

                if(mWifiAccessPoint != null && mWifiServiceSearcher != null) {
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
                if(mWifiAccessPoint != null && mWifiServiceSearcher != null) {
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
    public void gotPeersList(Collection<WifiP2pDevice> list) {

        if(list.size() > 0) {
            ServiceFoundTimeOutTimer.cancel();
            ServiceFoundTimeOutTimer.start();

            print_line("SS", "Found " + list.size() + " peers.");
            int numm = 0;
            for (WifiP2pDevice peer : list) {
                numm++;
                print_line("SS", "Peer(" + numm + "): " + peer.deviceName + " " + peer.deviceAddress);
            }

            setState(State.FindingServices);
        }else{
            print_line("SS", "We got empty peers list");
            this.connectSelector.CurrentPeersList(null);
        }
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {
        if(mWifiBase != null && list != null && list.size() > 0) {

            ServiceItem selItem = null;

            if(this.connectSelector != null){
                selItem = this.connectSelector.CurrentPeersList(list);
            }else
            {
                selItem = mWifiBase.SelectServiceToConnect(list);
            }

            TryConnect(selItem);
        }
    }

    @Override
    public void foundService(ServiceItem item) {
        if(this.connectSelector != null){
            this.connectSelector.PeerDiscovered(item);
        }
    }

    public boolean TryConnect(ServiceItem selectedDevice) {

        boolean ret = false;
        if (selectedDevice != null && mBluetoothBase != null) {

            ret = true;
            if (mBTConnectToThread != null) {
                mBTConnectToThread.Stop();
                mBTConnectToThread = null;
            }

            if(ServiceFoundTimeOutTimer != null) {
                ServiceFoundTimeOutTimer.cancel();
            }

            print_line("", "Selected device address: " + selectedDevice.peerAddress +  ", from: " + selectedDevice.peerName);

            BluetoothDevice device = mBluetoothBase.getRemoteDevice(selectedDevice.peerAddress);

            mBTConnectToThread = new BTConnectToThread(that, device,ConSettings,selectedDevice);
            mBTConnectToThread.start();

            //we have connection, no need to find new ones
            stopServices();
            setState(State.Connecting);
            print_line("", "Connecting to " + device.getName() + ", at " + device.getAddress());
        } else {
            // we'll get discovery stopped event soon enough
            // and it starts the discovery again, so no worries :)
            print_line("", "No devices selected");
        }

        return ret;
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
