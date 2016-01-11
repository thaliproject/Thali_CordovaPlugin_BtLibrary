/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app;

import android.content.Context;
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
import org.thaliproject.nativetest.app.fragments.LogFragment;
import org.thaliproject.nativetest.app.fragments.PeerListFragment;
import org.thaliproject.nativetest.app.fragments.SettingsFragment;
import org.thaliproject.nativetest.app.fragments.TestsFragment;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.nativetest.app.slidingtabs.SlidingTabLayout;
import org.thaliproject.nativetest.app.test.TestListener;
import org.thaliproject.nativetest.app.utils.MenuUtils;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

public class MainActivity
        extends AppCompatActivity
        implements PeerListFragment.Listener, TestListener {

    private static final String TAG = MainActivity.class.getName();

    private static MainActivity mThisInstance = null;
    private static Context mContext = null;

    private ConnectionEngine mConnectionEngine = null;
    private ConnectionEngine mTestEngine = null;

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
    private PeerListFragment mPeerListFragment = null;
    private LogFragment mLogFragment = null;
    private SettingsFragment mSettingsFragment = null;
    private TestsFragment mTestsFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mThisInstance = this;
        mContext = getApplicationContext();

        if (mConnectionEngine == null) {
            mConnectionEngine = new ConnectionEngine(mContext);
            mConnectionEngine.bindSettings();
            mTestEngine = new TestEngine(mContext, this);
        }

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();

        mMyFragmentAdapter = new MyFragmentAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager)findViewById(R.id.pager);
        mViewPager.setAdapter(mMyFragmentAdapter);

        mSlidingTabLayout = (SlidingTabLayout)findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);

        mPeerListFragment = new PeerListFragment();
        mPeerListFragment.setListener(this);

        mLogFragment = new LogFragment();

        mSettingsFragment = new SettingsFragment();

        mTestsFragment = new TestsFragment();
        mTestsFragment.setTestEngine((TestEngine) mTestEngine);

        mConnectionEngine.start();
    }

    /**
     * Updates the options menu.
     */
    public static void updateOptionsMenu() {
        if (mThisInstance != null) {
            Log.d(TAG, "updateOptionsMenu");
            mThisInstance.invalidateOptionsMenu();
        }
    }

    /**
     * Displays a toast with the given message.
     * @param message The message to show.
     */
    public static void showToast(final String message) {
        final Context context = mContext;

        if (context != null) {
            Handler handler = new Handler(mContext.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    CharSequence text = message;
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        mConnectionEngine.stop();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuUtils.PeerMenuItemsAvailability availability =
                MenuUtils.resolvePeerMenuItemsAvailability(
                        mPeerListFragment.getSelectedPeerProperties(), PeerAndConnectionModel.getInstance());

        MenuItem connectMenuItem = menu.getItem(0);
        MenuItem sendDataMenuItem = menu.getItem(1);
        MenuItem disconnectMenuItem = menu.getItem(2);
        MenuItem killAllConnectionsMenuItem = menu.getItem(3);

        connectMenuItem.setVisible(availability.connectMenuItemAvailable);
        connectMenuItem.setEnabled(availability.connectMenuItemAvailable);
        sendDataMenuItem.setVisible(availability.sendDataMenuItemAvailable);
        sendDataMenuItem.setEnabled(availability.sendDataMenuItemAvailable);
        disconnectMenuItem.setEnabled(availability.disconnectMenuItemAvailable);
        killAllConnectionsMenuItem.setEnabled(availability.killAllConnectionsMenuItemAvailable);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        boolean wasConsumed = false;
        PeerProperties peerProperties = mPeerListFragment.getSelectedPeerProperties();
        PeerAndConnectionModel model = PeerAndConnectionModel.getInstance();

        switch (id) {
            case R.id.action_connect:
                onConnectRequest(peerProperties); // Has a null check
                wasConsumed = true;
                break;
            case R.id.action_disconnect:
                if (peerProperties != null) {
                    if (model.closeConnection(peerProperties)) {
                        wasConsumed = true;
                    }
                }

                break;
            case R.id.action_send_data:
                onSendDataRequest(peerProperties); // Has a null check
                wasConsumed = true;
                break;
            case R.id.action_kill_all_connections:
                model.closeAllConnections();
                wasConsumed = true;
                break;
        }

        return wasConsumed || super.onOptionsItemSelected(item);
    }


    @Override
    public void onPeerSelected(PeerProperties peerProperties) {
        updateOptionsMenu();
    }

    @Override
    public void onConnectRequest(PeerProperties peerProperties) {
        mConnectionEngine.connect(peerProperties);
    }

    @Override
    public void onSendDataRequest(PeerProperties peerProperties) {
        mConnectionEngine.startSendingData(peerProperties);
    }

    @Override
    public void onTestFinished(String testName, float successRate, String results) {
        showToast("Test \"" + testName + "\" finished with success rate of " + Math.round(successRate * 100) + " %");
    }

    /**
     * The fragment adapter for tabs.
     */
    public class MyFragmentAdapter extends FragmentPagerAdapter {
        private static final int PEER_LIST_FRAGMENT = 0;
        private static final int LOG_FRAGMENT = 1;
        private static final int SETTINGS_FRAGMENT = 2;
        private static final int TESTS_FRAGMENT = 3;

        public MyFragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int index) {
            switch (index){
                case PEER_LIST_FRAGMENT: return mPeerListFragment;
                case LOG_FRAGMENT: return mLogFragment;
                case SETTINGS_FRAGMENT: return mSettingsFragment;
                case TESTS_FRAGMENT: return mTestsFragment;
            }

            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case PEER_LIST_FRAGMENT: return "Peers";
                case LOG_FRAGMENT: return "Log";
                case SETTINGS_FRAGMENT: return "Settings";
                case TESTS_FRAGMENT: return "Tests";
            }

            return super.getPageTitle(position);
        }

        @Override
        public int getCount() {
            return 4;
        }
    }
}
