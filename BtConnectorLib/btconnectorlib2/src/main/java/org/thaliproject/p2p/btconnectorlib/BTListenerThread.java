// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

/**
 * Created by juksilve on 12.3.2015.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;


class BTListenerThread extends Thread {

    private final BTListenerThread that = this;

    public interface  BtListenCallback{
        void GotConnection(BluetoothSocket socket,String peerId,String peerName,String peerAddress);
        void ListeningFailed(String reason);
    }

    private final CountDownTimer HandShakeTimeOutTimer = new CountDownTimer(4000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            HandShakeFailed("TimeOut");
        }
    };

    private String peerIdentifier = "";
    private String peerName = "";
    private String peerAddress = "";
    private final String shakeBackBuf = "shakehand";

    private BTHandShakeSocketTread mBTHandShakeSocketTread = null;
    private final BtListenCallback callback;
    private final BluetoothServerSocket mSocket;
    private BluetoothSocket acceptedSocket = null;
    private boolean mStopped = false;

    public BTListenerThread(BtListenCallback Callback,BluetoothAdapter bta,UUID BtUuid, String btName)  throws IOException {
        callback = Callback;
        mSocket = bta.listenUsingInsecureRfcommWithServiceRecord(btName, BtUuid);
    }

    public void run() {
        //    while (!this.interrupted()) {
        if (callback == null || mSocket == null) {
            return;
        }
        print_debug("","starting to listen");

        try {
            acceptedSocket = mSocket.accept();

            if (acceptedSocket != null) {
                print_debug("","we got incoming connection");
                mSocket.close();
                mStopped = true;
                if (mBTHandShakeSocketTread == null) {
                    HandShakeTimeOutTimer.start();
                    mBTHandShakeSocketTread = new BTHandShakeSocketTread(acceptedSocket, mHandler);
                    mBTHandShakeSocketTread.start();
                }
            } else if (!mStopped) {
                callback.ListeningFailed("Socket is null");
            }

        } catch (IOException e) {
            if (!mStopped) {
                //return failure
                print_debug("","accept socket failed: " + e.toString());
                callback.ListeningFailed(e.toString());
            }
        }

        // }
    }

    private void HandShakeOk() {
        HandShakeTimeOutTimer.cancel();
        mBTHandShakeSocketTread = null;
        callback.GotConnection(that.acceptedSocket, that.peerIdentifier,that.peerName,that.peerAddress);
    }

    private void HandShakeFailed(String reason) {
        HandShakeTimeOutTimer.cancel();
        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }

        callback.ListeningFailed("handshake: " + reason);
    }

    public void Stop() {
        print_debug("","cancelled");
        HandShakeTimeOutTimer.cancel();
        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.interrupt();
        }

        mStopped = true;
        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            print_debug("","closing socket failed: " + e.toString());
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            BTHandShakeSocketTread tmpThread = mBTHandShakeSocketTread;
            if (tmpThread != null) {
                switch (msg.what) {
                    case BTHandShakeSocketTread.MESSAGE_WRITE: {
                        print_debug("","MESSAGE_WRITE " + msg.arg1 + " bytes.");
                        HandShakeOk();
                    }
                    break;
                    case BTHandShakeSocketTread.MESSAGE_READ: {
                        print_debug("","got MESSAGE_READ " + msg.arg1 + " bytes.");

                        try {
                            byte[] readBuf = (byte[]) msg.obj;// construct a string from the valid bytes in the buffer

                            String JsonLine = new String(readBuf, 0, msg.arg1);
                            print_debug("","Got JSON from encryption:" + JsonLine);
                            JSONObject jObject = new JSONObject(JsonLine);

                            that.peerIdentifier = jObject.getString(BTConnector.JSON_ID_PEERID);
                            that.peerName = jObject.getString(BTConnector.JSON_ID_PEERNAME);
                            that.peerAddress = jObject.getString(BTConnector.JSON_ID_BTADRRES);
                            print_debug("","peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                            tmpThread.write(shakeBackBuf.getBytes());

                        } catch (JSONException e) {
                            //handshake timeout will eventually clear out stuff, we'll just wait.
                            HandShakeFailed("Decrypting instance failed , :" + e.toString());
                        }

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
        Log.d("BTListerThread",  "BTListerThread: " + message);
    }
}