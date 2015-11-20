/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * Manages the device Bluetooth settings and provides information on Bluetooth status on the
 * device.
 */
public class BluetoothManager {
    /**
     * A listener interface for Bluetooth scan ode changes.
     */
    public interface BluetoothAdapterScanModeListener {
        void onBluetoothAdapterScanModeChanged(int mode);
    }

    private static final String TAG = BluetoothManager.class.getName();
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothAdapterScanModeListener mBluetoothAdapterScanModeListener;
    private final Context mContext;
    private BluetoothModeBroadCastReceiver mBluetoothBroadcastReceiver = null;
    private boolean mInitialized = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener A listener for bluetooth adapter scan mode changes.
     */
    public BluetoothManager(Context context, BluetoothAdapterScanModeListener listener) {
        mContext = context;
        mBluetoothAdapterScanModeListener = listener;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Registers the broadcast receiver to listen to Bluetooth scan mode changes.
     * @return True, if successfully initialized (even if that the initialization was done earlier).
     * If false is returned, this could indicate the lack of Bluetooth hardware support.
     */
    public synchronized boolean initialize() {
        if (!mInitialized) {
            if (mBluetoothAdapter != null) {
                Log.i(TAG, "initialize: My bluetooth address is " + getBluetoothAddress());

                mBluetoothBroadcastReceiver = new BluetoothModeBroadCastReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

                try {
                    mContext.registerReceiver(mBluetoothBroadcastReceiver, filter);
                    mInitialized = true;
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "initialize: Failed to register the broadcast receiver: " + e.getMessage(), e);
                    mBluetoothBroadcastReceiver = null;
                }
            } else {
                Log.e(TAG, "initialize: No bluetooth adapter!");
            }
        } else {
            Log.w(TAG, "initialize: Already initialized, call deinitialize() first to reinitialize");
        }

        return mInitialized;
    }

    /**
     * Unregisters and disposes the broadcast receiver listening to bluetooth adapter mode changes.
     */
    public synchronized void deinitialize() {
        if (mBluetoothBroadcastReceiver != null) {
            try {
                mContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "deinitialize: Failed to unregister the broadcast receiver: " + e.getMessage(), e);
            }

            mBluetoothBroadcastReceiver = null;
            mInitialized = false;
        }
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    /**
     * Enables/disables bluetooth.
     * @param enable If true, will enable. If false, will disable.
     */
    public void setBluetoothEnabled(boolean enable) {
        if (mBluetoothAdapter != null) {
            if (enable) {
                mBluetoothAdapter.enable();
            } else {
                mBluetoothAdapter.disable();
            }
        }
    }

    public BluetoothAdapter getBluetoothAdapter()
    {
        return mBluetoothAdapter;
    }

    public String getBluetoothAddress() {
        return mBluetoothAdapter == null ? null : mBluetoothAdapter.getAddress();
    }

    public String getName() {
        return mBluetoothAdapter == null ? null : mBluetoothAdapter.getName();
    }

    public BluetoothDevice getRemoteDevice(String address) {
        return mBluetoothAdapter == null ? null : mBluetoothAdapter.getRemoteDevice(address);
    }

    /**
     * Broadcast receiver for Bluetooth adapter scan mode changes.
     */
    private class BluetoothModeBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                if (mBluetoothAdapterScanModeListener != null) {
                    mBluetoothAdapterScanModeListener.onBluetoothAdapterScanModeChanged(mode);
                }
            }
        }
    }
}
