package org.thaliproject.nativesample.app;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.CountDownTimer;
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
            BluetoothSocketIoThread.Listener {

    private static final String TAG = MainActivity.class.getName();

    // Service type and UUID has to be application/service specific.
    // The app will only connect to peers with the matching values.
    private static final String SERVICE_TYPE = "ThaliNativeSampleApp._tcp";
    private static final String SERVICE_UUID_AS_STRING = "9ab3c173-66d5-4da6-9e23-e8ce520b479b";
    private static final String SERVICE_NAME = "Thali Native Sample App";
    private static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);

    private static final byte[] PING_PACKAGE = new String("Is there anybody out there?").getBytes();
    private static final int SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES = 1024 * 2;

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
    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private CountDownTimer mCheckConnectionsTimer = null;
    private PeerAndConnectionModel mModel = null;
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

            Context context = this.getApplicationContext();
            mConnectionManager = new ConnectionManager(context, this, SERVICE_UUID, SERVICE_NAME);
            mDiscoveryManager = new DiscoveryManager(context, this, SERVICE_TYPE);

            String peerName = Build.PRODUCT; // Use product (device) name as the peer name
            mConnectionManager.start(peerName);
            mDiscoveryManager.setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
            mDiscoveryManager.start(peerName);
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
        BluetoothSocketIoThread socketIoThread = null;

        try {
            socketIoThread = new BluetoothSocketIoThread(bluetoothSocket, this);
        } catch (Exception e) {
            Log.e(TAG, "onConnected: Failed to create a socket IO thread instance: " + e.getMessage(), e);

            try {
                bluetoothSocket.close();
            } catch (IOException e2) {
            }
        }

        socketIoThread.setPeerProperties(peerProperties);
        socketIoThread.setBufferSize(SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);

        mModel.addConnection(peerProperties.getId(), socketIoThread, isIncoming);

        socketIoThread.start(); // Start listening for incoming data
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
            mConnectionManager.connect(peerProperties);
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
    public void onDisconnected(String reason, BluetoothSocketIoThread bluetoothSocketIoThread) {
        PeerProperties peerProperties = bluetoothSocketIoThread.getPeerProperties();

        if (peerProperties != null) {
            Log.i(TAG, "onDisconnected: Peer " + bluetoothSocketIoThread.getPeerProperties().toString()
                    + " disconnected: " + reason);

            if (!mModel.removeConnection(peerProperties.getId(), bluetoothSocketIoThread) && !mShuttingDown) {
                Log.e(TAG, "onDisconnected: Failed to remove the connection, because not found in the list");
            }

            bluetoothSocketIoThread.close(true);
        } else {
            Log.e(TAG, "onDisconnected: No peer properties");
        }

        final int totalNumberOfConnections = mModel.getTotalNumberOfConnections();

        Log.i(TAG, "onDisconnected: Total number of connections is now " + totalNumberOfConnections);

        if (totalNumberOfConnections == 0) {
            mCheckConnectionsTimer.cancel();
        }
    }

    /**
     * Sends a ping message to all connected peers.
     */
    private synchronized void sendPingToAllPeers() {
        for (BluetoothSocketIoThread socketIoThread : mModel.getOutgoingConnections().values()) {
            socketIoThread.write(PING_PACKAGE);
        }

        for (BluetoothSocketIoThread socketIoThread : mModel.getIncomingConnections().values()) {
            socketIoThread.write(PING_PACKAGE);
        }
    }

    /**
     *
     */
    public static class MyFragmentAdapter extends FragmentPagerAdapter {
        private static final int PEER_LIST_FRAGMENT = 0;
        private static final int LOG_FRAGMENT = 1;

        public MyFragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int index) {
            switch (index){
                case PEER_LIST_FRAGMENT: return new PeerListFragment();
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
