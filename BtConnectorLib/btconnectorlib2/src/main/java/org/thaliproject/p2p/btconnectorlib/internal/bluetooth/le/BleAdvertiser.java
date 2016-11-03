/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
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
import org.thaliproject.p2p.btconnectorlib.utils.ThreadUtils;

/**
 * General BLE advertiser.
 */
@TargetApi(21)
class BleAdvertiser extends AdvertiseCallback {
    public interface Listener {
        /**
         * Called when the Bluetooth LE advertiser fails to start.
         *
         * @param errorCode The error code.
         */
        void onAdvertiserFailedToStart(int errorCode);

        /**
         * Called when this advertiser is started or stopped.
         *
         * @param isStarted If true, the advertising was started. If false, the advertising was stopped.
         */
        void onIsAdvertiserStartedChanged(boolean isStarted);
    }

    private enum State {
        NOT_STARTED,
        STARTING,
        RUNNING
    }

    ;

    private static final String TAG = BleAdvertiser.class.getName();
    private Listener mListener = null;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser = null;
    private AdvertiseSettings mAdvertiseSettings = null;
    private AdvertiseData mAdvertiseData = null;
    private State mState = State.NOT_STARTED;

    /**
     * Constructor.
     *
     * @param listener         The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     */
    public BleAdvertiser(Listener listener, BluetoothAdapter bluetoothAdapter) {
        this(listener, bluetoothAdapter, new AdvertiseSettings.Builder(),
                DiscoveryManagerSettings.getInstance(null));
    }

    /**
     * Constructor.
     *
     * @param listener         The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param builder          The builder for AdvertiseSettings.
     * @param settings         The discovery manager settings.
     */
    public BleAdvertiser(Listener listener, BluetoothAdapter bluetoothAdapter,
                         AdvertiseSettings.Builder builder, DiscoveryManagerSettings settings) {
        mListener = listener;
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        try {
            if (settings != null) {
                builder.setAdvertiseMode(settings.getAdvertiseMode());
                builder.setTxPowerLevel(settings.getAdvertiseTxPowerLevel());
            } else {
                Log.e(TAG, "Failed to get the discovery manager settings instance - using default settings");
                builder.setAdvertiseMode(DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE);
                builder.setTxPowerLevel(DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL);
            }

            builder.setTimeout(0);
            builder.setConnectable(false); // No characteristics support by default
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BleAdvertiser: Failed to apply settings: " + e.getMessage(), e);
        }

        setAdvertiseSettings(builder.build());
    }

    /**
     * Sets the advertise data. Restarts the instance, if it was started/running.
     *
     * @param advertiseData The advertise data to set.
     */
    public void setAdvertiseData(AdvertiseData advertiseData) {
        Log.d(TAG, "setAdvertiseData: " + ThreadUtils.currentThreadToString());
        if (advertiseData != null) {
            boolean wasStarted = isStarted();
            Log.d(TAG, "setAdvertiseData: was started = " + wasStarted);
            if (wasStarted) {
                stop(false);
            }

            Log.d(TAG, "setAdvertiseData: advertiseData = " + advertiseData.toString());
            mAdvertiseData = advertiseData;

            if (wasStarted) {
                start();
            }
        } else {
            throw new NullPointerException("The given advertise data is null");
        }
        Log.d(TAG, "setAdvertiseData finished: " + ThreadUtils.currentThreadToString());
    }

    /**
     * Sets the advertise settings. Note that the advertiser is not restarted automatically.
     *
     * @param advertiseSettings The advertise settings to set.
     */
    public void setAdvertiseSettings(AdvertiseSettings advertiseSettings) {
        Log.d(TAG, "setAdvertiseSettings, " + ThreadUtils.currentThreadToString());
        if (advertiseSettings != null) {
            mAdvertiseSettings = advertiseSettings;

            Log.i(TAG, "setAdvertiseSettings: Mode: " + mAdvertiseSettings.getMode()
                    + ", Tx power level: " + mAdvertiseSettings.getTxPowerLevel()
                    + ", timeout: " + mAdvertiseSettings.getTimeout()
                    + ", is connectable: " + mAdvertiseSettings.isConnectable());
        } else {
            throw new NullPointerException("The argument (AdvertiseSettings) cannot be null");
        }
    }

