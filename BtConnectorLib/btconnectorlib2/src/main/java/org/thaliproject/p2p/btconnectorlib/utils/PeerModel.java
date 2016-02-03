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
         * @param peerProperties The properties of the expired and removed peer.
         */
        void onPeerExpiredAndRemoved(PeerProperties peerProperties);
    }

    private static final String TAG = PeerModel.class.getName();
    private final HashMap<Timestamp, PeerProperties> mDiscoveredPeers = new HashMap<>();
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
        Log.i(TAG, "clear");

        if (mCheckExpiredPeersTimer != null) {
            mCheckExpiredPeersTimer.cancel();
            mCheckExpiredPeersTimer = null;
        }

        mDiscoveredPeers.clear();
    }

    /**
     * Recreates the check expired peers timer.
     * @param peerExpirationInMilliseconds Not used, since it is expected that the time has been
     *                                     updated to the settings, where the timer will retrieve it
     *                                     when reconstructed.
     */
    public void updatePeerExpirationTime(long peerExpirationInMilliseconds) {
        Log.d(TAG, "updatePeerExpirationTime: " + peerExpirationInMilliseconds);

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
    public synchronized PeerProperties findDiscoveredPeer(final String deviceAddress) {
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        PeerProperties peerProperties = null;

        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry) iterator.next();
            PeerProperties existingPeerProperties = (PeerProperties) entry.getValue();

            if (existingPeerProperties != null
                    && existingPeerProperties.getDeviceAddress() != null
                    && existingPeerProperties.getDeviceAddress().equalsIgnoreCase(deviceAddress)) {
                peerProperties = existingPeerProperties;
                break;
            }
        }

        return peerProperties;
    }

    /**
     * Tries to modify the list of discovered peers.
     * @param peerProperties The properties of the peer to modify (add/update or remove).
     * @parma addOrUpdate If true, will add/update. If false, will remove.
     * @return True, if success. False otherwise.
     */
    public synchronized boolean modifyListOfDiscoveredPeers(PeerProperties peerProperties, boolean addOrUpdate) {
        //Log.v(TAG, "modifyListOfDiscoveredPeers: " + peerProperties.toString() + ", add/update: " + addOrUpdate);
        Iterator iterator = mDiscoveredPeers.entrySet().iterator();
        PeerProperties oldPeerProperties = null;

        // Always remove first
        while (iterator.hasNext()) {
            HashMap.Entry entry = (HashMap.Entry) iterator.next();

            if (entry != null) {
                PeerProperties existingPeerProperties = (PeerProperties) entry.getValue();

                if (existingPeerProperties.equals(peerProperties)) {
                    oldPeerProperties = existingPeerProperties;
                    iterator.remove();
                    break;
                }
            }
        }

        boolean success = false;

        if (addOrUpdate) {
            if (oldPeerProperties != null) {
                // This one was already in the list (same ID)
                // Make sure we don't lose any data when updating
                Log.v(TAG, "modifyListOfDiscoveredPeers: Updating the timestamp of peer "
                        + peerProperties.toString());

                PeerProperties.checkNewPeerForMissingInformation(oldPeerProperties, peerProperties);

                if (peerProperties.hasMoreInformation(oldPeerProperties)) {
                    // The new discovery result has more information than the old one
                    if (mListener != null) {
                        mListener.onPeerUpdated(peerProperties);
                    }
                }
            } else {
                // The given peer was not in the list before, hence it is a new one
                Log.d(TAG, "modifyListOfDiscoveredPeers: Adding a new peer: " + peerProperties.toString());

                if (mListener != null) {
                    mListener.onPeerAdded(peerProperties);
                }
            }

            mDiscoveredPeers.put(new Timestamp(new Date().getTime()), peerProperties);

            if (mCheckExpiredPeersTimer == null) {
                createCheckPeerExpirationTimer();
                mCheckExpiredPeersTimer.start();
            }

            success = true;
        } else if (oldPeerProperties != null) {
            Log.d(TAG, "modifyListOfDiscoveredPeers: Removed peer " + peerProperties.toString());
            success = true;
        }

        return success;
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
            Timestamp entryTimestamp = (Timestamp)entry.getKey();
            PeerProperties entryPeerProperties = (PeerProperties)entry.getValue();

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
                modifyListOfDiscoveredPeers(expiredPeer, false);
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
