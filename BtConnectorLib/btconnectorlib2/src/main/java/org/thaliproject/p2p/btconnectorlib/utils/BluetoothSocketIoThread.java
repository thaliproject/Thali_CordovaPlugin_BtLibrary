/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.utils;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Thread for reading bytes from a Bluetooth socket and provides a method to write bytes to a socket.
 * This class is public, since the implementation is generic and can be utilized by client applications.
 */
public class BluetoothSocketIoThread extends Thread {
    /**
     * Thread listener.
     */
    public interface Listener {
        /**
         * Called when bytes were successfully read.
         * @param bytes The array of bytes read.
         * @param size The size of the array.
         * @param who The related BluetoothSocketIoThread instance.
         */
        void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who);

        /**
         * Called when bytes were written successfully.
         * @param bytes The array of bytes written.
         * @param size The size of the array.
         * @param who The related BluetoothSocketIoThread instance.
         */
        void onBytesWritten(byte[] bytes, int size, BluetoothSocketIoThread who);

        /**
         * Called when the socket associated with the BluetoothSocketIoThread instance is disconnected.
         * @param reason The reason why we got disconnected. Contains an exception message in case of failure.
         * @param who The related BluetoothSocketIoThread instance.
         */
        void onDisconnected(String reason, BluetoothSocketIoThread who);
    }

    private static final String TAG = BluetoothSocketIoThread.class.getName();
    protected static final int DEFAULT_BUFFER_SIZE_IN_BYTES = 256;
    private final BluetoothSocket mSocket;
    private final Listener mListener;
    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private PeerProperties mPeerProperties;
    private int mBufferSizeInBytes = DEFAULT_BUFFER_SIZE_IN_BYTES;
    private boolean mExitThreadAfterRead = false;
    private boolean mIsShuttingDown = false;

    /**
     * Constructor.
     * @param socket A Bluetooth socket.
     * @param listener The listener.
     * @throws NullPointerException Thrown, if either the listener or the Bluetooth socket instance is null.
     * @throws IOException Thrown in case of failure to get the input and the output streams for the given socket.
     */
    public BluetoothSocketIoThread(BluetoothSocket socket, Listener listener)
            throws NullPointerException, IOException {
        if (socket == null || listener == null) {
            throw new NullPointerException("Either the Bluetooth socket or the listener instance is null");
        }

        mListener = listener;
        mSocket = socket;
        mInputStream = mSocket.getInputStream();
        mOutputStream = mSocket.getOutputStream();
        mPeerProperties = new PeerProperties();
    }

    public BluetoothSocket getSocket() {
        return mSocket;
    }

    public PeerProperties getPeerProperties() {
        return mPeerProperties;
    }

    public void setPeerProperties(PeerProperties peerProperties) {
        mPeerProperties = peerProperties;
    }

    /**
     * Sets whether the thread should exit after a read() call or not.
     * @param exit If true, will exit after one read() call. If false, will keep reading until closed.
     */
    public void setExitThreadAfterRead(boolean exit) {
        mExitThreadAfterRead = exit;
    }

    /**
     * Returns the buffer size used by the input stream.
     * @return The buffer size in bytes.
     */
    public int getBufferSize() {
        return mBufferSizeInBytes;
    }

    /**
     * Sets the buffer size used by the input stream.
     * Note that the buffer size needs to be set before calling start(). Otherwise, it will have no effect.
     * @param bufferSizeInBytes The buffer size in bytes.
     */
    public void setBufferSize(int bufferSizeInBytes) {
        if (bufferSizeInBytes > 0) {
            mBufferSizeInBytes = bufferSizeInBytes;
        }
    }

    /**
     * From Thread.
     *
     * Keeps reading the input stream of the socket until closed or disconnected (unless is set to
     * exit after one read() call).
     */
    @Override
    public void run() {
        Log.d(TAG, "Entering thread (ID: " + getId() + ")");
        byte[] buffer = new byte[mBufferSizeInBytes];
        int numberOfBytesRead = 0;

        while (!mIsShuttingDown) {
            try {
                numberOfBytesRead = mInputStream.read(buffer); // Blocking call
            } catch (IOException e) {
                if (!mIsShuttingDown) {
                    Log.d(TAG, "Disconnected: " + e.getMessage());
                    mListener.onDisconnected(e.getMessage(), this);
                }

                break;
            }

            if (numberOfBytesRead > 0) {
                mListener.onBytesRead(buffer, numberOfBytesRead, this);
            }

            if (mExitThreadAfterRead) {
                break;
            }
        }

        Log.d(TAG, "Exiting thread (ID: " + getId() + ")");
    }

    /**
     * Writes the given bytes to the output stream of the socket.
     * @param bytes The bytes to write.
     * @return True, if the given bytes were written successfully. False otherwise.
     */
    public boolean write(byte[] bytes) {
        boolean wasSuccessful = false;

        if (mOutputStream != null) {
            try {
                mOutputStream.write(bytes);
                wasSuccessful = true;
            } catch (IOException e) {
                if (!mIsShuttingDown) {
                    Log.e(TAG, "write: Failed to write to output stream: " + e.getMessage(), e);
                }
            }
        } else {
            Log.e(TAG, "write: No output stream!");
        }

        if (wasSuccessful) {
            mListener.onBytesWritten(bytes, bytes.length, this);
        }

        return wasSuccessful;
    }

    /**
     * Closes the input and output streams and, if requested, the socket.
     * Note that after calling this method, this instance is no longer in valid state and must be
     * disposed of.
     * @param closeSocket If true, will close the socket. Otherwise only the streams are closed.
     */
    public synchronized void close(boolean closeSocket) {
        mIsShuttingDown = true;

        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close the input stream: " + e.getMessage() + " (thread ID: " + getId() + ")");
            }
        }

        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close the output stream: " + e.getMessage() + " (thread ID: " + getId() + ")");
            }
        }

        if (closeSocket && mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close the socket: " + e.getMessage() + " (thread ID: " + getId() + ")");
            }
        }
    }
}
