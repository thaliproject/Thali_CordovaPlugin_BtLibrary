/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.ScanFilter;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.io.*;
import java.util.UUID;

/**
 *
 */
@TargetApi(21)
class PeerAdvertisementFactory {
    private static final String TAG = PeerAdvertisementFactory.class.getName();
    public static final int MANUFACTURER_ID = 76;
    private static final int BEACON_AD_LENGTH_AND_TYPE = 0x0215;
    private static final String BLUETOOTH_ADDRESS_SEPARATOR = ":";

    /**
     *
     * @param peerName
     * @param serviceUuid
     * @param bluetoothAddress
     * @return A newly created AdvertiseData instance or null in case of a failure.
     */
    public static AdvertiseData createAdvertiseData(String peerName, UUID serviceUuid, String bluetoothAddress) {
        Log.i(TAG, "createAdvertiseData: From: " + peerName + " " + serviceUuid + " " + bluetoothAddress);
        AdvertiseData advertiseData = null;
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(25);
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

            if (bluetoothAddressAsInt8Array != null && bluetoothAddressAsInt8Array.length == 6) {
                for (int bluetoothAddressByte : bluetoothAddressAsInt8Array) {
                    dataOutputStream.writeByte(bluetoothAddressByte);
                }

                ok = true;
            }

            ok = true;
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
     *
     * @param manufacturerData
     * @return
     */
    public static PeerProperties manufacturerDataToPeerProperties(byte[] manufacturerData) {
        PeerProperties peerProperties = null;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(manufacturerData);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

        byte[] adLengthAndType = null;
        byte[] serviceUuid = null;
        int[] bluetoothAddressAsInt8Array = null;
        boolean ok = false;

        try {
            adLengthAndType = new byte[2];
            dataInputStream.read(adLengthAndType);

            serviceUuid = new byte[16];
            dataInputStream.read(serviceUuid);

            bluetoothAddressAsInt8Array = new int[6];

            for (int i = 0; i < bluetoothAddressAsInt8Array.length; ++i) {
                bluetoothAddressAsInt8Array[i] = dataInputStream.readByte();
            }

            ok = true;
        } catch (IOException e) {
            Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse peer properties: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse peer properties: " + e.getMessage(), e);
        }

        if (ok) {
            String bluetoothAddress = int8ArrayToBluetoothAddress(bluetoothAddressAsInt8Array);

            if (bluetoothAddress != null) {
                peerProperties = new PeerProperties(bluetoothAddress, "<no peer name>", bluetoothAddress);
            } else {
                Log.e(TAG, "manufacturerDataToPeerProperties: Failed to parse the Bluetooth address");
            }
        }

        return peerProperties;
    }

    /**
     *
     * @return
     */
    public static ScanFilter createScanFilter() {
        ScanFilter scanFilter = null;
        ScanFilter.Builder builder = new ScanFilter.Builder();

        try {
            builder.setManufacturerData(MANUFACTURER_ID, null);
            scanFilter = builder.build();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "createScanFilter: " + e.getMessage(), e);
        }

        return scanFilter;
    }

    /**
     *
     * @param peerName
     * @return
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
     *
     * @param twoBytes
     * @return
     */
    private static String twoBytesToPeerName(byte[] twoBytes) {
        return twoBytes.toString();
    }

    /**
     * Since Java does not have unsigned bytes we have to use signed 8 bit integers.
     * @param bluetoothAddress
     * @return
     */
    private static int[] bluetoothAddressToInt8Array(String bluetoothAddress) {
        String[] hexStringArray = bluetoothAddress.split(BLUETOOTH_ADDRESS_SEPARATOR);
        int[] intArray = null;

        if (hexStringArray.length >= 6) {
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
     *
     * @param bluetoothAddressAsInt8Array
     * @return
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
