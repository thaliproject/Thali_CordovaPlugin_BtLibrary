/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
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

    private static final String KEY_ENABLE_WIFI_DISCOVERY = "enable_wifi_discovery";
    private static final String KEY_ENABLE_BLE_DISCOVERY = "enable_ble_discovery";
    private static final String KEY_DATA_AMOUNT = "data_amount";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_AUTO_CONNECT_WHEN_INCOMING = "auto_connect_when_incoming";
    private static final long START_DISCOVERY_MANAGER_DELAY_IN_MILLISECONDS = 3000;

    private DiscoveryManager mDiscoveryManager = null;
    private DiscoveryManagerSettings mDiscoveryManagerSettings = null;
    private ConnectionManagerSettings mConnectionManagerSettings = null;
    private boolean mEnableWifiDiscovery = true;
    private boolean mEnableBleDiscovery = true;
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
        mDiscoveryManagerSettings = DiscoveryManagerSettings.getInstance(context);
        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(context);
    }

    /**
     * Loads the settings.
     */
    public void load() {
        mEnableWifiDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_WIFI_DISCOVERY, true);
        mEnableBleDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_BLE_DISCOVERY, true);

        mDataAmountInBytes = mSharedPreferences.getLong(KEY_DATA_AMOUNT, Connection.DEFAULT_DATA_AMOUNT_IN_BYTES);
        mBufferSizeInBytes = mSharedPreferences.getInt(
                KEY_BUFFER_SIZE, Connection.DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);
        mAutoConnect = mSharedPreferences.getBoolean(KEY_AUTO_CONNECT, false);
        mAutoConnectEvenWhenIncomingConnectionEstablished = mSharedPreferences.getBoolean(
                KEY_AUTO_CONNECT_WHEN_INCOMING, false);

        Log.i(TAG, "load: "
                + "\n\tEnable Wi-Fi Direct peer discovery: " + mEnableWifiDiscovery
                + "\n\tEnable BLE peer discovery: " + mEnableBleDiscovery
                + "\n\tData amount in bytes: " + mDataAmountInBytes
                + "\n\tBuffer size in bytes: " + mBufferSizeInBytes
                + "\n\tAuto connect enabled: " + mAutoConnect
                + "\n\tAuto connect even when incoming connection established: " + mAutoConnectEvenWhenIncomingConnectionEstablished);

        mDiscoveryManagerSettings.setDiscoveryMode(getDesiredDiscoveryMode());
    }

    public void setDiscoveryManager(DiscoveryManager discoveryManager) {
        mDiscoveryManager = discoveryManager;
    }

    /**
     * @return The connection timeout in milliseconds.
     */
    public long getConnectionTimeout() {
        return mConnectionManagerSettings.getConnectionTimeout();
    }

    /**
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        mConnectionManagerSettings.setConnectionTimeout(connectionTimeoutInMilliseconds);
    }

    public int getPortNumber() {
        return mConnectionManagerSettings.getInsecureRfcommSocketPortNumber();
    }

    public void setPortNumber(int portNumber) {
        mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(portNumber);
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
        Log.i(TAG, "setDesiredDiscoveryMode: " + desiredMode);

        if (desiredMode == DiscoveryManager.DiscoveryMode.NOT_SET) {
            if (mDiscoveryManager != null) {
                mDiscoveryManager.stop();
            }
        } else  {
            mDiscoveryManagerSettings.setDiscoveryMode(desiredMode, true);

            if (mDiscoveryManager != null) {
                Handler handler = new Handler(mContext.getMainLooper());

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (getDesiredDiscoveryMode() != DiscoveryManager.DiscoveryMode.NOT_SET
                            && mDiscoveryManager.getState() == DiscoveryManager.DiscoveryManagerState.NOT_STARTED) {
                            Log.d(TAG, "Starting the discovery manager...");
                            mDiscoveryManager.start(true, true);
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
        return mDiscoveryManagerSettings.getAdvertiseMode();
    }

    public void setAdvertiseMode(int advertiseMode) {
        mDiscoveryManagerSettings.setAdvertiseMode(advertiseMode);
    }

    public int getAdvertiseTxPowerLevel() {
        return mDiscoveryManagerSettings.getAdvertiseTxPowerLevel();
    }

    public void setAdvertiseTxPowerLevel(int advertiseTxPowerLevel) {
        mDiscoveryManagerSettings.setAdvertiseTxPowerLevel(advertiseTxPowerLevel);
    }

    public int getScanMode() {
        return mDiscoveryManagerSettings.getScanMode();
    }

    public void setScanMode(int scanMode) {
        mDiscoveryManagerSettings.setScanMode(scanMode);
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
