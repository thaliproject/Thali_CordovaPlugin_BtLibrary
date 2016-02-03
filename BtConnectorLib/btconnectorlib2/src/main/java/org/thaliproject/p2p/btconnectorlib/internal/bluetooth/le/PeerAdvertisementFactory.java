/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseData;
import android.os.ParcelUuid;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothUtils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Contains utility methods for BLE peer discovery.
 */
@TargetApi(21)
class PeerAdvertisementFactory {
    private static final String TAG = PeerAdvertisementFactory.class.getName();
    public static final int MANUFACTURER_ID = 76;
    private static final int BEACON_AD_LENGTH_AND_TYPE = 0x0215;

    /**
     * Creates advertise data based on the given service UUID and Bluetooth MAC address.
     *
     * advertiseData MUST be set to:
     *
     *  addManufacturerData() - This MUST NOT be set. We need all the space in the BLE Advertisement we can get.
     *  addServiceData(serviceDataUuid, serviceData) - serviceDataUuid MUST be set to the Thali service's BLE UUID and serviceData MUST be set to the single byte "0" followed by the Bluetooth MAC address as a byte stream.
     *  addServiceUuid(serviceUuid) - serviceUuid MUST be set to the Thali service's BLE UUID.
     *  setIncludeDeviceName() - Must be set to false. We need the space.
     *  setIncludeTxPowerLevel() - Must be set to false. We need the space.
     *
     * @param serviceUuid The service UUID.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     * @return A newly created AdvertiseData instance or null in case of a failure.
     */
    public static AdvertiseData createAdvertiseDataToServiceData(UUID serviceUuid, String bluetoothMacAddress) {
        Log.i(TAG, "createAdvertiseDataToServiceData: Service UUID: \"" + serviceUuid
                + "\", Bluetooth MAC address: \"" + bluetoothMacAddress + "\"");

        AdvertiseData advertiseData = null;
        byte[] bluetoothMacAddressAsByteArray =
                BlePeerDiscoveryUtils.bluetoothMacAddressToByteArray(bluetoothMacAddress);

        if (bluetoothMacAddressAsByteArray != null) {
            AdvertiseData.Builder builder = new AdvertiseData.Builder();

            ParcelUuid serviceUuidAsParcelUuid = new ParcelUuid(serviceUuid);

            builder.addServiceUuid(serviceUuidAsParcelUuid);
            builder.setIncludeDeviceName(false);
            builder.setIncludeTxPowerLevel(false);

            byte[] serviceDataAsByteArray = new byte[bluetoothMacAddressAsByteArray.length + 1];
            serviceDataAsByteArray[0] = 0x0;

            for (int i = 0; i < bluetoothMacAddressAsByteArray.length; ++i) {
                serviceDataAsByteArray[i + 1] = bluetoothMacAddressAsByteArray[i];
            }

            try {
                builder.addServiceData(serviceUuidAsParcelUuid, serviceDataAsByteArray);
                advertiseData = builder.build();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "createAdvertiseDataToServiceData: " + e.getMessage(), e);
            }
        }

