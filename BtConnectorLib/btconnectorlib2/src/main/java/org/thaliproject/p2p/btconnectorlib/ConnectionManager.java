/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;

import java.util.List;
import java.util.UUID;

/**
 *
 */
public class ConnectionManager implements
        BluetoothManager.BluetoothAdapterScanModeListener,
        BluetoothConnector.BluetoothConnectorListener {

    public enum ConnectionManagerState {
        NOT_INITIALIZED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED,
        INITIALIZED,
        RUNNING
    }

    public enum BluetoothState {
        BLUETOOTH_OK,
        BLUETOOTH_DISABLED,
        BLUETOOTH_NOT_SUPPORTED
    }

    public interface ConnectionManagerListener {
        /**
         *
         * @param bluetoothSocket
         * @param isIncoming
         * @param peerId
         * @param peerName
         * @param peerBluetoothAddress
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming,
                         String peerId, String peerName, String peerBluetoothAddress);

        /**
         *
         * @param peerId
         * @param peerName
         * @param peerBluetoothAddress
         */
        void onConnectionFailed(String peerId, String peerName, String peerBluetoothAddress);

        /**
         *
         * @param state
         */
        void onConnectionManagerStateChanged(ConnectionManagerState state);
    }

    private static final String TAG = ConnectionManager.class.getName();

    private final Context mContext;
    private final Handler mHandler;
    private final ConnectionManagerListener mListener;

    private BluetoothManager mBluetoothManager = null;
    private BluetoothConnector mBluetoothConnector = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_INITIALIZED;
    private String mMyIdentityString = "";
    private UUID mMyUuid = null;
    private String mMyName = null;

    /**
     * Constructor.
     * @param context
     * @param listener
     * @param myUuid
     * @param myName
     */
    public ConnectionManager(Context context, ConnectionManagerListener listener, UUID myUuid, String myName) {
        mContext = context;
        mListener = listener;
        mMyUuid = myUuid;
        mMyName = myName;

        mHandler = new Handler(mContext.getMainLooper());
        mBluetoothManager = new BluetoothManager(mContext, this);
    }

    /**
     *
     * @param peerId
     * @param peerName
     * @return
     */
    public synchronized BluetoothState initialize(String peerId, String peerName) {
        deinitialize();
        Log.i(TAG, "initialize: " + peerId + " " + peerName);
        BluetoothState bluetoothState = BluetoothState.BLUETOOTH_NOT_SUPPORTED;

        boolean isBluetoothPresent = mBluetoothManager.initialize();
        boolean isBluetoothEnabled = mBluetoothManager.isBluetoothEnabled();

        if (isBluetoothPresent) {
            bluetoothState = BluetoothState.BLUETOOTH_OK;

            if (!isBluetoothEnabled) {
                bluetoothState = BluetoothState.BLUETOOTH_DISABLED;
            }
        }

        if (bluetoothState == BluetoothState.BLUETOOTH_OK) {
            try {
                mMyIdentityString = CommonUtils.createIdentityString(
                        peerId, peerName, mBluetoothManager.getBluetoothAddress());
            } catch (JSONException e) {
                Log.e(TAG, "initialize: Failed create an identity string: " + e.getMessage(), e);
            }

            Log.i(TAG, "initialize: Bluetooth OK, my identity string is: " + mMyIdentityString);
            setState(ConnectionManagerState.INITIALIZED);
            start();
        } else if (bluetoothState == BluetoothState.BLUETOOTH_DISABLED) {
            Log.w(TAG, "initialize: Bluetooth is disabled, waiting for it to be enabled...");
            setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        } else if (bluetoothState == BluetoothState.BLUETOOTH_NOT_SUPPORTED) {
            Log.e(TAG, "initialize: Bluetooth not supported");
            deinitialize();
        }

        return bluetoothState;
    }

    /**
     *
     */
    public synchronized void deinitialize() {
        stop();
        mBluetoothManager.deinitialize();
        setState(ConnectionManagerState.NOT_INITIALIZED);
    }

    /**
     *
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean start() {
        if (mState == ConnectionManagerState.INITIALIZED) {
            if (mBluetoothConnector == null) {
                BluetoothAdapter bluetoothAdapter = mBluetoothManager.getBluetoothAdapter();

                mBluetoothConnector =
                        new BluetoothConnector(
                                mContext, this, bluetoothAdapter, mMyUuid, mMyName, mMyIdentityString);

                mBluetoothConnector.startListeningForIncomingConnections();
                Log.i(TAG, "start: OK");
            } else {
                Log.e(TAG, "start: This should not happen - Found an existing Bluetooth connector instance although the state was not 'RUNNING'!");
                stop();
            }

            setState(ConnectionManagerState.RUNNING);
        } else if (mState == ConnectionManagerState.RUNNING) {
            Log.w(TAG, "start: Already running, call stop() first in order to restart");
        } else {
            Log.e(TAG, "start: Cannot be started due to invalid state: " + mState.toString());
        }

        return (mState == ConnectionManagerState.RUNNING);
    }

    /**
     * Stops the Bluetooth connector. Calling this method does nothing, if the service is not
     * running.
     */
    public synchronized void stop() {
        if (mBluetoothConnector != null) {
            Log.i(TAG, "stopBluetoothListener");
            mBluetoothConnector.shutdown();
            mBluetoothConnector = null;
        }
    }

    /**
     *
     * @param deviceToConnectTo
     * @return
     */
    public synchronized boolean connect(PeerDevice deviceToConnectTo) {
        boolean success = false;

        if (deviceToConnectTo != null) {
            try {
                BluetoothDevice device = mBluetoothManager.getRemoteDevice(deviceToConnectTo.peerAddress);

                success = mBluetoothConnector.connect(
                        device, mMyUuid,
                        deviceToConnectTo.peerId, deviceToConnectTo.peerName,
                        deviceToConnectTo.peerAddress);
            } catch (NullPointerException e) {
                Log.e(TAG, "connect: Failed to connect to device \"" + deviceToConnectTo.peerName
                        + "\" with address \"" + deviceToConnectTo.peerAddress + "\": "
                        + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "connect: The given device is null!");
        }

        return success;
    }

    /**
     *
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

        if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth or Wi-Fi disabled, stopping...");
                stop();
                setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mBluetoothManager.isBluetoothEnabled()) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Both Bluetooth and Wi-Fi enabled, restarting...");
                setState(ConnectionManagerState.INITIALIZED);
                start();
            }
        }
    }

    /**
     *
     * @param bluetoothDeviceName The name of the Bluetooth device connecting to.
     * @param bluetoothDeviceAddress The address of the Bluetooth device connecting to.
     */
    @Override
    public void onConnecting(String bluetoothDeviceName, String bluetoothDeviceAddress) {
        Log.i(TAG, "onConnecting: " + bluetoothDeviceName + " " + bluetoothDeviceAddress);
    }

    /**
     *
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
            final String peerId = peerProperties.id;
            final String peerName = peerProperties.name;
            final String peerBluetoothAddress = peerProperties.bluetoothAddress;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnected(
                            tempBluetoothSocket, tempIsIncoming, peerId, peerName, peerBluetoothAddress);
                }
            });
        }
    }

    /**
     *
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
            if (peerProperties != null) {
                final String peerId = peerProperties.id;
                final String peerName = peerProperties.name;
                final String peerBluetoothAddress = peerProperties.bluetoothAddress;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionFailed(peerId, peerName, peerBluetoothAddress);
                    }
                });
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionFailed("", "", "");
                    }
                });
            }
        }
    }

    /**
     *
     * @param state The new state.
     */
    private synchronized void setState(ConnectionManagerState state) {
        if (mState != state) {
            Log.i(TAG, "setState: " + state.toString());
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
