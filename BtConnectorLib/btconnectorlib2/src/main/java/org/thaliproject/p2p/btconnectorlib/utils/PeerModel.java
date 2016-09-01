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
 * <p>
 * Peer expiration is based on a time elapsed since we last saw the peer.
 */
public class PeerModel {
    public interface Listener {
        /**
         * Called when a new peer is added to the model.
         *
         * @param peerProperties The properties of the added peer.
         */
        void onPeerAdded(PeerProperties peerProperties);

        /**
         * Called when an existing peer is updated.
         *
         * @param peerProperties The properties of the updated peer.
         */
        void onPeerUpdated(PeerProperties peerProperties);

        /**
         * Called when an existing peer expired and was removed from the model.
         * Peer expiration is based on a time elapsed since we last saw the peer.
         *
         * @param peerProperties The properties of the expired and removed peer.
         */
        void onPeerExpiredAndRemoved(PeerProperties peerProperties);
    }

    private static final String TAG = PeerModel.class.getName();
    private final HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final DiscoveryManagerSettings mSettings;
    private CountDownTimer mCheckExpiredPeersTimer = null;

    /**
     * Constructor.
     *
     * @param settings The Discovery manager settings.
     */
    public PeerModel(Listener listener, DiscoveryManagerSettings settings) {
        mSettings = settings;
        addListener(listener);
    }

    /**
     * Adds the given listener.
     *
     * @param listener The listener to add.
     */
    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            Log.d(TAG, "addListener: New listener added - the number of listeners is now " + mListeners.size());
        }
    }

    /**
     * Removes the given listener.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
            Log.d(TAG, "removeListener: Listener removed - the number of listeners is now " + mListeners.size());
        }
    }

    /**
     * Stops the timer for checking for expired peers and clears the peer container.
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
     * <p>
     * Peer expiration is based on a time elapsed since we last saw the peer.
     * <p>
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
     * Tries to find a discovered peer with the given Bluetooth MAC address.
     *
     * @param bluetoothMacAddress The Bluetooth MAC address of a peer to find.
     * @return A peer properties instance if found, null if not.
     */
    public synchronized PeerProperties getDiscoveredPeerByBluetoothMacAddress(final String bluetoothMacAddress) {
        PeerProperties foundPeerProperties = null;

        for (PeerProperties peerProperties : mDiscoveredPeers.keySet()) {
            if (peerProperties != null
                    && peerProperties.getBluetoothMacAddress() != null
                    && peerProperties.getBluetoothMacAddress().equals(bluetoothMacAddress)) {
                foundPeerProperties = peerProperties;
                break;
            }
        }

        return foundPeerProperties;
    }

    /**
     * Tries to find a discovered peer with the given device address.
     *
     * @param deviceAddress The device address of a peer to find.
     * @return A peer properties instance if found, null if not.
     */
    public synchronized PeerProperties getDiscoveredPeerByDeviceAddress(final String deviceAddress) {
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
     *
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
     * return peer properties if they are not expired or null
     *
     * @param peerPropertiesToAddOrUpdate The peer properties .
     * @return The found not expired peer properties or null otherwise.
     */
    public PeerProperties getNotExpiredPeerProperties(PeerProperties peerPropertiesToAddOrUpdate) {
        PeerProperties oldPeerProperties = null;
        Timestamp lastUpdatePeerPropertiesTime = mDiscoveredPeers.get(peerPropertiesToAddOrUpdate);
        long expireTime = System.currentTimeMillis() -
                DiscoveryManagerSettings.DEFAULT_PEER_PROPERTIES_UPDATE_PERIOD_IN_MILLISECONDS;
        if (lastUpdatePeerPropertiesTime != null && expireTime > lastUpdatePeerPropertiesTime.getTime()) {
            removePeer(peerPropertiesToAddOrUpdate);
        } else {
            oldPeerProperties = removePeer(peerPropertiesToAddOrUpdate);
        }
        return oldPeerProperties;
    }

    /**
     * Adds or updates the given peer properties to the collection.
     *
     * @param peerPropertiesToAddOrUpdate The peer properties to add/update.
     */
    public void addOrUpdateDiscoveredPeer(PeerProperties peerPropertiesToAddOrUpdate) {
        synchronized (this) {
            if (peerPropertiesToAddOrUpdate != null) {
                //Log.v(TAG, "addOrUpdateDiscoveredPeer: " + peerProperties.toString());
                PeerProperties oldPeerProperties = getNotExpiredPeerProperties(peerPropertiesToAddOrUpdate);
                if (oldPeerProperties != null) {
                    // This one was already in the list

                    // Check if the extra info differs before copying the data
                    boolean extraInformationDiffers =
                            (peerPropertiesToAddOrUpdate.getExtraInformation()
                                    != oldPeerProperties.getExtraInformation());

                    // Make sure we don't lose any data when updating
                    PeerProperties.copyMissingValuesFromOldPeer(oldPeerProperties, peerPropertiesToAddOrUpdate);

                    if (peerPropertiesToAddOrUpdate.hasMoreInformation(oldPeerProperties)
                            || extraInformationDiffers) {
                        // The new discovery result has new/more information than the old one
                        for (Listener listener : mListeners) {
                            listener.onPeerUpdated(peerPropertiesToAddOrUpdate);
                        }
                    }
                } else {
                    // The given peer was not in the list before, hence it is a new one
                    for (Listener listener : mListeners) {
                        listener.onPeerAdded(peerPropertiesToAddOrUpdate);
                    }

                }

                mDiscoveredPeers.put(peerPropertiesToAddOrUpdate, new Timestamp(new Date().getTime()));

                Log.v(TAG, "addOrUpdateDiscoveredPeer: "
                        + ((oldPeerProperties == null)
                        ? ("New peer, " + peerPropertiesToAddOrUpdate.toString() + ", added")
                        : ("Timestamp of peer " + peerPropertiesToAddOrUpdate.toString() + " updated"))
                        + " - the peer count is " + mDiscoveredPeers.size());

                if (mCheckExpiredPeersTimer == null) {
                    createCheckPeerExpirationTimer();
                    mCheckExpiredPeersTimer.start();
                }
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
            HashMap.Entry entry = (HashMap.Entry) iterator.next();
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
                for (Listener listener : mListeners) {
                    listener.onPeerExpiredAndRemoved(expiredPeer);
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
