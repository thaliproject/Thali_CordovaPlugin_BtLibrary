/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.List;

/**
 * Service (peer) discovery listener.
 */
public interface ServiceDiscoveryListener {
    /**
     * Called when the list of discovered peers (with the appropriate services) is changed.
     * @param peerPropertiesList The new list of peers (with the appropriate services) available.
     */
    void onServiceListChanged(List<PeerProperties> peerPropertiesList);

    /**
     * Called when a new peer (with an appropriate service) is discovered.
     * @param peerProperties The discovered peer device with an appropriate service.
     */
    void onServiceDiscovered(PeerProperties peerProperties);
}
