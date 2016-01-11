/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.nativetest.app.ConnectionEngine;
import org.thaliproject.p2p.btconnectorlib.ConnectionManagerSettings;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;

/**
 * Manages the application settings.
 */
public class Settings {
    private static final String TAG = Settings.class.getName();
    private static Settings mInstance = null;
    private static Context mContext = null;
    private final SharedPreferences mSharedPreferences;
    private final SharedPreferences.Editor mSharedPreferencesEditor;

    private static final String KEY_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String KEY_PORT_NUMBER = "port_number";
    private static final String KEY_ENABLE_WIFI_DISCOVERY = "enable_wifi_discovery";
    private static final String KEY_ENABLE_BLE_DISCOVERY = "enable_ble_discovery";
    private static final String KEY_ADVERTISE_MODE = "advertise_mode";
    private static final String KEY_ADVERTISE_TX_POWER_LEVEL = "advertise_tx_power_level";
    private static final String KEY_SCAN_MODE = "scan_mode";
    private static final String KEY_DATA_AMOUNT = "data_amount";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_AUTO_CONNECT_WHEN_INCOMING = "auto_connect_when_incoming";
    private static final long START_DISCOVERY_MANAGER_DELAY_IN_MILLISECONDS = 1000;

    private DiscoveryManager mDiscoveryManager = null;
    private long mConnectionTimeoutInMilliseconds = ConnectionManagerSettings.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mPortNumber = ConnectionManagerSettings.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    private boolean mEnableWifiDiscovery = true;
    private boolean mEnableBleDiscovery = true;
    private int mAdvertiseMode = DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE;
    private int mAdvertiseTxPowerLevel = DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL;
    private int mScanMode = DiscoveryManagerSettings.DEFAULT_SCAN_MODE;
    private long mDataAmountInBytes = Connection.DEFAULT_DATA_AMOUNT_IN_BYTES;
    private int mBufferSizeInBytes = Connection.DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES;
    private boolean mAutoConnect = false;
    private boolean mAutoConnectEvenWhenIncomingConnectionEstablished = false;

    /**
     * Returns the singleton instance of this class. Creates the instance if not already created.
     * @param context The application context.
     * @return The singleton instance of this class or null, if no context given and not created before.
     */
    public static Settings getInstance(Context context) {
        if (mInstance == null && context != null) {
            mContext = context;
            mInstance = new Settings(mContext);
        }

        return mInstance;
    }

    /**
     * Constructor.
     * @param context The application context.
     */
    private Settings(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mSharedPreferencesEditor = mSharedPreferences.edit();
    }

    /**
     * Loads the settings.
     */
    public void load() {
        mConnectionTimeoutInMilliseconds = mSharedPreferences.getLong(
                KEY_CONNECTION_TIMEOUT, ConnectionManagerSettings.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        mPortNumber = mSharedPreferences.getInt(
                KEY_PORT_NUMBER, ConnectionManagerSettings.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
        mEnableWifiDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_WIFI_DISCOVERY, true);
        mEnableBleDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_BLE_DISCOVERY, true);
        mAdvertiseMode = mSharedPreferences.getInt(KEY_ADVERTISE_MODE, DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE);
        mAdvertiseTxPowerLevel = mSharedPreferences.getInt(
                KEY_ADVERTISE_TX_POWER_LEVEL, DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL);
        mScanMode = mSharedPreferences.getInt(KEY_SCAN_MODE, DiscoveryManagerSettings.DEFAULT_SCAN_MODE);
        mDataAmountInBytes = mSharedPreferences.getLong(KEY_DATA_AMOUNT, Connection.DEFAULT_DATA_AMOUNT_IN_BYTES);
        mBufferSizeInBytes = mSharedPreferences.getInt(
                KEY_BUFFER_SIZE, Connection.DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);
        mAutoConnect = mSharedPreferences.getBoolean(KEY_AUTO_CONNECT, false);
        mAutoConnectEvenWhenIncomingConnectionEstablished = mSharedPreferences.getBoolean(
                KEY_AUTO_CONNECT_WHEN_INCOMING, false);

        Log.i(TAG, "load: "
                + mConnectionTimeoutInMilliseconds + ", "
                + mPortNumber + ", "
                + mEnableWifiDiscovery + ", "
                + mEnableBleDiscovery + ", "
                + mAdvertiseMode + ", "
                + mAdvertiseTxPowerLevel + ", "
                + mScanMode + ", "
                + mDataAmountInBytes + ", "
                + mBufferSizeInBytes + ", "
                + mAutoConnect + ", "
                + mAutoConnectEvenWhenIncomingConnectionEstablished);

        ConnectionManagerSettings connectionManagerSettings = ConnectionManagerSettings.getInstance();
        connectionManagerSettings.setConnectionTimeout(mConnectionTimeoutInMilliseconds);
        connectionManagerSettings.setInsecureRfcommSocketPortNumber(mPortNumber);

        DiscoveryManagerSettings discoveryManagerSettings = DiscoveryManagerSettings.getInstance();
        discoveryManagerSettings.setDiscoveryMode(getDesiredDiscoveryMode());
        discoveryManagerSettings.setAdvertiseMode(mAdvertiseMode);
        discoveryManagerSettings.setAdvertiseTxPowerLevel(mAdvertiseTxPowerLevel);
        discoveryManagerSettings.setScanMode(mScanMode);
    }

