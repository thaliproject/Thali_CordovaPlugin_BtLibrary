/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * Manages the device Wi-Fi settings and provides information on Wi-Fi and Wi-Fi Direct status on
 * the device.
 */
public class WifiDirectManager {
    /**
     * A listener interface for Wi-Fi state changes.
     */
    public interface WifiStateListener {
        void onWifiStateChanged(int state);
    }

    private static final String TAG = WifiDirectManager.class.getName();
    private final Context mContext;
    private final WifiStateListener mWifiStateListener;
    private WifiStateBroadcastReceiver mWifiStateBroadcastReceiver = null;
    private WifiP2pManager mP2pManager = null;
    private WifiP2pManager.Channel mP2pChannel = null;
    private WifiManager mWifiManager = null;
    private boolean mInitialized = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener A listener for Wi-Fi state changed events.
     */
    public WifiDirectManager(Context context, WifiStateListener listener) {
        mContext = context;
        mWifiStateListener = listener;
    }

    /**
     * Registers the broadcast receiver to listen to Wi-Fi state changes and fetches the Wi-Fi
     * P2P manager instance.
     * @return True, if successfully initialized (even if that the initialization was done earlier).
     * If false is returned, this could indicate the lack of Wi-Fi Direct hardware support.
     */
    public synchronized boolean initialize() {
        if (!mInitialized) {
            mWifiStateBroadcastReceiver = new WifiStateBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

            try {
                mContext.registerReceiver(mWifiStateBroadcastReceiver, filter);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                mWifiStateBroadcastReceiver = null;
            }

            if (mWifiStateBroadcastReceiver != null) {
                mP2pManager = (WifiP2pManager)mContext.getSystemService(Context.WIFI_P2P_SERVICE);

                if (mP2pManager == null) {
                    Log.w(TAG, "initialize: This device does not support Wi-Fi Direct");

                    try {
                        mContext.unregisterReceiver(mWifiStateBroadcastReceiver);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                } else {
                    mP2pChannel = mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
                    mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
                    mInitialized = true;
                }
            }
        } else {
            Log.w(TAG, "initialize: Already initialized, call deinitialize() first to reinitialize");
        }

        return mInitialized;
    }

    /**
     * Unregisters the broadcast receiver and releases the Wi-Fi Direct P2P manager instance.
     */
    public synchronized void deinitialize() {
        if (mWifiStateBroadcastReceiver != null) {
            try {
                mContext.unregisterReceiver(mWifiStateBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "deinitialize: Failed to unregister the broadcast receiver: " + e.getMessage(), e);
            }

            mWifiStateBroadcastReceiver = null;
            mP2pManager = null;
            mP2pChannel = null;
            mWifiManager = null;
            mInitialized = false;
        }
    }

    public boolean isWifiEnabled() {
        return mWifiManager != null && mWifiManager.isWifiEnabled();
    }

    public boolean setWifiEnabled(boolean enabled) {
        return mWifiManager != null && mWifiManager.setWifiEnabled(enabled);
    }

    public WifiP2pManager getWifiP2pManager() {
        return mP2pManager;
    }

    public WifiP2pManager.Channel getWifiP2pChannel() {
        return mP2pChannel;
    }

    /**
     * Broadcast receiver for Wi-Fi state changes.
     */
    private class WifiStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                if (mWifiStateListener != null) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    mWifiStateListener.onWifiStateChanged(state);
                }
            }
        }
    }
}
