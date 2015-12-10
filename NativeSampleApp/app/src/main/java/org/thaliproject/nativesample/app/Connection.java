package org.thaliproject.nativesample.app;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
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

    public boolean send(byte[] bytes) {
        return mBluetoothSocketIoThread.write(bytes);
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
     * Note that this callback is called from a different thread. Therefore, it is important to
     * use a handler with the main looper so that we update the UI in the UI thread! We could also
     * use the handler in the MainActivity instead of here.
     *
     * @param reason The reason for disconnect.
     * @param bluetoothSocketIoThread The Bluetooth socket IO thread associated with the connection.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread bluetoothSocketIoThread) {
        Handler handler = new Handler(mContext.getMainLooper());
        final String finalReason = reason;
        final Connection finalConnection = this;

        handler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisconnected(finalReason, finalConnection);
            }
        });
    }
}
