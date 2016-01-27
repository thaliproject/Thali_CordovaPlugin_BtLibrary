/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Bluetooth GATT services.
 * Used currently only for providing/resolving Bluetooth MAC address.
 */
public class BluetoothGattManager {
    public interface BluetoothGattManagerListener {
        /**
         * Called when the Bluetooth GATT client operation count in the queue has changed.
         * @param operationCountInQueue The new operation count.
         */
        void onBluetoothGattClientOperationCountInQueueChanged(int operationCountInQueue);

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

    /**
     * Represents a single Bluetooth GATT client operation.
     */
    protected class BluetoothGattClientOperation {
        protected BluetoothGatt bluetoothGatt = null;
        protected BluetoothDevice bluetoothDevice = null;
        protected UUID requestUuid = null;
        protected boolean connected = false;

        public BluetoothGattClientOperation(BluetoothDevice bluetoothDevice, String requestId) {
            this.bluetoothDevice = bluetoothDevice;

            if (requestId != null) {
                requestUuid = PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(mServiceUuid, requestId);
            }
        }
    }

    private static final String TAG = BluetoothGattManager.class.getName();
    private static final long REQUEST_TIMEOUT_IN_MILLISECONDS = 15000;
    private final BluetoothGattManagerListener mListener;
    private final Context mContext;
    private final UUID mServiceUuid;
    private UUID mProvideBluetoothMacAddressServerUuid = null;
    private BluetoothGattServer mBluetoothGattServer = null;
    private String mRequestIdForBluetoothGattService = null; // For server
    private CopyOnWriteArrayList<BluetoothGattClientOperation> mBluetoothGattClientOperationQueue = new CopyOnWriteArrayList<>();
    private BluetoothGatt mCurrentBluetoothGatt = null;
    private CountDownTimer mCancelRequestTimer = null;
    private boolean mBluetoothGattClientOperationExecutionInProgress = false;
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

                            List<BluetoothGattService> bluetoothGattServices = mBluetoothGattServer.getServices();

                            if (bluetoothGattServices.size() > 0) {
                                for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
                                    Log.d(TAG, "startBluetoothMacAddressRequestServer: The server has service with UUID \""
                                            + bluetoothGattService.getUuid() + "\"");

                                    for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {
                                        Log.d(TAG, "startBluetoothMacAddressRequestServer: - Characteristic with UUID \""
                                                + bluetoothGattCharacteristic.getUuid()
                                                + "\": Value: " + bluetoothGattCharacteristic.getValue()
                                                + ", permissions: " + bluetoothGattCharacteristic.getPermissions()
                                                + ", write type: " + bluetoothGattCharacteristic.getWriteType());
                                    }
                                }

