package org.thaliproject.nativesample.app;

import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import java.util.ArrayList;
import java.util.HashMap;

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
    private HashMap<String, BluetoothSocketIoThread> mIncomingConnections = new HashMap<String, BluetoothSocketIoThread>();
    private HashMap<String, BluetoothSocketIoThread> mOutgoingConnections = new HashMap<String, BluetoothSocketIoThread>();
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

    public HashMap<String, BluetoothSocketIoThread> getIncomingConnections() {
        return mIncomingConnections;
    }

    public HashMap<String, BluetoothSocketIoThread> getOutgoingConnections() {
        return mOutgoingConnections;
    }

    /**
     *
     * @param peerProperties
     * @return
     */
    public boolean addPeer(PeerProperties peerProperties) {
        boolean wasAdded = false;

        if (!mPeers.contains(peerProperties)) {
            mPeers.add(peerProperties);
            Log.i(TAG, "addPeer: Peer " + peerProperties.toString() + " added to list");
            wasAdded = true;

            if (mListener != null) {
                mListener.onDataChanged();
            }
        } else {
            Log.i(TAG, "addPeer: Peer " + peerProperties.toString() + " already in the list");
        }

        return wasAdded;
    }

    /**
     *
     * @return
     */
    public int getTotalNumberOfConnections() {
        return mOutgoingConnections.size() + mIncomingConnections.size();
    }

    public boolean hasIncomingConnectionToPeer(String peerId) {
        return (mIncomingConnections.containsKey(peerId));
    }

    public boolean hasOutgoingConnectionToPeer(String peerId) {
        return (mOutgoingConnections.containsKey(peerId));
    }

    /**
     *
     */
    public void closeAllConnections() {
        for (BluetoothSocketIoThread socketIoThread : mOutgoingConnections.values()) {
            socketIoThread.close(true);
        }

        mOutgoingConnections.clear();

        for (BluetoothSocketIoThread socketIoThread : mIncomingConnections.values()) {
            socketIoThread.close(true);
        }

        mIncomingConnections.clear();

        if (mListener != null) {
            mListener.onDataChanged();
        }
    }

    /**
     *
     * @param peerId
     * @param bluetoothSocketIoThread
     * @param isIncoming
     */
    public void addConnection(String peerId, BluetoothSocketIoThread bluetoothSocketIoThread, boolean isIncoming) {
        if (bluetoothSocketIoThread != null) {
            if (isIncoming) {
                mIncomingConnections.put(peerId, bluetoothSocketIoThread);
            } else {
                mOutgoingConnections.put(peerId, bluetoothSocketIoThread);
            }

            if (mListener != null) {
                mListener.onDataChanged();
            }
        }
    }

    /**
     *
     * @param peerId
     * @param bluetoothSocketIoThread
     * @return
     */
    public boolean removeConnection(String peerId, BluetoothSocketIoThread bluetoothSocketIoThread) {
        boolean wasRemoved = false;

        if (mIncomingConnections.values().contains(bluetoothSocketIoThread)) {
            if (mIncomingConnections.remove(peerId) != null) {
                wasRemoved = true;
            }
        } else if (mOutgoingConnections.values().contains(bluetoothSocketIoThread)) {
            if (mOutgoingConnections.remove(peerId) != null) {
                wasRemoved = true;
            }
        }

        if (wasRemoved && mListener != null) {
            mListener.onDataChanged();
        }

        return wasRemoved;
    }
}
