/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

/**
 *
 */
public class PeerDeviceProperties {
    /**
     * Constructor.
     * @param peerId
     * @param peerName
     * @param peerBluetoothAddress
     * @param serviceType
     * @param deviceAddress
     * @param deviceName
     */
    public PeerDeviceProperties(
            String peerId, String peerName, String peerBluetoothAddress,
            String serviceType, String deviceAddress, String deviceName) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.peerBluetoothAddress = peerBluetoothAddress;
        this.serviceType = serviceType;
        this.deviceName =  deviceName;
        this.deviceAddress = deviceAddress;

    }

    final public String peerId;
    final public String peerName;
    final public String peerBluetoothAddress;
    final public String serviceType;
    final public String deviceName;
    final public String deviceAddress;
}
