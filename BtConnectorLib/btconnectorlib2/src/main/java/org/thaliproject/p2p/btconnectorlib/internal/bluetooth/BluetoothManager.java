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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the device Bluetooth settings and provides information on the status of the Bluetooth
 * device.
 */
public class BluetoothManager {
    /**
     * A listener interface for Bluetooth scan mode changes.
     */
    public interface BluetoothManagerListener {
        /**
         * Called when the mode of the Bluetooth adapter changes.
         *
         * @param mode The new mode.
         */
        void onBluetoothAdapterScanModeChanged(int mode);

        /**
         * Called when the state of the Bluetooth adapter changes.
         *
         * @param state The new state.
         */
        void onBluetoothAdapterStateChanged(int state);
    }

    public enum FeatureSupportedStatus {
        NOT_RESOLVED,
        NOT_SUPPORTED,
        SUPPORTED
    }

    /**
     * Helper class for checking features support
     */
    private abstract class FeatureSupportChecker {

        protected abstract String getFeatureName();
        protected abstract String getSharedPreferencesKey();
        protected abstract FeatureSupportedStatus getCurrentStatus();
        protected abstract void setCurrentStatus(FeatureSupportedStatus status);

        public FeatureSupportedStatus isSupported() {

            if (!CommonUtils.isLollipopOrHigher()) {
                Log.d(TAG, "Checking support for " + getFeatureName() + ": The build version of the device is too low - API level 21 or higher required");
                return FeatureSupportedStatus.NOT_SUPPORTED;
            }
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "Checking support for " + getFeatureName() + ": No Bluetooth adapter - This may indicate that the device has no Bluetooth support at all");
                return FeatureSupportedStatus.NOT_SUPPORTED;
            }
            if (!isBluetoothEnabled()) {
                Log.w(TAG, "Checking support for " + getFeatureName() + ": Cannot do the check when the Bluetooth is disabled - will return stored value");
                return getCurrentStatus();
            }

            FeatureSupportedStatus featureSupportedStatus = isFeatureSupported() ?
                    FeatureSupportedStatus.SUPPORTED : FeatureSupportedStatus.NOT_SUPPORTED;

            if (getCurrentStatus() == FeatureSupportedStatus.NOT_RESOLVED
                    || getCurrentStatus() != featureSupportedStatus) {
                // Store the value in case this is queried sometime when the Bluetooth is disabled
                setCurrentStatus(featureSupportedStatus);

                Log.v(TAG, "Checking support for " + getFeatureName() + ": Storing the value ("
                        + getCurrentStatus() + ") in persistent storage");

                mSharedPreferencesEditor.putInt(
                        getSharedPreferencesKey(),
                        featureSupportedStatusToInt(getCurrentStatus()));
                mSharedPreferencesEditor.apply();
            }

