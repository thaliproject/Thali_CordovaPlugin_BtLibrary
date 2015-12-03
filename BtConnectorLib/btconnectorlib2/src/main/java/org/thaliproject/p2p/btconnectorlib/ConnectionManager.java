/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import java.util.UUID;

/**
 * The main interface of this library for managing Bluetooth connections.
 */
public class ConnectionManager
        extends AbstractBluetoothConnectivityAgent
        implements BluetoothManager.BluetoothManagerListener, BluetoothConnector.BluetoothConnectorListener {

    public enum ConnectionManagerState {
        NOT_STARTED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED, // When Bluetooth is disabled and waiting for it to be enabled to start
        RUNNING
    }

    public interface ConnectionManagerListener {
        /**
         * Called when the state of this instance is changed.
         * @param state The new state.
         */
        void onConnectionManagerStateChanged(ConnectionManagerState state);

        /**
         * Called when successfully connected to a peer.
         * Note that the ownership of the bluetooth socket is transferred to the listener.
         * @param bluetoothSocket The Bluetooth socket associated with the peer.
         * @param isIncoming True, if the connection was incoming. False, if outgoing.
         * @param peerProperties The peer properties including the peer ID, name and the Bluetooth address.
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties);

        /**
         * Called in case of a failure to connect to a peer.
         * @param peerProperties The peer properties including the peer ID, name and the Bluetooth address.
         */
        void onConnectionFailed(PeerProperties peerProperties);
    }

    private static final String TAG = ConnectionManager.class.getName();
    private final Context mContext;
    private final ConnectionManagerListener mListener;
    private final Handler mHandler;
    private BluetoothManager mBluetoothManager = null;
    private BluetoothConnector mBluetoothConnector = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_STARTED;
    private UUID mMyUuid = null;
    private String mMyName = null;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener The listener.
     * @param myUuid Our UUID.
     * @param myName Our name.
     */
    public ConnectionManager(
            Context context, ConnectionManagerListener listener,
            UUID myUuid, String myName) {
        super(context);

        mContext = context;
        mListener = listener;
        mMyUuid = myUuid;
        mMyName = myName;

        mHandler = new Handler(mContext.getMainLooper());
        mBluetoothManager = BluetoothManager.getInstance(mContext);
    }

    /**
     * Initializes the components and starts the listener for incoming connections.
     * @return True, if started successfully or was already running. False otherwise.
     */
    public boolean start() {
        switch (mState) {
            case NOT_STARTED:
                if (mBluetoothManager.bind(this)) {
                    if (mBluetoothManager.isBluetoothEnabled()) {
                        if (verifyIdentityString()) {
                            BluetoothAdapter bluetoothAdapter = mBluetoothManager.getBluetoothAdapter();

                            mBluetoothConnector = new BluetoothConnector(
                                    mContext, this, bluetoothAdapter, mMyUuid, mMyName, mMyIdentityString);

                            mBluetoothConnector.startListeningForIncomingConnections();
                            setState(ConnectionManagerState.RUNNING);
                            Log.i(TAG, "start: OK");
                        } else {
                            Log.e(TAG, "start: The identity string is invalid: " + mMyIdentityString);
                        }
                    } else {
                        Log.i(TAG, "start: Bluetooth disabled, waiting for it to be enabled...");
                        setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    }
                } else {
                    Log.e(TAG, "start: Failed to start, this may indicate that Bluetooth is not supported on this device");
                }

                break;

            case WAITING_FOR_SERVICES_TO_BE_ENABLED:
                Log.w(TAG, "start: Still waiting for Bluetooth to be enabled...");
                break;

            case RUNNING:
                Log.d(TAG, "start: Already running, call stop() first in order to restart");
                break;
        }

        return (mState == ConnectionManagerState.RUNNING);
    }

    /**
     * Shuts down the Bluetooth connectivity and releases the Bluetooth manager instance.
     * Calling this method does nothing, if not started.
     */
    public void stop() {
        if (mState != ConnectionManagerState.NOT_STARTED) {
            Log.i(TAG, "stop: Stopping Bluetooth...");
        }

        shutdownAndDisposeBluetoothConnector();
        mBluetoothManager.release(this);
        setState(ConnectionManagerState.NOT_STARTED);
    }

    /**
     * Tries to connect to the given device.
     * Note that even when this method returns true, it does not yet indicate a successful
     * connection, but merely that the connection process was started successfully.
     * ConnectionManagerListener.onConnected callback gets called after a successful connection.
     * @param peerToConnectTo The peer to connect to.
     * @return True, if the connection process was started successfully.
     */
    public synchronized boolean connect(PeerProperties peerToConnectTo) {
        boolean success = false;

        if (peerToConnectTo != null) {
            Log.i(TAG, "connect: " + peerToConnectTo.toString());

            try {
                BluetoothDevice device = mBluetoothManager.getRemoteDevice(peerToConnectTo.getBluetoothAddress());
                success = mBluetoothConnector.connect(device, peerToConnectTo, mMyUuid);
            } catch (NullPointerException e) {
                Log.e(TAG, "connect: Failed to start connecting to peer "
                        + peerToConnectTo.toString() + ": " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "connect: The given device is null!");
        }

        return success;
    }

    /**
     * @return The current state of this instance.
     */
    public ConnectionManagerState getState() {
        return mState;
    }

    /**
     * Restarts/pauses the connectivity processes based on the given mode.
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

        if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, pausing...");
                shutdownAndDisposeBluetoothConnector();
                setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mBluetoothManager.isBluetoothEnabled()) {
                Log.i(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth enabled, restarting...");
                start();
            }
        }
    }

    /**
     * Does nothing but logs the event.
     * @param bluetoothDeviceName The mName of the Bluetooth device connecting to.
     * @param bluetoothDeviceAddress The address of the Bluetooth device connecting to.
     */
    @Override
    public void onConnecting(String bluetoothDeviceName, String bluetoothDeviceAddress) {
        Log.i(TAG, "onConnecting: " + bluetoothDeviceName + " " + bluetoothDeviceAddress);
    }

    /**
     * Notifies the listener about a successful connection.
     * @param bluetoothSocket The Bluetooth socket.
     * @param isIncoming True, if the connection was incoming. False, if it was outgoing.
     * @param peerProperties The properties of the peer connected to.
     */
    @Override
    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
        Log.i(TAG, "onConnected: " + peerProperties.toString());

        if (mListener != null) {
            final BluetoothSocket tempBluetoothSocket = bluetoothSocket;
            final boolean tempIsIncoming = isIncoming;
            final PeerProperties tempPeerProperties = peerProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnected(tempBluetoothSocket, tempIsIncoming, tempPeerProperties);
                }
            });
        }
    }

    /**
     * Notifies the listener about a failed connection.
     * @param reason The reason of the failure.
     * @param peerProperties The properties of the peer. Note: Can be null!
     */
    @Override
    public void onConnectionFailed(String reason, PeerProperties peerProperties) {
        if (peerProperties != null) {
            Log.w(TAG, "onConnectionFailed: " + reason + " " + peerProperties.toString());
        } else {
            Log.w(TAG, "onConnectionFailed: " + reason);
        }

        if (mListener != null) {
            final PeerProperties tempPeerProperties = peerProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionFailed(tempPeerProperties);
                }
            });
        }
    }

    /**
     * Shuts down and disposes the BluetoothConnector instance.
     */
    private synchronized void shutdownAndDisposeBluetoothConnector() {
        if (mBluetoothConnector != null) {
            mBluetoothConnector.shutdown();
            mBluetoothConnector = null;
        }
    }

    /**
     * Sets the state of this instance and notifies the listener.
     * @param state The new state.
     */
    private synchronized void setState(ConnectionManagerState state) {
        if (mState != state) {
            Log.d(TAG, "setState: " + state.toString());
            mState = state;

            if (mListener != null) {
                final ConnectionManagerState tempState = mState;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionManagerStateChanged(tempState);
                    }
                });
            }
        }
    }
}
