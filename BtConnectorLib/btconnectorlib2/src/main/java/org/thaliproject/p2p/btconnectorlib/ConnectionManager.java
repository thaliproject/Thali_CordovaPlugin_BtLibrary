/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import java.util.UUID;

/**
 * The main interface of this library for managing Bluetooth connections.
 */
public class ConnectionManager
        extends AbstractBluetoothConnectivityAgent
        implements
            BluetoothConnector.BluetoothConnectorListener,
            ConnectionManagerSettings.Listener {

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
         * @param peerProperties The properties of the peer we're connected to.
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties);

        /**
         * Notifies the listener about this failed connection attempt.
         * @param peerProperties The properties of the peer we we're trying to connect to.
         *                       Note: This can be null.
         */
        void onConnectionTimeout(PeerProperties peerProperties);

        /**
         * Called in case of a failure to connect to a peer.
         * @param peerProperties The properties of the peer we we're trying to connect to.
         *                       Note: This can be null.
         * @param errorMessage The error message. Note: This can be null.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage);
    }

    private static final String TAG = ConnectionManager.class.getName();
    private final Context mContext;
    private final ConnectionManagerListener mListener;
    private final Handler mHandler;
    private BluetoothConnector mBluetoothConnector = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_STARTED;
    private UUID mMyUuid = null;
    private String mMyName = null;
    private ConnectionManagerSettings mSettings = null;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener The listener.
     * @param myUuid Our (service record) UUID. Note that his has to match the one of the peers we
     *               are trying to connect to, otherwise any connection attempt will fail.
     * @param myName Our name.
     */
    public ConnectionManager(
            Context context, ConnectionManagerListener listener,
            UUID myUuid, String myName) {
        super(context); // Gets the BluetoothManager instance

        mContext = context;
        mListener = listener;
        mMyUuid = myUuid;
        mMyName = myName;

        mSettings = ConnectionManagerSettings.getInstance(mContext);
        mSettings.load();
        mSettings.addListener(this);

        mHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * @return The current state of this instance.
     */
    public ConnectionManagerState getState() {
        return mState;
    }

    /**
     * Initializes the components and starts the listener for incoming connections.
     * @return True, if started successfully or was already running. False otherwise.
     */
    public boolean start() {
        Log.i(TAG, "start");

        switch (mState) {
            case NOT_STARTED:
            case WAITING_FOR_SERVICES_TO_BE_ENABLED:
                if (mBluetoothManager.bind(this)) {
                    if (mBluetoothManager.isBluetoothEnabled()) {
                        if (verifyIdentityString()) {
                            BluetoothAdapter bluetoothAdapter = mBluetoothManager.getBluetoothAdapter();

                            mBluetoothConnector = new BluetoothConnector(
                                    mContext, this, bluetoothAdapter, mMyUuid, mMyName, mMyIdentityString);

                            mBluetoothConnector.setConnectionTimeout(mSettings.getConnectionTimeout());
                            startListeningForIncomingConnections();
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

            case RUNNING:
                Log.d(TAG, "start: Already running, call stop() first in order to restart");
                break;

            default:
                Log.e(TAG, "start: Unrecognized state");
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
     * Starts listening for incoming connections. If already listening, this method does nothing.
     * @return True, if started or already listening. False, if failed to start.
     */
    public boolean startListeningForIncomingConnections() {
        boolean started = false;

        if (mBluetoothConnector != null) {
            Log.d(TAG, "startListeningForIncomingConnections");
            started = mBluetoothConnector.startListeningForIncomingConnections();
        } else {
            Log.e(TAG, "startListeningForIncomingConnections: No connector instance, did you forget to call start()");
        }

        return started;
    }

    /**
     * Stops listening for incoming connections. This can be used to block new incoming connections,
     * if maximum number of connections (from application's point of view) is reached.
     */
    public void stopListeningForIncomingConnections() {
        if (mBluetoothConnector != null) {
            Log.d(TAG, "stopListeningForIncomingConnections");
            mBluetoothConnector.stopListeningForIncomingConnections();
        } else {
            Log.e(TAG, "stopListeningForIncomingConnections: No connector instance, did you forget to call start()");
        }
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
                BluetoothDevice device = mBluetoothManager.getRemoteDevice(peerToConnectTo.getBluetoothMacAddress());
                success = mBluetoothConnector.connect(device, peerToConnectTo);
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
     * Cancels an ongoing connection attempt to the peer with the given properties.
     * @param peerConnectingTo The properties of the peer whose connection attempt to cancel.
     * @return True, if cancelled successfully. False otherwise.
     */
    public synchronized boolean cancelConnectionAttempt(PeerProperties peerConnectingTo) {
        boolean wasCancelled = false;

        if (mBluetoothConnector != null) {
            wasCancelled = mBluetoothConnector.cancelConnectionAttempt(peerConnectingTo);
        }

        return wasCancelled;
    }

    @Override
    public void dispose() {
        Log.i(TAG, "dispose");
        super.dispose();

        if (mState != ConnectionManagerState.NOT_STARTED) {
            stop();
        }

        mSettings.removeListener(this);
    }

    /**
     * Applies the changed settings, if the connector instance already exists.
     */
    @Override
    public void onConnectionManagerSettingsChanged() {
        if (mBluetoothConnector != null) {
            mBluetoothConnector.setConnectionTimeout(mSettings.getConnectionTimeout());
            mBluetoothConnector.setInsecureRfcommSocketPort(mSettings.getInsecureRfcommSocketPortNumber());
            mBluetoothConnector.setMaxNumberOfOutgoingConnectionAttemptRetries(mSettings.getMaxNumberOfConnectionAttemptRetries());
        }
    }

    /**
     * Applies the changed setting, if the connector instance already exists.
     * The reason why this is a separate callback, is that when changing the setting we have to
     * restart the server thread.
     * @param hanshakeRequired True, if a handshake protocol should be applied when establishing a connection.
     */
    @Override
    public void onHandshakeRequiredSettingChanged(boolean hanshakeRequired) {
        if (mBluetoothConnector != null) {
            mBluetoothConnector.setHandshakeRequired(mSettings.getHandshakeRequired());
        }
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
     * @param bluetoothDeviceName The name of the Bluetooth device connecting to.
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
     * Notifies the listener about this failed connection attempt.
     * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
     */
    @Override
    public void onConnectionTimeout(PeerProperties peerProperties) {
        if (peerProperties != null) {
            Log.w(TAG, "onConnectionTimeout: " + peerProperties.toString());
        } else {
            Log.w(TAG, "onConnectionTimeout");
        }

        if (mListener != null) {
            final PeerProperties tempPeerProperties = peerProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionTimeout(tempPeerProperties);
                }
            });
        }
    }

    /**
     * Notifies the listener about this failed connection attempt.
     * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
     * @param errorMessage The error message. Note: Can be null.
     */
    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        if (peerProperties != null) {
            Log.w(TAG, "onConnectionFailed: " + errorMessage + " " + peerProperties.toString());
        } else {
            Log.w(TAG, "onConnectionFailed: " + errorMessage);
        }

        if (mListener != null) {
            final PeerProperties tempPeerProperties = peerProperties;
            final String tempErrorMessage = errorMessage;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionFailed(tempPeerProperties, tempErrorMessage);
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