    public void setDiscoveryManager(DiscoveryManager discoveryManager) {
        mDiscoveryManager = discoveryManager;
    }

    /**
     * @return The connection timeout in milliseconds.
     */
    public long getConnectionTimeout() {
        return mConnectionTimeoutInMilliseconds;
    }

    /**
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        Log.i(TAG, "setConnectionTimeout: " + connectionTimeoutInMilliseconds);
        mConnectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
        mSharedPreferencesEditor.putLong(KEY_CONNECTION_TIMEOUT, mConnectionTimeoutInMilliseconds);
        mSharedPreferencesEditor.apply();
        ConnectionManagerSettings.getInstance().setConnectionTimeout(mConnectionTimeoutInMilliseconds);
    }

    public int getPortNumber() {
        return mPortNumber;
    }

    public void setPortNumber(int portNumber) {
        Log.i(TAG, "setPortNumber: " + portNumber);
        mPortNumber = portNumber;
        mSharedPreferencesEditor.putInt(KEY_PORT_NUMBER, mPortNumber);
        mSharedPreferencesEditor.apply();
        ConnectionManagerSettings.getInstance().setInsecureRfcommSocketPortNumber(mPortNumber);
    }

    /**
     * @return The desired discovery mode based on the current settings.
     */
    public DiscoveryManager.DiscoveryMode getDesiredDiscoveryMode() {
        DiscoveryManager.DiscoveryMode desiredMode = DiscoveryManager.DiscoveryMode.NOT_SET;

        if (mEnableWifiDiscovery && mEnableBleDiscovery) {
            desiredMode = DiscoveryManager.DiscoveryMode.BLE_AND_WIFI;
        } else if (mEnableBleDiscovery) {
            desiredMode = DiscoveryManager.DiscoveryMode.BLE;
        } else if (mEnableWifiDiscovery) {
            desiredMode = DiscoveryManager.DiscoveryMode.WIFI;
        }

        return desiredMode;
    }

