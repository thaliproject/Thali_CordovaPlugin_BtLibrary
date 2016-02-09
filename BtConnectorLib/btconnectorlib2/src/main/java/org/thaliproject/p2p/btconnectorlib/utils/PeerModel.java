/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.utils;

import android.os.CountDownTimer;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A model for discovered peers.
 *
 * Peer expiration is based on a time elapsed since we last saw the peer.
 */
public class PeerModel {
    public interface Listener {
        /**
         * Called when a new peer is added to the model.
         * @param peerProperties The properties of the added peer.
         */
        void onPeerAdded(PeerProperties peerProperties);

        /**
         * Called when an existing peer is updated.
         * @param peerProperties The properties of the updated peer.
         */
        void onPeerUpdated(PeerProperties peerProperties);

        /**
         * Called when an existing peer expired and was removed from the model.
         * Peer expiration is based on a time elapsed since we last saw the peer.
         * @param peerProperties The properties of the expired and removed peer.
         */
        void onPeerExpiredAndRemoved(PeerProperties peerProperties);
    }

    private static final String TAG = PeerModel.class.getName();
    private final HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
    private final Listener mListener;
    private final DiscoveryManagerSettings mSettings;
    private CountDownTimer mCheckExpiredPeersTimer = null;

    /**
     * Constructor.
     * @param settings The Discovery manager settings.
     */
    public PeerModel(Listener listener, DiscoveryManagerSettings settings) {
        mListener = listener;
        mSettings = settings;
    }

    /**
     * Stops the check expired peers timer and clears the peer container.
     */
    public void clear() {
        if (mCheckExpiredPeersTimer != null) {
            Log.i(TAG, "clear");
            mCheckExpiredPeersTimer.cancel();
            mCheckExpiredPeersTimer = null;
        }

        mDiscoveredPeers.clear();
    }

    /**
     * Recreates the check expired peers timer.
     *
     * Peer expiration is based on a time elapsed since we last saw the peer.
     *
     * This method takes no argument, since it is expected that the time has been updated to the
     * settings, where the timer will retrieve it when reconstructed.
     */
    public void onPeerExpirationTimeChanged() {
        if (mCheckExpiredPeersTimer != null) {
            // Recreate the timer
            createCheckPeerExpirationTimer();
        }
    }

    /**
     * Tries to find a discovered peer with the given device address.
     * @param deviceAddress The device address of a peer to find.
     * @return A peer properties instance if found, null if not.
     */
    public synchronized PeerProperties getDiscoveredPeer(final String deviceAddress) {
        PeerProperties foundPeerProperties = null;

        for (PeerProperties peerProperties : mDiscoveredPeers.keySet()) {
            if (peerProperties != null
                    && peerProperties.getDeviceAddress() != null
                    && peerProperties.getDeviceAddress().equalsIgnoreCase(deviceAddress)) {
                foundPeerProperties = peerProperties;
                break;
            }
        }

        return foundPeerProperties;
    }

