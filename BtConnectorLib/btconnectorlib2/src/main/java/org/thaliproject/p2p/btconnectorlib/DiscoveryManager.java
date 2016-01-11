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
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The main interface for managing peer discovery.
 */
public class DiscoveryManager
        extends AbstractBluetoothConnectivityAgent
        implements
            WifiDirectManager.WifiStateListener,
            WifiPeerDiscoverer.WifiPeerDiscoveryListener,
            BlePeerDiscoverer.BlePeerDiscoveryListener,
            DiscoveryManagerSettings.Listener {

    public enum DiscoveryManagerState {
        NOT_STARTED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED, // When the chosen peer discovery method is disabled and waiting for it to be enabled to start
        RUNNING_BLE,
        RUNNING_WIFI,
        RUNNING_BLE_AND_WIFI
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
         * Called when a peer data is updated (missing data is added). This callback is never
         * called, if data is lost.
         * @param peerProperties The updated properties of a discovered peer.
         */
        void onPeerUpdated(PeerProperties peerProperties);

        /**
         * Called when an existing peer is lost (i.e. not available anymore).
         * @param peerProperties The properties of the lost peer.
         */
        void onPeerLost(PeerProperties peerProperties);
    }

    private static final String TAG = DiscoveryManager.class.getName();

    private final Context mContext;
    private final DiscoveryManagerListener mListener;
    private final UUID mBleServiceUuid;
    private final String mServiceType;
    private final Handler mHandler;
    private final HashMap<Timestamp, PeerProperties> mDiscoveredPeers = new HashMap<>();
    private WifiDirectManager mWifiDirectManager = null;
    private BlePeerDiscoverer mBlePeerDiscoverer = null;
    private WifiPeerDiscoverer mWifiPeerDiscoverer = null;
    private CountDownTimer mCheckExpiredPeersTimer = null;
    private DiscoveryManagerState mState = DiscoveryManagerState.NOT_STARTED;
    private DiscoveryManagerSettings mSettings = null;
    private boolean mShouldBeRunning = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener The listener.
     * @param bleServiceUuid Our BLE service UUID (both ours and requirement for the peer).
     *                       Required by BLE based peer discovery only.
     * @param serviceType The service type (both ours and requirement for the peer).
     *                    Required by Wi-Fi Direct based peer discovery only.
     */
    public DiscoveryManager(Context context, DiscoveryManagerListener listener, UUID bleServiceUuid, String serviceType) {
        super(context); // Gets the BluetoothManager instance

        mContext = context;
        mListener = listener;
        mBleServiceUuid = bleServiceUuid;
        mServiceType = serviceType;

        mSettings = DiscoveryManagerSettings.getInstance();
        mSettings.setListener(this);

        mHandler = new Handler(mContext.getMainLooper());
        mWifiDirectManager = WifiDirectManager.getInstance(mContext);
    }

    /**
     * @return True, if Bluetooth LE advertising is supported. False otherwise.
     */
    public boolean isBleAdvertisingSupported() {
        return mBluetoothManager.isBleSupported();
    }

    /**
     * @return The current state.
     */
    public DiscoveryManagerState getState() {
        return mState;
    }

    /**
     * Checks the current state and returns true, if running regardless of the discovery mode in use.
     * @return True, if running regardless of the mode. False otherwise.
     */
    public boolean isRunning() {
        return (mState == DiscoveryManagerState.RUNNING_BLE
                || mState == DiscoveryManagerState.RUNNING_WIFI
                || mState == DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
    }

    /**
     * Starts the peer discovery.
     * @param myPeerId Our peer ID (used for the identity).
     * @param myPeerName Our peer name (used for the identity).
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean start(String myPeerId, String myPeerName) {
        Log.i(TAG, "start: Peer ID: " + myPeerId + ", peer name: " + myPeerName);
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();
        mShouldBeRunning = true;
        mMyPeerId = myPeerId;
        mMyPeerName = myPeerName;

        mBluetoothManager.bind(this);
        mWifiDirectManager.bind(this);

        if (discoveryMode != DiscoveryMode.NOT_SET) {
            boolean bleDiscoveryStarted = false;
            boolean wifiDiscoveryStarted = false;

            if (mBluetoothManager.isBluetoothEnabled()
                    && (discoveryMode == DiscoveryMode.BLE || discoveryMode == DiscoveryMode.BLE_AND_WIFI)) {
                // Try to start BLE based discovery
                bleDiscoveryStarted = startBlePeerDiscovery();
            }

            if (mWifiDirectManager.isWifiEnabled()
                    && (discoveryMode == DiscoveryMode.WIFI || discoveryMode == DiscoveryMode.BLE_AND_WIFI)) {
                if (verifyIdentityString()) {
                    // Try to start Wi-Fi Direct based discovery
                    wifiDiscoveryStarted = startWifiPeerDiscovery();
                } else {
                    if (mMyIdentityString == null || mMyIdentityString.length() == 0) {
                        Log.e(TAG, "start: Identity string is null or empty");
                    } else {
                        Log.e(TAG, "start: Invalid identity string: " + mMyIdentityString);
                    }
                }
            }

            if ((discoveryMode == DiscoveryMode.BLE_AND_WIFI && (bleDiscoveryStarted || wifiDiscoveryStarted))
                    || (discoveryMode == DiscoveryMode.BLE && bleDiscoveryStarted)
                    || (discoveryMode == DiscoveryMode.WIFI && wifiDiscoveryStarted)) {
                Log.i(TAG, "start: OK");

                if (bleDiscoveryStarted && wifiDiscoveryStarted) {
                    setState(DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
                } else if (bleDiscoveryStarted) {
                    setState(DiscoveryManagerState.RUNNING_BLE);
                } else if (wifiDiscoveryStarted) {
                    setState(DiscoveryManagerState.RUNNING_WIFI);
                }
            }
        } else {
            Log.e(TAG, "start: Discovery mode not set, call setDiscoveryMode() to set");
        }

        return isRunning();
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

        mShouldBeRunning = false;

        stopBlePeerDiscovery();
        stopWifiPeerDiscovery();
        mWifiDirectManager.release(this);
        mBluetoothManager.release(this);

        if (mCheckExpiredPeersTimer != null) {
            mCheckExpiredPeersTimer.cancel();
            mCheckExpiredPeersTimer = null;
        }

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

    @Override
    public boolean onDiscoveryModeChanged(final DiscoveryMode discoveryMode, boolean forceRestart) {
        boolean wasRunning = (mState != DiscoveryManagerState.NOT_STARTED);
        boolean discoveryModeSet = false;

        if (wasRunning && forceRestart) {
            stop();
        }

        if (!wasRunning || forceRestart) {
            boolean isBleSupported = isBleAdvertisingSupported();
            boolean isWifiSupported = mWifiDirectManager.isWifiDirectSupported();

            switch (discoveryMode) {
                case BLE:
                    if (isBleSupported) {
                        discoveryModeSet = true;
                    }

                    break;

                case WIFI:
                    if (isWifiSupported) {
                        discoveryModeSet = true;
                    }

                    break;

                case BLE_AND_WIFI:
                    if (isBleSupported && isWifiSupported) {
                        discoveryModeSet = true;
                    }

                    break;
            }

            if (!discoveryModeSet) {
                Log.w(TAG, "onDiscoveryModeChanged: Failed to set discovery mode to " + discoveryMode
                        + ", BLE supported: " + isBleSupported + ", Wi-Fi supported: " + isWifiSupported);
            } else {
                Log.i(TAG, "onDiscoveryModeChanged: Mode set to " + discoveryMode);
            }
        }

        if (discoveryModeSet && wasRunning && forceRestart) {
            discoveryModeSet = start(mMyPeerId, mMyPeerName);
        }

        return discoveryModeSet;
    }

    @Override
    public void onPeerExpirationSettingChanged(long peerExpirationInMilliseconds) {
        if (mCheckExpiredPeersTimer != null) {
            // Recreate the timer
            createCheckPeerExpirationTimer();
        }
    }

    @Override
    public void onAdvertiseSettingsChanged(int advertiseMode, int advertiseTxPowerLevel) {
        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.applySettings(advertiseMode, advertiseTxPowerLevel, mSettings.getScanMode());
        }
    }

    @Override
    public void onScanModeSettingChanged(int scanMode) {
        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.applySettings(mSettings.getAdvertiseMode(), mSettings.getAdvertiseTxPowerLevel(), scanMode);
        }
    }

    /**
     * Stops/restarts the BLE based peer discovery depending on the given mode.
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.BLE || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

            if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
                if (mState != DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                    Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, pausing BLE based peer discovery...");
                    stopBlePeerDiscovery();

                    if (discoveryMode == DiscoveryMode.BLE ||
                            (discoveryMode == DiscoveryMode.BLE_AND_WIFI &&
                                    !mWifiDirectManager.isWifiEnabled())) {
                        setState(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    } else if (discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
                        setState(DiscoveryManagerState.RUNNING_WIFI);
                    }
                }
            } else {
                if (mShouldBeRunning && mBluetoothManager.isBluetoothEnabled()) {
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
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.WIFI || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onWifiStateChanged: State changed to " + state);

            if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                if (mState != DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED) {
                    Log.w(TAG, "onWifiStateChanged: Wi-Fi disabled, pausing Wi-Fi Direct based peer discovery...");
                    stopWifiPeerDiscovery();

                    if (discoveryMode == DiscoveryMode.WIFI
                            || (discoveryMode == DiscoveryMode.BLE_AND_WIFI
                                && !mBluetoothManager.isBluetoothEnabled())) {
                        setState(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
                    } else if (discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
                        setState(DiscoveryManagerState.RUNNING_BLE);
                    }
                }
            } else {
                if (mShouldBeRunning) {
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
    public void onIsWifiPeerDiscoveryStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsWifiPeerDiscoveryStartedChanged: " + isStarted);
    }

    /**
     * Does nothing but logs the event.
     * @param isStarted If true, the discovery was started. If false, it was stopped.
     */
    @Override
    public void onIsBlePeerDiscoveryStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsBlePeerDiscoveryStartedChanged: " + isStarted);
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
     * Adds or updates the discovered peer.
     * @param peerProperties The properties of the discovered peer.
     */
    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.i(TAG, "onPeerDiscovered: " + peerProperties.toString());
        modifyListOfDiscoveredPeers(peerProperties, true); // Will notify the listener
    }

    /**
     * Tries to start the BLE based peer discovery.
     * @return True, if started (or already running). False otherwise.
     */
    private synchronized boolean startBlePeerDiscovery() {
        boolean started = false;

        if (mBluetoothManager.bind(this)) {
            if (mBlePeerDiscoverer == null) {
                if (mBleServiceUuid != null) {
                    mBlePeerDiscoverer = new BlePeerDiscoverer(
                            this, mBluetoothManager.getBluetoothAdapter(),
                            mMyPeerName, mBleServiceUuid, mBluetoothManager.getBluetoothAddress());

                    started = mBlePeerDiscoverer.start();
                } else {
                    Log.e(TAG, "startBlePeerDiscovery: No BLE service UUID");
                }
            } else {
                Log.d(TAG, "startBlePeerDiscovery: Already running");
                started = true;
            }
        }

        if (started) {
            Log.d(TAG, "startBlePeerDiscovery: OK");
        }

        return started;
    }

    /**
     * Stops the BLE based peer discovery.
     */
    private synchronized void stopBlePeerDiscovery() {
        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.stop();
            mBlePeerDiscoverer = null;
            Log.d(TAG, "stopBlePeerDiscovery: Stopped");
        }
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
        PeerProperties oldPeerProperties = null;

        // Always remove first
        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry) iterator.next();

            if (entry != null) {
                PeerProperties existingPeerProperties = (PeerProperties) entry.getValue();

                if (existingPeerProperties.equals(peerProperties)) {
                    oldPeerProperties = existingPeerProperties;
                    iterator.remove();
                    break;
                }
            }
        }

        boolean success = false;

        if (addOrUpdate) {
            if (oldPeerProperties != null) {
                // This one was already in the list (same ID)
                // Make sure we don't lose any data when updating
                PeerProperties.checkNewPeerForMissingInformation(oldPeerProperties, peerProperties);

                if (peerProperties.hasMoreInformation(oldPeerProperties)) {
                    // The new discovery result has more information than the old one
                    if (mListener != null) {
                        final PeerProperties updatedPeerProperties = peerProperties;

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onPeerUpdated(updatedPeerProperties);
                            }
                        });
                    }
                }

                Log.d(TAG, "modifyListOfDiscoveredPeers: Updating the timestamp of peer "
                        + peerProperties.toString());
            } else {
                // The given peer was not in the list before, hence it is a new one
                Log.d(TAG, "modifyListOfDiscoveredPeers: Adding a new peer: " + peerProperties.toString());

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

            mDiscoveredPeers.put(new Timestamp(new Date().getTime()), peerProperties);

            if (mCheckExpiredPeersTimer == null) {
                createCheckPeerExpirationTimer();
                mCheckExpiredPeersTimer.start();
            }

            success = true;
        } else if (oldPeerProperties != null) {
            Log.d(TAG, "modifyListOfDiscoveredPeers: Removed peer " + peerProperties.toString());
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

            if (timestampNow.getTime() - entryTimestamp.getTime() > mSettings.getPeerExpiration()) {
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

        long peerExpirationInMilliseconds = mSettings.getPeerExpiration();

        if (peerExpirationInMilliseconds > 0) {
            long timerTimeout = peerExpirationInMilliseconds / 2;

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
