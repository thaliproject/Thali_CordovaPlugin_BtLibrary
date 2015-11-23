/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerDevice;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class WifiPeerDiscoverer implements WifiServiceWatcher.WifiServiceWatcherListener {
    /**
     * A listener for peer discovery events.
     */
    public interface WifiPeerDiscoveryListener {
        void onIsDiscoveryStartedChanged(boolean isStarted);
        void onPeerDiscovered(PeerDevice peerDevice);
        void onListOfDiscoveredPeersChanged(List<PeerDevice> peerDeviceList);
    }

    private static final String TAG = WifiPeerDiscoverer.class.getName();
    private static final long SERVICE_SEARCH_TIMEOUT_IN_MILLISECONDS = 600000;
    private static final long SERVICE_SEARCH_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 10000;
    private final Context mContext;
    private final WifiP2pManager.Channel mP2pChannel;
    private final WifiP2pManager mP2pManager;
    private final WifiPeerDiscoveryListener mWifiPeerDiscoveryListener;
    private final String mServiceType;
    private final String mIdentityString;
    private WifiServiceAdvertiser mWifiServiceAdvertiser = null;
    private WifiServiceWatcher mWifiServiceWatcher = null;
    private CountDownTimer mServiceSearchTimeoutTimer = null;
    private boolean mIsStarted = false;

    /**
     *
     * @param context
     * @param p2pChannel
     * @param p2pManager
     * @param listener
     * @param serviceType
     * @param identityString
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

        final WifiPeerDiscoverer thisInstance = this;

        mServiceSearchTimeoutTimer =
            new CountDownTimer(SERVICE_SEARCH_TIMEOUT_IN_MILLISECONDS,
                    SERVICE_SEARCH_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
                public void onTick(long millisUntilFinished) {
                    // Not used
                }

                public void onFinish() {
                    if (thisInstance.mWifiPeerDiscoveryListener != null) {
                        // Clear the existing list of found peers
                        thisInstance.mWifiPeerDiscoveryListener.onListOfDiscoveredPeersChanged(null);
                    }

                    // Restart discovery
                    thisInstance.stop();
                    thisInstance.start();
                }
            };
    }

    /**
     * Starts both advertising a Wi-Fi service and listening to other Wi-Fi services.
     * If already started, this method does nothing.
     */
    public synchronized void start(){
        if (!mIsStarted) {
            if (mP2pManager != null && mP2pChannel != null) {
                Log.i(TAG, "initialize: " + mIdentityString);

                mServiceSearchTimeoutTimer.cancel();
                mServiceSearchTimeoutTimer.start();

                mWifiServiceAdvertiser = new WifiServiceAdvertiser(mP2pManager, mP2pChannel);
                mWifiServiceAdvertiser.Start(mIdentityString, mServiceType);

                mWifiServiceWatcher = new WifiServiceWatcher(mContext, mP2pManager, mP2pChannel, this, mServiceType);
                mWifiServiceWatcher.initialize();

                setIsStarted(true);
            } else {
                Log.e(TAG, "initialize: Missing critical P2P instances!");
            }
        } else {
            Log.w(TAG, "initialize: Already running, call stopListening() first to restart");
        }
    }

    /**
     * Stops advertising and listening to Wi-Fi services.
     */
    public synchronized void stop() {
        if (mIsStarted) {
            Log.i("", "Stopping services");
            mServiceSearchTimeoutTimer.cancel();

            if (mWifiServiceAdvertiser != null)
            {
                mWifiServiceAdvertiser.Stop();
                mWifiServiceAdvertiser = null;
            }

            if (mWifiServiceWatcher != null)
            {
                mWifiServiceWatcher.deinitialize();
                mWifiServiceWatcher = null;
            }

            setIsStarted(false);
        }
    }

    /**
     *
     * @param p2pDeviceList
     */
    @Override
    public void onNewListOfPeersAvailable(Collection<WifiP2pDevice> p2pDeviceList) {
        Log.i(TAG, "onNewListOfPeersAvailable: " + p2pDeviceList.size() + " peers discovered");

        if (p2pDeviceList.size() > 0) {
            mServiceSearchTimeoutTimer.cancel();
            mServiceSearchTimeoutTimer.start();

            int index = 0;

            for (WifiP2pDevice p2pDevice : p2pDeviceList) {
                index++;
                Log.i(TAG, "onNewListOfPeersAvailable: Peer " + index + ": " + p2pDevice.deviceName + " " + p2pDevice.deviceAddress);
            }
        } else {
            mWifiPeerDiscoveryListener.onListOfDiscoveredPeersChanged(null);
        }
    }

    /**
     *
     * @param peerDeviceList
     */
    @Override
    public void gotServicesList(List<PeerDevice> peerDeviceList) {
        if (mWifiPeerDiscoveryListener != null && peerDeviceList != null && peerDeviceList.size() > 0) {
            mWifiPeerDiscoveryListener.onListOfDiscoveredPeersChanged(peerDeviceList);
        }
    }

    /**
     *
     * @param peerDevice
     */
    @Override
    public void foundService(PeerDevice peerDevice) {
        if (mWifiPeerDiscoveryListener != null) {
            mWifiPeerDiscoveryListener.onPeerDiscovered(peerDevice);
        }
    }

    /**
     * Sets the "is started" state and notifies the listener.
     * @param isStarted
     */
    private void setIsStarted(boolean isStarted) {
        mIsStarted = isStarted;

        if (mWifiPeerDiscoveryListener != null) {
            mWifiPeerDiscoveryListener.onIsDiscoveryStartedChanged(mIsStarted);
        }
    }
}
