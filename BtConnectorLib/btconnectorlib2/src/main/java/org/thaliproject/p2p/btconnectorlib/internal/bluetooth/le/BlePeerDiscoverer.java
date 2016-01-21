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
         * Called when we receive a request from a peer to provide it its Bluetooth MAC address.
         * @param requestId The request ID associated with the device in need of assistance.
         */
        void onProvideBluetoothMacAddressRequest(String requestId);

        /**
         * Called when we see that a peer is willing to provide us our own Bluetooth MAC address
         * via Bluetooth device discovery. After receiving this event, we should make our device
         * discoverable via Bluetooth.
         * @param requestId The request ID.
         */
        void onPeerReadyToProvideBluetoothMacAddress(String requestId);

        /**
         * Called when we are done broadcasting our willingness to help the peer provide its
         * Bluetooth MAC address or done broadcasting the discovered Bluetooth MAC address.
         *
         * Note: The operation is never considered to be completed, since this method is called
         * when the advertising timeouts.
         *
         * @param requestId The request ID associated with the device in need of assistance.
         * @param wasCompleted True, if the operation was completed.
         */
        void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted);

        /**
         * Called when we receive our own Bluetooth MAC address.
         * @param bluetoothMacAddress Our Bluetooth MAC address.
         */
        void onBluetoothMacAddressResolved(String bluetoothMacAddress);

        /**
         * Called when a peer was discovered.
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);
    }

    private static final String TAG = BlePeerDiscoverer.class.getName();
    private static final int UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD = 9;
    private final BlePeerDiscoveryListener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private final String mMyPeerName;
    private final UUID mServiceUuid;
    private final UUID mProvideBluetoothMacAddressRequestUuid;
    private String mMyBluetoothMacAddress = null;
    private String mOurRequestId = null;
    private BleAdvertiser mBleAdvertiser = null;
    private BleScanner mBleScanner = null;
    private BleAdvertiser mPeerAddressHelperBleAdvertiser = null;
    private CountDownTimer mPeerAddressHelperAdvertisementTimeoutTimer = null;
    private boolean mIsAdvertiserStarted = false;
    private boolean mIsScannerStarted = false;
    private boolean mIsStarted = false;

    /**
     * See PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid
     */
    public static UUID generateNewProvideBluetoothMacAddressRequestUuid(UUID serviceUuid) {
        return PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid(serviceUuid);
    }

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param myPeerName Our peer name for advertisement.
     * @param serviceUuid The BLE service UUID.
     * @param myBluetoothMacAddress Our Bluetooth MAC address for advertisement.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            String myPeerName, UUID serviceUuid, UUID provideBluetoothMacAddressRequestUuid,
            String myBluetoothMacAddress) {
        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mMyPeerName = myPeerName;
        mServiceUuid = serviceUuid;
        mProvideBluetoothMacAddressRequestUuid = provideBluetoothMacAddressRequestUuid;
        mMyBluetoothMacAddress = myBluetoothMacAddress;

        mBleAdvertiser = new BleAdvertiser(this, mBluetoothAdapter);

        if (mMyBluetoothMacAddress == null) {
            // Request UUID and ID are only needed in case we don't know our own Bluetooth MAC
            // address and need to request it from a peer
            mOurRequestId = PeerAdvertisementFactory.parseRequestIdFromUuid(mProvideBluetoothMacAddressRequestUuid);
            Log.i(TAG, "BlePeerDiscoverer: Provide Bluetooth MAC address request ID is " + mOurRequestId);

            mBleAdvertiser.setAdvertiseData(
                    PeerAdvertisementFactory.createAdvertiseData(
                            myPeerName, mProvideBluetoothMacAddressRequestUuid,
                            PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN));
        } else {
            mBleAdvertiser.setAdvertiseData(
                    PeerAdvertisementFactory.createAdvertiseData(
                            myPeerName, mServiceUuid, mMyBluetoothMacAddress));
        }

        mBleScanner = new BleScanner(this, mBluetoothAdapter);
        mBleScanner.addFilter(BlePeerDiscoveryUtils.createScanFilter(null));
    }

    /**
     * @return Our current "provide Bluetooth MAC address" request ID generated by this instance.
     */
    public String getProvideBluetoothMacAddressRequestId() {
        return mOurRequestId;
    }

    /**
     * Sets the Bluetooth MAC address for the advertiser.
     * @param myBluetoothMacAddress Our Bluetooth MAC address.
     */
    public void setBluetoothMacAddress(String myBluetoothMacAddress) {
        if (myBluetoothMacAddress != null) {
            mMyBluetoothMacAddress = myBluetoothMacAddress;
            mOurRequestId = null;

            if (mBleAdvertiser == null) {
                mBleAdvertiser = new BleAdvertiser(this, mBluetoothAdapter);
            }

            mBleAdvertiser.setAdvertiseData(
                    PeerAdvertisementFactory.createAdvertiseData(mMyPeerName, mServiceUuid, mMyBluetoothMacAddress));

            if (mIsStarted && !mBleAdvertiser.isStarted()) {
                mBleAdvertiser.start();
            }
        } else {
            Log.e(TAG, "setBluetoothMacAddress: The given address was null");
        }
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
            if (mBleAdvertiser != null) {
                isStarting = (mBleAdvertiser.start() && mBleScanner.start());
            } else {
                isStarting = mBleScanner.start();
            }
        }

        return isStarting;
    }

    /**
     * Stops the BLE peer discovery.
     */
    public synchronized void stop() {
        Log.i(TAG, "stop");

        stopPeerAddressHelperAdvertiser();

        if (mBleAdvertiser != null) {
            mBleAdvertiser.stop();
        }

        mBleScanner.stop();
    }

    /**
     * Starts advertising the given Bluetooth MAC address with the given UUID via BLE for a certain
     * period of time.
     * @param requestId The request ID.
     * @param bluetoothMacAddress A Bluetooth MAC address of a discovered device.
     *                            Use PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN to notify the
     *                            peer of our willingness to help (so that it will make itself
     *                            discoverable).
     * @param durationInMilliseconds The duration of the advertising in milliseconds.
     * @return True, if started. False otherwise.
     */
    public synchronized boolean startPeerAddressHelperAdvertiser(
            final String requestId, String bluetoothMacAddress, long durationInMilliseconds) {
        boolean wasStarted = false;

        if (mPeerAddressHelperBleAdvertiser == null) {
            mPeerAddressHelperBleAdvertiser = new BleAdvertiser(null, mBluetoothAdapter);
            UUID baseUuid = mServiceUuid;

            if (bluetoothMacAddress.equals(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)) {
                // We have not yet discovered the peer, but we notify it that we are willing to help
                // in order for it to know to make itself discoverable
                baseUuid = BlePeerDiscoveryUtils.rotateByte(
                        mServiceUuid, UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD);
            }

            UUID uuid = PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(baseUuid, requestId);

            mPeerAddressHelperBleAdvertiser.setAdvertiseData(
                    PeerAdvertisementFactory.createAdvertiseData(
                            PeerProperties.NO_PEER_NAME_STRING, uuid, bluetoothMacAddress));

            if (mPeerAddressHelperAdvertisementTimeoutTimer != null) {
                mPeerAddressHelperAdvertisementTimeoutTimer.cancel();
                mPeerAddressHelperAdvertisementTimeoutTimer = null;
            }

            mPeerAddressHelperAdvertisementTimeoutTimer =
                    new CountDownTimer(durationInMilliseconds, durationInMilliseconds) {
                @Override
                public void onTick(long millisUntilFinished) {
                    // Not used
                }

                @Override
                public void onFinish() {
                    mPeerAddressHelperBleAdvertiser.stop();
                    mPeerAddressHelperBleAdvertiser = null;
                    mPeerAddressHelperAdvertisementTimeoutTimer = null;
                    Log.i(TAG, "Stopped advertising the Bluetooth MAC address of a discovered device");
                    mListener.onProvideBluetoothMacAddressResult(requestId, false);
                }
            };

            if (mPeerAddressHelperBleAdvertiser.start()) {
                Log.i(TAG, "startPeerAddressHelperAdvertiser: Started advertising: " + uuid + " " + bluetoothMacAddress);
                mPeerAddressHelperAdvertisementTimeoutTimer.start();
                wasStarted = true;
            } else {
                mPeerAddressHelperAdvertisementTimeoutTimer.cancel();
                mPeerAddressHelperAdvertisementTimeoutTimer = null;
            }
        }

        return wasStarted;
    }

    /**
     * Stops the peer Bluetooth MAC address helper advertiser.
     */
    public synchronized void stopPeerAddressHelperAdvertiser() {
        if (mPeerAddressHelperAdvertisementTimeoutTimer != null) {
            mPeerAddressHelperAdvertisementTimeoutTimer.cancel();
            mPeerAddressHelperAdvertisementTimeoutTimer = null;
        }

        if (mPeerAddressHelperBleAdvertiser != null) {
            mPeerAddressHelperBleAdvertiser.stop();
            Log.i(TAG, "stopPeerAddressHelperAdvertiser: Stopped");
        }
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
        mIsAdvertiserStarted = isStarted;
        setIsStarted(mIsScannerStarted && mIsAdvertiserStarted);
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
        mIsScannerStarted = isStarted;

        if (mIsAdvertiserStarted || mBleAdvertiser == null) {
            setIsStarted(mIsScannerStarted);
        }
    }

    @Override
    public void onScanResult(ScanResult result) {
        checkScanResult(result);
    }

    /**
     * Tries to parse peer properties from the given result. If a valid peer is found or the
     * received data contains either a provide Bluetooth MAC address or our own address (which could
     * be unknown to us), the listener will be notified.
     * @param scanResult The scan result.
     */
    private synchronized void checkScanResult(ScanResult scanResult) {
        byte[] manufacturerData = null;

        if (scanResult.getScanRecord() != null) {
            manufacturerData = scanResult.getScanRecord().getManufacturerSpecificData(
                    PeerAdvertisementFactory.MANUFACTURER_ID);
        }

        if (manufacturerData != null) {
            BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement =
                    BlePeerDiscoveryUtils.parseManufacturerData(manufacturerData, mServiceUuid);

            PeerProperties peerProperties = null;
            boolean isProvideBluetoothMacAddressRequest = false;
            boolean isPeerReadyToProvideBluetoothMacAddress = false;
            boolean containsOurOwnBluetoothMacAddress = false;

            if (parsedAdvertisement != null) {
                if (parsedAdvertisement.provideBluetoothMacAddressId != null) {
                    Log.i(TAG, "checkScanResult: Received Bluetooth MAC address request ID: "
                            + parsedAdvertisement.provideBluetoothMacAddressId);

                    boolean bluetoothMacAddressIsNullOrUnknown =
                            (parsedAdvertisement.bluetoothMacAddress == null
                             || parsedAdvertisement.bluetoothMacAddress.equals(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN));

                    if (bluetoothMacAddressIsNullOrUnknown) {
                        //Log.d(TAG, "checkScanResult: Our request ID: " + mOurRequestId);

                        UUID rotatedProvideBluetoothMacAddressRequestUuid =
                                BlePeerDiscoveryUtils.rotateByte(
                                        mProvideBluetoothMacAddressRequestUuid,
                                        UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD);

                        //Log.d(TAG, "checkScanResult: Comparing \"" + parsedAdvertisement.uuid
                        //        + "\" with \"" + rotatedProvideBluetoothMacAddressRequestUuid + "\"");

                        if (mOurRequestId != null
                                && parsedAdvertisement.uuid.equals(rotatedProvideBluetoothMacAddressRequestUuid)) {
                            isPeerReadyToProvideBluetoothMacAddress = true;
                        } else {
                            isProvideBluetoothMacAddressRequest = true;
                        }
                    } else if (mOurRequestId != null
                            && parsedAdvertisement.provideBluetoothMacAddressId.equals(mOurRequestId)) {
                        containsOurOwnBluetoothMacAddress = true;
                    }
                } else {
                    // Try to construct peer properties from the received data
                    peerProperties = PeerAdvertisementFactory.parsedAdvertisementToPeerProperties(parsedAdvertisement);
                }
            }

            if (mListener != null) {
                if (isPeerReadyToProvideBluetoothMacAddress) {
                    mListener.onPeerReadyToProvideBluetoothMacAddress(mOurRequestId);
                } else if (isProvideBluetoothMacAddressRequest) {
                    // Compare the request IDs, if we don't know our Bluetooth MAC address either,
                    // to get rid of a possible race condition.
                    if (mMyBluetoothMacAddress != null
                        || parsedAdvertisement.provideBluetoothMacAddressId.compareTo(mOurRequestId) > 0) {
                        //Log.d(TAG, "checkScanResult: Will try to provide a device its Bluetooth MAC address");
                        mListener.onProvideBluetoothMacAddressRequest(
                                parsedAdvertisement.provideBluetoothMacAddressId);
                    } else {
                        Log.d(TAG, "checkScanResult: Will not try to provide a device its Bluetooth MAC address, but expect it to provide ours instead");
                    }
                } else if (containsOurOwnBluetoothMacAddress) {
                    mListener.onBluetoothMacAddressResolved(parsedAdvertisement.bluetoothMacAddress);
                } else if (peerProperties != null) {
                    mListener.onPeerDiscovered(peerProperties);
                }
            }
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
