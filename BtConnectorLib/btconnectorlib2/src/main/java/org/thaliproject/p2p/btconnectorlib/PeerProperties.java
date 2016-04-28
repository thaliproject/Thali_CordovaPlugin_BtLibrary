/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

/**
 * Contains properties of a peer.
 * The ID of the peer is its Bluetooth MAC address.
 */
public class PeerProperties {
    public static final String NO_PEER_NAME_STRING = "<no peer name>";
    public static final String BLUETOOTH_MAC_ADDRESS_UNKNOWN = "0:0:0:0:0:0";
    public static final int NO_EXTRA_INFORMATION = 0;
    private String mName; // The peer name
    private String mBluetoothMacAddress;
    private String mServiceType;
    private String mDeviceName;
    private String mDeviceAddress;
    private int mExtraInformation;

    /**
     * Constructor.
     * Not recommended to be used.
     */
    public PeerProperties() {
    }

    /**
     * Constructor.
     * @param bluetoothMacAddress The Bluetooth MAC address.
     */
    public PeerProperties(String bluetoothMacAddress) {
        mName = NO_PEER_NAME_STRING;
        mBluetoothMacAddress = bluetoothMacAddress;
        mServiceType = "";
        mDeviceName =  "";
        mDeviceAddress = "";
        mExtraInformation = NO_EXTRA_INFORMATION;
    }

    /**
     * Constructor.
     * @param name The peer name.
     * @param bluetoothMacAddress The Bluetooth MAC address of the peer.
     */
    public PeerProperties(String name, String bluetoothMacAddress) {
        mName = name;
        mBluetoothMacAddress = bluetoothMacAddress;
        mServiceType = "";
        mDeviceName =  "";
        mDeviceAddress = "";
        mExtraInformation = NO_EXTRA_INFORMATION;
    }

    /**
     * Constructor.
     * @param name The peer name.
     * @param bluetoothMacAddress The Bluetooth MAC address of the peer.
     * @param serviceType The service type of the peer.
     * @param deviceAddress The device address of the peer.
     * @param deviceName The device name of the peer.
     */
    public PeerProperties(
            String name, String bluetoothMacAddress,
            String serviceType, String deviceAddress, String deviceName) {
        mName = name;
        mBluetoothMacAddress = bluetoothMacAddress;
        mServiceType = serviceType;
        mDeviceName =  deviceName;
        mDeviceAddress = deviceAddress;
        mExtraInformation = NO_EXTRA_INFORMATION;
    }

    /**
     * @return The identifier of this peer, which is its Bluetooth MAC address.
     */
    public String getId() {
        return mBluetoothMacAddress;
    }

    public String getName() {
        return mName;
    }

    public void setName(final String name) {
        mName = name;
    }

    public String getBluetoothMacAddress() {
        return mBluetoothMacAddress;
    }

    public void setBluetoothMacAddress(final String bluetoothAddress) {
        mBluetoothMacAddress = bluetoothAddress;
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

    public int getExtraInformation() {
        return mExtraInformation;
    }

    public void setExtraInformation(int extraInformation) {
        mExtraInformation = extraInformation;
    }

    /**
     * Copies the content of the given source to this one.
     * @param sourcePeerProperties The source peer properties.
     */
    public void copyFrom(PeerProperties sourcePeerProperties) {
        if (sourcePeerProperties != null) {
            mName = sourcePeerProperties.mName;
            mBluetoothMacAddress = sourcePeerProperties.mBluetoothMacAddress;
            mServiceType = sourcePeerProperties.mServiceType;
            mDeviceName = sourcePeerProperties.mDeviceName;
            mDeviceAddress = sourcePeerProperties.mDeviceAddress;
            mExtraInformation = sourcePeerProperties.mExtraInformation;
        }
    }

    /**
     * Checks that the main values (peer ID, name and Bluetooth address) are not empty (or null).
     * @return True, if the values are not empty (or null). False otherwise.
     */
    public boolean isValid() {
        return (mName != null && !mName.isEmpty()
                && mBluetoothMacAddress != null && !mBluetoothMacAddress.isEmpty());
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
    public static boolean copyMissingValuesFromOldPeer(
            PeerProperties oldPeerProperties, PeerProperties newPeerProperties) {
        boolean dataWasCopied = false;

        if (oldPeerProperties != null && newPeerProperties != null) {
            if (!isNullOrEmpty(oldPeerProperties.mName)
                    && !oldPeerProperties.mName.equals(NO_PEER_NAME_STRING)
                    && (isNullOrEmpty(newPeerProperties.mName)
                        || newPeerProperties.mName.equals(NO_PEER_NAME_STRING))) {
                newPeerProperties.mName = oldPeerProperties.mName;
                dataWasCopied = true;
            }

            if (!isNullOrEmpty(oldPeerProperties.mBluetoothMacAddress)
                    && isNullOrEmpty(newPeerProperties.mBluetoothMacAddress)) {
                newPeerProperties.mBluetoothMacAddress = oldPeerProperties.mBluetoothMacAddress;
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

            // Extra information is never copied, since it is OK for it to change
        }

        return dataWasCopied;
    }

    @Override
    public boolean equals(Object otherPeerProperties) {
        PeerProperties other = (PeerProperties)otherPeerProperties;
        boolean isMatch = false;

        if (other != null && other.getBluetoothMacAddress() != null && mBluetoothMacAddress != null) {
            isMatch = other.getBluetoothMacAddress().equals(mBluetoothMacAddress);
        }

        return isMatch;
    }

    @Override
    public String toString() {
        return "[" + mName + " " + mBluetoothMacAddress
                + ((mExtraInformation == NO_EXTRA_INFORMATION) ? "]" : (" " + mExtraInformation + "]"));
    }

    /**
     * @return The number of fields with data.
     */
    private int fieldsWithData() {
        int count = 0;

        if (!isNullOrEmpty(mName) && !mName.equals(NO_PEER_NAME_STRING)) {
            count++;
        }

        if (!isNullOrEmpty(mBluetoothMacAddress)) {
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

        if (mExtraInformation != NO_EXTRA_INFORMATION) {
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
