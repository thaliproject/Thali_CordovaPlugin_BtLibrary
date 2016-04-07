/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.nativetest.app.ConnectionEngine;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.ConnectionManagerSettings;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;

/**
 * Manages the application settings.
 */
public class Settings {
    private static final String TAG = Settings.class.getName();
    private static Settings mInstance = null;
    private static Context mContext = null;
    private final SharedPreferences mSharedPreferences;
    private final SharedPreferences.Editor mSharedPreferencesEditor;

    public static final String DEFAULT_PEER_NAME = Build.MANUFACTURER + "_" + Build.MODEL; // Use manufacturer and device model name as the peer name
    private static final boolean DEFAULT_LISTEN_FOR_INCOMING_CONNECTIONS = true;
    private static final boolean DEFAULT_ENABLE_WIFI_DISCOVERY = false;
    private static final boolean DEFAULT_ENABLE_BLE_DISCOVERY = true;
    private static final boolean DEFAULT_AUTO_CONNECT = false;
    private static final boolean DEFAULT_AUTO_CONNECT_WHEN_INCOMING = false;

    private static final String KEY_LISTEN_FOR_INCOMING_CONNECTIONS = "listen_for_incoming_connections";
    private static final String KEY_ENABLE_WIFI_DISCOVERY = "enable_wifi_discovery";
    private static final String KEY_ENABLE_BLE_DISCOVERY = "enable_ble_discovery";
    private static final String KEY_PEER_NAME = "peer_name";
    private static final String KEY_DATA_AMOUNT = "data_amount";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_AUTO_CONNECT_WHEN_INCOMING = "auto_connect_when_incoming";
    private static final long START_DISCOVERY_MANAGER_DELAY_IN_MILLISECONDS = 3000;

    private ConnectionManager mConnectionManager = null;
    private DiscoveryManager mDiscoveryManager = null;
    private DiscoveryManagerSettings mDiscoveryManagerSettings = null;
    private ConnectionManagerSettings mConnectionManagerSettings = null;
    private boolean mListenForIncomingConnections = true;
    private boolean mEnableWifiDiscovery = true;
    private boolean mEnableBleDiscovery = true;
    private String mPeerName = DEFAULT_PEER_NAME;
    private long mDataAmountInBytes = Connection.DEFAULT_DATA_AMOUNT_IN_BYTES;
    private int mBufferSizeInBytes = Connection.DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES;
    private boolean mAutoConnect = false;
    private boolean mAutoConnectEvenWhenIncomingConnectionEstablished = false;
    private boolean mLoaded = false;

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
        if (!mLoaded) {
            mLoaded = true;
            mListenForIncomingConnections = mSharedPreferences.getBoolean(
                    KEY_LISTEN_FOR_INCOMING_CONNECTIONS, DEFAULT_LISTEN_FOR_INCOMING_CONNECTIONS);

            mEnableWifiDiscovery = mSharedPreferences.getBoolean(
                    KEY_ENABLE_WIFI_DISCOVERY, DEFAULT_ENABLE_WIFI_DISCOVERY);
            mEnableBleDiscovery = mSharedPreferences.getBoolean(
                    KEY_ENABLE_BLE_DISCOVERY, DEFAULT_ENABLE_BLE_DISCOVERY);

            mPeerName = mSharedPreferences.getString(KEY_PEER_NAME, DEFAULT_PEER_NAME);
            mDataAmountInBytes = mSharedPreferences.getLong(KEY_DATA_AMOUNT, Connection.DEFAULT_DATA_AMOUNT_IN_BYTES);
            mBufferSizeInBytes = mSharedPreferences.getInt(
                    KEY_BUFFER_SIZE, Connection.DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);
            mAutoConnect = mSharedPreferences.getBoolean(KEY_AUTO_CONNECT, DEFAULT_AUTO_CONNECT);
            mAutoConnectEvenWhenIncomingConnectionEstablished = mSharedPreferences.getBoolean(
                    KEY_AUTO_CONNECT_WHEN_INCOMING, DEFAULT_AUTO_CONNECT_WHEN_INCOMING);

            Log.i(TAG, "load: "
                    + "\n    - Listen for incoming connections: " + mListenForIncomingConnections
                    + "\n    - Enable Wi-Fi Direct peer discovery: " + mEnableWifiDiscovery
                    + "\n    - Enable BLE peer discovery: " + mEnableBleDiscovery
                    + "\n    - Peer name: " + mPeerName
                    + "\n    - Data amount in bytes: " + mDataAmountInBytes
                    + "\n    - Buffer size in bytes: " + mBufferSizeInBytes
                    + "\n    - Auto connect enabled: " + mAutoConnect
                    + "\n    - Auto connect even when incoming connection established: " + mAutoConnectEvenWhenIncomingConnectionEstablished);

            mConnectionManager.setPeerName(mPeerName);
            mDiscoveryManager.setPeerName(mPeerName);

            DiscoveryManager.DiscoveryMode discoveryMode = getDesiredDiscoveryMode();

            if (discoveryMode != null) {
                mDiscoveryManagerSettings.setDiscoveryMode(getDesiredDiscoveryMode());
            }
        } else {
            Log.v(TAG, "load: Already loaded");
        }
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        mConnectionManager = connectionManager;
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
        DiscoveryManager.DiscoveryMode desiredMode = null;

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

        if (desiredMode == null) {
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
                        if (getDesiredDiscoveryMode() != null
                            && mDiscoveryManager.getState() == DiscoveryManager.DiscoveryManagerState.NOT_STARTED) {
                            Log.d(TAG, "Starting the discovery manager...");
                            mDiscoveryManager.start(true, true);
                        }
                    }
                }, START_DISCOVERY_MANAGER_DELAY_IN_MILLISECONDS);
            }
        }
    }

    public boolean getHandshakeRequired() {
        return mConnectionManagerSettings.getHandshakeRequired();
    }

    public void setHandshakeRequired(boolean handshakeRequired) {
        mConnectionManagerSettings.setHandshakeRequired(handshakeRequired);
    }

    public boolean getListenForIncomingConnections() {
        return mListenForIncomingConnections;
    }

    public void setListenForIncomingConnections(boolean listenForIncomingConnections) {
        if (mListenForIncomingConnections != listenForIncomingConnections) {
            Log.i(TAG, "setListenForIncomingConnections: " + listenForIncomingConnections);
            mListenForIncomingConnections = listenForIncomingConnections;
            mSharedPreferencesEditor.putBoolean(KEY_LISTEN_FOR_INCOMING_CONNECTIONS, mListenForIncomingConnections);
            mSharedPreferencesEditor.apply();

            if (mConnectionManager != null) {
                if (mListenForIncomingConnections) {
                    mConnectionManager.startListeningForIncomingConnections();
                } else {
                    mConnectionManager.stopListeningForIncomingConnections();
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

    public BlePeerDiscoverer.AdvertisementDataType getAdvertisementDataType() {
        return mDiscoveryManagerSettings.getAdvertisementDataType();
    }

    public void setAdvertisementDataType(BlePeerDiscoverer.AdvertisementDataType advertisementDataType) {
        mDiscoveryManagerSettings.setAdvertisementDataType(advertisementDataType);
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

    public String getPeerName() {
        return mPeerName;
    }

    public void setPeerName(String peerName) {
        if (mPeerName != peerName) {
            mPeerName = peerName;
            mSharedPreferencesEditor.putString(KEY_PEER_NAME, mPeerName);
            mSharedPreferencesEditor.apply();
            mConnectionManager.setPeerName(mPeerName);
        }
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
