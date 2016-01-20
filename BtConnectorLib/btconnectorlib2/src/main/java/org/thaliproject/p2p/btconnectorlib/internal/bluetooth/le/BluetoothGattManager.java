/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

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

        void onBluetoothMacAddressResolved(String bluetoothMacAddress);
    }

    private static final String TAG = BluetoothGattManager.class.getName();
    private final Listener mListener;
    private final Context mContext;
    private final UUID mServiceUuid;
    private final UUID mProvideBluetoothMacAddressUuid;
    private BluetoothGattServer mBluetoothGattServer = null;
    private CopyOnWriteArrayList<BluetoothGatt> mBluetoothGattList = new CopyOnWriteArrayList<>();
    private String mRequestIdForBluetoothGattService = null;
    private boolean mBluetoothMacAddressRequestServerStarted = false;

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
        mProvideBluetoothMacAddressUuid = PeerAdvertisementFactory.createProvideBluetoothMacAddressRequestUuid(mServiceUuid);
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

            if (getIsBluetoothMacAddressRequestServerStarted()) {
                stopBluetoothMacAddressRequestServer();
            }

            mRequestIdForBluetoothGattService = requestId;
            final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

            if (bluetoothManager != null) {
                //new Handler(mContext.getMainLooper()).post(new Runnable() {
                    //@Override
                    //public void run() {

                mBluetoothGattServer = bluetoothManager.openGattServer(mContext, new MyBluetoothGattServerCallback());

                if (mBluetoothGattServer != null) {
                    Log.d(TAG, "startBluetoothMacAddressRequestServer: Open Bluetooth GATT server OK");

                    BluetoothGattService bluetoothGattService =
                            createBluetoothGattService(mRequestIdForBluetoothGattService);

                    if (mBluetoothGattServer.addService(bluetoothGattService)) {
                        Log.i(TAG, "startBluetoothMacAddressRequestServer: Add service, with request ID \""
                                + mRequestIdForBluetoothGattService + "\", operation initiated successfully");
                    } else {
                        Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to add the Bluetooth GATT service");
                        stopBluetoothMacAddressRequestServer();
                    }
                } else {
                    Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to open the Bluetooth GATT server");
                    stopBluetoothMacAddressRequestServer();
                }

                    //}
                //});
            } else {
                Log.e(TAG, "startBluetoothMacAddressRequestServer: Failed to obtain the Bluetooth manager (android.bluetooth.BluetoothManager) instance");
            }
        }
    }

    /**
     *
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
     * Initiates the operation to read the Bluetooth GATT characteristic containing the request ID
     * from a GATT service of the given Bluetooth device.
     * @param bluetoothDevice A Bluetooth device instance.
     */
    public void readRequestIdFromBluetoothMacAddressRequestService(final BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice != null) {
            new Handler(mContext.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    BluetoothGatt bluetoothGatt = bluetoothDevice.connectGatt(mContext, false, new MyBluetoothGattCallback());

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

    /**
     *
     * @param requestId
     * @return
     */
    private BluetoothGattService createBluetoothGattService(String requestId) {
        boolean createdSuccessfully = false;

        BluetoothGattService bluetoothGattService =
                new BluetoothGattService(mServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(
                mProvideBluetoothMacAddressUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        if (bluetoothGattCharacteristic.setValue(requestId)) {
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
     *
     * @param bluetoothGattToRemove
     * @param close
     */
    private void removeBluetoothGattFromList(BluetoothGatt bluetoothGattToRemove, boolean close) {
        boolean wasRemoved = false;

        if (bluetoothGattToRemove != null && bluetoothGattToRemove.getDevice() != null) {
            String bluetoothMacAddress = bluetoothGattToRemove.getDevice().getAddress();

            if (bluetoothMacAddress != null) {
                for (BluetoothGatt bluetoothGatt : mBluetoothGattList) {
                    if (bluetoothGatt != null
                            && bluetoothGatt.getDevice() != null
                            && bluetoothMacAddress.equals(bluetoothGatt.getDevice().getAddress())) {
                        mBluetoothGattList.remove(bluetoothGatt);
                        wasRemoved = true;
                        Log.d(TAG, "removeBluetoothGattFromList: Bluetooth GATT associated with device address \""
                            + bluetoothMacAddress + "\" removed");
                    }
                }
            }

            if (close) {
                bluetoothGattToRemove.close();
                Log.d(TAG, "removeBluetoothGattFromList: Bluetooth GATT associated with device address \""
                        + bluetoothMacAddress + "\" closed");
            }
        }

        if (!wasRemoved) {
            Log.e(TAG, "removeBluetoothGattFromList: Failed to remove the given BluetoothGatt instance from the list");
        }
    }

    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {
            //Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            super.onConnectionStateChange(bluetoothGatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Connected");
                if (bluetoothGatt.discoverServices()) {
                    Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Bluetooth GATT service discovery started successfully");
                } else {
                    Log.e(TAG, "BluetoothGattCallback.onConnectionStateChange: Failed to start the Bluetooth GATT service discovery");
                    removeBluetoothGattFromList(bluetoothGatt, true);
                }
            } else {
                Log.d(TAG, "BluetoothGattCallback.onConnectionStateChange: Not connected");
                removeBluetoothGattFromList(bluetoothGatt, true);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            //Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Status: " + status);
            super.onServicesDiscovered(bluetoothGatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService desiredBluetoothGattService = null; //bluetoothGatt.getService(mServiceUuid);

                for (BluetoothGattService bluetoothGattService : bluetoothGatt.getServices()) {
                    Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Found service with UUID " + bluetoothGattService.getUuid());

                    if (bluetoothGattService.getUuid().compareTo(mServiceUuid) == 0) {
                        desiredBluetoothGattService = bluetoothGattService;
                        break;
                    }
                }

                if (desiredBluetoothGattService != null) {
                    final BluetoothGattCharacteristic bluetoothGattCharacteristic =
                            desiredBluetoothGattService.getCharacteristic(mProvideBluetoothMacAddressUuid);

                    if (bluetoothGattCharacteristic != null) {
                        Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Got Bluetooth GATT characteristic");

                        /*if (bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic)) {
                            Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Initiated read characteristic operation successfully");
                        } else {
                            Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the reading of the Bluetooth GATT characteristic");
                            removeBluetoothGattFromList(bluetoothGatt, true);
                        }*/

                        bluetoothGattCharacteristic.setValue(bluetoothGatt.getDevice().getAddress().getBytes());

                        if (bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic)) {
                            Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Initiated write characteristic operation successfully");
                        } else {
                            Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to initiate the writing of the Bluetooth GATT characteristic");
                            removeBluetoothGattFromList(bluetoothGatt, true);
                        }
                    } else {
                        Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the desired Bluetooth GATT characteristic");
                        removeBluetoothGattFromList(bluetoothGatt, true);
                    }
                } else {
                    Log.d(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to obtain the Bluetooth GATT service");
                    removeBluetoothGattFromList(bluetoothGatt, true);
                }
            } else {
                Log.e(TAG, "BluetoothGattCallback.onServicesDiscovered: Failed to discover services, got status: " + status);
                removeBluetoothGattFromList(bluetoothGatt, true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicRead: Status: " + status);
            super.onCharacteristicRead(bluetoothGatt, characteristic, status);

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

            removeBluetoothGattFromList(bluetoothGatt, true);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "BluetoothGattCallback.onCharacteristicWrite: Status: " + status);
        }
    }

    private class MyBluetoothGattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            //Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGattServerCallback.onServiceAdded: SUCCESS");
                mBluetoothMacAddressRequestServerStarted = true;
            } else {
                Log.e(TAG, "BluetoothGattServerCallback.onServiceAdded: Failed to add service, got status: " + status);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            //Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Status: " + status + ", new state: " + newState);
            super.onConnectionStateChange(device, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothGattServerCallback.onConnectionStateChange: Connected");
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

            if (mBluetoothGattServer != null && characteristic.getUuid().equals(mProvideBluetoothMacAddressUuid)) {
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

            if (characteristic.getUuid().equals(mProvideBluetoothMacAddressUuid)) {
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
