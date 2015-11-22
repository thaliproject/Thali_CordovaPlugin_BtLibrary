/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Thread for reading bytes from a Bluetooth socket and provides a method to write bytes to a socket.
 */
class BluetoothSocketIoThread extends Thread {
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
    private static final int BUFFER_SIZE_IN_BYTES = 256;
    private final BluetoothSocket mSocket;
    private final Listener mHandler;
    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private String mPeerId = "";
    private String mPeerName = "";
    private String mPeerAddress = "";
    private boolean mIsClosingSocket = false;

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

        mHandler = listener;
        mSocket = socket;
        mInputStream = mSocket.getInputStream();
        mOutputStream = mSocket.getOutputStream();
    }

    public BluetoothSocket getSocket() {
        return mSocket;
    }

    /**
     * From Thread.
     *
     * Keeps reading the input stream of the socket until disconnected.
     */
    @Override
    public void run() {
        Log.i(TAG, "Thread started");
        byte[] buffer = new byte[BUFFER_SIZE_IN_BYTES];
        int numberOfBytesRead;

        try {
            numberOfBytesRead = mInputStream.read(buffer);
            mHandler.onBytesRead(buffer, numberOfBytesRead, this);
        } catch (IOException e) {
            Log.i(TAG, "Disconnected: " + e.getMessage());
            mHandler.onDisconnected(e.getMessage(), this);
        }

        Log.i(TAG, "Thread stopping");
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
                if (!mIsClosingSocket) {
                    Log.e(TAG, "write: Failed to write to output stream: " + e.getMessage(), e);
                }
            }
        } else {
            Log.e(TAG, "write: No output stream!");
        }

        if (wasSuccessful) {
            mHandler.onBytesWritten(bytes, bytes.length, this);
        }

        return wasSuccessful;
    }

    /**
     * Closes the input and output streams in addition to the socket.
     * Note that after calling this method, this instance is no longer in valid state and must be
     * disposed of.
     */
    public synchronized void closeSocket() {
        mIsClosingSocket = true;

        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the input stream: " + e.getMessage(), e);
            }
        }

        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the output stream: " + e.getMessage(), e);
            }
        }

        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the socket: " + e.getMessage(), e);
            }
        }
    }

    public String getPeerId() {
        return mPeerId;
    }

    public void setPeerId(String peerId) {
        mPeerId = peerId;
    }

    public String getPeerName() {
        return mPeerName;
    }

    public void setPeerName(String peerName) {
        mPeerName = peerName;
    }

    public String getPeerAddress() {
        return mPeerAddress;
    }

    public void setPeerAddress(String peerAddress) {
        mPeerAddress = peerAddress;
    }
}
