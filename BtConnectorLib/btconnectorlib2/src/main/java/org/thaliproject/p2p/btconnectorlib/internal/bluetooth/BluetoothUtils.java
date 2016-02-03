/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * General Bluetooth utils.
 */
public class BluetoothUtils {
    private static final String TAG = BluetoothUtils.class.getName();
    public static final String BLUETOOTH_ADDRESS_SEPARATOR = ":";
    public static final int BLUETOOTH_ADDRESS_BYTE_COUNT = 6;
    public static final int BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MIN = 11; // E.g. "0:0:0:0:0:0"
    public static final int BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MAX = 17; // E.g. "01:23:45:67:89:AB"
    private static final String UPPER_CASE_HEX_REGEXP_CONDITION = "-?[0-9A-F]+";
    private static final String METHOD_NAME_FOR_CREATING_SECURE_RFCOMM_SOCKET = "createRfcommSocket";
    private static final String METHOD_NAME_FOR_CREATING_INSECURE_RFCOMM_SOCKET = "createInsecureRfcommSocket";
    private static final int MAX_ALTERNATIVE_CHANNEL = 30;
    private static int mAlternativeChannel = 0;

    /**
     * Checks if the given Bluetooth MAC address is unknown (as in not set/missing).
     * @param bluetoothMacAddress The Bluetooth MAC address to check.
     * @return True, if the Bluetooth MAC address is unknown.
     */
    public static boolean isBluetoothMacAddressUnknown(String bluetoothMacAddress) {
        return (bluetoothMacAddress == null
                || bluetoothMacAddress.equals(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN));
    }

    /**
     * Checks whether the given Bluetooth MAC address has the proper form or not.
     *
     * A valid Bluetooth MAC address has form of: 01:23:45:67:89:AB
     * Note that the possible alphabets in the string have to be upper case.
     *
     * @param bluetoothMacAddress The Bluetooth MAC address to validate.
     * @return True, if the address is valid. False otherwise.
     */
    public static boolean isValidBluetoothMacAddress(String bluetoothMacAddress) {
        boolean isValid = false;

        if (!isBluetoothMacAddressUnknown(bluetoothMacAddress)
                && bluetoothMacAddress.length() >= BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MIN
                && bluetoothMacAddress.length() <= BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MAX) {
            String[] bytesAsHexStringArray = bluetoothMacAddress.split(BLUETOOTH_ADDRESS_SEPARATOR);

            if (bytesAsHexStringArray.length == BLUETOOTH_ADDRESS_BYTE_COUNT) {
                boolean allBytesAreValid = true;

                for (String byteAsHexString : bytesAsHexStringArray) {
                    if (byteAsHexString.length() == 0
                            || byteAsHexString.length() > 2
                            || !byteAsHexString.matches(UPPER_CASE_HEX_REGEXP_CONDITION)) {
                        allBytesAreValid = false;
                        break;
                    }
                }

                isValid = allBytesAreValid;
            }
        }

        if (!isValid && bluetoothMacAddress != null) {
            Log.e(TAG, "isValidBluetoothMacAddress: Not a valid Bluetooth MAC address: " + bluetoothMacAddress);
        }

        return isValid;
    }

    /**
     * @return The alternative RFCOMM channel/L2CAP psm used previously.
     */
    public static int getPreviouslyUsedAlternativeChannelOrPort() {
        return mAlternativeChannel;
    }

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
     * Creates a new Bluetooth socket with the given service record UUID and the given channel/port.
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
