/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractSettings;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;

/**
 * Connection manager settings.
 */
public class ConnectionManagerSettings extends AbstractSettings {
    public interface Listener {
        void onConnectionManagerSettingsChanged();
    }

    // Default settings
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = BluetoothConnector.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = BluetoothConnector.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES = BluetoothConnector.DEFAULT_MAX_NUMBER_OF_RETRIES;

    // Keys for shared preferences
    private static final String KEY_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String KEY_PORT_NUMBER = "port_number";
    private static final String KEY_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES = "max_number_of_connection_attempt_retries";

    private static final String TAG = ConnectionManagerSettings.class.getName();
    private static final int MAX_INSECURE_RFCOMM_SOCKET_PORT = 30;

    private static ConnectionManagerSettings mInstance = null;
    private Listener mListener = null;
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mInsecureRfcommSocketPortNumber = DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfConnectionAttemptRetries = 0;

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
     * Private constructor.
     */
    private ConnectionManagerSettings(Context context) {
        super(context); // Will create Shared preferences (and editor) instance
    }

    /**
     * Sets the listener. Note: Only the connection manager can act as a listener.
     * @param connectionManager The connection manager instance.
     */
    public void setListener(ConnectionManager connectionManager) {
        mListener = connectionManager;
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

            if (mListener != null) {
                mListener.onConnectionManagerSettingsChanged();
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

                if (mListener != null) {
                    mListener.onConnectionManagerSettingsChanged();
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

            if (mListener != null) {
                mListener.onConnectionManagerSettingsChanged();
            }
        }
    }

    @Override
    public void load() {
        mConnectionTimeoutInMilliseconds = mSharedPreferences.getLong(
                KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        mInsecureRfcommSocketPortNumber = mSharedPreferences.getInt(
                KEY_PORT_NUMBER, SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
        mMaxNumberOfConnectionAttemptRetries = mSharedPreferences.getInt(
                KEY_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES, DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES);

        Log.v(TAG, "load: "
                + "\n\tConnection timeout in milliseconds: " + mConnectionTimeoutInMilliseconds + ", "
                + "\n\tInsecure RFCOMM socket port number: " + mInsecureRfcommSocketPortNumber + ", "
                + "\n\tMaximum number of connection attempt retries: " + mMaxNumberOfConnectionAttemptRetries);
    }

    @Override
    public void resetDefaults() {
        setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        setInsecureRfcommSocketPortNumber(SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);
        setMaxNumberOfConnectionAttemptRetries(DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES);
    }
}
