/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.model;

import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * The model containing discovered peers and connections.
 */
public class PeerAndConnectionModel {
    public interface Listener {
        void onDataChanged();
    }

    private static final String TAG = PeerAndConnectionModel.class.getName();
    private static PeerAndConnectionModel mInstance = null;
    private ArrayList<PeerProperties> mPeers = new ArrayList<PeerProperties>();
    private ArrayList<Connection> mConnections = new ArrayList<Connection>();
    private Listener mListener = null;

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

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void requestUpdateUi() {
        if (mListener != null) {
            mListener.onDataChanged();
        }
    }

    public ArrayList<PeerProperties> getPeers() {
        return mPeers;
    }

    public ArrayList<Connection> getConnections() {
        return mConnections;
    }

    /**
     * Adds the given peer to the list.
     * @param peerProperties The peer to add.
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
     * Tries to remove the given peer.
     * @return True, if the peer was found and removed. False otherwise.
     */
    public boolean removePeer(final PeerProperties peerProperties) {
        boolean wasRemoved = false;

        for (Iterator<PeerProperties> iterator = mPeers.iterator(); iterator.hasNext();) {
            PeerProperties existingPeer = iterator.next();

            if (existingPeer.equals(peerProperties)) {
                iterator.remove();
                wasRemoved = true;
                break;
            }
        }

        if (wasRemoved && mListener != null) {
            mListener.onDataChanged();
        }

        return wasRemoved;
    }

    /**
     * Updates the name of the peer in the list.
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

    /**
     * @return The total number of connections.
     */
    public synchronized int getTotalNumberOfConnections() {
        return mConnections.size();
    }

    /**
     * Checks if we are connected to the given peer (incoming or outgoing).
     * @param peerProperties The peer properties.
     * @return True, if connected. False otherwise.
     */
    public synchronized boolean hasConnectionToPeer(PeerProperties peerProperties) {
        boolean hasConnection = false;

        for (Connection connection : mConnections) {
            if (connection.getPeerProperties().equals(peerProperties)) {
                hasConnection = true;
                break;
            }
        }

        return hasConnection;
    }

    /**
     * Checks if we are connected to the given peer.
     * @param peerId The peer ID.
     * @param isIncoming If true, will check if we have an incoming connection. If false, check if we have an outgoing connection.
     * @return True, if connected. False otherwise.
     */
    public synchronized boolean hasConnectionToPeer(final String peerId, boolean isIncoming) {
        boolean hasConnection = false;
        //int i = 0;

        for (Connection connection : mConnections) {
            //Log.d(TAG, "hasConnectionToPeer: " + ++i + ": " + connection.toString());

            if (connection.getPeerId().equals(peerId) && connection.getIsIncoming() == isIncoming) {
                hasConnection = true;
                break;
            }
        }

        return hasConnection;
    }

    /**
     * Tries to find a connection to the given peer.
     * @param peerProperties The peer properties.
     * @param isIncoming If true, will try to get an incoming connection. If false, an outgoing connection.
     * @return The connection instance or null if not found.
     */
    public synchronized Connection getConnectionToPeer(PeerProperties peerProperties, boolean isIncoming) {
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
            connection.close(true);
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
}
