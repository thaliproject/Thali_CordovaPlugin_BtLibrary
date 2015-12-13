/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private static final long DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS = 30000;

    private final Context mContext;
    private final DiscoveryManagerListener mListener;
    private final String mServiceType;
    private final Handler mHandler;
    private final HashMap<Timestamp, PeerProperties> mDiscoveredPeers = new HashMap<>();
    private WifiDirectManager mWifiDirectManager = null;
    private WifiPeerDiscoverer mWifiPeerDiscoverer = null;
    private CountDownTimer mCheckExpiredPeersTimer = null;
    private DiscoveryManagerState mState = DiscoveryManagerState.NOT_STARTED;
    private DiscoveryMode mDiscoveryMode = DiscoveryMode.NOT_SET;
    private long mPeerExpirationInMilliseconds = DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS;

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

                    // Not implemented yet
                    discoveryModeSet = false;

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

                    // BLE discovery not implemented yet
                    discoveryModeSet = false;

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
     * Sets the peer expiration time. If the given value is zero or less, peers will not expire.
     * @param peerExpirationInMilliseconds The peer expiration time in milliseconds.
     */
    public void setPeerExpiration(long peerExpirationInMilliseconds) {
        mPeerExpirationInMilliseconds = peerExpirationInMilliseconds;

        if (mCheckExpiredPeersTimer != null) {
            // Recreate the timer
            createCheckPeerExpirationTimer();
        }
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
     * Starts the peer discovery.
     * This method uses the Bluetooth address to set the value of the peer ID.
     * @param myPeerName Our peer name (used for the identity).
     * @return True, if started successfully or was already running. False otherwise.
     */
    public boolean start(String myPeerName) {
        return start(mBluetoothManager.getBluetoothAddress(), myPeerName);
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

        mCheckExpiredPeersTimer.cancel();
        mDiscoveredPeers.clear();

        setState(DiscoveryManagerState.NOT_STARTED);
    }

    /**
     * Adds the given peer to the list of discovered peer, if not already in the list. If the peer
     * is in the list, its timestamp is updated.
     *
     * This method is public so that, for instance, if you get a peer lost event while you still
     * have an existing connection with that peer, it is definitely not lost and can be added back.
     * You might also get an incoming connection from a peer that you haven't discovered yet so it
     * makes sense to add it.
     *
     * @param peerProperties The properties of a discovered peer.
     */
    public void addOrUpdateDiscoveredPeer(PeerProperties peerProperties) {
        Log.i(TAG, "addOrUpdateDiscoveredPeer: " + peerProperties.toString());
        modifyListOfDiscoveredPeers(peerProperties, true);
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
     * Updates the discovered peers, which match the ones on the given list.
     * @param p2pDeviceList A list containing the discovered P2P devices.
     */
    @Override
    public void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList) {
        if (p2pDeviceList != null && p2pDeviceList.size() > 0) {
            final Object[] p2pDeviceArray = p2pDeviceList.toArray();
            WifiP2pDevice wifiP2pDevice = null;

            for (int i = 0; i < p2pDeviceArray.length; ++i) {
                wifiP2pDevice = (WifiP2pDevice)p2pDeviceArray[i];

                if (wifiP2pDevice != null) {
                    Log.d(TAG, "onP2pDeviceListChanged: Peer " + (i + 1) + ": "
                            + wifiP2pDevice.deviceName + " " + wifiP2pDevice.deviceAddress);

                    PeerProperties peerProperties = findDiscoveredPeer(wifiP2pDevice.deviceAddress);

                    if (peerProperties != null) {
                        modifyListOfDiscoveredPeers(peerProperties, true);
                    }
                }
            }
        }
    }

    /**
     * Forward this event to the listener.
     * @param peerProperties The properties of the discovered peer.
     */
    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered: " + peerProperties.toString());
        modifyListOfDiscoveredPeers(peerProperties, true);

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

    /**
     * Tries to find a discovered peer with the given device address.
     * @param deviceAddress The device address of a peer to find.
     * @return A peer properties instance if found, null if not.
     */
    private synchronized PeerProperties findDiscoveredPeer(final String deviceAddress) {
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        PeerProperties peerProperties = null;

        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry) iterator.next();
            PeerProperties existingPeerProperties = (PeerProperties) entry.getValue();

            if (existingPeerProperties != null
                    && existingPeerProperties.getDeviceAddress() != null
                    && existingPeerProperties.getDeviceAddress().equalsIgnoreCase(deviceAddress)) {
                peerProperties = existingPeerProperties;
                break;
            }
        }

        return peerProperties;
    }

    /**
     * Tries to modify the list of discovered peers.
     * @param peerProperties The properties of the peer to modify (add/update or remove).
     * @parma addOrUpdate If true, will add/update. If false, will remove.
     * @return True, if success. False otherwise.
     */
    private synchronized boolean modifyListOfDiscoveredPeers(PeerProperties peerProperties, boolean addOrUpdate) {
        Log.v(TAG, "modifyListOfDiscoveredPeers: " + peerProperties.toString() + ", add/update: " + addOrUpdate);
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        boolean wasRemoved = false;

        // Always remove first
        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry) iterator.next();

            if (entry != null) {
                PeerProperties existingPeerProperties = (PeerProperties) entry.getValue();

                if (existingPeerProperties.equals(peerProperties)) {
                    iterator.remove();
                    wasRemoved = true;
                    break;
                }
            }
        }

        boolean success = false;

        if (addOrUpdate) {
            if (wasRemoved) {
                Log.d(TAG, "modifyListOfDiscoveredPeers: Updating the timestamp of peer "
                        + peerProperties.toString());
            } else {
                Log.d(TAG, "modifyListOfDiscoveredPeers: Adding new peer: " + peerProperties.toString());
            }

            mDiscoveredPeers.put(new Timestamp(new Date().getTime()), peerProperties);

            if (mCheckExpiredPeersTimer == null) {
                createCheckPeerExpirationTimer();
                mCheckExpiredPeersTimer.start();
            }

            success = true;
        } else if (wasRemoved) {
            Log.d(TAG, "modifyListOfDiscoveredPeers: Removed " + peerProperties.toString());
            success = true;
        }

        return success;
    }

    /**
     * Checks the list of peers for expired ones, removes them if found and notifies the listener.
     */
    private synchronized void checkListForExpiredPeers() {
        final Timestamp timestampNow = new Timestamp(new Date().getTime());
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        CopyOnWriteArrayList<PeerProperties> expiredPeers = new CopyOnWriteArrayList<>();

        // Find and copy expired peers to a separate list
        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry)iterator.next();
            Timestamp entryTimestamp = (Timestamp)entry.getKey();
            PeerProperties entryPeerProperties = (PeerProperties)entry.getValue();

            //Log.v(TAG, "checkListForExpiredPeers: Peer " + entryPeerProperties.toString() + " is now "
            //        + ((timestampNow.getTime() - entryTimestamp.getTime()) / 1000) + " seconds old");

            if (timestampNow.getTime() - entryTimestamp.getTime() > mPeerExpirationInMilliseconds) {
                Log.d(TAG, "checkListForExpiredPeers: Peer " + entryPeerProperties.toString() + " expired");
                expiredPeers.add(entryPeerProperties);
            }
        }

        if (expiredPeers.size() > 0) {
            // First remove all the expired peers from the list and only then notify the listener
            for (PeerProperties expiredPeer : expiredPeers) {
                modifyListOfDiscoveredPeers(expiredPeer, false);
            }

            for (PeerProperties expiredPeer : expiredPeers) {
                final PeerProperties finalExpiredPeer = expiredPeer;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onPeerLost(finalExpiredPeer);
                    }
                });
            }

            expiredPeers.clear();
        }
    }

    /**
     * Creates the timer for checking peers expired (not seen for a while).
     */
    private synchronized void createCheckPeerExpirationTimer() {
        if (mCheckExpiredPeersTimer != null) {
            mCheckExpiredPeersTimer.cancel();
            mCheckExpiredPeersTimer = null;
        }

        if (mPeerExpirationInMilliseconds > 0) {
            long timerTimeout = mPeerExpirationInMilliseconds / 2;

            mCheckExpiredPeersTimer = new CountDownTimer(timerTimeout, timerTimeout) {
                @Override
                public void onTick(long l) {
                    // Not used
                }

                @Override
                public void onFinish() {
                    checkListForExpiredPeers();

                    if (mDiscoveredPeers.size() == 0) {
                        // No more peers, dispose this timer
                        this.cancel();
                        mCheckExpiredPeersTimer = null;
                    } else {
                        // Restart the timer
                        this.start();
                    }
                }
            };
        }
    }
}
