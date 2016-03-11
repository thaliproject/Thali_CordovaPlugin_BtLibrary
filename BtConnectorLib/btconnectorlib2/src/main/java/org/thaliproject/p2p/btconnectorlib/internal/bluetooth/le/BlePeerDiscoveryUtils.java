/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

/**
 * Contains utility methods supporting Bluetooth LE peer discovery.
 */
@TargetApi(21)
class BlePeerDiscoveryUtils {
    /**
     * Container class for properties parsed from an advertisement.
     */
    public static class ParsedAdvertisement {
        UUID uuid = null;
        String provideBluetoothMacAddressRequestId = null;
        String bluetoothMacAddress = null;
    }

    private static final String TAG = BlePeerDiscoveryUtils.class.getName();

    public static final int ADVERTISEMENT_BYTE_COUNT = 24;
    private static final int UUID_LENGTH_IN_BYTES = 16;
    private static final String BLUETOOTH_ADDRESS_SEPARATOR = ":";
    private static final String SERVICE_UUID_MASK_AS_STRING = "11111111-1111-1111-1110-000000000000";

    private static Random mRandom = null;

    /**
     * Creates a new scan filter based on the given arguments.
     * @param serviceUuid The service UUID for the scan filter. Use null to not set.
     * @param useManufacturerId If true, will add the manufacturer ID to the filter properties.
     * @return A newly created scan filter or null in case of a failure.
     */
    public static ScanFilter createScanFilter(UUID serviceUuid, boolean useManufacturerId) {
        Log.d(TAG, "createScanFilter: "
                + ((serviceUuid != null) ? "Service UUID: \"" + serviceUuid.toString() + "\"" : "No service UUID")
                + ", use manufacturer ID: " + useManufacturerId);

        ScanFilter scanFilter = null;
        ScanFilter.Builder builder = new ScanFilter.Builder();

        try {
            if (useManufacturerId) {
                builder.setManufacturerData(PeerAdvertisementFactory.MANUFACTURER_ID, null);
            }

            if (serviceUuid != null) {
                ParcelUuid uuidMask = ParcelUuid.fromString(SERVICE_UUID_MASK_AS_STRING);
                builder.setServiceUuid(new ParcelUuid(serviceUuid), uuidMask);
            }

            scanFilter = builder.build();
            Log.v(TAG, "createScanFilter: " + scanFilter.toString());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "createScanFilter: " + e.getMessage(), e);
        }

