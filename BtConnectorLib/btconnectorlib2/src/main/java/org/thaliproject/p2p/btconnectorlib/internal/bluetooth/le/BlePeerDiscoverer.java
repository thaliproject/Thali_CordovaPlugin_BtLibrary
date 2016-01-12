/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.CountDownTimer;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.UUID;

/**
 * The main interface for BLE based peer discovery.
 */
@TargetApi(21)
public class BlePeerDiscoverer implements BleAdvertiser.Listener, BleScanner.Listener {
    /**
     * A listener for peer discovery events.
     */
    public interface BlePeerDiscoveryListener {
        /**
         * Called when the discovery is started or stopped.
         * @param isStarted If true, the discovery was started. If false, it was stopped.
         */
        void onIsBlePeerDiscoveryStartedChanged(boolean isStarted);

        /**
         * Called when a peer was discovered.
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);
    }

    private static final String TAG = BlePeerDiscoverer.class.getName();
    private static final long ADVERTISE_PEER_ADDRESS_TIMEOUT_IN_MILLISECONDS = 10000;
    private final BlePeerDiscoveryListener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private final UUID mServiceUuid;
    private final UUID mBluetoothDeviceDiscoveryUuid;
    private BleAdvertiser mBleAdvertiser = null;
    private BleScanner mBleScanner = null;
    private BleAdvertiser mPeerAddressBleAdvertiser = null;
    private boolean mIsAdvertiserStarted = false;
    private boolean mIsScannerStarted = false;
    private boolean mIsStarted = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param myPeerName Our peer name for advertisement.
     * @param serviceUuid The BLE service UUID.
     * @param myBluetoothAddress Our Bluetooth address for advertisement.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            String myPeerName, UUID serviceUuid, String myBluetoothAddress) {
        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mServiceUuid = serviceUuid;
        mBluetoothDeviceDiscoveryUuid = BlePeerDiscoveryUtils.rotateTheLastByte(mServiceUuid);

        mBleAdvertiser = new BleAdvertiser(this, mBluetoothAdapter);

        mBleAdvertiser.setAdvertiseData(
                PeerAdvertisementFactory.createAdvertiseData(myPeerName, mServiceUuid, myBluetoothAddress));

        mBleScanner = new BleScanner(this, mBluetoothAdapter);
        //mBleScanner.addFilter(PeerAdvertisementFactory.createScanFilter(mServiceUuid));
        mBleScanner.addFilter(BlePeerDiscoveryUtils.createScanFilter(null));
    }

    /**
     * Sets the settings for both the BLE advertiser and the scanner.
     * @param advertiseMode The advertise mode for the BLE advertiser.
     * @param advertiseTxPowerLevel The advertise TX power level for the BLE advertiser.
     * @param scanMode The scan mode for the BLE scanner.
     * @return True, if all the settings were applied successfully. False, if at least one of
     * settings failed to be applied.
     */
    public boolean applySettings(int advertiseMode, int advertiseTxPowerLevel, int scanMode) {
        Log.i(TAG, "applySettings: Advertise mode: " + advertiseMode
                + ", advertise TX power level: " + advertiseTxPowerLevel
                + ", scan mode: " + scanMode);

        boolean advertiserSettingsWereSet = false;
        AdvertiseSettings.Builder advertiseSettingsBuilder = new AdvertiseSettings.Builder();

        try {
            advertiseSettingsBuilder.setAdvertiseMode(advertiseMode);
            advertiseSettingsBuilder.setTxPowerLevel(advertiseTxPowerLevel);
            advertiserSettingsWereSet = true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "applySettings: Failed to apply advertiser settings: " + e.getMessage(), e);
        }

        if (advertiserSettingsWereSet && mBleAdvertiser != null) {
            boolean advertiserWasStarted = mBleAdvertiser.isStarted();

            if (advertiserWasStarted) {
                mBleAdvertiser.stop();
            }

            mBleAdvertiser.setAdvertiseSettings(advertiseSettingsBuilder.build());

            if (advertiserWasStarted) {
                mBleAdvertiser.start();
            }
        }

        boolean scannerSettingsWereSet = false;
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();

        try {
            scanSettingsBuilder.setScanMode(scanMode);
            scannerSettingsWereSet = true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "applySettings: Failed to apply scan mode setting: " + e.getMessage(), e);
        }

        if (scannerSettingsWereSet && mBleScanner != null) {
            boolean scannerWasStarted = mBleScanner.isStarted();

            if (scannerWasStarted) {
                mBleScanner.stop();
            }

            mBleScanner.setScanSettings(scanSettingsBuilder.build());

            if (scannerWasStarted) {
                mBleScanner.start();
            }
        }

