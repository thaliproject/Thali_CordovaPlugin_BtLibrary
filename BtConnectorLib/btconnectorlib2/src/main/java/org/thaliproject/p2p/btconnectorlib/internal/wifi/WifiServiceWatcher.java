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
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.PeerDevice;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;


/**
 *
 */
class WifiServiceWatcher {
    /**
     *
     */
    public interface WifiServiceWatcherListener {
        void onNewListOfPeersAvailable(Collection<WifiP2pDevice> p2pDeviceList);
        void gotServicesList(List<PeerDevice> list);
        void foundService(PeerDevice item);
    }

    private enum WifiServiceWatcherState {
        NOT_INITIALIZED,
        INITIALIZED,
        DISCOVERING_PEERS,
        DISCOVERING_SERVICES
    }

    private static final String TAG = WifiServiceWatcher.class.getName();
    private static final long SERVICE_DISCOVERY_TIMEOUT_IN_MILLISECONDS = 600000;
    private static final long SERVICE_DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 10000;
    private final Context mContext;
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;
    private final WifiServiceWatcherListener mServiceWatcherListener;
    private final String mServiceType;
    private final CopyOnWriteArrayList<PeerDevice> mPeerDeviceList;
    private final CountDownTimer peerDiscoveryTimer;
    private BroadcastReceiver mPeerDiscoveryBroadcastReceiver = null;
    private PeerListListener mPeerListListener = null;
    private DnsSdServiceResponseListener mDnsSdServiceResponseListener = null;
    private WifiServiceWatcherState mState = WifiServiceWatcherState.NOT_INITIALIZED;


    private final CountDownTimer mServiceDiscoveryTimeoutTimer = new CountDownTimer(SERVICE_DISCOVERY_TIMEOUT_IN_MILLISECONDS, SERVICE_DISCOVERY_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
        public void onTick(long millisUntilFinished) {
            // Not used
        }

        public void onFinish() {
            stopServiceDiscovery();
            startPeerDiscovery();
        }
    };

