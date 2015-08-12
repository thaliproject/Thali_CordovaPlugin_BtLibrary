// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.connectorlib;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by juksilve on 12.3.2015.
 */
public class BTConnectToThread extends Thread {

    public interface  BtConnectToCallback{
        public void Connected(BluetoothSocket socket, String peerId, String peerName, String peerAddress);
        public void ConnectionFailed(String reason, String peerId, String peerName, String peerAddress);
    }

    private BtConnectToCallback callback;
    private final BluetoothSocket mSocket;
    String mPeerId;
    String mPeerName;
    String mPeerAddress;

    public BTConnectToThread(BtConnectToCallback Callback, BluetoothDevice device, UUID BtUUID, String peerId,String peerName, String peerAddress) {
        callback = Callback;
        mPeerId = peerId;
        mPeerName = peerName;
        mPeerAddress = peerAddress;
        BluetoothSocket tmp = null;
        try {
            tmp = device.createInsecureRfcommSocketToServiceRecord(BtUUID);
        } catch (IOException e) {
            printe_line("createInsecure.. failed: " + e.toString());
        }
        mSocket = tmp;
    }
    public void run() {
        printe_line("Starting to connect");
        if(mSocket != null && callback != null) {
            try {
                mSocket.connect();
                //return success
                callback.Connected(mSocket,mPeerId,mPeerName,mPeerAddress);
            } catch (IOException e) {
                printe_line("socket connect failed: " + e.toString());
                try {
                    mSocket.close();
                } catch (IOException ee) {
                    printe_line("closing socket 2 failed: " + ee.toString());
                }
                callback.ConnectionFailed(e.toString(),mPeerId,mPeerName,mPeerAddress);
            }
        }
    }

    private void printe_line(String message){
        //Log.d("BTConnectToThread", "BTConnectToThread: " + message);
    }

    public void Stop() {
        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            printe_line("closing socket failed: " + e.toString());
        }
    }
}
