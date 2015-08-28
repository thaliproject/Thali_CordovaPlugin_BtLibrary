// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

/**
 * Created by juksilve on 13.3.2015.
 */
public class BTConnector implements BluetoothBase.BluetoothStatusChanged, DiscoveryCallback, BTConnector_BtConnection.ListenerCallback {

    private final BTConnector that = this;

    public class WifiBtStatus{
        public WifiBtStatus(){
            isWifiOk = false;
            isBtOk = false;
            isWifiEnabled = false;
            isBtEnabled = false;
      //      isBLESupported = false;
      //      isBLEAdvertisingSupported = false;
        }
        public boolean isWifiOk;
        public boolean isBtOk;
        public boolean isWifiEnabled;
        public boolean isBtEnabled;
    //    public boolean isBLESupported;
    //    public boolean isBLEAdvertisingSupported;
    }

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
        void Connected(BluetoothSocket socket, boolean incoming,String peerId,String peerName,String peerAddress);
        void ConnectionFailed(String peerId,String peerName,String peerAddress);
        void StateChanged(State newState);
    }

    public interface  ConnectSelector{
        ServiceItem CurrentPeersList(List<ServiceItem> available);
        void PeerDiscovered(ServiceItem service);
    }

    private BTConnector_Discovery mBTConnector_Discovery = null;

    private BluetoothBase mBluetoothBase = null;
    private BTConnector_BtConnection mBTConnector_BtConnection = null;

    private String mInstanceString = "";
    static final String JSON_ID_PEERID   = "pi";
    static final String JSON_ID_PEERNAME = "pn";
    static final String JSON_ID_BTADRRES = "ra";

    private final Callback callback;
    private final ConnectSelector connectSelector;
    private final Context context;
    private final Handler mHandler;

    private final BTConnectorSettings ConSettings;

    public BTConnector(Context Context, Callback Callback, ConnectSelector selector, BTConnectorSettings settings){
        this.context = Context;
        this.callback = Callback;
        this.mHandler = new Handler(this.context.getMainLooper());
        this.ConSettings = settings;
        this.connectSelector = selector;
    }

    public WifiBtStatus Start(String peerIdentifier, String peerName) {
        //initialize the system, and
        // make sure BT & Wifi is enabled before we start running

        WifiBtStatus ret = new WifiBtStatus();
        Stop();

        BluetoothBase tmpBTbase = new BluetoothBase(this.context, this);

        ret.isBtOk = tmpBTbase.Start();
        ret.isBtEnabled = tmpBTbase.isBluetoothEnabled();

        JSONObject jsonobj = new JSONObject();
        try {
            jsonobj.put(JSON_ID_PEERID, peerIdentifier);
            jsonobj.put(JSON_ID_PEERNAME, peerName);
            jsonobj.put(JSON_ID_BTADRRES, tmpBTbase.getAddress());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mInstanceString = jsonobj.toString();

        Log.i("", " mInstanceString : " + mInstanceString);

        // these are needed with BLE discovery, we'll fix the naming later, now just ignoring notto make breaks.
//      ret.isBLESupported =  BLEBase.isBLESupported(context);
//      ret.isBLEAdvertisingSupported =  BLEBase.isBLEAdvertisingSupported(context);
        ret.isWifiOk =  BLEBase.isBLESupported(context);
        ret.isWifiEnabled =  BLEBase.isBLEAdvertisingSupported(context);
                //    ret.isWifiOk = tmpWifibase.Start();
    //    ret.isWifiEnabled = tmpWifibase.isWifiEnabled();

        //set the global values with our local ones
        mBluetoothBase = tmpBTbase;

        if (!ret.isWifiOk || !ret.isBtOk) {
            // the HW is not supporting all needed stuff
            Log.i("", "BT available: " + ret.isBtOk + ", wifi available: " + ret.isWifiOk);
            setState(State.NotInitialized);
            return ret;
        }

        if (!ret.isBtEnabled  || !ret.isWifiEnabled) {
            //we will be waiting until both Wifi & BT are turned on
            setState(State.WaitingStateChange);
            return ret;
        }

        //all is good, so lets get started
        Log.i("", "All stuff available and enabled");
        startAll();
        return ret;
    }

    public void Stop() {
        stopAll();

        BluetoothBase tmpb =mBluetoothBase;
        mBluetoothBase = null;
        if (tmpb != null) {
            tmpb.Stop();
        }
    }

    public enum TryConnectReturnValues{
        Connecting,
        AlreadyAttemptingToConnect, // this test is wrong, don't want to change for compatibility
        NoSelectedDevice,
        BTDeviceFetchFailed
    }

    public TryConnectReturnValues TryConnect(ServiceItem selectedDevice) {

        if(selectedDevice == null) {
            return TryConnectReturnValues.NoSelectedDevice;
        }

        BluetoothBase tmoBase = mBluetoothBase;
        if(tmoBase == null) {// should never happen, would indicate uninitialized system
            throw new RuntimeException("BluetoothBase is not initialized properly");
        }

        BluetoothDevice device = tmoBase.getRemoteDevice(selectedDevice.peerAddress);
        if (device == null) {
            return TryConnectReturnValues.BTDeviceFetchFailed;
        }

        BTConnector_BtConnection tmpConn = mBTConnector_BtConnection;
        if(tmpConn == null) { // should never happen, would indicate uninitialized system
            throw new RuntimeException("Connector class is not initialized properly");
        }

        // actually the ret will now be always true, since mBTConnector_BtConnection only checks if device is non-null
        tmpConn.TryConnect(device, this.ConSettings.MY_UUID, selectedDevice.peerId, selectedDevice.peerName, selectedDevice.peerAddress);
        return TryConnectReturnValues.Connecting;
    }

    private void startServices() {
        stopServices();

        Log.i("", "Starting services address: " + mInstanceString + ", " + ConSettings);
        BTConnector_Discovery tmpDisc = new BTConnector_Discovery(this.context, this, ConSettings.SERVICE_TYPE, mInstanceString);
        tmpDisc.Start();
        mBTConnector_Discovery = tmpDisc;

    }

    private  void stopServices() {
        Log.i("", "Stopping services");
        BTConnector_Discovery tmp = mBTConnector_Discovery;
        mBTConnector_Discovery = null;
        if (tmp != null) {
            tmp.Stop();
        }
    }

    private  void startBluetooth() {
        stopBluetooth();
        BluetoothAdapter tmp = null;

        BluetoothBase tmpBase = mBluetoothBase;
        if (tmpBase != null) {
            tmp = tmpBase.getAdapter();
        }
        Log.i("", "StartBluetooth listener");
        BTConnector_BtConnection tmpconn = new BTConnector_BtConnection(this.context,this,tmp,this.ConSettings.MY_UUID, this.ConSettings.MY_NAME,this.mInstanceString);
        tmpconn.StartListening();
        mBTConnector_BtConnection = tmpconn;
    }

    private  void stopBluetooth() {
        Log.i("", "Stop Bluetooth");
        BTConnector_BtConnection tmp = mBTConnector_BtConnection;
        mBTConnector_BtConnection = null;
        if(tmp != null){
            tmp.Stop();
        }
    }

    private void stopAll() {
        Log.i("", "Stoping All");
        stopServices();
        stopBluetooth();
    }

    private void startAll() {
        stopAll();
        Log.i("", "Starting All");
        startServices();
        startBluetooth();
    }

    @Override
    public void BluetoothStateChanged(int state) {

        if (state == BluetoothAdapter.SCAN_MODE_NONE) {
            Log.i("BT", "Bluetooth DISABLED, stopping");
            stopAll();
            // indicate the waiting with state change
            setState(State.WaitingStateChange);
            return;
        }

        if (mBTConnector_Discovery != null) {
            Log.i("WB", "We already were running, thus doing nothing");
            return;
        }

        // we got bt back, and Wifi is already on, thus we can re-start now
        Log.i("BT", "Bluetooth enabled, re-starting");
        startAll();
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {
        if (this.connectSelector == null) {
            return;
        }

        final List<ServiceItem> availableTmp = list;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.connectSelector.CurrentPeersList(availableTmp);
            }
        });
    }

    @Override
    public void foundService(ServiceItem item) {
        if (this.connectSelector == null) {
            return;
        }

        final ServiceItem serviceTmp = item;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.connectSelector.PeerDiscovered(serviceTmp);
            }
        });
    }

    @Override
    public void StateChanged(DiscoveryCallback.State newState) {

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
            default:
                throw new RuntimeException("DiscoveryStateChanged called with invalid vale for BTConnector_Discovery.State");
        }
    }

    @Override
    public void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress) {
        if (this.callback == null) {
            return;
        }

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

    @Override
    public void ConnectionFailed(String peerId, String peerName, String peerAddress) {
        if (this.callback == null) {
            return;
        }

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

    @Override
    public void ConnectionStateChanged(BTConnector_BtConnection.State newState) {

        switch (newState) {
            case ConnectionConnecting:
                setState(State.Connecting);
                break;
            case ConnectionConnected:
                setState(State.Connected);
                break;
            default:
                throw new RuntimeException("ConnectionStateChanged called with invalid vale for BTConnector_BtConnection.State");
        }
    }

    private void setState(State newState) {
        if (this.callback == null) {
            return;
        }

        final State tmpState = newState;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.callback.StateChanged(tmpState);
            }
        });
    }
}
