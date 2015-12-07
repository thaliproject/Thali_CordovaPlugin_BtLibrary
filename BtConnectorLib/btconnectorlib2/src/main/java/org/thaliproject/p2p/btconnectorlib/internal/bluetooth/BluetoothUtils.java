/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * General Bluetooth utils.
 */
public class BluetoothUtils {
    private static final String TAG = BluetoothUtils.class.getName();
    private static final String METHOD_NAME_FOR_CREATING_SECURE_RFCOMM_SOCKET = "createRfcommSocket";
    private static final String METHOD_NAME_FOR_CREATING_INSECURE_RFCOMM_SOCKET = "createInsecureRfcommSocket";
    private static final int MAX_ALTERNATIVE_CHANNEL = 30;
    private static int mAlternativeChannel = 0;

    /**
     * Sets the next alternative RFCOMM channel or L2CAP psm to use.
     * @param channelOrPort The next alternative RFCOMM channel or L2CAP psm to use.
     */
    public static void setNextAlternativeChannelOrPort(int channelOrPort) {
        if (channelOrPort >= 0 && channelOrPort < MAX_ALTERNATIVE_CHANNEL) {
            mAlternativeChannel = channelOrPort - 1;
        }
    }

    /**
     *
     * @param bluetoothDevice The Bluetooth device.
     * @param serviceRecordUuid The service record UUID.
     * @param channelOrPort The RFCOMM channel or L2CAP psm to use.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return A new Bluetooth socket with the specified channel/port or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocketToServiceRecord(
            BluetoothDevice bluetoothDevice, UUID serviceRecordUuid, int channelOrPort, boolean secure) {
        Constructor[] bluetoothSocketConstructors = BluetoothSocket.class.getDeclaredConstructors();
        Constructor bluetoothSocketConstructor = null;

        for (Constructor constructor : bluetoothSocketConstructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            boolean takesBluetoothDevice = false;
            boolean takesParcelUuid = false;

            for (Class<?> parameterType : parameterTypes) {
                if (parameterType.equals(BluetoothDevice.class)) {
                    takesBluetoothDevice = true;
                } else if (parameterType.equals(ParcelUuid.class)) {
                    takesParcelUuid = true;
                }
            }

            if (takesBluetoothDevice && takesParcelUuid) {
                // We found the right constructor
                bluetoothSocketConstructor = constructor;
                break;
            }
        }

        // This is the constructor we should now have:
        // BluetoothSocket(int type, int fd, boolean auth, boolean encrypt, BluetoothDevice device,
        //      int port, ParcelUuid uuid) throws IOException

        // Create the parameters for the constructor
        Object[] parameters = new Object[] {
                Integer.valueOf(1), // BluetoothSocket.TYPE_RFCOMM
                Integer.valueOf(-1),
                Boolean.valueOf(secure),
                Boolean.valueOf(secure),
                bluetoothDevice,
                Integer.valueOf(channelOrPort),
                new ParcelUuid(serviceRecordUuid)
        };

        bluetoothSocketConstructor.setAccessible(true);
        BluetoothSocket bluetoothSocket = null;

        try {
            bluetoothSocket = (BluetoothSocket)bluetoothSocketConstructor.newInstance(parameters);
            Log.d(TAG, "createBluetoothSocketToServiceRecord: Socket created with channel/port " + channelOrPort);
        } catch (Exception e) {
            Log.e(TAG, "createBluetoothSocketToServiceRecord: Failed to create a new Bluetooth socket instance: " + e.getMessage(), e);
        }

        return bluetoothSocket;
    }

    /**
     * Creates a new Bluetooth socket with the given service record UUID using a rotating channel/port.
     * @param bluetoothDevice The Bluetooth device.
     * @param serviceRecordUuid The service record UUID.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return A new Bluetooth socket with the specified channel/port or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocketToServiceRecordWithNextPort(
            BluetoothDevice bluetoothDevice, UUID serviceRecordUuid, boolean secure) {
        if (mAlternativeChannel >= MAX_ALTERNATIVE_CHANNEL) {
            mAlternativeChannel = 0;
        }

        return createBluetoothSocketToServiceRecord(bluetoothDevice, serviceRecordUuid, ++mAlternativeChannel, secure);
    }

    /**
     * Creates a new Bluetooth socket based on the given one using the given channel.
     * @param originalBluetoothSocket The original Bluetooth socket.
     * @param channelOrPort The RFCOMM channel or L2CAP psm to use.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return A new Bluetooth socket with the specified channel/port or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocket(
            BluetoothSocket originalBluetoothSocket, int channelOrPort, boolean secure) {
        Log.d(TAG, "createBluetoothSocketWithSpecifiedChannel: Channel/port: " + channelOrPort + ", secure: " + secure);
        Class<?> bluetoothDeviceClass = originalBluetoothSocket.getRemoteDevice().getClass();
        Class<?>[] parameterTypes = new Class<?>[] { Integer.TYPE };

        String methodName = secure
                ? METHOD_NAME_FOR_CREATING_SECURE_RFCOMM_SOCKET
                : METHOD_NAME_FOR_CREATING_INSECURE_RFCOMM_SOCKET;

        BluetoothSocket newSocket = null;

        try {
            Method createSocketMethod = bluetoothDeviceClass.getMethod(methodName, parameterTypes);
            Object[] parameters = new Object[] { Integer.valueOf(channelOrPort) };
            newSocket = (BluetoothSocket) createSocketMethod.invoke(originalBluetoothSocket.getRemoteDevice(), parameters);
        } catch (Exception e) {
            Log.e(TAG, "createBluetoothSocketWithSpecifiedChannel: Failed to create a new Bluetooth socket: " + e.getMessage(), e);
        }

        return newSocket;
    }

    /**
     * Creates a new Bluetooth socket based on the given one using a rotating channel/port.
     * @param originalBluetoothSocket The original Bluetooth socket.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return A new Bluetooth socket with the specified channel/port or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocketWithNextChannel(
            BluetoothSocket originalBluetoothSocket, boolean secure) {
        if (mAlternativeChannel >= MAX_ALTERNATIVE_CHANNEL) {
            mAlternativeChannel = 0;
        }

        return createBluetoothSocket(originalBluetoothSocket, ++mAlternativeChannel, secure);
    }
}
