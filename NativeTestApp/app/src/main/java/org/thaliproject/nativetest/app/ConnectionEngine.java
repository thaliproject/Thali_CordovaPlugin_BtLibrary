/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.thaliproject.nativetest.app.fragments.LogFragment;
import org.thaliproject.nativetest.app.model.Connection;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.nativetest.app.model.Settings;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
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
    protected static final String SERVICE_TYPE = "ThaliTestSampleApp._tcp";
    protected static final String SERVICE_UUID_AS_STRING = "b6a44ad1-d319-4b3a-815d-8b805a47fb51";
    protected static final String SERVICE_NAME = "Thali_Bluetooth";
    protected static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);
    protected static final long CHECK_CONNECTIONS_INTERVAL_IN_MILLISECONDS = 10000;
    protected static final long RESTART_CONNECTION_MANAGER_DELAY_IN_MILLISECONDS = 10000;
    protected static final long NOTIFY_STATE_CHANGED_DELAY_IN_MILLISECONDS = 500;
    protected static int DURATION_OF_DEVICE_DISCOVERABLE_IN_SECONDS = 60;
    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;

    protected Context mContext = null;
    protected Activity mActivity = null;
    protected Settings mSettings = null;
    protected ConnectionManager mConnectionManager = null;
    protected DiscoveryManager mDiscoveryManager = null;
    protected PeerAndConnectionModel mModel = null;
    protected CountDownTimer mCheckConnectionsTimer = null;
    protected CountDownTimer mNotifyStateChangedTimer = null;
    private AlertDialog mAlertDialog = null;
    private boolean mIsShuttingDown = false;

    /**
     * Constructor.
     */
    public ConnectionEngine(Context context, Activity activity) {
        mContext = context;
        mActivity = activity;
        mModel = PeerAndConnectionModel.getInstance();

        mConnectionManager = new ConnectionManager(mContext, this, SERVICE_UUID, SERVICE_NAME);
        mDiscoveryManager = new DiscoveryManager(mContext, this, SERVICE_UUID, SERVICE_TYPE);
    }

    /**
     * Loads the settings and binds the discovery manager to the settings instance.
     */
    public void bindSettings() {
        mSettings = Settings.getInstance(mContext);
        mSettings.setConnectionManager(mConnectionManager);
        mSettings.setDiscoveryManager(mDiscoveryManager);
        mSettings.load();
    }

    /**
     * Starts both the connection and the discovery manager.
     *
     * @return True, if started successfully. False otherwise.
     */
    public synchronized boolean start() {
        mIsShuttingDown = false;

        boolean shouldConnectionManagerBeRunning = mSettings.getListenForIncomingConnections();
        boolean wasConnectionManagerStarted = false;

        if (shouldConnectionManagerBeRunning) {
            wasConnectionManagerStarted = mConnectionManager.startListeningForIncomingConnections();

            if (!wasConnectionManagerStarted) {
                Log.e(TAG, "start: Failed to start the connection manager");
                LogFragment.logError("Failed to start the connection manager");
            }
        }

        boolean shouldDiscoveryManagerBeRunning =
                (mSettings.getEnableBleDiscovery() || mSettings.getEnableWifiDiscovery());
        boolean wasDiscoveryManagerStarted = false;

        if (shouldDiscoveryManagerBeRunning) {
            wasDiscoveryManagerStarted =
                    (mDiscoveryManager.getState() != DiscoveryManager.DiscoveryManagerState.NOT_STARTED
                            || mDiscoveryManager.start(true, true));

            if (!shouldDiscoveryManagerBeRunning) {
                Log.e(TAG, "start: Failed to start the discovery manager");
                LogFragment.logError("Failed to start the discovery manager");
            }
        }

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

        return ((!shouldConnectionManagerBeRunning || wasConnectionManagerStarted)
                && (!shouldDiscoveryManagerBeRunning || wasDiscoveryManagerStarted));
    }

    /**
     * Stops both the connection and the discovery manager.
     */
    public synchronized void stop() {
        mIsShuttingDown = true;

        if (mCheckConnectionsTimer != null) {
            mCheckConnectionsTimer.cancel();
        }

        mConnectionManager.stopListeningForIncomingConnections();
        mConnectionManager.cancelAllConnectionAttempts();
        mDiscoveryManager.stop();

        mModel.closeAllConnections();
        mModel.clearPeers();
    }

    /**
     * Disposes both the discovery and the connection manager.
     * After calling this method, this instance of the connection engine cannot be used again.
     */
    public void dispose() {
        stop();
        mDiscoveryManager.dispose();
        mConnectionManager.dispose();
        mDiscoveryManager = null;
        mConnectionManager = null;
    }

    /**
     * Connects to the peer with the given properties.
     *
     * @param peerProperties The properties of the peer to connect to.
     */
    public synchronized void connect(PeerProperties peerProperties) {
        if (mDiscoveryManager.isAdvertising() || mDiscoveryManager.isDiscovering()) {
            mDiscoveryManager.stop();
        }

        if (peerProperties != null) {
            if (mConnectionManager.connect(peerProperties)) {
                LogFragment.logMessage("Trying to connect to peer " + peerProperties.toString());
                mModel.addPeerBeingConnectedTo(peerProperties);
                MainActivity.updateOptionsMenu();
            } else {
                String errorMessageStub = "Failed to start connecting to peer ";
                Log.e(TAG, "connect: " + errorMessageStub + peerProperties.toString());
                LogFragment.logError(errorMessageStub + peerProperties.toString());
                MainActivity.showToast(errorMessageStub + peerProperties.getBluetoothMacAddress());
            }
        }
    }

    /**
     * Starts the Bluetooth device discovery.
     */
    public void startBluetoothDeviceDiscovery() {
        mDiscoveryManager.getBluetoothMacAddressResolutionHelper().startBluetoothDeviceDiscovery();
    }

    /**
     * Makes the device discoverable.
     */
    public void makeDeviceDiscoverable() {
        mDiscoveryManager.makeDeviceDiscoverable(DURATION_OF_DEVICE_DISCOVERABLE_IN_SECONDS);
    }

    /**
     * Starts sending data to the peer with the given properties.
     *
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

    /**
     * Called when the user grants/denies a permission request.
     *
     * @param requestCode  The request code associated with the permission request.
     * @param permissions  The permissions in question.
     * @param grantResults The grant results (granted/denied).
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: Request code: " + requestCode);

        for (int i = 0; (i < permissions.length && i < grantResults.length); ++i) {
            Log.d(TAG, "onRequestPermissionsResult: Permission: " + permissions[i] + ", grant result: " + grantResults[i]);
        }

        if (requestCode == PERMISSION_REQUEST_ACCESS_COARSE_LOCATION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Permission granted");
                mDiscoveryManager.start(true, true);
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Permission denied");
            }

            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
                mAlertDialog = null;
            }
        }
    }

    /**
     * If the connection manager stopped and we are not explicitly stopping, will try to restart
     * the connection manager after a short delay.
     *
     * @param connectionManagerState The new state.
     */
    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState connectionManagerState) {
        Log.v(TAG, "onConnectionManagerStateChanged: " + connectionManagerState);
        restartNotifyStateChangedTimer();

        if (!mIsShuttingDown && connectionManagerState == ConnectionManager.ConnectionManagerState.NOT_STARTED) {
            LogFragment.logError("Connection manager stopped - trying to restart in "
                    + RESTART_CONNECTION_MANAGER_DELAY_IN_MILLISECONDS + " milliseconds");

            Handler handler = new Handler(mContext.getMainLooper());

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Trying to restart the connection manager");
                    mConnectionManager.startListeningForIncomingConnections();
                }
            }, RESTART_CONNECTION_MANAGER_DELAY_IN_MILLISECONDS);
        }
    }

    /**
     * Constructs a Bluetooth socket IO thread for the new connection and adds it to the list of
     * connections.
     *
     * @param bluetoothSocket The Bluetooth socket.
     * @param isIncoming      If true, this is an incoming connection. If false, this is an outgoing connection.
     * @param peerProperties  The peer properties.
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
            final String peerName = connection.getPeerProperties().getBluetoothMacAddress();
            final boolean wasIncoming = connection.getIsIncoming();

            mModel.addOrRemoveConnection(connection, true);

            MainActivity.showToast(peerName + " connected (is " + (wasIncoming ? "incoming" : "outgoing") + ")");

            if (isIncoming) {
                // Add peer, if it was not discovered before
                mModel.addOrUpdatePeer(peerProperties);
                mDiscoveryManager.getPeerModel().addOrUpdateDiscoveredPeer(peerProperties);
            }

            // Update the peer name, if already in the model
            mModel.updatePeerName(peerProperties);

            LogFragment.logMessage((isIncoming ? "Incoming" : "Outgoing") + " connection established to peer " + peerProperties.toString());
        }

        final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

        Log.i(TAG, "onConnected: Total number of connections is now " + totalNumberOfConnections);

        if (totalNumberOfConnections == 1) {
            if (mCheckConnectionsTimer == null) {
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
            }
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

            MainActivity.showToast("Failed to connect to " + peerProperties.getBluetoothMacAddress() + ": Connection timeout");
            LogFragment.logError("Failed to connect to peer " + peerProperties.toString() + ": Connection timeout");
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

            MainActivity.showToast("Failed to connect to " + peerProperties.getBluetoothMacAddress()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));
            LogFragment.logError("Failed to connect to peer " + peerProperties.toString()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));

        } else {
            MainActivity.showToast("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
            LogFragment.logError("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
        }

        MainActivity.updateOptionsMenu();
    }

    @Override
    public boolean onPermissionCheckRequired(String permission) {
        int permissionCheck = PackageManager.PERMISSION_DENIED;

        if (mActivity != null) {
            permissionCheck = ContextCompat.checkSelfPermission(mActivity, permission);
            Log.i(TAG, "onPermissionCheckRequired: " + permission + ": " + permissionCheck);

            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                requestPermission(permission);
            }
        } else {
            Log.e(TAG, "onPermissionCheckRequired: The activity is null");
        }

        return (permissionCheck == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * @param state         The new state.
     * @param isDiscovering True, if peer discovery is active. False otherwise.
     * @param isAdvertising True, if advertising is active. False otherwise.
     */
    @Override
    public void onDiscoveryManagerStateChanged(
            DiscoveryManager.DiscoveryManagerState state,
            boolean isDiscovering, boolean isAdvertising) {
        Log.v(TAG, "onDiscoveryManagerStateChanged: " + state + ", " + isDiscovering + ", " + isAdvertising);
        restartNotifyStateChangedTimer();
    }

    @Override
    public void onProvideBluetoothMacAddressRequest(String requestId) {
        // TODO
    }

    @Override
    public void onPeerReadyToProvideBluetoothMacAddress() {
        if (!DiscoveryManagerSettings.getInstance(null).getAutomateBluetoothMacAddressResolution()) {
            mDiscoveryManager.makeDeviceDiscoverable(DURATION_OF_DEVICE_DISCOVERABLE_IN_SECONDS);
        }
    }

    @Override
    public void onBluetoothMacAddressResolved(String bluetoothMacAddress) {
        Log.i(TAG, "onBluetoothMacAddressResolved: " + bluetoothMacAddress);
        LogFragment.logMessage("Bluetooth MAC address resolved: " + bluetoothMacAddress);
        start();
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
            mDiscoveryManager.getPeerModel().addOrUpdateDiscoveredPeer(peerProperties);
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
    public void onDisconnected(String reason, final Connection connection) {
        Log.i(TAG, "onDisconnected: Peer " + connection.getPeerProperties().toString()
                + " disconnected: " + reason);
        final PeerProperties peerProperties = connection.getPeerProperties();
        final String peerName = peerProperties.getBluetoothMacAddress();
        final boolean wasIncoming = connection.getIsIncoming();

        synchronized (this) {
            new Thread() {
                @Override
                public void run() {
                    if (!mModel.addOrRemoveConnection(connection, false) && !mIsShuttingDown) {
                        Log.e(TAG, "onDisconnected: Failed to remove the connection, because not found in the list");
                    } else if (!mIsShuttingDown) {
                        Log.d(TAG, "onDisconnected: Connection " + connection.toString() + " removed from the list");
                    }

                    connection.close(true);

                    final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

                    Log.i(TAG, "onDisconnected: Total number of connections is now " + totalNumberOfConnections);

                    if (totalNumberOfConnections == 0) {
                        mCheckConnectionsTimer.cancel();
                    }

                    if (!mIsShuttingDown) {
                        autoConnectIfEnabled(peerProperties);
                    }

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
        MainActivity.showToast(message + " to peer " + receivingPeer.getBluetoothMacAddress());
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
     *
     * @param peerProperties The peer properties.
     */
    protected synchronized void autoConnectIfEnabled(PeerProperties peerProperties) {
        //null pointer crash on battery test
        if (!mIsShuttingDown && mSettings != null) {
            if (mSettings.getAutoConnect() && !mModel.hasConnectionToPeer(peerProperties, false)) {
                if (mSettings.getAutoConnectEvenWhenIncomingConnectionEstablished()
                        || !mModel.hasConnectionToPeer(peerProperties, true)) {
                    // Do auto-connect
                    Log.i(TAG, "autoConnectIfEnabled: Auto-connecting to peer " + peerProperties.toString());
                    connect(peerProperties);
                }
            }
        }
    }

    protected synchronized void restartNotifyStateChangedTimer() {
        if (mNotifyStateChangedTimer != null) {
            mNotifyStateChangedTimer.cancel();
            mNotifyStateChangedTimer = null;
        }

        mNotifyStateChangedTimer = new CountDownTimer(
                NOTIFY_STATE_CHANGED_DELAY_IN_MILLISECONDS, NOTIFY_STATE_CHANGED_DELAY_IN_MILLISECONDS) {
            @Override
            public void onTick(long l) {
                // Not used
            }

            @Override
            public void onFinish() {
                this.cancel();
                mNotifyStateChangedTimer = null;

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Connectivity: ");
                stringBuilder.append((mConnectionManager != null)
                        ? mConnectionManager.getState() : "not running");
                stringBuilder.append(", discovery: ");
                stringBuilder.append((mDiscoveryManager != null)
                        ? mDiscoveryManager.getState() : "not running");
                stringBuilder.append(", ");
                stringBuilder.append((mDiscoveryManager != null && mDiscoveryManager.isDiscovering())
                        ? "discovering/scanning" : "not discovering/scanning");
                stringBuilder.append(", ");
                stringBuilder.append((mDiscoveryManager != null && mDiscoveryManager.isAdvertising())
                        ? "advertising" : "not advertising");
                String message = stringBuilder.toString();

                LogFragment.logMessage(message);
                MainActivity.showToast(message);
            }
        };

        mNotifyStateChangedTimer.start();
    }

    /**
     * Prompts the user to grant the given permission.
     *
     * @param permission The permission, which needs to be granted.
     */
    private void requestPermission(final String permission) {
        Log.i(TAG, "requestPermission: " + permission);

        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permission)) {
            // The app has requested this permission previously and the user denied the request.
            if (mAlertDialog == null) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mActivity);
                alertDialogBuilder.setMessage("Location permission is required to scan for nearby peers using Bluetooth LE.");
                alertDialogBuilder.setCancelable(true);

                alertDialogBuilder.setPositiveButton(
                        "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                ActivityCompat.requestPermissions(
                                        mActivity, new String[]{permission}, PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
                            }
                        });

                mAlertDialog = alertDialogBuilder.create();
            }

            if (!mAlertDialog.isShowing()) {
                mAlertDialog.show();
            }
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(
                    mActivity, new String[]{permission}, PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
        }
    }

}