    /**
     * @return True, if the advertiser is either starting or running. False otherwise.
     */
    public boolean isStarted() {
        return (mState != State.NOT_STARTED);
    }

    /**
     * Tries to start advertising.
     *
     * @return True, if starting. False in case of a failure.
     */
    public synchronized boolean start() {
        Log.d(TAG, "start:  state = " + mState + ThreadUtils.currentThreadToString());
        if (mState == State.NOT_STARTED) {
            if (mBluetoothLeAdvertiser != null) {
                if (mAdvertiseData != null) {
                    try {
                        Log.i(TAG, "start: Starting... adv data = " + mAdvertiseData.toString() + ThreadUtils.currentThreadToString());
                        mBluetoothLeAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData, null, this);
                        Log.i(TAG, "start: Started advertisment " + ThreadUtils.currentThreadToString());
                        setState(State.STARTING, true);
                    } catch (Exception e) {
                        Log.e(TAG, "start: Failed to start advertising: " + e.getMessage(), e);
                    }
                } else {
                    Log.e(TAG, "start: No advertisement data set");
                }
            } else {
                Log.e(TAG, "start: No BLE advertiser instance");
            }
        } else {
            Log.d(TAG, "start: Already running");
        }
        try {
            Log.i(TAG, "start: return, state = " + mState + ThreadUtils.currentThreadToString());
            return (mState != State.NOT_STARTED);
        } finally {
            Log.d(TAG, "start returned: " + ThreadUtils.currentThreadToString());
        }
    }

    /**
     * Stops advertising.
     *
     * @param notifyStateChanged If true, will notify the listener, if the state is changed.
     */
    public synchronized void stop(boolean notifyStateChanged) {
        Log.d(TAG, "stop. " + ThreadUtils.currentThreadToString());
        if (mBluetoothLeAdvertiser != null) {
            try {
                mBluetoothLeAdvertiser.stopAdvertising(this);
                Log.d(TAG, "stop: Stopped");
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop: " + e.getMessage(), e);
            }
        }

        setState(State.NOT_STARTED, notifyStateChanged);
        Log.d(TAG, "stop finished. " + ThreadUtils.currentThreadToString());
    }

    /**
     * Notifies the listener.
     *
     * @param errorCode The error code.
     */
    @Override
    public void onStartFailure(int errorCode) {
        String reason = "";
        Log.d(TAG, "onStartFailure: " + ThreadUtils.currentThreadToString());

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
        setState(State.NOT_STARTED, true);

        if (mListener != null) {
            mListener.onAdvertiserFailedToStart(errorCode);
        }
    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        Log.d(TAG, "onStartSuccess : " + ThreadUtils.currentThreadToString());
        setState(State.RUNNING, true);
    }

    /**
     * Sets the state and notifies listener if required.
     *
     * @param state              The new state.
     * @param notifyStateChanged If true, will notify the listener, if the state is changed.
     */
    private synchronized void setState(State state, boolean notifyStateChanged) {
        Log.d(TAG, "setState: current  = " + mState + ", new = " + state + ThreadUtils.currentThreadToString());
        if (mState != state) {
            Log.d(TAG, "setState: State changed from " + mState + " to " + state);
            mState = state;

            if (notifyStateChanged && mListener != null) {
                notifyStateChanged();
            }
        }
        Log.d(TAG, "setState: finished " + ThreadUtils.currentThreadToString());
    }

    private void notifyStateChanged() {
        switch (mState) {
            case NOT_STARTED:
                notifyAdvertiserStateChanged(false);
                break;
            case RUNNING:
                notifyAdvertiserStateChanged(true);
                break;
            default:
                // Nothing to do here
                Log.d(TAG, "setState: onIsAdvertiserStartedChanged default (no call to listener). " +
                        ThreadUtils.currentThreadToString());
                break;
        }
    }

    private void notifyAdvertiserStateChanged(final boolean isStarted) {
        Log.d(TAG, "notifyAdvertiserStateChanged: started =  " + isStarted + ". " + ThreadUtils.currentThreadToString());
        boolean posted = ThreadUtils.postToMainHelper(new Runnable() {
            @Override
            public void run() {
                mListener.onIsAdvertiserStartedChanged(isStarted);
            }
        });
        processPosted(posted);
    }

    private void processPosted(boolean posted) {
        if (!posted) {
            throw new RuntimeException("Couldn't post to main helper");
        }
    }

}
