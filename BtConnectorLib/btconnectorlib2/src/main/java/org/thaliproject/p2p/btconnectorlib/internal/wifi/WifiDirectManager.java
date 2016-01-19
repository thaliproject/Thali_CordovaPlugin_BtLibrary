/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import java.util.ArrayList;

/**
 * Manages the device Wi-Fi settings and provides information on Wi-Fi and Wi-Fi Direct status on
 * the device.
 */
public class WifiDirectManager {
    /**
     * A listener interface for Wi-Fi state changes.
     */
    public interface WifiStateListener {
        /**
         * Called when the Wi-Fi state on the device is changed (e.g. enabled or disabled).
         * @param state The new state.
         */
        void onWifiStateChanged(int state);
    }

    private static final String TAG = WifiDirectManager.class.getName();
    private static WifiDirectManager mInstance = null;
    private final Context mContext;
    private final ArrayList<WifiStateListener> mListeners = new ArrayList<>();
    private WifiStateBroadcastReceiver mWifiStateBroadcastReceiver = null;
    private WifiP2pManager mP2pManager = null;
    private WifiP2pManager.Channel mP2pChannel = null;
    private WifiManager mWifiManager = null;
    private boolean mInitialized = false;

    /**
     * Getter for the singleton instance of this class.
     * @param context The application context.
     * @return The singleton instance of this class.
     */
    public static WifiDirectManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new WifiDirectManager(context);
        }

        return mInstance;
    }

    /**
     * Constructor.
     * @param context The application context.
     */
    private WifiDirectManager(Context context) {
        mContext = context;
    }

    /**
     * Binds the given listener to this instance. If already bound, this method does nothing.
     *
     * Note that the listener acts as a sort of a reference counter. You must call release() after
     * you're done using the instance.
     *
     * @param listener A listener for Wi-Fi state changed events.
     * @return True, if bound successfully (or already bound). If false is returned, this could
     * indicate the lack of Bluetooth hardware support.
     */
    public boolean bind(WifiStateListener listener) {
        if (!mListeners.contains(listener)) {
            Log.i(TAG, "bind: Binding a new listener");
            mListeners.add(listener);
        }

        return initialize();
    }

    /**
     * Removes the given listener from the list of listeners. If, after this, the list of listeners
     * is empty, there is no reason to keep this instance "running" and we can de-initialize.
     * @param listener The listener to remove.
     */
    public void release(WifiStateListener listener) {
        if (!mListeners.remove(listener)) {
            Log.e(TAG, "release: The given listener does not exist in the list");
        }

        if (mListeners.size() == 0) {
            Log.i(TAG, "release: No more listeners, de-initializing...");
            deinitialize();
        } else {
            Log.d(TAG, "release: " + mListeners.size() + " listener(s) left");
        }
    }

    /**
     * Checks whether the device supports Wi-Fi Direct or not. Note that this method also retrieves
     * the WifiP2pManager instance.
     * @return True, if Wi-Fi Direct is supported. False otherwise.
     */
    public boolean isWifiDirectSupported() {
        if (mP2pManager == null) {
            mP2pManager = (WifiP2pManager)mContext.getSystemService(Context.WIFI_P2P_SERVICE);
        }

        return (mP2pManager != null);
    }

    /**
     * @return True, Wi-Fi is enabled.
     */
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
     * Registers the broadcast receiver to listen to Wi-Fi state changes.
     * @return True, if successfully initialized (even if that the initialization was done earlier).
     * If false is returned, this could indicate the lack of Wi-Fi Direct hardware support.
     */
    private synchronized boolean initialize() {
        if (!mInitialized && isWifiDirectSupported()) {
            mWifiStateBroadcastReceiver = new WifiStateBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

            try {
                mContext.registerReceiver(mWifiStateBroadcastReceiver, filter);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                mWifiStateBroadcastReceiver = null;
            }

            mP2pChannel = mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
            mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
            mInitialized = true;
        }

        return mInitialized;
    }

    /**
     * Unregisters the broadcast receiver and releases the Wi-Fi Direct P2P manager instance.
     */
    private synchronized void deinitialize() {
        if (mWifiStateBroadcastReceiver != null) {
            try {
                mContext.unregisterReceiver(mWifiStateBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "deinitialize: Failed to unregister the broadcast receiver: " + e.getMessage(), e);
            }

            mWifiStateBroadcastReceiver = null;
        }

        mP2pManager = null;
        mP2pChannel = null;
        mWifiManager = null;
        mInitialized = false;
    }

    /**
     * Broadcast receiver for Wi-Fi state changes.
     */
    private class WifiStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

                for (WifiStateListener listener : mListeners) {
                    listener.onWifiStateChanged(state);
                }
            }
        }
    }
}
