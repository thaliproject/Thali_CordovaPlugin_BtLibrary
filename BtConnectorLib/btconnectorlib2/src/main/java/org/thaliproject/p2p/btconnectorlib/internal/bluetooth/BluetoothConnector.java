/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

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
 *
 */
public class BluetoothConnector
        implements BluetoothSocketListenerThread.Listener, BTConnectToThread.BtConnectToCallback {

    public enum State{
        ConnectionConnecting,
        ConnectionConnected
    }

    public interface BluetoothConnectorListener {
        void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress);
        void ConnectionFailed(String peerId, String peerName, String peerAddress);
        void ConnectionStateChanged(State newState);
    }

    private static final String TAG = BluetoothConnector.class.getName();
    private static final long CONNECTION_TIMEOUT_IN_MILLISECONDS = 60000;
    private static final long CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 10000;

    private final BluetoothConnector that = this;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothConnectorListener mListener;
    private final UUID mBluetoothUuid;
    private final String mBluetoothName;
    private final String mInstanceString;
    private final Handler mHandler;
    private final CountDownTimer mConnectionTimeoutTimer;
    private final Thread.UncaughtExceptionHandler mUncaughtExceptionHandler;
    private BluetoothSocketListenerThread mConnectorThread = null;
    private BTConnectToThread mBTConnectToThread = null;
    private boolean mIsStarted = false;

    /**
     * Constructor.
     * @param context
     * @param listener
     * @param bluetoothAdapter
     * @param bluetoothUuid
     * @param bluetoothName
     * @param instanceString
     */
    public BluetoothConnector(
            Context context, BluetoothConnectorListener listener, BluetoothAdapter bluetoothAdapter,
            UUID bluetoothUuid, String bluetoothName, String instanceString) {
        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mBluetoothUuid = bluetoothUuid;
        mBluetoothName = bluetoothName;
        mInstanceString = instanceString;
        mHandler = new Handler(context.getMainLooper());

        mConnectionTimeoutTimer = new CountDownTimer(
                CONNECTION_TIMEOUT_IN_MILLISECONDS, CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            @Override
            public void onFinish() {
                Log.i(TAG, "Connection timeout");
                BTConnectToThread temp = mBTConnectToThread;
                mBTConnectToThread = null;

                if (temp != null) {
                    // will stopListening & report failing to connect
                    temp.Cancel();
                }
            }
        };

        mUncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
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

    /**
     *
     */
    public synchronized void start() {
        if (!mIsStarted) {
            if (mConnectorThread != null) {
                mConnectorThread.stopListening();
                mConnectorThread = null;
            }

            Log.i("", "StartBluetooth listener");
            try {
                mConnectorThread = new BluetoothSocketListenerThread(that, mBluetoothAdapter, mBluetoothUuid, mBluetoothName, mInstanceString);
            } catch (IOException e) {
                e.printStackTrace();
                // in this point of time we can not accept any incoming connections, thus what should we do ?
                return;
            }

            mConnectorThread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
            mConnectorThread.start();
            mIsStarted = true;
        }
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
        tmp.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
        mConnectionTimeoutTimer.start();
        tmp.start();
        mBTConnectToThread = tmp;

        setState(State.ConnectionConnecting);
        Log.i("", "Connecting to " + device.getName() + ", at " + device.getAddress());

        return true;
    }

    public void Stop() {
        Log.i("", "deinitialize Bluetooth");
        mConnectionTimeoutTimer.cancel();

        BluetoothSocketListenerThread tmpList = mConnectorThread;
        mConnectorThread = null;
        if (tmpList != null) {
            tmpList.stopListening();
        }

        BTConnectToThread tmpConn = mBTConnectToThread;
        mBTConnectToThread = null;
        if (tmpConn != null) {
            tmpConn.Stop();
        }
    }

    @Override
    public void Connected(BluetoothSocket socket,String peerId,String peerName,String peerAddress) {
        mConnectionTimeoutTimer.cancel();
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
                    that.mListener.Connected(tmp, false,peerIdTmp,peerNaTmp,peerAdTmp);
                } else {
                    ConnectionFailed("Disconnected", peerIdTmp, peerNaTmp, peerAdTmp);
                }
            }
        });
    }

    @Override
    public void onIncomingConnection(BluetoothSocket socket, String peerId, String peerName, String peerAddress) {
        final BluetoothSocket tmp = socket;
        Log.i("HS", "Incoming connection Hand Shake finished for : " + peerName);

        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        start(); // re-initialize listening for incoming connections.

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tmp.isConnected()) {
                    setState(State.ConnectionConnected);
                    that.mListener.Connected(tmp, true,peerIdTmp,peerNaTmp,peerAdTmp);
                } else {
                    onSocketAcceptFailure("Disconnected");
                }
            }
        });
    }

    @Override
    public void ConnectionFailed(String reason,String peerId,String peerName,String peerAddress) {
        mConnectionTimeoutTimer.cancel();
        final String tmp = reason;
        final String peerIdTmp = peerId;
        final String peerNaTmp = peerName;
        final String peerAdTmp = peerAddress;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.i("CONNEC", "Error: " + tmp);

                that.mListener.ConnectionFailed(peerIdTmp,peerNaTmp,peerAdTmp);

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
    public void onSocketAcceptFailure(String reason) {
        final String tmp = reason;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.i("LISTEN", "Error: " + tmp);
                start();
            }
        });
    }

    private void setState(State newState) {
        final State tmpState = newState;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.mListener.ConnectionStateChanged(tmpState);
            }
        });
    }
}
