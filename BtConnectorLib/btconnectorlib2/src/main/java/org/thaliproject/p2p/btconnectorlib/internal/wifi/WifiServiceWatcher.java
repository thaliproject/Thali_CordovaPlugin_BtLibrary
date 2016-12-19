/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;

/**
 * Watcher for Wi-Fi P2P services (peers) matching the desired service type and which also have a
 * valid identity.
 */
class WifiServiceWatcher {
    public interface Listener {
        /**
         * Called when a new peer (with an appropriate service) is discovered.
         *
         * @param peerProperties The discovered peer device with an appropriate service.
         */
        void onServiceDiscovered(PeerProperties peerProperties);
    }

    private static final String TAG = WifiServiceWatcher.class.getName();
    private static final long START_SERVICE_DISCOVERY_DELAY_IN_MILLISECONDS = 1000;
    private final WifiP2pManager mP2pManager;
    private final WifiP2pManager.Channel mP2pChannel;
    private final Listener mListener;
    private final String mServiceType;
    private DnsSdServiceResponseListener mDnsSdServiceResponseListener = null;
    private boolean mIsRestarting = false;

    /**
     * Constructor.
     *
     * @param listener    The listener.
     * @param p2pManager  The Wi-Fi P2P manager.
     * @param p2pChannel  The Wi-Fi P2P channel.
     * @param serviceType The service type.
     */
    public WifiServiceWatcher(
            Listener listener, WifiP2pManager p2pManager, WifiP2pManager.Channel p2pChannel, String serviceType) {
        mP2pManager = p2pManager;
        mP2pChannel = p2pChannel;
        mListener = listener;
        mServiceType = serviceType;

        mDnsSdServiceResponseListener = new MyDnsSdServiceResponseListener();
        mP2pManager.setDnsSdResponseListeners(mP2pChannel, mDnsSdServiceResponseListener, null);
    }

    /**
     * Starts the service discovery.
     */
    public synchronized void start() {
        mIsRestarting = false;
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
                                        Log.d(TAG, "Service discovery started successfully");
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        Log.e(TAG, "Failed to start the service discovery, got error code: " + reason);

                                        // Uncomment the following to auto-restart
                                        //thisInstance.stop(true); // Restart
                                    }
                                });
                    }
                }, START_SERVICE_DISCOVERY_DELAY_IN_MILLISECONDS);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to add a service request, got error code: " + reason);

                // Uncomment the following to auto-restart
                //thisInstance.stop(true); // Restart
            }
        });
    }

    /**
     * Stops the service discovery.
     *
     * @param restart If true, will restart.
     */
    public synchronized void stop(boolean restart) {
        mIsRestarting = restart;

        mP2pManager.clearServiceRequests(mP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                if (mIsRestarting) {
                    start();
                } else {
                    Log.d(TAG, "Service requests cleared successfully");
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to clear service requests, got error code: " + reason);

                if (mIsRestarting) {
                    start();
                }
            }
        });
    }

    /**
     * Stops the service discovery.
     */
    public void stop() {
        stop(false);
    }

    /**
     * Restarts the service discovery.
     */
    public synchronized void restart() {
        Log.d(TAG, "restart: Restarting...");
        stop(true);
    }

    /**
     * Custom listener for services (peers).
     */
    private class MyDnsSdServiceResponseListener implements WifiP2pManager.DnsSdServiceResponseListener {
        /**
         * Handles found services. Checks if the service type matches ours and that the received
         * identity string is valid. Notifies the listener, when peers are found.
         *
         * @param identityString The identity string.
         * @param serviceType    The service type.
         * @param p2pDevice      The P2P device associated with the service.
         */
        @Override
        public void onDnsSdServiceAvailable(String identityString, String serviceType, WifiP2pDevice p2pDevice) {
            Log.i(TAG, "onDnsSdServiceAvailable: Identity: \"" + identityString
                    + "\", service type: \"" + serviceType + "\"");

            if (serviceType.startsWith(mServiceType)) {
                PeerProperties peerProperties = new PeerProperties();
                boolean resolvedPropertiesOk = false;

                try {
                    resolvedPropertiesOk =
                            AbstractBluetoothConnectivityAgent.getPropertiesFromIdentityString(
                                    identityString, peerProperties);
                } catch (JSONException e) {
                    Log.e(TAG, "onDnsSdServiceAvailable: Failed to resolve peer properties: " + e.getMessage(), e);
                }

                if (resolvedPropertiesOk) {
                    Log.d(TAG, "onDnsSdServiceAvailable: Resolved peer properties: " + peerProperties.toString());
                    peerProperties = new PeerProperties(serviceType, p2pDevice.deviceName, p2pDevice.deviceAddress);
                }
                mListener.onServiceDiscovered(peerProperties);
            } else {
                Log.i(TAG, "onDnsSdServiceAvailable: This not our service: " + mServiceType + " != " + serviceType);
            }
        }
    }
}
