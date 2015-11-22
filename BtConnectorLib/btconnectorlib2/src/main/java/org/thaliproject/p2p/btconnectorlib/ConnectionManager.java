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
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 *
 */
public class ConnectionManager implements
        BluetoothManager.BluetoothAdapterScanModeListener,
        WifiDirectManager.WifiStateListener,
        WifiPeerDiscoverer.WifiPeerDiscoveryListener,
        BluetoothConnector.BluetoothConnectorListener {

    public enum ConnectionManagerState {
        NOT_INITIALIZED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED,
        INITIALIZED,
        IDLE,
        FINDING_PEERS,
        FINDING_SERVICES,
        CONNECTING,
        CONNECTED
    }

    public enum DeviceConnectivityState {
        WIFI_OK,
        WIFI_DISABLED,
        WIFI_NOT_PRESENT,
        BLUETOOTH_OK,
        BLUETOOTH_DISABLED,
        BLUETOOTH_NOT_PRESENT
    }

    public interface ConnectorListener {
        void connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress);
        void connectionFailed(String peerId, String peerName, String peerAddress);
        void connectorStateChanged(ConnectionManagerState newState);
    }

    public interface ConnectSelector {
        PeerDevice CurrentPeersList(List<PeerDevice> available);
        void PeerDiscovered(PeerDevice service);
    }

    private static final String TAG = ConnectionManager.class.getName();

    private final Context mContext;
    private final Handler mHandler;
    private final ConnectorListener mListener;
    private final ConnectSelector mConnectSelector;

    private WifiDirectManager mWifiManager = null;
    private BluetoothManager mBluetoothManager = null;
    private WifiPeerDiscoverer mPeerDiscoverer = null;
    private BluetoothConnector mBTConnector_BtConnection = null;
    private ConnectionManagerState mState = ConnectionManagerState.NOT_INITIALIZED;
    private String mInstanceString = "";
    private UUID mMyUuid = null;
    private String mMyServiceType = null;
    private String mMyName = null;

    /**
     * Constructor.
     * @param context
     * @param listener
     * @param selector
     * @param myUuid
     * @param myServiceType
     */
    public ConnectionManager(
            Context context, ConnectorListener listener, ConnectSelector selector,
            UUID myUuid, String myServiceType, String myName) {
        mContext = context;
        mListener = listener;
        mConnectSelector = selector;
        mHandler = new Handler(mContext.getMainLooper());
        mMyUuid = myUuid;
        mMyServiceType = myServiceType;
        mMyName = myName;

        mWifiManager = new WifiDirectManager(mContext, this);
        mBluetoothManager = new BluetoothManager(mContext, this);
    }

    /**
     *
     * @param peerId
     * @param peerName
     * @return
     */
    public synchronized EnumSet<DeviceConnectivityState> initialize(String peerId, String peerName) {
        deinitialize();
        Log.i(TAG, "initialize: " + peerId + " " + peerName);
        final EnumSet<DeviceConnectivityState> connectivityState = getDeviceConnectivityState();

        if (connectivityState.contains(DeviceConnectivityState.BLUETOOTH_OK)) {
            mInstanceString = createInstanceString(peerId, peerName, mBluetoothManager.getBluetoothAddress());
            Log.i(TAG, "initialize: Instance string: \"" + mInstanceString + "\"");
        }

        if (connectivityState.contains(DeviceConnectivityState.BLUETOOTH_NOT_PRESENT)
                || connectivityState.contains(DeviceConnectivityState.WIFI_NOT_PRESENT)) {
            Log.e(TAG, "Either Bluetooth or Wi-Fi is not present on the device: " + connectivityState.toString());
            deinitialize();
        } else if (connectivityState.contains(DeviceConnectivityState.BLUETOOTH_DISABLED)
                    || connectivityState.contains(DeviceConnectivityState.WIFI_DISABLED)) {
            Log.w(TAG, "Either Bluetooth or Wi-Fi disabled");
            setConnectorState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        } else {
            // Good to go
            setConnectorState(ConnectionManagerState.INITIALIZED);
            start();
        }

        return connectivityState;
    }

    /**
     *
     */
    public synchronized void deinitialize() {
        stop();
        mWifiManager.deinitialize();
        mBluetoothManager.deinitialize();
        setConnectorState(ConnectionManagerState.NOT_INITIALIZED);
    }

    /**
     *
     * @return
     */
    public synchronized boolean start() {
        boolean success = false;

        if (mState != ConnectionManagerState.NOT_INITIALIZED
                && mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
            startWifiPeerDiscovery();
            startBluetoothListener();
            success = true;
        } else {
            Log.e(TAG, "initialize: Cannot be started due to invalid state: " + mState.toString());
        }

        return success;
    }

    /**
     * Stops the bluetooth listener and the peer discovery. Calling this method does nothing, if
     * the aforementioned services are not running.
     */
    public synchronized void stop() {
        stopBluetoothListener();
        stopWifiPeerDiscovery();
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
                success = mBTConnector_BtConnection.TryConnect(device, mMyUuid, deviceToConnectTo.peerId, deviceToConnectTo.peerName, deviceToConnectTo.peerAddress);
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
     */
    private synchronized void startWifiPeerDiscovery() {
        if (mPeerDiscoverer == null) {
            WifiP2pManager p2pManager = mWifiManager.GetWifiP2pManager();
            WifiP2pManager.Channel channel = mWifiManager.GetWifiChannel();

            if (p2pManager != null && channel != null) {
                mPeerDiscoverer =
                        new WifiPeerDiscoverer(
                                mContext, channel, p2pManager, this, mMyServiceType, mInstanceString);

                mPeerDiscoverer.start();
                Log.i(TAG, "startWifiPeerDiscovery: OK");
            }
        } else {
            Log.e(TAG, "startWifiPeerDiscovery: Attempted to initialize peer discovery although already running!");
        }
    }

    /**
     *
     */
    private synchronized void stopWifiPeerDiscovery() {
        WifiPeerDiscoverer temp = mPeerDiscoverer;
        mPeerDiscoverer = null;

        if (temp != null) {
            Log.i(TAG, "stopWifiPeerDiscovery");
            temp.stop();
        }
    }

    /**
     *
     */
    private synchronized void startBluetoothListener() {
        if (mBTConnector_BtConnection == null) {
            BluetoothAdapter bluetoothAdapter = mBluetoothManager.getBluetoothAdapter();

            mBTConnector_BtConnection =
                    new BluetoothConnector(
                            mContext, this, bluetoothAdapter, mMyUuid, mMyName, mInstanceString);

            mBTConnector_BtConnection.start();
            Log.i(TAG, "startBluetoothListener: OK");
        } else {
            Log.e(TAG, "startBluetoothListener: Attempted to initialize although already running!");
        }
    }

    /**
     *
     */
    private synchronized void stopBluetoothListener() {
        BluetoothConnector temp = mBTConnector_BtConnection;
        mBTConnector_BtConnection = null;

        if (temp != null) {
            Log.i(TAG, "stopBluetoothListener");
            temp.Stop();
        }
    }

    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

        if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth or Wi-Fi disabled, stopping...");
                stop();
                setConnectorState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mWifiManager.isWifiEnabled() && mBluetoothManager.isBluetoothEnabled()) {
                Log.w(TAG, "onBluetoothAdapterScanModeChanged: Both Bluetooth and Wi-Fi enabled, restarting...");
                setConnectorState(ConnectionManagerState.INITIALIZED);
                start();
            }
        }
    }

    @Override
    public void onWifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
            if (mState != ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                Log.w(TAG, "onWifiStateChanged: Bluetooth or Wi-Fi disabled, stopping...");
                stop();
                setConnectorState(ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
            }
        } else {
            if (mState == ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED
                    && mWifiManager.isWifiEnabled() && mBluetoothManager.isBluetoothEnabled()) {
                Log.w(TAG, "onWifiStateChanged: Both Bluetooth and Wi-Fi enabled, restarting...");
                setConnectorState(ConnectionManagerState.INITIALIZED);
                start();
            }
        }
    }

    @Override
    public void onListOfDiscoveredPeersChanged(List<PeerDevice> peerDeviceList) {
        if (this.mConnectSelector == null) {
            return;
        }

        final List<PeerDevice> availableTmp = peerDeviceList;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.connectSelector.CurrentPeersList(availableTmp);
            }
        });
    }

    @Override
    public void onPeerDiscovered(PeerDevice peerDevice) {
        if (this.mConnectSelector == null) {
            return;
        }

        final PeerDevice serviceTmp = peerDevice;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                that.connectSelector.PeerDiscovered(serviceTmp);
            }
        });
    }

    @Override
    public void onIsDicovererRunningChanged(WifiPeerDiscoverer.DiscovererState newState) {
        switch (newState) {
            case DiscoveryIdle:
                setState(State.Idle);
                break;
            case DiscoveryNotInitialized:
                setState(State.NotInitialized);
                break;
            case DiscoveryFindingPeers:
                setState(State.FindingPeers);
                break;
            case DiscoveryFindingServices:
                setState(State.FindingServices);
                break;
            default:
                throw new RuntimeException("onIsDiscoveryStartedChanged called with invalid vale for BTConnector_Discovery.State");
        }
    }

    @Override
    public void Connected(BluetoothSocket socket, boolean incoming, String peerId, String peerName, String peerAddress) {
        if (this.callback == null) {
            return;
        }

        final BluetoothSocket socketTmp = socket;
        final boolean incomingTmp = incoming;
        final String peerIdTmp = peerId;
        final String peerNameTmp = peerName;
        final String peerAddressTmp = peerAddress;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.Connected(socketTmp, incomingTmp, peerIdTmp, peerNameTmp, peerAddressTmp);
            }
        });
    }

    @Override
    public void ConnectionFailed(String peerId, String peerName, String peerAddress) {
        if (this.callback == null) {
            return;
        }

        final String peerIdTmp = peerId;
        final String peerNameTmp = peerName;
        final String peerAddressTmp = peerAddress;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.ConnectionFailed(peerIdTmp, peerNameTmp, peerAddressTmp);
            }
        });
    }

    @Override
    public void ConnectionStateChanged(BluetoothConnector.State newState) {

        switch (newState) {
            case ConnectionConnecting:
                setState(State.Connecting);
                break;
            case ConnectionConnected:
                setState(State.Connected);
                break;
            default:
                throw new RuntimeException("ConnectionStateChanged called with invalid vale for BTConnector_BtConnection.State");
        }
    }

    /**
     *
     * @param newState
     */
    private void setConnectorState(ConnectionManagerState newState) {
        if (mState != newState) {
            Log.i(TAG, "setConnectorState: " + newState.toString());
            mState = newState;

            if (mListener != null) {
                final ConnectionManagerState tempState = mState;
                final ConnectionManager thisInstance = this;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        thisInstance.mListener.connectorStateChanged(tempState);
                    }
                });
            }
        }
    }

    /**
     * Resolves the device connectivty state in respect to Bluetooth and Wi-Fi.
     * Note that this method changes the state of both the BluetoothManager and WifiDirectManager by
     * initializing them.
     * @return The connectivity state of the device.
     */
    private EnumSet<DeviceConnectivityState> getDeviceConnectivityState()
    {
        EnumSet<DeviceConnectivityState> connectivityState = EnumSet.noneOf(DeviceConnectivityState.class);

        boolean isBluetoothPresent = mBluetoothManager.initialize();
        boolean isBluetoothEnabled = mBluetoothManager.isBluetoothEnabled();

        if (isBluetoothPresent) {
            connectivityState.add(DeviceConnectivityState.BLUETOOTH_OK);

            if (!isBluetoothEnabled) {
                connectivityState.add(DeviceConnectivityState.BLUETOOTH_DISABLED);
            }
        } else {
            connectivityState.add(DeviceConnectivityState.BLUETOOTH_NOT_PRESENT);
        }

        boolean isWifiPresent = mWifiManager.initialize();
        boolean isWifiEnabled = mWifiManager.isWifiEnabled();

        if (isWifiPresent) {
            connectivityState.add(DeviceConnectivityState.WIFI_OK);

            if (!isWifiEnabled) {
                connectivityState.add(DeviceConnectivityState.WIFI_DISABLED);
            }
        } else {
            connectivityState.add(DeviceConnectivityState.WIFI_NOT_PRESENT);
        }

        return connectivityState;
    }
}
