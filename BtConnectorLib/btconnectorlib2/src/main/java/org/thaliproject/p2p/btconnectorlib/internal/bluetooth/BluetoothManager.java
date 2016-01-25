/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import java.util.ArrayList;

/**
 * Manages the device Bluetooth settings and provides information on Bluetooth status on the
 * device.
 */
public class BluetoothManager {
    /**
     * A listener interface for Bluetooth scan ode changes.
     */
    public interface BluetoothManagerListener {
        /**
         * Called when the mode of the Bluetooth adapter changes.
         * @param mode The new mode.
         */
        void onBluetoothAdapterScanModeChanged(int mode);
    }

    private static final String TAG = BluetoothManager.class.getName();
    private static BluetoothManager mInstance = null;
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final ArrayList<BluetoothManagerListener> mListeners = new ArrayList<>();
    private BluetoothModeBroadCastReceiver mBluetoothBroadcastReceiver = null;
    private boolean mInitialized = false;

    /**
     * Getter for the singleton instance of this class.
     * @param context The application context.
     * @return The singleton instance of this class.
     */
    public static BluetoothManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BluetoothManager(context);
        }

        return mInstance;
    }

    /**
     * Constructor.
     * @param context The application context.
     */
    private BluetoothManager(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Binds the given listener to this instance. If already bound, this method does nothing excepts
     * verifies the Bluetooth support.
     *
     * Note that the listener acts as a sort of a reference counter. You must call release() after
     * you're done using the instance.
     *
     * @param listener The listener.
     * @return True, if bound successfully (or already bound). If false is returned, this could
     * indicate the lack of Bluetooth hardware support.
     */
    public boolean bind(BluetoothManagerListener listener) {
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
    public void release(BluetoothManagerListener listener) {
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
     * Checks whether the device has a Bluetooth support or not.
     * @return True, if the device supports Bluetooth. False otherwise.
     */
    public boolean isBluetoothSupported() {
        boolean isSupported = initialize();

        if (mListeners.size() == 0) {
            // No reason to keep the broadcast receiver around since there is no-one to listen to it
            deinitialize();
        }

        return isSupported;
    }

    /**
     * @return True, if the Bluetooth LE is supported.
     */
    public boolean isBleSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * @return True, if the multi advertisement is supported by the chipset.
     */
    @TargetApi(21)
    public boolean isBleMultipleAdvertisementSupported() {
        boolean isSupported = false;

        if (CommonUtils.isLollipopOrHigher()) {
            isSupported = mBluetoothAdapter.isMultipleAdvertisementSupported();
        } else {
            Log.d(TAG, "isBleMultipleAdvertisementSupported: The build version of the device is too low - API level 21 or higher required");
        }

        return isSupported;
    }

    /**
     * @return True, if Bluetooth is enabled.
     */
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

    /**
     * @return The Bluetooth MAC address or null, if no Bluetooth adapter instance or if the Android
     * version is higher than Lollipop (5.x).
     */
    public String getBluetoothMacAddress() {
        String bluetoothMacAddress = null;

        if (!CommonUtils.isMarshmallowOrHigher() && mBluetoothAdapter != null) {
            bluetoothMacAddress = mBluetoothAdapter.getAddress();
        } else {
            //Log.v(TAG, "getBluetoothMacAddress: Cannot retrieve our own Bluetooth MAC address from the Bluetooth adapter when running on Marshmallow (6.x) or higher Android version");
        }

        return bluetoothMacAddress;
    }

    public String getBluetoothName() {
        return mBluetoothAdapter == null ? null : mBluetoothAdapter.getName();
    }

    public BluetoothDevice getRemoteDevice(String address) {
        return mBluetoothAdapter == null ? null : mBluetoothAdapter.getRemoteDevice(address);
    }

    /**
     * Registers the broadcast receiver to listen to Bluetooth scan mode changes.
     * @return True, if successfully initialized (even if that the initialization was done earlier).
     * If false is returned, this could indicate the lack of Bluetooth hardware support.
     */
    private synchronized boolean initialize() {
        if (!mInitialized) {
            if (mBluetoothAdapter != null) {
                Log.i(TAG, "initialize: My bluetooth address is " + getBluetoothMacAddress());

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
        }

        return mInitialized;
    }

    /**
     * Unregisters and disposes the broadcast receiver listening to bluetooth adapter mode changes.
     */
    private synchronized void deinitialize() {
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

    /**
     * Broadcast receiver for Bluetooth adapter scan mode changes.
     */
    private class BluetoothModeBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                for (BluetoothManagerListener listener : mListeners) {
                    listener.onBluetoothAdapterScanModeChanged(mode);
                }
            }
        }
    }
}
