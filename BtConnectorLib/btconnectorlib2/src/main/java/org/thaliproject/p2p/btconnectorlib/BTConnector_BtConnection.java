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
public class BTConnector_BtConnection implements BTListenerThread.BtListenCallback, BTConnectToThread.BtConnectToCallback{

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

    public synchronized void StartListening() {

        if (mBTListenerThread != null) {
            mBTListenerThread.Stop();
            mBTListenerThread = null;
        }

        print_line("", "StartBluetooth listener");
        mBTListenerThread = new BTListenerThread(that, mBluetoothAdapter,BluetoothUUID,BluetootName,mInstanceString);
        mBTListenerThread.start();
    }

    public synchronized boolean TryConnect(BluetoothDevice device,UUID BtUUID, String peerId,String peerName, String peerAddress) {

        boolean ret = false;
        if (device != null) {

            ret = true;
            BTConnectToThread tmp = mBTConnectToThread;
            mBTConnectToThread = null;
            if (tmp != null) {
                tmp.Stop();
            }

            print_line("", "Selected device address: " + device.getAddress() +  ", name: " + device.getName());

            tmp = new BTConnectToThread(that, device,BtUUID,peerId,peerName,peerAddress,mInstanceString);
            tmp.start();
            mBTConnectToThread = tmp;

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

        BTListenerThread tmpList = mBTListenerThread;
        mBTListenerThread = null;
        if (tmpList != null) {
            tmpList.Stop();
        }

        BTConnectToThread tmpConn = mBTConnectToThread;
        mBTConnectToThread = null;
        if (tmpConn != null) {
            tmpConn.Stop();
        }
    }

    @Override
    public void Connected(BluetoothSocket socket,String peerId,String peerName,String peerAddress) {
        mBTConnectToThread = null;
        final BluetoothSocket tmp = socket;

        print_line("HS", "HandShaked outgoing for : " + peerName);

        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tmp.isConnected()) {
                    setState(State.ConnectionConnected);
                    that.callback.Connected(tmp, false,peerIdTmp,peerNaTmp,peerAdTmp);
                } else {
                    ConnectionFailed("Disconnected",peerIdTmp,peerNaTmp,peerAdTmp);
                }
            }
        });
    }

    @Override
    public void GotConnection(BluetoothSocket socket,String peerId,String peerName,String peerAddress) {
        final BluetoothSocket tmp = socket;
        print_line("HS", "Incoming connection HandShaked for : " + peerName);

        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        StartListening(); // re-start listening for incoming connections.

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tmp.isConnected()) {
                    setState(State.ConnectionConnected);
                    that.callback.Connected(tmp, true,peerIdTmp,peerNaTmp,peerAdTmp);
                } else {
                    ListeningFailed("Disconnected");
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
                BTConnectToThread tmp = mBTConnectToThread;
                mBTConnectToThread = null;
                if (tmp != null) {
                    tmp.Stop();
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
                StartListening();
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
