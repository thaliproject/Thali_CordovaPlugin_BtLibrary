package org.thaliproject.nativesample.app;

import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.ArrayList;

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
        int i = 0;

        for (Connection connection : mConnections) {
            Log.d(TAG, "hasConnectionToPeer: " + ++i + ": "
                    + connection.getPeerProperties().toString() + ", is incoming: "
                    + connection.getIsIncoming());

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
    public void closeAllConnections() {
        for (Connection connection : mConnections) {
            connection.close(true);
        }

        mConnections.clear();
    }

    /**
     *
     * @param connection
     */
    public void addConnection(Connection connection) {
        if (connection != null) {
            mConnections.add(connection);

            if (mListener != null) {
                mListener.onDataChanged();
            }
        }
    }

    /**
     *
     * @param connection
     * @return
     */
    public synchronized boolean removeConnection(Connection connection) {
        boolean wasRemoved = false;

        for (Connection existingConnection : mConnections) {
            if (existingConnection.equals(connection)) {
                mConnections.remove(existingConnection);
                wasRemoved = true;
                // Do not break just to make sure we get any excess connections
            }
        }

        if (wasRemoved && mListener != null) {
            mListener.onDataChanged();
        }

        return wasRemoved;
    }
}
