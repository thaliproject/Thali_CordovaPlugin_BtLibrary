/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import java.util.List;

/**
 * The main interface for managing peer discovery.
 */
public class DiscoveryManager
        extends AbstractBluetoothConnectivityAgent
        implements WifiDirectManager.WifiStateListener, WifiPeerDiscoverer.WifiPeerDiscoveryListener {

    public enum DiscoveryManagerState {
        NOT_STARTED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED, // When the chosen peer discovery method is disabled and waiting for it to be enabled to start
        RUNNING
    }

    public enum DiscoveryMode {
        NOT_SET,
        BLE,
        WIFI,
        BLE_AND_WIFI
    }

    public interface DiscoveryManagerListener {
        /**
         * Called when the state of this instance is changed.
         * @param state The new state.
         */
        void onDiscoveryManagerStateChanged(DiscoveryManagerState state);

        /**
         * Called when the list of discovered peers is changed.
         * @param peerPropertiesList The new list of discovered peers.
         */
        void onPeerListChanged(List<PeerProperties> peerPropertiesList);

        /**
         * Called when a new peer is discovered.
         * @param peerProperties The properties of the new peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);

        /**
         * Called when an existing peer is lost (i.e. not available anymore).
         * @param peerProperties The properties of the lost peer.
         */
        void onPeerLost(PeerProperties peerProperties);
    }

    private static final String TAG = DiscoveryManager.class.getName();
    private static final DiscoveryMode DEFAULT_DISCOVERY_MODE = DiscoveryMode.BLE;

    private final Context mContext;
    private final DiscoveryManagerListener mListener;
    private final String mServiceType;
    private final Handler mHandler;

    private WifiDirectManager mWifiDirectManager = null;
    private WifiPeerDiscoverer mWifiPeerDiscoverer = null;
    private String mMyIdentityString = "";
    private DiscoveryManagerState mState = DiscoveryManagerState.NOT_STARTED;
    private DiscoveryMode mDiscoveryMode = DiscoveryMode.NOT_SET;


    /**
     * Constructor.
     * @param context
     * @param listener
     * @param serviceType The service type (both ours and requirement for the peer).
     */
    public DiscoveryManager(Context context, DiscoveryManagerListener listener, String serviceType) {
        super(context);

        mContext = context;
        mListener = listener;
        mServiceType = serviceType;

        mHandler = new Handler(mContext.getMainLooper());
        mWifiDirectManager = WifiDirectManager.getInstance(mContext);

        if (!setDiscoveryMode(DEFAULT_DISCOVERY_MODE)) {
            // Try to fallback to Wi-Fi Direct
            setDiscoveryMode(DiscoveryMode.WIFI);
        }
    }

    public DiscoveryMode getDiscoveryMode() {
        return mDiscoveryMode;
    }

    /**
     * Sets the discovery mode.
     * @param discoveryMode The discovery mode to set.
     * @param forceRestart If true and the discovery was running, will try to do a restart.
     * @return True, if the mode was set. False otherwise (likely because not supported). Note that,
     * if forceRestarts was true, false is also be returned in case the restart fails.
     */
    public boolean setDiscoveryMode(final DiscoveryMode discoveryMode, boolean forceRestart) {
        boolean wasRunning = (mState != DiscoveryManagerState.NOT_STARTED);
        boolean discoveryModeSet = false;

        if (wasRunning && forceRestart) {
            stop();
        }

        if (!wasRunning || forceRestart) {
            switch (discoveryMode) {
                case BLE:
                    if (mBluetoothManager.isBleAdvertisingSupported()) {
                        mDiscoveryMode = discoveryMode;
                        discoveryModeSet = true;
                    }

                    break;

                case WIFI:
                    mDiscoveryMode = discoveryMode;
                    discoveryModeSet = true;
                    break;

                case BLE_AND_WIFI:
                    if (mBluetoothManager.isBleAdvertisingSupported()
                            && mWifiDirectManager.isWifiDirectSupported()) {
                        mDiscoveryMode = discoveryMode;
                        discoveryModeSet = true;
                    }

                    break;
            }

            if (!discoveryModeSet) {
                Log.w(TAG, "setDiscoveryMode: Failed to set discovery mode to " + discoveryMode);
                mDiscoveryMode = DiscoveryMode.NOT_SET;
            } else {
                Log.i(TAG, "setDiscoveryMode: Mode set to " + mDiscoveryMode);
            }
        }

        if (discoveryModeSet && wasRunning && forceRestart) {
            discoveryModeSet = start(mMyPeerId, mMyPeerName);
        }

        return discoveryModeSet;
    }

    /**
     * Sets the discovery mode. Note that this method will fail, if the discovery is currently
     * running.
     * @param discoveryMode The discovery mode to set.
     * @return True, if the mode was set. False otherwise (likely because not supported).
     */
    public boolean setDiscoveryMode(final DiscoveryMode discoveryMode) {
        return setDiscoveryMode(discoveryMode, false);
    }

    /**
     * Starts the peer discovery.
     * @param myPeerId Our peer ID (used for the identity).
     * @param myPeerName Our peer name (used for the identity).
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean start(String myPeerId, String myPeerName) {
        Log.i(TAG, "start: Peer ID: " + myPeerId + ", peer name: " + myPeerName);
        mMyPeerId = myPeerId;
        mMyPeerName = myPeerName;

        switch (mState) {
            case NOT_STARTED:
                if (mDiscoveryMode != DiscoveryMode.NOT_SET) {
                    if (mBluetoothManager.isBluetoothEnabled()) {
                        if (verifyIdentityString()) {
                            boolean bleDiscoveryStarted = false;
                            boolean wifiDiscoveryStarted = false;

                            if (mDiscoveryMode == DiscoveryMode.BLE || mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI) {
                                // Try to start BLE based discovery
                                bleDiscoveryStarted = startBlePeerDiscovery();
                            }

                            if (mDiscoveryMode == DiscoveryMode.WIFI || mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI) {
                                // Try to start Wi-Fi Direct based discovery
                                wifiDiscoveryStarted = startWifiPeerDiscovery();
                            }

                            if ((mDiscoveryMode != DiscoveryMode.BLE_AND_WIFI
                                    && (bleDiscoveryStarted || wifiDiscoveryStarted))
                                || (mDiscoveryMode == DiscoveryMode.BLE && bleDiscoveryStarted)
                                || (mDiscoveryMode == DiscoveryMode.WIFI && wifiDiscoveryStarted)) {
                                Log.i(TAG, "start: OK");
                                setState(DiscoveryManagerState.RUNNING);
                            }
                        } else {
                            Log.e(TAG, "start: Invalid identity string: " + mMyIdentityString);
                        }
                    } else {
                        setState(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    }
                } else {
                    Log.e(TAG, "start: Discovery mode not set, call setDiscoveryMode() to set");
                }

                break;
            case WAITING_FOR_SERVICES_TO_BE_ENABLED:
                Log.w(TAG, "start: Still waiting for Wi-Fi/Bluetooth to be enabled...");
                break;

            case RUNNING:
                Log.d(TAG, "start: Already running, call stop() first in order to restart");
                break;
        }

        return (mState == DiscoveryManagerState.RUNNING);
    }

    /**
     * Stops the peer discovery.
     * Calling this method does nothing, if not running.
     */
    public synchronized void stop() {
        if (mState != DiscoveryManagerState.NOT_STARTED) {
            Log.i(TAG, "stop: Stopping peer discovery...");
        }

        stopBlePeerDiscovery();
        stopWifiPeerDiscovery();
        mWifiDirectManager.release(this);
        mBluetoothManager.release(this);
        setState(DiscoveryManagerState.NOT_STARTED);
    }

    /**
     * Stops/restarts the BLE based peer discovery depending on the given mode.
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        if (mDiscoveryMode == DiscoveryMode.BLE || mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

            if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
                if (mState != DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                    Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, pausing BLE based peer discovery...");
                    stopBlePeerDiscovery();

                    if (mDiscoveryMode == DiscoveryMode.BLE ||
                            (mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI &&
                                    !mWifiDirectManager.isWifiEnabled())) {
                        setState(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    }
                }
            } else {
                if (mState != DiscoveryManagerState.NOT_STARTED
                        && mBluetoothManager.isBluetoothEnabled()) {
                    Log.i(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth enabled, restarting BLE based peer discovery...");
                    start(mMyPeerId, mMyPeerName);
                }
            }
        }
    }

    /**
     * Stops/restarts the Wi-Fi Direct based peer discovery depending on the given state.
     * @param state The new state.
     */
    @Override
    public void onWifiStateChanged(int state) {
        if (mDiscoveryMode == DiscoveryMode.WIFI || mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onWifiStateChanged: State changed to " + state);

            if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                if (mState != DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                    Log.w(TAG, "onWifiStateChanged: Wi-Fi disabled, pausing Wi-Fi Direct based peer discovery...");
                    stopWifiPeerDiscovery();

                    if (mDiscoveryMode == DiscoveryMode.WIFI ||
                            (mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI &&
                                    !mBluetoothManager.isBluetoothEnabled())) {
                        setState(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    }
                }
            } else {
                if ((mDiscoveryMode == DiscoveryMode.WIFI
                        && mState == DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED)
                    || (mDiscoveryMode == DiscoveryMode.BLE_AND_WIFI
                        && mState == DiscoveryManagerState.RUNNING)) {
                    Log.i(TAG, "onWifiStateChanged: Wi-Fi enabled, trying to restart Wi-Fi Direct based peer discovery...");
                    start(mMyPeerId, mMyPeerName);
                }
            }
        }
    }

    /**
     * Does nothing but logs the event.
     * @param isStarted If true, the discovery was started. If false, it was stopped.
     */
    @Override
    public void onIsDiscoveryStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsDiscoveryStartedChanged: " + isStarted);
    }

    /**
     * Forward this event to the listener.
     * @param peerProperties The properties of the discovered peer.
     */
    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered");

        if (mListener != null) {
            final PeerProperties tempPeerProperties = peerProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPeerDiscovered(tempPeerProperties);
                }
            });
        }
    }

    /**
     * Forward this event to the listener.
     * @param peerPropertiesList The new list of available peers.
     */
    @Override
    public void onPeerListChanged(List<PeerProperties> peerPropertiesList) {
        Log.i(TAG, "onPeerListChanged");

        if (mListener != null) {
            final List<PeerProperties> tempPeerPropertiesList = peerPropertiesList;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPeerListChanged(tempPeerPropertiesList);
                }
            });
        }
    }

    /**
     * Tries to start the BLE based peer discovery.
     * @return True, if started (or already running). False otherwise.
     */
    private synchronized boolean startBlePeerDiscovery() {
        boolean started = false;

        if (mBluetoothManager.bind(this)) {
            // TODO: Implement
        }

        return started;
    }

    /**
     * Stops the BLE based peer discovery.
     */
    private synchronized void stopBlePeerDiscovery() {
        // TODO: Implement
    }

    /**
     * Tries to start the Wi-Fi Direct based peer discovery.
     * Note that this method does not validate the current state nor the identity string.
     * @return True, if started (or already running). False otherwise.
     */
    private synchronized boolean startWifiPeerDiscovery() {
        boolean started = false;

        if (mWifiDirectManager.bind(this)) {
            if (mWifiPeerDiscoverer == null) {
                WifiP2pManager p2pManager = mWifiDirectManager.getWifiP2pManager();
                WifiP2pManager.Channel channel = mWifiDirectManager.getWifiP2pChannel();

                if (p2pManager != null && channel != null) {
                    mWifiPeerDiscoverer = new WifiPeerDiscoverer(
                            mContext, channel, p2pManager, this, mServiceType, mMyIdentityString);

                    mWifiPeerDiscoverer.start();
                } else {
                    Log.e(TAG, "startWifiPeerDiscovery: Failed to get Wi-Fi P2P manager or channel");
                }
            }

            if (mWifiPeerDiscoverer != null) {
                started = true;
                Log.d(TAG, "startWifiPeerDiscovery: Wi-Fi Direct OK");
            }
        } else {
            Log.e(TAG, "startWifiPeerDiscovery: Failed to start, this may indicate that Wi-Fi Direct is not supported on this device");
        }

        return started;
    }

    /**
     * Stops the Wi-Fi Direct based peer discovery.
     */
    private synchronized void stopWifiPeerDiscovery() {
        if (mWifiPeerDiscoverer != null) {
            mWifiPeerDiscoverer.stop();
            mWifiPeerDiscoverer = null;
            Log.i(TAG, "stopWifiPeerDiscovery: Stopped");
        }
    }

    /**
     * Sets the state of this instance and notifies the listener.
     * @param state The new state.
     */
    private synchronized void setState(DiscoveryManagerState state) {
        if (mState != state) {
            Log.d(TAG, "setState: " + state.toString());
            mState = state;

            if (mListener != null) {
                final DiscoveryManagerState tempState = mState;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onDiscoveryManagerStateChanged(tempState);
                    }
                });
            }
        }
    }
}
