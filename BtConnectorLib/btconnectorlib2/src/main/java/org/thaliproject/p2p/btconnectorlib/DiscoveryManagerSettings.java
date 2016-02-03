/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager.DiscoveryMode;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import java.util.ArrayList;

/**
 * Discovery manager settings.
 * Manages all discovery manager settings except for the discovery mode.
 */
public class DiscoveryManagerSettings extends AbstractSettings {
    public interface Listener {
        /**
         * Called when the desired discovery mode is changed.
         * @param discoveryMode The new discovery mode.
         * @param forceRestart If true, should restart.
         * @return True, if the mode was set successfully. False otherwise.
         */
        boolean onDiscoveryModeChanged(DiscoveryMode discoveryMode, boolean forceRestart);

        /**
         * Called when the peer expiration time is changed.
         * @param peerExpirationInMilliseconds The new peer expiration time in milliseconds.
         */
        void onPeerExpirationSettingChanged(long peerExpirationInMilliseconds);

        /**
         * Called when the advertise settings are changed.
         * @param advertiseMode The new advertise mode.
         * @param advertiseTxPowerLevel The new advertise TX power level.
         */
        void onAdvertiseSettingsChanged(int advertiseMode, int advertiseTxPowerLevel);

        /**
         * Called when either the scan mode or the scan report delay is changed.
         * @param scanMode The new scan mode.
         * @param scanReportDelayInMilliseconds The new scan report delay in milliseconds.
         */
        void onScanSettingsChanged(int scanMode, long scanReportDelayInMilliseconds);
    }

    // Default settings
    public static boolean DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION = true;
    public static final long DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS = 40000;
    public static final int DEFAULT_DEVICE_DISCOVERABLE_DURATION_IN_SECONDS = (int)(DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS / 1000);
    public static final DiscoveryMode DEFAULT_DISCOVERY_MODE = DiscoveryMode.BLE;
    public static final long DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS = 60000;
    public static final int DEFAULT_ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    public static final int DEFAULT_ADVERTISE_TX_POWER_LEVEL = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
    public static final int DEFAULT_SCAN_MODE = ScanSettings.SCAN_MODE_BALANCED;
    public static final long DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS = 500;
    public static final long DEFAULT_SCAN_REPORT_DELAY_IN_BACKGROUND_IN_MILLISECONDS = 1000;

    // Keys for shared preferences
    private static final String KEY_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION = "automate_bluetooth_mac_address_resolution";
    private static final String KEY_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS = "provide_bluetooth_mac_address_timeout";
    private static final String KEY_BLUETOOTH_MAC_ADDRESS = "bluetooth_mac_address";
    private static final String KEY_DISCOVERY_MODE = "discovery_mode";
    private static final String KEY_PEER_EXPIRATION = "peer_expiration";
    private static final String KEY_ADVERTISE_MODE = "advertise_mode";
    private static final String KEY_ADVERTISE_TX_POWER_LEVEL = "advertise_tx_power_level";
    private static final String KEY_SCAN_MODE = "scan_mode";
    private static final String KEY_SCAN_REPORT_DELAY_IN_MILLISECONDS = "scan_report_delay";

    private static final int DISCOVERY_MODE_NOT_SET = -1;
    private static final int DISCOVERY_MODE_BLE = 0;
    private static final int DISCOVERY_MODE_WIFI = 1;
    private static final int DISCOVERY_MODE_BLE_AND_WIFI = 2;

    private static final String TAG = DiscoveryManagerSettings.class.getName();

    private static DiscoveryManagerSettings mInstance = null;
    private final ArrayList<Listener> mListeners = new ArrayList<>();
    private boolean mAutomateBluetoothMacAddressResolution = DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION;
    private String mBluetoothMacAddress = null;
    private DiscoveryMode mDiscoveryMode = DEFAULT_DISCOVERY_MODE;
    private long mPeerExpirationInMilliseconds = DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS;
    private int mAdvertiseMode = DEFAULT_ADVERTISE_MODE;
    private int mAdvertiseTxPowerLevel = DEFAULT_ADVERTISE_TX_POWER_LEVEL;
    private int mScanMode = DEFAULT_SCAN_MODE;
    private long mScanReportDelayInMilliseconds = DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS;
    private long mProvideBluetoothMacAddressTimeoutInMilliseconds = DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS;

