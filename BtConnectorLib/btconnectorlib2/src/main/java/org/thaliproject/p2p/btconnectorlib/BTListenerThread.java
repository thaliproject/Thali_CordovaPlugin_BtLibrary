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

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;


public class BTListenerThread extends Thread {

    BTListenerThread that = this;

    public interface  BtListenCallback{
        public void GotConnection(BluetoothSocket socket,String peerId,String peerName,String peerAddress);
        public void ListeningFailed(String reason);
    }

    CountDownTimer HandShakeTimeOutTimer = new CountDownTimer(4000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            HandShakeFailed("TimeOut");
        }
    };

    String peerIdentifier = "";
    String peerName = "";
    String peerAddress = "";
    String shakeBackBuf = "shakehand";

    BTHandShakeSocketTread mBTHandShakeSocketTread = null;
    private String mInstanceString = "";
    private BtListenCallback callback;
    private final BluetoothServerSocket mSocket;
    BluetoothSocket acceptedSocket = null;
    boolean mStopped = false;

    public BTListenerThread(BtListenCallback Callback,BluetoothAdapter bta,UUID BtUuid, String btName, String InstanceString) {
        callback = Callback;
        mInstanceString = InstanceString;
        BluetoothServerSocket tmp = null;

        try {
            tmp = bta.listenUsingInsecureRfcommWithServiceRecord(btName, BtUuid);
        } catch (IOException e) {

            printe_line("listen() failed: " + e.toString());
        }
        mSocket = tmp;
    }

    public void run() {
    //    while (!this.interrupted()) {
        if(callback != null) {
            printe_line("starting to listen");

            try {
                if (mSocket != null) {
                    acceptedSocket = mSocket.accept();
                }
                if (acceptedSocket != null) {
                    printe_line("we got incoming connection");
                    mSocket.close();
                    mStopped = true;
                    if(mBTHandShakeSocketTread == null) {
                        HandShakeTimeOutTimer.start();
                        mBTHandShakeSocketTread = new BTHandShakeSocketTread(acceptedSocket, mHandler);
                        mBTHandShakeSocketTread.start();
                    }
                } else if (!mStopped) {
                    callback.ListeningFailed("Socket is null");
                }

            } catch (Exception e) {
                if (!mStopped) {
                    //return failure
                    printe_line("accept socket failed: " + e.toString());
                    callback.ListeningFailed(e.toString());
                }
            }
        }
       // }
    }

    public void HandShakeOk() {
        HandShakeTimeOutTimer.cancel();
        mBTHandShakeSocketTread = null;
        callback.GotConnection(that.acceptedSocket, that.peerIdentifier,that.peerName,that.peerAddress);
    }

    public void HandShakeFailed(String reason) {
        HandShakeTimeOutTimer.cancel();
        BTHandShakeSocketTread tmp = mBTHandShakeSocketTread;
        mBTHandShakeSocketTread = null;
        if(tmp != null) {
            tmp.CloseSocket();
        }

        callback.ListeningFailed("handshake: " + reason);
    }

    private void printe_line(String message){
        Log.d("BTListerThread",  "BTListerThread: " + message);
    }

    public void Stop() {
        printe_line("cancelled");
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
            printe_line("closing socket failed: " + e.toString());
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            BTHandShakeSocketTread tmpThread = mBTHandShakeSocketTread;
            if (tmpThread != null) {
                switch (msg.what) {
                    case BTHandShakeSocketTread.MESSAGE_WRITE: {
                        printe_line("MESSAGE_WRITE " + msg.arg1 + " bytes.");
                        HandShakeOk();
                    }
                    break;
                    case BTHandShakeSocketTread.MESSAGE_READ: {
                        printe_line("got MESSAGE_READ " + msg.arg1 + " bytes.");

                        try {
                            byte[] readBuf = (byte[]) msg.obj;// construct a string from the valid bytes in the buffer
                            String readMessage = new String(readBuf, 0, msg.arg1);

                            String JsonLine = readMessage;
                            printe_line("Got JSON from encryption:" + JsonLine);
                            JSONObject jObject = new JSONObject(JsonLine);

                            that.peerIdentifier = jObject.getString(BTConnector.JSON_ID_PEERID);
                            that.peerName = jObject.getString(BTConnector.JSON_ID_PEERNAME);
                            that.peerAddress = jObject.getString(BTConnector.JSON_ID_BTADRRES);
                            printe_line("peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                            tmpThread.write(shakeBackBuf.getBytes());

                        } catch (Exception e) {
                            HandShakeFailed("Decryptin instance failed , :" + e.toString());
                        }

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