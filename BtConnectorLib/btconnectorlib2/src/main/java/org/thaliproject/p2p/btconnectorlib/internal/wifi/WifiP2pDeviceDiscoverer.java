/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.util.Log;
import java.util.Collection;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;

/**
 *
 */
class WifiP2pDeviceDiscoverer {
    /**
     * P2P device discovery listener.
     */
    public interface Listener {
        /**
         * Called when the list of discovered P2P devices is changed.
         * @param p2pDeviceList The new list of P2P device discovered.
         */
        void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList);
    }

    private static final String TAG = WifiP2pDeviceDiscoverer.class.getName();
    private static final long DISCOVERY_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final long DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = DISCOVERY_TIMEOUT_IN_MILLISECONDS;
    private static final long START_DISCOVERY_DELAY_IN_MILLISECONDS = 5000;
    private static final long START_DISCOVERY_TIMER_INTERVAL_IN_MILLISECONDS = DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS;
    private final Context mContext;
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;
    private final Listener mListener;
    private final CountDownTimer mDiscoveryTimeoutTimer;
    private final CountDownTimer mStartTimer;
    private BroadcastReceiver mPeerDiscoveryBroadcastReceiver = null;
    private WifiP2pManager.PeerListListener mPeerListListener = null;
    private boolean mIsInitialized = false;
    private boolean mIsPeerDiscoveryStarted = false;
    private boolean mIsRestartPending = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param context The application context.
     * @param p2pManager The Wi-Fi P2P manager.
     * @param p2pChannel The Wi-Fi P2P channel.
     */
    public WifiP2pDeviceDiscoverer(
            Listener listener, Context context, WifiP2pManager p2pManager, WifiP2pManager.Channel p2pChannel) {
        mContext = context;
        mP2pManager = p2pManager;
        mP2pChannel = p2pChannel;
        mListener = listener;

        mPeerListListener = new MyPeerListListener();

        mDiscoveryTimeoutTimer = new CountDownTimer(
                DISCOVERY_TIMEOUT_IN_MILLISECONDS,
                DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            public void onFinish() {
                Log.i(TAG, "Got discovery timeout, restarting...");
                mDiscoveryTimeoutTimer.cancel();
                restart();
            }
        };

        final WifiP2pDeviceDiscoverer thisInstance = this;

        mStartTimer = new CountDownTimer(
                START_DISCOVERY_DELAY_IN_MILLISECONDS,
                START_DISCOVERY_TIMER_INTERVAL_IN_MILLISECONDS) {
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            public void onFinish() {
                Log.i(TAG, "Start timer timeout, starting now...");
                mStartTimer.cancel();
                thisInstance.start();
            }
        };
    }

    /**
     * Initializes this instance. Creates and registers a broadcast receiver for P2P discovery
     * events.
     * @return True, if the successfully initialized (or was already initialized). False otherwise.
     */
    public synchronized boolean initialize() {
        if (!mIsInitialized) {
            mPeerDiscoveryBroadcastReceiver = new PeerDiscoveryBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);

            try {
                mContext.registerReceiver(mPeerDiscoveryBroadcastReceiver, filter);
                mIsInitialized = true;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                mPeerDiscoveryBroadcastReceiver = null;
            }
        }

        return mIsInitialized;
    }

    /**
     * De-initializes this instance. Unregisters the broadcast receiver.
     */
    public synchronized void deinitialize() {
        if (mPeerDiscoveryBroadcastReceiver != null) {
            try {
                mContext.unregisterReceiver(mPeerDiscoveryBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "deinitialize: Failed to unregister the broadcast receiver: " + e.getMessage(), e);
            }

            mPeerDiscoveryBroadcastReceiver = null;
        }

        mDiscoveryTimeoutTimer.cancel();
        stop();

        mIsInitialized = false;
    }

    /**
     * Starts the P2P device discovery.
     * @return True, if successful or already started. False otherwise.
     */
    public synchronized boolean start() {
        boolean wasSuccessful = false;

        if (mIsInitialized && (!mIsPeerDiscoveryStarted || mIsRestartPending)) {
            Log.i(TAG, "start: Starting P2P device discovery...");
            mIsRestartPending = false;

            mP2pManager.discoverPeers(mP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mStartTimer.cancel();
                    mIsPeerDiscoveryStarted = true;
                    Log.d(TAG, "P2P device discovery started successfully");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to P2P device discovery, got error code: " + reason);

                    // Try again after awhile
                    mDiscoveryTimeoutTimer.cancel();
                    mDiscoveryTimeoutTimer.start();
                }
            });

            wasSuccessful = true;
        } else {
            if (!mIsInitialized) {
                Log.e(TAG, "start: Cannot start, because not initialized");
            } else if (mIsPeerDiscoveryStarted) {
                Log.w(TAG, "start: Already started");
            }
        }

        return (wasSuccessful || mIsPeerDiscoveryStarted);
    }

    /**
     * Stops the P2P device discovery.
     */
    public synchronized void stop() {
        if (mIsPeerDiscoveryStarted) {
            Log.i(TAG, "stop: Stopping P2P device discovery...");
        }

        mP2pManager.stopPeerDiscovery(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2P device discovery stopped successfully");
                mIsPeerDiscoveryStarted = false;

                if (mIsRestartPending) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to shutdown P2P device discovery, got error code: " + reason);

                if (mIsRestartPending) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }
        });
    }

    /**
     * Restarts the P2P device discovery.
     */
    public synchronized void restart() {
        if (mIsInitialized) {
            Log.i(TAG, "restart: Restarting...");
            mDiscoveryTimeoutTimer.cancel();
            mIsRestartPending = true;
            stop();
        } else {
            Log.e(TAG, "restart: Cannot restart, because not initialized");
        }
    }

    /**
     * Peer (P2P device) list listener.
     */
    private class MyPeerListListener implements WifiP2pManager.PeerListListener {
        /**
         * Called when peers discovery gets a result.
         * @param p2pDeviceList
         */
        @Override
        public void onPeersAvailable(WifiP2pDeviceList p2pDeviceList) {
            if (mListener != null) {
                mListener.onP2pDeviceListChanged(p2pDeviceList.getDeviceList());
            }

            // Restart the timeout timer
            mDiscoveryTimeoutTimer.cancel();
            mDiscoveryTimeoutTimer.start();
        }
    }

    /**
     * Broadcast receiver for WIFI P2P state changes.
     */
    private class PeerDiscoveryBroadcastReceiver extends BroadcastReceiver {
        /**
         * Handles the P2P state changes.
         * @param context
         * @param intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                mP2pManager.requestPeers(mP2pChannel, mPeerListListener);
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);

                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery stopped, restarting...");
                    restart();
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery started");
                } else {
                    Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery state changed to " + state);
                }
            }
        }
    }
}
