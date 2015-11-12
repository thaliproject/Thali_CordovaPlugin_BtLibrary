// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by juksilve on 13.3.2015.
 */
public class BTConnector_BtConnection implements BTListenerThread.BtListenCallback, BTConnectToThread.BtConnectToCallback {

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

    private static final String TAG = BTConnector_BtConnection.class.getName();

    // incase the connection establishment takes too long, then we need to cancel it
    private final CountDownTimer connectionTimeoutTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) { }
        public void onFinish() {
            //we got timeout, thus lets go for next round
            Log.i("BtConnection", "connectionTimeoutTimer");
            BTConnectToThread tmp = mBTConnectToThread;
            mBTConnectToThread = null;
            if (tmp != null) {
                // will stop & report failing to connect
                tmp.Cancel();
            }
        }
    };

    private final BluetoothAdapter mBluetoothAdapter;
    private BTListenerThread mBTListenerThread = null;
    private BTConnectToThread mBTConnectToThread = null;

    private final ListenerCallback callback;
    private final UUID BluetoothUUID;
    private final String BluetootName;
    private final String mInstanceString;
    private final Handler mHandler;

    // implementation which forwards any uncaught exception from threads to the UI app's thread
    private final Thread.UncaughtExceptionHandler mThreadUncaughtExceptionHandler;

    public BTConnector_BtConnection(Context Context, ListenerCallback Callback, BluetoothAdapter adapter, UUID BtUuid, String btName, String instanceLine){
        this.callback = Callback;
        this.mBluetoothAdapter = adapter;
        this.BluetoothUUID = BtUuid;
        this.BluetootName = btName;
        this.mInstanceString = instanceLine;
        this.mHandler = new Handler(Context.getMainLooper());
        this.mThreadUncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(TAG, "Uncaught exception: " + ex.getMessage(), ex);
                final Throwable tmpException = ex;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        throw new RuntimeException(tmpException);
                    }
                });
            }
        };
    }

    public void StartListening() {

        BTListenerThread tmpList = mBTListenerThread;
        mBTListenerThread = null;
        if (tmpList != null) {
            tmpList.Stop();
        }

        Log.i("", "StartBluetooth listener");
        try {
            tmpList = new BTListenerThread(that, mBluetoothAdapter, BluetoothUUID, BluetootName, mInstanceString);
        }catch (IOException e){
            e.printStackTrace();
            // in this point of time we can not accept any incoming connections, thus what should we do ?
            return;
        }
        tmpList.setDefaultUncaughtExceptionHandler(mThreadUncaughtExceptionHandler);
        tmpList.start();
        mBTListenerThread = tmpList;
    }

    public boolean TryConnect(BluetoothDevice device,UUID BtUUID, String peerId,String peerName, String peerAddress) {

        if (device == null) {
            Log.i("", "No devices selected");
            return false;
        }

        BTConnectToThread tmp = mBTConnectToThread;
        mBTConnectToThread = null;
        if (tmp != null) {
            tmp.Stop();
        }

        Log.i("", "Selected device address: " + device.getAddress() + ", name: " + device.getName());

        try {
            tmp = new BTConnectToThread(that, device, BtUUID, mInstanceString);
            tmp.saveRemotePeerValue(peerId, peerName, peerAddress);
        } catch (IOException e){
            e.printStackTrace();
            //lets inform that outgoing connection just failed.
            ConnectionFailed(e.toString(), peerId, peerName, peerAddress);
            return false;
        }
        tmp.setDefaultUncaughtExceptionHandler(mThreadUncaughtExceptionHandler);
        connectionTimeoutTimer.start();
        tmp.start();
        mBTConnectToThread = tmp;

        setState(State.ConnectionConnecting);
        Log.i("", "Connecting to " + device.getName() + ", at " + device.getAddress());

        return true;
    }

    public void Stop() {
        Log.i("", "Stop Bluetooth");
        connectionTimeoutTimer.cancel();

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
        connectionTimeoutTimer.cancel();
        mBTConnectToThread = null;
        final BluetoothSocket tmp = socket;

        Log.i("HS", "Hand Shake finished outgoing for : " + peerName);

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
                    ConnectionFailed("Disconnected", peerIdTmp, peerNaTmp, peerAdTmp);
                }
            }
        });
    }

    @Override
    public void GotConnection(BluetoothSocket socket,String peerId,String peerName,String peerAddress) {
        final BluetoothSocket tmp = socket;
        Log.i("HS", "Incoming connection Hand Shake finished for : " + peerName);

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
        connectionTimeoutTimer.cancel();
        final String tmp = reason;
        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.i("CONNEC", "Error: " + tmp);

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
                Log.i("LISTEN", "Error: " + tmp);
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
}