                                // Clear existing services
                                mBluetoothGattServer.clearServices();
                            } else {
                                Log.d(TAG, "startBluetoothMacAddressRequestServer: No existing services");
                            }

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
        if (bluetoothDevice != null) {
            String bluetoothMacAddress = getBluetoothMacAddress(bluetoothDevice, true);
            BluetoothGattClientOperation operation = getBluetoothGattClientOperation(bluetoothDevice);

            if (requestId != null) {
                if (operation != null && PeerAdvertisementFactory.parseRequestIdFromUuid(operation.requestUuid).equals(requestId)) {
                    Log.d(TAG, "provideBluetoothMacAddressToDevice: An existing operation with the same device address (\""
                            + bluetoothMacAddress + "\") and request ID (" + requestId
                            + ") already in the queue, removing the old previous and creating a new one...");
                    removeBluetoothGattClientOperationFromQueue(operation, true, false);
                } else {
                    Log.d(TAG, "provideBluetoothMacAddressToDevice: Adding a new operation (device address: \""
                            + bluetoothMacAddress + "\", request ID: " + requestId + ")");
                }

                operation = new BluetoothGattClientOperation(bluetoothDevice, requestId);
                mBluetoothGattClientOperationQueue.add(operation);
                mListener.onBluetoothGattClientOperationCountInQueueChanged(mBluetoothGattClientOperationQueue.size());
                executeBluetoothGattClientOperation();
            } else {
                // No request ID
                if (operation == null) {
                    // Used for testing; no request ID will do a service discovery
                    Log.d(TAG, "provideBluetoothMacAddressToDevice: No request ID, will only discover services...");
                    operation = new BluetoothGattClientOperation(bluetoothDevice, requestId);
                    mBluetoothGattClientOperationQueue.add(operation);
                    mListener.onBluetoothGattClientOperationCountInQueueChanged(mBluetoothGattClientOperationQueue.size());
                    executeBluetoothGattClientOperation();
                } else {
                    Log.d(TAG, "provideBluetoothMacAddressToDevice: An existing operation with the same device address (\""
                            + bluetoothMacAddress + "\") already in the queue");
                }
            }
        } else {
            Log.e(TAG, "provideBluetoothMacAddressToDevice: The given BluetoothDevice instance is null");
        }
    }

    /**
     * Clears all existing BluetoothGatt instances and closes them.
     */
    public synchronized void clearBluetoothGattClientOperationQueue() {
        if (mCancelRequestTimer != null) {
            mCancelRequestTimer.cancel();
            mCancelRequestTimer = null;
        }

        if (mBluetoothGattClientOperationQueue.size() > 0) {
            Log.i(TAG, "clearBluetoothGattClientOperationQueue: Clearing " + mBluetoothGattClientOperationQueue.size() + " instance(s)");

            for (BluetoothGattClientOperation bluetoothGattClientOperation : mBluetoothGattClientOperationQueue) {
                BluetoothGatt bluetoothGatt = bluetoothGattClientOperation.bluetoothGatt;

                if (bluetoothGatt != null) {
                    if (bluetoothGatt.getDevice() != null) {
                        Log.d(TAG, "clearBluetoothGattClientOperationQueue: Closing Bluetooth GATT to device with address "
                                + bluetoothGatt.getDevice().getAddress());
                    } else {
                        Log.d(TAG, "clearBluetoothGattClientOperationQueue: Closing " + bluetoothGatt);
                    }

                    bluetoothGattClientOperation.bluetoothGatt.close();
                }

                bluetoothGattClientOperation.connected = false;
            }

            mBluetoothGattClientOperationQueue.clear();
            mListener.onBluetoothGattClientOperationCountInQueueChanged(0);
        }

        mBluetoothGattClientOperationExecutionInProgress = false;
    }

    /**
     * Executes the action, which has been in the queue the longest.
     */
    private void executeBluetoothGattClientOperation() {
        if (mBluetoothGattClientOperationQueue.size() > 0) {
            if (!mBluetoothGattClientOperationExecutionInProgress) {
                mBluetoothGattClientOperationExecutionInProgress = true;

                if (mCancelRequestTimer != null) {
                    mCancelRequestTimer.cancel();
                    mCancelRequestTimer = null;
                }

                final BluetoothGattClientOperation bluetoothGattClientOperation = mBluetoothGattClientOperationQueue.get(0);

                if (bluetoothGattClientOperation != null && bluetoothGattClientOperation.bluetoothDevice != null) {
                    final BluetoothDevice bluetoothDevice = bluetoothGattClientOperation.bluetoothDevice;

                    new Handler(mContext.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentBluetoothGatt =
                                    bluetoothDevice.connectGatt(
                                            mContext, false, new MyBluetoothGattCallback());

                            if (mCurrentBluetoothGatt != null) {
                                Log.i(TAG, "executeBluetoothGattClientOperation: Connection to Bluetooth GATT (device address \""
                                        + bluetoothDevice.getAddress() + "\") initiated");
                                bluetoothGattClientOperation.bluetoothGatt = mCurrentBluetoothGatt;

                                mCancelRequestTimer = new CountDownTimer(
                                        REQUEST_TIMEOUT_IN_MILLISECONDS, REQUEST_TIMEOUT_IN_MILLISECONDS) {
                                    @Override
                                    public void onTick(long l) {
                                        // Not used
                                    }

                                    @Override
                                    public void onFinish() {
                                        this.cancel();
                                        mCancelRequestTimer = null;

                                        if (getBluetoothGattClientOperation(bluetoothDevice) != null) {
                                            Log.d(TAG, "Request timeout (device address: \"" + bluetoothGattClientOperation.bluetoothDevice.getAddress() + "\")");
                                            removeBluetoothGattClientOperationFromQueue(bluetoothGattClientOperation, true, true);
                                        }
                                    }
                                };

                                mCancelRequestTimer.start();
                            } else {
                                Log.d(TAG, "executeBluetoothGattClientOperation: Failed to connect to Bluetooth GATT (device address \""
                                        + bluetoothDevice.getAddress() + "\")");
                                removeBluetoothGattClientOperationFromQueue(bluetoothGattClientOperation, true, true);
                            }
                        }
                    });
                }
            } else {
                Log.d(TAG, "executeBluetoothGattClientOperation: Waiting for the previous operation to complete");
            }
        } else {
            Log.d(TAG, "executeBluetoothGattClientOperation: The queue is empty");
        }
    }

    /**
     * Finds the operation associated with the given Bluetooth device instance
     * @param bluetoothDevice The Bluetooth device instance associated with the request to find.
     * @return A BluetoothGattClientOperation instance or null if not found.
     */
    private synchronized BluetoothGattClientOperation getBluetoothGattClientOperation(final BluetoothDevice bluetoothDevice) {
        BluetoothGattClientOperation matchingBluetoothGattClientOperation = null;

        if (bluetoothDevice != null && mBluetoothGattClientOperationQueue.size() > 0) {
            for (BluetoothGattClientOperation bluetoothGattClientOperation : mBluetoothGattClientOperationQueue) {
                if (bluetoothGattClientOperation.bluetoothDevice != null
                        && bluetoothGattClientOperation.bluetoothDevice.getAddress().equals(bluetoothDevice.getAddress())) {
                    matchingBluetoothGattClientOperation = bluetoothGattClientOperation;
                    break;
                }
            }
        }

        return matchingBluetoothGattClientOperation;
    }

    /**
     * Finds the operation associated with the given BluetoothGatt instance.
     * @param bluetoothGatt The BluetoothGatt instance associated with the request to find.
     * @return A BluetoothGattClientOperation instance or null if not found.
     */
    private synchronized BluetoothGattClientOperation getBluetoothGattClientOperation(BluetoothGatt bluetoothGatt) {
        BluetoothGattClientOperation matchingBluetoothGattClientOperation = null;

        if (bluetoothGatt != null && bluetoothGatt.getDevice() != null) {
            matchingBluetoothGattClientOperation = getBluetoothGattClientOperation(bluetoothGatt.getDevice());
        }

        return matchingBluetoothGattClientOperation;
    }

    /**
     * Removes/moves the given operation from the queue and closes its BluetoothGatt instance if one exists.
     * @param bluetoothGattClientOperationToRemove The operation to remove.
     * @param executeNext If true, will execute a next operation in line.
     * @param moveToBackOfQueue If true, will not remove the operation but move it to the back of the queue.
     * @return True, if removed successfully. False, if not found.
     */
    private synchronized boolean removeBluetoothGattClientOperationFromQueue(
            final BluetoothGattClientOperation bluetoothGattClientOperationToRemove, boolean executeNext, boolean moveToBackOfQueue) {
        boolean wasRemoved = false;

        if (bluetoothGattClientOperationToRemove != null) {
            if (bluetoothGattClientOperationToRemove.bluetoothGatt != null) {
                if (bluetoothGattClientOperationToRemove.connected) {
                    bluetoothGattClientOperationToRemove.bluetoothGatt.disconnect();
                    bluetoothGattClientOperationToRemove.connected = false;
                } else {
                    bluetoothGattClientOperationToRemove.bluetoothGatt.close();
                }

                bluetoothGattClientOperationToRemove.bluetoothGatt = null;
            }
        }

        for (BluetoothGattClientOperation bluetoothGattClientOperation : mBluetoothGattClientOperationQueue) {
            if (bluetoothGattClientOperation.bluetoothDevice.getAddress().equals(
                    bluetoothGattClientOperationToRemove.bluetoothDevice.getAddress())) {
                mBluetoothGattClientOperationQueue.remove(bluetoothGattClientOperation);

                if (moveToBackOfQueue) {
                    mBluetoothGattClientOperationQueue.add(bluetoothGattClientOperation);
                } else {
                    mListener.onBluetoothGattClientOperationCountInQueueChanged(mBluetoothGattClientOperationQueue.size());
                }

                if (bluetoothGattClientOperation.bluetoothDevice != null
                        && bluetoothGattClientOperation.bluetoothDevice.getAddress() != null) {
                    Log.d(TAG, "removeBluetoothGattClientOperationFromQueue: Operation associated with device address \""
                            + bluetoothGattClientOperation.bluetoothDevice.getAddress() + "\" removed from the queue");
                } else {
                    Log.d(TAG, "removeBluetoothGattClientOperationFromQueue: Operation with request UUID \""
                            + bluetoothGattClientOperation.requestUuid + "\" removed from the queue");
                }

                wasRemoved = true;
                break;
            }
        }

        if (!wasRemoved) {
            Log.e(TAG, "removeBluetoothGattClientOperationFromQueue: Failed to remove the given BluetoothGatt instance from the queue");
        }

        if (executeNext) {
            mBluetoothGattClientOperationExecutionInProgress = false;
            executeBluetoothGattClientOperation();
        }

        return wasRemoved;
    }

    /**
     * Removes and closes the given BluetoothGattClientOperation instance from the queue.
     * @param bluetoothGattToRemove The Bluetooth GATT to remove.
     * @param executeNext If true, will execute a next operation in line.
     * @param moveToBackOfQueue If true, will not remove the operation but move it to the back of the queue.
     */
    private synchronized void removeBluetoothGattClientOperationFromQueue(
            BluetoothGatt bluetoothGattToRemove, boolean executeNext, boolean moveToBackOfQueue) {
        if (bluetoothGattToRemove != null && bluetoothGattToRemove.getDevice() != null) {
            String bluetoothMacAddress = bluetoothGattToRemove.getDevice().getAddress();

            if (bluetoothMacAddress != null) {
                for (BluetoothGattClientOperation bluetoothGattClientOperation : mBluetoothGattClientOperationQueue) {
                    String bluetoothMacAddressFromBluetoothGatt =
                            getBluetoothMacAddress(bluetoothGattClientOperation.bluetoothGatt, false);

                    if (bluetoothMacAddressFromBluetoothGatt != null
                            && bluetoothMacAddress.equals(bluetoothMacAddressFromBluetoothGatt)) {
                        removeBluetoothGattClientOperationFromQueue(bluetoothGattClientOperation, executeNext, moveToBackOfQueue);
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

    /**
     * Extracts the Bluetooth MAC address from the given BluetoothDevice instance.
     * @param bluetoothDevice A BluetoothDevice instance.
     * @param returnNonEmptyString If true, will return string "<unknown>" in case failed to extract
     *                             a non-null address.
     * @return The extracted address or "<unknown>" if either the BluetoothGatt instance or its
     * BluetoothDevice instance is null.
     */
    private String getBluetoothMacAddress(BluetoothDevice bluetoothDevice, boolean returnNonEmptyString) {
        String bluetoothMacAddress = null;

        if (bluetoothDevice != null) {
            bluetoothMacAddress = bluetoothDevice.getAddress();
        }

        if (returnNonEmptyString && bluetoothMacAddress == null) {
            bluetoothMacAddress = "<unknown>";
        }

        return bluetoothMacAddress;
    }

    /**
     * See getBluetoothMacAddress(BluetoothDevice, boolean)
     */
    private String getBluetoothMacAddress(BluetoothGatt bluetoothGatt, boolean returnNonEmptyString) {
        String bluetoothMacAddress = null;

        if (bluetoothGatt != null) {
            bluetoothMacAddress = getBluetoothMacAddress(bluetoothGatt.getDevice(), returnNonEmptyString);
        }

        if (returnNonEmptyString && bluetoothMacAddress == null) {
            bluetoothMacAddress = "<unknown>";
        }

        return bluetoothMacAddress;
    }

    /**
     * Logs the given services and their characteristics.
     * @param bluetoothGattServices A list of Bluetooth GATT services.
     */
    private void logDiscoveredServices(List<BluetoothGattService> bluetoothGattServices) {
        if (bluetoothGattServices != null) {
            if (bluetoothGattServices.size() > 0) {
                for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
                    List<BluetoothGattCharacteristic> bluetoothGattCharacteristics =
                            bluetoothGattService.getCharacteristics();

                    Log.d(TAG, "logDiscoveredServices: Bluetooth GATT service: "
                            + "Instance ID: " + bluetoothGattService.getInstanceId()
                            + ", type: " + bluetoothGattService.getType()
                            + ", UUID: " + bluetoothGattService.getUuid()
                            + ", characteristic count: " + bluetoothGattCharacteristics.size());

                    for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattCharacteristics) {
                        Log.d(TAG, "    - Bluetooth GATT characteristic: "
                                + "UUID: " + bluetoothGattCharacteristic.getUuid()
                                + ", permissions: " + bluetoothGattCharacteristic.getPermissions()
                                + ", write type: " + bluetoothGattCharacteristic.getWriteType());
                    }
                }
            } else {
                Log.d(TAG, "logDiscoveredServices: The given list contains no services");
            }
        } else {
            Log.e(TAG, "logDiscoveredServices: The given list is null");
        }
    }

    /**
     * Implements callbacks for Bluetooth GATT client.
     */
    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {
            Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            BluetoothGattClientOperation bluetoothGattClientOperation = getBluetoothGattClientOperation(bluetoothGatt);
            String bluetoothMacAddress = getBluetoothMacAddress(bluetoothGatt, true);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Connected (device address: \""
                        + bluetoothMacAddress + "\")");

                if (bluetoothGattClientOperation != null) {
                    bluetoothGattClientOperation.connected = true;
                }

                if (bluetoothGatt.discoverServices()) {
                    Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Bluetooth GATT service discovery started successfully (device address: \""
                            + bluetoothMacAddress + "\")");
                } else {
                    Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: Failed to start the Bluetooth GATT service discovery (device address: \""
                            + bluetoothMacAddress + "\")");

                    removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, true);
                }
            } else {
                // Not connected
                if (bluetoothGattClientOperation != null) {
                    if (bluetoothGattClientOperation.connected) {
                        Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Was connected (device address: \""
                                + bluetoothMacAddress + "\"), but got disconnected");
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: Got disconnected, although wasn't even connected in the first place (device address: \""
                                + bluetoothMacAddress + "\")");
                    }

                    bluetoothGattClientOperation.connected = false;
                    // One might think reconnection attempt here would work, but, alas, it does not
                } else {
                    Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Disconnected (device address: \""
                            + bluetoothMacAddress + "\"), closing BluetoothGatt instance...");
                    bluetoothGatt.close();
                }
            }

            //super.onConnectionStateChange(bluetoothGatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            //Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Status: " + status);
            String bluetoothMacAddress = getBluetoothMacAddress(bluetoothGatt, true);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Service discovery successful (device address: \""
                    + bluetoothMacAddress + "\")");

                List<BluetoothGattService> bluetoothGattServices = bluetoothGatt.getServices();
                logDiscoveredServices(bluetoothGattServices);

                BluetoothGattClientOperation bluetoothGattClientOperation = getBluetoothGattClientOperation(bluetoothGatt);
                BluetoothGattService desiredBluetoothGattService = null;
                BluetoothGattCharacteristic desiredBluetoothGattCharacteristic = null;

                for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
                    // For some reason, the UUID of the service may be different than expected
                    /*if (bluetoothGattService.getUuid().equals(mServiceUuid)) {
                        desiredBluetoothGattService = bluetoothGattService;
                        desiredBluetoothGattCharacteristic = desiredBluetoothGattCharacteristic;
                        break;
                    }*/

                    for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {
                        if (bluetoothGattClientOperation != null &&
                                bluetoothGattCharacteristic.getUuid().equals(
                                        bluetoothGattClientOperation.requestUuid)) {
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
                        Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Found the desired Bluetooth GATT characteristic (device address: \""
                                + bluetoothMacAddress + "\")");

                        desiredBluetoothGattCharacteristic.setValue(bluetoothGatt.getDevice().getAddress().getBytes());

                        if (bluetoothGatt.writeCharacteristic(desiredBluetoothGattCharacteristic)) {
                            Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Initiated write characteristic operation successfully (device address: \""
                                    + bluetoothMacAddress + "\")");
                        } else {
                            Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the writing of the Bluetooth GATT characteristic (device address: \""
                                    + bluetoothMacAddress + "\")");

                            removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, true);
                        }
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the desired Bluetooth GATT characteristic (device address: \""
                                + bluetoothMacAddress + "\")");

                        removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, false);
                    }
                } else {
                    if (bluetoothGattClientOperation != null && bluetoothGattClientOperation.requestUuid != null) {
                        Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the desired Bluetooth GATT service (device address: \""
                                + bluetoothMacAddress + "\")");
                    }

                    removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, false);
                }
            } else {
                Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Service discovery failed (device address: \""
                        + bluetoothMacAddress + "\"), got status: " + status);

                removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, true);
                //super.onServicesDiscovered(bluetoothGatt, status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicRead: Status: " + status);

            if (characteristic != null && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattCallback.onCharacteristicRead: Value: " + characteristic.getValue());
            } else {
                Log.e(TAG, "BluetoothGattCallback.onCharacteristicRead: Status not successful or the Bluetooth GATT characteristic is null");
            }

            //removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true);
            super.onCharacteristicRead(bluetoothGatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicWrite: Status: " + status);
            BluetoothGattClientOperation bluetoothGattClientOperation = getBluetoothGattClientOperation(bluetoothGatt);

            if (bluetoothGattClientOperation != null &&
                    bluetoothGattCharacteristic.getUuid().equals(
                            bluetoothGattClientOperation.requestUuid)) {
                String requestId = PeerAdvertisementFactory.parseRequestIdFromUuid(bluetoothGattClientOperation.requestUuid);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mListener.onProvideBluetoothMacAddressResult(requestId, true);
                } else {
                    mListener.onProvideBluetoothMacAddressResult(requestId, false);
                }

                // We are done
                removeBluetoothGattClientOperationFromQueue(bluetoothGatt, true, true);
            }

            //super.onCharacteristicWrite(bluetoothGatt, bluetoothGattCharacteristic, status);
        }
    }

    /**
     * Implements callbacks for Bluetooth GATT server.
     */
    private class MyBluetoothGattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            //Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: Service with UUID \""
                        + service.getUuid() + "\" added successfully");
                mBluetoothMacAddressRequestServerStarted = true;
            } else {
                Log.e(TAG, "BluetoothGattServerCallback.onServiceAdded: Failed to add service with UUID \""
                        + service.getUuid() + "\", got status: " + status);
            }

            //super.onServiceAdded(status, service);
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            //Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            String bluetoothMacAddress = getBluetoothMacAddress(device, true);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Connected (device address: \""
                        + bluetoothMacAddress + "\")");
                //Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Device with address \""
                //        + device.getAddress()+ "\" connected");
            } else {
                Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Disconnected (device address: \""
                        + bluetoothMacAddress + "\")");
            }

            //super.onConnectionStateChange(device, status, newState);
        }

        @Override
        public void onCharacteristicReadRequest(
                BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "BluetoothGattServerCallback.onCharacteristicReadRequest: "
                    + "Device address: " + device.getAddress()
                    + ", request ID: " + requestId
                    + ", offset: " + offset
                    + ", characteristic UUID: " + characteristic.getUuid());

            if (mBluetoothGattServer != null && characteristic.getUuid().equals(mProvideBluetoothMacAddressServerUuid)) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN.getBytes());
            } else {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
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
            } else {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Log.d(TAG, "BluetoothGattServerCallback.onDescriptorReadRequest: Request ID: " + requestId + ", offset: " + offset);
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "BluetoothGattServerCallback.onDescriptorWriteRequest: Request ID: " + requestId + ", prepared write: " + preparedWrite + ", response needed: " + responseNeeded + ", offset: " + offset + ", value: " + value);
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Log.d(TAG, "BluetoothGattServerCallback.onExecuteWrite: Request ID: " + requestId + ", execute: " + execute);
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "BluetoothGattServerCallback.onMtuChanged: MTU: " + mtu);
            super.onMtuChanged(device, mtu);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.d(TAG, "BluetoothGattServerCallback.onNotificationSent: Status: " + status);
            super.onNotificationSent(device, status);
        }
    }
}
