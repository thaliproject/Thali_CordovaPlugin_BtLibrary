/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.model;

import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The model containing discovered peers and connections.
 */
public class PeerAndConnectionModel {
    public interface Listener {
        void onDataChanged();
        void onPeerRemoved(PeerProperties peerProperties);
    }

    private static final String TAG = PeerAndConnectionModel.class.getName();
    private static PeerAndConnectionModel mInstance = null;
    private ArrayList<PeerProperties> mPeers = new ArrayList<PeerProperties>();
    private ArrayList<PeerProperties> mPeersBeingConnectedTo = new ArrayList<PeerProperties>();
    private ArrayList<Connection> mConnections = new ArrayList<Connection>();
    private Listener mListener = null;

    /**
     * @return The singleton instance of this class.
     */
    public static PeerAndConnectionModel getInstance() {
        if (mInstance == null) {
            mInstance = new PeerAndConnectionModel();
        }

        return mInstance;
    }

    /**
     * Private constructor.
     */
    private PeerAndConnectionModel() {
    }

    /**
     * Sets the listener of this model.
     * @param listener The listener to set.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Notifies the listener that the data of this model has changed. This should update the UI.
     */
    public void requestUpdateUi() {
        if (mListener != null) {
            mListener.onDataChanged();
        }
    }

    /**
     * @return All the peers.
     */
    public ArrayList<PeerProperties> getPeers() {
        return mPeers;
    }

    /**
     * Adds the given peer properties to the list or tries to update an existing properties, if they
     * share the same peer ID. Notifies the listener, if added to the list or updated.
     * @param peerProperties The peer to add or update.
     * @return True, if the peer was added. False, if it was already in the list.
     */
    public synchronized boolean addOrUpdatePeer(PeerProperties peerProperties) {
        boolean alreadyInTheList = false;
        boolean wasUpdated = false;
        final String newPeerId = peerProperties.getId();
        int index = 0;

        for (PeerProperties existingPeerProperties : mPeers) {
            if (existingPeerProperties.getId().equals(newPeerId)) {
                // Update the peer
                try {
                    mPeers.get(index).copyFrom(peerProperties);
                    wasUpdated = true;
                } catch (Exception e) {
                    Log.e(TAG, "addOrUpdatePeer: Failed to update the peer name of peer "
                            + peerProperties + ": " + e.getMessage(), e);
                }

                alreadyInTheList = true;
                break;
            }

            index++;
        }

        if (alreadyInTheList) {
            if (wasUpdated) {
                Log.i(TAG, "addOrUpdatePeer: Peer " + peerProperties.toString() + " updated");
            } else {
                Log.i(TAG, "addOrUpdatePeer: Peer " + peerProperties.toString() + " already in the list");
            }
        } else {
            mPeers.add(peerProperties);
            Log.i(TAG, "addOrUpdatePeer: Peer " + peerProperties.toString() + " added to list");
        }

        if (!alreadyInTheList || wasUpdated && mListener != null) {
            mListener.onDataChanged();
        }

        return !alreadyInTheList;
    }

    /**
     * Tries to remove a peer with the given properties from the list of discovered peers.
     * Notifies the listener, if removed.
     * @return True, if the peer was found and removed. False otherwise.
     */
    public boolean removePeer(final PeerProperties peerProperties) {
        boolean wasRemoved = removePeerPropertiesFromList(peerProperties, mPeers);

        if (wasRemoved && mListener != null) {
            mListener.onPeerRemoved(peerProperties);
        }

        return wasRemoved;
    }

    /**
     * Updates the name of the peer in the list. Notifies the listener, if updated.
     * @param peerProperties The peer properties containing the new name.
     * @return True, if the name was updated. False, if not found.
     */
    public synchronized boolean updatePeerName(final PeerProperties peerProperties) {
        boolean wasUpdated = false;

        for (int i = 0; i < mPeers.size(); ++i) {
            try {
                if (mPeers.get(i).equals(peerProperties)) {
                    mPeers.get(i).setName(peerProperties.getName());
                    wasUpdated = true;
                    break;
                }
            } catch (Exception e) {
            }
        }

        if (wasUpdated && mListener != null) {
            mListener.onDataChanged();
        }

        return wasUpdated;
    }

    public synchronized boolean isConnectingToPeer(final PeerProperties peerProperties) {
        return doesListContainPeerProperties(peerProperties, mPeersBeingConnectedTo);
    }

    /**
     * Adds the properties of a peer currently being connected to to the list.
     * Notifies the listener, if added to the list.
     * @param peerProperties The peer properties to add.
     * @return True, if added. False, if already in the list.
     */
    public synchronized boolean addPeerBeingConnectedTo(final PeerProperties peerProperties) {
        boolean alreadyInTheList = doesListContainPeerProperties(peerProperties, mPeersBeingConnectedTo);

        if (!alreadyInTheList) {
            mPeersBeingConnectedTo.add(peerProperties);

            if (mListener != null) {
                mListener.onDataChanged();
            }
        }

        return !alreadyInTheList;
    }

