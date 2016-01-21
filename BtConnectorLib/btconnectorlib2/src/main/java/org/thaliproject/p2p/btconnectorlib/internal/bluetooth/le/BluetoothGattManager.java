/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Bluetooth GATT services.
 * Used currently only for providing/resolving Bluetooth MAC address.
 */
public class BluetoothGattManager {
    public interface BluetoothGattManagerListener {
        /**
         * Called when the process of providing a peer its Bluetooth MAC address is complete.
         * @param requestId The request ID associated with the device in need of assistance.
         * @param wasCompleted True, if the operation was completed.
         */
        void onProvideBluetoothMacAddressResult(String requestId, boolean wasCompleted);

        /**
         * Called when the characteristic where we expected to receive our Bluetooth MAC address is
         * written.
         * @param bluetoothMacAddress Our Bluetooth MAC address.
         */
        void onBluetoothMacAddressResolved(String bluetoothMacAddress);
    }

    private class BluetoothGattRequest {
        protected BluetoothGatt bluetoothGatt = null;
        protected BluetoothDevice bluetoothDevice = null;
        protected UUID requestUuid = null;

        public BluetoothGattRequest(BluetoothDevice bluetoothDevice, String requestId) {
            this.bluetoothDevice = bluetoothDevice;
            requestUuid = PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(mServiceUuid, requestId);
        }
    }

    private static final String TAG = BluetoothGattManager.class.getName();
    private static final long REQUEST_TIMEOUT_IN_MILLISECONDS = 10000;
    private final BluetoothGattManagerListener mListener;
    private final Context mContext;
    private final UUID mServiceUuid;
    private UUID mProvideBluetoothMacAddressServerUuid = null;
    private BluetoothGattServer mBluetoothGattServer = null;
    private String mRequestIdForBluetoothGattService = null; // For server
    private CopyOnWriteArrayList<BluetoothGattRequest> mBluetoothGattRequestQueue = new CopyOnWriteArrayList<>();
    private BluetoothGatt mCurrentBluetoothGatt = null;
    private CountDownTimer mCancelRequestTimer = null;
    private boolean mBluetoothGattRequestExecutionInProgress = false;
    private boolean mBluetoothMacAddressRequestServerStarted = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param context The application context.
     * @param serviceUuid The service UUID.
     */
    public BluetoothGattManager(BluetoothGattManagerListener listener, Context context, UUID serviceUuid) {
        mListener = listener;
        mContext = context;
        mServiceUuid = serviceUuid;
    }

    /**
     * @return True, if a Bluetooth MAC address request (GATT) service has been added and the
     * Bluetooth GATT server is running.
     */
    public boolean getIsBluetoothMacAddressRequestServerStarted() {
        return mBluetoothMacAddressRequestServerStarted;
    }