            return featureSupportedStatus;
        }

        protected abstract boolean isFeatureSupported();
    }

    private class MultipleAdvertisementSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "multiple advertisement";
        }

        @Override
        protected String getSharedPreferencesKey() {
            return KEY_IS_BLUETOOTH_LE_MULTI_ADVERTISEMENT_SUPPORTED;
        }

        @Override
        protected FeatureSupportedStatus getCurrentStatus() {
            return mBleMultipleAdvertisementSupportedStatus;
        }

        @Override
        protected void setCurrentStatus(FeatureSupportedStatus status) {
            mBleMultipleAdvertisementSupportedStatus = status;
        }

        @Override
        protected boolean isFeatureSupported() {
            return mBluetoothAdapter.isMultipleAdvertisementSupported();
        }
    }

    private class ScanBatchingSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "offloaded scan batching";
        }

        @Override
        protected String getSharedPreferencesKey() {
            return KEY_IS_BLUETOOTH_LE_OFFLOADED_SCAN_BATCHING_SUPPORTED;
        }

        @Override
        protected FeatureSupportedStatus getCurrentStatus() {
            return mBleScanBatchingSupportedStatus;
        }

        @Override
        protected void setCurrentStatus(FeatureSupportedStatus status) {
            mBleScanBatchingSupportedStatus = status;
        }

        @Override
        protected boolean isFeatureSupported() {
            return mBluetoothAdapter.isOffloadedScanBatchingSupported();
        }
    }

    private class FilteringSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "offloaded filtering";
        }

        @Override
        protected String getSharedPreferencesKey() {
            return KEY_IS_BLUETOOTH_LE_OFFLOADED_FILTERING_SUPPORTED;
        }

        @Override
        protected FeatureSupportedStatus getCurrentStatus() {
            return mBleFilteringSupportedStatus;
        }

        @Override
        protected void setCurrentStatus(FeatureSupportedStatus status) {
            mBleFilteringSupportedStatus = status;
        }

        @Override
        protected boolean isFeatureSupported() {
            return mBluetoothAdapter.isOffloadedFilteringSupported();
        }
    }

    private static final String TAG = BluetoothManager.class.getName();
    private static final String KEY_IS_BLUETOOTH_LE_MULTI_ADVERTISEMENT_SUPPORTED = "is_bluetooth_le_multi_advertisement_supported";
    private static final String KEY_IS_BLUETOOTH_LE_OFFLOADED_SCAN_BATCHING_SUPPORTED = "is_bluetooth_le_offloaded_scan_batching_supported";
    private static final String KEY_IS_BLUETOOTH_LE_OFFLOADED_FILTERING_SUPPORTED = "is_bluetooth_le_offloaded_filtering_supported";
    private static BluetoothManager mInstance = null;
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final CopyOnWriteArrayList<BluetoothManagerListener> mListeners = new CopyOnWriteArrayList<>();
    private BluetoothModeBroadcastReceiver mBluetoothBroadcastReceiver = null;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mSharedPreferencesEditor;
    private boolean mInitialized = false;
    private FeatureSupportedStatus mBleMultipleAdvertisementSupportedStatus = FeatureSupportedStatus.NOT_RESOLVED;
    private FeatureSupportedStatus mBleScanBatchingSupportedStatus = FeatureSupportedStatus.NOT_RESOLVED;
    private FeatureSupportedStatus mBleFilteringSupportedStatus = FeatureSupportedStatus.NOT_RESOLVED;

    /**
     * Getter for the singleton instance of this class.
     *
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
     * Getter for the singleton instance of this class.
     *
     * @param context The application context.
     * @param bluetoothAdapter The bluetooth adapter.
     * @param sharedPreferences The shared preferences.
     * @return The singleton instance of this class.
     */
    public static BluetoothManager getInstance(Context context, BluetoothAdapter bluetoothAdapter,
                                               SharedPreferences sharedPreferences) {
        if (mInstance == null) {
            mInstance = new BluetoothManager(context, bluetoothAdapter, sharedPreferences);
        }

        return mInstance;
    }

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    private BluetoothManager(Context context) {
        this(context, BluetoothAdapter.getDefaultAdapter(),
                PreferenceManager.getDefaultSharedPreferences(context));
    }

    /**
     * Constructor.
     *
     * @param context The application context.
     * @param bluetoothAdapter The bluetooth adapter.
     * @param sharedPreferences The shared preferences.
     */
    private BluetoothManager(Context context, BluetoothAdapter bluetoothAdapter,
                             SharedPreferences sharedPreferences) {
        mContext = context;
        mBluetoothAdapter = bluetoothAdapter;
        mSharedPreferences = sharedPreferences;
        mSharedPreferencesEditor = mSharedPreferences.edit();

        mBleMultipleAdvertisementSupportedStatus =
                readStatusFromSharedPreferences(KEY_IS_BLUETOOTH_LE_MULTI_ADVERTISEMENT_SUPPORTED);
        mBleScanBatchingSupportedStatus =
                readStatusFromSharedPreferences(KEY_IS_BLUETOOTH_LE_OFFLOADED_SCAN_BATCHING_SUPPORTED);
        mBleFilteringSupportedStatus =
                readStatusFromSharedPreferences(KEY_IS_BLUETOOTH_LE_OFFLOADED_FILTERING_SUPPORTED);

    }

    /**
     * Helper method to read feature status from shared preferences
     *
     * @param key The key to look for in shared preferences.
     * @return Feature status or FeatureSupportedStatus.NOT_RESOLVED if given key is not found.
     */
    private FeatureSupportedStatus readStatusFromSharedPreferences(String key) {
        return intToFeatureSupportedStatus(mSharedPreferences.getInt(key,
                        featureSupportedStatusToInt(FeatureSupportedStatus.NOT_RESOLVED)));
    }

    /**
     * Binds the given listener to this instance. If already bound, this method does nothing except
     * verifies Bluetooth support.
     *
     * Note that the listener acts as a sort of a reference counter. You must call release() after
     * you're done using the instance.
     *
     * @param listener The listener.
     * @return True, if bound successfully (or already bound). If false is returned, this could
     * indicate the lack of Bluetooth hardware support.
     */
    public synchronized boolean bind(BluetoothManagerListener listener) {
        if (!mListeners.contains(listener)) {
            Log.i(TAG, "bind: Binding a new listener");
            mListeners.add(listener);
        }

        return initialize();
    }

    /**
     * Removes the given listener from the list of listeners. If, after this, the list of listeners
     * is empty, there is no reason to keep this instance "running" and we can de-initialize.
     *
     * @param listener The listener to remove.
     */
    public synchronized void release(BluetoothManagerListener listener) {
        if (!mListeners.remove(listener) && mListeners.size() > 0) {
            Log.w(TAG, "release: The given listener does not exist in the list - probably already removed");
        }

        if (mListeners.size() == 0) {
            if (mInitialized) {
                Log.i(TAG, "release: No more listeners, de-initializing...");
            }

            deinitialize();
        } else {
            Log.d(TAG, "release: " + mListeners.size() + " listener(s) left");
        }
    }

    /**
     * Checks whether the device has Bluetooth support or not.
     *
     * @return True, if the device supports Bluetooth. False otherwise.
     */
    public boolean isBluetoothSupported() {
        return (mBluetoothAdapter != null);
    }

    /**
     * @return True, if the Bluetooth LE is supported.
     */
    public boolean isBleSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Checks if the chipset has a support for Bluetooth LE multi advertisement.
     * If Bluetooth is enabled and the feature support status is not resolved before, the value is
     * stored in the persistent storage.
     *
     * @return FeatureSupportedStatus.NOT_RESOLVED, if Bluetooth is disabled and the feature support has not been resolved before.
     *         FeatureSupportedStatus.NOT_SUPPORTED, if not supported.
     *         FeatureSupportedStatus.SUPPORTED, if supported.
     */
    @TargetApi(21)
    public FeatureSupportedStatus isBleMultipleAdvertisementSupported() {
        FeatureSupportChecker checker = new MultipleAdvertisementSupportChecker();
        return checker.isSupported();
    }

    /**
     * Checks if the chipset has a support for Bluetooth LE scan batching.
     * If Bluetooth is enabled and the feature support status is not resolved before, the value is
     * stored in the persistent storage.
     *
     * @return FeatureSupportedStatus.NOT_RESOLVED, if Bluetooth is disabled and the feature support has not been resolved before.
     *         FeatureSupportedStatus.NOT_SUPPORTED, if not supported.
     *         FeatureSupportedStatus.SUPPORTED, if supported.
     */
    @TargetApi(21)
    public FeatureSupportedStatus isBleOffloadedScanBatchingSupported() {
        FeatureSupportChecker checker = new ScanBatchingSupportChecker();
        return checker.isSupported();
    }

    /**
     * Checks if the chipset has a support for Bluetooth LE filtering.
     * If Bluetooth is enabled and the feature support status is not resolved before, the value is
     * stored in the persistent storage.
     *
     * @return FeatureSupportedStatus.NOT_RESOLVED, if Bluetooth is disabled and the feature support has not been resolved before.
     *         FeatureSupportedStatus.NOT_SUPPORTED, if not supported.
     *         FeatureSupportedStatus.SUPPORTED, if supported.
     */
    @TargetApi(21)
    public FeatureSupportedStatus isBleOffloadedFilteringSupported() {
        FeatureSupportChecker checker = new FilteringSupportChecker();
        return checker.isSupported();
    }

    /**
     * @return True, if Bluetooth is enabled.
     */
    public boolean isBluetoothEnabled() {
        return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled());
    }

    /**
     * Enables/disables bluetooth.
     *
     * @param enable If true, will enable. If false, will disable.
     */
    public boolean setBluetoothEnabled(boolean enable) {
        if (mBluetoothAdapter != null) {
            if (enable) {
                return mBluetoothAdapter.enable();
            } else {
                return mBluetoothAdapter.disable();
            }
        }
        throw  new RuntimeException("Bluetooth adapter is null");
    }

    public BluetoothAdapter getBluetoothAdapter()
    {
        return mBluetoothAdapter;
    }

    /**
     * @return The Bluetooth MAC address.
     */
    public String getBluetoothMacAddress() {
        String bluetoothMacAddress = null;

        if (!CommonUtils.isMarshmallowOrHigher() && mBluetoothAdapter != null) {
            bluetoothMacAddress = mBluetoothAdapter.getAddress();
        } else {
            bluetoothMacAddress = android.provider.Settings.Secure.getString(
                    mContext.getContentResolver(), "bluetooth_address");
        }

        return bluetoothMacAddress;
    }

    public String getBluetoothName() {
        return (mBluetoothAdapter == null ? null : mBluetoothAdapter.getName());
    }

    public BluetoothDevice getRemoteDevice(String address) {
        BluetoothDevice bluetoothDevice = null;

        if (mBluetoothAdapter != null) {
            try {
                bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "getRemoteDevice: Failed to get the remote device: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "getRemoteDevice: No Bluetooth adapter instance");
        }

        return bluetoothDevice;
    }

    /**
     * Registers the broadcast receiver to listen to Bluetooth scan mode changes.
     *
     * @return True, if successfully initialized (even if that the initialization was done earlier).
     * If false is returned, this could indicate the lack of Bluetooth hardware support.
     */
    private synchronized boolean initialize() {
        if (!mInitialized) {
            if (mBluetoothAdapter != null) {
                Log.i(TAG, "initialize: My bluetooth address is " + getBluetoothMacAddress());

                mBluetoothBroadcastReceiver = new BluetoothModeBroadcastReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

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

    private int featureSupportedStatusToInt(FeatureSupportedStatus featureSupportedStatus) {
        switch (featureSupportedStatus) {
            case NOT_RESOLVED: return 0;
            case NOT_SUPPORTED: return 1;
            case SUPPORTED: return 2;
            default:
                Log.e(TAG, "featureSupportedStatusToInt: Unrecognized status: " + featureSupportedStatus);
                break;
        }

        return 0;
    }

    private FeatureSupportedStatus intToFeatureSupportedStatus(int featureSupportedStatusAsInt) {
        switch (featureSupportedStatusAsInt) {
            case 0: return FeatureSupportedStatus.NOT_RESOLVED;
            case 1: return FeatureSupportedStatus.NOT_SUPPORTED;
            case 2: return FeatureSupportedStatus.SUPPORTED;
            default:
                Log.e(TAG, "intToFeatureSupportedStatus: Invalid argument: " + featureSupportedStatusAsInt);
                break;
        }

        return FeatureSupportedStatus.NOT_RESOLVED;
    }

    /**
     * Helper method to resolve feature support if not already resolved
     */
    private void resolveFeatureSupport() {
        Log.d(TAG, "resolveFeatureSupport");

        if (mBleMultipleAdvertisementSupportedStatus == FeatureSupportedStatus.NOT_RESOLVED) {
            // Resolve the BLE multi advertisement support
            isBleMultipleAdvertisementSupported();
        }

        if (mBleScanBatchingSupportedStatus == FeatureSupportedStatus.NOT_RESOLVED) {
            // Resolve the BLE scan batching support
            isBleOffloadedScanBatchingSupported();
        }

        if (mBleFilteringSupportedStatus == FeatureSupportedStatus.NOT_RESOLVED) {
            // Resolve the BLE filtering support
            isBleOffloadedFilteringSupported();
        }
    }

    /**
     * Broadcast receiver for Bluetooth adapter scan mode changes.
     */
    private class BluetoothModeBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                if (mode != BluetoothAdapter.SCAN_MODE_NONE) {
                    resolveFeatureSupport();
                }

                for (BluetoothManagerListener listener : mListeners) {
                    listener.onBluetoothAdapterScanModeChanged(mode);
                }
            }

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    resolveFeatureSupport();
                }
                //TODO add some logic detect that user turned bluetooth off and it didn't just crash
                for (BluetoothManagerListener listener : mListeners) {
                    listener.onBluetoothAdapterStateChanged(state);
                }
            }
        }
    }
}
