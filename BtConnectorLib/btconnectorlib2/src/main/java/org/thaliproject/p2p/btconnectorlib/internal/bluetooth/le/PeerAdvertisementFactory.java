/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Contains utility methods for BLE peer discovery.
 */
@TargetApi(21)
class PeerAdvertisementFactory {
    private static final String TAG = PeerAdvertisementFactory.class.getName();
    public static final int MANUFACTURER_ID = 76;
    private static final int BEACON_AD_LENGTH_AND_TYPE = 0x0215;
    private static final String BLUETOOTH_ADDRESS_SEPARATOR = ":";
    private static final int BLUETOOTH_ADDRESS_BYTE_COUNT = 6;
    private static final int ADVERTISEMENT_BYTE_COUNT = 24;

    /**
     * Tries to create an advertise data based on the given peer properties.
     * @param peerName The peer name.
     * @param serviceUuid The service UUID.
     * @param bluetoothAddress The Bluetooth address.
     * @return A newly created AdvertiseData instance or null in case of a failure.
     */
    public static AdvertiseData createAdvertiseData(String peerName, UUID serviceUuid, String bluetoothAddress) {
        Log.i(TAG, "createAdvertiseData: From: " + peerName + " " + serviceUuid + " " + bluetoothAddress);
        AdvertiseData advertiseData = null;
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(ADVERTISEMENT_BYTE_COUNT);
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        boolean ok = false;

        try {
            // For beacons the first two bytes consist of advertisement length and type
            dataOutputStream.writeShort(BEACON_AD_LENGTH_AND_TYPE);

            // Use the first two bytes for truncated peer name
            //byte[] peerNameAsTwoBytes = peerNameAsTwoByteArray(peerName);
            //dataOutputStream.writeByte(peerNameAsTwoBytes[0]);
            //dataOutputStream.writeByte(peerNameAsTwoBytes[1]);

            dataOutputStream.writeLong(serviceUuid.getMostSignificantBits());
            dataOutputStream.writeLong(serviceUuid.getLeastSignificantBits());

            // For beacons the four bytes after the UUID are reserved for second and third IDs,
            // which are followed by RSSI (1 byte) and a manufacturer reserved byte. That is six
            // bytes in total.
            //dataOutputStream.writeShort(0x0001); // ID 2
            //dataOutputStream.writeShort(0x0002); // ID 3
            //dataOutputStream.writeByte(0xb5); // RSSI
            //dataOutputStream.writeByte(0xff); // Manufacturer reserved byte

            // Insert the Bluetooth address after the UUID (six bytes)
            int[] bluetoothAddressAsInt8Array = bluetoothAddressToInt8Array(bluetoothAddress);

            Log.i(TAG, int8ArrayToBluetoothAddress(bluetoothAddressAsInt8Array));

            if (bluetoothAddressAsInt8Array != null && bluetoothAddressAsInt8Array.length == BLUETOOTH_ADDRESS_BYTE_COUNT) {
                for (int bluetoothAddressByte : bluetoothAddressAsInt8Array) {
                    dataOutputStream.writeByte(bluetoothAddressByte);
                }

                ok = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "createAdvertiseData: " + e.getMessage(), e);
        }

        if (ok) {
            ok = false;

            try {
                builder.addManufacturerData(MANUFACTURER_ID, byteArrayOutputStream.toByteArray());
                ok = true;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "createAdvertiseData: " + e.getMessage(), e);
            }
        }

        if (ok) {
            advertiseData = builder.build();
        }

        return advertiseData;
    }

    /**
     * Parses peer properties from the given manufacturer data byte array.
     * @param manufacturerData The manufacturer data.
     * @param serviceUuid The service UUID. Will return peer properties, if and only if this UUID
     *                    matches the one provided in manufacturer data. If this is null, no
     *                    comparison is made and all UUIDs are accepted.
     * @return The peer properties or null in case of a failure or UUID mismatch.
     */
    public static PeerProperties manufacturerDataToPeerProperties(byte[] manufacturerData, UUID serviceUuid) {
        PeerProperties peerProperties = null;
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
                Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse peer properties: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse peer properties: " + e.getMessage(), e);
            }
        }

        if (bytesExtracted) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(serviceUuidAsByteArray);
            long mostSignificantBits = byteBuffer.getLong();
            long leastSignificantBits = byteBuffer.getLong();
            UUID uuid = new UUID(mostSignificantBits, leastSignificantBits);

            if (serviceUuid != null && serviceUuid.compareTo(uuid) == 0) {
                // The UUID is a match, do continue
                String bluetoothAddress = int8ArrayToBluetoothAddress(bluetoothAddressAsInt8Array);

                if (bluetoothAddress != null) {
                    peerProperties = new PeerProperties(bluetoothAddress, "<no peer name>", bluetoothAddress);
                } else {
                    Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse the Bluetooth address");
                }
            } else {
                Log.d(TAG, "manufacturerDataToPeerProperties: UUID mismatch");
            }
        }

        return peerProperties;
    }

    /**
     * Creates a new scan filter using the given service UUID.
     * @param serviceUuid The service UUID for the scan filter.
     * @return A newly created scan filter or null in case of a failure.
     */
    public static ScanFilter createScanFilter(UUID serviceUuid) {
        ScanFilter scanFilter = null;
        ScanFilter.Builder builder = new ScanFilter.Builder();

        try {
            builder.setManufacturerData(MANUFACTURER_ID, null);

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
     * Converts the given peer name into an array of two bytes (sort of like a hash, but not quite).
     * @param peerName The peer name to convert.
     * @return An array of two bytes.
     */
    private static byte[] peerNameAsTwoByteArray(String peerName) {
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
    private static int[] bluetoothAddressToInt8Array(String bluetoothAddress) {
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
    private static String int8ArrayToBluetoothAddress(int[] bluetoothAddressAsInt8Array) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
            stringBuilder.append(Integer.toHexString(bluetoothAddressAsInt8Array[i] & 0xff));

            if (i < bluetoothAddressAsInt8Array.length - 1) {
                stringBuilder.append(BLUETOOTH_ADDRESS_SEPARATOR);
            }
        }

        return stringBuilder.toString().toUpperCase();
    }
}
