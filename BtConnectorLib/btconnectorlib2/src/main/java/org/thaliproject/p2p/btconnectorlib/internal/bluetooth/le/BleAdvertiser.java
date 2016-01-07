/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;

/**
 * General BLE advertiser.
 */
@TargetApi(21)
class BleAdvertiser extends AdvertiseCallback {
    public interface Listener {
        /**
         * Called when the Bluetooth LE advertiser fails to start.
         * @param errorCode The error code.
         */
        void onAdvertiserFailedToStart(int errorCode);

        /**
         * Called when this advertiser is started or stopped.
         * @param isStarted If true, the advertising was started. If false, the advertising was stopped.
         */
        void onIsAdvertiserStartedChanged(boolean isStarted);
    }

    private enum State {
        NOT_STARTED,
        STARTING,
        RUNNING
    };

    private static final String TAG = BleAdvertiser.class.getName();
    private Listener mListener = null;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser = null;
    private AdvertiseSettings mAdvertiseSettings = null;
    private AdvertiseData mAdvertiseData = null;
    private State mState = State.NOT_STARTED;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     */
    public BleAdvertiser(Listener listener, BluetoothAdapter bluetoothAdapter) {
        mListener = listener;
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance();

        try {
            builder.setAdvertiseMode(settings.getAdvertiseMode());
            builder.setTxPowerLevel(settings.getAdvertiseTxPowerLevel());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BleAdvertiser: Failed to apply settings: " + e.getMessage(), e);
        }

        setAdvertiseSettings(builder.build());
    }

    public void setAdvertiseData(AdvertiseData advertiseData) {
        Log.i(TAG, "setAdvertiseData: " + advertiseData.toString());
        mAdvertiseData = advertiseData;
    }

    public void setAdvertiseSettings(AdvertiseSettings advertiseSettings) {
        if (advertiseSettings != null) {
            mAdvertiseSettings = advertiseSettings;

            Log.i(TAG, "setAdvertiseSettings: Mode: " + mAdvertiseSettings.getMode()
                    + ", Tx power level: " + mAdvertiseSettings.getTxPowerLevel()
                    + ", timeout: " + mAdvertiseSettings.getTimeout()
                    + ", is connectable: " + mAdvertiseSettings.isConnectable());
        } else {
            Log.e(TAG, "setAdvertiseSettings: The argument (AdvertiseSettings) cannot be null");
        }
    }

    /**
     * Tries to start advertising.
     * @return True, if starting. False in case of a failure.
     */
    public synchronized boolean start() {
        if (mState == State.NOT_STARTED) {
            if (mBluetoothLeAdvertiser != null) {
                if (mAdvertiseData != null) {
                    try {
                        mBluetoothLeAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData, this);
                        setState(State.STARTING);
                    } catch (Exception e) {
                        Log.e(TAG, "start: Failed to start advertising: " + e.getMessage(), e);
                    }
                } else {
                    Log.e(TAG, "start: No advertisement data set");
                }
            }
        } else {
            Log.e(TAG, "start: No BLE advertiser instance");
        }

        return (mState != State.NOT_STARTED);
    }

    /**
     * Stops advertising.
     */
    public synchronized void stop() {
        if (mBluetoothLeAdvertiser != null) {
            try {
                mBluetoothLeAdvertiser.stopAdvertising(this);
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop: " + e.getMessage(), e);
            }
        }

        setState(State.NOT_STARTED);
    }

    /**
     * @return True, if the BLE advertising is started. False otherwise.
     */
    public boolean isStarted() {
        return (mState == State.RUNNING);
    }

    /**
     * Notifies the listener.
     * @param errorCode The error code.
     */
    @Override
    public void onStartFailure(int errorCode) {
        String reason = "";

        switch (errorCode) {
            case ADVERTISE_FAILED_ALREADY_STARTED:
                reason = "Advertising is already started";
                break;
            case ADVERTISE_FAILED_DATA_TOO_LARGE:
                reason = "The advertise data to be broadcast is larger than 31 bytes";
                break;
            case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                reason = "This feature is not supported on this platform";
                break;
            case ADVERTISE_FAILED_INTERNAL_ERROR:
                reason = "Operation failed due to an internal error";
                break;
            case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                reason = "No advertising instance is available";
                break;
            default:
                reason = "Unknown error";
                break;
        }

        Log.e(TAG, "onStartFailure: " + reason + ", error code is " + errorCode);

        if (mListener != null) {
            mListener.onAdvertiserFailedToStart(errorCode);
        }

        setState(State.NOT_STARTED);
    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        Log.i(TAG, "onStartSuccess");
        setState(State.RUNNING);
    }

    /**
     * Sets the state and notifies listener if required.
     * @param state The new state.
     */
    private synchronized void setState(State state) {
        if (mState != state) {
            mState = state;

            switch (mState) {
                case NOT_STARTED:
                    if (mListener != null) {
                        mListener.onIsAdvertiserStartedChanged(false);
                    }

                    break;
                case RUNNING:
                    if (mListener != null) {
                        mListener.onIsAdvertiserStartedChanged(true);
                    }

                    break;
            }
        }
    }
}
