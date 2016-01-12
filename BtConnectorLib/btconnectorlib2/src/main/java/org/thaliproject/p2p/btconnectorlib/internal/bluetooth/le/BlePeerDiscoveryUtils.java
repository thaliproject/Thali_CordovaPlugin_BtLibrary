/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;
import java.util.UUID;

/**
 * Contains utility methods supporting Bluetooth LE peer discovery.
 */
@TargetApi(21)
public class BlePeerDiscoveryUtils {
    private static final String TAG = BlePeerDiscoveryUtils.class.getName();

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
}
