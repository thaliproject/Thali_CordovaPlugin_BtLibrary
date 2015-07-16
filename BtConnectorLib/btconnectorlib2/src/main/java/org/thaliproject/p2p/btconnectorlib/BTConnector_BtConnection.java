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
public class BTConnector_BtConnection implements BTListenerThread.BtListenCallback, BTConnectToThread.BtConnectToCallback,BTHandShaker.BtHandShakeCallback {

    BTConnector_BtConnection that = this;

    public enum State{
        ConnectionIdle,
        ConnectionNotInitialized,
        ConnectionConnecting,
        ConnectionConnected
    }

    public interface  ListenerCallback{
        public void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress);
        public void ConnectionFailed(String peerId, String peerName, String peerAddress);
        public void ConnectionStateChanged(State newState);
    }


    BluetoothAdapter mBluetoothAdapter= null;
    BTListenerThread mBTListenerThread = null;
    BTConnectToThread mBTConnectToThread = null;
    BTHandShaker mBTHandShaker = null;

    private ListenerCallback callback = null;
    private Context context = null;
    private UUID BluetoothUUID;
    private String BluetootName;
    private String mInstanceString = "";
    private Handler mHandler = null;
    private State myState = State.ConnectionNotInitialized;


    public BTConnector_BtConnection(Context Context, ListenerCallback Callback, BluetoothAdapter adapter, UUID BtUuid, String btName, String instanceLine){
        this.context = Context;
        this.callback = Callback;
        this.mBluetoothAdapter = adapter;
        this.BluetoothUUID = BtUuid;
        this.BluetootName = btName;
        this.mInstanceString = instanceLine;
        this.mHandler = new Handler(this.context.getMainLooper());
        this.myState = State.ConnectionNotInitialized;

    }

    public void Start() {
        Stop();

        if (mBTListenerThread == null) {
            print_line("", "StartBluetooth listener");
            mBTListenerThread = new BTListenerThread(that, mBluetoothAdapter,BluetoothUUID,BluetootName);
            mBTListenerThread.start();
        }
    }

    public boolean TryConnect(BluetoothDevice device,UUID BtUUID, String peerId,String peerName, String peerAddress) {

        boolean ret = false;
        if (device != null) {

            ret = true;
            if (mBTConnectToThread != null) {
                mBTConnectToThread.Stop();
                mBTConnectToThread = null;
            }

            print_line("", "Selected device address: " + device.getAddress() +  ", name: " + device.getName());

            mBTConnectToThread = new BTConnectToThread(that, device,BtUUID,peerId,peerName,peerAddress);
            mBTConnectToThread.start();

            setState(State.ConnectionConnecting);
            print_line("", "Connecting to " + device.getName() + ", at " + device.getAddress());
        } else {
            // we'll get discovery stopped event soon enough
            // and it starts the discovery again, so no worries :)
            print_line("", "No devices selected");
        }

        return ret;
    }

    public void Stop() {
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

    @Override
    public void Connected(BluetoothSocket socket,String peerId,String peerName,String peerAddress) {

        if(mBTHandShaker == null) {
            final String peerIdTmp = peerId;
            final String peerNameTmp = peerName;
            final String peerAddressTmp = peerAddress;

            final BluetoothSocket tmp = socket;
            //make sure we do not close the socket,
            mBTConnectToThread = null;
            Stop();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBTHandShaker = new BTHandShaker(tmp, that, false);
                    // we crreated the connection, thus
                    // - we need to store our target device information for future use
                    // - we also need to sent our information to the other side
                    mBTHandShaker.Start(mInstanceString, peerIdTmp,peerNameTmp,peerAddressTmp);
                }
            });
        }
    }

    @Override
    public void GotConnection(BluetoothSocket socket) {
        if(mBTHandShaker == null) {
            final BluetoothSocket tmp = socket;
            Stop();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBTHandShaker = new BTHandShaker(tmp, that, true);
                    // we got incoming connection, thus we expet to get device information from them
                    // and thus do not supply any values in here
                    mBTHandShaker.Start(mInstanceString,"","","");
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
        Start(); // re-start listening for incoming connections.

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tmp.isConnected()) {
                    setState(State.ConnectionConnected);
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
                    Start();
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
                    Start();
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
                    Start();
                }
            }
        });
    }




    private void setState(State newState) {
        final State tmpState = newState;
        myState = tmpState;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.callback.ConnectionStateChanged(tmpState);
            }
        });
    }

    public void print_line(String who, String line) {
        Log.i("BTConnector_BtConnection" + who, line);
    }
}
