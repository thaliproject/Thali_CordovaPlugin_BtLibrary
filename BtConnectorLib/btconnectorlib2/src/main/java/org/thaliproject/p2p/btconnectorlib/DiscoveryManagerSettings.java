/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager.DiscoveryMode;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractSettings;

/**
 * Discovery manager settings.
 * Manages all discovery manager settings except for the discovery mode.
 */
public class DiscoveryManagerSettings extends AbstractSettings {
    public interface Listener {
        boolean onDiscoveryModeChanged(DiscoveryMode discoveryMode, boolean forceRestart);
        void onPeerExpirationSettingChanged(long peerExpirationInMilliseconds);
        void onAdvertiseSettingsChanged(int advertiseMode, int advertiseTxPowerLevel);
        void onScanModeSettingChanged(int scanMode);
    }

    // Default settings
    public static final DiscoveryMode DEFAULT_DISCOVERY_MODE = DiscoveryMode.BLE;
    public static final long DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS = 60000;
    public static final int DEFAULT_ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    public static final int DEFAULT_ADVERTISE_TX_POWER_LEVEL = AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
    public static int DEFAULT_SCAN_MODE = ScanSettings.SCAN_MODE_BALANCED;
    public static int DEFAULT_DEVICE_DISCOVERABLE_DURATION_IN_SECONDS = 60;

    // Keys for shared preferences
    private static final String KEY_BLUETOOTH_MAC_ADDRESS = "bluetooth_mac_address";
    private static final String KEY_DISCOVERY_MODE = "discovery_mode";
    private static final String KEY_PEER_EXPIRATION = "peer_expiration";
    private static final String KEY_ADVERTISE_MODE = "advertise_mode";
    private static final String KEY_ADVERTISE_TX_POWER_LEVEL = "advertise_tx_power_level";
    private static final String KEY_SCAN_MODE = "scan_mode";

    private static final int DISCOVERY_MODE_NOT_SET = -1;
    private static final int DISCOVERY_MODE_BLE = 0;
    private static final int DISCOVERY_MODE_WIFI = 1;
    private static final int DISCOVERY_MODE_BLE_AND_WIFI = 2;

    private static final String TAG = DiscoveryManagerSettings.class.getName();

    private static DiscoveryManagerSettings mInstance = null;
    private Listener mListener = null;
    private String mBluetoothMacAddress = null;
    private DiscoveryMode mDiscoveryMode = DEFAULT_DISCOVERY_MODE;
    private long mPeerExpirationInMilliseconds = DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS;
    private int mAdvertiseMode = DEFAULT_ADVERTISE_MODE;
    private int mAdvertiseTxPowerLevel = DEFAULT_ADVERTISE_TX_POWER_LEVEL;
    private int mScanMode = DEFAULT_SCAN_MODE;


    /**
     * @return The singleton instance of this class.
     * @param context The application context for the shared preferences.
     */
    public static DiscoveryManagerSettings getInstance(Context context) {
        if (mInstance == null) {
            mContext = context;
            mInstance = new DiscoveryManagerSettings();
        }

        return mInstance;
    }

    /**
     * Private constructor.
     */
    private DiscoveryManagerSettings() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSharedPreferencesEditor = mSharedPreferences.edit();
    }

    /**
     * Sets the listener. Note: Only the discovery manager can act as a listener.
     * @param discoveryManager The discovery manager instance.
     */
    public void setListener(DiscoveryManager discoveryManager) {
        mListener = discoveryManager;
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
        if (mBluetoothMacAddress == null || !mBluetoothMacAddress.equals(bluetoothMacAddress)) {
            Log.i(TAG, "setBluetoothMacAddress: " + bluetoothMacAddress);
            mBluetoothMacAddress = bluetoothMacAddress;
            mSharedPreferencesEditor.putString(KEY_BLUETOOTH_MAC_ADDRESS, mBluetoothMacAddress);
            mSharedPreferencesEditor.apply();
        }
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

        if (mListener != null) {
            wasSet = mListener.onDiscoveryModeChanged(discoveryMode, forceRestart);

            if (wasSet) {
                mDiscoveryMode = discoveryMode;
                mSharedPreferencesEditor.putInt(KEY_DISCOVERY_MODE, discoveryModeToInt(mDiscoveryMode));
                mSharedPreferencesEditor.apply();
            }
        } else {
            Log.e(TAG, "setDiscoveryMode: Cannot set discovery mode, if no listener is present");
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
        mPeerExpirationInMilliseconds = peerExpirationInMilliseconds;
        mSharedPreferencesEditor.putLong(KEY_PEER_EXPIRATION, mPeerExpirationInMilliseconds);
        mSharedPreferencesEditor.apply();

        if (mListener != null) {
            mListener.onPeerExpirationSettingChanged(mPeerExpirationInMilliseconds);
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
            Log.d(TAG, "setAdvertiseMode: " + advertiseMode);
            mAdvertiseMode = advertiseMode;
            mSharedPreferencesEditor.putInt(KEY_ADVERTISE_MODE, mAdvertiseMode);
            mSharedPreferencesEditor.apply();

            if (mListener != null) {
                mListener.onAdvertiseSettingsChanged(mAdvertiseMode, mAdvertiseTxPowerLevel);
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
            Log.d(TAG, "setAdvertiseTxPowerLevel: " + advertiseTxPowerLevel);
            mAdvertiseTxPowerLevel = advertiseTxPowerLevel;
            mSharedPreferencesEditor.putInt(KEY_ADVERTISE_TX_POWER_LEVEL, mAdvertiseTxPowerLevel);
            mSharedPreferencesEditor.apply();

            if (mListener != null) {
                mListener.onAdvertiseSettingsChanged(mAdvertiseMode, mAdvertiseTxPowerLevel);
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
            Log.d(TAG, "setScanMode: " + scanMode);
            mScanMode = scanMode;
            mSharedPreferencesEditor.putInt(KEY_SCAN_MODE, mScanMode);
            mSharedPreferencesEditor.apply();

            if (mListener != null) {
                mListener.onScanModeSettingChanged(mScanMode);
            }
        }
    }

    @Override
    public void load() {
        mBluetoothMacAddress = mSharedPreferences.getString(KEY_BLUETOOTH_MAC_ADDRESS, null);
        int discoveryModeAsInt = mSharedPreferences.getInt(KEY_DISCOVERY_MODE, discoveryModeToInt(DEFAULT_DISCOVERY_MODE));
        mDiscoveryMode = intToDiscoveryMode(discoveryModeAsInt);
        mPeerExpirationInMilliseconds = mSharedPreferences.getLong(KEY_PEER_EXPIRATION, DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS);
        mAdvertiseMode = mSharedPreferences.getInt(KEY_ADVERTISE_MODE, DEFAULT_ADVERTISE_MODE);
        mAdvertiseTxPowerLevel = mSharedPreferences.getInt(
                KEY_ADVERTISE_TX_POWER_LEVEL, DEFAULT_ADVERTISE_TX_POWER_LEVEL);
        mScanMode = mSharedPreferences.getInt(KEY_SCAN_MODE, DEFAULT_SCAN_MODE);


        Log.i(TAG, "load: "
                + mBluetoothMacAddress + ", "
                + mDiscoveryMode + ", "
                + mPeerExpirationInMilliseconds + ", "
                + mAdvertiseMode + ", "
                + mAdvertiseTxPowerLevel + ", "
                + mScanMode);
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
