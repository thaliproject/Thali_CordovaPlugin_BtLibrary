/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.utils;

import org.thaliproject.nativetest.app.model.Connection;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * Utility classes/methods for managing application specific menus.
 */
public class MenuUtils {
    /**
     * Contains information on which items in the peer context menu should be available
     * (enabled and visible).
     */
    public static class PeerMenuItemsAvailability {
        public boolean connectMenuItemAvailable = false;
        public boolean sendDataMenuItemAvailable = false;
        public boolean disconnectMenuItemAvailable = false;
        public boolean killAllConnectionsMenuItemAvailable = false;
    }

    /**
     * Resolves the menu items, which should be enabled/disabled, for the given peer.
     * @param peerProperties The peer properties for the menu context.
     * @param model The peer and connection model.
     * @return The resolved availability for the menu items.
     */
    public static PeerMenuItemsAvailability resolvePeerMenuItemsAvailability(
            PeerProperties peerProperties, PeerAndConnectionModel model) {
        PeerMenuItemsAvailability availability = new PeerMenuItemsAvailability();

        if (model.getTotalNumberOfConnections() > 0) {
            availability.killAllConnectionsMenuItemAvailable = true;
        }

        if (peerProperties != null) {
            Connection connection = model.getConnectionToPeer(peerProperties, false);

            if (connection != null) {
                // We have an outgoing connection
                if (!connection.isSendingData()) {
                    availability.sendDataMenuItemAvailable = true;
                }

                availability.disconnectMenuItemAvailable = true;
            } else {
                connection = model.getConnectionToPeer(peerProperties, true);

                if (connection != null) {
                    // We have an incoming connection
                    if (!model.isConnectingToPeer(peerProperties)) {
                        // Can connect (outgoing)
                        availability.connectMenuItemAvailable = true;
                    }

                    if (!connection.isSendingData()) {
                        availability.sendDataMenuItemAvailable = true;
                    }
                } else if (!model.isConnectingToPeer(peerProperties)) {
                    // No connection and not currently trying to connecting to this peer
                    availability.connectMenuItemAvailable = true;
                }
            }
        }

        return availability;
    }
}
