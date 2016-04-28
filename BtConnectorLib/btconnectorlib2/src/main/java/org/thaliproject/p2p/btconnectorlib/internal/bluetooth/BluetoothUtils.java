/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
    public static final String SIMPLE_HANDSHAKE_MESSAGE_AS_STRING = "thali_handshake";
    public static final byte[] SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY =
            SIMPLE_HANDSHAKE_MESSAGE_AS_STRING.getBytes(StandardCharsets.UTF_8);
    private static final String MARSHMALLOW_FAKE_MAC_ADDRESS = "02:00:00:00:00:00";
    private static final String UPPER_CASE_HEX_REGEXP_CONDITION = "-?[0-9A-F]+";
    private static final String METHOD_NAME_FOR_CREATING_SECURE_RFCOMM_SOCKET = "createRfcommSocket";
    private static final String METHOD_NAME_FOR_CREATING_INSECURE_RFCOMM_SOCKET = "createInsecureRfcommSocket";
    private static final int MAX_ALTERNATIVE_CHANNEL = 30;
    private static int mAlternativeChannel = 0;

    /**
     * Checks if the given Bluetooth MAC address is unknown (as in not set/missing).
     *
     * In addition, will check for false addresses introduced in Android 6.0;
     * From http://developer.android.com/about/versions/marshmallow/android-6.0-changes.html:
     *
     * "To provide users with greater data protection, starting in this release, Android removes
     * programmatic access to the device’s local hardware identifier for apps using the Wi-Fi and
     * Bluetooth APIs. The WifiInfo.getMacAddress() and the BluetoothAdapter.getAddress() methods
     * now return a constant value of 02:00:00:00:00:00."
     *
     * @param bluetoothMacAddress The Bluetooth MAC address to check.
     * @return True, if the Bluetooth MAC address is unknown.
     */
    public static boolean isBluetoothMacAddressUnknown(String bluetoothMacAddress) {
        return (bluetoothMacAddress == null
                || bluetoothMacAddress.equals(MARSHMALLOW_FAKE_MAC_ADDRESS)
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
     * Extracts the Bluetooth MAC address of the peer from the given Bluetooth socket instance.
     * @param bluetoothSocket The Bluetooth socket.
     * @return The Bluetooth MAC address or null in case of an error.
     */
    public static String getBluetoothMacAddressFromSocket(final BluetoothSocket bluetoothSocket) {
        String bluetoothMacAddress = null;

        if (bluetoothSocket != null && bluetoothSocket.getRemoteDevice() != null) {
            bluetoothMacAddress = bluetoothSocket.getRemoteDevice().getAddress();
        } else {
            // The whole purpose of this method is to have exception free, quick way to get the
            // Bluetooth MAC address. Thus, do not throw an exception here.
            Log.e(TAG, "getBluetoothMacAddressFromSocket: Either the socket or its remote device is null");
        }

        return bluetoothMacAddress;
    }

    /**
     * Checks the validity of the received handshake message.
     * @param handshakeMessage The received handshake message as a byte array.
     * @param handshakeMessageLength The length of the handshake message.
     * @param bluetoothSocketOfSender The Bluetooth socket of the sender.
     * @return The resolved peer properties of the sender, if the handshake was valid. Null otherwise.
     */
    public static PeerProperties validateReceivedHandshakeMessage(
            byte[] handshakeMessage, int handshakeMessageLength, BluetoothSocket bluetoothSocketOfSender) {
        String handshakeMessageAsString = new String(handshakeMessage, StandardCharsets.UTF_8);
        PeerProperties peerProperties = null;
        boolean receivedHandshakeMessageValidated = false;

        if (!handshakeMessageAsString.isEmpty()) {
            if (handshakeMessageLength == SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY.length) {
                // This must be the simple handshake message
                try {
                    handshakeMessageAsString = handshakeMessageAsString.substring(0, SIMPLE_HANDSHAKE_MESSAGE_AS_STRING.length());
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "validateReceivedHandshakeMessage: " + e.getMessage(), e);
                }

                if (handshakeMessageAsString.equals(SIMPLE_HANDSHAKE_MESSAGE_AS_STRING)) {
                    String bluetoothMacAddress = getBluetoothMacAddressFromSocket(bluetoothSocketOfSender);

                    if (isValidBluetoothMacAddress(bluetoothMacAddress)) {
                        receivedHandshakeMessageValidated = true;
                        peerProperties = new PeerProperties(bluetoothMacAddress);
                    }
                }
            } else {
                // Long handshake message with peer name and Bluetooth MAC address
                peerProperties = new PeerProperties();

                try {
                    receivedHandshakeMessageValidated =
                            AbstractBluetoothConnectivityAgent.getPropertiesFromIdentityString(
                                    handshakeMessageAsString, peerProperties);
                } catch (JSONException e) {
                    Log.e(TAG, "validateReceivedHandshakeMessage: Failed to resolve peer properties: "
                            + e.getMessage(), e);
                }

                if (receivedHandshakeMessageValidated) {
                    String bluetoothMacAddress =
                            BluetoothUtils.getBluetoothMacAddressFromSocket(bluetoothSocketOfSender);

                    if (bluetoothMacAddress == null
                            || !bluetoothMacAddress.equals(peerProperties.getBluetoothMacAddress())) {
                        Log.e(TAG, "validateReceivedHandshakeMessage: Bluetooth MAC address mismatch: Got \""
                                + peerProperties.getBluetoothMacAddress()
                                + "\", but was expecting \"" + bluetoothMacAddress + "\"");

                        receivedHandshakeMessageValidated = false;
                    }
                }
            }
        }

        return receivedHandshakeMessageValidated ? peerProperties : null;
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
