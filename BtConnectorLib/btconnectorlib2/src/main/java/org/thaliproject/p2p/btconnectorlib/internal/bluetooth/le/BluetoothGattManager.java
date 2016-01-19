/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Bluetooth GATT services.
 */
public class BluetoothGattManager {
    public interface Listener {
        /**
         * Called when the request ID is read successfully.
         * @param requestId The request ID.
         * @param bluetoothDevice The Bluetooth device who we read the request ID from.
         */
        void onProvideBluetoothMacAddressRequestIdRead(String requestId, BluetoothDevice bluetoothDevice);
    }

    private static final String TAG = BluetoothGattManager.class.getName();
    private final Listener mListener;
    private final Context mContext;
    private final UUID mServiceUuid;
    private final MyBluetoothGattCallback mBluetoothGattCallback;
    private final MyBluetoothGattServerCallback mBluetoothGattServerCallback;
    private BluetoothGattServer mBluetoothGattServer = null;
    private CopyOnWriteArrayList<BluetoothGatt> mBluetoothGattList = new CopyOnWriteArrayList<>();
    private String mPreviouslyAddedRequestId = null;

    /**
     * Constructor.
     * @param listener The listener.
     * @param context The application context.
     * @param serviceUuid The service UUID.
     */
    public BluetoothGattManager(Listener listener, Context context, UUID serviceUuid) {
        mListener = listener;
        mContext = context;
        mServiceUuid = serviceUuid;
        mBluetoothGattCallback = new MyBluetoothGattCallback();
        mBluetoothGattServerCallback = new MyBluetoothGattServerCallback();
    }

    /**
     * Adds or updates a GATT service with the given request UUID for Bluetooth MAC address request.
     * @param requestId The request ID.
     * @return True, if success. False otherwise.
     */
    public boolean addBluetoothMacAddressRequestService(String requestId) {
        if (requestId != null &&
                (mPreviouslyAddedRequestId == null
                        || !mPreviouslyAddedRequestId.equals(requestId))) {
            BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

            if (bluetoothManager != null) {
                mBluetoothGattServer = bluetoothManager.openGattServer(mContext, mBluetoothGattServerCallback);

                BluetoothGattService bluetoothGattService =
                        new BluetoothGattService(mServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

                BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(
                        mServiceUuid,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ);

                if (bluetoothGattCharacteristic.setValue(requestId)) {
                    if (bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic)) {
                        if (mBluetoothGattServer.addService(bluetoothGattService)) {
                            mPreviouslyAddedRequestId = requestId;
                            Log.i(TAG, "addBluetoothMacAddressRequestService: Service with request ID \""
                                    + mPreviouslyAddedRequestId + "\" added successfully");
                        } else {
                            Log.e(TAG, "addBluetoothMacAddressRequestService: Failed to add the GATT service");
                        }
                    } else {
                        Log.e(TAG, "addBluetoothMacAddressRequestService: Failed to add the GATT characteristic to the GATT service");
                    }
                } else {
                    Log.e(TAG, "addBluetoothMacAddressRequestService: Failed to set the request ID for the GATT characteristic");
                }
            } else {
                Log.e(TAG, "addBluetoothMacAddressRequestService: Failed to obtain the Bluetooth manager (android.bluetooth.BluetoothManager) instance");
            }
        }

        return (mPreviouslyAddedRequestId != null
                && requestId != null
                && mPreviouslyAddedRequestId.equals(requestId));
    }

