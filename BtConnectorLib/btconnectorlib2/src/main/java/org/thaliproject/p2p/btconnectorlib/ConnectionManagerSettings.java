/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connection manager settings.
 */
public class ConnectionManagerSettings extends AbstractSettings {
    public interface Listener {
        void onConnectionManagerSettingsChanged();
        void onHandshakeRequiredSettingChanged(boolean hanshakeRequired);
    }

    // Default settings
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = BluetoothConnector.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES = BluetoothConnector.DEFAULT_MAX_NUMBER_OF_RETRIES;
    public static final boolean DEFAULT_HANDSHAKE_REQUIRED = BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED;

    // Keys for shared preferences
    private static final String KEY_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String KEY_PORT_NUMBER = "port_number";
    private static final String KEY_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES = "max_number_of_connection_attempt_retries";
    private static final String KEY_HANDSHAKE_REQUIRED = "require_handshake";

    private static final String TAG = ConnectionManagerSettings.class.getName();
    private static final int MAX_INSECURE_RFCOMM_SOCKET_PORT = 30;

    private static ConnectionManagerSettings mInstance = null;
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mInsecureRfcommSocketPortNumber = DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfConnectionAttemptRetries = DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES;
    private boolean mHandshakeRequired = DEFAULT_HANDSHAKE_REQUIRED;

