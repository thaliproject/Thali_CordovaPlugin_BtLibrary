/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.lang.reflect.Method;

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
     * Creates a new Bluetooth socket based on the given one using the given channel.
     * @param originalBluetoothSocket The original Bluetooth socket.
     * @param channel The new channel for the Bluetooth socket.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return The new Bluetooth socket with the specified channel or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocketWithSpecifiedChannel(
            BluetoothSocket originalBluetoothSocket, int channel, boolean secure) {
        Log.d(TAG, "createBluetoothSocketWithSpecifiedChannel: Channel: " + channel + ", secure: " + secure);
        Class<?> bluetoothDeviceClass = originalBluetoothSocket.getRemoteDevice().getClass();
        Class<?>[] parameterTypes = new Class<?>[] { Integer.TYPE };

        String methodName = secure
                ? METHOD_NAME_FOR_CREATING_SECURE_RFCOMM_SOCKET
                : METHOD_NAME_FOR_CREATING_INSECURE_RFCOMM_SOCKET;

        BluetoothSocket newSocket = null;

        try {
            Method createSocketMethod = bluetoothDeviceClass.getMethod(methodName, parameterTypes);
            Object[] parameters = new Object[] { Integer.valueOf(channel) };
            newSocket = (BluetoothSocket) createSocketMethod.invoke(originalBluetoothSocket.getRemoteDevice(), parameters);
        } catch (Exception e) {
            Log.e(TAG, "createBluetoothSocketWithSpecifiedChannel: Failed to create a new Bluetooth socket: " + e.getMessage(), e);
        }

        return newSocket;
    }

    /**
     * Creates a new Bluetooth socket based on the given one using a rotating channel.
     * @param originalBluetoothSocket The original Bluetooth socket.
     * @param secure If true, will try to create a secure RFCOMM socket. If false, will try to create an insecure one.
     * @return The new Bluetooth socket with the specified channel or null in case of a failure.
     */
    public static BluetoothSocket createBluetoothSocketWithNextChannel(
            BluetoothSocket originalBluetoothSocket, boolean secure) {
        if (mAlternativeChannel >= MAX_ALTERNATIVE_CHANNEL) {
            mAlternativeChannel = 0;
        }

        return createBluetoothSocketWithSpecifiedChannel(originalBluetoothSocket, ++mAlternativeChannel, secure);
    }
}
