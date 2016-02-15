/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;

/**
 * The main interface for peer discovery via Wi-Fi.
 */
public class WifiPeerDiscoverer implements
        WifiP2pDeviceDiscoverer.Listener, WifiServiceWatcher.Listener {
    /**
     * A listener for peer discovery events.
     */
    public interface WifiPeerDiscoveryListener {
        /**
         * Called when the state of this class is changed.
         * @param state The new state.
         */
        void onWifiPeerDiscovererStateChanged(EnumSet<WifiPeerDiscovererStateSet> state);

        /**
         * Called when the content of the P2P devices list is changed.
         * @param p2pDeviceList A list containing the discovered P2P devices.
         */
        void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList);

        /**
         * Called when a peer was discovered.
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);
    }

    /**
     * Possible states of this class.
     */
    public enum WifiPeerDiscovererStateSet {
        NOT_STARTED,
        SCANNING,
        ADVERTISING
    }

    private static final String TAG = WifiPeerDiscoverer.class.getName();
    private static final long RESTART_WATCHER_DELAY_IN_MILLISECONDS = 10000;
    private final Context mContext;
    private final WifiP2pManager.Channel mP2pChannel;
    private final WifiP2pManager mP2pManager;
    private final WifiPeerDiscoveryListener mListener;
    private final String mServiceType;
    private final String mIdentityString;
    private WifiP2pDeviceDiscoverer mWifiP2pDeviceDiscoverer = null;
    private WifiServiceAdvertiser mWifiServiceAdvertiser = null;
    private WifiServiceWatcher mWifiServiceWatcher = null;
    private Timestamp mWifiServiceWatcherLastRestarted = null;
    private EnumSet<WifiPeerDiscovererStateSet> mStateSet = EnumSet.of(WifiPeerDiscovererStateSet.NOT_STARTED);

    /**
     * Constructor.
     * @param context The application context.
     * @param p2pChannel The Wi-Fi P2P manager.
     * @param p2pManager The Wi-Fi P2P channel.
     * @param listener The listener.
     * @param serviceType The service type.
     * @param identityString Our identity (for service advertisement).
     */
    public WifiPeerDiscoverer (
            Context context, WifiP2pManager.Channel p2pChannel, WifiP2pManager p2pManager,
            WifiPeerDiscoveryListener listener, String serviceType, String identityString) {
        mContext = context;
        mP2pChannel = p2pChannel;
        mP2pManager = p2pManager;
        mListener = listener;
        mServiceType = serviceType;
        mIdentityString = identityString;
    }

    /**
     * @return The current state of this class.
     */
    public EnumSet<WifiPeerDiscovererStateSet> getState() {
        return mStateSet;
    }

    /**
     * Starts the Wi-Fi P2P device discovery.
     * @return True, if starting or already started. False otherwise.
     */
    public synchronized boolean startDiscoverer() {
        boolean isStarted = false;

        if (mWifiP2pDeviceDiscoverer == null || mWifiServiceWatcher == null) {
            Log.i(TAG, "startWatcher: Starting...");

            if (mP2pManager != null && mP2pChannel != null) {
                mWifiP2pDeviceDiscoverer = new WifiP2pDeviceDiscoverer(this, mContext, mP2pManager, mP2pChannel);
                mWifiServiceWatcher = new WifiServiceWatcher(this, mP2pManager, mP2pChannel, mServiceType);

                if (mWifiP2pDeviceDiscoverer.initialize() && mWifiP2pDeviceDiscoverer.start()) {
                    // Let's not restart the service watcher until we find P2P devices
                    //mWifiServiceWatcher.start();

                    isStarted = true;
                    updateState();
                } else {
                    Log.e(TAG, "Failed to initialize and start the peer discovery");
                    stopDiscoverer();
                }
            } else {
                throw new NullPointerException("Missing critical P2P instances");
            }
        } else {
            // Already started
            isStarted = true;
        }

        return isStarted;
    }

    /**
     * Stops the Wi-Fi P2P device discovery and the service watcher.
     */
    public synchronized void stopDiscoverer() {
        if (mWifiP2pDeviceDiscoverer != null || mWifiServiceWatcher != null) {
            Log.i(TAG, "stopWatcher: Stopping...");

            if (mWifiP2pDeviceDiscoverer != null) {
                mWifiP2pDeviceDiscoverer.deinitialize();
                mWifiP2pDeviceDiscoverer = null;
            }

            if (mWifiServiceWatcher != null) {
                mWifiServiceWatcher.stop();
                mWifiServiceWatcher = null;
            }

            updateState();
        }
    }

    /**
     * Starts the Wi-Fi service advertiser.
     * @return True, if starting or already started. False otherwise.
     */
    public synchronized boolean startAdvertiser() {
        boolean isStarted = false;

        if (mWifiServiceAdvertiser == null) {
            Log.i(TAG, "startAdvertiser: Using identity string: " + mIdentityString);

            if (mP2pManager != null && mP2pChannel != null) {
                mWifiServiceAdvertiser = new WifiServiceAdvertiser(mP2pManager, mP2pChannel);
                mWifiServiceAdvertiser.start(mIdentityString, mServiceType);
                isStarted = true;
                updateState();
            } else {
                throw new NullPointerException("Missing critical P2P instances");
            }
        } else {
            // already started
            isStarted = true;
        }

        return isStarted;
    }

    /**
     * Stops the Wi-Fi service advertiser.
     */
    public synchronized void stopAdvertiser() {
        if (mWifiServiceAdvertiser != null) {
            Log.i(TAG, "stopAdvertiser: Stopping...");

            if (mWifiServiceAdvertiser != null) {
                mWifiServiceAdvertiser.stop();
                mWifiServiceAdvertiser = null;
            }

            updateState();
        }
    }

    /**
     * Starts both advertising a Wi-Fi service and listening to other Wi-Fi services.
     * If already started, this method does nothing.
     * @return True, if successfully started or was already running. False otherwise.
     */
    public synchronized boolean startDiscovererAndAdvertiser() {
        Log.i(TAG, "startDiscovererAndAdvertiser: Using identity string: " + mIdentityString);
        return (startDiscoverer() && startAdvertiser());
    }

    /**
     * Stops advertising and listening to Wi-Fi services.
     */
    public synchronized void stopDiscovererAndAdvertiser() {
        Log.i(TAG, "stopDiscovererAndAdvertiser: Stopping...");
        stopDiscoverer();
        stopAdvertiser();
    }

    /**
     * Forwards the event to the listener and restarts the Wi-Fi service watcher, if enough time has
     * elapsed since it was last restarted.
     * @param p2pDeviceList The new list of P2P devices discovered.
     */
    @Override
    public void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList) {
        Log.d(TAG, "onP2pDeviceListChanged: " + ((p2pDeviceList == null)
                ? "Got empty list" : (p2pDeviceList.size() + " device(s) discovered")));

        if (mListener != null) {
            mListener.onP2pDeviceListChanged(p2pDeviceList);
        }

        if (mWifiServiceWatcher != null && p2pDeviceList != null && p2pDeviceList.size() > 0) {
            if (mWifiServiceWatcherLastRestarted == null) {
                // First time
                mWifiServiceWatcherLastRestarted = new Timestamp(new Date().getTime());
                mWifiServiceWatcher.start();
            } else {
                final Timestamp timestampNow = new Timestamp(new Date().getTime());

                if (timestampNow.getTime() - mWifiServiceWatcherLastRestarted.getTime() > RESTART_WATCHER_DELAY_IN_MILLISECONDS) {
                    mWifiServiceWatcherLastRestarted = timestampNow;
                    mWifiServiceWatcher.restart();
                }
            }
        }

        if (mWifiServiceAdvertiser != null && !mWifiServiceAdvertiser.isStarted()) {
            // Try to (re)start the service advertiser
            mWifiServiceAdvertiser.start(mIdentityString, mServiceType);
        }
    }

    /**
     * Forwards the event to the listener.
     * @param peerProperties The discovered peer device with an appropriate service.
     */
    @Override
    public void onServiceDiscovered(PeerProperties peerProperties) {
        Log.d(TAG, "onServiceDiscovered: " + peerProperties.toString());

        if (mListener != null) {
            mListener.onPeerDiscovered(peerProperties);
        }
    }

    /**
     * Resolves and updates the state and notifies the listener.
     */
    private synchronized void updateState() {
        EnumSet<WifiPeerDiscovererStateSet> deducedStateSet =
                EnumSet.noneOf(WifiPeerDiscovererStateSet.class);

        if (mWifiP2pDeviceDiscoverer != null && mWifiServiceWatcher != null) {
            deducedStateSet.add(WifiPeerDiscovererStateSet.SCANNING);
        }

        if (mWifiServiceAdvertiser != null) {
            deducedStateSet.add(WifiPeerDiscovererStateSet.ADVERTISING);
        }

        if (deducedStateSet.isEmpty()) {
            deducedStateSet.add(WifiPeerDiscovererStateSet.NOT_STARTED);
        }

        if (!mStateSet.equals(deducedStateSet)) {
            Log.d(TAG, "updateState: State changed from " + mStateSet + " to " + deducedStateSet);
            mStateSet = deducedStateSet;
            mListener.onWifiPeerDiscovererStateChanged(mStateSet);
        }
    }
}