        Log.v(TAG, "createAdvertiseDataToServiceData: Created: " + advertiseData);
        return advertiseData;
    }

    /**
     * Creates advertise data based on the given service UUID and Bluetooth MAC address.
     * @param serviceUuid The service UUID.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     * @return A newly created AdvertiseData instance or null in case of a failure.
     */
    public static AdvertiseData createAdvertiseDataToManufacturerData(UUID serviceUuid, String bluetoothMacAddress) {
        Log.i(TAG, "createAdvertiseDataToManufacturerData: Service UUID: \"" + serviceUuid
                + "\", Bluetooth MAC address: \"" + bluetoothMacAddress + "\"");

        AdvertiseData advertiseData = null;
        AdvertiseData.Builder builder = new AdvertiseData.Builder();

        ByteArrayOutputStream byteArrayOutputStream =
                new ByteArrayOutputStream(BlePeerDiscoveryUtils.ADVERTISEMENT_BYTE_COUNT);

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
            int[] bluetoothMacAddressAsInt8Array = BlePeerDiscoveryUtils.bluetoothAddressToInt8Array(bluetoothMacAddress);

            if (bluetoothMacAddressAsInt8Array != null
                    && bluetoothMacAddressAsInt8Array.length == BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT) {
                for (int bluetoothAddressByte : bluetoothMacAddressAsInt8Array) {
                    dataOutputStream.writeByte(bluetoothAddressByte);
                }

                ok = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "createAdvertiseDataToManufacturerData: " + e.getMessage(), e);
        }

        if (ok) {
            ok = false;

            try {
                builder.addManufacturerData(MANUFACTURER_ID, byteArrayOutputStream.toByteArray());
                ok = true;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "createAdvertiseDataToManufacturerData: " + e.getMessage(), e);
            }

            builder.setIncludeDeviceName(false);
            builder.setIncludeTxPowerLevel(false);
        }

        if (ok) {
            advertiseData = builder.build();
        }

        return advertiseData;
    }

    /**
     * Creates a new PeerProperties instance based on the given parsed advertisement.
     * @param parsedAdvertisement The parsed advertisement.
     * @return A newly created PeerProperties instance or null, if the advertisement does not
     * contain a Bluetooth MAC address.
     */
    public static PeerProperties parsedAdvertisementToPeerProperties(
            BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement) {
        PeerProperties peerProperties = null;

        if (parsedAdvertisement != null) {
            if (parsedAdvertisement.bluetoothMacAddress != null) {
                peerProperties = new PeerProperties(parsedAdvertisement.bluetoothMacAddress);
            } else {
                Log.e(TAG, "parsedAdvertisementToPeerProperties: No Bluetooth MAC address");
            }
        }

        return peerProperties;
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
        BlePeerDiscoveryUtils.ParsedAdvertisement parsedAdvertisement =
                BlePeerDiscoveryUtils.parseManufacturerData(manufacturerData, serviceUuid);
        return parsedAdvertisementToPeerProperties(parsedAdvertisement);
    }

    /**
     * Creates a "Provide Bluetooth MAC address" UUID based on the given service UUID and request ID.
     * @param serviceUuid The service UUID to be used as a basis for the new UUID.
     * @param requestId The request ID that will be used to replace the ending of the service UUID.
     * @return A newly created UUID.
     */
    public static UUID createProvideBluetoothMacAddressUuid(UUID serviceUuid, String requestId) {
        UUID provideBluetoothMacAddressUuid = null;

        if (serviceUuid != null && requestId != null) {
            String serviceUuidAsString = serviceUuid.toString();

            String provideBluetoothMacAddressRequestUuidAsString =
                    serviceUuidAsString.substring(0, serviceUuidAsString.length() - requestId.length()) + requestId;

            provideBluetoothMacAddressUuid = UUID.fromString(provideBluetoothMacAddressRequestUuidAsString);
        }

        //Log.d(TAG, "createProvideBluetoothMacAddressUuid: " + serviceUuid.toString()
        //        + " -> " + provideBluetoothMacAddressUuid.toString());

        return provideBluetoothMacAddressUuid;
    }

    /**
     * Generates a unique UUID for "Provide Bluetooth MAC address" request: The last six bytes of
     * the given service UUID are changed, but the beginning of the UUID remains the same.
     * @param serviceUuid The service UUID to be used as a basis for the new UUID.
     * @return A newly created unique UUID.
     */
    public static UUID generateNewProvideBluetoothMacAddressRequestUuid(UUID serviceUuid) {
        UUID provideBluetoothMacAddressRequestUuid = null;

        if (serviceUuid != null) {
            provideBluetoothMacAddressRequestUuid = serviceUuid;

            while (serviceUuid.compareTo(provideBluetoothMacAddressRequestUuid) == 0) {
                StringBuilder stringBuilder = new StringBuilder();

                for (int i = 0; i < BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT; ++i) {
                    stringBuilder.append(BlePeerDiscoveryUtils.generatedRandomByteAsHexString());
                }

                provideBluetoothMacAddressRequestUuid =
                        createProvideBluetoothMacAddressUuid(serviceUuid, stringBuilder.toString());
            }

            //Log.d(TAG, "generateNewProvideBluetoothMacAddressRequestUuid: " + serviceUuid.toString()
            //        + " -> " + provideBluetoothMacAddressRequestUuid.toString());
        }

        return provideBluetoothMacAddressRequestUuid;
    }

    /**
     * Parses the request ID (as hex string) from the given UUID (the last six bytes, 12 chars).
     * @param provideBluetoothMacAddressRequestUuid A "Provide Bluetooth MAC address" request UUID.
     * @return The parsed request ID (as hex string) or null in case of a failure.
     */
    public static String parseRequestIdFromUuid(UUID provideBluetoothMacAddressRequestUuid) {
        String uuidAsString = provideBluetoothMacAddressRequestUuid.toString();
        return uuidAsString.substring(
                uuidAsString.length() - BluetoothUtils.BLUETOOTH_ADDRESS_BYTE_COUNT * 2,
                uuidAsString.length());
    }
}
