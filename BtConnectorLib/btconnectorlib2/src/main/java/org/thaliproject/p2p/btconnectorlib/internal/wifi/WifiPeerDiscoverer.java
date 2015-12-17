/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
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
         * Called when the discovery is started or stopped.
         * @param isStarted If true, the discovery was started. If false, it was stopped.
         */
        void onIsWifiPeerDiscoveryStartedChanged(boolean isStarted);

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

    private static final String TAG = WifiPeerDiscoverer.class.getName();
    private static final long RESTART_WATCHER_DELAY_IN_MILLISECONDS = 10000;
    private final Context mContext;
    private final WifiP2pManager.Channel mP2pChannel;
    private final WifiP2pManager mP2pManager;
    private final WifiPeerDiscoveryListener mWifiPeerDiscoveryListener;
    private final String mServiceType;
    private final String mIdentityString;
    private WifiP2pDeviceDiscoverer mWifiP2pDeviceDiscoverer = null;
    private WifiServiceAdvertiser mWifiServiceAdvertiser = null;
    private WifiServiceWatcher mWifiServiceWatcher = null;
    private Timestamp mWifiServiceWatcherLastRestarted = null;
    private boolean mIsStarted = false;

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
        mWifiPeerDiscoveryListener = listener;
        mServiceType = serviceType;
        mIdentityString = identityString;
    }

    /**
     * Starts both advertising a Wi-Fi service and listening to other Wi-Fi services.
     * If already started, this method does nothing.
     */
    public synchronized void start() {
        if (!mIsStarted) {
            if (mP2pManager != null && mP2pChannel != null) {
                Log.i(TAG, "start: " + mIdentityString);

                mWifiServiceAdvertiser = new WifiServiceAdvertiser(mP2pManager, mP2pChannel);
                mWifiServiceAdvertiser.start(mIdentityString, mServiceType);

                mWifiP2pDeviceDiscoverer = new WifiP2pDeviceDiscoverer(this, mContext, mP2pManager, mP2pChannel);
                mWifiServiceWatcher = new WifiServiceWatcher(this, mP2pManager, mP2pChannel, mServiceType);

                if (mWifiP2pDeviceDiscoverer.initialize() && mWifiP2pDeviceDiscoverer.start()) {
                    // Let's not restart the service watcher until we find P2P devices
                    //mWifiServiceWatcher.start();

                    setIsStarted(true);
                } else {
                    Log.e(TAG, "Failed to initialize and start the peer discovery");
                    stop();
                }
            } else {
                Log.e(TAG, "start: Missing critical P2P instances!");
            }
        } else {
            Log.w(TAG, "start: Already running, call stopListening() first to restart");
        }
    }

    /**
     * Stops advertising and listening to Wi-Fi services.
     */
    public synchronized void stop() {
        if (mIsStarted) {
            Log.i(TAG, "stop: Stopping services");
        }

        if (mWifiServiceAdvertiser != null) {
            mWifiServiceAdvertiser.stop();
            mWifiServiceAdvertiser = null;
        }

        if (mWifiP2pDeviceDiscoverer != null) {
            mWifiP2pDeviceDiscoverer.deinitialize();
            mWifiP2pDeviceDiscoverer = null;
        }

        if (mWifiServiceWatcher != null) {
            mWifiServiceWatcher.stop();
            mWifiServiceWatcher = null;
        }

        setIsStarted(false);
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

        if (mWifiPeerDiscoveryListener != null) {
            mWifiPeerDiscoveryListener.onP2pDeviceListChanged(p2pDeviceList);
        }

        if (p2pDeviceList != null && p2pDeviceList.size() > 0) {
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

        if (!mWifiServiceAdvertiser.isStarted()) {
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

        if (mWifiPeerDiscoveryListener != null) {
            mWifiPeerDiscoveryListener.onPeerDiscovered(peerProperties);
        }
    }

    /**
     * Sets the "is started" state and notifies the listener.
     * @param isStarted Expected: True, if started. False, if stopped.
     */
    private void setIsStarted(boolean isStarted) {
        if (mIsStarted != isStarted) {
            mIsStarted = isStarted;

            if (mWifiPeerDiscoveryListener != null) {
                mWifiPeerDiscoveryListener.onIsWifiPeerDiscoveryStartedChanged(mIsStarted);
            }
        }
    }
}
