// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.util.UUID;

/**
 * Created by juksilve on 13.3.2015.
 */
public class BTConnector_BtConnection implements BTListenerThread.BtListenCallback, BTConnectToThread.BtConnectToCallback{

    private final BTConnector_BtConnection that = this;

    public enum State{
        ConnectionConnecting,
        ConnectionConnected
    }

    public interface  ListenerCallback{
        void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress);
        void ConnectionFailed(String peerId, String peerName, String peerAddress);
        void ConnectionStateChanged(State newState);
    }

    private BluetoothAdapter mBluetoothAdapter= null;
    private BTListenerThread mBTListenerThread = null;
    private BTConnectToThread mBTConnectToThread = null;

    private ListenerCallback callback = null;
    private final UUID BluetoothUUID;
    private final String BluetootName;
    private String mInstanceString = "";
    private Handler mHandler = null;


    public BTConnector_BtConnection(Context Context, ListenerCallback Callback, BluetoothAdapter adapter, UUID BtUuid, String btName, String instanceLine){
        this.callback = Callback;
        this.mBluetoothAdapter = adapter;
        this.BluetoothUUID = BtUuid;
        this.BluetootName = btName;
        this.mInstanceString = instanceLine;
        this.mHandler = new Handler(Context.getMainLooper());
    }

    public void StartListening() {

        BTListenerThread tmpList = mBTListenerThread;
        mBTListenerThread = null;
        if (tmpList != null) {
            tmpList.Stop();
        }

        print_line("", "StartBluetooth listener");
        tmpList = new BTListenerThread(that, mBluetoothAdapter,BluetoothUUID,BluetootName,mInstanceString);
        tmpList.start();
        mBTListenerThread = tmpList;
    }

    public boolean TryConnect(BluetoothDevice device,UUID BtUUID, String peerId,String peerName, String peerAddress) {

        if (device == null) {
            print_line("", "No devices selected");
            return false;
        }

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

        return true;
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

        print_line("HS", "Hand Shake finished outgoing for : " + peerName);

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
        print_line("HS", "Incoming connection Hand Shake finished for : " + peerName);

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

                //only care if we have not stopped & nulled the instance
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.callback.ConnectionStateChanged(tmpState);
            }
        });
    }

    private void print_line(String who, String line) {
        Log.i("BTConnector_BtConnection" + who, line);
    }
}
