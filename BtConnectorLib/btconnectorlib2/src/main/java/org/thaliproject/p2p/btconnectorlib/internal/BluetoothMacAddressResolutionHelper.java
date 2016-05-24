/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothDeviceDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BluetoothGattManager;
import java.util.UUID;

/**
 * A helper class to manage providing peer/receiving Bluetooth MAC address (Bro Mode).
 */
public class BluetoothMacAddressResolutionHelper
        implements BluetoothDeviceDiscoverer.BluetoothDeviceDiscovererListener,
                   BluetoothGattManager.BluetoothGattManagerListener {
    public interface BluetoothMacAddressResolutionHelperListener {
        /**
         * Called when we start/stop providing peer its Bluetooth MAC address.
         * @param isStarted If true, was started. If false, was stopped.
         */
        void onProvideBluetoothMacAddressModeStartedChanged(boolean isStarted);

        /**
         * Called when we start/stop receive Bluetooth MAC address mode.
         * @param isStarted If true, was started. If false, was stopped.
         */
        void onReceiveBluetoothMacAddressModeStartedChanged(boolean isStarted);
    }

    private final String TAG = BluetoothMacAddressResolutionHelper.class.getName();

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final UUID mProvideBluetoothMacAddressRequestUuid;
    private final DiscoveryManager mDiscoveryManager;
    private final DiscoveryManagerSettings mSettings;
    private final BluetoothGattManager mBluetoothGattManager;

    private BluetoothDeviceDiscoverer mBluetoothDeviceDiscoverer = null;
    private CountDownTimer mReceiveBluetoothMacAddressTimeoutTimer = null;
    private String mCurrentProvideBluetoothMacAddressRequestId = null;
    private boolean mIsProvideBluetoothMacAddressModeStarted = false;
    private boolean mIsReceiveBluetoothMacAddressModeStarted = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param bluetoothAdapter The Bluetooth adapter instance.
     * @param discoveryManager The discovery manager instance.
     * @param bleServiceUuid Our BLE service UUID for the Bluetooth GATT manager.
     * @param provideBluetoothMacAddressRequestUuid The UUID for "Provide Bluetooth MAC address" request.
     */
    public BluetoothMacAddressResolutionHelper(
            Context context, BluetoothAdapter bluetoothAdapter, DiscoveryManager discoveryManager,
            UUID bleServiceUuid, UUID provideBluetoothMacAddressRequestUuid) {
        this(context, bluetoothAdapter, discoveryManager, bleServiceUuid, provideBluetoothMacAddressRequestUuid, null);
    }

    /**
     * Constructor.
     * @param context The application context.
     * @param bluetoothAdapter The Bluetooth adapter instance.
     * @param discoveryManager The discovery manager instance.
     * @param bleServiceUuid Our BLE service UUID for the Bluetooth GATT manager.
     * @param provideBluetoothMacAddressRequestUuid The UUID for "Provide Bluetooth MAC address" request.
     * @param preferences The shared preferences.
     */
    public BluetoothMacAddressResolutionHelper(
            Context context, BluetoothAdapter bluetoothAdapter, DiscoveryManager discoveryManager,
            UUID bleServiceUuid, UUID provideBluetoothMacAddressRequestUuid, SharedPreferences preferences) {

        mContext = context;
        mBluetoothAdapter = bluetoothAdapter;
        mDiscoveryManager = discoveryManager;
        mBluetoothGattManager = new BluetoothGattManager(this, context, bleServiceUuid);

        if (preferences == null) {
            mSettings = DiscoveryManagerSettings.getInstance(context);
        } else {
            mSettings = DiscoveryManagerSettings.getInstance(context, preferences);
        }
        mProvideBluetoothMacAddressRequestUuid = provideBluetoothMacAddressRequestUuid;
    }

    /**
     * @return The UUID for "Provide Bluetooth MAC address" request.
     */
    public UUID getProvideBluetoothMacAddressRequestUuid() {
        return mProvideBluetoothMacAddressRequestUuid;
    }

    /**
     * @return The request ID of the current "Provide Bluetooth MAC address" request.
     */
    public String getCurrentProvideBluetoothMacAddressRequestId() {
        return mCurrentProvideBluetoothMacAddressRequestId;
    }

    /**
     * @return True, if the "Provide Bluetooth MAC address" mode is started.
     */
    public boolean getIsProvideBluetoothMacAddressModeStarted() {
        return mIsProvideBluetoothMacAddressModeStarted;
    }

    /**
     * @return True, if the Bluetooth GATT server for receiving the Bluetooth MAC address is started.
     */
    public boolean getIsBluetoothMacAddressGattServerStarted() {
        return mBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted();
    }

    /**
     * @return True, if the "Receive Bluetooth MAC address" mode is started.
     */
    public boolean getIsReceiveBluetoothMacAddressModeStarted() {
        return mIsReceiveBluetoothMacAddressModeStarted;
    }

    /**
     * Stops all Bluetooth MAC address resolution operations.
     */
    public synchronized void stopAllBluetoothMacAddressResolutionOperations() {
        stopProvideBluetoothMacAddressMode(); // Stops the Bluetooth device discovery if it was running
        stopReceiveBluetoothMacAddressMode();
        mBluetoothGattManager.stopBluetoothMacAddressRequestServer();
    }

    /**
     * Starts the "Provide Bluetooth MAC address" mode for certain period of time.
     * @param requestId The request ID to identify the device in need of assistance. This ID should
     *                  have been provided by the said device via onProvideBluetoothMacAddressRequest
     *                  callback.
     * @return True, if the "Provide Bluetooth MAC address" mode was started successfully. False otherwise.
     */
    public synchronized boolean startProvideBluetoothMacAddressMode(String requestId) {
        Log.d(TAG, "startProvideBluetoothMacAddressMode: " + requestId);
        boolean wasStarted = false;

        if (mDiscoveryManager.isBleMultipleAdvertisementSupported()) {
            if (!mIsProvideBluetoothMacAddressModeStarted) {
                mIsProvideBluetoothMacAddressModeStarted = true;
                mCurrentProvideBluetoothMacAddressRequestId = requestId;

                if (mCurrentProvideBluetoothMacAddressRequestId != null
                        && mCurrentProvideBluetoothMacAddressRequestId.length() > 0) {
                    if (startBluetoothDeviceDiscovery()) {
                        BlePeerDiscoverer blePeerDiscoverer =
                                mDiscoveryManager.getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

                        if (blePeerDiscoverer.startPeerAddressHelperAdvertiser(
                                mCurrentProvideBluetoothMacAddressRequestId,
                                PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN,
                                mSettings.getProvideBluetoothMacAddressTimeout())) {
                            mDiscoveryManager.onProvideBluetoothMacAddressModeStartedChanged(true);
                            wasStarted = true;
                        } else {
                            Log.e(TAG, "startProvideBluetoothMacAddressMode: Failed to start advertising our willingness to help via BLE");
                        }
                    } else {
                        Log.e(TAG, "startProvideBluetoothMacAddressMode: Failed to start Bluetooth device discovery");
                    }
                } else {
                    Log.e(TAG, "startProvideBluetoothMacAddressMode: Invalid request ID: "
                            + mCurrentProvideBluetoothMacAddressRequestId);
                }
            } else {
                Log.d(TAG, "startProvideBluetoothMacAddressMode: Already started");
            }
        } else {
            Log.e(TAG, "startProvideBluetoothMacAddressMode: Bluetooth LE advertising is not supported on this device");
        }

        if (!wasStarted) {
            // Failed to start
            stopProvideBluetoothMacAddressMode();
        }

        return wasStarted;
    }

    /**
     * Stops the "Provide Bluetooth MAC address" mode and notifies the discovery manager.
     */
    public synchronized void stopProvideBluetoothMacAddressMode() {
        Log.d(TAG, "stopProvideBluetoothMacAddressMode");
        mCurrentProvideBluetoothMacAddressRequestId = null;
        mBluetoothGattManager.clearBluetoothGattClientOperationQueue();
        mIsProvideBluetoothMacAddressModeStarted = false;
        stopBluetoothDeviceDiscovery();
        mDiscoveryManager.onProvideBluetoothMacAddressModeStartedChanged(false);
    }

    /**
     * Tries to provide the given device with its Bluetooth MAC address via Bluetooth GATT service.
     * @param bluetoothDevice The Bluetooth device to provide the address to.
     */
    public void provideBluetoothMacAddressToDevice(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice != null) {
            mBluetoothGattManager.provideBluetoothMacAddressToDevice(
                    bluetoothDevice, mCurrentProvideBluetoothMacAddressRequestId);
        }
    }

    /**
     * Starts "Receive Bluetooth MAC address" mode. This should be called when
     * we get notified that there is a peer ready to provide us our Bluetooth
     * MAC address.
     * @param requestId Our "Provide Bluetooth MAC address" request ID.
     */
    public synchronized void startReceiveBluetoothMacAddressMode(String requestId) {
        if (!mIsReceiveBluetoothMacAddressModeStarted) {
            mIsReceiveBluetoothMacAddressModeStarted = true;
            Log.i(TAG, "startReceiveBluetoothMacAddressMode: Starting...");

            if (mReceiveBluetoothMacAddressTimeoutTimer != null) {
                mReceiveBluetoothMacAddressTimeoutTimer.cancel();
                mReceiveBluetoothMacAddressTimeoutTimer = null;
            }

            startBluetoothMacAddressGattServer(requestId); // Starts if not yet started, otherwise does nothing

            if (mSettings.getAutomateBluetoothMacAddressResolution()) {
                long durationInSeconds = mSettings.getProvideBluetoothMacAddressTimeout();

                if (durationInSeconds == 0) {
                    durationInSeconds = DiscoveryManagerSettings.DEFAULT_DEVICE_DISCOVERABLE_DURATION_IN_SECONDS;
                } else {
                    durationInSeconds /= 1000;
                }

                mDiscoveryManager.makeDeviceDiscoverable((int)durationInSeconds);
            }

            long timeoutInMilliseconds = mSettings.getProvideBluetoothMacAddressTimeout();

            mReceiveBluetoothMacAddressTimeoutTimer =
                    new CountDownTimer(timeoutInMilliseconds, timeoutInMilliseconds) {
                        @Override
                        public void onTick(long l) {
                            // Not used
                        }

                        @Override
                        public void onFinish() {
                            Log.d(TAG, "Receive Bluetooth MAC address timeout");
                            this.cancel();
                            mReceiveBluetoothMacAddressTimeoutTimer = null;
                            stopReceiveBluetoothMacAddressMode();
                        }
                    };

            mReceiveBluetoothMacAddressTimeoutTimer.start();
        } else {
            Log.d(TAG, "startReceiveBluetoothMacAddressMode: Already started");
        }
    }

    /**
     * Stops "Receive Bluetooth MAC address" mode and notifies the discovery manager.
     */
    public synchronized void stopReceiveBluetoothMacAddressMode() {
        if (mReceiveBluetoothMacAddressTimeoutTimer != null) {
            mReceiveBluetoothMacAddressTimeoutTimer.cancel();
            mReceiveBluetoothMacAddressTimeoutTimer = null;
        }

        // Uncomment the following to stop the Bluetooth GATT server
        //mBluetoothGattManager.stopBluetoothMacAddressRequestServer();

        mIsReceiveBluetoothMacAddressModeStarted = false;
        mDiscoveryManager.onReceiveBluetoothMacAddressModeStartedChanged(false);
    }

    /**
     * Starts the Bluetooth GATT server for receiving the Bluetooth MAC address.
     * @param requestId
     */
    public void startBluetoothMacAddressGattServer(String requestId) {
        Log.v(TAG, "startBluetoothMacAddressGattServer: Request ID: " + requestId);

        if (!mBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted()) {
            mBluetoothGattManager.startBluetoothMacAddressRequestServer(requestId);
        } else {
            Log.d(TAG, "startBluetoothMacAddressGattServer: Already started");
        }
    }

    /**
     * Starts Bluetooth device discovery.
     *
     * Note that Bluetooth LE scanner cannot be running, when doing Bluetooth device discovery.
     * Otherwise, the state of the Bluetooth stack on the device may become invalid. Observed
     * consequences on Lollipop (Android version 5.x) include BLE scanning not turning on
     * ("app cannot be registered" error state) and finally the application utilizing this library
     * won't start at all (you get only a blank screen). To prevent this, calling this method will
     * always stop BLE discovery.
     *
     * This method should not be called directly by an app utilizing this library. The method is
     * public for testing purposes only.
     *
     * @return True, if started successfully. False otherwise.
     */
    public synchronized boolean startBluetoothDeviceDiscovery() {
        Log.d(TAG, "startBluetoothDeviceDiscovery");

        BlePeerDiscoverer blePeerDiscoverer =
                mDiscoveryManager.getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        blePeerDiscoverer.stopScanner();

        if (mBluetoothDeviceDiscoverer == null) {
            mBluetoothDeviceDiscoverer = new BluetoothDeviceDiscoverer(mContext, mBluetoothAdapter, this);
        }

        boolean isStarted = false;

        if (blePeerDiscoverer == null
                || !blePeerDiscoverer.getState().contains(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING)) {
            isStarted = (mBluetoothDeviceDiscoverer.isRunning()
                    || mBluetoothDeviceDiscoverer.start(mSettings.getProvideBluetoothMacAddressTimeout()));
        } else {
            Log.e(TAG, "startBluetoothDeviceDiscovery: Bluetooth LE peer discoverer cannot be running, when doing Bluetooth LE scanning");
        }

        return isStarted;
    }

    /**
     * Stops the Bluetooth device discovery.
     *
     * This method should not be called directly by an app utilizing this library. The method is
     * public for testing purposes only.
     *
     * @return True, if stopped. False otherwise.
     */
    public synchronized boolean stopBluetoothDeviceDiscovery() {
        boolean wasStopped = false;

        if (mBluetoothDeviceDiscoverer != null) {
            mBluetoothDeviceDiscoverer.stop();
            mBluetoothDeviceDiscoverer = null;
            Log.d(TAG, "stopBluetoothDeviceDiscovery: Stopped");
            wasStopped = true;
        }

        return wasStopped;
    }

    /**
     * From BluetoothDeviceDiscoverer.BluetoothDeviceDiscovererListener
     *
     * Initiates the operation to read the Bluetooth GATT characteristic containing the request ID
     * from a GATT service of the given Bluetooth device.
     * @param bluetoothDevice The Bluetooth device.
     */
    @Override
    public void onBluetoothDeviceDiscovered(BluetoothDevice bluetoothDevice) {
        String bluetoothMacAddress = bluetoothDevice.getAddress();
        Log.d(TAG, "onBluetoothDeviceDiscovered: " + bluetoothMacAddress);
        provideBluetoothMacAddressToDevice(bluetoothDevice);
    }

    /**
     * From BluetoothGattManager.BluetoothGattManagerListener
     *
     * Logs the event.
     *
     * @param operationCountInQueue The new operation count.
     */
    @Override
    public void onBluetoothGattClientOperationCountInQueueChanged(int operationCountInQueue) {
        Log.v(TAG, "onBluetoothGattClientOperationCountInQueueChanged: " + operationCountInQueue);
    }

    /**
     * From BluetoothGattManager.BluetoothGattManagerListener
     *
     * Stops the "Provide Bluetooth MAC address" mode and forwards the event to the discovery manager.
     *
     * @param requestId The request ID associated with the device in need of assistance.
     * @param wasCompleted True, if the operation was completed.
     */
    @Override
    public void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted) {
        Log.d(TAG, "onProvideBluetoothMacAddressResult: Operation with request ID \""
                + requestId + (wasCompleted ? "\" was completed" : "\" was not completed"));
        stopProvideBluetoothMacAddressMode();
        mDiscoveryManager.onProvideBluetoothMacAddressResult(requestId, wasCompleted);
    }

    /**
     * From BlePeerDiscoverer.BlePeerDiscoveryListener
     *
     * Stops the Bluetooth GATT server and forwards the event to the discovery manager.
     *
     * @param bluetoothMacAddress Our Bluetooth MAC address.
     */
    @Override
    public void onBluetoothMacAddressResolved(String bluetoothMacAddress) {
        Log.d(TAG, "onBluetoothMacAddressResolved: " + bluetoothMacAddress);
        mBluetoothGattManager.stopBluetoothMacAddressRequestServer();
        mDiscoveryManager.onBluetoothMacAddressResolved(bluetoothMacAddress);
    }
}