    public void setDesiredDiscoveryMode() {
        DiscoveryManager.DiscoveryMode desiredMode = getDesiredDiscoveryMode();

        if (desiredMode == DiscoveryManager.DiscoveryMode.NOT_SET) {
            if (mDiscoveryManager != null) {
                mDiscoveryManager.stop();
            }
        } else  {
            Log.i(TAG, "setDesiredDiscoveryMode: " + desiredMode);
            DiscoveryManagerSettings.getInstance().setDiscoveryMode(desiredMode, true);

            if (mDiscoveryManager != null) {
                Handler handler = new Handler(mContext.getMainLooper());

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mDiscoveryManager.getState() == DiscoveryManager.DiscoveryManagerState.NOT_STARTED) {
                            mDiscoveryManager.start(ConnectionEngine.PEER_NAME);
                        }
                    }
                }, START_DISCOVERY_MANAGER_DELAY_IN_MILLISECONDS);
            }
        }
    }

    public boolean getEnableWifiDiscovery() {
        return mEnableWifiDiscovery;
    }

    public void setEnableWifiDiscovery(boolean enable) {
        Log.i(TAG, "setEnableWifiDiscovery: " + enable);
        mEnableWifiDiscovery = enable;
        mSharedPreferencesEditor.putBoolean(KEY_ENABLE_WIFI_DISCOVERY, mEnableWifiDiscovery);
        mSharedPreferencesEditor.apply();
        setDesiredDiscoveryMode();
    }

    public boolean getEnableBleDiscovery() {
        return mEnableBleDiscovery;
    }

    public void setEnableBleDiscovery(boolean enable) {
        Log.i(TAG, "setEnableBleDiscovery: " + enable);
        mEnableBleDiscovery = enable;
        mSharedPreferencesEditor.putBoolean(KEY_ENABLE_BLE_DISCOVERY, mEnableBleDiscovery);
        mSharedPreferencesEditor.apply();
        setDesiredDiscoveryMode();
    }

    public int getAdvertiseMode() {
        return mAdvertiseMode;
    }

    public void setAdvertiseMode(int advertiseMode) {
        mAdvertiseMode = advertiseMode;
        mSharedPreferencesEditor.putInt(KEY_ADVERTISE_MODE, mAdvertiseMode);
        mSharedPreferencesEditor.apply();
        DiscoveryManagerSettings.getInstance().setAdvertiseMode(mAdvertiseMode);
    }

    public int getAdvertiseTxPowerLevel() {
        return mAdvertiseTxPowerLevel;
    }

    public void setAdvertiseTxPowerLevel(int advertiseTxPowerLevel) {
        mAdvertiseTxPowerLevel = advertiseTxPowerLevel;
        mSharedPreferencesEditor.putInt(KEY_ADVERTISE_TX_POWER_LEVEL, mAdvertiseTxPowerLevel);
        mSharedPreferencesEditor.apply();
        DiscoveryManagerSettings.getInstance().setAdvertiseTxPowerLevel(mAdvertiseTxPowerLevel);
    }

    public int getScanMode() {
        return mScanMode;
    }

    public void setScanMode(int scanMode) {
        mScanMode = scanMode;
        mSharedPreferencesEditor.putInt(KEY_SCAN_MODE, mScanMode);
        mSharedPreferencesEditor.apply();
        DiscoveryManagerSettings.getInstance().setScanMode(mScanMode);
    }

    /**
     * @return The data amount to send, in bytes.
     */
    public long getDataAmount() {
        return mDataAmountInBytes;
    }

    /**
     * @param dataAmountInBytes The data amount to send, in bytes.
     */
    public void setDataAmount(long dataAmountInBytes) {
        Log.i(TAG, "setDataAmount: " + dataAmountInBytes);
        mDataAmountInBytes = dataAmountInBytes;
        mSharedPreferencesEditor.putLong(KEY_DATA_AMOUNT, mDataAmountInBytes);
        mSharedPreferencesEditor.apply();
    }

    /**
     * @return The buffer size in bytes.
     */
    public int getBufferSize() {
        return mBufferSizeInBytes;
    }

    /**
     * @param bufferSizeInBytes The buffer size in bytes.
     */
    public void setBufferSize(int bufferSizeInBytes) {
        Log.i(TAG, "setBufferSize: " + bufferSizeInBytes);
        mBufferSizeInBytes = bufferSizeInBytes;
        mSharedPreferencesEditor.putInt(KEY_BUFFER_SIZE, mBufferSizeInBytes);
        mSharedPreferencesEditor.apply();
        PeerAndConnectionModel.getInstance().setBufferSizeOfConnections(mBufferSizeInBytes);
    }

    public boolean getAutoConnect() {
        return mAutoConnect;
    }

    public void setAutoConnect(boolean autoConnect) {
        Log.i(TAG, "setAutoConnect: " + autoConnect);
        mAutoConnect = autoConnect;
        mSharedPreferencesEditor.putBoolean(KEY_AUTO_CONNECT, mAutoConnect);
        mSharedPreferencesEditor.apply();
    }

    public boolean getAutoConnectEvenWhenIncomingConnectionEstablished() {
        return mAutoConnectEvenWhenIncomingConnectionEstablished;
    }

    public void setAutoConnectEvenWhenIncomingConnectionEstablished(boolean autoConnect) {
        Log.i(TAG, "setAutoConnectEvenWhenIncomingConnectionEstablished: " + autoConnect);
        mAutoConnectEvenWhenIncomingConnectionEstablished = autoConnect;
        mSharedPreferencesEditor.putBoolean(
                KEY_AUTO_CONNECT_WHEN_INCOMING, mAutoConnectEvenWhenIncomingConnectionEstablished);
        mSharedPreferencesEditor.apply();
    }
}
