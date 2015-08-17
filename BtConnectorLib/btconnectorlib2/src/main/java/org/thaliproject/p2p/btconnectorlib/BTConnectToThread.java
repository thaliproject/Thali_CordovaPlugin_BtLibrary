// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by juksilve on 12.3.2015.
 */
public class BTConnectToThread extends Thread{

    public interface  BtConnectToCallback{
        public void Connected(BluetoothSocket socket,String peerId,String peerName,String peerAddress);
        public void ConnectionFailed(String reason,String peerId,String peerName,String peerAddress);
    }

    BTHandShakeSocketTread mBTHandShakeSocketTread = null;
    private String mInstanceString = "";
    private BtConnectToCallback callback;
    private final BluetoothSocket mSocket;
    String mPeerId;
    String mPeerName;
    String mPeerAddress;

    CountDownTimer HandShakeTimeOutTimer = new CountDownTimer(4000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            HandShakeFailed("TimeOut");
        }
    };

    public BTConnectToThread(BtConnectToCallback Callback, BluetoothDevice device, UUID BtUUID, String peerId,String peerName, String peerAddress, String InstanceString) {
        callback = Callback;
        mPeerId = peerId;
        mPeerName = peerName;
        mPeerAddress = peerAddress;
        mInstanceString = InstanceString;

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
                if(mBTHandShakeSocketTread == null) {
                    HandShakeTimeOutTimer.start();
                    mBTHandShakeSocketTread = new BTHandShakeSocketTread(mSocket, mHandler);
                    mBTHandShakeSocketTread.start();
                    mBTHandShakeSocketTread.write(mInstanceString.getBytes());
                }
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

    public void HandShakeOk() {
        HandShakeTimeOutTimer.cancel();
        mBTHandShakeSocketTread = null;

        callback.Connected(mSocket,mPeerId,mPeerName,mPeerAddress);
    }

    public void HandShakeFailed(String reason) {
        HandShakeTimeOutTimer.cancel();
        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }
        callback.ConnectionFailed("handshake: " + reason, mPeerId, mPeerName, mPeerAddress);
    }

    private void printe_line(String message){
        //Log.d("BTConnectToThread", "BTConnectToThread: " + message);
    }

    public void Stop() {
        HandShakeTimeOutTimer.cancel();

        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.interrupt();
        }
        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            printe_line("closing socket failed: " + e.toString());
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mBTHandShakeSocketTread != null) {
                switch (msg.what) {
                    case BTHandShakeSocketTread.MESSAGE_WRITE: {
                        printe_line("MESSAGE_WRITE " + msg.arg1 + " bytes.");
                    }
                    break;
                    case BTHandShakeSocketTread.MESSAGE_READ: {
                        printe_line("got MESSAGE_READ " + msg.arg1 + " bytes.");
                        HandShakeOk();
                    }
                    break;
                    case BTHandShakeSocketTread.SOCKET_DISCONNEDTED: {
                        HandShakeFailed("SOCKET_DISCONNEDTED");
                    }
                    break;
                }
            } else {
                printe_line("handleMessage called for NULL thread handler");
            }
        }
    };
}
