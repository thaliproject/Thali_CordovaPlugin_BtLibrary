// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.connectorlib;

import android.bluetooth.BluetoothSocket;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

/**
 * Created by juksilve on 11.3.2015.
 */

public class BTHandShaker {

    BTHandShaker that = this;

    public interface  BtHandShakeCallback{
        public void HandShakeOk(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress);
        public void HandShakeFailed(String reason, boolean incoming, String peerId, String peerName, String peerAddress);
    }

    private BtHandShakeCallback callback;

    private BluetoothSocket mmSocket;

    private boolean isIncoming;

    String handShakeBuf = "handshake";
    String shakeBackBuf = "shakehand";

    String peerIdentifier = "";
    String peerName = "";
    String peerAddress = "";

    BTHandShakeSocketTread mBTHandShakeSocketTread = null;

    CountDownTimer HandShakeTimeOutTimer = new CountDownTimer(4000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            callback.HandShakeFailed("TimeOut",isIncoming,that.peerIdentifier,that.peerName,that.peerAddress);
        }
    };

    public BTHandShaker(BluetoothSocket socket, BtHandShakeCallback Callback, boolean incoming) {
        printe_line("Creating BTHandShaker");
        callback = Callback;
        mmSocket = socket;
        isIncoming = incoming;
    }


    public String GetPeerId(){
        return peerIdentifier;
    }
    public String GetPeerName() {
        return peerName;
    }
    public String GetPeerAddress() {
        return peerAddress;
    }

    public void Start(String instanceData,String peerId,String peerName,String peerAddress) {
        printe_line("Start");
        HandShakeTimeOutTimer.start();
        handShakeBuf = instanceData;

        mBTHandShakeSocketTread = new BTHandShakeSocketTread(mmSocket,mHandler);
        mBTHandShakeSocketTread.start();

        if(!isIncoming) {
            that.peerIdentifier = peerId;
            that.peerName = peerName;
            that.peerAddress = peerAddress;

            mBTHandShakeSocketTread.write(handShakeBuf.getBytes());
        }
    }

    public void tryCloseSocket() {
        if(mBTHandShakeSocketTread != null){
            mBTHandShakeSocketTread.CloseSocket();
        }
    }

    public void Stop() {
        printe_line("Stop");
        HandShakeTimeOutTimer.cancel();
        if(mBTHandShakeSocketTread != null){
            mBTHandShakeSocketTread = null;
        }
    }

    private void printe_line(String message){
           Log.d("BTHandShaker",  "BTHandShaker: " + message);
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mBTHandShakeSocketTread != null) {
                switch (msg.what) {
                    case BTHandShakeSocketTread.MESSAGE_WRITE: {
                        printe_line("MESSAGE_WRITE " + msg.arg1 + " bytes.");
                        if (isIncoming) {
                            callback.HandShakeOk(mmSocket, isIncoming,that.peerIdentifier,that.peerName,that.peerAddress);
                        }
                    }
                    break;
                    case BTHandShakeSocketTread.MESSAGE_READ: {
                        printe_line("got MESSAGE_READ " + msg.arg1 + " bytes.");
                        if (isIncoming) {
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

                                mBTHandShakeSocketTread.write(shakeBackBuf.getBytes());

                            }catch (Exception e){
                                callback.HandShakeFailed("Decryptin instance failed , :" + e.toString(), isIncoming,that.peerIdentifier,that.peerName,that.peerAddress);
                            }
                        } else {
                            callback.HandShakeOk(mmSocket, isIncoming,that.peerIdentifier,that.peerName,that.peerAddress);
                        }
                    }
                    break;
                    case BTHandShakeSocketTread.SOCKET_DISCONNEDTED: {

                        callback.HandShakeFailed("SOCKET_DISCONNEDTED", isIncoming,that.peerIdentifier,that.peerName,that.peerAddress);
                    }
                    break;
                }
            } else {
                printe_line("handleMessage called for NULL thread handler");
            }
        }
    };
}
