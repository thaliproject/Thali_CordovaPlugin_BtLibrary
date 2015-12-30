/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
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
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import java.util.UUID;

/**
 * The main interface of this library for managing Bluetooth connections.
 */
public class ConnectionManager
        extends AbstractBluetoothConnectivityAgent
        implements BluetoothConnector.BluetoothConnectorListener {

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
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = BluetoothConnector.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    private final Context mContext;
    private final ConnectionManagerListener mListener;
    private final Handler mHandler;
    private BluetoothConnector mBluetoothConnector = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_STARTED;
    private UUID mMyUuid = null;
    private String mMyName = null;
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;

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

        mHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * Initializes the components and starts the listener for incoming connections.
     * @param myPeerId Our peer ID (used for the identity).
     * @param myPeerName Our peer name (used for the identity).
     * @return True, if started successfully or was already running. False otherwise.
     */
    public boolean start(String myPeerId, String myPeerName) {
        Log.i(TAG, "start: Peer ID: " + myPeerId + ", peer name: " + myPeerName);
        mMyPeerId = myPeerId;
        mMyPeerName = myPeerName;

        switch (mState) {
            case NOT_STARTED:
            case WAITING_FOR_SERVICES_TO_BE_ENABLED:
                if (mBluetoothManager.bind(this)) {
                    if (mBluetoothManager.isBluetoothEnabled()) {
                        if (verifyIdentityString()) {
                            BluetoothAdapter bluetoothAdapter = mBluetoothManager.getBluetoothAdapter();

                            mBluetoothConnector = new BluetoothConnector(
                                    mContext, this, bluetoothAdapter, mMyUuid, mMyName, mMyIdentityString);

                            mBluetoothConnector.setConnectionTimeout(mConnectionTimeoutInMilliseconds);
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
        }

        return (mState == ConnectionManagerState.RUNNING);
    }

    /**
     * Initializes the components and starts the listener for incoming connections. This method
     * uses the Bluetooth address to set the value of the peer ID.
     * @param myPeerName Our peer name (used for the identity).
     * @return True, if started successfully or was already running. False otherwise.
     */
    public boolean start(String myPeerName) {
        return start(mBluetoothManager.getBluetoothAddress(), myPeerName);
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
     * Sets the connection timeout. If the given value is negative or zero, no timeout is set.
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        mConnectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
    }

    /**
     * Returns the port to be used by the insecure RFCOMM socket when connecting.
     * -1: The system decides (the default method).
     * 0: Using a rotating port number.
     * 1-30: Using a custom port.
     * @return The port to be used by the insecure RFCOMM socket when connecting.
     */
    public int getInsecureRfcommSocketPort() {
        int port = DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;

        if (mBluetoothConnector != null) {
            port = mBluetoothConnector.getInsecureRfcommSocketPort();
        }

        return port;
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket when connecting.
     * @param insecureRfcommSocketPort The port to use.
     *                                 Use -1 for to let the system decide (the default method).
     *                                 Use 0 for rotating port number.
     *                                 Values 1-30 are valid custom ports (1 is recommended).
     * @return True, if the port was set successfully. False otherwise.
     */
    public boolean setInsecureRfcommSocketPort(int insecureRfcommSocketPort) {
        boolean wasSet = false;

        if (mBluetoothConnector != null) {
            Log.i(TAG, "setInsecureRfcommSocketPort: Will use port " + insecureRfcommSocketPort + " when trying to connect");
            mBluetoothConnector.setInsecureRfcommSocketPort(insecureRfcommSocketPort);
            wasSet = true;
        } else {
            Log.e(TAG, "setInsecureRfcommSocketPort: Cannot set port, because not started yet");
        }

        return wasSet;
    }

    /**
     * Sets the maximum number of (outgoing) socket connection  attempt retries (0 means only one attempt).
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries for outgoing connections.
     * @return True, if was set successfully. False otherwise.
     */
    public boolean setMaxNumberOfOutgoingConnectionAttemptRetries(int maxNumberOfRetries) {
        boolean wasSet = false;

        if (mBluetoothConnector != null) {
            Log.i(TAG, "setMaxNumberOfOutgoingConnectionAttemptRetries: " + maxNumberOfRetries);
            mBluetoothConnector.setMaxNumberOfOutgoingConnectionAttemptRetries(maxNumberOfRetries);
            wasSet = true;
        } else {
            Log.e(TAG, "setMaxNumberOfOutgoingConnectionAttemptRetries: Cannot set port, because not started yet");
        }

        return wasSet;
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
                start(mMyPeerId, mMyPeerName);
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
