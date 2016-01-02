/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app;

import java.io.IOException;
import java.util.UUID;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import org.thaliproject.nativesample.app.fragments.LogFragment;
import org.thaliproject.nativesample.app.fragments.PeerListFragment;
import org.thaliproject.nativesample.app.fragments.SettingsFragment;
import org.thaliproject.nativesample.app.model.Connection;
import org.thaliproject.nativesample.app.model.PeerAndConnectionModel;
import org.thaliproject.nativesample.app.model.Settings;
import org.thaliproject.nativesample.app.slidingtabs.SlidingTabLayout;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;

public class MainActivity
        extends AppCompatActivity
        implements
            ConnectionManager.ConnectionManagerListener,
            DiscoveryManager.DiscoveryManagerListener,
            Connection.Listener,
            PeerListFragment.Listener {

    private static final String TAG = MainActivity.class.getName();

    // Service type and UUID has to be application/service specific.
    // The app will only connect to peers with the matching values.
    private static final String SERVICE_TYPE = "ThaliNativeSampleApp._tcp";
    private static final String SERVICE_UUID_AS_STRING = "9ab3c173-66d5-4da6-9e23-e8ce520b479b";
    private static final String SERVICE_NAME = "Thali Native Sample App";
    public static final String PEER_NAME = Build.MANUFACTURER + "_" + Build.MODEL; // Use manufacturer and device model name as the peer name
    private static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);
    private static final long CHECK_CONNECTIONS_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private MyFragmentAdapter mMyFragmentAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private SlidingTabLayout mSlidingTabLayout;
    private Context mContext = null;
    private Settings mSettings = null;
    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private CountDownTimer mCheckConnectionsTimer = null;
    private PeerAndConnectionModel mModel = null;
    private PeerProperties mSelectedPeerProperties = null;
    private PeerListFragment mPeerListFragment = null;
    private LogFragment mLogFragment = null;
    private SettingsFragment mSettingsFragment = null;
    private Menu mMainMenu = null;
    private boolean mShuttingDown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();

        mMyFragmentAdapter = new MyFragmentAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager)findViewById(R.id.pager);
        mViewPager.setAdapter(mMyFragmentAdapter);

        mSlidingTabLayout = (SlidingTabLayout)findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);

        mShuttingDown = false;

        mContext = this.getApplicationContext();
        mSettings = Settings.getInstance(mContext);
        mSettings.load();

        if (mConnectionManager == null) {
            mModel = PeerAndConnectionModel.getInstance();

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

            mConnectionManager = new ConnectionManager(mContext, this, SERVICE_UUID, SERVICE_NAME);
            mSettings.setConnectionManager(mConnectionManager);
            mConnectionManager.setConnectionTimeout(mSettings.getConnectionTimeout());
            mConnectionManager.setInsecureRfcommSocketPort(mSettings.getPortNumber());

            mDiscoveryManager = new DiscoveryManager(mContext, this, SERVICE_UUID, SERVICE_TYPE);
            mSettings.setDiscoveryManager(mDiscoveryManager);
            mSettings.setDesiredDiscoveryMode();

            mConnectionManager.start(PEER_NAME);
            mDiscoveryManager.start(PEER_NAME);

            mPeerListFragment = new PeerListFragment();
            mPeerListFragment.setListener(this);

            mLogFragment = new LogFragment();

            mSettingsFragment = new SettingsFragment();
        }
    }

    @Override
    public void onDestroy() {
        mShuttingDown = true;
        mCheckConnectionsTimer.cancel();

        mModel.closeAllConnections();

        mConnectionManager.stop();
        mDiscoveryManager.stop();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mMainMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mSelectedPeerProperties == null) {
            menu.getItem(0).setVisible(false);
            menu.getItem(1).setVisible(false);
            menu.getItem(0).setEnabled(false);
            menu.getItem(1).setEnabled(false);
            menu.getItem(2).setEnabled(false);
        } else {
            Connection connection = mModel.getConnectionToPeer(mSelectedPeerProperties, false);

            if (connection != null) {
                // We have an outgoing connection
                menu.getItem(0).setVisible(false);
                menu.getItem(0).setEnabled(false);

                if (connection.isSendingData()) {
                    menu.getItem(1).setVisible(true);
                    menu.getItem(1).setEnabled(false);
                } else {
                    menu.getItem(1).setVisible(true);
                    menu.getItem(1).setEnabled(true);
                }

                menu.getItem(2).setEnabled(true);
            } else {
                // No outgoing connection
                menu.getItem(0).setVisible(true);
                menu.getItem(0).setEnabled(true);
                menu.getItem(2).setEnabled(false);

                connection = mModel.getConnectionToPeer(mSelectedPeerProperties, true);

                if (connection != null) {
                    if (connection.isSendingData()) {
                        menu.getItem(1).setVisible(true);
                        menu.getItem(1).setEnabled(false);
                    } else {
                        menu.getItem(1).setVisible(true);
                        menu.getItem(1).setEnabled(true);
                    }
                } else {
                    // No incoming/outgoing connection
                    menu.getItem(0).setVisible(true);
                    menu.getItem(0).setEnabled(true);
                    menu.getItem(1).setVisible(false);
                    menu.getItem(1).setEnabled(false);
                    menu.getItem(2).setEnabled(false);
                }
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        boolean wasConsumed = false;

        switch (id) {
            case R.id.action_connect:
                onConnectRequest(mSelectedPeerProperties); // Has a null check
                wasConsumed = true;
                break;
            case R.id.action_disconnect:
                if (mSelectedPeerProperties != null) {
                    if (mModel.closeConnection(mSelectedPeerProperties)) {
                        wasConsumed = true;
                    }
                }

                break;
            case R.id.action_send_data:
                onSendDataRequest(mSelectedPeerProperties); // Has a null check
                wasConsumed = true;
                break;
            case R.id.action_kill_all_connections:
                mModel.closeAllConnections();
                wasConsumed = true;
                break;
        }

        return wasConsumed || super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState connectionManagerState) {
        mLogFragment.logMessage("Connection manager state changed: " + connectionManagerState);
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

            showToast(peerName + " connected (is " + (wasIncoming ? "incoming" : "outgoing") + ")");

            if (isIncoming) {
                // Add peer, if it was not discovered before
                mModel.addOrUpdatePeer(peerProperties);
                mDiscoveryManager.addOrUpdateDiscoveredPeer(peerProperties);
            }

            // Update the peer name, if already in the model
            mModel.updatePeerName(peerProperties);

            mLogFragment.logMessage((isIncoming ? "Incoming" : "Outgoing") + " connection established to peer " + peerProperties.toString());
        }

        final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

        Log.i(TAG, "onConnected: Total number of connections is now " + totalNumberOfConnections);

        if (totalNumberOfConnections == 1) {
            mCheckConnectionsTimer.cancel();
            mCheckConnectionsTimer.start();
        }

        onPrepareOptionsMenu(mMainMenu); // Update the main menu
    }

    @Override
    public void onConnectionTimeout(PeerProperties peerProperties) {
        Log.i(TAG, "onConnectionTimeout: " + peerProperties);

        if (peerProperties != null) {
            showToast("Failed to connect to " + peerProperties.getName() + ": Connection timeout");
            mLogFragment.logError("Failed to connect to peer " + peerProperties.toString() + ": Connection timeout");
        } else {
            showToast("Failed to connect: Connection timeout");
            mLogFragment.logError("Failed to connect: Connection timeout");
        }

        onPrepareOptionsMenu(mMainMenu); // Update the main menu
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        Log.i(TAG, "onConnectionFailed: " + errorMessage + ": " + peerProperties);

        if (peerProperties != null) {
            showToast("Failed to connect to " + peerProperties.getName()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));
            mLogFragment.logError("Failed to connect to peer " + peerProperties.toString()
                    + ((errorMessage != null) ? (": " + errorMessage) : ""));
        } else {
            showToast("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
            mLogFragment.logError("Failed to connect" + ((errorMessage != null) ? (": " + errorMessage) : ""));
        }

        onPrepareOptionsMenu(mMainMenu); // Update the main menu
    }

    @Override
    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState discoveryManagerState) {
        mLogFragment.logMessage("Discovery manager state changed: " + discoveryManagerState);
        showToast("Discovery manager state changed: " + discoveryManagerState);
    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered: " + peerProperties.toString());

        if (mModel.addOrUpdatePeer(peerProperties)) {
            mLogFragment.logMessage("Peer " + peerProperties.toString() + " discovered");

            if (mSettings.getAutoConnect() && !mModel.hasConnectionToPeer(peerProperties.getId(), false)) {
                if (mSettings.getAutoConnectEvenWhenIncomingConnectionEstablished()
                        || !mModel.hasConnectionToPeer(peerProperties.getId(), true)) {
                    // Do auto-connect
                    Log.i(TAG, "onPeerDiscovered: Auto-connecting to peer " + peerProperties.toString());
                    mConnectionManager.connect(peerProperties);
                }
            }
        }
    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerUpdated: " + peerProperties.toString());
        mModel.addOrUpdatePeer(peerProperties);
        mLogFragment.logMessage("Peer " + peerProperties.toString() + " updated");
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerLost: " + peerProperties.toString());

        if (mModel.hasConnectionToPeer(peerProperties)) {
            // We are connected so it can't be lost
            mDiscoveryManager.addOrUpdateDiscoveredPeer(peerProperties);
        } else {
            mModel.removePeer(peerProperties);
            mLogFragment.logMessage("Peer " + peerProperties.toString() + " lost");
        }

        if (mSelectedPeerProperties.equals(peerProperties)) {
            onPeerSelected(null);
        }
    }

    @Override
    public void onBytesRead(byte[] bytes, int numberOfBytesRead, BluetoothSocketIoThread bluetoothSocketIoThread) {
        Log.i(TAG, "onBytesRead: Received " + numberOfBytesRead + " bytes from peer "
                + (bluetoothSocketIoThread.getPeerProperties() != null
                ? bluetoothSocketIoThread.getPeerProperties().toString() : "<no ID>"));
    }

    @Override
    public void onBytesWritten(byte[] bytes, int numberOfBytesWritten, BluetoothSocketIoThread bluetoothSocketIoThread) {
        Log.i(TAG, "onBytesWritten: Sent " + numberOfBytesWritten + " bytes to peer "
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

                onPrepareOptionsMenu(mMainMenu); // Update the main menu
            }
        }.start();

        showToast(peerName + " disconnected (was " + (wasIncoming ? "incoming" : "outgoing") + ")");
        mLogFragment.logMessage("Peer " + peerProperties.toString() + " disconnected (was " + (wasIncoming ? "incoming" : "outgoing") + ")");
    }

    @Override
    public void onSendDataProgress(float progressInPercentages, float transferSpeed, PeerProperties receivingPeer) {
        Log.i(TAG, "onSendDataProgress: " + Math.round(progressInPercentages * 100) + " % " + transferSpeed + " MB/s");
        mModel.requestUpdateUi(); // To update the progress bar
    }

    @Override
    public void onDataSent(float dataSentInMegaBytes, float transferSpeed, PeerProperties receivingPeer) {
        String message = "Sent " + String.format("%.2f", dataSentInMegaBytes)
                + " MB with transfer speed of " + String.format("%.3f", transferSpeed) + " MB/s";

        Log.i(TAG, "onDataSent: " + message + " to peer " + receivingPeer);
        mLogFragment.logMessage(message + " to peer " + receivingPeer);
        showToast(message + " to peer " + receivingPeer.getName());
        mModel.requestUpdateUi(); // To update the progress bar
        onPrepareOptionsMenu(mMainMenu); // Update the main menu
    }

    @Override
    public void onPeerSelected(PeerProperties peerProperties) {
        mSelectedPeerProperties = peerProperties;
        onPrepareOptionsMenu(mMainMenu); // Update the main menu
    }

    @Override
    public void onConnectRequest(PeerProperties peerProperties) {
        if (peerProperties != null) {
            mConnectionManager.connect(peerProperties);
            mLogFragment.logMessage("Trying to connect to peer " + peerProperties.toString());
            onPrepareOptionsMenu(mMainMenu); // Update the main menu
        }
    }

    @Override
    public void onSendDataRequest(PeerProperties peerProperties) {
        Connection connection = mModel.getConnectionToPeer(peerProperties, false);

        if (connection == null) {
            connection = mModel.getConnectionToPeer(peerProperties, true);
        }

        if (connection != null) {
            connection.sendData();
            mLogFragment.logMessage("Sending "
                    + String.format("%.2f", connection.getTotalDataAmountCurrentlySendingInMegaBytes())
                    + " MB to peer " + peerProperties.toString());
            mModel.requestUpdateUi(); // To update the progress bar
            onPrepareOptionsMenu(mMainMenu); // Update the main menu
        } else {
            Log.e(TAG, "onSendDataRequest: No connection found");
        }
    }

    /**
     * Sends a ping message to all connected peers.
     */
    private synchronized void sendPingToAllPeers() {
        for (Connection connection : mModel.getConnections()) {
            connection.ping();
        }
    }

    /**
     * Displays a toast with the given message.
     * @param message The message to show.
     */
    private void showToast(final String message) {
        Handler handler = new Handler(mContext.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();
                CharSequence text = message;
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });
    }

    /**
     * The fragment adapter for tabs.
     */
    public class MyFragmentAdapter extends FragmentPagerAdapter {
        private static final int PEER_LIST_FRAGMENT = 0;
        private static final int LOG_FRAGMENT = 1;
        private static final int SETTINGS_FRAGMENT = 2;

        public MyFragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int index) {
            switch (index){
                case PEER_LIST_FRAGMENT: return mPeerListFragment;
                case LOG_FRAGMENT: return mLogFragment;
                case SETTINGS_FRAGMENT: return mSettingsFragment;
            }

            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case PEER_LIST_FRAGMENT: return "Peers";
                case LOG_FRAGMENT: return "Log";
                case SETTINGS_FRAGMENT: return "Settings";
            }

            return super.getPageTitle(position);
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
