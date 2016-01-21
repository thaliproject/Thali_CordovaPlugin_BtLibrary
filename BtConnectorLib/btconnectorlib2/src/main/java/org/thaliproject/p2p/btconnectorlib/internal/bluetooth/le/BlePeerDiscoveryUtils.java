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
import java.io.DataInputStream;
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
    public static class ParsedAdvertisement {
        UUID uuid = null;
        String provideBluetoothMacAddressId = null;
        String bluetoothMacAddress = null;
    }

    private static final String TAG = BlePeerDiscoveryUtils.class.getName();

    public static final int ADVERTISEMENT_BYTE_COUNT = 24;
    public static final int BLUETOOTH_ADDRESS_BYTE_COUNT = 6;
    private static final String BLUETOOTH_ADDRESS_SEPARATOR = ":";

    private static Random mRandom = null;

    /**
     * Creates a new scan filter using the given service UUID.
     * @param serviceUuid The service UUID for the scan filter.
     * @return A newly created scan filter or null in case of a failure.
     */
    public static ScanFilter createScanFilter(UUID serviceUuid) {
        ScanFilter scanFilter = null;
        ScanFilter.Builder builder = new ScanFilter.Builder();

        try {
            builder.setManufacturerData(PeerAdvertisementFactory.MANUFACTURER_ID, null);

            if (serviceUuid != null) {
                builder.setServiceUuid(new ParcelUuid(serviceUuid));
            }

            scanFilter = builder.build();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "createScanFilter: " + e.getMessage(), e);
        }

        return scanFilter;
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

                bluetoothAddressAsInt8Array = new int[BLUETOOTH_ADDRESS_BYTE_COUNT];

                for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
                    bluetoothAddressAsInt8Array[i] = dataInputStream.readByte();
                }

                bytesExtracted = true;
            } catch (IOException e) {
                Log.e(TAG, "parseManufacturerData: Failed to parse data: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "parseManufacturerData: Failed to parse data: " + e.getMessage(), e);
            }
        }

        ParsedAdvertisement parsedAdvertisement = null;

        if (bytesExtracted) {
            parsedAdvertisement = new ParsedAdvertisement();
            ByteBuffer byteBuffer = ByteBuffer.wrap(serviceUuidAsByteArray);
            long mostSignificantBits = byteBuffer.getLong();
            long leastSignificantBits = byteBuffer.getLong();
            parsedAdvertisement.uuid = new UUID(mostSignificantBits, leastSignificantBits);
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

            if (parsedAdvertisement != null && parsedAdvertisement.uuid != null) {
                if (serviceUuid.compareTo(parsedAdvertisement.uuid) == 0) {
                    // The UUID is a match
                    // No need to do anything
                } else {
                    // Get the beginning of the parsed UUID, leave out the last seven bytes (11 chars)
                    String beginningOfParsedUuid = parsedAdvertisement.uuid.toString().substring(0, 22);

                    if (serviceUuid.toString().startsWith(beginningOfParsedUuid)) {
                        // The beginning of the UUID is a match
                        // Parse the request ID
                        parsedAdvertisement.provideBluetoothMacAddressId =
                                PeerAdvertisementFactory.parseRequestIdFromUuid(parsedAdvertisement.uuid);
                    } else {
                        Log.d(TAG, "parseManufacturerData: UUID mismatch: Was expecting \""
                                + serviceUuid + "\", got \"" + parsedAdvertisement.uuid + "\"");
                    }
                }
            }
        }

        return parsedAdvertisement;
    }

    /**
     * Converts the given peer name into an array of two bytes (sort of like a hash, but not quite).
     * @param peerName The peer name to convert.
     * @return An array of two bytes.
     */
    public static byte[] peerNameAsTwoByteArray(String peerName) {
        byte[] twoByteArray =  new byte[2];
        twoByteArray[0] = (byte)peerName.length(); // The first byte is the length of the original peer name

        for (int i = 0; i < peerName.length(); ++i) {
            twoByteArray[1] += (byte)peerName.charAt(i);
        }

        return twoByteArray;
    }

    /**
     * Converts the given Bluetooth address into an integer array.
     * Since Java does not have unsigned bytes we have to use signed 8 bit integers.
     * @param bluetoothAddress The Bluetooth address to convert.
     * @return An integer array containing the Bluetooth address.
     */
    public static int[] bluetoothAddressToInt8Array(String bluetoothAddress) {
        String[] hexStringArray = bluetoothAddress.split(BLUETOOTH_ADDRESS_SEPARATOR);
        int[] intArray = null;

        if (hexStringArray.length >= BLUETOOTH_ADDRESS_BYTE_COUNT) {
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
    public static String int8ArrayToBluetoothAddress(int[] bluetoothAddressAsInt8Array) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
            stringBuilder.append(Integer.toHexString(bluetoothAddressAsInt8Array[i] & 0xff));

            if (i < bluetoothAddressAsInt8Array.length - 1) {
                stringBuilder.append(BLUETOOTH_ADDRESS_SEPARATOR);
            }
        }

        return stringBuilder.toString().toUpperCase();
    }

    /**
     * Rotates the byte, with the given index, of the given UUID and returns the modified UUID.
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
        // index, if necessary:
        //
        // UUID as string:  "de305d54-75b4-431b-adb2-eb6b9e546014"
        // byte index:       0 1 2 3  4 5  6 7  8 9  ...
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
}
