/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;

import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.BluetoothMacAddressResolutionHelper;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer.BlePeerDiscovererStateSet;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer.WifiPeerDiscovererStateSet;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;
import org.thaliproject.p2p.btconnectorlib.utils.PeerModel;
import org.thaliproject.p2p.btconnectorlib.utils.ThreadUtils;

import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.UUID;

/**
 * The main interface for managing peer discovery.
 */
public class DiscoveryManager
        extends AbstractBluetoothConnectivityAgent
        implements
        WifiDirectManager.WifiStateListener,
        WifiPeerDiscoverer.WifiPeerDiscoveryListener,
        BlePeerDiscoverer.BlePeerDiscoveryListener,
        BluetoothMacAddressResolutionHelper.BluetoothMacAddressResolutionHelperListener,
        PeerModel.Listener,
        DiscoveryManagerSettings.Listener {

    public enum DiscoveryManagerState {
        NOT_STARTED,
        WAITING_FOR_SERVICES_TO_BE_ENABLED, // When the chosen peer discovery method is disabled and waiting for it to be enabled to start
        WAITING_FOR_BLUETOOTH_MAC_ADDRESS, // When we don't know our own Bluetooth MAC address
        PROVIDING_BLUETOOTH_MAC_ADDRESS, // When helping a peer by providing it its Bluetooth MAC address
        RUNNING_BLE,
        RUNNING_WIFI,
        RUNNING_BLE_AND_WIFI
    }

    public enum DiscoveryMode {
        BLE,
        WIFI,
        BLE_AND_WIFI
    }

    public interface DiscoveryManagerListener {
        /**
         * Called when a permission check for a certain functionality is needed. The activity
         * utilizing this class then needs to perform the check and return true, if allowed.
         * <p>
         * Note: The permission check is only needed if we are running on Marshmallow
         * (Android version 6.x) or higher.
         *
         * @param permission The permission to check.
         * @return True, if permission is granted. False, if not.
         */
        boolean onPermissionCheckRequired(String permission);

        /**
         * Called when the state of this instance is changed.
         *
         * @param state         The new state.
         * @param isDiscovering True, if peer discovery is active. False otherwise.
         * @param isAdvertising True, if advertising is active. False otherwise.
         */
        void onDiscoveryManagerStateChanged(
                DiscoveryManagerState state, boolean isDiscovering, boolean isAdvertising);

        /**
         * Called when a new peer is discovered.
         *
         * @param peerProperties The properties of the new peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);

        /**
         * Called when a peer data is updated (missing data is added). This callback is never
         * called, if data is lost.
         *
         * @param peerProperties The updated properties of a discovered peer.
         */
        void onPeerUpdated(PeerProperties peerProperties);

        /**
         * Called when an existing peer is lost (i.e. not available anymore).
         *
         * @param peerProperties The properties of the lost peer.
         */
        void onPeerLost(PeerProperties peerProperties);

        // Bro Mode callbacks ->

        /**
         * Called when we discovery a device that needs to find out its own Bluetooth MAC address.
         * <p>
         * Part of Bro Mode.
         * <p>
         * Note: If the Bluetooth MAC address resolution process is set to be automated, this
         * callback will not be called.
         *
         * @param requestId The request ID associated with the device.
         */
        void onProvideBluetoothMacAddressRequest(String requestId);

        /**
         * Called when we see that a peer is willing to provide us our own Bluetooth MAC address
         * via Bluetooth device discovery. After receiving this event, we should make our device
         * discoverable via Bluetooth.
         * <p>
         * Part of Bro Mode.
         * <p>
         * Note: If the Bluetooth MAC address resolution process is set to be automated, this
         * callback will not be called.
         */
        void onPeerReadyToProvideBluetoothMacAddress();

        /**
         * Called when the Bluetooth MAC address of this device is resolved.
         * <p>
         * Part of Bro Mode.
         *
         * @param bluetoothMacAddress The Bluetooth MAC address.
         */
        void onBluetoothMacAddressResolved(String bluetoothMacAddress);
    }

    /**
     * Helper class for checking features support
     */
    private abstract class FeatureSupportChecker {

        protected abstract String getFeatureName();

        public boolean isSupported() {
            BluetoothManager.FeatureSupportedStatus featureSupportedStatus = isFeatureSupported();
            if (featureSupportedStatus == BluetoothManager.FeatureSupportedStatus.NOT_RESOLVED
                    && isBleSupported()) {
                Log.w(TAG, "Bluetooth is not enabled so we could not check whether or not Bluetooth "
                        + "LE " + getFeatureName() + " is supported. However, Bluetooth LE is supported "
                        + "so we just *assume* this feature is supported too (which may not "
                        + "always be the case).");
                return true;
            }
            return featureSupportedStatus == BluetoothManager.FeatureSupportedStatus.SUPPORTED;
        }

        protected abstract BluetoothManager.FeatureSupportedStatus isFeatureSupported();
    }

    private class MultipleAdvertisementSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "multiple advertisement";
        }

        @Override
        protected BluetoothManager.FeatureSupportedStatus isFeatureSupported() {
            return mBluetoothManager.isBleMultipleAdvertisementSupported();
        }
    }

    private class ScanBatchingSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "offloaded scan batching";
        }

        @Override
        protected BluetoothManager.FeatureSupportedStatus isFeatureSupported() {
            return mBluetoothManager.isBleOffloadedScanBatchingSupported();
        }
    }

    private class FilteringSupportChecker extends FeatureSupportChecker {

        @Override
        protected String getFeatureName() {
            return "offloaded filtering";
        }

        @Override
        protected BluetoothManager.FeatureSupportedStatus isFeatureSupported() {
            return mBluetoothManager.isBleOffloadedFilteringSupported();
        }
    }

    private static final String TAG = DiscoveryManager.class.getName();

    private final DiscoveryManagerListener mListener;
    private final UUID mBleServiceUuid;
    private final String mServiceType;
    private final Handler mHandler;
    private WifiDirectManager mWifiDirectManager = null;
    private BlePeerDiscoverer mBlePeerDiscoverer = null;
    private WifiPeerDiscoverer mWifiPeerDiscoverer = null;
    private DiscoveryManagerState mState = DiscoveryManagerState.NOT_STARTED;
    private DiscoveryManagerSettings mSettings = null;
    private EnumSet<WifiPeerDiscovererStateSet> mWifiPeerDiscovererStateSet = EnumSet.of(WifiPeerDiscovererStateSet.NOT_STARTED);
    private EnumSet<BlePeerDiscovererStateSet> mBlePeerDiscovererStateSet = EnumSet.of(BlePeerDiscovererStateSet.NOT_STARTED);
    private PeerModel mPeerModel = null;
    private BluetoothMacAddressResolutionHelper mBluetoothMacAddressResolutionHelper = null;
    private String mMissingPermission = null;
    private long mLastTimeDeviceWasMadeDiscoverable = 0;
    private int mPreviousDeviceDiscoverableTimeInSeconds = 0;
    private boolean mShouldBeScanning = false;
    private boolean mShouldBeAdvertising = false;
    private boolean mIsDiscovering = false;
    private boolean mIsAdvertising = false;

    /**
     * Constructor.
     *
     * @param context        The application context.
     * @param listener       The listener.
     * @param bleServiceUuid Our BLE service UUID (both ours and requirement for the peer).
     *                       Required by BLE based peer discovery only.
     * @param serviceType    The service type (both ours and requirement for the peer).
     *                       Required by Wi-Fi Direct based peer discovery only.
     */
    public DiscoveryManager(Context context, DiscoveryManagerListener listener, UUID bleServiceUuid, String serviceType) {
        super(context); // Gets the BluetoothManager instance

        mListener = listener;
        mBleServiceUuid = bleServiceUuid;
        mServiceType = serviceType;

        mBluetoothMacAddressResolutionHelper = new BluetoothMacAddressResolutionHelper(
                context, mBluetoothManager.getBluetoothAdapter(), this,
                mBleServiceUuid, BlePeerDiscoverer.generateNewProvideBluetoothMacAddressRequestUuid(mBleServiceUuid));

        setupManager(context, null);

        mHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * Constructor.
     *
     * @param context          The application context.
     * @param listener         The listener.
     * @param bleServiceUuid   Our BLE service UUID (both ours and requirement for the peer).
     *                         Required by BLE based peer discovery only.
     * @param serviceType      The service type (both ours and requirement for the peer).
     *                         Required by Wi-Fi Direct based peer discovery only.
     * @param bluetoothManager The bluetooth manager
     */
    public DiscoveryManager(Context context, DiscoveryManagerListener listener, UUID bleServiceUuid,
                            String serviceType, BluetoothManager bluetoothManager, SharedPreferences preferences) {
        super(context, bluetoothManager); // Gets the BluetoothManager instance

        mListener = listener;
        mBleServiceUuid = bleServiceUuid;
        mServiceType = serviceType;

        mBluetoothMacAddressResolutionHelper = new BluetoothMacAddressResolutionHelper(
                context, mBluetoothManager.getBluetoothAdapter(), this,
                mBleServiceUuid, BlePeerDiscoverer.generateNewProvideBluetoothMacAddressRequestUuid(mBleServiceUuid),
                preferences);


        setupManager(context, preferences);

        mHandler = new Handler(mContext.getMainLooper());
    }

    private void setupManager(Context context, SharedPreferences preferences) {

        if (preferences == null) {
            mSettings = DiscoveryManagerSettings.getInstance(context);

        } else {
            mSettings = DiscoveryManagerSettings.getInstance(context, preferences);
        }

        mSettings.load();
        mSettings.addListener(this);

        mPeerModel = new PeerModel(this, mSettings);
        mWifiDirectManager = WifiDirectManager.getInstance(mContext);
    }

    /**
     * @return True, if the multi advertisement is supported by the chipset. Note that if Bluetooth
     * is not enabled on the device (and we haven't resolved if supported before), this method will
     * only check whether or not Bluetooth LE is supported.
     */
    public boolean isBleMultipleAdvertisementSupported() {
        FeatureSupportChecker checker = new MultipleAdvertisementSupportChecker();
        return checker.isSupported();
    }

    /**
     * @return True, if the scan batching is supported by the chipset. Note that if Bluetooth
     * is not enabled on the device (and we haven't resolved if supported before), this method will
     * only check whether or not Bluetooth LE is supported.
     */
    public boolean isBleOffloadedScanBatchingSupported() {
        FeatureSupportChecker checker = new ScanBatchingSupportChecker();
        return checker.isSupported();
    }

    /**
     * @return True, if the filtering is supported by the chipset. Note that if Bluetooth
     * is not enabled on the device (and we haven't resolved if supported before), this method will
     * only check whether or not Bluetooth LE is supported.
     */
    public boolean isBleOffloadedFilteringSupported() {
        FeatureSupportChecker checker = new FilteringSupportChecker();
        return checker.isSupported();
    }

    /**
     * @return True, if BLE is supported. False otherwise.
     */
    public boolean isBleSupported() {
        return mBluetoothManager.isBleSupported();
    }

    /**
     * @return True, if Wi-Fi Direct is supported. False otherwise.
     */
    public boolean isWifiDirectSupported() {
        return mWifiDirectManager.isWifiDirectSupported();
    }

    /**
     * @return The current state.
     */
    public DiscoveryManagerState getState() {
        return mState;
    }

    /**
     * Checks the current state and returns true, if running regardless of the discovery mode in use.
     *
     * @return True, if running regardless of the mode. False otherwise.
     */
    public boolean isRunning() {
        return (mState == DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS
                || mState == DiscoveryManagerState.PROVIDING_BLUETOOTH_MAC_ADDRESS
                || mState == DiscoveryManagerState.RUNNING_BLE
                || mState == DiscoveryManagerState.RUNNING_WIFI
                || mState == DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
    }

    /**
     * @return True, if peer discovery is active. False otherwise.
     */
    public boolean isDiscovering() {
        return (isBleDiscovering() || isWifiDiscovering());
    }

    /**
     * @return True, if ble peer discovery is active. False otherwise.
     */
    public boolean isBleDiscovering() {
        return (mBlePeerDiscoverer != null
                && mBlePeerDiscoverer.getState().contains(BlePeerDiscovererStateSet.SCANNING));
    }

    /**
     * @return True, if wifi peer discovery is active. False otherwise.
     */
    public boolean isWifiDiscovering() {
        return (mWifiPeerDiscoverer != null
                && mWifiPeerDiscoverer.getState().contains(WifiPeerDiscovererStateSet.SCANNING));
    }

    /**
     * @return True, if advertising is active. False otherwise.
     */
    public boolean isAdvertising() {
        return (isBleAdvertising() || isWifiAdvertising());
    }

    /**
     * @return True, if ble advertising is active. False otherwise.
     */
    public boolean isBleAdvertising() {
        return (mBlePeerDiscoverer != null
                && mBlePeerDiscoverer.getState().contains(BlePeerDiscovererStateSet.ADVERTISING));
    }

    /**
     * @return True, if wifi advertising is active. False otherwise.
     */
    public boolean isWifiAdvertising() {
        return (mWifiPeerDiscoverer != null
                && mWifiPeerDiscoverer.getState().contains(WifiPeerDiscovererStateSet.ADVERTISING));
    }

    /**
     * @return The Wi-Fi Direct manager instance.
     */
    public WifiDirectManager getWifiDirectManager() {
        return mWifiDirectManager;
    }

    /**
     * @return The peer model.
     */
    public PeerModel getPeerModel() {
        return mPeerModel;
    }

    /**
     * Returns the Bluetooth MAC address resolution helper. Note that the helper isn't meant to be
     * used directly. This getter is here strictly for testing purposes.
     *
     * @return The Bluetooth MAC address resolution helper.
     */
    public BluetoothMacAddressResolutionHelper getBluetoothMacAddressResolutionHelper() {
        return mBluetoothMacAddressResolutionHelper;
    }

    /**
     * Used to check, if some required permission has not been granted by the user.
     *
     * @return The name of the missing permission or null, if none.
     */
    public String getMissingPermission() {
        return mMissingPermission;
    }

    /**
     * Starts the peer discovery with current settings.
     *
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean start() {
        return start(mShouldBeScanning, mShouldBeAdvertising);
    }

    /**
     * Starts the peer discovery.
     *
     * @param startDiscovery   If true, will start the scanner/discovery.
     * @param startAdvertising If true, will start the advertiser.
     * @return True, if started successfully or was already running. False otherwise.
     */
    public synchronized boolean start(boolean startDiscovery, boolean startAdvertising) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();
        boolean started = false;

        Log.i(TAG, "start: Discovery mode: " + discoveryMode
                + ", start discovery: " + startDiscovery
                + ", start advertiser: " + startAdvertising);

        mShouldBeScanning = startDiscovery;
        mShouldBeAdvertising = startAdvertising;

        if (!mShouldBeScanning && !mShouldBeAdvertising) {
            if (mState != DiscoveryManagerState.NOT_STARTED) {
                stop();
            }
            return false;
        } else if (!mShouldBeScanning && isDiscovering()) {
            stopDiscovery();
        } else if (!mShouldBeAdvertising && isAdvertising()) {
            stopAdvertising();
        }


        mBluetoothManager.bind(this);
        mWifiDirectManager.bind(this);

        boolean bleDiscoveryStarted = false;
        boolean wifiDiscoveryStarted = false;

        boolean bluetoothEnabled = false;
        boolean wifiEnabled = false;

        if (discoveryMode == DiscoveryMode.BLE || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            bluetoothEnabled = mBluetoothManager.isBluetoothEnabled();
            if (bluetoothEnabled) {
                // Try to start BLE based discovery
                mBluetoothMacAddressResolutionHelper.stopBluetoothDeviceDiscovery();

                if (mBluetoothManager.isBleSupported()) {
                    bleDiscoveryStarted = startBlePeerDiscoverer();
                } else {
                    Log.e(TAG, "start: Cannot start BLE based peer discovery, because the device does not support Bluetooth LE");
                }
            } else {
                Log.e(TAG, "start: Cannot start BLE based peer discovery, because Bluetooth is not enabled on the device");
            }
        }

        if (discoveryMode == DiscoveryMode.WIFI || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            wifiEnabled = mWifiDirectManager.isWifiEnabled();
            if (wifiEnabled) {
                if (verifyIdentityString()) {
                    // Try to start Wi-Fi Direct based discovery
                    wifiDiscoveryStarted = startWifiPeerDiscovery();
                } else {
                    if (mMyIdentityString == null || mMyIdentityString.length() == 0) {
                        Log.e(TAG, "start: Identity string is null or empty");
                    } else {
                        Log.e(TAG, "start: Invalid identity string: " + mMyIdentityString);
                    }
                }
            } else {
                Log.e(TAG, "start: Cannot start Wi-Fi Direct based peer discovery, because Wi-Fi is not enabled on the device");
            }
        }

        if ((discoveryMode == DiscoveryMode.BLE_AND_WIFI && (bleDiscoveryStarted || wifiDiscoveryStarted))
                || (discoveryMode == DiscoveryMode.BLE && bleDiscoveryStarted)
                || (discoveryMode == DiscoveryMode.WIFI && wifiDiscoveryStarted)) {
            started = true;
            if (bleDiscoveryStarted) {
                if (BluetoothUtils.isBluetoothMacAddressUnknown(getBluetoothMacAddress())) {
                    Log.i(TAG, "start: Our Bluetooth MAC address is not known");
                    if (mBlePeerDiscoverer != null) {
                        mBluetoothMacAddressResolutionHelper.startBluetoothMacAddressGattServer(
                                mBlePeerDiscoverer.getProvideBluetoothMacAddressRequestId());
                    }
                }
            }
            Log.i(TAG, "start: OK");
        } else if ((discoveryMode == DiscoveryMode.BLE_AND_WIFI && !bluetoothEnabled && !wifiEnabled) ||
                (discoveryMode == DiscoveryMode.BLE && !bluetoothEnabled) ||
                (discoveryMode == DiscoveryMode.WIFI && !wifiEnabled)) {
            // required services not started - just waiting for enabling
            started = false;
        } else {
            // couldn't start - ensure to do the cleanup
            stop();
            started = false;
        }
        updateState();

        return started;
    }

    /**
     * Stops discovery.
     */
    public synchronized void stopDiscovery() {
        Log.i(TAG, "stopDiscovery");

        mShouldBeScanning = false;

        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.stopScanner();
        }

        if (mWifiPeerDiscoverer != null) {
            mWifiPeerDiscoverer.stopDiscoverer();
        }

        if (mState != DiscoveryManagerState.NOT_STARTED && !mShouldBeAdvertising) {
            stop();
        }
    }

    /**
     * Stops advertising.
     */
    public synchronized void stopAdvertising() {
        Log.i(TAG, "stopAdvertising");

        mShouldBeAdvertising = false;

        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.stopAdvertiser();
        }

        if (mWifiPeerDiscoverer != null) {
            mWifiPeerDiscoverer.stopAdvertiser();
        }

        if (mState != DiscoveryManagerState.NOT_STARTED && !mShouldBeScanning) {
            stop();
        }
    }

    /**
     * Stops the peer discovery.
     * Calling this method does nothing, if not running.
     */
    public synchronized void stop() {
        if (mState != DiscoveryManagerState.NOT_STARTED) {
            Log.i(TAG, "stop: Stopping peer discovery...");
        }

        mShouldBeScanning = false;
        mShouldBeAdvertising = false;

        stopForRestart();

        mWifiDirectManager.release(this);
        mBluetoothManager.release(this);

        mPeerModel.clear();

        updateState();
    }

    /**
     * Makes the device (Bluetooth) discoverable for the given duration.
     *
     * @param durationInSeconds The duration in seconds. 0 means the device is always discoverable.
     *                          Any value below 0 or above 3600 is automatically set to 120 secs.
     */
    public void makeDeviceDiscoverable(int durationInSeconds) {
        long currentTime = new Date().getTime();

        if (currentTime > mLastTimeDeviceWasMadeDiscoverable + mPreviousDeviceDiscoverableTimeInSeconds * 1000) {
            mLastTimeDeviceWasMadeDiscoverable = currentTime;
            mPreviousDeviceDiscoverableTimeInSeconds = durationInSeconds;

            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationInSeconds);
            mContext.startActivity(discoverableIntent);
        }
    }

    @Override
    public void dispose() {
        Log.i(TAG, "dispose");
        super.dispose();

        if (mState != DiscoveryManagerState.NOT_STARTED) {
            stop();
        }

        mSettings.removeListener(this);
    }

    /**
     * Constructs the BlePeerDiscoverer instance, if one does not already exist.
     *
     * @return The BlePeerDiscoverer instance.
     */
    public BlePeerDiscoverer getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress() {
        if (mBlePeerDiscoverer == null) {
            Log.v(TAG, "getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress: Constructing...");
            mBlePeerDiscoverer = new BlePeerDiscoverer(
                    this,
                    mBluetoothManager.getBluetoothAdapter(),
                    mBleServiceUuid,
                    mBluetoothMacAddressResolutionHelper.getProvideBluetoothMacAddressRequestUuid(),
                    getBluetoothMacAddress(),
                    mSettings.getManufacturerId(),
                    mSettings.getBeaconAdLengthAndType(),
                    mSettings.getBeaconAdExtraInformation(),
                    mSettings.getAdvertisementDataType());
        }

        if (BluetoothUtils.isBluetoothMacAddressUnknown(mBlePeerDiscoverer.getBluetoothMacAddress())
                && BluetoothUtils.isValidBluetoothMacAddress(getBluetoothMacAddress())) {
            Log.v(TAG, "getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress: Updating Bluetooth MAC address...");
            mBlePeerDiscoverer.setBluetoothMacAddress(getBluetoothMacAddress());
        }

        return mBlePeerDiscoverer;
    }

    /**
     * From DiscoveryManagerSettings.Listener
     *
     * @param discoveryMode     The new discovery mode.
     * @param startIfNotRunning If true, will start even if the discovery wasn't running.
     */
    @Override
    public void onDiscoveryModeChanged(final DiscoveryMode discoveryMode, boolean startIfNotRunning) {
        if (mState != DiscoveryManagerState.NOT_STARTED) {
            stopForRestart();
            start();
        } else if (startIfNotRunning) {
            start(true, true);
        }
    }

    /**
     * From DiscoveryManagerSettings.Listener
     *
     * @param peerExpirationInMilliseconds The new peer expiration time in milliseconds.
     */
    @Override
    public void onPeerExpirationSettingChanged(long peerExpirationInMilliseconds) {
        mPeerModel.onPeerExpirationTimeChanged();
    }

    /**
     * From DiscoveryManagerSettings.Listener
     */
    @Override
    public void onAdvertiseScanSettingsChanged() {
        Log.d(TAG, "onAdvertiseScanSettingsChanged: " + ThreadUtils.currentThreadToString());
        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.applySettings(
                    mSettings.getManufacturerId(),
                    mSettings.getBeaconAdLengthAndType(),
                    mSettings.getBeaconAdExtraInformation(),
                    mSettings.getAdvertisementDataType(),
                    mSettings.getAdvertiseMode(),
                    mSettings.getAdvertiseTxPowerLevel(),
                    mSettings.getScanMode(),
                    mSettings.getScanReportDelay());
        }
    }

    /**
     * From BluetoothManager.BluetoothManagerListener
     * <p>
     * Stops/restarts the BLE based peer discovery depending on the given mode.
     *
     * @param mode The new mode.
     */
    @Override
    public void onBluetoothAdapterScanModeChanged(int mode) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.BLE || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onBluetoothAdapterScanModeChanged: Mode changed to " + mode);

            if (mode == BluetoothAdapter.SCAN_MODE_NONE) {
                if (isRunning()) {
                    Log.w(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth disabled, pausing BLE based peer discovery...");
                    stopBlePeerDiscoverer();
                }
                updateState();
            } else {
                if ((mShouldBeScanning || mShouldBeAdvertising)
                        && mBluetoothManager.isBluetoothEnabled()
                        && !mBluetoothMacAddressResolutionHelper.getIsBluetoothMacAddressGattServerStarted()) {
                    Log.i(TAG, "onBluetoothAdapterScanModeChanged: Bluetooth enabled, restarting BLE based peer discovery...");
                    start();
                }
            }
        }
    }

    /**
     * From BluetoothManager.BluetoothManagerListener
     * <p>
     * Stops/restarts the BLE based peer discovery depending on the given state.
     *
     * @param state The new state.
     */
    @Override
    public void onBluetoothAdapterStateChanged(int state) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.BLE || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onBluetoothAdapterStateChanged: State changed to " + state);

            if (state == BluetoothAdapter.STATE_OFF) {
                if (isRunning()) {
                    Log.w(TAG, "onBluetoothAdapterStateChanged: Bluetooth disabled, pausing BLE based peer discovery...");
                    stopBlePeerDiscoverer();
                }
                updateState();
            } else if (state == BluetoothAdapter.STATE_ON) {
                if ((mShouldBeScanning || mShouldBeAdvertising)
                        && mBluetoothManager.isBluetoothEnabled()
                        && !mBluetoothMacAddressResolutionHelper.getIsBluetoothMacAddressGattServerStarted()) {
                    Log.i(TAG, "onBluetoothAdapterStateChanged: Bluetooth enabled, restarting BLE based peer discovery...");
                    start();
                }
            }
        }
    }

    /**
     * From WifiDirectManager.WifiStateListener
     * <p>
     * Stops/restarts the Wi-Fi Direct based peer discovery depending on the given P2P state.
     *
     * @param state The new state.
     */
    @Override
    public void onWifiP2PStateChanged(int state) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.WIFI || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onWifiP2PStateChanged: State changed to " + state);

            if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                if (isRunning()) {
                    Log.w(TAG, "onWifiP2PStateChanged: Wi-Fi P2P disabled, pausing Wi-Fi Direct based peer discovery...");
                    stopWifiPeerDiscovery();
                }
                updateState();
            } else {
                if (mShouldBeScanning || mShouldBeAdvertising) {
                    Log.i(TAG, "onWifiP2PStateChanged: Wi-Fi P2P enabled, trying to restart Wi-Fi Direct based peer discovery...");
                    start();
                }
            }
        }
    }

    /**
     * From WifiDirectManager.WifiStateListener
     * <p>
     * Stops/restarts the Wi-Fi Direct based peer discovery depending on the given state.
     *
     * @param state The new state.
     */
    @Override
    public void onWifiStateChanged(int state) {
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();

        if (discoveryMode == DiscoveryMode.WIFI || discoveryMode == DiscoveryMode.BLE_AND_WIFI) {
            Log.i(TAG, "onWifiStateChanged: State changed to " + state);

            if (state == WifiManager.WIFI_STATE_DISABLED) {
                if (isRunning()) {
                    Log.w(TAG, "onWifiStateChanged: Wi-Fi disabled, pausing Wi-Fi Direct based peer discovery...");
                    stopWifiPeerDiscovery();
                }
                updateState();
            } else if (state == WifiManager.WIFI_STATE_ENABLED) {
                if (mShouldBeScanning || mShouldBeAdvertising) {
                    Log.i(TAG, "onWifiStateChanged: Wi-Fi enabled, trying to restart Wi-Fi Direct based peer discovery...");
                    start();
                }
            }
        }
    }

    /**
     * From WifiPeerDiscoverer.WifiPeerDiscoveryListener
     * <p>
     * Stores the new state and notifies the listener.
     *
     * @param state The new state.
     */
    @Override
    public void onWifiPeerDiscovererStateChanged(EnumSet<WifiPeerDiscovererStateSet> state) {
        if (!mWifiPeerDiscovererStateSet.equals(state)) {
            Log.i(TAG, "onWifiPeerDiscovererStateChanged: " + mWifiPeerDiscovererStateSet + " -> " + state +
                    " " + ThreadUtils.currentThreadToString());
            mWifiPeerDiscovererStateSet = state;
            updateState();
        }
    }

    /**
     * From BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Stores the new state and notifies the listener.
     *
     * @param state The new state.
     */
    @Override
    public void onBlePeerDiscovererStateChanged(EnumSet<BlePeerDiscovererStateSet> state) {
        if (!mBlePeerDiscovererStateSet.equals(state)) {
            Log.i(TAG, "onBlePeerDiscovererStateChanged: " + mBlePeerDiscovererStateSet + " -> " + state +
                    " " + ThreadUtils.currentThreadToString());
            mBlePeerDiscovererStateSet = state;
            updateState();
        }
    }

    /**
     * From both WifiPeerDiscoverer.WifiPeerDiscoveryListener and BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Adds or updates the discovered peer.
     *
     * @param peerProperties The properties of the discovered peer.
     */
    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.d(TAG, "onPeerDiscovered: " + peerProperties);
        mPeerModel.addOrUpdateDiscoveredPeer(peerProperties); // Will notify us, if added/updated
    }

    /**
     * From WifiPeerDiscoverer.WifiPeerDiscoveryListener
     * <p>
     * Updates the discovered peers, which match the ones on the given list.
     *
     * @param p2pDeviceList A list containing the discovered P2P devices.
     */
    @Override
    public synchronized void onP2pDeviceListChanged(Collection<WifiP2pDevice> p2pDeviceList) {
        if (p2pDeviceList != null && p2pDeviceList.size() > 0) {
            int index = 0;

            for (WifiP2pDevice wifiP2pDevice : p2pDeviceList) {
                if (wifiP2pDevice != null) {
                    Log.d(TAG, "onP2pDeviceListChanged: Peer " + (index + 1) + ": "
                            + wifiP2pDevice.deviceName + " " + wifiP2pDevice.deviceAddress);

                    PeerProperties peerProperties = mPeerModel.getDiscoveredPeerByDeviceAddress(wifiP2pDevice.deviceAddress);

                    if (peerProperties != null) {
                        mPeerModel.addOrUpdateDiscoveredPeer(peerProperties);
                    }
                }

                index++;
            }
        }
    }

    /**
     * From BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Part of Bro Mode.
     * <p>
     * Forwards the event to the listener, if the Bluetooth MAC address resolution process is not
     * set to be automated.
     * <p>
     * Otherwise, starts discovering Bluetooth devices to find out their Bluetooth MAC addresses so
     * that we can provide them to the devices unaware of their own addresses.
     */
    @Override
    public void onProvideBluetoothMacAddressRequest(final String requestId) {
        String currentProvideBluetoothMacAddressRequestId =
                mBluetoothMacAddressResolutionHelper.getCurrentProvideBluetoothMacAddressRequestId();

        if (!mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted()) {
            Log.d(TAG, "onProvideBluetoothMacAddressRequest: " + requestId);

            if (mSettings.getAutomateBluetoothMacAddressResolution()) {
                if (!mBluetoothMacAddressResolutionHelper.startProvideBluetoothMacAddressMode(requestId)) {
                    Log.e(TAG, "onProvideBluetoothMacAddressRequest: Failed to start the \"Provide Bluetooth MAC address\" mode");
                }
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onProvideBluetoothMacAddressRequest(requestId);
                    }
                });
            }
        } else if (currentProvideBluetoothMacAddressRequestId != null
                && !currentProvideBluetoothMacAddressRequestId.equals(requestId)) {
            Log.d(TAG, "onProvideBluetoothMacAddressRequest: Received request ID \""
                    + requestId + "\", but already servicing \""
                    + currentProvideBluetoothMacAddressRequestId + "\"");
        }
    }

    /**
     * From BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Part of Bro Mode.
     * <p>
     * Forwards the event to the listener, if the Bluetooth MAC address resolution process is not
     * set to be automated.
     * <p>
     * Otherwise, starts "Receive Bluetooth MAC address" mode, which also requests the device to
     * make itself discoverable (requires user's attention).
     *
     * @param requestId The request ID.
     */
    @Override
    public void onPeerReadyToProvideBluetoothMacAddress(String requestId) {
        if (mSettings.getAutomateBluetoothMacAddressResolution() && mBlePeerDiscoverer != null) {
            mBluetoothMacAddressResolutionHelper.startReceiveBluetoothMacAddressMode(
                    mBlePeerDiscoverer.getProvideBluetoothMacAddressRequestId());
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPeerReadyToProvideBluetoothMacAddress();
                }
            });
        }
    }

    /**
     * From both BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Part of Bro Mode.
     *
     * @param requestId    The request ID associated with the device in need of assistance.
     * @param wasCompleted True, if the operation was completed.
     */
    @Override
    public void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted) {
        Log.d(TAG, "onProvideBluetoothMacAddressResult: Operation with request ID \""
                + requestId + (wasCompleted ? "\" was completed" : "\" was not completed"));

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
    }

    /**
     * From BlePeerDiscoverer.BlePeerDiscoveryListener
     * <p>
     * Part of Bro Mode.
     * <p>
     * Stores and forwards the resolved Bluetooth MAC address to the listener.
     *
     * @param bluetoothMacAddress Our Bluetooth MAC address.
     */
    @Override
    public void onBluetoothMacAddressResolved(final String bluetoothMacAddress) {
        Log.i(TAG, "onBluetoothMacAddressResolved: " + bluetoothMacAddress);

        if (BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
            mSettings.setBluetoothMacAddress(bluetoothMacAddress);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onBluetoothMacAddressResolved(bluetoothMacAddress);
                    mBluetoothMacAddressResolutionHelper.stopReceiveBluetoothMacAddressMode();
                    start();
                }
            });
        }
    }

    /**
     * From BluetoothMacAddressResolutionHelper.BluetoothMacAddressResolutionHelperListener
     * <p>
     * Part of Bro Mode.
     * <p>
     * Changes the state based on the given argument.
     *
     * @param isStarted If true, was started. If false, was stopped.
     */
    @Override
    public void onProvideBluetoothMacAddressModeStartedChanged(final boolean isStarted) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isStarted) {
                    updateState();
                } else {
                    stopBlePeerDiscoverer();
                    start();
                }
            }
        });
    }

    /**
     * From BluetoothMacAddressResolutionHelper.BluetoothMacAddressResolutionHelperListener
     * <p>
     * Part of Bro Mode.
     * <p>
     * Changes the state based on the given argument.
     *
     * @param isStarted If true, was started. If false, was stopped.
     */
    @Override
    public void onReceiveBluetoothMacAddressModeStartedChanged(final boolean isStarted) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isStarted) {
                    if (mBlePeerDiscoverer != null) {
                        Log.v(TAG, "onReceiveBluetoothMacAddressModeStartedChanged: Stopping BLE scanning in order to increase the bandwidth for GATT");
                        mBlePeerDiscoverer.stopScanner();
                    }
                } else {
                    start();
                }
            }
        });
    }

    /**
     * From PeerModel.Listener
     * <p>
     * Forwards the event to the listener.
     *
     * @param peerProperties The properties of the added peer.
     */
    @Override
    public void onPeerAdded(final PeerProperties peerProperties) {
        Log.d(TAG, "onPeerAdded: " + peerProperties.toString());
        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPeerDiscovered(peerProperties);
                }
            });
        }
    }

    /**
     * From PeerModel.Listener
     * <p>
     * Forwards the event to the listener.
     *
     * @param peerProperties The properties of the updated peer.
     */
    @Override
    public void onPeerUpdated(final PeerProperties peerProperties) {
        Log.d(TAG, "onPeerUpdated: " + peerProperties.toString());
        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPeerUpdated(peerProperties);
                }
            });
        }
    }

    /**
     * From PeerModel.Listener
     * <p>
     * Forwards the event to the listener.
     *
     * @param peerProperties The properties of the expired and removed peer.
     */
    @Override
    public void onPeerExpiredAndRemoved(final PeerProperties peerProperties) {
        Log.d(TAG, "onPeerExpiredAndRemoved: " + peerProperties.toString() + ThreadUtils.currentThreadToString());
        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "onPeerExpiredAndRemoved: " + peerProperties.toString() + ThreadUtils.currentThreadToString());
                    mListener.onPeerLost(peerProperties);
                }
            });
        }
    }

    /**
     * Stops the discovery for pending restart. Does not notify the listener.
     */
    private synchronized void stopForRestart() {
        Log.d(TAG, "stopForRestart " + ThreadUtils.currentThreadToString());
        if (mState != DiscoveryManagerState.NOT_STARTED) {
            Log.d(TAG, "stopForRestart. " + mState.toString());
            mBluetoothMacAddressResolutionHelper.stopAllBluetoothMacAddressResolutionOperations();
            stopBlePeerDiscoverer();
            stopWifiPeerDiscovery();
        }
    }

    /**
     * Tries to start the BLE peer discoverer.
     *
     * @return True, if started (or already running). False otherwise.
     */
    private synchronized boolean startBlePeerDiscoverer() {
        Log.d(TAG, "startBlePeerDiscoverer");
        boolean started = false;
        boolean permissionsGranted = false;

        if (CommonUtils.isMarshmallowOrHigher()) {
            permissionsGranted = mListener.onPermissionCheckRequired(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else {
            permissionsGranted = true;
            mMissingPermission = null;
        }

        if (permissionsGranted) {
            if (mBluetoothManager.bind(this)) {
                getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

                if (mBleServiceUuid != null) {
                    if (mShouldBeScanning && mShouldBeAdvertising &&
                            isBleOffloadedFilteringSupported() &&
                            isBleOffloadedScanBatchingSupported() &&
                            isBleMultipleAdvertisementSupported()) {
                        started = mBlePeerDiscoverer.startScannerAndAdvertiser();
                    } else if (mShouldBeScanning &&
                            isBleOffloadedFilteringSupported() &&
                            isBleOffloadedScanBatchingSupported()) {
                        started = mBlePeerDiscoverer.startScanner();
                    } else if (mShouldBeAdvertising && isBleMultipleAdvertisementSupported()) {
                        started = mBlePeerDiscoverer.startAdvertiser();
                    } else {
                        Log.w(TAG, "startBlePeerDiscoverer: useless ble start discovering");
                    }
                } else {
                    Log.e(TAG, "startBlePeerDiscoverer: No BLE service UUID");
                }
            }
        } else {
            mMissingPermission = Manifest.permission.ACCESS_COARSE_LOCATION;
            Log.e(TAG, "startBlePeerDiscoverer: Permission \"" + mMissingPermission + "\" denied");
        }

        if (started) {
            Log.d(TAG, "startBlePeerDiscoverer: OK");
        }

        return started;
    }

    /**
     * Stops the BLE peer discoverer.
     */
    private synchronized void stopBlePeerDiscoverer() {
        if (mBlePeerDiscoverer != null) {
            mBlePeerDiscoverer.stopScannerAndAdvertiser();
            mBlePeerDiscoverer.releaseListener();
            mBlePeerDiscoverer = null;
            Log.d(TAG, "stopBlePeerDiscoverer: Stopped");
        }
    }

    /**
     * Tries to start the Wi-Fi Direct based peer discovery.
     * Note that this method does not validate the current state nor the identity string.
     *
     * @return True, if started (or already running). False otherwise.
     */
    private synchronized boolean startWifiPeerDiscovery() {
        boolean started = false;

        if (mWifiDirectManager.bind(this)) {
            if (mWifiPeerDiscoverer == null) {
                WifiP2pManager p2pManager = mWifiDirectManager.getWifiP2pManager();
                WifiP2pManager.Channel channel = mWifiDirectManager.getWifiP2pChannel();

                if (p2pManager != null && channel != null) {
                    mWifiPeerDiscoverer = new WifiPeerDiscoverer(
                            mContext, channel, p2pManager, this, mServiceType, mMyIdentityString);
                } else {
                    Log.e(TAG, "startWifiPeerDiscovery: Failed to get Wi-Fi P2P manager or channel");
                }
            }

            if (mWifiPeerDiscoverer != null) {
                if (mShouldBeScanning && mShouldBeAdvertising) {
                    started = mWifiPeerDiscoverer.startDiscovererAndAdvertiser();
                } else if (mShouldBeScanning) {
                    started = mWifiPeerDiscoverer.startDiscoverer();
                } else if (mShouldBeAdvertising) {
                    started = mWifiPeerDiscoverer.startAdvertiser();
                }

                if (started) {
                    Log.d(TAG, "startWifiPeerDiscovery: Wi-Fi Direct OK");
                }
            }
        } else {
            Log.e(TAG, "startWifiPeerDiscovery: Failed to start, this may indicate that Wi-Fi Direct is not supported on this device");
        }

        return started;
    }

    /**
     * Stops the Wi-Fi Direct based peer discovery.
     */
    private synchronized void stopWifiPeerDiscovery() {
        if (mWifiPeerDiscoverer != null) {
            mWifiPeerDiscoverer.stopDiscovererAndAdvertiser();
            mWifiPeerDiscoverer = null;
            Log.i(TAG, "stopWifiPeerDiscovery: Stopped");
        }
    }

    /**
     * Updates the state of this instance and notifies the listener.
     *
     * @param state         The new state.
     * @param isDiscovering The new status of discovering
     * @param isAdvertising The new status of advertising
     */
    private synchronized void updateStateInternal(final DiscoveryManagerState state, final boolean isDiscovering, final boolean isAdvertising) {
        Log.d(TAG, "updateStateInternal " + ThreadUtils.currentThreadToString());
        if (mState != state || mIsDiscovering != isDiscovering || mIsAdvertising != isAdvertising) {
            mState = state;
            mIsDiscovering = isDiscovering;
            mIsAdvertising = isAdvertising;

            Log.d(TAG, "updateState: State: " + mState
                    + ", is discovering: " + mIsDiscovering
                    + ", is advertising: " + mIsAdvertising);

            if (mListener != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onDiscoveryManagerStateChanged(state, isDiscovering, isAdvertising);
                    }
                });
            }
        }
        Log.d(TAG, "updateStateInternal finished " + ThreadUtils.currentThreadToString());
    }

    /**
     * Updates the state of this instance and notifies the listener.
     */
    private synchronized void updateState() {
        Log.d(TAG, "updateState: " + ThreadUtils.currentThreadToString());
        DiscoveryMode discoveryMode = mSettings.getDiscoveryMode();
        if (mBlePeerDiscoverer != null) {
            if (!mBlePeerDiscoverer.getState().equals(mBlePeerDiscovererStateSet)) {
                Log.e(TAG, "seems that updateState call different BlePeerDiscoverer");
            }
            mBlePeerDiscovererStateSet = mBlePeerDiscoverer.getState();
        }
        mBlePeerDiscovererStateSet = mBlePeerDiscoverer != null ? mBlePeerDiscoverer.getState() :
                mBlePeerDiscovererStateSet;
        boolean isBleAdvertising = isBleAdvertising();
        boolean isBleDiscovering = isBleDiscovering();
        boolean isWifiAdvertising = isWifiAdvertising();
        boolean isWifiDiscovering = isWifiDiscovering();
        boolean isBleWorking = isBleAdvertising || isBleDiscovering;
        boolean isWifiWorking = isWifiAdvertising || isWifiDiscovering;
        boolean isAdvertising = isBleAdvertising || isWifiAdvertising;
        boolean isDiscovering = isBleDiscovering || isWifiDiscovering;
        boolean bluetoothEnabled = mBluetoothManager.isBluetoothEnabled();
        boolean wifiEnabled = mWifiDirectManager.isWifiEnabled();

        if (isBleWorking) {
            if (BluetoothUtils.isBluetoothMacAddressUnknown(getBluetoothMacAddress()) &&
                    !mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted()) {
                updateStateInternal(DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS, isDiscovering, isAdvertising);
            } else if (mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted()) {
                updateStateInternal(DiscoveryManagerState.PROVIDING_BLUETOOTH_MAC_ADDRESS, isDiscovering, isAdvertising);
            } else if (isWifiWorking) {
                updateStateInternal(DiscoveryManagerState.RUNNING_BLE_AND_WIFI, isDiscovering, isAdvertising);
            } else {
                updateStateInternal(DiscoveryManagerState.RUNNING_BLE, isDiscovering, isAdvertising);
            }
        } else if (isWifiWorking) {
            updateStateInternal(DiscoveryManagerState.RUNNING_WIFI, isDiscovering, isAdvertising);
        } else {
            // no discovery/advertisement running
            if ((mShouldBeAdvertising || mShouldBeScanning) &&
                    ((discoveryMode == DiscoveryMode.BLE_AND_WIFI && !bluetoothEnabled && !wifiEnabled) ||
                            (discoveryMode == DiscoveryMode.BLE && !bluetoothEnabled) ||
                            (discoveryMode == DiscoveryMode.WIFI && !wifiEnabled))) {
                updateStateInternal(DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED, false, false);
            } else {
                updateStateInternal(DiscoveryManagerState.NOT_STARTED, false, false);
            }
        }
        Log.d(TAG, "updateState finished " + ThreadUtils.currentThreadToString());
    }
}
