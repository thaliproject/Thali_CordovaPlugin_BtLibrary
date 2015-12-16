/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@TargetApi(21)
class BleScanner extends ScanCallback {
    public interface Listener {
        /**
         * Called when the Bluetooth LE scanning fails.
         * @param errorCode The error code.
         */
        void onScannerFailed(int errorCode);

        /**
         * Called when this scanner is started or stopped.
         * @param isStarted If true, the scanning was started. If false, the scanning was stopped.
         */
        void onIsScannerStartedChanged(boolean isStarted);

        /**
         *
         * @param result
         */
        void onScanResult(ScanResult result);
    }

    private enum State {
        NOT_STARTED,
        STARTING,
        RUNNING
    };

    private static final String TAG = BleScanner.class.getName();
    public static int DEFAULT_SCAN_MODE = ScanSettings.SCAN_MODE_LOW_POWER;
    private Listener mListener = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;
    private List<ScanFilter> mScanFilters = new ArrayList<>();
    private ScanSettings mScanSettings = null;
    private State mState = State.NOT_STARTED;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     */
    public BleScanner(Listener listener, BluetoothAdapter bluetoothAdapter) {
        mListener = listener;
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(DEFAULT_SCAN_MODE);
        setScanSettings(builder.build());
    }

    /**
     * Tries to start the BLE scanning.
     * @return True, if starting. False in case of an error.
     */
    public synchronized boolean start() {
        if (mState == State.NOT_STARTED) {
            try {
                mBluetoothLeScanner.startScan(mScanFilters, mScanSettings, this);
                setState(State.STARTING);
            } catch (Exception e) {
                Log.e(TAG, "start: Failed to start: " + e.getMessage(), e);
            }
        }

        return (mState != State.NOT_STARTED);
    }

    /**
     * Stops the scanning.
     */
    public synchronized void stop() {
        mBluetoothLeScanner.stopScan(this);
        setState(State.NOT_STARTED);
    }

    /**
     * Clears the scan filters. If the scanning is running, it is restarted.
     */
    public void clearScanFilters() {
        boolean wasStarted = (mState != State.NOT_STARTED);

        if (wasStarted) {
            stop();
        }

        mScanFilters.clear();

        if (wasStarted) {
            start();
        }
    }

    /**
     * Adds the given scan filter to the list of filters. If the scanning is running, it is restarted.
     * @param scanFilter The scan filter to add.
     */
    public void addFilter(ScanFilter scanFilter) {
        if (scanFilter != null) {
            boolean wasStarted = (mState != State.NOT_STARTED);

            if (wasStarted) {
                stop();
            }

            mScanFilters.add(scanFilter);

            if (wasStarted) {
                start();
            }
        }
    }

    /**
     *
     * @param scanSettings
     */
    public void setScanSettings(ScanSettings scanSettings) {
        if (scanSettings != null) {
            mScanSettings = scanSettings;

            Log.i(TAG, "setScanSettings: Mode: " + mScanSettings.getScanMode()
                + ", report delay in milliseconds: " + mScanSettings.getReportDelayMillis()
                + ", scan result type: " + mScanSettings.getScanResultType());
        } else {
            Log.e(TAG, "setScanSettings: The argument (ScanSettings) cannot be null");
        }
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {

    }

    @Override
    public void onScanFailed(int errorCode) {
        String reason = "";

        switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED:
                reason = "BLE scan with the same settings is already started by the app";
                break;
            case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                reason = "App cannot be registered";
                break;
            case SCAN_FAILED_FEATURE_UNSUPPORTED:
                reason = "Feature is not supported";
                break;
            case SCAN_FAILED_INTERNAL_ERROR:
                reason = "Internal error";
                break;
        }

        Log.e(TAG, "onScanFailed: " + reason + ", error code is " + errorCode);

        if (mListener != null) {
            mListener.onScannerFailed(errorCode);
        }

        setState(State.NOT_STARTED);
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        if (result != null) {
            Log.i(TAG, "onScanResult: Callback type: " + callbackType + ", Scan result: " + result.toString());

            if (mListener != null) {
                mListener.onScanResult(result);
            }
        }
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
                        mListener.onIsScannerStartedChanged(false);
                    }

                    break;
                case RUNNING:
                    if (mListener != null) {
                        mListener.onIsScannerStartedChanged(true);
                    }

                    break;
            }
        }
    }
}