    /**
     * Adds or updates a GATT service with the given request UUID for Bluetooth MAC address request.
     * @param requestId The request ID.
     * @return True, if success. False otherwise.
     */
    public void startBluetoothMacAddressRequestServer(final String requestId) {
        if (requestId != null &&
                (mRequestIdForBluetoothGattService == null
                        || !mRequestIdForBluetoothGattService.equals(requestId))) {
            Log.d(TAG, "startBluetoothMacAddressRequestServer: Trying to add a service with request ID " + requestId);
            stopBluetoothMacAddressRequestServer();

            mRequestIdForBluetoothGattService = requestId;
            mProvideBluetoothMacAddressServerUuid =
                    PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(
                            mServiceUuid, mRequestIdForBluetoothGattService);

            final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

            if (bluetoothManager != null) {
                new Handler(mContext.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothGattServer = bluetoothManager.openGattServer(mContext, new MyBluetoothGattServerCallback());

                        if (mBluetoothGattServer != null) {
                            Log.d(TAG, "startBluetoothMacAddressRequestServer: Open Bluetooth GATT server OK");

                            BluetoothGattService bluetoothGattService =
                                    createBluetoothGattService(mRequestIdForBluetoothGattService);

                            if (mBluetoothGattServer.addService(bluetoothGattService)) {
                                Log.d(TAG, "startBluetoothMacAddressRequestServer: Add service, with request ID \""
                                        + mRequestIdForBluetoothGattService + "\", operation initiated successfully");
                            } else {
                                Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to add the Bluetooth GATT service");
                                stopBluetoothMacAddressRequestServer();
                            }
                        } else {
                            Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to open the Bluetooth GATT server");
                            stopBluetoothMacAddressRequestServer();
                        }
                    }
                });
            } else {
                Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to obtain the Bluetooth manager (android.bluetooth.BluetoothManager) instance");
            }
        }
    }

    /**
     * Stops the Bluetooth GATT server with Bluetooth MAC address request service.
     */
    public void stopBluetoothMacAddressRequestServer() {
        if (mBluetoothGattServer != null) {
            mBluetoothGattServer.close();
            mBluetoothGattServer = null;
            Log.d(TAG, "stopBluetoothMacAddressRequestServer: Stopped");
        }

        mRequestIdForBluetoothGattService = null;
        mBluetoothMacAddressRequestServerStarted = false;
    }

    /**
     * Initiates the operation, which should eventually provide the given Bluetooth device its
     * Bluetooth MAC address should it be the device who made the request.
     * @param bluetoothDevice A Bluetooth device instance.
     * @param requestId The "provide Bluetooth MAC address" request ID.
     */
    public void provideBluetoothMacAddressToDevice(final BluetoothDevice bluetoothDevice, final String requestId) {
        if (bluetoothDevice != null && requestId != null) {
            BluetoothGattRequest request = getBluetoothGattRequest(bluetoothDevice);

            if (request != null && PeerAdvertisementFactory.parseRequestIdFromUuid(request.requestUuid).equals(requestId)) {
                Log.e(TAG, "provideBluetoothMacAddressToDevice: An existing request already pending");
            } else {
                request = new BluetoothGattRequest(bluetoothDevice, requestId);
                mBluetoothGattRequestQueue.add(request);
                executeRequest();
            }
        }
    }

    /**
     * Clears all existing BluetoothGatt instances and closes them.
     */
    public synchronized void clearBluetoothGattRequestQueue() {
        if (mBluetoothGattRequestQueue.size() > 0) {
            Log.i(TAG, "clearBluetoothGattRequestQueue: Clearing " + mBluetoothGattRequestQueue.size() + " instances");

            for (BluetoothGattRequest bluetoothGattRequest : mBluetoothGattRequestQueue) {
                if (bluetoothGattRequest.bluetoothGatt != null) {
                    bluetoothGattRequest.bluetoothGatt.close();
                }
            }

            mBluetoothGattRequestQueue.clear();
        }

        mBluetoothGattRequestExecutionInProgress = false;
    }

    /**
     * Executes the request, which has been in the queue the longest.
     */
    private void executeRequest() {
        if (mBluetoothGattRequestQueue.size() > 0) {
            if (!mBluetoothGattRequestExecutionInProgress) {
                mBluetoothGattRequestExecutionInProgress = true;

                if (mCancelRequestTimer != null) {
                    mCancelRequestTimer.cancel();
                    mCancelRequestTimer = null;
                }

                final BluetoothGattRequest bluetoothGattRequest = mBluetoothGattRequestQueue.get(0);

                if (bluetoothGattRequest != null && bluetoothGattRequest.bluetoothDevice != null) {
                    final BluetoothDevice bluetoothDevice = bluetoothGattRequest.bluetoothDevice;

                    new Handler(mContext.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentBluetoothGatt =
                                    bluetoothDevice.connectGatt(
                                            mContext, false, new MyBluetoothGattCallback());

                            if (mCurrentBluetoothGatt != null) {
                                Log.i(TAG, "executeRequest: Connection to Bluetooth GATT (device address \""
                                        + bluetoothDevice.getAddress() + "\") initiated");
                                bluetoothGattRequest.bluetoothGatt = mCurrentBluetoothGatt;

                                mCancelRequestTimer = new CountDownTimer(
                                        REQUEST_TIMEOUT_IN_MILLISECONDS, REQUEST_TIMEOUT_IN_MILLISECONDS) {
                                    @Override
                                    public void onTick(long l) {
                                        // Not used
                                    }

                                    @Override
                                    public void onFinish() {
                                        Log.d(TAG, "Request timeout (device address: \"" + bluetoothGattRequest.bluetoothDevice.getAddress() + "\")");
                                        this.cancel();
                                        mCancelRequestTimer = null;
                                        removeBluetoothGattRequestFromQueue(bluetoothGattRequest, true);
                                    }
                                };

                                mCancelRequestTimer.start();
                            } else {
                                Log.d(TAG, "executeRequest: Failed to connect to Bluetooth GATT (device address \""
                                        + bluetoothDevice.getAddress() + "\")");
                                removeBluetoothGattRequestFromQueue(bluetoothGattRequest, true);
                            }
                        }
                    });
                }
            } else {
                Log.d(TAG, "executeRequest: Waiting for the previous request to complete");
            }
        } else {
            Log.d(TAG, "executeRequest: The queue is empty");
        }
    }

    /**
     * Finds the request associated with the given Bluetooth device instance
     * @param bluetoothDevice The Bluetooth device instance associated with the request to find.
     * @return A BluetoothGattRequest instance or null if not found.
     */
    private synchronized BluetoothGattRequest getBluetoothGattRequest(final BluetoothDevice bluetoothDevice) {
        BluetoothGattRequest matchingBluetoothGattRequest = null;

        if (bluetoothDevice != null) {
            for (BluetoothGattRequest bluetoothGattRequest : mBluetoothGattRequestQueue) {
                if (bluetoothGattRequest.bluetoothDevice != null
                        && bluetoothGattRequest.bluetoothDevice.getAddress().equals(bluetoothDevice.getAddress())) {
                    matchingBluetoothGattRequest = bluetoothGattRequest;
                    break;
                }
            }
        }

        return matchingBluetoothGattRequest;
    }

    /**
     * Finds the request associated with the given BluetoothGatt instance.
     * @param bluetoothGatt The BluetoothGatt instance associated with the request to find.
     * @return A BluetoothGattRequest instance or null if not found.
     */
    private synchronized BluetoothGattRequest getBluetoothGattRequest(BluetoothGatt bluetoothGatt) {
        BluetoothGattRequest matchingBluetoothGattRequest = null;

        if (bluetoothGatt != null && bluetoothGatt.getDevice() != null) {
            matchingBluetoothGattRequest = getBluetoothGattRequest(bluetoothGatt.getDevice());
        }

        return matchingBluetoothGattRequest;
    }

    /**
     * Removes the given request from the queue (or actually moves it to the end of the queue) and
     * closes its BluetoothGatt instance if one exists.
     * @param bluetoothGattRequestToRemove The request to remove.
     * @param executeNext If true, will execute a next request in line.
     * @return True, if removed successfully. False, if not found.
     */
    private synchronized boolean removeBluetoothGattRequestFromQueue(
            final BluetoothGattRequest bluetoothGattRequestToRemove, boolean executeNext) {
        boolean wasRemoved = false;

        if (bluetoothGattRequestToRemove != null) {
            if (bluetoothGattRequestToRemove.bluetoothGatt != null) {
                bluetoothGattRequestToRemove.bluetoothGatt.disconnect();
                bluetoothGattRequestToRemove.bluetoothGatt.close();
                bluetoothGattRequestToRemove.bluetoothGatt = null;
            }
        }

        for (BluetoothGattRequest bluetoothGattRequest : mBluetoothGattRequestQueue) {
            if (bluetoothGattRequest.bluetoothDevice.getAddress().equals(
                    bluetoothGattRequestToRemove.bluetoothDevice.getAddress())) {
                mBluetoothGattRequestQueue.remove(bluetoothGattRequest);

                mBluetoothGattRequestQueue.add(bluetoothGattRequest); // Move to the end of the queue

                if (bluetoothGattRequest.bluetoothDevice != null
                        && bluetoothGattRequest.bluetoothDevice.getAddress() != null) {
                    Log.d(TAG, "removeBluetoothGattRequestFromQueue: Request associated with device address \""
                            + bluetoothGattRequest.bluetoothDevice.getAddress() + "\" removed from the queue");
                } else {
                    Log.d(TAG, "removeBluetoothGattRequestFromQueue: Request with request UUID \""
                            + bluetoothGattRequest.requestUuid + "\" removed from the queue");
                }

                wasRemoved = true;
                break;
            }
        }

        if (!wasRemoved) {
            Log.e(TAG, "removeBluetoothGattRequestFromQueue: Failed to remove the given BluetoothGatt instance from the queue");
        }

        if (executeNext) {
            mBluetoothGattRequestExecutionInProgress = false;
            executeRequest();
        }

        return wasRemoved;
    }

    /**
     * Removes and closes the given BluetoothGattRequest instance from the queue.
     * @param bluetoothGattToRemove The Bluetooth GATT to remove.
     * @param executeNext If true, will execute a next request in line.
     */
    private synchronized void removeBluetoothGattRequestFromQueue(
            BluetoothGatt bluetoothGattToRemove, boolean executeNext) {
        if (bluetoothGattToRemove != null && bluetoothGattToRemove.getDevice() != null) {
            String bluetoothMacAddress = bluetoothGattToRemove.getDevice().getAddress();

            if (bluetoothMacAddress != null) {
                for (BluetoothGattRequest bluetoothGattRequest : mBluetoothGattRequestQueue) {
                    if (bluetoothGattRequest.bluetoothGatt != null
                            && bluetoothGattRequest.bluetoothGatt.getDevice() != null
                            && bluetoothMacAddress.equals(bluetoothGattRequest.bluetoothGatt.getDevice().getAddress())) {
                        removeBluetoothGattRequestFromQueue(bluetoothGattRequest, executeNext);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Creates a new Bluetooth GATT service with the given request ID.
     * @param requestId The "provide Bluetooth MAC address" request ID.
     * @return A newly created Bluetooth GATT service.
     */
    private BluetoothGattService createBluetoothGattService(String requestId) {
        boolean createdSuccessfully = false;

        BluetoothGattService bluetoothGattService =
                new BluetoothGattService(mServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(
                PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(mServiceUuid, requestId),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        if (bluetoothGattCharacteristic.setValue(requestId.getBytes())) {
            if (bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic)) {
                createdSuccessfully = true;
            } else {
                Log.e(TAG, "createBluetoothGattService: Failed to add characteristic to Bluetooth GATT service");
            }
        } else {
            Log.e(TAG, "createBluetoothGattService: Failed to set Bluetooth GATT characteristic value");
        }

        if (!createdSuccessfully) {
            bluetoothGattService = null;
        }

        return bluetoothGattService;
    }

    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {
            Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            super.onConnectionStateChange(bluetoothGatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Connected");
                if (bluetoothGatt.discoverServices()) {
                    Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Bluetooth GATT service discovery started successfully");
                } else {
                    Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: Failed to start the Bluetooth GATT service discovery");
                    removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
                }
            } else {
                Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Not connected");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            //Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Status: " + status);
            super.onServicesDiscovered(bluetoothGatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService desiredBluetoothGattService = null;
                BluetoothGattCharacteristic desiredBluetoothGattCharacteristic = null;

                for (BluetoothGattService bluetoothGattService : bluetoothGatt.getServices()) {
                    Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Found service with UUID "
                            + bluetoothGattService.getUuid());

                    // For some reason, the UUID of the service is different than expected
                    /*if (bluetoothGattService.getUuid().equals(mServiceUuid)) {
                        desiredBluetoothGattService = bluetoothGattService;
                        desiredBluetoothGattCharacteristic = desiredBluetoothGattCharacteristic;
                        break;
                    }*/

                    for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {
                        Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: - The service has a characteristic with UUID "
                                + bluetoothGattCharacteristic.getUuid()
                                + ": Permissions: " + bluetoothGattCharacteristic.getPermissions()
                                + ", write type: " + bluetoothGattCharacteristic.getWriteType());

                        BluetoothGattRequest bluetoothGattRequest = getBluetoothGattRequest(bluetoothGatt);

                        if (bluetoothGattRequest != null &&
                                bluetoothGattCharacteristic.getUuid().equals(
                                        bluetoothGattRequest.requestUuid)) {
                            desiredBluetoothGattService = bluetoothGattService;
                            desiredBluetoothGattCharacteristic = bluetoothGattCharacteristic;
                            break;
                        }
                    }
                }

                if (desiredBluetoothGattService != null) {
                    //final BluetoothGattCharacteristic bluetoothGattCharacteristic =
                    //        desiredBluetoothGattService.getCharacteristic(mProvideBluetoothMacAddressClientUuid);

                    if (desiredBluetoothGattCharacteristic != null) {
                        Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Got Bluetooth GATT characteristic");

                        /*if (bluetoothGatt.readCharacteristic(desiredBluetoothGattCharacteristic)) {
                            Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Initiated read characteristic operation successfully");
                        } else {
                            Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the reading of the Bluetooth GATT characteristic");
                            //removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
                        }*/

                        desiredBluetoothGattCharacteristic.setValue(bluetoothGatt.getDevice().getAddress().getBytes());

                        if (bluetoothGatt.writeCharacteristic(desiredBluetoothGattCharacteristic)) {
                            Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Initiated write characteristic operation successfully");
                        } else {
                            Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the writing of the Bluetooth GATT characteristic");
                            removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
                        }
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the desired Bluetooth GATT characteristic");
                        removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
                    }
                } else {
                    Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the desired Bluetooth GATT service");
                    removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
                }
            } else {
                Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to discover services, got status: " + status);
                removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicRead: Status: " + status);
            super.onCharacteristicRead(bluetoothGatt, characteristic, status);

            if (characteristic != null && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattCallback.onCharacteristicRead: Value: " + characteristic.getValue());
            } else {
                Log.e(TAG, "BluetoothGattCallback.onCharacteristicRead: Status not successful or the Bluetooth GATT characteristic is null");
            }

            //removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicWrite: Status: " + status);
            super.onCharacteristicWrite(bluetoothGatt, bluetoothGattCharacteristic, status);

            BluetoothGattRequest bluetoothGattRequest = getBluetoothGattRequest(bluetoothGatt);

            if (bluetoothGattRequest != null &&
                    bluetoothGattCharacteristic.getUuid().equals(
                            bluetoothGattRequest.requestUuid)) {
                String requestId = PeerAdvertisementFactory.parseRequestIdFromUuid(bluetoothGattRequest.requestUuid);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mListener.onProvideBluetoothMacAddressResult(requestId, true);
                } else {
                    mListener.onProvideBluetoothMacAddressResult(requestId, false);
                }

                // We are done
                removeBluetoothGattRequestFromQueue(bluetoothGatt, true);
            }
        }
    }

    private class MyBluetoothGattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            //Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: Status: " + status);
            super.onServiceAdded(status, service);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: Service with UUID \""
                        + service.getUuid() + "\" added successfully");
                mBluetoothMacAddressRequestServerStarted = true;
            } else {
                Log.e(TAG, "BluetoothGattServerCallback.onServiceAdded: Failed to add service with UUID \""
                        + service.getUuid() + "\", got status: " + status);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            //Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            super.onConnectionStateChange(device, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Connected");
                //Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Device with address \""
                //        + device.getAddress()+ "\" connected");
            } else {
                Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Not connected");
            }
        }

        @Override
        public void onCharacteristicReadRequest(
                BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "BluetoothGattServerCallback.onCharacteristicReadRequest: "
                    + "Device address: " + device.getAddress()
                    + ", request ID: " + requestId
                    + ", offset: " + offset
                    + ", characteristic UUID: " + characteristic.getUuid());
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            if (mBluetoothGattServer != null && characteristic.getUuid().equals(mProvideBluetoothMacAddressServerUuid)) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN.getBytes());
            }
        }

        @Override
        public void onCharacteristicWriteRequest(
                BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "BluetoothGattServerCallback.onCharacteristicWriteRequest: "
                    + "Device address: " + device.getAddress()
                    + ", request ID: " + requestId
                    + ", characteristic UUID: " + characteristic.getUuid()
                    + ", preparedWrite: " + preparedWrite
                    + ", responseNeeded: " + responseNeeded
                    + ", offset: " + offset
                    + ", value: " + new String(value));
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (characteristic.getUuid().equals(mProvideBluetoothMacAddressServerUuid)) {
                String bluetoothMacAddress = null;

                if (value != null) {
                    bluetoothMacAddress = new String(value);

                    if (bluetoothMacAddress != null) {
                        mListener.onBluetoothMacAddressResolved(bluetoothMacAddress);
                    }
                }

                if (mBluetoothGattServer != null) {
                    mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
                }
            }
        }
    }
}
