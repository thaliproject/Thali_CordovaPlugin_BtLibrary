package org.thaliproject.nativesample.app;

import java.io.IOException;
import java.util.*;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

public class MainActivity
        extends AppCompatActivity
        implements
            ConnectionManager.ConnectionManagerListener,
            DiscoveryManager.DiscoveryManagerListener,
            ActionBar.TabListener {

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
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private ArrayList<PeerProperties> mPeers = new ArrayList<PeerProperties>();
    private HashMap<String, BluetoothSocket> mIncomingConnections = new HashMap<String, BluetoothSocket>();
    private HashMap<String, BluetoothSocket> mOutgoingConnections = new HashMap<String, BluetoothSocket>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        Context context = this.getApplicationContext();
        mConnectionManager = new ConnectionManager(context, this, SERVICE_UUID, SERVICE_NAME);
        mDiscoveryManager = new DiscoveryManager(context, this, SERVICE_TYPE);

        String peerName = Build.PRODUCT; // Use product (device) name as the peer name
        mConnectionManager.start(peerName);
        mDiscoveryManager.setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        mDiscoveryManager.start(peerName);
    }

    @Override
    public void onDestroy() {
        for (BluetoothSocket socket : mOutgoingConnections.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "onDestroy: Failed to close a socket of an outgoing connection: " + e.getMessage(), e);
            }
        }

        mOutgoingConnections.clear();

        for (BluetoothSocket socket : mIncomingConnections.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "onDestroy: Failed to close a socket of an incoming connection: " + e.getMessage(), e);
            }
        }

        mIncomingConnections.clear();

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
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState connectionManagerState) {

    }

    @Override
    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
        Log.i(TAG, "onConnected: " + (isIncoming ? "Incoming" : "Outgoing") + " connection: " + peerProperties.toString());

        if (isIncoming) {
            mIncomingConnections.put(peerProperties.getId(), bluetoothSocket);
        } else {
            mOutgoingConnections.put(peerProperties.getId(), bluetoothSocket);
        }

        Log.i(TAG, "onConnected: Total number of connections is now "
                + (mOutgoingConnections.size() + mIncomingConnections.size()));
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
        if (!mPeers.contains(peerProperties)) {
            mPeers.add(peerProperties);
            Log.i(TAG, "onPeerDiscovered: Peer " + peerProperties.toString() + " added to list");
            mConnectionManager.connect(peerProperties);
        } else {
            Log.i(TAG, "onPeerDiscovered: Peer " + peerProperties.toString() + " already in the list");
        }
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {

    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
                case 2:
                    return getString(R.string.title_section3).toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }
}
