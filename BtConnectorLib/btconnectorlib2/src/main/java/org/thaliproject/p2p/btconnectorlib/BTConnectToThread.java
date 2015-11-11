// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by juksilve on 12.3.2015.
 */
class BTConnectToThread extends Thread implements BTHandshakeSocketThread.HandShakeCallback {


    public interface  BtConnectToCallback{
        void Connected(BluetoothSocket socket, String peerId, String peerName, String peerAddress);
        void ConnectionFailed(String reason,String peerId,String peerName,String peerAddress);
    }

    private String peerIdentifier = "";
    private String peerName = "";
    private String peerAddress = "";

    private final BTConnectToThread that = this;
    private BTHandshakeSocketThread mBTHandshakeSocketThread = null;
    private final String mInstanceString;
    private final BtConnectToCallback callback;
    private final BluetoothSocket mSocket;

    public BTConnectToThread(BtConnectToCallback Callback, BluetoothDevice device, UUID BtUUID, String InstanceString)  throws IOException {
        callback = Callback;
        mInstanceString = InstanceString;
        mSocket = device.createInsecureRfcommSocketToServiceRecord(BtUUID);

    }

    // used to store outgoing data, so we can use it in fail callback
    public  void saveRemotePeerValue(String peerId,String peerName,String peerAddress){
        this.peerIdentifier = peerId;
        this.peerName = peerName;
        this.peerAddress = peerAddress;
    }

    public void run() {
        Log.i("BTConnectToThread", "Starting to connect");

        try {
            mSocket.connect();
            //return when success

            Log.i("BTConnectToThread", "Starting to Handshake");
            mBTHandshakeSocketThread = new BTHandshakeSocketThread(mSocket, this);
            mBTHandshakeSocketThread.setDefaultUncaughtExceptionHandler(that.getUncaughtExceptionHandler());
            mBTHandshakeSocketThread.start();

            mBTHandshakeSocketThread.write(mInstanceString.getBytes());
        } catch (IOException e) {
            Log.i("BTConnectToThread", "socket connect failed: " + e.toString());
            try {
                mSocket.close();
            } catch (IOException ee) {
                Log.i("BTConnectToThread", "closing socket 2 failed: " + ee.toString());
            }
            callback.ConnectionFailed(e.toString(),peerIdentifier,peerName,peerAddress);
        }
    }

    private void HandShakeOk(BluetoothSocket socket, String peerId, String peerName, String peerAddress) {
        Log.i("BTConnectToThread", "HandshakeOk : " + peerName);
        mBTHandshakeSocketThread = null;
        // on successful handshake, we'll pass the socket for further processing, so do not close it here
        callback.Connected(socket, peerId,peerName,peerAddress);
    }

    private void HandShakeFailed(String reason) {
        Log.i("BTConnectToThread", "HandshakeFailed : " + reason);
        BTHandshakeSocketThread tmp = mBTHandshakeSocketThread;
        mBTHandshakeSocketThread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }
        callback.ConnectionFailed("handshake: " + reason,peerIdentifier,peerName,peerAddress);
    }


    //called by timeout timer to cancel outgoing connection attempt
    public void Cancel() {
        Stop();
        callback.ConnectionFailed("Cancelled",peerIdentifier,peerName,peerAddress);
    }

    public void Stop() {
        BTHandshakeSocketThread tmp = mBTHandshakeSocketThread;
        mBTHandshakeSocketThread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.i("BTConnectToThread", "closing socket failed: " + e.toString());
        }
    }

    @Override
    public void handshakeMessageRead(byte[] buffer, int size, BTHandshakeSocketThread who) {
        Log.i("BTConnectToThread", "got MESSAGE_READ " + size + " bytes.");
        try {

            String JsonLine = new String(buffer);
            Log.i("BTConnectToThread", "Got JSON from encryption:" + JsonLine);
            JSONObject jObject = new JSONObject(JsonLine);

            //set that we got the identifications right from remote peer
            who.setPeerId(jObject.getString(BTConnector.JSON_ID_PEERID));
            who.setPeerName(jObject.getString(BTConnector.JSON_ID_PEERNAME));
            who.setPeerAddress(jObject.getString(BTConnector.JSON_ID_BTADRRESS));

            HandShakeOk(who.getSocket(),who.getPeerId(),who.getPeerName(),who.getPeerAddress());
        } catch (JSONException e) {
            HandShakeFailed("Decrypting instance failed , :" + e.toString());
        }
    }

    @Override
    public void handshakeMessageWrite(byte[] buffer, int size, BTHandshakeSocketThread who) {
        Log.i("BTConnectToThread", "MESSAGE_WRITE " + size + " bytes.");
    }

    @Override
    public void handshakeDisconnected(String error, BTHandshakeSocketThread who) {
        // we got disconnected after we were succccesfull
        if(mBTHandshakeSocketThread != null) {
            HandShakeFailed("SOCKET_DISCONNECTED");
        }
    }
}