    /**
     * Removes the given peer properties from the collection.
     * @param peerPropertiesToRemove The peer properties to remove.
     * @return The found and removed peer properties or null, if not found.
     */
    public synchronized PeerProperties removePeer(PeerProperties peerPropertiesToRemove) {
        PeerProperties oldPeerProperties = null;

        if (peerPropertiesToRemove != null) {
            Iterator iterator = mDiscoveredPeers.entrySet().iterator();

            while (iterator.hasNext()) {
                HashMap.Entry entry = (HashMap.Entry) iterator.next();

                if (entry != null) {
                    PeerProperties peerProperties = (PeerProperties) entry.getKey();

                    if (peerProperties.equals(peerPropertiesToRemove)) {
                        oldPeerProperties = peerProperties;
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        return oldPeerProperties;
    }

    /**
     * Adds or updates the given peer properties to the collection.
     * @param peerPropertiesToAddOrUpdate The peer properties to add/update.
     */
    public synchronized void addOrUpdateDiscoveredPeer(PeerProperties peerPropertiesToAddOrUpdate) {
        if (peerPropertiesToAddOrUpdate != null) {
            //Log.v(TAG, "addOrUpdateDiscoveredPeer: " + peerProperties.toString());
            PeerProperties oldPeerProperties = removePeer(peerPropertiesToAddOrUpdate);

            if (oldPeerProperties != null) {
                // This one was already in the list
                // Make sure we don't lose any data when updating
                Log.v(TAG, "addOrUpdateDiscoveredPeer: Updating the timestamp of peer "
                        + peerPropertiesToAddOrUpdate.toString());

                PeerProperties.copyMissingValuesFromOldPeer(oldPeerProperties, peerPropertiesToAddOrUpdate);

                if (peerPropertiesToAddOrUpdate.hasMoreInformation(oldPeerProperties)) {
                    // The new discovery result has more information than the old one
                    if (mListener != null) {
                        mListener.onPeerUpdated(peerPropertiesToAddOrUpdate);
                    }
                }
            } else {
                // The given peer was not in the list before, hence it is a new one
                Log.d(TAG, "addOrUpdateDiscoveredPeer: Adding a new peer: " + peerPropertiesToAddOrUpdate.toString());

                if (mListener != null) {
                    mListener.onPeerAdded(peerPropertiesToAddOrUpdate);
                }
            }

            mDiscoveredPeers.put(peerPropertiesToAddOrUpdate, new Timestamp(new Date().getTime()));

            if (mCheckExpiredPeersTimer == null) {
                createCheckPeerExpirationTimer();
                mCheckExpiredPeersTimer.start();
            }
        }
    }

    /**
     * Checks the list of peers for expired ones, removes them if found and notifies the listener.
     */
    public synchronized void checkListForExpiredPeers() {
        final Timestamp timestampNow = new Timestamp(new Date().getTime());
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        CopyOnWriteArrayList<PeerProperties> expiredPeers = new CopyOnWriteArrayList<>();

        // Find and copy expired peers to a separate list
        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry)iterator.next();
            PeerProperties entryPeerProperties = (PeerProperties) entry.getKey();
            Timestamp entryTimestamp = (Timestamp) entry.getValue();

            //Log.v(TAG, "checkListForExpiredPeers: Peer " + entryPeerProperties.toString() + " is now "
            //        + ((timestampNow.getTime() - entryTimestamp.getTime()) / 1000) + " seconds old");

            if (timestampNow.getTime() - entryTimestamp.getTime() > mSettings.getPeerExpiration()) {
                Log.d(TAG, "checkListForExpiredPeers: Peer " + entryPeerProperties.toString() + " expired");
                expiredPeers.add(entryPeerProperties);
            }
        }

        if (expiredPeers.size() > 0) {
            // First remove all the expired peers from the list and only then notify the listener
            for (PeerProperties expiredPeer : expiredPeers) {
                removePeer(expiredPeer);
            }

            for (PeerProperties expiredPeer : expiredPeers) {
                if (mListener != null) {
                    mListener.onPeerExpiredAndRemoved(expiredPeer);
                }
            }

            expiredPeers.clear();
        }
    }

    /**
     * Creates the timer for checking peers expired (not seen for a while).
     */
    private synchronized void createCheckPeerExpirationTimer() {
        if (mCheckExpiredPeersTimer != null) {
            mCheckExpiredPeersTimer.cancel();
            mCheckExpiredPeersTimer = null;
        }

        long peerExpirationInMilliseconds = mSettings.getPeerExpiration();

        if (peerExpirationInMilliseconds > 0) {
            long timerTimeout = peerExpirationInMilliseconds / 2;

            mCheckExpiredPeersTimer = new CountDownTimer(timerTimeout, timerTimeout) {
                @Override
                public void onTick(long l) {
                    // Not used
                }

                @Override
                public void onFinish() {
                    checkListForExpiredPeers();

                    if (mDiscoveredPeers.size() == 0) {
                        // No more peers, dispose this timer
                        this.cancel();
                        mCheckExpiredPeersTimer = null;
                    } else {
                        // Restart the timer
                        this.start();
                    }
                }
            };
        }
    }
}
