/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

/**
 * Contains properties of a peer.
 */
public class PeerProperties {
    public static final String NO_PEER_NAME_STRING = "<no peer name>";
    private String mId; // The peer ID
    private String mName; // The peer name
    private String mBluetoothAddress;
    private String mServiceType;
    private String mDeviceName;
    private String mDeviceAddress;

    /**
     * Constructor.
     * Not recommended to be used.
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
     * Copies the content of the given source to this one.
     * @param sourcePeerProperties The source peer properties.
     */
    public void copyFrom(PeerProperties sourcePeerProperties) {
        if (sourcePeerProperties != null) {
            mId = sourcePeerProperties.mId;
            mName = sourcePeerProperties.mName;
            mBluetoothAddress = sourcePeerProperties.mBluetoothAddress;
            mServiceType = sourcePeerProperties.mServiceType;
            mDeviceName = sourcePeerProperties.mDeviceName;
            mDeviceAddress = sourcePeerProperties.mDeviceAddress;
        }
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

    /**
     * Checks if this instance has more information than the given one i.e. has more members with
     * data.
     * @param otherPeerProperties The other peer properties to compare to.
     * @return True, if this has more information than the given one. False otherwise.
     */
    public boolean hasMoreInformation(PeerProperties otherPeerProperties) {
        return (this.fieldsWithData() > otherPeerProperties.fieldsWithData());
    }

    /**
     * Checks the new peer properties for any missing information and copies them from the old
     * properties if it has it. In other words, this method can be used to make sure we do not lose
     * any information, when updating peers.
     * @param oldPeerProperties The old peer properties.
     * @param newPeerProperties The new peer properties.
     * @return True, if data was copied. False otherwise.
     */
    public static boolean checkNewPeerForMissingInformation(
            PeerProperties oldPeerProperties, PeerProperties newPeerProperties) {
        boolean dataWasCopied = false;

        if (oldPeerProperties != null && newPeerProperties != null) {
            if (!isNullOrEmpty(oldPeerProperties.mId) && isNullOrEmpty(newPeerProperties.mId)) {
                newPeerProperties.mId = oldPeerProperties.mId;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mName)
                    && !oldPeerProperties.mName.equals(NO_PEER_NAME_STRING)
                    && (isNullOrEmpty(newPeerProperties.mName)
                        || newPeerProperties.mName.equals(NO_PEER_NAME_STRING))) {
                newPeerProperties.mName = oldPeerProperties.mName;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mBluetoothAddress)
                    && isNullOrEmpty(newPeerProperties.mBluetoothAddress)) {
                newPeerProperties.mBluetoothAddress = oldPeerProperties.mBluetoothAddress;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mServiceType) && isNullOrEmpty(newPeerProperties.mServiceType)) {
                newPeerProperties.mServiceType = oldPeerProperties.mServiceType;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mDeviceName) && isNullOrEmpty(newPeerProperties.mDeviceName)) {
                newPeerProperties.mDeviceName = oldPeerProperties.mDeviceName;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mDeviceAddress) && isNullOrEmpty(newPeerProperties.mDeviceAddress)) {
                newPeerProperties.mDeviceAddress = oldPeerProperties.mDeviceAddress;
                dataWasCopied = true;
            }
        }

        return dataWasCopied;
    }

    @Override
    public boolean equals(Object otherPeerProperties) {
        PeerProperties other = (PeerProperties)otherPeerProperties;
        boolean isMatch = false;

        if (other != null && other.getId() != null && mId != null) {
            isMatch = other.getId().equalsIgnoreCase(mId);
        }

        return isMatch;
    }

    @Override
    public String toString() {
        String returnValue = "";

        if (mId != null && mId.equals(mBluetoothAddress)) {
            returnValue = "[" + mId + " " + mName + "]";
        } else {
            returnValue = "[" + mId + " " + mName + " " + mBluetoothAddress + "]";
        }

        return returnValue;
    }

    /**
     * @return The number of fields with data.
     */
    private int fieldsWithData() {
        int count = 0;

        if (!isNullOrEmpty(mId)) {
            count++;
        }

        if (!isNullOrEmpty(mName) && !mName.equals(NO_PEER_NAME_STRING)) {
            count++;
        }

        if (!isNullOrEmpty(mBluetoothAddress)) {
            count++;
        }

        if (!isNullOrEmpty(mServiceType)) {
            count++;
        }

        if (!isNullOrEmpty(mDeviceName)) {
            count++;
        }

        if (!isNullOrEmpty(mDeviceAddress)) {
            count++;
        }

        return count;
    }

    /**
     * @param stringToCheck The string to check.
     * @return True, if the string is null or empty. False otherwise.
     */
    private static boolean isNullOrEmpty(String stringToCheck) {
        return (stringToCheck == null || stringToCheck.length() == 0);
    }
}
