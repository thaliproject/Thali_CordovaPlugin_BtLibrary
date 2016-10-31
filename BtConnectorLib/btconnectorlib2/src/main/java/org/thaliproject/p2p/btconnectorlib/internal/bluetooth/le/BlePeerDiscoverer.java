/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.CountDownTimer;
import android.os.ParcelUuid;
import android.util.Log;

import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.utils.ThreadUtils;

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
         *
         * @param state The new state.
         */
        void onBlePeerDiscovererStateChanged(EnumSet<BlePeerDiscovererStateSet> state);

        /**
         * Called when a peer was discovered.
         *
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);


        // BRO MODE INTERFACES:

        /**
         * Called when we receive a request from a peer to provide it its Bluetooth MAC address.
         * <p>
         * Part of Bro Mode.
         *
         * @param requestId The request ID associated with the device in need of assistance.
         */
        void onProvideBluetoothMacAddressRequest(String requestId);

        /**
         * Called when we see that a peer is willing to provide us our own Bluetooth MAC address
         * via Bluetooth device discovery. After receiving this event, we should make our device
         * discoverable via Bluetooth.
         * <p>
         * Part of Bro Mode.
         *
         * @param requestId The request ID.
         */
        void onPeerReadyToProvideBluetoothMacAddress(String requestId);

        /**
         * Called when we are done broadcasting our willingness to help the peer provide its
         * Bluetooth MAC address or done broadcasting the discovered Bluetooth MAC address.
         * <p>
         * Part of Bro Mode.
         * <p>
         * Note: The operation is never considered to be completed, since this method is called
         * when the advertising timeouts.
         *
         * @param requestId    The request ID associated with the device in need of assistance.
         * @param wasCompleted True, if the operation was completed.
         */
        void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted);

        /**
         * Called when we receive our own Bluetooth MAC address.
         * <p>
         * Part of Bro Mode.
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
        ADVERTISING, // Advertising our presence
        ADVERTISING_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST, // Bro Mode - When we need help to find out our own Bluetooth MAC address
        ADVERTISING_PROVIDING_ASSISTANCE // Bro Mode - When helping a peer with its Bluetooth MAC address
    }

    /**
     * The type of the advertisement data advertised by the Bluetooth LE advertiser.
     * <p>
     * MANUFACTURER_DATA: Will advertise using manufacturer data and will parse only manufacturer
     * data based advertisements.
     * <p>
     * SERVICE_DATA: Will advertise using service data and will scan (using a filter) and parse only
     * service data based advertisements.
     * <p>
     * DO_NOT_CARE: Will advertise primarily using service data, but in case that fails, will
     * fallback to using manufacturer data. Will parse both manufacturer and service
     * data based advertisements.
     */
    public enum AdvertisementDataType {
        MANUFACTURER_DATA,
        SERVICE_DATA,
        DO_NOT_CARE
    }

    protected enum AdvertisementType {
        ADVERTISEMENT_UNKNOWN,
        ADVERTISEMENT_PEER_PROPERTIES, // Peer advertising its presence
        ADVERTISEMENT_PROVIDE_BLUETOOTH_MAC_ADDRESS_REQUEST, // Peer requesting assistance
        ADVERTISEMENT_PEER_READY_TO_PROVIDE_BLUETOOTH_MAC_ADDRESS, // Peer is ready to provide assistance to us
        ADVERTISEMENT_PEER_PROVIDING_OUR_BLUETOOTH_MAC_ADDRESS // Peer has resolved our Bluetooth MAC address
    }

    private static final String TAG = BlePeerDiscoverer.class.getName();
    private static final int UUID_BYTE_INDEX_TO_ROTATE_FOR_PEER_READY_TO_PROVIDE_AD = 9;
    private final BlePeerDiscoveryListener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private final UUID mServiceUuid;
    private final UUID mProvideBluetoothMacAddressRequestUuid;
    private final BleAdvertiser mBleAdvertiser;
    private final BleScanner mBleScanner;
    private AdvertisementDataType mAdvertisementDataType = DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE;
    private String mMyBluetoothMacAddress = null;
    private EnumSet<BlePeerDiscovererStateSet> mStateSet = EnumSet.of(BlePeerDiscovererStateSet.NOT_STARTED);
    private String mOurRequestId = null;
    private CountDownTimer mPeerAddressHelperAdvertisementTimeoutTimer = null;
    private int mManufacturerId = DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID;
    private int mBeaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
    private int mBeaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
    private boolean mIsAssistingPeer = false;
    private boolean mWasAdvertiserStartedBeforeStartingToAssistPeer = false;
    private boolean mAdvertiserFailedToStartUsingServiceData = false;

    /**
     * See PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid
     * <p>
     * Part of Bro Mode.
     */
    public static UUID generateNewProvideBluetoothMacAddressRequestUuid(UUID serviceUuid) {
        return PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid(serviceUuid);
    }

    /**
     * Constructor.
     *
     * @param listener                              The listener.
     * @param bluetoothAdapter                      The Bluetooth adapter.
     * @param serviceUuid                           The BLE service UUID.
     * @param provideBluetoothMacAddressRequestUuid UUID for "Provide Bluetooth MAC address" mode.
     * @param myBluetoothMacAddress                 Our Bluetooth MAC address for advertisement.
     * @param manufacturerId                        The manufacturer ID.
     * @param beaconAdLengthAndType                 The beacon ad length and type (comes after the manufacturer ID).
     * @param beaconAdExtraInformation              The optional extra information for beacon data (unsigned 8-bit integer).
     * @param advertisementDataType                 The advertisement data type.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceUuid, UUID provideBluetoothMacAddressRequestUuid,
            String myBluetoothMacAddress,
            int manufacturerId, int beaconAdLengthAndType, int beaconAdExtraInformation,
            AdvertisementDataType advertisementDataType) {

        this(listener, bluetoothAdapter, serviceUuid, provideBluetoothMacAddressRequestUuid,
                myBluetoothMacAddress, manufacturerId, beaconAdLengthAndType, beaconAdExtraInformation,
                advertisementDataType, null, null);
    }

    /**
     * Constructor.
     * The constructor used for testing purposes allowing to use mocked bleAdvertiser and bleScanner.
     *
     * @param listener                              The listener.
     * @param bluetoothAdapter                      The Bluetooth adapter.
     * @param serviceUuid                           The BLE service UUID.
     * @param provideBluetoothMacAddressRequestUuid UUID for "Provide Bluetooth MAC address" mode.
     * @param myBluetoothMacAddress                 Our Bluetooth MAC address for advertisement.
     * @param manufacturerId                        The manufacturer ID.
     * @param beaconAdLengthAndType                 The beacon ad length and type (comes after the manufacturer ID).
     * @param beaconAdExtraInformation              The optional extra information for beacon data (unsigned 8-bit integer).
     * @param bleAdvertiser                         The instance of the general BLE advertiser.
     * @param bleScanner                            The instance of the general BLE scanner.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceUuid, UUID provideBluetoothMacAddressRequestUuid,
            String myBluetoothMacAddress,
            int manufacturerId, int beaconAdLengthAndType, int beaconAdExtraInformation,
            AdvertisementDataType advertisementDataType,
            BleAdvertiser bleAdvertiser, BleScanner bleScanner) {
        mListener = listener;

        if (mListener == null) {
            throw new NullPointerException("BlePeerDiscoveryListener cannot be null");
        }

        mBluetoothAdapter = bluetoothAdapter;
        mServiceUuid = serviceUuid;
        mProvideBluetoothMacAddressRequestUuid = provideBluetoothMacAddressRequestUuid;
        mMyBluetoothMacAddress = myBluetoothMacAddress;

        mBleAdvertiser = bleAdvertiser != null ? bleAdvertiser : new BleAdvertiser(this, mBluetoothAdapter);

        if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
            // Request UUID and ID are only needed in case we don't know our own Bluetooth MAC
            // address and need to request it from a peer
            mOurRequestId = PeerAdvertisementFactory.parseRequestIdFromUuid(mProvideBluetoothMacAddressRequestUuid);
            Log.i(TAG, "BlePeerDiscoverer: Provide Bluetooth MAC address request ID is " + mOurRequestId);
        }

        mBleScanner = bleScanner != null ? bleScanner : new BleScanner(this, mBluetoothAdapter);

        mManufacturerId = manufacturerId;
        mBeaconAdLengthAndType = beaconAdLengthAndType;
        mBeaconAdExtraInformation = beaconAdExtraInformation;
        mAdvertisementDataType = advertisementDataType;
    }

    /**
     * @return The current state of this instance.
     */
    public EnumSet<BlePeerDiscovererStateSet> getState() {
        return mStateSet;
    }

    /**
     * Part of Bro Mode.
     *
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
     *
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
     *
     * @param manufacturerId                The manufacturer ID.
     * @param beaconAdLengthAndType         The beacon ad length and type (comes after the manufacturer ID).
     * @param beaconAdExtraInformation      The optional extra information for beacon data (unsigned 8-bit integer).
     * @param advertisementDataType         The advertisement data type.
     * @param advertiseMode                 The advertise mode for the BLE advertiser.
     * @param advertiseTxPowerLevel         The advertise TX power level for the BLE advertiser.
     * @param scanMode                      The scan mode for the BLE scanner.
     * @param scanReportDelayInMilliseconds The new scan report delay in milliseconds.
     * @return True, if all the settings were applied successfully. False, if at least one of
     * settings failed to be applied.
     */
    public boolean applySettings(
            int manufacturerId, int beaconAdLengthAndType, int beaconAdExtraInformation,
            AdvertisementDataType advertisementDataType, int advertiseMode,
            int advertiseTxPowerLevel, int scanMode, long scanReportDelayInMilliseconds) {

        return this.applySettings(manufacturerId, beaconAdLengthAndType,
                beaconAdExtraInformation, advertisementDataType, advertiseMode,
                advertiseTxPowerLevel, scanMode, scanReportDelayInMilliseconds,
                new AdvertiseSettings.Builder(), new ScanSettings.Builder());
    }

    /**
     * Sets the settings for both the BLE advertiser and the scanner.
     *
     * @param manufacturerId                The manufacturer ID.
     * @param beaconAdLengthAndType         The beacon ad length and type (comes after the manufacturer ID).
     * @param beaconAdExtraInformation      The optional extra information for beacon data (unsigned 8-bit integer).
     * @param advertisementDataType         The advertisement data type.
     * @param advertiseMode                 The advertise mode for the BLE advertiser.
     * @param advertiseTxPowerLevel         The advertise TX power level for the BLE advertiser.
     * @param scanMode                      The scan mode for the BLE scanner.
     * @param scanReportDelayInMilliseconds The new scan report delay in milliseconds.
     * @param advertiseSettingsBuilder      The Builder for AdvertiseSettings.
     * @param scanSettingsBuilder           The Builder for ScanSettings.
     * @return True, if all the settings were applied successfully. False, if at least one of
     * settings failed to be applied.
     */
    public boolean applySettings(
            int manufacturerId, int beaconAdLengthAndType, int beaconAdExtraInformation,
            AdvertisementDataType advertisementDataType, int advertiseMode,
            int advertiseTxPowerLevel, int scanMode, long scanReportDelayInMilliseconds,
            AdvertiseSettings.Builder advertiseSettingsBuilder, ScanSettings.Builder scanSettingsBuilder) {
        Log.i(TAG, "applySettings:"
                + "\n    - Manufacturer ID: " + manufacturerId
                + "\n    - Beacon ad length and type: " + beaconAdLengthAndType
                + "\n    - Beacon ad extra information: " + beaconAdExtraInformation
                + "\n    - Advertisement data type: " + advertisementDataType
                + "\n    - Advertise mode: " + advertiseMode
                + "\n    - Advertise TX power level: " + advertiseTxPowerLevel
                + "\n    - Scan mode: " + scanMode
                + "\n    - Scan report delay in milliseconds: " + scanReportDelayInMilliseconds
                + "\n    - " + ThreadUtils.currentThreadToString());

        boolean advertiserSettingsWereSet = false;

        // The advertise data will be automatically updated when the advertiser is started/restarted
        // The scanner filter will be automatically updated when the scanner is started/restarted
        mManufacturerId = manufacturerId;
        mBeaconAdLengthAndType = beaconAdLengthAndType;
        mBeaconAdExtraInformation = beaconAdExtraInformation;
        mAdvertisementDataType = advertisementDataType;

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
            Log.d(TAG, "applySettings: advertiserWasStarted " + advertiserWasStarted + " " + ThreadUtils.currentThreadToString());
            if (advertiserWasStarted) {
                Log.d(TAG, "applySettings: stop advertiser "  + ThreadUtils.currentThreadToString());
                mBleAdvertiser.stop(false);
            }
            mBleAdvertiser.setAdvertiseSettings(advertiseSettingsBuilder.build());
            if (advertiserWasStarted) {
                Log.d(TAG, "applySettings: start advertiser "  + ThreadUtils.currentThreadToString());
                boolean started = startAdvertiser();
                Log.d(TAG, "applySettings: start advertiser started = " + started + " "  + ThreadUtils.currentThreadToString());
            }
        }

        boolean scannerSettingsWereSet = false;

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
            Log.d(TAG, "applySettings: scannerWasStarted " + scannerWasStarted + " " + ThreadUtils.currentThreadToString());
            if (scannerWasStarted) {
                Log.d(TAG, "applySettings: stop scanner "  + ThreadUtils.currentThreadToString());
                mBleScanner.stop(false);
            }
            mBleScanner.setScanSettings(scanSettingsBuilder.build());
            if (scannerWasStarted) {
                Log.d(TAG, "applySettings: start scanner "  + ThreadUtils.currentThreadToString());
                boolean started = startScanner();
                Log.d(TAG, "applySettings: start scanner started = " + started + " "  + ThreadUtils.currentThreadToString());
            }
        }

        return (advertiserSettingsWereSet && scannerSettingsWereSet);
    }

    /**
     * Starts the BLE scanner. Adds the appropriate filter for the scanner, if the scanner was not
     * already running.
     *
     * @return True, if starting or already started. False otherwise.
     */
    public synchronized boolean startScanner() {
        Log.d(TAG, "startScanner: " + ThreadUtils.currentThreadToString());
        if (!mBleScanner.isStarted()) {
            Log.i(TAG, "startScanner: Starting...");

            mBleScanner.clearScanFilters();
            mBleScanner.addScanFilter(createScanFilter());
        }

        return mBleScanner.start();
    }

    /**
     * Stops the BLE scanner.
     */
    public synchronized void stopScanner() {
        Log.d(TAG, "stopScanner: " + ThreadUtils.currentThreadToString());
        if (mBleScanner.isStarted()) {
            Log.i(TAG, "stopScanner: Stopping...");
        }

        mBleScanner.stop(true);
    }

    /**
     * Starts the BLE advertiser.
     *
     * @return True, if starting or already started. False otherwise.
     */
    public synchronized boolean startAdvertiser() {
        Log.d(TAG, "startAdvertiser: " + ThreadUtils.currentThreadToString());
        stopPeerAddressHelperAdvertiser(); // Need to stop, if running, since we can have only one advertiser
        if (!mBleAdvertiser.isStarted()) {
            Log.i(TAG, "startAdvertiser: Starting...");
        }

        AdvertiseData advertiseData = null;

        if (BluetoothUtils.isBluetoothMacAddressUnknown(mMyBluetoothMacAddress)) {
            // We do not know our own Bluetooth MAC address, do request it (Bro mode)
            advertiseData = createAdvertiseData(
                    mProvideBluetoothMacAddressRequestUuid, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN);
        } else {
            advertiseData = createAdvertiseData(mServiceUuid, mMyBluetoothMacAddress);
            mOurRequestId = null;
        }
        boolean start;
        synchronized (mBleAdvertiser) {
            mBleAdvertiser.setAdvertiseData(advertiseData);
            start = mBleAdvertiser.start();
        }
        Log.d(TAG, "startAdvertiser started = " + start + " " + ThreadUtils.currentThreadToString());
        return start;
    }

    /**
     * Stops the BLE advertiser.
     */
    public synchronized void stopAdvertiser() {
        Log.d(TAG, "stopAdvertiser:" + ThreadUtils.currentThreadToString());
        if (mBleAdvertiser.isStarted()) {
            Log.i(TAG, "stopAdvertiser: Stopping...");
        }

        mBleAdvertiser.stop(true);
    }

    /**
     * Starts the BLE peer discovery.
     *
     * @return True, if starting or already started. False, if failed to start.
     */
    public synchronized boolean startScannerAndAdvertiser() {
        Log.d(TAG, "startScannerAndAdvertiser : " + ThreadUtils.currentThreadToString());
        boolean advertiserStarted = startAdvertiser();
        Log.d(TAG, "startScannerAndAdvertiser: advertiser is started. " + ThreadUtils.currentThreadToString());
        boolean scannerStarted = startScanner();
        Log.i(TAG, "startScannerAndAdvertiser: adv = " + advertiserStarted + ", disc = " + scannerStarted);
        return (advertiserStarted && scannerStarted);
    }

    /**
     * Stops the BLE peer discovery.
     */
    public synchronized void stopScannerAndAdvertiser() {
        Log.d(TAG, "stopScannerAndAdvertiser: " + ThreadUtils.currentThreadToString());
        stopPeerAddressHelperAdvertiser();
        stopScanner();
        stopAdvertiser();
    }

    /**
     * Starts advertising the given Bluetooth MAC address with the given UUID via BLE for a certain
     * period of time.
     * <p>
     * Part of Bro Mode.
     *
     * @param requestId              The request ID.
     * @param bluetoothMacAddress    A Bluetooth MAC address of a discovered device.
     *                               Use PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN to notify the
     *                               peer of our willingness to help (so that it will make itself
     *                               discoverable).
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
     * <p>
     * Part of Bro Mode.
     */
    private synchronized void stopPeerAddressHelperAdvertiser() {
        Log.d(TAG, "stopPeerAddressHelperAdvertiser " + ThreadUtils.currentThreadToString());
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

    /**
     * Called when the Bluetooth LE advertiser fails to start.
     * <p>
     * If the error code was ADVERTISE_FAILED_DATA_TOO_LARGE and the advertisement data type is
     * DO_NOT_CARE and this is the first time we got the error (using service data), we will try
     * fallback to using manufacturer data instead.
     *
     * @param errorCode The error code.
     */
    @Override
    public void onAdvertiserFailedToStart(int errorCode) {
        Log.e(TAG, "onAdvertiserFailedToStart: " + errorCode);
        // No need to update state here, since onIsAdvertiserStartedChanged will be called

        if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
                && mAdvertisementDataType == AdvertisementDataType.DO_NOT_CARE) {
            if (!mAdvertiserFailedToStartUsingServiceData) {
                Log.i(TAG, "onAdvertiserFailedToStart: Falling back to using manufacturer data - restarting...");
                mAdvertiserFailedToStartUsingServiceData = true;
                startAdvertiser();
            } else {
                Log.e(TAG, "onAdvertiserFailedToStart: Manufacturer data fallback did not work either");
            }
        } else {
            // Just restart. We can change it when found out how to detect difference
            // between user's disabling and crash
            // issue https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/85
            Log.e(TAG, "onAdvertiserFailedToStart: Just restart advertiser");
            startAdvertiser();
        }
    }

    @Override
    public void onIsAdvertiserStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsAdvertiserStartedChanged: " + isStarted + ThreadUtils.currentThreadToString());
        updateState();
    }

    @Override
    public void onScannerFailed(int errorCode) {
        Log.e(TAG, "onScannerFailed: " + errorCode);
        Log.e(TAG, "onScannerFailed: scanner is started =  " + mBleScanner.isStarted());
        // No need to update state here, since onIsScannerStartedChanged will be called
    }

    @Override
    public void onIsScannerStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsScannerStartedChanged: " + isStarted + ThreadUtils.currentThreadToString());
        updateState();
    }

    @Override
    public void onScanResult(ScanResult result) {
        checkScanResult(result);
    }

    /**
     * Tries to parse the given result and take action based on the advertisement type.
     *
     * @param scanResult The scan result.
     */
    private synchronized void checkScanResult(ScanResult scanResult) {
        BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement = null;
        if (scanResult != null && scanResult.getScanRecord() != null) {
            if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA
                    || mAdvertisementDataType == AdvertisementDataType.DO_NOT_CARE) {
                // Try to parse the service data
                Map<ParcelUuid, byte[]> serviceData = scanResult.getScanRecord().getServiceData();

                if (serviceData != null && serviceData.size() > 0) {
                    for (ParcelUuid uuid : serviceData.keySet()) {
                        byte[] serviceDataContent = serviceData.get(uuid);
                        parsedAdvertisement = BlePeerDiscoveryUtils.parseServiceData(serviceDataContent);
                        if (parsedAdvertisement != null) {
                            UUID scannedServiceUuid = scanResult.getScanRecord().getServiceUuids() != null ?
                                    scanResult.getScanRecord().getServiceUuids().get(0).getUuid() : null;
                            if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA
                                    || BlePeerDiscoveryUtils.uuidStartsWithExpectedServiceUuid(scannedServiceUuid, mServiceUuid)) {
                                parsedAdvertisement.uuid = scannedServiceUuid;
                                parsedAdvertisement.provideBluetoothMacAddressRequestId =
                                        BlePeerDiscoveryUtils.checkIfUuidContainsProvideBluetoothMacAddressRequestId(
                                                parsedAdvertisement.uuid, mServiceUuid);
                                break;
                            } else {
                                // UUID mismatch
                                Log.d(TAG, "checkScanResult: parsedAdvertisement uuid mismatch");
                                parsedAdvertisement = null;
                            }
                        } else {
                            Log.d(TAG, "checkScanResult: parsedAdvertisement : null");
                        }
                    }
                }
            }

            if (mAdvertisementDataType == AdvertisementDataType.MANUFACTURER_DATA
                    || (mAdvertisementDataType == AdvertisementDataType.DO_NOT_CARE && parsedAdvertisement == null)) {
                // Try to parse the manufacturer data
                byte[] manufacturerData = scanResult.getScanRecord().getManufacturerSpecificData(mManufacturerId);

                if (manufacturerData != null) {
                    parsedAdvertisement = BlePeerDiscoveryUtils.parseManufacturerData(manufacturerData, mServiceUuid);
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
                        Log.d(TAG, "checkScanResult: Will try to provide a device its Bluetooth MAC address");
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
        Log.d(TAG, "updateState : " + ThreadUtils.currentThreadToString());
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
                deducedStateSet.add(BlePeerDiscovererStateSet.ADVERTISING);
            }
        }

        if (deducedStateSet.isEmpty()) {
            deducedStateSet.add(BlePeerDiscovererStateSet.NOT_STARTED);
        }

        Log.d(TAG, "updateState: deducedStateSet: " + deducedStateSet);
        Log.d(TAG, "updateState: stateSet: " + mStateSet);
        Log.d(TAG, ThreadUtils.currentThreadToString());
        if (!mStateSet.equals(deducedStateSet)) {
            Log.d(TAG, "updateState: State changed from " + mStateSet + " to " + deducedStateSet +
                    ThreadUtils.currentThreadToString());
            mStateSet = deducedStateSet;
            mListener.onBlePeerDiscovererStateChanged(mStateSet);
        }
        Log.d(TAG, "update state finished. " + ThreadUtils.currentThreadToString());
    }

    /**
     * Creates the advertise data based on the current advertise data type and the given properties.
     *
     * @param uuid                The UUID.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     * @return A newly created AdvertiseData instance.
     */
    private AdvertiseData createAdvertiseData(UUID uuid, String bluetoothMacAddress) {
        AdvertiseData advertiseData;
        if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA
                || (mAdvertisementDataType == AdvertisementDataType.DO_NOT_CARE
                && !mAdvertiserFailedToStartUsingServiceData)) {
            Log.d(TAG, "createAdvertiseData: createAdvertiseDataToServiceData ");
            advertiseData = PeerAdvertisementFactory.createAdvertiseDataToServiceData(
                    uuid, mBeaconAdExtraInformation, bluetoothMacAddress);
        } else {
            Log.d(TAG, "createAdvertiseData: createAdvertiseDataToManufacturerData ");
            // MANUFACTURER_DATA or DO_NOT_CARE with failure trying to use service data
            advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(
                    mManufacturerId, mBeaconAdLengthAndType, uuid, mBeaconAdExtraInformation, bluetoothMacAddress);
        }
        Log.i(TAG, "createAdvertiseData: created " + advertiseData.toString());
        return advertiseData;
    }

    /**
     * Creates the scan filter based on the set advertisement data type.
     *
     * @return A newly created scan filter.
     */
    private ScanFilter createScanFilter() {
        ScanFilter scanFilter = null;

        if (mAdvertisementDataType == AdvertisementDataType.SERVICE_DATA) {
            scanFilter = BlePeerDiscoveryUtils.createScanFilter(mServiceUuid, 0, false);
        } else {
            // Either MANUFACTURER_DATA or DO_NOT_CARE
            scanFilter = BlePeerDiscoveryUtils.createScanFilter(null, mManufacturerId, true);
        }

        return scanFilter;
    }

    /**
     * Resolves the type of the parsed advertisement.
     *
     * @param parsedAdvertisement The parsed advertisement.
     * @return The advertisement type. Will return AdvertisementType.ADVERTISEMENT_UNKNOWN, if the
     * type is not recognized (and should be ignored).
     */
    private AdvertisementType resolveAdvertisementType(
            BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement) {
        AdvertisementType advertisementType = AdvertisementType.ADVERTISEMENT_UNKNOWN;

        if (parsedAdvertisement != null) {
            if (parsedAdvertisement.provideBluetoothMacAddressRequestId != null) {
                if (BluetoothUtils.isBluetoothMacAddressUnknown(parsedAdvertisement.bluetoothMacAddress)) {
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