    /**
     * @return The singleton instance of this class.
     * @param context The application context for the shared preferences.
     */
    public static DiscoveryManagerSettings getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiscoveryManagerSettings(context);
        }

        return mInstance;
    }

    /**
     * Private constructor.
     */
    private DiscoveryManagerSettings(Context context) {
        super(context); // Will create Shared preferences (and editor) instance
    }

    /**
     * Adds a listener. In the ideal situation there is only one discovery manager and thus, one
     * listener. However, for testing we might need to use multiple.
     *
     * Note: Only the discovery manager can act as a listener.
     *
     * @param discoveryManager The discovery manager instance.
     */
    /* Package */ void addListener(DiscoveryManager discoveryManager) {
        if (discoveryManager != null) {
            Listener listener = (Listener) discoveryManager;

            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
                Log.v(TAG, "addListener: Listener " + listener + " added. We now have " + mListeners.size() + " listener(s)");
            } else {
                Log.e(TAG, "addListener: Listener " + listener + " already in the list");
            }
        }
    }

    /**
     * Removes the given listener from the list.
     * @param discoveryManager The listener to remove.
     */
    /* Package */ void removeListener(DiscoveryManager discoveryManager) {
        if (discoveryManager != null && mListeners.size() > 0) {
            Listener listener = (Listener) discoveryManager;

            if (mListeners.remove(listener)) {
                Log.v(TAG, "removeListener: Listener " + listener + " removed from the list");
            } else {
                Log.e(TAG, "removeListener: Listener " + listener + " not in the list");
            }
        }
    }

    /**
     * @return True, if the Bluetooth MAC address resolution should be automated.
     */
    public boolean getAutomateBluetoothMacAddressResolution() {
        return mAutomateBluetoothMacAddressResolution;
    }

    /**
     * Enables/disables Bluetooth MAC address automation.
     * @param automate If true, Bluetooth MAC address resolution should be automated.
     */
    public void setAutomateBluetoothMacAddressResolution(boolean automate) {
        if (mAutomateBluetoothMacAddressResolution != automate) {
            Log.i(TAG, "setAutomateBluetoothMacAddressResolution: " + automate);
            mAutomateBluetoothMacAddressResolution = automate;
            mSharedPreferencesEditor.putBoolean(
                    KEY_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION, mAutomateBluetoothMacAddressResolution);
            mSharedPreferencesEditor.apply();
        }
    }

    /**
     * @return The maximum duration of "Provide Bluetooth MAC address" in milliseconds.
     */
    public long getProvideBluetoothMacAddressTimeout() {
        return mProvideBluetoothMacAddressTimeoutInMilliseconds;
    }

    /**
     * Sets the maximum duration of "Provide Bluetooth MAC address" in milliseconds.
     * @param provideBluetoothMacAddressTimeoutInMilliseconds The maximum duration of "Provide Bluetooth MAC address" in milliseconds.
     */
    public void setProvideBluetoothMacAddressTimeout(long provideBluetoothMacAddressTimeoutInMilliseconds) {
        if (mProvideBluetoothMacAddressTimeoutInMilliseconds != provideBluetoothMacAddressTimeoutInMilliseconds) {
            Log.i(TAG, "setProvideBluetoothMacAddressTimeout: "
                    + mProvideBluetoothMacAddressTimeoutInMilliseconds
                    + " -> " + provideBluetoothMacAddressTimeoutInMilliseconds);

            mProvideBluetoothMacAddressTimeoutInMilliseconds = provideBluetoothMacAddressTimeoutInMilliseconds;
            mSharedPreferencesEditor.putLong(
                    KEY_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS, mProvideBluetoothMacAddressTimeoutInMilliseconds);
            mSharedPreferencesEditor.apply();
        }
    }

    /**
     * @return The Bluetooth MAC address of this device or null, if not resolved.
     */
    public String getBluetoothMacAddress() {
        return mBluetoothMacAddress;
    }

    /**
     * Stores the unique Bluetooth MAC address of this device.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     */
    public void setBluetoothMacAddress(String bluetoothMacAddress) {
        if (BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)
            && (mBluetoothMacAddress == null || !mBluetoothMacAddress.equals(bluetoothMacAddress))) {
            Log.i(TAG, "setBluetoothMacAddress: " + bluetoothMacAddress);
            mBluetoothMacAddress = bluetoothMacAddress;
            mSharedPreferencesEditor.putString(KEY_BLUETOOTH_MAC_ADDRESS, mBluetoothMacAddress);
            mSharedPreferencesEditor.apply();
        }
    }

    /**
     * Clears the stored Bluetooth MAC address.
     * Can be used for testing purposes.
     */
    public void clearBluetoothMacAddress() {
        Log.i(TAG, "clearBluetoothMacAddress: The Bluetooth MAC address was \"" + mBluetoothMacAddress + "\"");
        mBluetoothMacAddress = null;
        mSharedPreferencesEditor.putString(KEY_BLUETOOTH_MAC_ADDRESS, mBluetoothMacAddress);
        mSharedPreferencesEditor.apply();
    }

    /**
     * @return The current discovery mode.
     */
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
        boolean wasSet = false;

        if (mDiscoveryMode != discoveryMode) {
            Log.i(TAG, "setDiscoveryMode: " + mDiscoveryMode + " -> " + discoveryMode);
            DiscoveryMode previousDiscoveryMode = mDiscoveryMode;

            if (mListeners.size() > 0) {
                previousDiscoveryMode = mDiscoveryMode;
                mDiscoveryMode = discoveryMode;
                wasSet = true;

                for (Listener listener : mListeners) {
                    if (!listener.onDiscoveryModeChanged(discoveryMode, forceRestart)) {
                        wasSet = false;
                    }
                }
            } else {
                Log.w(TAG, "setDiscoveryMode: Setting the discovery mode, but cannot verify if the new mode is supported");
                mDiscoveryMode = discoveryMode;
                wasSet = true;
            }

            if (wasSet) {
                mSharedPreferencesEditor.putInt(KEY_DISCOVERY_MODE, discoveryModeToInt(mDiscoveryMode));
                mSharedPreferencesEditor.apply();
            } else {
                Log.d(TAG, "setDiscoveryMode: Failed to set the discovery mode to "
                        + discoveryMode + ", restoring the previous mode (" + previousDiscoveryMode + ")");
                mDiscoveryMode = previousDiscoveryMode;
            }
        }

        return wasSet;
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
     * @return The peer expiration time in milliseconds.
     */
    public long getPeerExpiration() {
        return mPeerExpirationInMilliseconds;
    }

    /**
     * Sets the peer expiration time. If the given value is zero or less, peers will not expire.
     * @param peerExpirationInMilliseconds The peer expiration time in milliseconds.
     */
    public void setPeerExpiration(long peerExpirationInMilliseconds) {
        if (mPeerExpirationInMilliseconds != peerExpirationInMilliseconds) {
            Log.i(TAG, "setPeerExpiration: " + mPeerExpirationInMilliseconds + " -> " + peerExpirationInMilliseconds);
            mPeerExpirationInMilliseconds = peerExpirationInMilliseconds;
            mSharedPreferencesEditor.putLong(KEY_PEER_EXPIRATION, mPeerExpirationInMilliseconds);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onPeerExpirationSettingChanged(mPeerExpirationInMilliseconds);
                }
            }
        }
    }

    /**
     * @return The Bluetooth LE advertise model.
     */
    public int getAdvertiseMode() {
        return mAdvertiseMode;
    }

    /**
     * Sets the Bluetooth LE advertise mode.
     * @param advertiseMode The advertise mode to set.
     */
    public void setAdvertiseMode(int advertiseMode) {
        if (mAdvertiseMode != advertiseMode) {
            Log.i(TAG, "setAdvertiseMode: " + mAdvertiseMode + " -> " + advertiseMode);
            mAdvertiseMode = advertiseMode;
            mSharedPreferencesEditor.putInt(KEY_ADVERTISE_MODE, mAdvertiseMode);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onAdvertiseSettingsChanged(mAdvertiseMode, mAdvertiseTxPowerLevel);
                }
            }
        }
    }

    /**
     * @return The Bluetooth LE advertise TX power level.
     */
    public int getAdvertiseTxPowerLevel() {
        return mAdvertiseTxPowerLevel;
    }

    /**
     * Sets the Bluetooth LE advertise TX power level.
     * @param advertiseTxPowerLevel The power level to set.
     */
    public void setAdvertiseTxPowerLevel(int advertiseTxPowerLevel) {
        if (mAdvertiseTxPowerLevel != advertiseTxPowerLevel) {
            Log.i(TAG, "setAdvertiseTxPowerLevel: " + mAdvertiseTxPowerLevel + " -> " + advertiseTxPowerLevel);
            mAdvertiseTxPowerLevel = advertiseTxPowerLevel;
            mSharedPreferencesEditor.putInt(KEY_ADVERTISE_TX_POWER_LEVEL, mAdvertiseTxPowerLevel);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onAdvertiseSettingsChanged(mAdvertiseMode, mAdvertiseTxPowerLevel);
                }
            }
        }
    }

    /**
     * @return The Bluetooth LE scan mode.
     */
    public int getScanMode() {
        return mScanMode;
    }

    /**
     * Sets the Bluetooth LE scan mode.
     * @param scanMode The scan mode to set.
     */
    public void setScanMode(int scanMode) {
        if (mScanMode != scanMode) {
            Log.i(TAG, "setScanMode: " + mScanMode + " -> " + scanMode);
            mScanMode = scanMode;
            mSharedPreferencesEditor.putInt(KEY_SCAN_MODE, mScanMode);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onScanSettingsChanged(mScanMode, mScanReportDelayInMilliseconds);
                }
            }
        }
    }

    /**
     * @return The scan report delay in milliseconds.
     */
    public long getScanReportDelay() {
        return mScanReportDelayInMilliseconds;
    }

    /**
     * Sets the scan report delay.
     * @param scanReportDelayInMilliseconds The scan report delay in milliseconds.
     */
    public void setScanReportDelay(long scanReportDelayInMilliseconds) {
        if (mScanReportDelayInMilliseconds != scanReportDelayInMilliseconds
                && scanReportDelayInMilliseconds >= 0) {
            Log.i(TAG, "setScanReportDelay: " + mScanReportDelayInMilliseconds + " -> " + scanReportDelayInMilliseconds);
            mScanReportDelayInMilliseconds = scanReportDelayInMilliseconds;
            mSharedPreferencesEditor.putLong(KEY_SCAN_REPORT_DELAY_IN_MILLISECONDS, mScanReportDelayInMilliseconds);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onScanSettingsChanged(mScanMode, mScanReportDelayInMilliseconds);
                }
            }
        }
    }

    @Override
    public void load() {
        mAutomateBluetoothMacAddressResolution = mSharedPreferences.getBoolean(
                KEY_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION, DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION);
        mProvideBluetoothMacAddressTimeoutInMilliseconds = mSharedPreferences.getLong(
                KEY_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS, DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS);
        mBluetoothMacAddress = mSharedPreferences.getString(KEY_BLUETOOTH_MAC_ADDRESS, null);
        int discoveryModeAsInt = mSharedPreferences.getInt(KEY_DISCOVERY_MODE, discoveryModeToInt(DEFAULT_DISCOVERY_MODE));
        mDiscoveryMode = intToDiscoveryMode(discoveryModeAsInt);
        mPeerExpirationInMilliseconds = mSharedPreferences.getLong(
                KEY_PEER_EXPIRATION, DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS);
        mAdvertiseMode = mSharedPreferences.getInt(KEY_ADVERTISE_MODE, DEFAULT_ADVERTISE_MODE);
        mAdvertiseTxPowerLevel = mSharedPreferences.getInt(
                KEY_ADVERTISE_TX_POWER_LEVEL, DEFAULT_ADVERTISE_TX_POWER_LEVEL);
        mScanMode = mSharedPreferences.getInt(KEY_SCAN_MODE, DEFAULT_SCAN_MODE);
        mScanReportDelayInMilliseconds = mSharedPreferences.getLong(
                KEY_SCAN_REPORT_DELAY_IN_MILLISECONDS, DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS);

        Log.v(TAG, "load: "
                + "\n\tAutomate Bluetooth MAC address resolution: " + mAutomateBluetoothMacAddressResolution + ", "
                + "\n\tProvide Bluetooth MAC address timeout in milliseconds: " + mProvideBluetoothMacAddressTimeoutInMilliseconds + ", "
                + "\n\tBluetooth MAC address: " + mBluetoothMacAddress + ", "
                + "\n\tDiscovery mode: " + mDiscoveryMode + ", "
                + "\n\tPeer expiration time in milliseconds: " + mPeerExpirationInMilliseconds + ", "
                + "\n\tAdvertise mode: " + mAdvertiseMode + ", "
                + "\n\tAdvertise TX power level: " + mAdvertiseTxPowerLevel + ", "
                + "\n\tScan mode: " + mScanMode + ", "
                + "\n\tScan report delay in milliseconds: " + mScanReportDelayInMilliseconds);
    }

    @Override
    public void resetDefaults() {
        Log.i(TAG, "resetDefaults");
        setAutomateBluetoothMacAddressResolution(DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION);
        setProvideBluetoothMacAddressTimeout(DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS);
        setBluetoothMacAddress(null);
        setDiscoveryMode(DEFAULT_DISCOVERY_MODE, true);
        setPeerExpiration(DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS);
        setAdvertiseMode(DEFAULT_ADVERTISE_MODE);
        setAdvertiseTxPowerLevel(DEFAULT_ADVERTISE_TX_POWER_LEVEL);
        setScanMode(DEFAULT_SCAN_MODE);
        setScanReportDelay(DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS);
    }

    /**
     * Converts the given discovery mode to integer.
     * @param discoveryMode The discovery mode to convert.
     * @return The discovery mode as integer.
     */
    private int discoveryModeToInt(DiscoveryMode discoveryMode) {
        switch (discoveryMode) {
            case NOT_SET: return DISCOVERY_MODE_NOT_SET;
            case BLE: return DISCOVERY_MODE_BLE;
            case WIFI: return DISCOVERY_MODE_WIFI;
            case BLE_AND_WIFI: return DISCOVERY_MODE_BLE_AND_WIFI;
            default:
                Log.e(TAG, "discoveryModeToInt: Unknown discovery mode: " + discoveryMode);
                break;
        }

        return DISCOVERY_MODE_NOT_SET;
    }

    /**
     * Converts the given integer to DiscoveryMode.
     * @param discoveryModeAsInt The discovery mode as integer.
     * @return The discovery mode.
     */
    private DiscoveryMode intToDiscoveryMode(int discoveryModeAsInt) {
        switch (discoveryModeAsInt) {
            case DISCOVERY_MODE_NOT_SET: return DiscoveryMode.NOT_SET;
            case DISCOVERY_MODE_BLE: return DiscoveryMode.BLE;
            case DISCOVERY_MODE_WIFI: return DiscoveryMode.WIFI;
            case DISCOVERY_MODE_BLE_AND_WIFI: return DiscoveryMode.BLE_AND_WIFI;
            default:
                Log.e(TAG, "intToDiscoveryMode: Invalid argument: " + discoveryModeAsInt);
                break;
        }

        return DEFAULT_DISCOVERY_MODE;
    }
}
