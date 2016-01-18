/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

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
        String provideBluetoothMacAddressRequestId = null;
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
                    // Get the beginning of the parsed UUID, leave out the last six bytes (12 chars)
                    String beginningOfParsedUuid = parsedAdvertisement.uuid.toString().substring(0, 23);

                    if (serviceUuid.toString().startsWith(beginningOfParsedUuid)) {
                        // The beginning of the UUID is a match
                        // Parse the request ID
                        parsedAdvertisement.provideBluetoothMacAddressRequestId =
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
     * Rotates the last byte of the given UUID and returns the modified UUID.
     * @param originalUuid The original UUID.
     * @return The UUID with the last byte rotated or null in case of a failure.
     */
    public static UUID rotateTheLastByte(UUID originalUuid) {
        String originalUuidAsString = originalUuid.toString();
        int lastByteAsInt = Integer.MAX_VALUE;
        int startIndexOfLastByte = originalUuidAsString.length() - 2;

        try {
            lastByteAsInt = Integer.parseInt(originalUuidAsString.substring(
                    startIndexOfLastByte, startIndexOfLastByte + 2), 16);
        } catch (NumberFormatException e) {
            Log.e(TAG, "rotateTheLastByte: Failed extract the last byte as integer: " + e.getMessage(), e);
        }

        UUID newUuid = null;

        if (lastByteAsInt != Integer.MAX_VALUE) {
            lastByteAsInt++;
            String lastByteAsString = Integer.toHexString(lastByteAsInt);

            String newUuidAsString =
                    originalUuidAsString.substring(0, originalUuidAsString.length() - 2)
                            + lastByteAsString;

            newUuid = UUID.fromString(newUuidAsString);
            Log.d(TAG, "rotateTheLastByte: " + originalUuidAsString + " -> " + newUuid.toString());
        }

        return newUuid;
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