    /**
     * @param context The application context for the shared preferences.
     * @return The singleton instance of this class.
     */
    public static ConnectionManagerSettings getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new ConnectionManagerSettings(context);
        }

        return mInstance;
    }

    /**
     * @param context The application context for the shared preferences.
     * @param preferences The shared preferences.
     * @return The singleton instance of this class.
     */
    public static ConnectionManagerSettings getInstance(Context context, SharedPreferences preferences) {
        if (mInstance == null) {
            mInstance = new ConnectionManagerSettings(context, preferences);
        }

        return mInstance;
    }

    /**
     * Private constructor.
     */
    private ConnectionManagerSettings(Context context) {
        super(context); // Will create Shared preferences (and editor) instance
    }

    /**
     * Private constructor.
     */
    private ConnectionManagerSettings(Context context, SharedPreferences preferences) {
        super(context, preferences); // Will create Shared preferences (and editor) instance
    }

    /**
     * Adds a listener. In the ideal situation there is only one connection manager and thus, one
     * listener. However, for testing we might need to use multiple.
     *
     * Note: Only the connection manager can act as a listener.
     *
     * @param connectionManager The connection manager instance.
     */
    /* Package */ void addListener(ConnectionManager connectionManager) {
        if (connectionManager != null) {
            Listener listener = connectionManager;

            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
                Log.v(TAG, "addListener: Listener " + listener + " added. We now have " + mListeners.size() + " listener(s)");
            } else {
                Log.e(TAG, "addListener: Listener " + listener + " already in the list");
                throw new IllegalArgumentException(TAG + " addListener: Listener already in the list");
            }
        }
    }

    /**
     * Removes the given listener from the list.
     * @param connectionManager The listener to remove.
     */
    /* Package */ void removeListener(ConnectionManager connectionManager) {
        if (connectionManager != null && mListeners.size() > 0) {
            Listener listener = connectionManager;

            if (mListeners.remove(listener)) {
                Log.v(TAG, "removeListener: Listener " + listener + " removed from the list");
            } else {
                Log.e(TAG, "removeListener: Listener " + listener + " not in the list");
            }
        }
    }

    /**
     * @return The connection timeout in milliseconds.
     */
    public long getConnectionTimeout() {
        return mConnectionTimeoutInMilliseconds;
    }

    /**
     * Sets the connection timeout. If the given value is negative or zero, no timeout is set.
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        if (mConnectionTimeoutInMilliseconds != connectionTimeoutInMilliseconds) {
            mConnectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
            mSharedPreferencesEditor.putLong(KEY_CONNECTION_TIMEOUT, mConnectionTimeoutInMilliseconds);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onConnectionManagerSettingsChanged();
                }
            }
        }
    }

    /**
     * Returns the port to be used by the insecure RFCOMM socket when connecting.
     * -1: The system decides (the default method).
     * 0: Using a rotating port number.
     * 1-30: Using a custom port.
     * @return The port to be used by the insecure RFCOMM socket when connecting.
     */
    public int getInsecureRfcommSocketPortNumber() {
        return mInsecureRfcommSocketPortNumber;
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket when connecting.
     * @param insecureRfcommSocketPort The port to use.
     *                                 Use -1 for to let the system decide (the default method).
     *                                 Use 0 for rotating port number.
     *                                 Values 1-30 are valid custom ports (1 is recommended).
     * @return True, if the port was set successfully. False otherwise.
     */
    public boolean setInsecureRfcommSocketPortNumber(int insecureRfcommSocketPort) {
        boolean wasSet = false;

        if (mInsecureRfcommSocketPortNumber != insecureRfcommSocketPort) {
            if (insecureRfcommSocketPort >= -1 && insecureRfcommSocketPort <= MAX_INSECURE_RFCOMM_SOCKET_PORT) {
                Log.i(TAG, "setInsecureRfcommSocketPortNumber: Will use port " + insecureRfcommSocketPort + " when trying to connect");
                mInsecureRfcommSocketPortNumber = insecureRfcommSocketPort;
                mSharedPreferencesEditor.putInt(KEY_PORT_NUMBER, mInsecureRfcommSocketPortNumber);
                mSharedPreferencesEditor.apply();

                if (mListeners.size() > 0) {
                    for (Listener listener : mListeners) {
                        listener.onConnectionManagerSettingsChanged();
                    }
                }

                wasSet = true;
            } else {
                Log.e(TAG, "setInsecureRfcommSocketPortNumber: Cannot set port, invalid port number: " + insecureRfcommSocketPort);
            }
        }

        return wasSet;
    }

    /**
     * @return The maximum number of (outgoing) socket connection attempt retries.
     */
    public int getMaxNumberOfConnectionAttemptRetries() {
        return mMaxNumberOfConnectionAttemptRetries;
    }

    /**
     * Sets the maximum number of (outgoing) socket connection attempt retries (0 means only one attempt).
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries for outgoing connections.
     */
    public void setMaxNumberOfConnectionAttemptRetries(int maxNumberOfRetries) {
        if (mMaxNumberOfConnectionAttemptRetries != maxNumberOfRetries) {
            mMaxNumberOfConnectionAttemptRetries = maxNumberOfRetries;
            mSharedPreferencesEditor.putInt(KEY_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES, mMaxNumberOfConnectionAttemptRetries);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onConnectionManagerSettingsChanged();
                }
            }
        }
    }

    /**
     * @return True, if a handshake protocol should be applied when establishing a connection.
     */
    public boolean getHandshakeRequired() {
        return mHandshakeRequired;
    }

    /**
     * Sets the value indicating whether we require a handshake protocol when establishing a connection or not.
     * @param requireHandshake True, if a handshake protocol should be applied when establishing a connection.
     */
    public void setHandshakeRequired(boolean requireHandshake) {
        if (mHandshakeRequired != requireHandshake) {
            Log.d(TAG, "setHandshakeRequired: " + mHandshakeRequired + " -> " + requireHandshake);
            mHandshakeRequired = requireHandshake;
            mSharedPreferencesEditor.putBoolean(KEY_HANDSHAKE_REQUIRED, mHandshakeRequired);
            mSharedPreferencesEditor.apply();

            if (mListeners.size() > 0) {
                for (Listener listener : mListeners) {
                    listener.onHandshakeRequiredSettingChanged(mHandshakeRequired);
                }
            }
        }
    }

    @Override
    public void load() {
        if (!mLoaded) {
            mLoaded = true;
            mConnectionTimeoutInMilliseconds = mSharedPreferences.getLong(
                    KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
            mInsecureRfcommSocketPortNumber = mSharedPreferences.getInt(
                    KEY_PORT_NUMBER, SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
            mMaxNumberOfConnectionAttemptRetries = mSharedPreferences.getInt(
                    KEY_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES, DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES);
            mHandshakeRequired = mSharedPreferences.getBoolean(KEY_HANDSHAKE_REQUIRED, DEFAULT_HANDSHAKE_REQUIRED);

            Log.v(TAG, "load: "
                    + "\n    - Connection timeout in milliseconds: " + mConnectionTimeoutInMilliseconds
                    + "\n    - Insecure RFCOMM socket port number: " + mInsecureRfcommSocketPortNumber
                    + "\n    - Maximum number of connection attempt retries: " + mMaxNumberOfConnectionAttemptRetries
                    + "\n    - Handshake required: " + mHandshakeRequired);
        } else {
            Log.v(TAG, "load: Already loaded");
        }
    }

    @Override
    public void resetDefaults() {
        setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        setInsecureRfcommSocketPortNumber(SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
        setMaxNumberOfConnectionAttemptRetries(DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES);
        setHandshakeRequired(DEFAULT_HANDSHAKE_REQUIRED);
    }
}