    /**
     * Initiates the operation to read the Bluetooth GATT characteristic containing the request ID
     * from a GATT service of the given Bluetooth device.
     * @param bluetoothDevice A Bluetooth device instance.
     */
    public void readRequestIdFromBluetoothMacAddressRequestService(final BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice != null) {
            new Handler(mContext.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    BluetoothGatt bluetoothGatt = bluetoothDevice.connectGatt(mContext, false, mBluetoothGattCallback);

                    if (bluetoothGatt != null) {
                        Log.i(TAG, "readRequestIdFromBluetoothMacAddressRequestService: Connected to Bluetooth GATT (device address " + bluetoothDevice.getAddress() + ")");
                        mBluetoothGattList.add(bluetoothGatt);
                    } else {
                        Log.d(TAG, "readRequestIdFromBluetoothMacAddressRequestService: Failed to connect to Bluetooth GATT (device address " + bluetoothDevice.getAddress() + ")");
                        bluetoothGatt.close();
                    }
                }
            });
        }
    }

    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {
            //Log.i(TAG, "BluetoothGattCallback.onConnectionStateChange: GATT: "
            //        + bluetoothGatt + ", status: " + status + ", new state: " + newState);

            super.onConnectionStateChange(bluetoothGatt, status, newState);

            if (bluetoothGatt != null) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    new Handler(mContext.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (bluetoothGatt.discoverServices()) {
                                Log.i(TAG, "BluetoothGattCallback.onConnectionStateChange: Bluetooth GATT service discovery started successfully");
                            } else {
                                Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: Failed to start the Bluetooth GATT service discovery");
                                mBluetoothGattList.remove(bluetoothGatt);
                                bluetoothGatt.close();
                            }
                        }
                    });
                } else {
                    Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Not connected");
                }
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            Log.i(TAG, "BluetoothGattCallback.onServicesDiscovered: Bluetooth GATT: " + bluetoothGatt + ", status: " + status);
            super.onServicesDiscovered(bluetoothGatt, status);

            if (bluetoothGatt != null) {
                boolean closeAndRemoveBluetoothGatt = false;
                BluetoothGattService bluetoothGattService = bluetoothGatt.getService(mServiceUuid);

                if (bluetoothGattService != null) {
                    final BluetoothGattCharacteristic bluetoothGattCharacteristic =
                            bluetoothGattService.getCharacteristic(mServiceUuid);

                    if (bluetoothGattCharacteristic != null) {
                        new Handler(mContext.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (!bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic)) {
                                    Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the reading of the Bluetooth GATT characteristic");
                                    mBluetoothGattList.remove(bluetoothGatt);
                                    bluetoothGatt.close();
                                }
                            };
                        });
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the Bluetooth GATT characteristic");
                        closeAndRemoveBluetoothGatt = true;
                    }
                } else {
                    Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the Bluetooth GATT service");
                    closeAndRemoveBluetoothGatt = true;
                }

                if (closeAndRemoveBluetoothGatt) {
                    mBluetoothGattList.remove(bluetoothGatt);
                    bluetoothGatt.close();
                }
            } else {
                Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: The Bluetooth GATT instance is null");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "BluetoothGattCallback.onCharacteristicRead: GATT: " + bluetoothGatt + ", GATT characteristic: " + characteristic + ", status: " + status);
            super.onCharacteristicRead(bluetoothGatt, characteristic, status);

            if (bluetoothGatt != null) {
                if (characteristic != null && status == BluetoothGatt.GATT_SUCCESS) {
                    String requestId = characteristic.getStringValue(0);

                    if (requestId != null) {
                        mListener.onProvideBluetoothMacAddressRequestIdRead(requestId, bluetoothGatt.getDevice());
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onCharacteristicRead: Got null for request ID");
                    }
                } else {
                    Log.e(TAG, "BluetoothGattCallback.onCharacteristicRead: Status not successful or the Bluetooth GATT characteristic is null");
                }

                mBluetoothGattList.remove(bluetoothGatt);
                bluetoothGatt.close();
            } else {
                Log.e(TAG, "BluetoothGattCallback.onCharacteristicRead: The Bluetooth GATT instance is null");
            }
        }
    }

    private class MyBluetoothGattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.i(TAG, "onServiceAdded: Status: " + status + ", service: " + service);
        }
    }
}
