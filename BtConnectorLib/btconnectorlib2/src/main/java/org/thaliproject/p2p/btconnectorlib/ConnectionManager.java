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
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import java.util.List;
import java.util.UUID;

/**
 *
 */
public class ConnectionManager implements
        BluetoothManager.BluetoothAdapterScanModeListener,
        BluetoothConnector.BluetoothConnectorListener,
        WifiDirectManager.WifiStateListener,
        WifiPeerDiscoverer.WifiPeerDiscoveryListener {

    public enum ConnectionManagerState {
        NOT_INITIALIZED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED, // When e.g. Bluetooth is disabled
        INITIALIZED, // Ready to be started
        RUNNING
    }

    public interface ConnectionManagerListener {
        /**
         *
         * @param state
         */
        void onConnectionManagerStateChanged(ConnectionManagerState state);

        /**
         *
         * @param peerDevicePropertiesList
         */
        void onPeerListChanged(List<PeerDeviceProperties> peerDevicePropertiesList);

        /**
         *
         * @param peerDeviceProperties
         */
        void onPeerDiscovered(PeerDeviceProperties peerDeviceProperties);

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
    }

    private static final String TAG = ConnectionManager.class.getName();

    private final Context mContext;
    private final ConnectionManagerListener mListener;
    private final Handler mHandler;

    private BluetoothManager mBluetoothManager = null;
    private BluetoothConnector mBluetoothConnector = null;
    private WifiDirectManager mWifiDirectManager = null;
    private WifiPeerDiscoverer mWifiPeerDiscoverer = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_INITIALIZED;
    private String mMyIdentityString = "";
    private UUID mMyUuid = null;
    private String mMyName = null;
    private String mServiceType = null;

    /**
     * Constructor.
     * @param context
     * @param listener
     * @param myUuid
     * @param myName
     * @param serviceType
     */
    public ConnectionManager(
            Context context, ConnectionManagerListener listener,
            UUID myUuid, String myName, String serviceType) {
        mContext = context;
        mListener = listener;
        mMyUuid = myUuid;
        mMyName = myName;
        mServiceType = serviceType;

        mHandler = new Handler(mContext.getMainLooper());
        mBluetoothManager = new BluetoothManager(mContext, this);
        mWifiDirectManager = new WifiDirectManager(mContext, this);
    }

    /**
     * Initializes this instance and resolves the device connectivity state. This method also
     * creates our own identity string given that Bluetooth is OK and enabled. If initialization is
     * successful, we will start everything automatically.
     *
     * Note that even if this method returns true, it does not mean that initialization was
     * successful. Check ConnectionManagerState for the actual result.
     * @param myPeerId Our peer ID.
     * @param myPeerName Our peer name.
     * @return True, if can be initialized. False, if the necessary hardware support is missing.
     */
    public synchronized boolean initialize(String myPeerId, String myPeerName) {
        deinitialize();
        Log.i(TAG, "initialize: " + myPeerId + " " + myPeerName);

        boolean servicesSupported = (mBluetoothManager.initialize() && mWifiDirectManager.initialize());
        boolean isBluetoothEnabled = mBluetoothManager.isBluetoothEnabled();
        boolean isWifiEnabled = mWifiDirectManager.isWifiEnabled();

        if (servicesSupported) {
            if (isBluetoothEnabled) {
                try {
                    mMyIdentityString = CommonUtils.createIdentityString(
                            myPeerId, myPeerName, mBluetoothManager.getBluetoothAddress());
                } catch (JSONException e) {
                    Log.e(TAG, "initialize: Failed create an identity string: " + e.getMessage(), e);
                }

                Log.i(TAG, "initialize: Bluetooth OK, my identity string is: " + mMyIdentityString);

                if (isWifiEnabled) {
                    Log.i(TAG, "initialize: Success");
                    setState(ConnectionManagerState.INITIALIZED);
                    start();
                } else {
                    Log.w(TAG, "initialize: Wi-Fi disabled, waiting for it to be enabled...");
                    setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                }
            }
        } else {
            Log.e(TAG, "initialize: Bluetooth or Wi-Fi Direct not supported");
            deinitialize();
        }

        return servicesSupported;
    }

    /**
     * Stops everything, if running, and de-initializes this instance.
     */
    public synchronized void deinitialize() {
        stop();
        mBluetoothManager.deinitialize();
        mWifiDirectManager.deinitialize();;
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

            startWifiPeerDiscovery();
            setState(ConnectionManagerState.RUNNING);
        } else if (mState == ConnectionManagerState.RUNNING) {
            Log.w(TAG, "start: Already running, call stop() first in order to restart");
        } else {
            Log.e(TAG, "start: Cannot be started due to invalid state: " + mState.toString());
        }

        return (mState == ConnectionManagerState.RUNNING);
    }

    /**
     * Stops the Bluetooth connector and the Wi-Fi peer discovery.
     * Calling this method does nothing, if the services are not running.
     */
    public synchronized void stop() {
        Log.i(TAG, "stop");

        if (mBluetoothConnector != null) {
            mBluetoothConnector.shutdown();
            mBluetoothConnector = null;
        }

        stopWifiPeerDiscovery();
    }

    /**
     * Starts the Wi-Fi peer discovery.
     */
    private synchronized void startWifiPeerDiscovery() {
        if (mState == ConnectionManagerState.INITIALIZED || mState == ConnectionManagerState.RUNNING) {
            if (mWifiPeerDiscoverer == null) {
                WifiP2pManager p2pManager = mWifiDirectManager.getWifiP2pManager();
                WifiP2pManager.Channel channel = mWifiDirectManager.getWifiP2pChannel();

                if (p2pManager != null && channel != null) {
                    mWifiPeerDiscoverer =
                            new WifiPeerDiscoverer(
                                    mContext, channel, p2pManager, this, mServiceType, mMyIdentityString);

                    mWifiPeerDiscoverer.start();
                    Log.i(TAG, "startWifiPeerDiscovery: Started");
                }
            } else {
                Log.w(TAG, "startWifiPeerDiscovery: Already started");
            }
        } else {
            Log.e(TAG, "startWifiPeerDiscovery: Cannot start peer discovery due to invalid state: " + mState);
        }
    }

    /**
     * Stops the Wi-Fi peer discovery.
     */
    private synchronized void stopWifiPeerDiscovery() {
        if (mWifiPeerDiscoverer != null) {
            mWifiPeerDiscoverer.stop();
            mWifiPeerDiscoverer = null;
            Log.i(TAG, "stopWifiPeerDiscovery: Stopped");
        }
    }

    /**
     *
     * @param deviceToConnectTo
     * @return
     */
    public synchronized boolean connect(PeerDeviceProperties deviceToConnectTo) {
        boolean success = false;

        if (deviceToConnectTo != null) {
            try {
                BluetoothDevice device = mBluetoothManager.getRemoteDevice(deviceToConnectTo.peerBluetoothAddress);

                success = mBluetoothConnector.connect(
                        device, mMyUuid,
                        deviceToConnectTo.peerId, deviceToConnectTo.peerName,
                        deviceToConnectTo.peerBluetoothAddress);
            } catch (NullPointerException e) {
                Log.e(TAG, "connect: Failed to connect to device \"" + deviceToConnectTo.peerName
                        + "\" with address \"" + deviceToConnectTo.peerBluetoothAddress + "\": "
                        + e.getMessage(), e);
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
     *
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

        if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, stopping...");
                stop();
                setState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mBluetoothManager.isBluetoothEnabled()) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth enabled, restarting...");
                setState(ConnectionManagerState.INITIALIZED);
                start();
            }
        }
    }

    /**
     * Starts/stops Wi-Fi peer discovery depending on the given state.
     * @param state The new state.
     */
    @Override
    public void onWifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onWifiStateChanged: Bluetooth or Wi-Fi disabled, stopping Wi-Fi peer discovery...");
                stopWifiPeerDiscovery();

                // No need for state change, the Bluetooth is still active although we won't
                // discover new peers.
                //setConnectorState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState != ConnectionManagerState.NOT_INITIALIZED
                    && mWifiDirectManager.isWifiEnabled()
                    && mBluetoothManager.isBluetoothEnabled()) {

                if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                    Log.i(TAG, "onWifiStateChanged: Wi-Fi enabled, trying to start everything...");
                    setState(ConnectionManagerState.INITIALIZED);
                    start();
                } else if (mState == ConnectionManagerState.RUNNING) {
                    Log.i(TAG, "onWifiStateChanged: Wi-Fi enabled, trying to restart Wi-Fi peer discovery...");
                    startWifiPeerDiscovery();
                }
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
     * @param isStarted If true, the discovery was started. If false, it was stopped.
     */
    @Override
    public void onIsDiscoveryStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsDiscoveryStartedChanged: " + isStarted);
    }

    /**
     *
     * @param peerDeviceProperties The properties of the discovered peer.
     */
    @Override
    public void onPeerDiscovered(PeerDeviceProperties peerDeviceProperties) {
        if (mListener != null) {
            final ConnectionManager thisInstance = this;
            final PeerDeviceProperties tempPeerDeviceProperties = peerDeviceProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    thisInstance.mListener.onPeerDiscovered(tempPeerDeviceProperties);
                }
            });
        }
    }

    /**
     *
     * @param peerDevicePropertiesList The new list of available peers.
     */
    @Override
    public void onPeerListChanged(List<PeerDeviceProperties> peerDevicePropertiesList) {
        if (mListener != null) {
            final ConnectionManager thisInstance = this;
            final List<PeerDeviceProperties> tempPeerDevicePropertiesList = peerDevicePropertiesList;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    thisInstance.mListener.onPeerListChanged(tempPeerDevicePropertiesList);
                }
            });
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