    /**
     * Tries to remove a peer with the given properties from the list of peers being currently
     * connected to. Notifies the listener, if removed.
     * @param peerProperties The peer properties.
     * @return True, if found and removed. False otherwise.
     */
    public synchronized boolean removePeerBeingConnectedTo(final PeerProperties peerProperties) {
        boolean wasRemoved = removePeerPropertiesFromList(peerProperties, mPeersBeingConnectedTo);

        if (wasRemoved && mListener != null) {
            mListener.onDataChanged();
        }

        return wasRemoved;
    }

    /**
     * @return The total number of connections.
     */
    public synchronized int getTotalNumberOfConnections() {
        return mConnections.size();
    }

    /**
     * @return All the connections.
     */
    public ArrayList<Connection> getConnections() {
        return mConnections;
    }

    /**
     * Tries to find a connection to the given peer.
     * @param peerProperties The peer properties.
     * @param isIncoming If true, will try to get an incoming connection. If false, an outgoing connection.
     * @return The connection instance or null if not found.
     */
    public synchronized Connection getConnectionToPeer(final PeerProperties peerProperties, boolean isIncoming) {
        Connection connection = null;

        for (Connection existingConnection : mConnections) {
            if (existingConnection.getPeerProperties().equals(peerProperties)
                    && existingConnection.getIsIncoming() == isIncoming) {
                connection = existingConnection;
                break;
            }
        }

        return connection;
    }

    /**
     * Checks if we are connected to the given peer.
     * @param peerProperties The peer properties.
     * @param isIncoming If true, will check if we have an incoming connection.
     *                   If false, check if we have an outgoing connection.
     * @return True, if connected. False otherwise.
     */
    public synchronized boolean hasConnectionToPeer(final PeerProperties peerProperties, boolean isIncoming) {
        return (getConnectionToPeer(peerProperties, isIncoming) != null);
    }

    /**
     * Checks if we are connected to the given peer (incoming or outgoing).
     * @param peerProperties The peer properties.
     * @return True, if connected. False otherwise.
     */
    public synchronized boolean hasConnectionToPeer(final PeerProperties peerProperties) {
        return (getConnectionToPeer(peerProperties, true) != null
                || getConnectionToPeer(peerProperties, false) != null);
    }

    /**
     * Tries to close an outgoing connection to a peer with the given properties.
     * @param peerProperties The peer properties.
     * @return True, if the connection was closed. False otherwise.
     */
    public synchronized boolean closeConnection(PeerProperties peerProperties) {
        boolean wasClosed = false;
        Connection connection = getConnectionToPeer(peerProperties, false); // Outgoing connections only

        if (connection != null) {
            connection.disconnect();
            wasClosed = true;
        }

        return wasClosed;
    }

    /**
     * Closes all connections.
     */
    public synchronized void closeAllConnections() {
        for (Connection connection : mConnections) {
            connection.disconnect();
        }

        mConnections.clear();
    }

    /**
     * Adds/removes a connection from the list of connections.
     * @param connection The connection to add/remove.
     * @param add If true, will add the given connection. If false, will remove it.
     * @return True, if was added/removed successfully. False otherwise.
     */
    public synchronized boolean addOrRemoveConnection(Connection connection, boolean add) {
        boolean wasAddedOrRemoved = false;

        if (connection != null) {
            if (add) {
                wasAddedOrRemoved = mConnections.add(connection);
            } else {
                // Remove
                for (Iterator<Connection> iterator = mConnections.iterator(); iterator.hasNext();) {
                    Connection existingConnection = iterator.next();

                    if (existingConnection.equals(connection)) {
                        iterator.remove();
                        wasAddedOrRemoved = true;
                        // Do not break just to make sure we get any excess connections
                    }
                }
            }
        }

        if (wasAddedOrRemoved && mListener != null) {
            mListener.onDataChanged();
        }

        return wasAddedOrRemoved;
    }

    /**
     * Sets the given buffer size to all existing connections.
     * @param bufferSize The buffer size in bytes.
     */
    public void setBufferSizeOfConnections(int bufferSize) {
        for (Connection connection : mConnections) {
            connection.setBufferSize(bufferSize);
        }
    }

    /**
     * Checks if the given peer properties exist in the given list.
     * @param peerProperties The peer properties to find.
     * @param peerPropertiesList The list of peer properties to search from.
     * @return True, if found. False otherwise.
     */
    private synchronized boolean doesListContainPeerProperties(
            final PeerProperties peerProperties, final List<PeerProperties> peerPropertiesList) {
        boolean peerPropertiesFound = false;

        for (PeerProperties existingPeerProperties : peerPropertiesList) {
            if (existingPeerProperties.equals(peerProperties)) {
                peerPropertiesFound = true;
                break;
            }
        }

        return peerPropertiesFound;
    }

    /**
     * Tries to remove the given peer properties from the given list.
     * @param peerProperties The peer properties to remove.
     * @param peerPropertiesList The list of peer properties to remove from.
     * @return True, if found and removed. False otherwise.
     */
    private synchronized boolean removePeerPropertiesFromList(
            final PeerProperties peerProperties, List<PeerProperties> peerPropertiesList) {
        boolean wasRemoved = false;

        for (Iterator<PeerProperties> iterator = peerPropertiesList.iterator(); iterator.hasNext();) {
            PeerProperties existingPeerProperties = iterator.next();

            if (existingPeerProperties.equals(peerProperties)) {
                iterator.remove();
                wasRemoved = true;
                break;
            }
        }

        return wasRemoved;
    }
}
