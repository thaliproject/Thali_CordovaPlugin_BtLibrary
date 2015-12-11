package org.thaliproject.nativesample.app;

import java.io.IOException;
import java.util.List;
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
    private static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);

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
    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private CountDownTimer mCheckConnectionsTimer = null;
    private PeerAndConnectionModel mModel = null;
    private PeerListFragment mPeerListFragment = null;
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

        if (mConnectionManager == null) {
            mModel = PeerAndConnectionModel.getInstance();

            mCheckConnectionsTimer = new CountDownTimer(10000, 10000) {
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

            mContext = this.getApplicationContext();
            mConnectionManager = new ConnectionManager(mContext, this, SERVICE_UUID, SERVICE_NAME);
            mDiscoveryManager = new DiscoveryManager(mContext, this, SERVICE_TYPE);

            String peerName = Build.PRODUCT; // Use product (device) name as the peer name
            mConnectionManager.start(peerName);
            mDiscoveryManager.setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
            mDiscoveryManager.start(peerName);

            mPeerListFragment = new PeerListFragment();
            mPeerListFragment.setListener(this);
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState connectionManagerState) {

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
                mModel.addPeer(peerProperties);
            }
        }

        final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

        Log.i(TAG, "onConnected: Total number of connections is now " + totalNumberOfConnections);

        if (totalNumberOfConnections == 1) {
            mCheckConnectionsTimer.cancel();
            mCheckConnectionsTimer.start();
        }
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties) {
        Log.i(TAG, "onConnectionFailed: " + peerProperties);

        if (peerProperties != null) {
            showToast("Failed to connect to " + peerProperties.getName());
        } else {
            showToast("Failed to connect");
        }
    }

    @Override
    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState discoveryManagerState) {

    }

    @Override
    public void onPeerListChanged(List<PeerProperties> list) {

    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered: " + peerProperties.toString());

        if (mModel.addPeer(peerProperties)) {
            // Uncomment the following to autoconnect
            //mConnectionManager.connect(peerProperties);
        }
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {

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
        final String peerName = connection.getPeerProperties().getName();
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
            }
        }.start();

        showToast(peerName + " disconnected (was " + (wasIncoming ? "incoming" : "outgoing") + ")");
    }

    @Override
    public void onConnectRequest(PeerProperties peerProperties) {
        mConnectionManager.connect(peerProperties);
    }

    @Override
    public void onSendDataRequest(PeerProperties peerProperties) {

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
     *
     */
    public class MyFragmentAdapter extends FragmentPagerAdapter {
        private static final int PEER_LIST_FRAGMENT = 0;
        private static final int LOG_FRAGMENT = 1;

        public MyFragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int index) {
            switch (index){
                case PEER_LIST_FRAGMENT: return mPeerListFragment;
                case LOG_FRAGMENT: return new LogFragment();
            }

            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case PEER_LIST_FRAGMENT: return "Peers";
                case LOG_FRAGMENT: return "Log";
            }

            return super.getPageTitle(position);
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
