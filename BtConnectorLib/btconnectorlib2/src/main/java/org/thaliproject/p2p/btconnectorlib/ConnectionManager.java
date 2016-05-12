/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

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
         *
         * @param bluetoothSocket The Bluetooth socket associated with the peer.
         * @param isIncoming True, if the connection was incoming. False, if outgoing.
         * @param peerProperties The properties of the peer we're connected to.
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties);

        /**
         * Notifies the listener about this failed connection attempt.
         *
         * @param peerProperties The properties of the peer we we're trying to connect to.
         *                       Note: This can be null.
         */
        void onConnectionTimeout(PeerProperties peerProperties);

        /**
         * Called in case of a failure to connect to a peer.
         *
         * @param peerProperties The properties of the peer we we're trying to connect to.
         *                       Note: This can be null.
         * @param errorMessage The error message. Note: This can be null.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage);
    }

    private static final String TAG = ConnectionManager.class.getName();
    private final ConnectionManagerListener mListener;
    private final Handler mHandler;
    private final BluetoothConnector mBluetoothConnector;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_STARTED;
    private UUID mMyUuid = null;
    private String mMyName = null;
    private ConnectionManagerSettings mSettings = null;

    /**
     * Constructor.
     *
     * @param context The application context.
     * @param listener The listener.
     * @param myUuid Our (service record) UUID. Note that his has to match the one of the peers we
     *               are trying to connect to, otherwise any connection attempt will fail.
     * @param myName Our name.
     */
    public ConnectionManager(
            Context context, ConnectionManagerListener listener,
            UUID myUuid, String myName) {
        this(context, listener, myUuid, myName, BluetoothManager.getInstance(context),
                PreferenceManager.getDefaultSharedPreferences(context));
    }

    /**
     * Constructor used in unit tests. It allows to provide initialized
     * bluetooth manager and shared preferences.
     *
     * @param context The application context.
     * @param listener The listener.
     * @param myUuid Our (service record) UUID. Note that his has to match the one of the peers we
     *               are trying to connect to, otherwise any connection attempt will fail.
     * @param myName Our name.
     * @param bluetoothManager The bluetooth manager.
     * @param preferences The shared preferences.
     */
    public ConnectionManager(
            Context context, ConnectionManagerListener listener,
            UUID myUuid, String myName, BluetoothManager bluetoothManager,
            SharedPreferences preferences) {
        super(context, bluetoothManager); // Gets the BluetoothManager instance

        mListener = listener;
        mMyUuid = myUuid;
        mMyName = myName;

        mSettings = ConnectionManagerSettings.getInstance(mContext, preferences);
        mSettings.load();
        mSettings.addListener(this);

        mHandler = new Handler(mContext.getMainLooper());

        verifyIdentityString(preferences); // Creates the identity string

        mBluetoothConnector = new BluetoothConnector(
                mContext, this, mBluetoothManager.getBluetoothAdapter(),
                mMyUuid, mMyName, mMyIdentityString);
        mBluetoothConnector.setConnectionTimeout(mSettings.getConnectionTimeout());
    }

    /**
     * @return The current state of this instance.
     */
    public ConnectionManagerState getState() {
        return mState;
    }

    /**
     * Initializes the components and starts the listener for incoming connections.
     * If already listening, this method does nothing.
     *
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean startListeningForIncomingConnections() {
        Log.d(TAG, "startListeningForIncomingConnections");

        switch (mState) {
            case NOT_STARTED:
            case WAITING_FOR_SERVICES_TO_BE_ENABLED:
                if (mBluetoothManager.bind(this)) {
                    if (mBluetoothManager.isBluetoothEnabled()) {
                        mBluetoothConnector.setConnectionTimeout(mSettings.getConnectionTimeout());

                        if (mBluetoothConnector.startListeningForIncomingConnections()) {
                            Log.i(TAG, "startListeningForIncomingConnections: OK");
                        } else {
                            Log.e(TAG, "startListeningForIncomingConnections: Failed to start");
                        }
                    } else {
                        Log.i(TAG, "startListeningForIncomingConnections: Bluetooth disabled, waiting for it to be enabled...");
                        setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    }
                } else {
                    Log.e(TAG, "startListeningForIncomingConnections: Failed to start, this may indicate that Bluetooth is not supported on this device");
                }

                break;

            case RUNNING:
                Log.d(TAG, "startListeningForIncomingConnections: Already running, call stopListeningForIncomingConnections() first in order to restart");
                break;

            default:
                Log.e(TAG, "startListeningForIncomingConnections: Unrecognized state");
                break;
        }

        return (mState == ConnectionManagerState.RUNNING);
    }

    /**
     * Stops listening for incoming connections. This can be used to block new incoming connections,
     * if maximum number of connections (from application's point of view) is reached.
     */
    public synchronized void stopListeningForIncomingConnections() {
        if (mState != ConnectionManagerState.NOT_STARTED) {
            Log.i(TAG, "stopListeningForIncomingConnections");
        }

        mBluetoothConnector.stopListeningForIncomingConnections();
        mBluetoothManager.release(this);

        if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
            // We won't get the onIsServerStartedChanged event, when we are waiting for Bluetooth to
            // be enabled. Thus, we need to change the state here.
            setState(ConnectionManagerState.NOT_STARTED);
        }
    }

    /**
     * Tries to connect to the given device.
     * Note that even when this method returns true, it does not yet indicate a successful
     * connection, but merely that the connection process was started successfully.
     * ConnectionManagerListener.onConnected callback gets called after a successful connection.
     *
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
     *
     * @param peerConnectingTo The properties of the peer whose connection attempt to cancel.
     * @return True, if cancelled successfully. False otherwise.
     */
    public synchronized boolean cancelConnectionAttempt(PeerProperties peerConnectingTo) {
        Log.i(TAG, "cancelConnectionAttempt: " + peerConnectingTo);
        return mBluetoothConnector.cancelConnectionAttempt(peerConnectingTo);
    }

    /**
     * Cancels all connection attempts.
     */
    public synchronized void cancelAllConnectionAttempts() {
        Log.i(TAG, "cancelAllConnectionAttempts");
        mBluetoothConnector.cancelAllConnectionAttempts();
    }

    /**
     * When the peer name is changed, the identity string is recreated. We need to provide the
     * updated string to the Bluetooth connector instance.
     *
     * @param myPeerName Our peer name.
     */
    @Override
    public void setPeerName(String myPeerName) {
        super.setPeerName(myPeerName);
        mBluetoothConnector.setIdentityString(mMyIdentityString);
    }

    @Override
    public void dispose() {
        Log.i(TAG, "dispose");
        super.dispose();
        stopListeningForIncomingConnections();
        mBluetoothConnector.shutdown();
        mSettings.removeListener(this);
    }

    /**
     * Applies the changed settings.
     */
    @Override
    public void onConnectionManagerSettingsChanged() {
        mBluetoothConnector.setConnectionTimeout(mSettings.getConnectionTimeout());
        mBluetoothConnector.setInsecureRfcommSocketPort(mSettings.getInsecureRfcommSocketPortNumber());
        mBluetoothConnector.setMaxNumberOfOutgoingConnectionAttemptRetries(mSettings.getMaxNumberOfConnectionAttemptRetries());
    }

    /**
     * Applies the changed setting.
     * The reason why this is a separate callback, is that when changing the setting we have to
     * restart the server thread.
     *
     * @param handshakeRequired True, if a handshake protocol should be applied when establishing a connection.
     */
    @Override
    public void onHandshakeRequiredSettingChanged(boolean handshakeRequired) {
        mBluetoothConnector.setHandshakeRequired(mSettings.getHandshakeRequired());
    }

    /**
     * Restarts/pauses the incoming connection listener based on the given mode.
     *
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

        if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, pausing...");
                mBluetoothConnector.stopListeningForIncomingConnections();
                setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mBluetoothManager.isBluetoothEnabled()) {
                Log.i(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth enabled, restarting...");
                startListeningForIncomingConnections();
            }
        }
    }

    /**
     * Restarts/pauses the incoming connection listener based on the given state.
     *
     * @param state The new state.
     */
    @Override
    public void onBluetoothAdapterStateChanged(int state) {
        Log.i(TAG, "onBluetoothAdapterStateChanged: State changed to " + state);

        if (state == BluetoothAdapter.STATE_OFF) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterStateChanged: Bluetooth disabled, pausing...");
                mBluetoothConnector.stopListeningForIncomingConnections();
                setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mBluetoothManager.isBluetoothEnabled()) {
                Log.i(TAG, "onBluetoothAdapterStateChanged: Bluetooth enabled, restarting...");
                startListeningForIncomingConnections();
            }
        }
    }

    /**
     * Sets the state based on the given argument.
     *
     * @param isStarted If true, the server thread is started. If false, the server thread is stopped.
     */
    @Override
    public void onIsServerStartedChanged(boolean isStarted) {
        Log.d(TAG, "onIsServerStartedChanged: " + isStarted);

        if (isStarted) {
            setState(ConnectionManagerState.RUNNING);
        } else {
            // We don't want to change the state, if we are waiting for Bluetooth to be enabled
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                setState(ConnectionManagerState.NOT_STARTED);
            }
        }
    }

    /**
     * Does nothing but logs the event.
     *
     * @param bluetoothDeviceName The name of the Bluetooth device connecting to.
     * @param bluetoothDeviceAddress The address of the Bluetooth device connecting to.
     */
    @Override
    public void onConnecting(String bluetoothDeviceName, String bluetoothDeviceAddress) {
        Log.i(TAG, "onConnecting: " + bluetoothDeviceName + " " + bluetoothDeviceAddress);
    }

    /**
     * Notifies the listener about a successful connection.
     *
     * @param bluetoothSocket The Bluetooth socket.
     * @param isIncoming True, if the connection was incoming. False, if it was outgoing.
     * @param peerProperties The properties of the peer connected to.
     */
    @Override
    public void onConnected(
            final BluetoothSocket bluetoothSocket, final boolean isIncoming,
            final PeerProperties peerProperties) {
        Log.i(TAG, "onConnected: " + peerProperties);

        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnected(bluetoothSocket, isIncoming, peerProperties);
                }
            });
        }
    }

    /**
     * Notifies the listener about this failed connection attempt.
     *
     * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
     */
    @Override
    public void onConnectionTimeout(final PeerProperties peerProperties) {
        if (peerProperties != null) {
            Log.e(TAG, "onConnectionTimeout: Connection attempt with peer " + peerProperties + " timed out");
        } else {
            Log.e(TAG, "onConnectionTimeout");
        }

        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionTimeout(peerProperties);
                }
            });
        }
    }

    /**
     * Notifies the listener about this failed connection attempt.
     *
     * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
     * @param errorMessage The error message. Note: Can be null.
     */
    @Override
    public void onConnectionFailed(final PeerProperties peerProperties, final String errorMessage) {
        if (peerProperties != null) {
            Log.w(TAG, "onConnectionFailed: Failed to connect to peer " + peerProperties + ": " + errorMessage);
        } else {
            Log.w(TAG, "onConnectionFailed: " + errorMessage);
        }

        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionFailed(peerProperties, errorMessage);
                }
            });
        }
    }

    /**
     * Sets the state of this instance and notifies the listener.
     *
     * @param state The new state.
     */
    private synchronized void setState(final ConnectionManagerState state) {
        if (mState != state) {
            Log.d(TAG, "setState: " + mState + " -> " + state);
            mState = state;

            if (mListener != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionManagerStateChanged(state);
                    }
                });
            }
        }
    }
}
