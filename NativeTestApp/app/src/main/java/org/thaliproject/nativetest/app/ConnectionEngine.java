/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;
import org.thaliproject.nativetest.app.fragments.LogFragment;
import org.thaliproject.nativetest.app.model.Connection;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.nativetest.app.model.Settings;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import java.io.IOException;
import java.util.UUID;

/**
 * This class is responsible for managing both peer discovery and connections.
 */
public class ConnectionEngine implements
        ConnectionManager.ConnectionManagerListener,
        DiscoveryManager.DiscoveryManagerListener,
        Connection.Listener {
    protected static final String TAG = ConnectionEngine.class.getName();

    // Service type and UUID has to be application/service specific.
    // The app will only connect to peers with the matching values.
    public static final String PEER_NAME = Build.MANUFACTURER + "_" + Build.MODEL; // Use manufacturer and device model name as the peer name
    protected static final String SERVICE_TYPE = "ThaliNativeSampleApp._tcp";
    protected static final String SERVICE_UUID_AS_STRING = "9ab3c173-66d5-4da6-9e23-e8ce520b479b";
    protected static final String SERVICE_NAME = "Thali Native Sample App";
    protected static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);
    protected static final long CHECK_CONNECTIONS_INTERVAL_IN_MILLISECONDS = 10000;

    protected Context mContext = null;
    protected Settings mSettings = null;
    protected ConnectionManager mConnectionManager = null;
    protected DiscoveryManager mDiscoveryManager = null;
    protected PeerAndConnectionModel mModel = null;
    protected CountDownTimer mCheckConnectionsTimer = null;
    private boolean mShuttingDown = false;

    /**
     * Constructor.
     */
    public ConnectionEngine(Context context) {
        mContext = context;
        mModel = PeerAndConnectionModel.getInstance();
        mConnectionManager = new ConnectionManager(mContext, this, SERVICE_UUID, SERVICE_NAME);
        mDiscoveryManager = new DiscoveryManager(mContext, this, SERVICE_UUID, SERVICE_TYPE);
    }

    /**
     * Loads the settings and binds the discovery manager to the settings instance.
     */
    public void bindSettings() {
        mSettings = Settings.getInstance(mContext);
        mSettings.setDiscoveryManager(mDiscoveryManager);
        mSettings.load();
    }

    /**
     * Starts both the connection and the discovery manager.
     * @return True, if started successfully. False otherwise.
     */
    public synchronized boolean start() {
        mShuttingDown = false;
        boolean wasStarted = false;

        if (mConnectionManager.start(PEER_NAME)
                && (mDiscoveryManager.getState() != DiscoveryManager.DiscoveryManagerState.NOT_STARTED
                    || mDiscoveryManager.start(PEER_NAME))) {
            if (mCheckConnectionsTimer != null) {
                mCheckConnectionsTimer.cancel();
                mCheckConnectionsTimer = null;
            }

            mCheckConnectionsTimer = new CountDownTimer(
                    CHECK_CONNECTIONS_INTERVAL_IN_MILLISECONDS,
                    CHECK_CONNECTIONS_INTERVAL_IN_MILLISECONDS) {
                @Override
                public void onTick(long l) {
                    // Not used
                }

                @Override
                public void onFinish() {
                    sendPingToAllPeers();
                    mCheckConnectionsTimer.start();
                }
            };

            wasStarted = true;
        } else {
            Log.e(TAG, "start: Failed to start");
            LogFragment.logError("Failed to start either the connection or the discovery manager");
        }

        return wasStarted;
    }

    /**
     * Stops both the connection and the discovery manager.
     */
    public synchronized void stop() {
        mShuttingDown = true;

        if (mCheckConnectionsTimer != null) {
            mCheckConnectionsTimer.cancel();
        }

        mConnectionManager.stop();
        mDiscoveryManager.stop();

        mModel.closeAllConnections();
        mModel.clearPeers();
    }

    /**
     * Connects to the peer with the given properties.
     * @param peerProperties The properties of the peer to connect to.
     */
    public synchronized void connect(PeerProperties peerProperties) {
        if (peerProperties != null) {
            if (mConnectionManager.connect(peerProperties)) {
                LogFragment.logMessage("Trying to connect to peer " + peerProperties.toString());
                mModel.addPeerBeingConnectedTo(peerProperties);
                MainActivity.updateOptionsMenu();
            } else {
                String errorMessageStub = "Failed to start connecting to peer ";
                Log.e(TAG, "connect: " + errorMessageStub + peerProperties.toString());
                LogFragment.logError(errorMessageStub + peerProperties.toString());
                MainActivity.showToast(errorMessageStub + peerProperties.getName());
            }
        }
    }

    /**
     * Starts sending data to the peer with the given properties.
     * @param peerProperties The properties of the peer to send data to.
     */
    public synchronized void startSendingData(PeerProperties peerProperties) {
        Connection connection = mModel.getConnectionToPeer(peerProperties, false);

        if (connection == null) {
            connection = mModel.getConnectionToPeer(peerProperties, true);
        }

        if (connection != null) {
            connection.sendData();
            LogFragment.logMessage("Sending "
                    + String.format("%.2f", connection.getTotalDataAmountCurrentlySendingInMegaBytes())
                    + " MB to peer " + peerProperties.toString());
            mModel.notifyListenersOnDataChanged(); // To update the progress bar
            MainActivity.updateOptionsMenu();
        } else {
            Log.e(TAG, "startSendingData: No connection found");
        }
    }
    
    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState connectionManagerState) {
        LogFragment.logMessage("Connection manager state changed: " + connectionManagerState);
    }

    /**
     * Constructs a Bluetooth socket IO thread for the new connection and adds it to the list of
     * connections.
     * @param bluetoothSocket The Bluetooth socket.
     * @param isIncoming If true, this is an incoming connection. If false, this is an outgoing connection.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
        Log.i(TAG, "onConnected: " + (isIncoming ? "Incoming" : "Outgoing") + " connection: " + peerProperties.toString());
        mModel.removePeerBeingConnectedTo(peerProperties);
        Connection connection = null;

        try {
            connection = new Connection(this, bluetoothSocket, peerProperties, isIncoming);
        } catch (Exception e) {
            Log.e(TAG, "onConnected: Failed to create a socket IO thread instance: " + e.getMessage(), e);

            try {
                bluetoothSocket.close();
            } catch (IOException e2) {
            }
        }

        if (connection != null) {
            final String peerName = connection.getPeerProperties().getName();
            final boolean wasIncoming = connection.getIsIncoming();

            mModel.addOrRemoveConnection(connection, true);

            MainActivity.showToast(peerName + " connected (is " + (wasIncoming ? "incoming" : "outgoing") + ")");

            if (isIncoming) {
                // Add peer, if it was not discovered before
                mModel.addOrUpdatePeer(peerProperties);
                mDiscoveryManager.addOrUpdateDiscoveredPeer(peerProperties);
            }

            // Update the peer name, if already in the model
            mModel.updatePeerName(peerProperties);

            LogFragment.logMessage((isIncoming ? "Incoming" : "Outgoing") + " connection established to peer " + peerProperties.toString());
        }

        final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

        Log.i(TAG, "onConnected: Total number of connections is now " + totalNumberOfConnections);

        if (totalNumberOfConnections == 1) {
            mCheckConnectionsTimer.cancel();
            mCheckConnectionsTimer.start();
        }

        MainActivity.updateOptionsMenu();
    }

    @Override
    public void onConnectionTimeout(PeerProperties peerProperties) {
        Log.i(TAG, "onConnectionTimeout: " + peerProperties);

        if (peerProperties != null) {
            mModel.removePeerBeingConnectedTo(peerProperties);

            MainActivity.showToast("Failed to connect to " + peerProperties.getName() + ": Connection timeout");
            LogFragment.logError("Failed to connect to peer " + peerProperties.toString() + ": Connection timeout");

            autoConnectIfEnabled(peerProperties);
        } else {
            MainActivity.showToast("Failed to connect: Connection timeout");
            LogFragment.logError("Failed to connect: Connection timeout");
        }

        MainActivity.updateOptionsMenu();
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        Log.i(TAG, "onConnectionFailed: " + errorMessage + ": " + peerProperties);

        if (peerProperties != null) {
            mModel.removePeerBeingConnectedTo(peerProperties);

            MainActivity.showToast("Failed to connect to " + peerProperties.getName()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));
            LogFragment.logError("Failed to connect to peer " + peerProperties.toString()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));

            autoConnectIfEnabled(peerProperties);

        } else {
            MainActivity.showToast("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
            LogFragment.logError("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
        }

        MainActivity.updateOptionsMenu();
    }

    @Override
    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState discoveryManagerState) {
        LogFragment.logMessage("Discovery manager state changed: " + discoveryManagerState);
        MainActivity.showToast("Discovery manager state changed: " + discoveryManagerState);
    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered: " + peerProperties.toString());

        if (mModel.addOrUpdatePeer(peerProperties)) {
            LogFragment.logMessage("Peer " + peerProperties.toString() + " discovered");
            autoConnectIfEnabled(peerProperties);
        }
    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerUpdated: " + peerProperties.toString());
        mModel.addOrUpdatePeer(peerProperties);
        LogFragment.logMessage("Peer " + peerProperties.toString() + " updated");
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerLost: " + peerProperties.toString());

        if (mModel.hasConnectionToPeer(peerProperties)) {
            // We are connected so it can't be lost
            mDiscoveryManager.addOrUpdateDiscoveredPeer(peerProperties);
        } else {
            mModel.removePeer(peerProperties);
            LogFragment.logMessage("Peer " + peerProperties.toString() + " lost");
        }
    }

    @Override
    public void onBytesRead(byte[] bytes, int numberOfBytesRead, BluetoothSocketIoThread bluetoothSocketIoThread) {
        Log.v(TAG, "onBytesRead: Received " + numberOfBytesRead + " bytes from peer "
                + (bluetoothSocketIoThread.getPeerProperties() != null
                ? bluetoothSocketIoThread.getPeerProperties().toString() : "<no ID>"));
    }

    @Override
    public void onBytesWritten(byte[] bytes, int numberOfBytesWritten, BluetoothSocketIoThread bluetoothSocketIoThread) {
        Log.v(TAG, "onBytesWritten: Sent " + numberOfBytesWritten + " bytes to peer "
                + (bluetoothSocketIoThread.getPeerProperties() != null
                ? bluetoothSocketIoThread.getPeerProperties().toString() : "<no ID>"));
    }

    @Override
    public void onDisconnected(String reason, Connection connection) {
        Log.i(TAG, "onDisconnected: Peer " + connection.getPeerProperties().toString()
                + " disconnected: " + reason);
        final Connection finalConnection = connection;
        final PeerProperties peerProperties = connection.getPeerProperties();
        final String peerName = peerProperties.getName();
        final boolean wasIncoming = connection.getIsIncoming();

        synchronized (this) {
            new Thread() {
                @Override
                public void run() {
                    if (!mModel.addOrRemoveConnection(finalConnection, false) && !mShuttingDown) {
                        Log.e(TAG, "onDisconnected: Failed to remove the connection, because not found in the list");
                    } else if (!mShuttingDown) {
                        Log.d(TAG, "onDisconnected: Connection " + finalConnection.toString() + " removed from the list");
                    }

                    finalConnection.close(true);

                    final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

                    Log.i(TAG, "onDisconnected: Total number of connections is now " + totalNumberOfConnections);

                    if (totalNumberOfConnections == 0) {
                        mCheckConnectionsTimer.cancel();
                    }

                    autoConnectIfEnabled(peerProperties);
                    MainActivity.updateOptionsMenu();
                }
            }.start();
        }

        MainActivity.showToast(peerName + " disconnected (was " + (wasIncoming ? "incoming" : "outgoing") + ")");
        LogFragment.logMessage("Peer " + peerProperties.toString() + " disconnected (was " + (wasIncoming ? "incoming" : "outgoing") + ")");
    }

    @Override
    public void onSendDataProgress(float progressInPercentages, float transferSpeed, PeerProperties receivingPeer) {
        Log.d(TAG, "onSendDataProgress: " + Math.round(progressInPercentages * 100) + " % " + transferSpeed + " MB/s");
        mModel.notifyListenersOnDataChanged(); // To update the progress bar
    }

    @Override
    public void onDataSent(float dataSentInMegaBytes, float transferSpeed, PeerProperties receivingPeer) {
        String message = "Sent " + String.format("%.2f", dataSentInMegaBytes)
                + " MB with transfer speed of " + String.format("%.3f", transferSpeed) + " MB/s";

        Log.i(TAG, "onDataSent: " + message + " to peer " + receivingPeer);
        LogFragment.logMessage(message + " to peer " + receivingPeer);
        MainActivity.showToast(message + " to peer " + receivingPeer.getName());
        mModel.notifyListenersOnDataChanged(); // To update the progress bar
        MainActivity.updateOptionsMenu();
    }

    /**
     * Sends a ping message to all connected peers.
     */
    protected synchronized void sendPingToAllPeers() {
        for (Connection connection : mModel.getConnections()) {
            connection.ping();
        }
    }

    /**
     * Tries to connect to the peer with the given properties if the auto-connect is enabled.
     * @param peerProperties The peer properties.
     */
    protected synchronized void autoConnectIfEnabled(PeerProperties peerProperties) {
        if (mSettings.getAutoConnect() && !mModel.hasConnectionToPeer(peerProperties, false)) {
            if (mSettings.getAutoConnectEvenWhenIncomingConnectionEstablished()
                    || !mModel.hasConnectionToPeer(peerProperties, true)) {
                // Do auto-connect
                Log.i(TAG, "autoConnectIfEnabled: Auto-connecting to peer " + peerProperties.toString());
                mConnectionManager.connect(peerProperties);
            }
        }
    }
}
