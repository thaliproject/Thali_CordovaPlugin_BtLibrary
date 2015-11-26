/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.PeerDeviceProperties;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils.PeerProperties;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;

/**
 * Watcher for Wi-Fi P2P services (peers) matching the desired service type and which also have a
 * valid identity.
 */
class WifiServiceWatcher {
    /**
     * Service (peer) discovery listener.
     */
    public interface WifiServiceWatcherListener {
        /**
         * Called when the list of discovered P2P devices is changed.
         * @param p2pDeviceList The new list of P2P device discovered.
         */
        void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList);

        /**
         * Called when the list of discovered peers (with the appropriate services) is changed.
         * @param peerDevicePropertiesList The new list of peers (with the appropriate services) available.
         */
        void onServiceListChanged(List<PeerDeviceProperties> peerDevicePropertiesList);

        /**
         * Called when a new peer (with an appropriate service) is discovered.
         * @param peerDeviceProperties The discovered peer device with an appropriate service.
         */
        void onServiceDiscovered(PeerDeviceProperties peerDeviceProperties);
    }

    private static final String TAG = WifiServiceWatcher.class.getName();
    private static final long DISCOVERY_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final long DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long START_DISCOVERY_DELAY_IN_MILLISECONDS = 5000;
    private static final long START_DISCOVERY_TIMER_INTERVAL_IN_MILLISECONDS = DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS;
    private static final long START_SERVICE_DISCOVERY_DELAY_IN_MILLISECONDS = 1000;
    private final Context mContext;
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;
    private final WifiServiceWatcherListener mServiceWatcherListener;
    private final String mServiceType;
    private final CopyOnWriteArrayList<PeerDeviceProperties> mPeerDevicePropertiesList;
    private final CountDownTimer mDiscoveryTimeoutTimer;
    private final CountDownTimer mStartTimer;
    private BroadcastReceiver mPeerDiscoveryBroadcastReceiver = null;
    private PeerListListener mPeerListListener = null;
    private DnsSdServiceResponseListener mDnsSdServiceResponseListener = null;
    private boolean mIsInitialized = false;
    private boolean mIsPeerDiscoveryStarted = false;
    private boolean mIsServiceDiscoveryStarted = false;
    private boolean mIsRestartPending = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param p2pManager The Wi-Fi P2P manager.
     * @param p2pChannel The Wi-Fi P2P channel.
     * @param listener The listener.
     * @param serviceType The service type.
     */
    public WifiServiceWatcher(
            Context context, WifiP2pManager p2pManager, WifiP2pManager.Channel p2pChannel,
            WifiServiceWatcherListener listener, String serviceType) {
        mContext = context;
        mP2pManager = p2pManager;
        mP2pChannel = p2pChannel;
        mServiceWatcherListener = listener;
        mServiceType = serviceType;

        mPeerDevicePropertiesList = new CopyOnWriteArrayList<>();
        mPeerListListener = new MyPeerListListener();
        mDnsSdServiceResponseListener = new MyDnsSdServiceResponseListener();
        mP2pManager.setDnsSdResponseListeners(mP2pChannel, mDnsSdServiceResponseListener, null);

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

        mStartTimer = new CountDownTimer(
                START_DISCOVERY_DELAY_IN_MILLISECONDS,
                START_DISCOVERY_TIMER_INTERVAL_IN_MILLISECONDS) {
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            public void onFinish() {
                Log.i(TAG, "Start timer timeout, starting now...");
                mStartTimer.cancel();
                start();
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
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                mPeerDiscoveryBroadcastReceiver = null;
            }

            if (mPeerDiscoveryBroadcastReceiver != null) {
                mP2pManager.setDnsSdResponseListeners(mP2pChannel, mDnsSdServiceResponseListener, null);
                mIsInitialized = true;
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
     * Starts peer discovery.
     * @return True, if successful or already started. False otherwise.
     */
    public synchronized boolean start() {
        boolean wasSuccessful = false;

        if (mIsInitialized && (!mIsPeerDiscoveryStarted || mIsRestartPending)) {
            Log.i(TAG, "start: Starting peer discovery...");
            mIsRestartPending = false;

            mP2pManager.discoverPeers(mP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mStartTimer.cancel();
                    mIsPeerDiscoveryStarted = true;
                    Log.i(TAG, "Peer discovery started successfully");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to startListeningForIncomingConnections peer discovery, got error code: " + reason);

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
     * Stops peer and service discovery.
     */
    public synchronized void stop() {
        if (mIsPeerDiscoveryStarted) {
            Log.i(TAG, "stop: Stopping peer discovery...");
        }

        stopServiceDiscovery();

        mP2pManager.stopPeerDiscovery(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery stopped successfully");
                mIsPeerDiscoveryStarted = false;

                if (mIsRestartPending) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to shutdown peer discovery, got error code: " + reason);

                if (mIsRestartPending) {
                    mStartTimer.cancel();
                    mStartTimer.start();
                }
            }
        });
    }

    /**
     * Restarts the watcher.
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
     * Starts the service discovery.
     */
    private synchronized void startServiceDiscovery() {
        if (mIsPeerDiscoveryStarted) {
            WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(mServiceType);
            final WifiServiceWatcher thisInstance = this;
            final Handler handler = new Handler();

            mP2pManager.addServiceRequest(mP2pChannel, request, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Service request added successfully");

                    // There is supposedly a possible race-condition bug with the service discovery.
                    // Thus, to avoid it, we are delaying the service discovery initialization here.
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            thisInstance.mP2pManager.discoverServices(
                                    thisInstance.mP2pChannel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.i(TAG, "Service discovery started successfully");
                                    thisInstance.mIsServiceDiscoveryStarted = true;
                                    thisInstance.mPeerDevicePropertiesList.clear();
                                    thisInstance.mServiceWatcherListener.onServiceListChanged(mPeerDevicePropertiesList);
                                }

                                @Override
                                public void onFailure(int reason) {
                                    Log.e(TAG, "Failed to start the service discovery, got error code: " + reason);
                                    thisInstance.stopServiceDiscovery();

                                    // Try again after awhile
                                    thisInstance.mDiscoveryTimeoutTimer.cancel();
                                    thisInstance.mDiscoveryTimeoutTimer.start();
                                }
                            });
                        }
                    }, START_SERVICE_DISCOVERY_DELAY_IN_MILLISECONDS);
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to add a service request, got error code: " + reason);

                    // Try again after awhile
                    mDiscoveryTimeoutTimer.cancel();
                    mDiscoveryTimeoutTimer.start();
                }
            });
        } else {
            Log.e(TAG, "startServiceDiscovery: Invalid state, try calling restart()");
        }
    }

    /**
     * Stops the service discovery.
     */
    private synchronized void stopServiceDiscovery() {
        if (mIsPeerDiscoveryStarted) {
            Log.i(TAG, "stopServiceDiscovery");
        }

        mIsServiceDiscoveryStarted = false;

        mP2pManager.clearServiceRequests(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Service requests cleared successfully");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to clear service requests, got error code: " + reason);
            }
        });
    }

    /**
     * Checks if the list of peer devices contains a device with the given address.
     * @param peerDeviceAddress The address of the peer device to find.
     * @return True, if the list contains a peer device with the given address. False otherwise.
     */
    private synchronized boolean listContainsPeerDevice(String peerDeviceAddress) {
        boolean peerDeviceFound = false;

        for (PeerDeviceProperties peerDeviceProperties : mPeerDevicePropertiesList) {
            if (peerDeviceProperties != null && peerDeviceProperties.deviceAddress.equals(peerDeviceAddress)) {
                peerDeviceFound = true;
                break;
            }
        }

        return peerDeviceFound;
    }

    /**
     * Peer list listener.
     */
    private class MyPeerListListener implements WifiP2pManager.PeerListListener {
        /**
         * Called when peers discovery gets a result.
         * @param p2pDeviceList
         */
        @Override
        public void onPeersAvailable(WifiP2pDeviceList p2pDeviceList) {
            // If we are currently discovering services from the peers (not the peers themselves),
            // we do not want to replace the existing list of peers already discovered.
            if (!mIsServiceDiscoveryStarted) {
                if (mServiceWatcherListener != null) {
                    // We do want to inform the listener even if the given list is empty, since
                    // this will indicate that all the peers are now unavailable.
                    mServiceWatcherListener.onP2pDeviceListChanged(p2pDeviceList.getDeviceList());
                }

                if (p2pDeviceList.getDeviceList().size() > 0) {
                    // tests have shown that if we have multiple peers with services advertising
                    // who disappear same time when we do this, there is a chance that we get stuck
                    // thus, if this happens, in 60 seconds we'll cancel this query and initialize
                    // peer discovery again

                    // Restart after awhile unless we find services
                    mDiscoveryTimeoutTimer.cancel();
                    mDiscoveryTimeoutTimer.start();
                    startServiceDiscovery();
                } // Else we'll just wait
            } else {
                Log.w(TAG, "Got a list of peers, but since we are currently discovering services (not peers), the new list is ignored");
            }
        }
    }

    /**
     * Custom listener for services (peers).
     */
    private class MyDnsSdServiceResponseListener implements WifiP2pManager.DnsSdServiceResponseListener {
        /**
         * Handles found services. Checks if the service type matches ours and that the received
         * identity string is valid. Notifies the listener, when peers are found.
         * @param identityString
         * @param serviceType
         * @param p2pDevice
         */
        @Override
        public void onDnsSdServiceAvailable(String identityString, String serviceType, WifiP2pDevice p2pDevice) {
            Log.i(TAG, "onDnsSdServiceAvailable: Identity: \"" + identityString
                    + "\", service type: \"" + serviceType + "\"");

            if (serviceType.startsWith(mServiceType)) {
                if (!listContainsPeerDevice(p2pDevice.deviceAddress)) {
                    PeerProperties peerProperties = new PeerProperties();
                    PeerDeviceProperties peerDeviceProperties = null;
                    boolean resolvedPropertiesOk = false;

                    try {
                        resolvedPropertiesOk = CommonUtils.getPropertiesFromIdentityString(
                                identityString, peerProperties);
                    } catch (JSONException e) {
                        Log.e(TAG, "onDnsSdServiceAvailable: Failed to resolve peer properties: " + e.getMessage(), e);
                    }

                    if (resolvedPropertiesOk) {
                        Log.i(TAG, "onDnsSdServiceAvailable: Resolved peer properties: " + peerProperties.toString());

                        peerDeviceProperties = new PeerDeviceProperties(
                                peerProperties.id, peerProperties.name, peerProperties.bluetoothAddress,
                                serviceType, p2pDevice.deviceAddress, p2pDevice.deviceName);
                    }

                    if (mServiceWatcherListener != null) {
                        // Inform the listener of this individual peer so that it does not have to
                        // wait for the complete list in case it wants to connect right away.
                        mServiceWatcherListener.onServiceDiscovered(peerDeviceProperties);
                    }

                    mPeerDevicePropertiesList.add(peerDeviceProperties);
                    mServiceWatcherListener.onServiceListChanged(mPeerDevicePropertiesList);
                } else {
                    Log.i(TAG, "onDnsSdServiceAvailable: Peer already exists in the list of peers");
                }
            } else {
                Log.i(TAG, "onDnsSdServiceAvailable: This not our service: " + mServiceType + " != " + serviceType);
            }

            mDiscoveryTimeoutTimer.cancel(); // Cancel the restart
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
                if (!mIsServiceDiscoveryStarted) {
                    mP2pManager.requestPeers(mP2pChannel, mPeerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);

                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.i(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P discovery stopped, restarting...");
                    restart();
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.i(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P discovery started");
                } else {
                    Log.i(TAG, "PeerDiscoveryBroadcastReceiver.onReceive: Wi-Fi P2P discovery state changed to " + state);
                }
            }
        }
    }
}