        return (advertiserSettingsWereSet && scannerSettingsWereSet);
    }

    /**
     * Starts the BLE peer discovery.
     * @return True, if starting or already started. False, if failed to start.
     */
    public synchronized boolean start() {
        boolean isStarting = (mIsAdvertiserStarted && mIsScannerStarted);

        if (!mIsAdvertiserStarted || !mIsScannerStarted) {
            isStarting = (mBleAdvertiser.start() && mBleScanner.start());
        }

        return isStarting;
    }

    /**
     * Stops the BLE peer discovery.
     */
    public synchronized void stop() {
        mBleAdvertiser.stop();
        mBleScanner.stop();
    }

    /**
     * Starts advertising the given Bluetooth MAC address via BLE for a certain period of time.
     * @param bluetoothMacAddress A Bluetooth MAC address of a discovered device.
     * @return True, if started. False otherwise.
     */
    public boolean startAdvertisingBluetoothAddressOfDiscoveredDevice(String bluetoothMacAddress) {
        boolean wasStarted = false;

        if (mPeerAddressBleAdvertiser == null) {
            mPeerAddressBleAdvertiser = new BleAdvertiser(null, mBluetoothAdapter);
            mPeerAddressBleAdvertiser.setAdvertiseData(
                    PeerAdvertisementFactory.createAdvertiseData(
                            PeerProperties.NO_PEER_NAME_STRING, mBluetoothDeviceDiscoveryUuid, bluetoothMacAddress));

            new CountDownTimer(
                    ADVERTISE_PEER_ADDRESS_TIMEOUT_IN_MILLISECONDS,
                    ADVERTISE_PEER_ADDRESS_TIMEOUT_IN_MILLISECONDS) {
                @Override
                public void onTick(long millisUntilFinished) {
                    // Not used
                }

                @Override
                public void onFinish() {
                    mPeerAddressBleAdvertiser.stop();
                    mPeerAddressBleAdvertiser = null;
                    Log.i(TAG, "Stopped advertising the Bluetooth MAC address of a discovered device");
                }
            }.start();

            if (mPeerAddressBleAdvertiser.start()) {
                Log.i(TAG, "startAdvertisingBluetoothAddressOfDiscoveredDevice: Started advertising Bluetooth MAC address: " + bluetoothMacAddress);
                wasStarted = true;
            }
        }

        return wasStarted;
    }

    @Override
    public void onAdvertiserFailedToStart(int errorCode) {
        Log.e(TAG, "onAdvertiserFailedToStart: " + errorCode);
        mIsAdvertiserStarted = false;
        setIsStarted(false);
    }

    @Override
    public void onIsAdvertiserStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsAdvertiserStartedChanged: " + isStarted);
        mIsAdvertiserStarted = true;

        if (mIsScannerStarted) {
            setIsStarted(true);
        }
    }

    @Override
    public void onScannerFailed(int errorCode) {
        Log.e(TAG, "onScannerFailed: " + errorCode);
        mIsScannerStarted = false;
        setIsStarted(false);
    }

    @Override
    public void onIsScannerStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsScannerStartedChanged: " + isStarted);
        mIsScannerStarted = true;

        if (mIsAdvertiserStarted) {
            setIsStarted(true);
        }
    }

    @Override
    public void onScanResult(ScanResult result) {
        checkScanResult(result);
    }

    /**
     * Tries to parse peer properties from the given result. If a valid peer is found, the listener
     * will be notified.
     * @param scanResult The scan result.
     */
    private synchronized void checkScanResult(ScanResult scanResult) {
        byte[] manufacturerData = null;

        if (scanResult.getScanRecord() != null) {
            manufacturerData = scanResult.getScanRecord().getManufacturerSpecificData(
                    PeerAdvertisementFactory.MANUFACTURER_ID);
        }

        PeerProperties peerProperties = null;

        if (manufacturerData != null) {
            peerProperties = PeerAdvertisementFactory.manufacturerDataToPeerProperties(manufacturerData, mServiceUuid);
        }

        if (peerProperties != null && mListener != null) {
            mListener.onPeerDiscovered(peerProperties);
        }
    }

    /**
     * Sets the state and notifies the listener.
     * @param isStarted True, if the peer discovery is started. False otherwise.
     */
    private synchronized void setIsStarted(boolean isStarted) {
        if (mIsStarted != isStarted) {
            Log.d(TAG, "setIsStarted: " + isStarted);
            mIsStarted = isStarted;

            if (mListener != null) {
                mListener.onIsBlePeerDiscoveryStartedChanged(mIsStarted);
            }
        }
    }
}
