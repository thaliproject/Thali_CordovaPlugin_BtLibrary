/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.nativesample.app.MainActivity;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;

/**
 * Manages the application settings.
 */
public class Settings {
    private static final String TAG = Settings.class.getName();
    private static Settings mInstance = null;
    private final SharedPreferences mSharedPreferences;
    private final SharedPreferences.Editor mSharedPreferencesEditor;

    private static final String KEY_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String KEY_PORT_NUMBER = "port_number";
    private static final String KEY_ENABLE_WIFI_DISCOVERY = "enable_wifi_discovery";
    private static final String KEY_ENABLE_BLE_DISCOVERY = "enable_ble_discovery";
    private static final String KEY_DATA_AMOUNT = "data_amount";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_AUTO_CONNECT_WHEN_INCOMING = "auto_connect_when_incoming";

    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private long mConnectionTimeoutInMilliseconds = ConnectionManager.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mPortNumber = ConnectionManager.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
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
            mInstance = new Settings(context);
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

    public void setConnectionManager(ConnectionManager connectionManager) {
        mConnectionManager = connectionManager;
    }

    public void setDiscoveryManager(DiscoveryManager discoveryManager) {
        mDiscoveryManager = discoveryManager;
    }

    /**
     * Loads the settings.
     */
    public void load() {
        mConnectionTimeoutInMilliseconds = mSharedPreferences.getLong(
                KEY_CONNECTION_TIMEOUT, ConnectionManager.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        mPortNumber = mSharedPreferences.getInt(
                KEY_PORT_NUMBER, ConnectionManager.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
        mEnableWifiDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_WIFI_DISCOVERY, true);
        mEnableBleDiscovery = mSharedPreferences.getBoolean(KEY_ENABLE_BLE_DISCOVERY, true);
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
                + mDataAmountInBytes + ", "
                + mBufferSizeInBytes + ", "
                + mAutoConnect + ", "
                + mAutoConnectEvenWhenIncomingConnectionEstablished);
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

        if (mConnectionManager != null) {
            mConnectionManager.setConnectionTimeout(mConnectionTimeoutInMilliseconds);
        }
    }

    public int getPortNumber() {
        return mPortNumber;
    }

    public void setPortNumber(int portNumber) {
        Log.i(TAG, "setPortNumber: " + portNumber);
        mPortNumber = portNumber;
        mSharedPreferencesEditor.putInt(KEY_PORT_NUMBER, mPortNumber);
        mSharedPreferencesEditor.apply();

        if (mConnectionManager != null) {
            mConnectionManager.setInsecureRfcommSocketPort(mPortNumber);
        }
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
        if (mDiscoveryManager != null) {
            DiscoveryManager.DiscoveryMode desiredMode = getDesiredDiscoveryMode();

            if (desiredMode == DiscoveryManager.DiscoveryMode.NOT_SET) {
                mDiscoveryManager.stop();
            } else {
                mDiscoveryManager.setDiscoveryMode(getDesiredDiscoveryMode(), true);

                if (mDiscoveryManager.getState() == DiscoveryManager.DiscoveryManagerState.NOT_STARTED) {
                    mDiscoveryManager.start(MainActivity.PEER_NAME);
                }
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
