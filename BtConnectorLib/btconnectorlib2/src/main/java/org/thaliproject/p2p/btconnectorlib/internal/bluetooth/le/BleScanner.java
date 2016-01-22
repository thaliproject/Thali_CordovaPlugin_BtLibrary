/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
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
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * General BLE scanner.
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
         * Called when the scanner has picked up a result.
         * @param result The scan result.
         */
        void onScanResult(ScanResult result);
    }

    private enum State {
        NOT_STARTED,
        STARTING,
        RUNNING
    }

    private static final String TAG = BleScanner.class.getName();
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
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(null);

        try {
            if (settings != null) {
                builder.setScanMode(settings.getScanMode());
            } else {
                Log.e(TAG, "Failed to get the discovery manager settings instance - using default settings");
                builder.setScanMode(DiscoveryManagerSettings.DEFAULT_SCAN_MODE);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BleScanner: Failed to apply scan mode setting: " + e.getMessage(), e);
        }

        setScanSettings(builder.build());
    }

    /**
     * @return True, if the scanner is either starting or running. False otherwise.
     */
    public boolean isStarted() {
        return (mState != State.NOT_STARTED);
    }

    /**
     * Tries to start the BLE scanning.
     * @return True, if starting. False in case of an error.
     */
    public synchronized boolean start() {
        if (mState == State.NOT_STARTED) {
            if (mBluetoothLeScanner != null) {
                try {
                    mBluetoothLeScanner.startScan(mScanFilters, mScanSettings, this);
                    setState(State.STARTING, true);
                } catch (Exception e) {
                    Log.e(TAG, "start: Failed to start: " + e.getMessage(), e);
                }
            } else {
                Log.e(TAG, "start: No BLE scanner instance");
            }
        }

        return (mState != State.NOT_STARTED);
    }

    /**
     * Stops the scanning.
     * @param notifyStateChanged If true, will notify the listener, if the state is changed.
     */
    public synchronized void stop(boolean notifyStateChanged) {
        if (mBluetoothLeScanner != null) {
            try {
                mBluetoothLeScanner.stopScan(this);
                Log.d(TAG, "stop: Stopped");
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop: " + e.getMessage(), e);
            }
        }

        setState(State.NOT_STARTED, notifyStateChanged);
    }

    /**
     * Clears the scan filters. If the scanning is running, it is restarted.
     */
    public void clearScanFilters() {
        boolean wasStarted = (mState != State.NOT_STARTED);

        if (wasStarted) {
            stop(false);
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
                stop(false);
            }

            mScanFilters.add(scanFilter);

            if (wasStarted) {
                start();
            }
        }
    }

    /**
     * Sets the scan settings. If not set explicitly, default settings will be used.
     * @param scanSettings The new scan settings.
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
        // Not used
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
        setState(State.NOT_STARTED, true);

        if (mListener != null) {
            mListener.onScannerFailed(errorCode);
        }
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        if (result != null) {
            Log.v(TAG, "onScanResult: Callback type: " + callbackType + ", Scan result: " + result.toString());

            if (mListener != null) {
                mListener.onScanResult(result);
            }
        }
    }

    /**
     * Sets the state and notifies listener if required.
     * @param state The new state.
     * @param notifyStateChanged If true, will notify the listener, if the state is changed.
     */
    private synchronized void setState(State state, boolean notifyStateChanged) {
        if (mState != state) {
            mState = state;

            if (notifyStateChanged && mListener != null) {
                switch (mState) {
                    case NOT_STARTED:
                        mListener.onIsScannerStartedChanged(false);
                        break;
                    case RUNNING:
                        mListener.onIsScannerStartedChanged(true);
                        break;
                }
            }
        }
    }
}
