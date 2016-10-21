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
import android.os.RemoteException;
import android.util.Log;

import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * General BLE scanner.
 */
@TargetApi(21)
class BleScanner extends ScanCallback {
    public interface Listener {
        /**
         * Called when the Bluetooth LE scanning fails.
         *
         * @param errorCode The error code.
         */
        void onScannerFailed(int errorCode);

        /**
         * Called when this scanner is started or stopped.
         *
         * @param isStarted If true, the scanning was started. If false, the scanning was stopped.
         */
        void onIsScannerStartedChanged(boolean isStarted);

        /**
         * Called when the scanner has picked up a result.
         *
         * @param result The scan result.
         */
        void onScanResult(ScanResult result);
    }

    private enum State {
        NOT_STARTED,
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
     *
     * @param listener         The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     */
    public BleScanner(Listener listener, BluetoothAdapter bluetoothAdapter) {
        this(listener, bluetoothAdapter, new ScanSettings.Builder(),
                DiscoveryManagerSettings.getInstance(null));
    }

    /**
     * Constructor.
     *
     * @param listener         The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param builder          The builder for ScanSettings.
     * @param settings         The discovery manager settings.
     */
    public BleScanner(Listener listener, BluetoothAdapter bluetoothAdapter,
                      ScanSettings.Builder builder, DiscoveryManagerSettings settings) {
        mListener = listener;
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        try {
            if (settings != null) {
                builder.setScanMode(settings.getScanMode());
                builder.setReportDelay(settings.getScanReportDelay());
            } else {
                Log.e(TAG, "Failed to get the discovery manager settings instance - using default settings");
                builder.setScanMode(DiscoveryManagerSettings.DEFAULT_SCAN_MODE);
                builder.setReportDelay(DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS);
            }

            if (CommonUtils.isMarshmallowOrHigher()) {
                applyAdditionalMarshmallowSettings(builder);
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
     *
     * @return True, if starting. False in case of an error.
     */
    public synchronized boolean start() {
        Log.d(TAG, "start: " + ThreadUtils.currentThreadToString());
        if (mState == State.NOT_STARTED) {
            if (mBluetoothLeScanner != null) {
                try {
                    mBluetoothLeScanner.startScan(mScanFilters, mScanSettings, this);
                    Log.d(TAG, "start: scan started");
                    setState(State.RUNNING, true);
                } catch (Exception e) {
                    Log.e(TAG, "start: Failed to start: " + e.getMessage(), e);
                }
            } else {
                Log.e(TAG, "start: No BLE scanner instance");
            }
        } else {
            Log.d(TAG, "start: Already running");
        }

        return (mState != State.NOT_STARTED);
    }

    /**
     * Stops the scanning.
     *
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
     *
     * @param scanFilter The scan filter to add.
     */
    public void addScanFilter(ScanFilter scanFilter) {
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
     *
     * @param scanSettings The new scan settings.
     */
    public void setScanSettings(ScanSettings scanSettings) {
        if (scanSettings != null) {
            mScanSettings = scanSettings;

            Log.i(TAG, "setScanSettings: Mode: " + mScanSettings.getScanMode()
                    + ", report delay in milliseconds: " + mScanSettings.getReportDelayMillis()
                    + ", scan result type: " + mScanSettings.getScanResultType());
        } else {
            throw new NullPointerException("The argument (ScanSettings) cannot be null");
        }
    }

    /**
     * Applies the additional, default scan setting values for Marshmallow.
     * <p>
     * Thali specs dictate that when calling startScan the settings argument MUST be used and MUST be set to:
     * <p>
     * setCallbackType(callbackType) - If on API 23 then callbackType MUST be set to the flag CALLBACK_TYPE_ALL_MATCHES and MUST NOT include the CALLBACK_TYPE_MATCH_LOST. We are explicitly not going to worry about announcing when a BLE peripheral has gone. It really shouldn't matter given how we are using BLE.
     * setMatchMode(matchMode) - If on API 23 then matchMode MUST be set to MATCH_MODE_STICKY .
     * setNumOfMatches(numOfMatches) - If on API 23 then numOfMatches MUST bet set to MATCH_NUM_MAX_ADVERTISEMENT.
     */
    @TargetApi(23)
    public void applyAdditionalMarshmallowSettings(ScanSettings.Builder scanSettingsBuilder) {
        Log.d(TAG, "applyAdditionalMarshmallowSettings");
        scanSettingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        scanSettingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_STICKY);
        scanSettingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
    }

    @Override
    public void onBatchScanResults(List<ScanResult> scanResults) {
        super.onBatchScanResults(scanResults);
        Log.d(TAG, "onBatchScanResults: " + ThreadUtils.currentThreadToString());
        if (mListener != null) {
            for (ScanResult scanResult : scanResults) {
                if (scanResult != null) {
                    mListener.onScanResult(scanResult);
                }
            }
        } else {
            Log.wtf(TAG, "No listener");
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
        String reason = "";
        Log.d(TAG, ThreadUtils.currentThreadToString());
        switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED:
                reason = "BLE scan with the same settings is already started by the app";
                Log.e(TAG, "onScanFailed: " + reason + ", error code is " + errorCode);
                try {
                    mBluetoothLeScanner.stopScan(this);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "onScanFailed: stop scan failure " + e.getMessage());
                }
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
            default:
                reason = "Unknown error";
                break;
        }

        Log.e(TAG, "onScanFailed: " + reason + ", error code is " + errorCode);
        setState(State.NOT_STARTED, true);

        if (mListener != null) {
            mListener.onScannerFailed(errorCode);
        }
    }

    @Override
    public void onScanResult(int callbackType, ScanResult scanResult) {
        super.onScanResult(callbackType, scanResult);
        Log.d(TAG, "onScanResult");
        Log.d(TAG, ThreadUtils.currentThreadToString());
        if (scanResult != null) {
            if (mListener == null) {
                Log.wtf(TAG, "No listener");
            }
            if (mListener != null) {
                mListener.onScanResult(scanResult);
            }
        } else {
            Log.d(TAG, "onScanResult: there are no scan result");
        }
    }

    /**
     * Sets the state and notifies listener if required.
     *
     * @param state              The new state.
     * @param notifyStateChanged If true, will notify the listener, if the state is changed.
     */
    private synchronized void setState(State state, boolean notifyStateChanged) {
        Log.d(TAG, "set state: " + ThreadUtils.currentThreadToString());
        if (mState != state) {
            Log.d(TAG, "setState: State changed from " + mState + " to " + state);
            mState = state;

            if (notifyStateChanged && mListener != null) {
                switch (mState) {
                    case NOT_STARTED:
                        mListener.onIsScannerStartedChanged(false);
                        break;
                    case RUNNING:
                        mListener.onIsScannerStartedChanged(true);
                        break;
                    default:
                        // Nothing to do here
                        break;
                }
            }
        }
    }
}
