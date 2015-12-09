/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

/**
 * Contains properties of a peer.
 */
public class PeerProperties {
    private String mId; // The peer ID
    private String mName; // The peer name
    private String mBluetoothAddress;
    private String mServiceType;
    private String mDeviceName;
    private String mDeviceAddress;

    /**
     * Constructor.
     */
    public PeerProperties() {
    }

    /**
     * Constructor.
     * @param id The peer ID.
     * @param name The peer name.
     * @param bluetoothAddress The Bluetooth address of the peer.
     */
    public PeerProperties(String id, String name, String bluetoothAddress) {
        mId = id;
        mName = name;
        mBluetoothAddress = bluetoothAddress;
        mServiceType = "";
        mDeviceName =  "";
        mDeviceAddress = "";
    }

    /**
     * Constructor.
     * @param id The peer ID.
     * @param name The peer name.
     * @param bluetoothAddress The Bluetooth address of the peer.
     * @param serviceType The service type of the peer.
     * @param deviceAddress The device address of the peer.
     * @param deviceName The device name of the peer.
     */
    public PeerProperties(
            String id, String name, String bluetoothAddress,
            String serviceType, String deviceAddress, String deviceName) {
        mId = id;
        mName = name;
        mBluetoothAddress = bluetoothAddress;
        mServiceType = serviceType;
        mDeviceName =  deviceName;
        mDeviceAddress = deviceAddress;
    }

    public String getId() {
        return mId;
    }

    public void setId(final String id) {
        mId = id;
    }

    public String getName() {
        return mName;
    }

    public void setName(final String name) {
        mName = name;
    }

    public String getBluetoothAddress() {
        return mBluetoothAddress;
    }

    public void setBluetoothAddress(final String bluetoothAddress) {
        mBluetoothAddress = bluetoothAddress;
    }

    public String getServiceType() {
        return mServiceType;
    }

    public void setServiceType(final String serviceType) {
        mServiceType = serviceType;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setDeviceName(final String deviceName) {
        mDeviceName = deviceName;
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public void setDeviceAddress(final String deviceAddress) {
        mDeviceAddress = deviceAddress;
    }

    /**
     * Checks that the main values (peer ID, name and Bluetooth address) are not empty (or null).
     * @return True, if the values are not empty (or null). False otherwise.
     */
    public boolean isValid() {
        return (mId != null && !mId.isEmpty()
                && mName != null && !mName.isEmpty()
                && mBluetoothAddress != null && !mBluetoothAddress.isEmpty());
    }

    @Override
    public String toString() {
        String returnValue = "";

        if (mId.equals(mBluetoothAddress)) {
            returnValue = "[" + mId + " " + mName + "]";
        } else {
            returnValue = "[" + mId + " " + mName + " " + mBluetoothAddress + "]";
        }

        return returnValue;
    }
}
