/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.CountDownTimer;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import java.util.EnumSet;
import java.util.Map;
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
         * Called when the state of this class has changed.
         * @param state The new state.
         */
        void onBlePeerDiscovererStateChanged(EnumSet<BlePeerDiscovererStateSet> state);

        /**
         * Called when a peer was discovered.
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);


        // BRO MODE INTERFACES - NOT CURRENTLY BEING USED


        /**
         * Called when we receive a request from a peer to provide it its Bluetooth MAC address.
         *
         * Note: This callback is not being used and thus not tested constantly so there is no
         * guarantee it'll work flawlessly.
         *
         * @param requestId The request ID associated with the device in need of assistance.
         */
        void onProvideBluetoothMacAddressRequest(String requestId);

        /**
         * Called when we see that a peer is willing to provide us our own Bluetooth MAC address
         * via Bluetooth device discovery. After receiving this event, we should make our device
         * discoverable via Bluetooth.
         *
         * Note: This callback is not being used and thus not tested constantly so there is no
         * guarantee it'll work flawlessly.
         *
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
         * Note: This callback is not being used and thus not tested constantly so there is no
         * guarantee it'll work flawlessly.
         *
         * @param requestId The request ID associated with the device in need of assistance.
         * @param wasCompleted True, if the operation was completed.
         */
        void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted);

        /**
         * Called when we receive our own Bluetooth MAC address.
         *
         * Note: This callback is not being used and thus not tested constantly so there is no
         * guarantee it'll work flawlessly.
         *
         * @param bluetoothMacAddress Our Bluetooth MAC address.
         */
        void onBluetoothMacAddressResolved(String bluetoothMacAddress);
    }

    /**
     * Possible states of this class.
     */
    public enum BlePeerDiscovererStateSet {
        NOT_STARTED,
        SCANNING,
        ADVERTISING_SELF, // Advertising our presence
        ADVERTISING_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST, // Bro Mode - When we need help to find out our own Bluetooth MAC address
        ADVERTISING_PROVIDING_ASSISTANCE // Bro Mode - When helping a peer with its Bluetooth MAC address
    }

    public enum AdvertisementDataType {
        MANUFACTURER_DATA,
        SERVICE_DATA
    }

    protected enum AdvertisementType {
        ADVERTISEMENT_UNKNOWN,
        ADVERTISEMENT_PEER_PROPERTIES, // Peer advertising its presence
        ADVERTISEMENT_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST, // Bro Mode - Peer requesting assistance
        ADVERTISEMENT_PEER_READY_TO_PROVIDE_BLUETOOTH_MAC_ADDRESS, // Bro Mode - Peer is ready to provide assistance to us
        ADVERTISEMENT_PEER_PROVIDING_OUR_BLUETOOTH_MAC_ADDRESS // Bro Mode - Peer has resolved our Bluetooth MAC address
    }

    private static final String TAG = BlePeerDiscoverer.class.getName();
    private static final int UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD = 9;
    private final BlePeerDiscoveryListener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private final UUID mServiceUuid;
    private final UUID mProvideBluetoothMacAddressRequestUuid;
    private final AdvertisementDataType mAdvertisementDataType;
    private String mMyBluetoothMacAddress = null;
    private EnumSet<BlePeerDiscovererStateSet> mStateSet = EnumSet.of(BlePeerDiscovererStateSet.NOT_STARTED);
    private final BleAdvertiser mBleAdvertiser;
    private final BleScanner mBleScanner;
    private String mOurRequestId = null;
    private CountDownTimer mPeerAddressHelperAdvertisementTimeoutTimer = null;
    private boolean mIsAssistingPeer = false;
    private boolean mWasAdvertiserStartedBeforeStartingToAssistPeer = false;

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
     * @param serviceUuid The BLE service UUID.
     * @param provideBluetoothMacAddressRequestUuid UUID for "Provide Bluetooth MAC address" mode.
     * @param myBluetoothMacAddress Our Bluetooth MAC address for advertisement.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceUuid, UUID provideBluetoothMacAddressRequestUuid,
            String myBluetoothMacAddress, AdvertisementDataType advertisementDataType) {
        mListener = listener;

        if (mListener == null) {
            throw new NullPointerException("BlePeerDiscoveryListener cannot be null");
        }

        mBluetoothAdapter = bluetoothAdapter;
        mServiceUuid = serviceUuid;
        mProvideBluetoothMacAddressRequestUuid = provideBluetoothMacAddressRequestUuid;
        mMyBluetoothMacAddress = myBluetoothMacAddress;
        mAdvertisementDataType = advertisementDataType;

        mBleAdvertiser = new BleAdvertiser(this, mBluetoothAdapter);

        if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
            // Request UUID and ID are only needed in case we don't know our own Bluetooth MAC
            // address and need to request it from a peer
            mOurRequestId = PeerAdvertisementFactory.parseRequestIdFromUuid(mProvideBluetoothMacAddressRequestUuid);
            Log.i(TAG, "BlePeerDiscoverer: Provide Bluetooth MAC address request ID is " + mOurRequestId);
        }

        mBleScanner = new BleScanner(this, mBluetoothAdapter);
        ScanFilter scanFilter = null;

        if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA) {
            scanFilter = BlePeerDiscoveryUtils.createScanFilter(mServiceUuid, false);
        } else {
            scanFilter = BlePeerDiscoveryUtils.createScanFilter(null, true);
        }

        mBleScanner.addFilter(scanFilter);
    }

    /**
     * @return The current state of this instance.
     */
    public EnumSet<BlePeerDiscovererStateSet> getState() {
        return mStateSet;
    }

    /**
     * @return Our current "provide Bluetooth MAC address" request ID generated by this instance.
     */
    public String getProvideBluetoothMacAddressRequestId() {
        return mOurRequestId;
    }

    /**
     * @return The Bluetooth MAC address given/set to this instance. Note that this does not in
     * anyway try to resolve the address, but only returns what is given.
     */
    public String getBluetoothMacAddress() {
        return mMyBluetoothMacAddress;
    }

    /**
     * Sets the Bluetooth MAC address. Note that the advertiser is not restarted automatically.
     * @param myBluetoothMacAddress Our Bluetooth MAC address.
     */
    public void setBluetoothMacAddress(String myBluetoothMacAddress) {
        if (BluetoothUtils.isValidBluetoothMacAddress(myBluetoothMacAddress)) {
            Log.d(TAG, "setBluetoothMacAddress: " + myBluetoothMacAddress);
            mMyBluetoothMacAddress = myBluetoothMacAddress;
            mOurRequestId = null;
        } else {
            Log.e(TAG, "setBluetoothMacAddress: The given Bluetooth MAC address is invalid: " + myBluetoothMacAddress);
        }
    }

    /**
     * Sets the settings for both the BLE advertiser and the scanner.
     * @param advertiseMode The advertise mode for the BLE advertiser.
     * @param advertiseTxPowerLevel The advertise TX power level for the BLE advertiser.
     * @param scanMode The scan mode for the BLE scanner.
     * @param scanReportDelayInMilliseconds The new scan report delay in milliseconds.
     * @return True, if all the settings were applied successfully. False, if at least one of
     * settings failed to be applied.
     */
    public boolean applySettings(int advertiseMode, int advertiseTxPowerLevel, int scanMode, long scanReportDelayInMilliseconds) {
        Log.i(TAG, "applySettings: Advertise mode: " + advertiseMode
                + ", advertise TX power level: " + advertiseTxPowerLevel
                + ", scan mode: " + scanMode);

        boolean advertiserSettingsWereSet = false;
        AdvertiseSettings.Builder advertiseSettingsBuilder = new AdvertiseSettings.Builder();

        try {
            advertiseSettingsBuilder.setAdvertiseMode(advertiseMode);
            advertiseSettingsBuilder.setTxPowerLevel(advertiseTxPowerLevel);
            advertiseSettingsBuilder.setTimeout(0);

            if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
                advertiseSettingsBuilder.setConnectable(true);
            } else {
                advertiseSettingsBuilder.setConnectable(false);
            }

            advertiserSettingsWereSet = true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "applySettings: Failed to apply advertiser settings: " + e.getMessage(), e);
        }

        if (advertiserSettingsWereSet) {
            boolean advertiserWasStarted = mBleAdvertiser.isStarted();

            if (advertiserWasStarted) {
                mBleAdvertiser.stop(false);
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
            scanSettingsBuilder.setReportDelay(scanReportDelayInMilliseconds);

            if (CommonUtils.isMarshmallowOrHigher()) {
                mBleScanner.applyAdditionalMarshmallowSettings(scanSettingsBuilder);
            }

            scannerSettingsWereSet = true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "applySettings: Failed to apply scan mode setting: " + e.getMessage(), e);
        }

        if (scannerSettingsWereSet) {
            boolean scannerWasStarted = mBleScanner.isStarted();

            if (scannerWasStarted) {
                mBleScanner.stop(false);
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
        Log.i(TAG, "start");
        stopPeerAddressHelperAdvertiser();
        AdvertiseData advertiseData = null;

        if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
            advertiseData = createAdvertiseData(
                    mProvideBluetoothMacAddressRequestUuid, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN);
        } else {
            advertiseData = createAdvertiseData(mServiceUuid, mMyBluetoothMacAddress);
            mOurRequestId = null;
        }

        mBleAdvertiser.setAdvertiseData(advertiseData);

        return (mBleScanner.start() && mBleAdvertiser.start());
    }

    /**
     * Stops the BLE peer discovery.
     */
    public synchronized void stop() {
        Log.i(TAG, "stop");
        stopPeerAddressHelperAdvertiser();
        mBleAdvertiser.stop(true);
        mBleScanner.stop(true);
    }

    /**
     * Stops the BLE scanner. Call BlePeerDiscoverer.start to restart.
     */
    public synchronized void stopScanner() {
        Log.d(TAG, "stopScanner");
        mBleScanner.stop(true);
    }

    /**
     * Stops the BLE advertiser. Call BlePeerDiscoverer.start to restart.
     */
    public synchronized void stopAdvertiser() {
        Log.d(TAG, "stopAdvertiser");
        mBleAdvertiser.stop(true);
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
        Log.d(TAG, "startPeerAddressHelperAdvertiser: Request ID: " + requestId
                + ", Bluetooth MAC address: " + bluetoothMacAddress
                + ", Duration in ms: " + durationInMilliseconds);

        boolean wasStarted = false;
        UUID baseUuid = mServiceUuid;

        if (BluetoothUtils.isBluetoothMacAddressUnknown(bluetoothMacAddress)) {
            // We have not yet discovered the peer, but we notify it that we are willing to help
            // in order for it to know to make itself discoverable
            baseUuid = BlePeerDiscoveryUtils.rotateByte(
                    mServiceUuid, UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD);
        }

        UUID uuid = PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(baseUuid, requestId);

        mWasAdvertiserStartedBeforeStartingToAssistPeer = mBleAdvertiser.isStarted();

        mBleAdvertiser.setAdvertiseData(createAdvertiseData(uuid, bluetoothMacAddress));

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
                        stopPeerAddressHelperAdvertiser();
                        Log.i(TAG, "Stopped advertising the Bluetooth MAC address of a discovered device");
                        mListener.onProvideBluetoothMacAddressResult(requestId, false);
                    }
                };

        if (mBleAdvertiser.start()) {
            Log.i(TAG, "startPeerAddressHelperAdvertiser: Started advertising: " + uuid + " " + bluetoothMacAddress);
            mPeerAddressHelperAdvertisementTimeoutTimer.start();
            mIsAssistingPeer = true;
            updateState();
            wasStarted = true;
        } else {
            Log.e(TAG, "startPeerAddressHelperAdvertiser: Failed to start");
            stopPeerAddressHelperAdvertiser();
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

        if (mIsAssistingPeer) {
            Log.d(TAG, "stopPeerAddressHelperAdvertiser: Stopping...");

            if (mWasAdvertiserStartedBeforeStartingToAssistPeer) {
                mWasAdvertiserStartedBeforeStartingToAssistPeer = false;
                mBleAdvertiser.start();
                updateState();
            } else {
                mBleAdvertiser.stop(true);
            }

            mIsAssistingPeer = false;
        }
    }

    @Override
    public void onAdvertiserFailedToStart(int errorCode) {
        Log.e(TAG, "onAdvertiserFailedToStart: " + errorCode);
        // No need to update state here, since onIsAdvertiserStartedChanged will be called
    }

    @Override
    public void onIsAdvertiserStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsAdvertiserStartedChanged: " + isStarted);
        updateState();
    }

    @Override
    public void onScannerFailed(int errorCode) {
        Log.e(TAG, "onScannerFailed: " + errorCode);
        // No need to update state here, since onIsScannerStartedChanged will be called
    }

    @Override
    public void onIsScannerStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsScannerStartedChanged: " + isStarted);
        updateState();
    }

    @Override
    public void onScanResult(ScanResult result) {
        checkScanResult(result);
    }

    /**
     * Tries to parse the given result and take action based on the advertisement type.
     * @param scanResult The scan result.
     */
    private synchronized void checkScanResult(ScanResult scanResult) {
        BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement = null;

        if (scanResult != null && scanResult.getScanRecord() != null) {
            byte[] manufacturerData = null;

            manufacturerData = scanResult.getScanRecord().getManufacturerSpecificData(
                    PeerAdvertisementFactory.MANUFACTURER_ID);
            Map<ParcelUuid, byte[]> serviceData = scanResult.getScanRecord().getServiceData();

            if (manufacturerData != null) {
                parsedAdvertisement = BlePeerDiscoveryUtils.parseManufacturerData(manufacturerData, mServiceUuid);
            } else if (serviceData != null && serviceData.size() > 0) {
                for (ParcelUuid uuid : serviceData.keySet()) {
                    byte[] serviceDataContent = serviceData.get(uuid);
                    //Log.v(TAG, "checkScanResult: Got service data with UUID \"" + uuid + "\"");
                    parsedAdvertisement = BlePeerDiscoveryUtils.parseServiceData(serviceDataContent);

                    if (parsedAdvertisement != null) {
                        parsedAdvertisement.uuid = scanResult.getScanRecord().getServiceUuids().get(0).getUuid();
                        parsedAdvertisement.provideBluetoothMacAddressRequestId =
                                BlePeerDiscoveryUtils.checkIfUuidContainsProvideBluetoothMacAddressRequestId(
                                        parsedAdvertisement.uuid, mServiceUuid);
                        break;
                    }
                }
            }
        }

        if (parsedAdvertisement != null) {
            AdvertisementType advertisementType = resolveAdvertisementType(parsedAdvertisement);
            //Log.v(TAG, "checkScanResult: Resolved advertisement type: " + advertisementType);

            switch (advertisementType) {
                case ADVERTISEMENT_UNKNOWN:
                    break;
                case ADVERTISEMENT_PEER_PROPERTIES:
                    PeerProperties peerProperties =
                            PeerAdvertisementFactory.parsedAdvertisementToPeerProperties(parsedAdvertisement);

                    if (peerProperties != null) {
                        mListener.onPeerDiscovered(peerProperties);
                    }

                    break;
                case ADVERTISEMENT_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST:
                    // Compare the request IDs, if we don't know our Bluetooth MAC address either,
                    // to get rid of a possible race condition.
                    if (mMyBluetoothMacAddress != null
                            || parsedAdvertisement.provideBluetoothMacAddressRequestId.compareTo(mOurRequestId) > 0) {
                        //Log.d(TAG, "checkScanResult: Will try to provide a device its Bluetooth MAC address");
                        mListener.onProvideBluetoothMacAddressRequest(
                                parsedAdvertisement.provideBluetoothMacAddressRequestId);
                    } else {
                        Log.d(TAG, "checkScanResult: Will not try to provide a device its Bluetooth MAC address, but expect it to provide ours instead");
                    }

                    break;
                case ADVERTISEMENT_PEER_READY_TO_PROVIDE_BLUETOOTH_MAC_ADDRESS:
                    mListener.onPeerReadyToProvideBluetoothMacAddress(mOurRequestId);
                    break;
                case ADVERTISEMENT_PEER_PROVIDING_OUR_BLUETOOTH_MAC_ADDRESS:
                    mListener.onBluetoothMacAddressResolved(parsedAdvertisement.bluetoothMacAddress);
                    break;
                default:
                    Log.e(TAG, "checkScanResult: Unrecognized advertisement type");
            }
        }
    }

    /**
     * Resolves and updates the state and notifies the listener.
     */
    private synchronized void updateState() {
        EnumSet<BlePeerDiscovererStateSet> deducedStateSet =
                EnumSet.noneOf(BlePeerDiscovererStateSet.class);

        if (mBleScanner.isStarted()) {
            deducedStateSet.add(BlePeerDiscovererStateSet.SCANNING);
        }

        if (mBleAdvertiser.isStarted()) {
            if (mIsAssistingPeer) {
                deducedStateSet.add(BlePeerDiscovererStateSet.ADVERTISING_PROVIDING_ASSISTANCE);
            } else if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
                // We do not need our own Bluetooth MAC address
                deducedStateSet.add(BlePeerDiscovererStateSet.ADVERTISING_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST);
            } else {
                deducedStateSet.add(BlePeerDiscovererStateSet.ADVERTISING_SELF);
            }
        }

        if (deducedStateSet.isEmpty()) {
            deducedStateSet.add(BlePeerDiscovererStateSet.NOT_STARTED);
        }

        if (!mStateSet.equals(deducedStateSet)) {
            Log.d(TAG, "updateState(): State changed from " + mStateSet + " to " + deducedStateSet);
            mStateSet = deducedStateSet;
            mListener.onBlePeerDiscovererStateChanged(mStateSet);
        }
    }

    /**
     * Creates the advertise data based on the current advertise data type and the given properties.
     * @param uuid The UUID.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     * @return A newly created AdvertiseData instance.
     */
    private AdvertiseData createAdvertiseData(UUID uuid, String bluetoothMacAddress) {
        AdvertiseData advertiseData = null;

        if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA) {
            advertiseData = PeerAdvertisementFactory.createAdvertiseDataToServiceData(uuid, bluetoothMacAddress);
        } else {
            advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(uuid, bluetoothMacAddress);
        }

        return advertiseData;
    }

    /**
     * Resolves the type of the parsed advertisement.
     * @param parsedAdvertisement The parsed advertisement.
     * @return The advertisement type. Will return AdvertisementType.ADVERTISEMENT_UNKNOWN, if the
     * type is not recognized (and should be ignored).
     */
    private AdvertisementType resolveAdvertisementType(
            BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement) {
        AdvertisementType advertisementType = AdvertisementType.ADVERTISEMENT_UNKNOWN;

        if (parsedAdvertisement != null) {
            if (parsedAdvertisement.provideBluetoothMacAddressRequestId != null) {
                //Log.i(TAG, "resolveAdvertisementType: Received Bluetooth MAC address request ID: "
                //        + parsedAdvertisement.provideBluetoothMacAddressRequestId);

                if (BluetoothUtils.isBluetoothMacAddressUnknown(parsedAdvertisement.bluetoothMacAddress)) {
                    //Log.d(TAG, "checkScanResult: Our request ID: " + mOurRequestId);

                    UUID rotatedProvideBluetoothMacAddressRequestUuid =
                            BlePeerDiscoveryUtils.rotateByte(
                                    mProvideBluetoothMacAddressRequestUuid,
                                    UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD);

                    if (mOurRequestId != null
                            && parsedAdvertisement.uuid.equals(rotatedProvideBluetoothMacAddressRequestUuid)) {
                        advertisementType = AdvertisementType.ADVERTISEMENT_PEER_READY_TO_PROVIDE_BLUETOOTH_MAC_ADDRESS;
                    } else if (BlePeerDiscoveryUtils.uuidsWithoutRequestIdMatch(parsedAdvertisement.uuid, mServiceUuid)) {
                        // Not rotated
                        advertisementType = AdvertisementType.ADVERTISEMENT_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST;
                    }
                } else if (mOurRequestId != null
                        && parsedAdvertisement.provideBluetoothMacAddressRequestId.equals(mOurRequestId)) {
                    advertisementType = AdvertisementType.ADVERTISEMENT_PEER_PROVIDING_OUR_BLUETOOTH_MAC_ADDRESS;
                }
            } else {
                // Possibly, but not guaranteed to be, an ad containing the peer properties
                advertisementType = AdvertisementType.ADVERTISEMENT_PEER_PROPERTIES;
            }
        }

        return advertisementType;
    }
}
