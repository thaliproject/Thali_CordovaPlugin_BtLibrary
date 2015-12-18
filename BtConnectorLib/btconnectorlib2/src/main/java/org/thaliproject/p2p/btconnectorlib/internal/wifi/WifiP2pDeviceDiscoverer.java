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
 * Discoverer for Wi-Fi P2P devices.
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

    private enum State {
        NOT_INITIALIZED,
        INITIALIZED, // Initialized, but not started
        STARTED,
        RESTARTING
    };

    private static final String TAG = WifiP2pDeviceDiscoverer.class.getName();
    private static final long DISCOVERY_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final long DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = DISCOVERY_TIMEOUT_IN_MILLISECONDS;
    private static final long START_DISCOVERY_DELAY_IN_MILLISECONDS = 5000;
    private static final long START_DISCOVERY_TIMER_INTERVAL_IN_MILLISECONDS = DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS;
    private final Context mContext;
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;
    private final Listener mListener;
    private final CountDownTimer mRestartDiscoveryTimer;
    private final CountDownTimer mStartTimer;
    private BroadcastReceiver mPeerDiscoveryBroadcastReceiver = null;
    private WifiP2pManager.PeerListListener mPeerListListener = null;
    private State mState = State.NOT_INITIALIZED;
    private boolean mIsStopping = false;

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

        mRestartDiscoveryTimer = new CountDownTimer(
                DISCOVERY_TIMEOUT_IN_MILLISECONDS,
                DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            public void onFinish() {
                mRestartDiscoveryTimer.cancel();

                if (!mIsStopping) {
                    Log.i(TAG, "Got discovery timeout, restarting...");
                    restart();
                }
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
                mStartTimer.cancel();

                if (!mIsStopping) {
                    Log.i(TAG, "Start timer timeout, starting now...");
                    thisInstance.start();
                }
            }
        };
    }

    /**
     * Initializes this instance. Creates and registers a broadcast receiver for P2P discovery
     * events.
     * @return True, if the successfully initialized (or was already initialized). False otherwise.
     */
    public synchronized boolean initialize() {
        if (mState == State.NOT_INITIALIZED) {
            mPeerDiscoveryBroadcastReceiver = new PeerDiscoveryBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);

            try {
                mContext.registerReceiver(mPeerDiscoveryBroadcastReceiver, filter);
                setState(State.INITIALIZED);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                mPeerDiscoveryBroadcastReceiver = null;
            }
        }

        return (mState != State.NOT_INITIALIZED);
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

        stop();
        setState(State.NOT_INITIALIZED);
    }

    /**
     * Starts the P2P device discovery.
     * @return True, if successful or already started. False otherwise.
     */
    public synchronized boolean start() {
        boolean wasSuccessful = false;

        if (!mIsStopping && (mState == State.INITIALIZED || mState == State.RESTARTING)) {
            if (mState != State.RESTARTING) {
                Log.i(TAG, "start: Starting P2P device discovery...");
            }

            mP2pManager.discoverPeers(mP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "P2P device discovery started successfully");
                    mStartTimer.cancel();
                    setState(State.STARTED);
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to P2P device discovery, got error code: " + reason);
                    setState(State.INITIALIZED);

                    mStartTimer.cancel();
                    mRestartDiscoveryTimer.cancel();

                    if (!mIsStopping) {
                        // Try again after awhile
                        mRestartDiscoveryTimer.start();
                    }
                }
            });

            wasSuccessful = true;
        } else {
            if (mState == State.NOT_INITIALIZED) {
                Log.e(TAG, "start: Cannot start, because not initialized");
            } else if (mState == State.STARTED) {
                Log.w(TAG, "start: Already started");
            }
        }

        return (wasSuccessful || mState == State.STARTED);
    }

    /**
     * Stops or tries to restart the P2P device discovery.
     * @param restart If true, will try to restart once stopped. If false, does only stop.
     */
    private synchronized void stop(boolean restart) {
        if (mState != State.NOT_INITIALIZED && restart) {
            setState(State.RESTARTING);
        } else {
            Log.i(TAG, "stop: Stopping P2P device discovery...");
            mIsStopping = true;
        }

        mStartTimer.cancel();
        mRestartDiscoveryTimer.cancel();

        mP2pManager.stopPeerDiscovery(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                if (mState != State.RESTARTING) {
                    Log.d(TAG, "P2P device discovery stopped successfully");
                }

                mIsStopping = false;

                if (mState == State.RESTARTING) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to shutdown P2P device discovery, got error code: " + reason);
                mIsStopping = false;

                if (mState == State.RESTARTING) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }
        });
    }

    /**
     * Stops the P2P device discovery.
     */
    public synchronized void stop() {
        stop(false);
    }

    /**
     * Restarts the P2P device discovery.
     */
    public synchronized void restart() {
        if (mState == State.NOT_INITIALIZED) {
            Log.e(TAG, "restart: Cannot restart, because not initialized");
        } else if (mState == State.RESTARTING) {
            // Do nothing
        } else {
            Log.i(TAG, "restart: Restarting...");
            stop(true);
        }
    }

    /**
     * Sets the state.
     * @param state The new state.
     */
    private synchronized void setState(State state) {
        if (mState != state) {
            Log.d(TAG, "setState: " + state);
            mState = state;
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

            if (!mIsStopping) {
                // Restart the timeout timer
                mRestartDiscoveryTimer.cancel();
                mRestartDiscoveryTimer.start();
            }
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
                    if (!mIsStopping && mState != State.NOT_INITIALIZED && mState != State.RESTARTING) {
                        Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery stopped, restarting...");
                        restart();
                    } else {
                        Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery stopped");
                    }
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery started");
                } else {
                    Log.d(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P device discovery state changed to " + state);
                }
            }
        }
    }
}
