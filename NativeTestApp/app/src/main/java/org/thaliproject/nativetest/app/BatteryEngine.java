package org.thaliproject.nativetest.app;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.thaliproject.nativetest.app.model.Connection;
import org.thaliproject.nativetest.app.test.TestListener;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * Created by ekaterina.vasileuskaya on 01/11/16.
 */

public class BatteryEngine extends ConnectionEngine implements TestListener {

    private static final String TAG = BatteryEngine.class.getName();

    private boolean isStarted = false;
    /**
     * Constructor.
     *
     * @param context
     * @param activity
     */
    private TestListener mListener = null;

    public BatteryEngine(Context context, Activity activity, TestListener listener) {
        super(context, activity);
        this.mListener = listener;
    }


    @Override
    public void onTestStarting(String testName) {

    }

    @Override
    public void onTestFinished(String testName, float successRate, String results) {

    }

    @Override
    public void onTestStarting() {
        if (mListener != null) {
            mListener.onTestStarting();
            isStarted = true;
        }
    }

    @Override
    public void onTestFinished() {
        stop();
        if (mListener != null) {
            mListener.onTestFinished();
            isStarted = false;
        }
    }

    public void runTest() {
        PeerProperties peerProperties = null;
        if (mModel.getPeers().size() != 0) {
            peerProperties = mModel.getPeers().get(0);
            mDiscoveryManager.stop();
            onTestStarting();
            Connection connection = mModel.getConnectionToPeer(peerProperties, false);
            if (connection != null) {
                connection.disconnect();
            } else {
                connect(peerProperties);
            }
        } else {
            Toast.makeText(((MainActivity) mListener), "There is no discovered peers!!! Start Discovery first", Toast
                    .LENGTH_SHORT).show();
        }

    }

    @Override
    public void onDisconnected(String reason, Connection connection) {
        super.onDisconnected(reason, connection);
        Log.i(TAG, "onDisconnected  " + connection.getPeerProperties());
        if (isStarted)
            connect(connection.getPeerProperties());
    }

    @Override
    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
        super.onConnected(bluetoothSocket, isIncoming, peerProperties);
        Log.i(TAG, "created new Bluetooth socket to " + peerProperties);
        if (isStarted)
            startSendingData(peerProperties);
    }

    @Override
    public void onDataSent(float dataSentInMegaBytes, float transferSpeed, PeerProperties receivingPeer) {
        super.onDataSent(dataSentInMegaBytes, transferSpeed, receivingPeer);
        Log.i(TAG, "onDataSent " + receivingPeer);
        Connection connection = mModel.getConnectionToPeer(receivingPeer, false);
        if (isStarted)
            connection.disconnect();
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        super.onConnectionFailed(peerProperties, errorMessage);
        Log.i(TAG, "onConnectionFailed " + peerProperties);
        if (isStarted)
            connect(peerProperties);
    }

    public boolean isStarted() {
        return isStarted;
    }

}