        return scanFilter;
    }

    /**
     * Parses the given service data.
     * @param serviceData The service data. Expected contain a "0" byte followed by the six bytes
     *                    consisting of the Bluetooth MAC address.
     * @return A newly created ParsedAdvertisement instance, containing at least the Bluetooth MAC
     * address, or null in case the parsing failed.
     */
    public static ParsedAdvertisement parseServiceData(byte[] serviceData) {
        ParsedAdvertisement parsedAdvertisement = null;

        if (serviceData.length >= (BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT + 1)
                && serviceData[0] == 0x0) {
            byte[] bluetoothAddressAsByteArray = new byte[BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT];

            for (int i = 0; (i < BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT && i < serviceData.length - 1); ++i) {
                bluetoothAddressAsByteArray[i] = serviceData[i + 1];
            }

            String bluetoothMacAddress = byteArrayToBluetoothMacAddress(bluetoothAddressAsByteArray);

            if (bluetoothMacAddress != null) {
                parsedAdvertisement = new ParsedAdvertisement();
                parsedAdvertisement.bluetoothMacAddress = bluetoothMacAddress;
            }
        }

        return parsedAdvertisement;
    }

    /**
     * Checks the given UUID for "Provide Bluetooth MAC address" request ID.
     * @param uuidToCheck The UUID to check.
     * @param serviceUuid The expected service UUID to compare against.
     * @return The request ID or null if not found.
     */
    public static String checkIfUuidContainsProvideBluetoothMacAddressRequestId(UUID uuidToCheck, UUID serviceUuid) {
        String requestId = null;

        if (uuidToCheck != null && serviceUuid != null) {
            if (serviceUuid.compareTo(uuidToCheck) == 0) {
                // The UUID is a match
                // No need to do anything
            } else {
                // Get the beginning of the parsed UUID, leave out the last seven bytes (11 chars)
                String beginningOfUuidToCheck = uuidToCheck.toString().substring(0, 22);

                if (serviceUuid.toString().startsWith(beginningOfUuidToCheck)) {
                    // The beginning of the UUID is a match
                    // Parse the request ID
                    requestId = PeerAdvertisementFactory.parseRequestIdFromUuid(uuidToCheck);
                } else {
                    Log.d(TAG, "checkIfUuidContainsProvideBluetoothMacAddressRequestId: UUID mismatch: Was expecting \""
                            + serviceUuid + "\", got \"" + uuidToCheck + "\"");
                }
            }
        }

        return requestId;
    }

    /**
     * Parses the given manufacturer data.
     * @param manufacturerData The manufacturer data.
     * @return A newly created ParsedAdvertisement instance containing at least the UUID given that
     * the manufacturer data is valid. Note that other members of the instance can be null.
     */
    public static ParsedAdvertisement parseManufacturerData(byte[] manufacturerData) {
        byte[] adLengthAndType = null;
        byte[] serviceUuidAsByteArray = null;
        int[] bluetoothAddressAsInt8Array = null;
        boolean bytesExtracted = false;

        if (manufacturerData != null && manufacturerData.length >= ADVERTISEMENT_BYTE_COUNT) {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(manufacturerData);
            DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

            try {
                adLengthAndType = new byte[2];
                dataInputStream.read(adLengthAndType);

                serviceUuidAsByteArray = new byte[16];
                dataInputStream.read(serviceUuidAsByteArray);

                bluetoothAddressAsInt8Array = new int[BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT];

                for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
                    bluetoothAddressAsInt8Array[i] = dataInputStream.readByte();
                }

                bytesExtracted = true;
            } catch (IOException e) {
                Log.e(TAG, "parseManufacturerData: Failed to parse data: " + e.getMessage(), e);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "parseManufacturerData: Failed to parse data: " + e.getMessage(), e);
            }
        }

        ParsedAdvertisement parsedAdvertisement = null;

        if (bytesExtracted) {
            parsedAdvertisement = new ParsedAdvertisement();
            parsedAdvertisement.uuid = byteArrayToUuid(serviceUuidAsByteArray);
            parsedAdvertisement.bluetoothMacAddress = int8ArrayToBluetoothAddress(bluetoothAddressAsInt8Array);
        }

        return parsedAdvertisement;
    }

    /**
     * Parses the given manufacturer data.
     * @param manufacturerData The manufacturer data.
     * @param serviceUuid The expected service UUID.
     * @return A newly created ParsedAdvertisement instance or null in case of UUID mismatch.
     */
    public static ParsedAdvertisement parseManufacturerData(byte[] manufacturerData, UUID serviceUuid) {
        ParsedAdvertisement parsedAdvertisement = null;

        if (serviceUuid != null) {
            parsedAdvertisement = parseManufacturerData(manufacturerData);
            parsedAdvertisement.provideBluetoothMacAddressRequestId =
                    checkIfUuidContainsProvideBluetoothMacAddressRequestId(parsedAdvertisement.uuid, serviceUuid);
        }

        return parsedAdvertisement;
    }

    /**
     * Converts the given UUID into a byte array.
     * @param uuid The UUID to convert.
     * @return A newly created byte array or null in case of a failure.
     */
    public static byte[] uuidToByteArray(UUID uuid) {
        byte[] uuidAsByteArray = null;

        if (uuid != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(UUID_LENGTH_IN_BYTES);
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            try {
                dataOutputStream.writeLong(uuid.getMostSignificantBits());
                dataOutputStream.writeLong(uuid.getLeastSignificantBits());
                uuidAsByteArray = byteArrayOutputStream.toByteArray();
            } catch (IOException e) {
                Log.e(TAG, "uuidToByteArray: " + e.getMessage(), e);
            }
        }

        return uuidAsByteArray;
    }

    /**
     * Converts the given byte array, which is expected to contain the UUID, into a UUID instance.
     * @param byteArray The byte array containing the UUID.
     * @return A newly created UUID instance or null in case of a failure.
     */
    public static UUID byteArrayToUuid(byte[] byteArray) {
        if (byteArray != null && byteArray.length >= UUID_LENGTH_IN_BYTES) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
            long mostSignificantBits = byteBuffer.getLong();
            long leastSignificantBits = byteBuffer.getLong();
            return new UUID(mostSignificantBits, leastSignificantBits);
        }

        return null;
    }


    /**
     * Converts the given Bluetooth MAC address into a byte array.
     * @param bluetoothMacAddress The Bluetooth MAC address to convert.
     * @return A newly created byte array containing the Bluetooth MAC address or null in case of a failure.
     */
    public static byte[] bluetoothMacAddressToByteArray(String bluetoothMacAddress) {
        byte[] bluetoothMacAddressAsByteArray = null;

        if (bluetoothMacAddress != null
                && bluetoothMacAddress.length() >= BluetoothUtils.BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MIN
                && bluetoothMacAddress.length() <= BluetoothUtils.BLUETOOTH_MAC_ADDRESS_STRING_LENGTH_MAX) {
            int[] bluetoothAddressAsInt8Array = bluetoothAddressToInt8Array(bluetoothMacAddress);

            if (bluetoothAddressAsInt8Array != null
                    && bluetoothAddressAsInt8Array.length == BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT) {
                ByteArrayOutputStream byteArrayOutputStream =
                        new ByteArrayOutputStream(BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT);
                DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

                try {
                    for (int bluetoothAddressByte : bluetoothAddressAsInt8Array) {
                        dataOutputStream.writeByte(bluetoothAddressByte);
                    }

                    bluetoothMacAddressAsByteArray = byteArrayOutputStream.toByteArray();
                } catch (IOException e) {
                    Log.e(TAG, "bluetoothMacAddressToByteArray: " + e.getMessage(), e);
                }
            }
        }

        return bluetoothMacAddressAsByteArray;
    }

    /**
     * Converts the given byte array, which should contain the Bluetooth MAC address, into a string.
     * @param byteArray The byte array containing the Bluetooth MAC address.
     * @return A newly created string containing the Bluetooth MAC address or null in case of a failure.
     */
    public static String byteArrayToBluetoothMacAddress(byte[] byteArray) {
        String bluetoothMacAddress = null;

        if (byteArray != null && byteArray.length >= BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT) {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
            DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
            int[] bluetoothAddressAsInt8Array = new int[BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT];

            try {
                for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
                    bluetoothAddressAsInt8Array[i] = dataInputStream.readByte();
                }

                bluetoothMacAddress = int8ArrayToBluetoothAddress(bluetoothAddressAsInt8Array);
            } catch (IOException e) {
                Log.e(TAG, "byteArrayToBluetoothMacAddress: Failed to read the Bluetooth MAC address: " + e.getMessage(), e);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "byteArrayToBluetoothMacAddress: Failed to read the Bluetooth MAC address: " + e.getMessage(), e);
            }
        }

        return bluetoothMacAddress;
    }

    /**
     * Rotates the byte, with the given index, of the given UUID and returns the modified UUID.
     *
     * A UUID with rotated byte can be used to define and communicate a distinct state e.g.
     * differentiate between willingness to provide assistance (peer its Bluetooth MAC address)
     * instead of requesting assistance.
     *
     * @param originalUuid The original UUID.
     * @param byteIndex The index of the byte (not the index of the char) to rotate. A UUID contains
     *                  16 bytes, so the last index is 15. Thus, greater value than 15 will result
     *                  in an error.
     * @return The UUID with the byte rotated or null in case of a failure.
     */
    public static UUID rotateByte(UUID originalUuid, int byteIndex) {
        String originalUuidAsString = originalUuid.toString();
        int byteAsInt = Integer.MAX_VALUE;
        int startIndexOfByte = byteIndex * 2; // Since byte as hex string is 2 characters

        // We have to take into account the dashes (UUID in form of a string) and add them to the
        // index, if necessary.
        //
        // UUID as string:  "de305d54-75b4-431b-adb2-eb6b9e546014"
        // Byte index:       0 1 2 3  4 5  6 7  8 9  ...
        //
        if (byteIndex > 9) {
            startIndexOfByte += 4;
        } else if (byteIndex > 7) {
            startIndexOfByte += 3;
        } else if (byteIndex > 5) {
            startIndexOfByte += 2;
        } else if (byteIndex > 3) {
            startIndexOfByte += 1;
        }

        try {
            byteAsInt = Integer.parseInt(originalUuidAsString.substring(
                    startIndexOfByte, startIndexOfByte + 2), 16);
        } catch (NumberFormatException e) {
            Log.e(TAG, "rotateByte: Failed extract the byte as integer: " + e.getMessage(), e);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "rotateByte: Failed extract the byte as integer: " + e.getMessage(), e);
        }

        UUID newUuid = null;

        if (byteAsInt != Integer.MAX_VALUE) {
            byteAsInt++;
            String newUuidAsString = null;
            String byteAsString = Integer.toHexString(byteAsInt);

            if (byteIndex == 0) {
                newUuidAsString = byteAsString + originalUuidAsString.substring(2, originalUuidAsString.length());
            } else if (byteIndex == 15) {
                // Last byte
                newUuidAsString =
                        originalUuidAsString.substring(0, originalUuidAsString.length() - 2)
                                + byteAsString;
            } else {
                newUuidAsString =
                        originalUuidAsString.substring(0, startIndexOfByte)
                        + byteAsString
                        + originalUuidAsString.substring(startIndexOfByte + 2, originalUuidAsString.length());
            }

            newUuid = UUID.fromString(newUuidAsString);
            //Log.d(TAG, "rotateByte: " + originalUuidAsString + " -> " + newUuid.toString());
        }

        return newUuid;
    }

    /**
     * Checks if the two UUIDs match, if we leave out the request ID part from the end
     * (6 bytes, 12 characters).
     * @param uuid1 UUID 1.
     * @param uuid2 UUID 2.
     * @return True, if the UUIDs match. False otherwise.
     */
    public static boolean uuidsWithoutRequestIdMatch(UUID uuid1, UUID uuid2) {
        boolean isMatch = false;

        if (uuid1 != null && uuid2 != null) {
            String uuid1AsString = uuid1.toString();
            String uuid2AsString = uuid2.toString();
            int requestIdLength = BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT * 2;

            isMatch = uuid1AsString.substring(0, uuid1AsString.length() - requestIdLength).equals(
                    uuid2AsString.substring(0, uuid2AsString.length() - requestIdLength));
        }

        return isMatch;
    }

    /**
     * Generates a random byte and returns it as a hexadecimal string.
     * @return A random byte as hexadecimal string.
     */
    public static String generatedRandomByteAsHexString() {
        if (mRandom == null) {
            mRandom = new Random(new Date().getTime());
        }

        int randomInt8 = mRandom.nextInt(256);
        return Integer.toHexString(randomInt8);
    }

    /**
     * Converts the given Bluetooth address into an integer array.
     * Since Java does not have unsigned bytes we have to use signed 8 bit integers.
     * @param bluetoothAddress The Bluetooth address to convert.
     * @return An integer array containing the Bluetooth address.
     */
    protected static int[] bluetoothAddressToInt8Array(String bluetoothAddress) {
        String[] hexStringArray = bluetoothAddress.split(BLUETOOTH_ADDRESS_SEPARATOR);
        int[] intArray = null;

        if (hexStringArray.length >= BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT) {
            intArray = new int[hexStringArray.length];

            for (int i = 0; i < hexStringArray.length; ++i) {
                try {
                    intArray[i] = Integer.parseInt(hexStringArray[i], 16);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "bluetoothAddressToInt8Array: " + e.getMessage(), e);
                    intArray = null;
                    break;
                }
            }
        }

        return intArray;
    }

    /**
     * Tries to parse a Bluetooth address from the given integer array.
     * Since Java does not have unsigned bytes we have to use signed 8 bit integers.
     * @param bluetoothAddressAsInt8Array The integer array containing the Bluetooth address.
     * @return The parsed Bluetooth address.
     */
    private static String int8ArrayToBluetoothAddress(int[] bluetoothAddressAsInt8Array) {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                (bluetoothAddressAsInt8Array[0] & 0xff),
                (bluetoothAddressAsInt8Array[1] & 0xff),
                (bluetoothAddressAsInt8Array[2] & 0xff),
                (bluetoothAddressAsInt8Array[3] & 0xff),
                (bluetoothAddressAsInt8Array[4] & 0xff),
                (bluetoothAddressAsInt8Array[5] & 0xff));
    }
}
