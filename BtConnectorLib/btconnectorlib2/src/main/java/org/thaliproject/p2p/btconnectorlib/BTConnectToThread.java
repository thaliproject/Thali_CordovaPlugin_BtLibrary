// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by juksilve on 12.3.2015.
 */
class BTConnectToThread extends Thread{

    public interface  BtConnectToCallback{
        void Connected(BluetoothSocket socket,String peerId,String peerName,String peerAddress);
        void ConnectionFailed(String reason,String peerId,String peerName,String peerAddress);
    }

    private BTHandShakeSocketTread mBTHandShakeSocketTread = null;
    private final String mInstanceString;
    private final BtConnectToCallback callback;
    private final BluetoothSocket mSocket;
    private final String mPeerId;
    private final String mPeerName;
    private final String mPeerAddress;

    private final CountDownTimer HandShakeTimeOutTimer = new CountDownTimer(4000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            HandShakeFailed("TimeOut");
        }
    };

    public BTConnectToThread(BtConnectToCallback Callback, BluetoothDevice device, UUID BtUUID, String peerId,String peerName, String peerAddress, String InstanceString)  throws IOException {
        callback = Callback;
        mPeerId = peerId;
        mPeerName = peerName;
        mPeerAddress = peerAddress;
        mInstanceString = InstanceString;
        mSocket = device.createInsecureRfcommSocketToServiceRecord(BtUUID);
    }
    public void run() {
        print_debug("","Starting to connect");

        try {
            mSocket.connect();
            //return success
            if (mBTHandShakeSocketTread == null) {
                HandShakeTimeOutTimer.start();

                mBTHandShakeSocketTread = new BTHandShakeSocketTread(mSocket, mHandler);
                mBTHandShakeSocketTread.start();
                mBTHandShakeSocketTread.write(mInstanceString.getBytes());
            }
        } catch (IOException e) {
            print_debug("","socket connect failed: " + e.toString());
            try {
                mSocket.close();
            } catch (IOException ee) {
                print_debug("","closing socket 2 failed: " + ee.toString());
            }
            callback.ConnectionFailed(e.toString(), mPeerId, mPeerName, mPeerAddress);
        }

    }

    private void HandShakeOk() {
        HandShakeTimeOutTimer.cancel();
        mBTHandShakeSocketTread = null;

        callback.Connected(mSocket,mPeerId,mPeerName,mPeerAddress);
    }

    private void HandShakeFailed(String reason) {
        HandShakeTimeOutTimer.cancel();
        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }
        callback.ConnectionFailed("handshake: " + reason, mPeerId, mPeerName, mPeerAddress);
    }

    public void Stop() {
        HandShakeTimeOutTimer.cancel();

        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.interrupt();
        }
        try {
            mSocket.close();
        } catch (IOException e) {
            print_debug("","closing socket failed: " + e.toString());
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (mBTHandShakeSocketTread != null) {
                switch (msg.what) {
                    case BTHandShakeSocketTread.MESSAGE_WRITE: {
                        print_debug("","MESSAGE_WRITE " + msg.arg1 + " bytes.");
                    }
                    break;
                    case BTHandShakeSocketTread.MESSAGE_READ: {
                        print_debug("","got MESSAGE_READ " + msg.arg1 + " bytes.");
                        HandShakeOk();
                    }
                    break;
                    case BTHandShakeSocketTread.SOCKET_DISCONNECTED: {
                        HandShakeFailed("SOCKET_DISCONNECTED");
                    }
                    break;
                    default:
                        throw new RuntimeException("Invalid message to Handshake handler");
                }
            } else {
                print_debug("","handleMessage called for NULL thread handler");
            }
        }
    };

    private void print_debug(String who, String message){
        //Log.d("BTConnectToThread" + who, "BTConnectToThread: " + message);
    }
}
