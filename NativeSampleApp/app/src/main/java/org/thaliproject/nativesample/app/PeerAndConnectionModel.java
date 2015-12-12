package org.thaliproject.nativesample.app;

import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
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

    public ArrayList<PeerProperties> getPeers() {
        return mPeers;
    }

    public ArrayList<Connection> getConnections() {
        return mConnections;
    }

    /**
     *
     * @param peerProperties
     * @return
     */
    public synchronized boolean addPeer(PeerProperties peerProperties) {
        boolean alreadyInTheList = false;
        final String newPeerId = peerProperties.getId();

        for (PeerProperties existingPeerProperties : mPeers) {
            if (existingPeerProperties.getId().equals(newPeerId)) {
                alreadyInTheList = true;
                break;
            }
        }

        if (alreadyInTheList) {
            Log.i(TAG, "addPeer: Peer " + peerProperties.toString() + " already in the list");
        } else {
            mPeers.add(peerProperties);
            Log.i(TAG, "addPeer: Peer " + peerProperties.toString() + " added to list");

            if (mListener != null) {
                mListener.onDataChanged();
            }
        }

        return !alreadyInTheList;
    }

    /**
     *
     * @return
     */
    public synchronized int getTotalNumberOfConnections() {
        return mConnections.size();
    }

    /**
     *
     * @param peerId
     * @param isIncoming
     * @return
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
     *
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
                        mConnections.remove(existingConnection);
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
}
