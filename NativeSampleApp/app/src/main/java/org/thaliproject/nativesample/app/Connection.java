package org.thaliproject.nativesample.app;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import java.io.IOException;

/**
 * Represents a two-way connection to a peer.
 */
public class Connection implements BluetoothSocketIoThread.Listener {
    public interface Listener {
        void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who);
        void onBytesWritten(byte[] bytes, int size, BluetoothSocketIoThread who);
        void onDisconnected(String reason, Connection who);
    }

    private static final String TAG = Connection.class.getName();
    private static final byte[] PING_PACKAGE = new String("Is there anybody out there?").getBytes();
    private static final int SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES = 1024 * 2;
    private Listener mListener = null;
    private Context mContext = null;
    private BluetoothSocketIoThread mBluetoothSocketIoThread = null;
    private PeerProperties mPeerProperties = null;
    private String mPeerId = null;
    private boolean mIsIncoming = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param context The application context (for using handler in onDisconnected).
     * @param bluetoothSocket The Bluetooth socket associated with this connection.
     * @param peerProperties The peer properties.
     * @param isIncoming If true, this connection is incoming. If false, this is an outgoing connection.
     * @throws IOException
     * @throws NullPointerException
     */
    public Connection(
            Listener listener, Context context,
            BluetoothSocket bluetoothSocket, PeerProperties peerProperties,
            boolean isIncoming)
        throws IOException, NullPointerException {
        mListener = listener;
        mContext = context;
        mBluetoothSocketIoThread = new BluetoothSocketIoThread(bluetoothSocket, this);
        mBluetoothSocketIoThread.setPeerProperties(peerProperties);
        mBluetoothSocketIoThread.setBufferSize(SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);
        mIsIncoming = isIncoming;
        mPeerProperties = peerProperties;
        mPeerId = mPeerProperties.getId();

        mBluetoothSocketIoThread.start();
    }

    public String getPeerId() {
        return mPeerId;
    }

    public PeerProperties getPeerProperties() {
        return mPeerProperties;
    }

    public boolean getIsIncoming() {
        return mIsIncoming;
    }

    public void send(byte[] bytes) {
        SocketWriterThread thread = new SocketWriterThread();
        thread.write(bytes);
    }

    public void ping() {
        send(PING_PACKAGE);
    }

    public void close(boolean closeSocket) {
        mBluetoothSocketIoThread.close(closeSocket);
        Log.d(TAG, "close: Closed");
    }

    @Override
    public boolean equals(Object object) {
        Connection otherConnection = null;

        try {
            otherConnection = (Connection)object;
        } catch (Exception e) {
        }

        return (otherConnection != null
            && otherConnection.getPeerId().equals(mPeerId)
            && otherConnection.getIsIncoming() == mIsIncoming);
    }

    @Override
    public String toString() {
        return "[" + mPeerId + " " + mPeerProperties.getName() + " " + mIsIncoming + "]";
    }

    @Override
    public void onBytesRead(byte[] bytes, int i, BluetoothSocketIoThread bluetoothSocketIoThread) {
        mListener.onBytesRead(bytes, i, bluetoothSocketIoThread);
    }

    @Override
    public void onBytesWritten(byte[] bytes, int i, BluetoothSocketIoThread bluetoothSocketIoThread) {
        mListener.onBytesWritten(bytes, i, bluetoothSocketIoThread);
    }

    /**
     * Forwards the event to the listener. The Bluetooth socket IO thread will always be the member
     * instance of this class.
     * @param reason The reason for disconnect.
     * @param bluetoothSocketIoThread The Bluetooth socket IO thread associated with the connection.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread bluetoothSocketIoThread) {
        mListener.onDisconnected(reason, this);
    }

    private class SocketWriterThread extends Thread {
        private byte[] mBytesToWrite = null;

        public void write(byte[] bytes) {
            mBytesToWrite = bytes;
            this.start();
        }

        @Override
        public void run() {
            if (!mBluetoothSocketIoThread.write(mBytesToWrite)) {
                Log.e(TAG, "Failed to write " + mBytesToWrite.length + " bytes");
            }
        }
    }
}
