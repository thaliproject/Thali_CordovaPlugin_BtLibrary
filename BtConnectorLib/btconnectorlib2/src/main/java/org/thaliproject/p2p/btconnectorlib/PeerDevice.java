/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib;

/**
 *
 */
public class PeerDevice {
    /**
     * Constructor.
     * @param peerId
     * @param peerName
     * @param peerAddress
     * @param serviceType
     * @param deviceAddress
     * @param deviceName
     */
    public PeerDevice(
            String peerId, String peerName, String peerAddress,
            String serviceType, String deviceAddress, String deviceName) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.peerAddress = peerAddress;
        this.serviceType = serviceType;
        this.deviceAddress = deviceAddress;
        this.deviceName =  deviceName;
    }

    final public String peerId;
    final public String peerName;
    final public String peerAddress;
    final public String serviceType;
    final public String deviceAddress;
    final public String deviceName;
}
