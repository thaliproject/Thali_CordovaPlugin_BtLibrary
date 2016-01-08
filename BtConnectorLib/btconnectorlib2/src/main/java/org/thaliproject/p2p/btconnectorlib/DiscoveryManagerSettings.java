/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager.DiscoveryMode;

/**
 * Discovery manager settings.
 * Manages all discovery manager settings except for the discovery mode.
 */
public class DiscoveryManagerSettings {
    public interface Listener {
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

    private static final String TAG = DiscoveryManagerSettings.class.getName();

    private static DiscoveryManagerSettings mInstance = null;
    private Listener mListener = null;
    private long mPeerExpirationInMilliseconds = DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS;
    private int mAdvertiseMode = DEFAULT_ADVERTISE_MODE;
    private int mAdvertiseTxPowerLevel = DEFAULT_ADVERTISE_TX_POWER_LEVEL;
    private int mScanMode = DEFAULT_SCAN_MODE;


    /**
     * @return The singleton instance of this class.
     */
    public static DiscoveryManagerSettings getInstance() {
        if (mInstance == null) {
            mInstance = new DiscoveryManagerSettings();
        }

        return mInstance;
    }

    /**
     * Private constructor.
     */
    private DiscoveryManagerSettings() {
    }

    /**
     * Sets the listener. Note: Only the discovery manager can act as a listener.
     * @param discoveryManager The discovery manager instance.
     */
    public void setListener(DiscoveryManager discoveryManager) {
        mListener = discoveryManager;
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

            if (mListener != null) {
                mListener.onScanModeSettingChanged(mScanMode);
            }
        }
    }
}