    /**
     *
     * @param context
     * @param p2pManager
     * @param p2pChannel
     * @param listener
     * @param serviceType
     */
    public WifiServiceWatcher(
            Context context, WifiP2pManager p2pManager, WifiP2pManager.Channel p2pChannel,
            WifiServiceWatcherListener listener, String serviceType) {
        mContext = context;
        mP2pManager = p2pManager;
        mP2pChannel = p2pChannel;
        mServiceWatcherListener = listener;
        mServiceType = serviceType;

        mPeerDeviceList = new CopyOnWriteArrayList<>();
        mPeerListListener = new MyPeerListener();
        mDnsSdServiceResponseListener = new MyDnsSdServiceResponseListener();
        mP2pManager.setDnsSdResponseListeners(mP2pChannel, mDnsSdServiceResponseListener, null);

        Random ran = new Random(System.currentTimeMillis());

        // if this 4 seconds minimum, then we see this
        // triggering before we got all services
        long millisInFuture = 5000 + (ran.nextInt(5000));

        Log.i("", "peerDiscoveryTimer timeout value:" + millisInFuture);

        peerDiscoveryTimer = new CountDownTimer(millisInFuture, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }
            public void onFinish() {
                setState(WifiServiceWatcherState.NOT_INITIALIZED);
                if (mServiceWatcherListener == null) {
                    startPeerDiscovery();
                    return;
                }

                mServiceWatcherListener.gotServicesList(mPeerDeviceList);
                //cancel all other counters, and initialize our wait cycle
                mServiceDiscoveryTimeoutTimer.cancel();
                peerDiscoveryTimer.cancel();
                stopServiceDiscovery();
                startPeerDiscovery();
            }
        };
    }

    /**
     *
     * @return
     */
    public synchronized boolean initialize() {
        if (mState == WifiServiceWatcherState.NOT_INITIALIZED) {
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
                setState(WifiServiceWatcherState.INITIALIZED);
            }
        }

        return (mState != WifiServiceWatcherState.NOT_INITIALIZED);
    }

    /**
     *
     */
    public synchronized void deinitialize() {
        if (mPeerDiscoveryBroadcastReceiver != null)
        {
            try {
                mContext.unregisterReceiver(mPeerDiscoveryBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "deinitialize: Failed to unregister the broadcast receiver: " + e.getMessage(), e);
            }

            mPeerDiscoveryBroadcastReceiver = null;
        }

        mServiceDiscoveryTimeoutTimer.cancel();
        peerDiscoveryTimer.cancel();
        stopServiceDiscovery();
        stopPeerDiscovery();

        setState(WifiServiceWatcherState.NOT_INITIALIZED);
    }

    /**
     *
     */
    private synchronized void startPeerDiscovery() {
        if (mState != WifiServiceWatcherState.INITIALIZED) {
            Log.i(TAG, "startPeerDiscovery");

            mP2pManager.discoverPeers(mP2pChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                    setState(WifiServiceWatcherState.DISCOVERING_PEERS);
                    Log.i(TAG, "Peer discovery started successfully");
                }

                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to startListeningForIncomingConnections peer discovery, got error code: " + reason);
                    setState(WifiServiceWatcherState.INITIALIZED);

                    // Lets try again after a 1 minute timeout
                    mServiceDiscoveryTimeoutTimer.start();
                }
            });
        } else {
            Log.e(TAG, "startPeerDiscovery: Cannot startListeningForIncomingConnections peer discovery due to invalid state (" + mState.toString() + ")");
        }
    }

    /**
     *
     */
    private synchronized void stopPeerDiscovery() {
        Log.i(TAG, "stopPeerDiscovery");

        mP2pManager.stopPeerDiscovery(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery stopped successfully");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to shutdown peer discovery, got error code: " + reason);
            }
        });
    }

    /**
     *
     */
    private synchronized void startServiceDiscovery() {
        if (mState == WifiServiceWatcherState.DISCOVERING_PEERS) {
            setState(WifiServiceWatcherState.DISCOVERING_SERVICES);
            WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(mServiceType);
            final Handler handler = new Handler();

            mP2pManager.addServiceRequest(mP2pChannel, request, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i("", "Added service request");
                    handler.postDelayed(new Runnable() {
                        //There are supposedly a possible race-condition bug with the service discovery
                        // thus to avoid it, we are delaying the service discovery initialize here
                        public void run() {
                            mP2pManager.discoverServices(mP2pChannel, new WifiP2pManager.ActionListener() {
                                public void onSuccess() {
                                    Log.i(TAG, "Service discovery started successfully");
                                    mPeerDeviceList.clear();
                                    Log.i("", "Started service discovery");
                                    setState(WifiServiceWatcherState.DISCOVERING_SERVICES);
                                }

                                public void onFailure(int reason) {
                                    stopServiceDiscovery();
                                    setState(WifiServiceWatcherState.NOT_INITIALIZED);
                                    Log.i("", "Starting service discovery failed, error code " + reason);
                                    //lets try again after 1 minute time-out !
                                    mServiceDiscoveryTimeoutTimer.start();
                                }
                            });
                        }
                    }, 1000);
                }

                @Override
                public void onFailure(int reason) {
                    setState(WifiServiceWatcherState.NOT_INITIALIZED);
                    Log.i("", "Adding service request failed, error code " + reason);
                    //lets try again after 1 minute time-out !
                    mServiceDiscoveryTimeoutTimer.start();
                }
            });
        } else {
            Log.e(TAG, "startServiceDiscovery: Cannot startListeningForIncomingConnections service discovery due to invalid state (" + mState.toString() + ")");
        }
    }

    /**
     *
     */
    private void stopServiceDiscovery() {
        if (mState == WifiServiceWatcherState.DISCOVERING_SERVICES) {
            Log.i(TAG, "stopServiceDiscovery");

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
    }

    /**
     * Sets the internal state.
     * @param newState The new state.
     */
    private synchronized void setState(WifiServiceWatcherState newState) {
        if (mState != newState) {
            mState = newState;
        }
    }

    private class MyPeerListener implements WifiP2pManager.PeerListListener {
        /**
         * Called when peers discovery gets a result.
         * @param p2pDeviceList
         */
        @Override
        public void onPeersAvailable(WifiP2pDeviceList p2pDeviceList) {
            // this is called still multiple time time-to-time
            // so need to make sure we only make one service discovery call
            if (mState != WifiServiceWatcherState.DISCOVERING_SERVICES) {
                if (mServiceWatcherListener != null) {
                    // We do want to inform the listener even if the given list is empty, since
                    // this will indicate that all the peers are now unavailable.
                    mServiceWatcherListener.onNewListOfPeersAvailable(p2pDeviceList.getDeviceList());
                }
                if (p2pDeviceList.getDeviceList().size() > 0) {
                    //tests have shown that if we have multiple peers with services advertising
                    // who disappear same time when we do this, there is a chance that we get stuck
                    // thus, if this happens, in 60 seconds we'll cancel this query and initialize peer discovery again
                    mServiceDiscoveryTimeoutTimer.start();
                    startServiceDiscovery();
                } // else we'll just wait
            } else {
                Log.w(TAG, "Got a list of peers, but since we are currently discovering services (not peers), the new list is ignored");
            }
        }
    }

    private class MyDnsSdServiceResponseListener implements WifiP2pManager.DnsSdServiceResponseListener {
        /**
         *
         * @param instanceName
         * @param serviceType
         * @param device
         */
        @Override
        public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {

            Log.i("", "Found Service, :" + instanceName + ", type" + serviceType + ":");

            if (serviceType.startsWith(mServiceType)) {
                boolean addService = true;

                for (PeerDevice item : mPeerDeviceList) {
                    if (item != null && item.deviceAddress.equals(device.deviceAddress)) {
                        addService = false;
                    }
                }
                if (addService) {
                    try {
                        JSONObject jObject = new JSONObject(instanceName);

                        String peerId = jObject.getString(CommonUtils.JSON_ID_PEER_ID);
                        String peerName = jObject.getString(CommonUtils.JSON_ID_PEER_NAME);
                        String peerAddress = jObject.getString(CommonUtils.JSON_ID_BLUETOOTH_ADDRESS);

                        Log.i("", "JsonLine: " + instanceName + " -- peerIdentifier:" + peerId + ", name: " + peerName + ", peerAddress: " + peerAddress);

                        PeerDevice tmpSrv = new PeerDevice(peerId, peerName, peerAddress, serviceType, device.deviceAddress, device.deviceName);
                        if (mServiceWatcherListener != null) {
                            //this is to inform right away that we have found a peer, so we don't need to wait for the whole list before connecting
                            mServiceWatcherListener.foundService(tmpSrv);
                        }
                        mPeerDeviceList.add(tmpSrv);

                    } catch (JSONException e) {
                        Log.i("", "checking instance failed , :" + e.toString());
                    }
                }

            } else {
                Log.i("", "Not our Service, :" + mServiceType + "!=" + serviceType + ":");
            }

            mServiceDiscoveryTimeoutTimer.cancel();
            peerDiscoveryTimer.cancel();
            peerDiscoveryTimer.start();
        }
    }

    private class PeerDiscoveryBroadcastReceiver extends BroadcastReceiver {
        /**
         *
         * @param context
         * @param intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (mState != WifiServiceWatcherState.DISCOVERING_SERVICES) {
                    mP2pManager.requestPeers(mP2pChannel, mPeerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);

                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.i(TAG, "ServiceSearcherReceiver.onReceive: Wi-Fi P2P discovery stopped, restarting...");
                    startPeerDiscovery();
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.i(TAG, "ServiceSearcherReceiver.onReceive: Wi-Fi P2P discovery started");
                } else {
                    Log.i(TAG, "ServiceSearcherReceiver.onReceive: Wi-Fi P2P discovery state changed to " + state);
                }
            }
        }
    }
}
