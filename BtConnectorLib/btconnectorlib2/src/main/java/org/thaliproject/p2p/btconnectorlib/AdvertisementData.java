package org.thaliproject.p2p.btconnectorlib;

import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;

import java.util.Locale;

/**
 * Created by evabishchevich on 12/16/16.
 */

public class AdvertisementData {

    public final int manufacturerId;
    public final int beaconAdLengthAndType;
    /**
     * The optional extra information for beacon data (unsigned 8-bit integer)
     */
    public final int beaconAdExtraInfo;
    public final BlePeerDiscoverer.AdvertisementDataType advertisementDataType;

    public AdvertisementData(int manufacturerId, int beaconAdLengthAndType, int beaconAdExtraInfo,
                             BlePeerDiscoverer.AdvertisementDataType advertisementDataType) {
        this.manufacturerId = manufacturerId;
        this.beaconAdLengthAndType = beaconAdLengthAndType;
        this.beaconAdExtraInfo = beaconAdExtraInfo;
        this.advertisementDataType = advertisementDataType;
    }

    @Override
    public String toString() {
        return String.format("Advertisement data: manufacturerId = %d, beaconLengthAndType = %d," +
                        " beaconExtraInfo = %d, advertisement data type = %s", manufacturerId,
                beaconAdLengthAndType, beaconAdExtraInfo, advertisementDataType.toString());
    }
}
