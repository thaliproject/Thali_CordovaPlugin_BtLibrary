/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread listening to incoming connections via Bluetooth server socket.
 */
class BluetoothServerThread extends AbstractBluetoothThread implements BluetoothSocketIoThread.Listener {
    /**
     * Listener interface.
     */
    public interface Listener {
        /**
         * Called when an incoming connection is validated (handshake OK) and connected.
         * Note that the responsibility over the Bluetooth socket is transferred to the listener.
         *
         * @param bluetoothSocket The Bluetooth socket associated with the incoming connection.
         * @param peerProperties The peer properties.
         */
        void onIncomingConnectionConnected(BluetoothSocket bluetoothSocket, PeerProperties peerProperties);

        /**
         * Called when the incoming connection fails.
         *
         * @param reason The reason for the failure.
         */
        void onIncomingConnectionFailed(String reason);

        /**
         * Called when this thread has been shut down i.e. when the thread is about to exit.
         */
        void onServerStopped();

        /**
         * Called when the creation of the Bluetooth server socket fails consecutively too many times.
         *
         * @param failureCount The number of times the creation failed consecutively.
         */
        void onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded(int failureCount);
    }

    private static final String TAG = BluetoothServerThread.class.getName();
    private static final int BLUETOOTH_SERVER_SOCKET_CONSECUTIVE_CREATION_FAILURE_COUNT_LIMIT = 10;
    private final CopyOnWriteArrayList<BluetoothSocketIoThread> mSocketIoThreads = new CopyOnWriteArrayList<BluetoothSocketIoThread>();
    private final Listener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private final String mBluetoothName;
    private BluetoothServerSocket mBluetoothServerSocket = null;
    private static int mBluetoothServerSocketConsecutiveCreationFailureCount = 0;
    private boolean mStopThread = false;

    /**
     * Constructor.
     *
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param serviceRecordUuid Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myBluetoothName Our Bluetooth name for the server socket.
     * @param myIdentityString Our identity (possible name and the Bluetooth MAC address). Used for
     *                         handshake (if required).
     * @throws NullPointerException Thrown, if either the given listener or the Bluetooth adapter instance is null.
     * @throws IOException Thrown, if BluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord fails.
     */
    public BluetoothServerThread(
            Listener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceRecordUuid, String myBluetoothName, String myIdentityString)
            throws NullPointerException, IOException {
        super(serviceRecordUuid, myIdentityString);

        if (listener == null || bluetoothAdapter == null) {
            throw new NullPointerException("Either the listener or the Bluetooth adapter instance is null");
        }

        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mBluetoothName = myBluetoothName;
    }

    /**
     * From Thread.
     *
     * Waits for the incoming connections and once received, will construct IO threads for each
     * connection to handle them.
     */
    @Override
    public void run() {
        Log.d(TAG, "Entering thread");
        while (!mStopThread) {
            try {
                mBluetoothServerSocket =
                        mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                                mBluetoothName, mServiceRecordUuid);
                resetBluetoothServerSocketConsecutiveCreationFailureCount();
            } catch (IOException e) {
                Log.e(TAG, "run: Failed to start listening: " + e.getMessage(), e);
                mBluetoothServerSocketConsecutiveCreationFailureCount++;
                Log.d(TAG, "run: Bluetooth server socket consecutive creation failure count is now "
                        + mBluetoothServerSocketConsecutiveCreationFailureCount);

                if (mBluetoothServerSocketConsecutiveCreationFailureCount >=
                        BLUETOOTH_SERVER_SOCKET_CONSECUTIVE_CREATION_FAILURE_COUNT_LIMIT) {
                    final int failureCount = mBluetoothServerSocketConsecutiveCreationFailureCount;
                    resetBluetoothServerSocketConsecutiveCreationFailureCount();
                    mListener.onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded(failureCount);
                    mStopThread = true;
                }

            }

            if (mBluetoothServerSocket != null && !mStopThread) {
                Log.i(TAG, "Waiting for incoming connections...");
                BluetoothSocket bluetoothSocket = null;

                try {
                    bluetoothSocket = mBluetoothServerSocket.accept(); // Blocking call
                    Log.i(TAG, "Incoming connection accepted");
                } catch (IOException | NullPointerException e) {
                    if (!mStopThread) {
                        Log.e(TAG, "Failed to accept socket: " + e.getMessage(), e);
                        mListener.onIncomingConnectionFailed("Failed to accept socket: " + e.getMessage());
                        mStopThread = true;
                    }

                    bluetoothSocket = null;
                }

                if (bluetoothSocket != null) {
                    if (mHandshakeRequired) {
                        BluetoothSocketIoThread handshakeThread = null;

                        try {
                            handshakeThread = new BluetoothSocketIoThread(bluetoothSocket, this);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to create a handshake thread instance: " + e.getMessage(), e);
                        }

                        if (handshakeThread != null) {
                            handshakeThread.setUncaughtExceptionHandler(this.getUncaughtExceptionHandler());
                            handshakeThread.setExitThreadAfterRead(true);
                            mSocketIoThreads.add(handshakeThread);
                            handshakeThread.start();
                            Log.d(TAG, "Incoming connection initialized with handshake (thread ID: " + handshakeThread.getId() + ")");
                        }
                    } else {
                        // No handshake required
                        String bluetoothMacAddress = BluetoothUtils.getBluetoothMacAddressFromSocket(bluetoothSocket);

                        if (BluetoothUtils.isValidBluetoothMacAddress(bluetoothMacAddress)) {
                            PeerProperties peerProperties = new PeerProperties(bluetoothMacAddress);
                            mListener.onIncomingConnectionConnected(bluetoothSocket, peerProperties);
                        } else {
                            String errorMessage = "Invalid Bluetooth MAC address: " + bluetoothMacAddress;
                            Log.e(TAG, errorMessage);
                            mListener.onIncomingConnectionFailed(errorMessage);
                        }
                    }
                } else if (!mStopThread) {
                    Log.e(TAG, "Socket is null");
                    mListener.onIncomingConnectionFailed("Socket is null");
                    mStopThread = true;
                }
            } // if (mBluetoothServerSocket != null && !mStopThread)

            closeBluetoothServerSocket();
        } // while (!mStopThread)

        Log.d(TAG, "Exiting thread");
        mListener.onServerStopped();
    }

    public void resetBluetoothServerSocketConsecutiveCreationFailureCount() {
        mBluetoothServerSocketConsecutiveCreationFailureCount = 0;
    }

    /**
     * Shuts down this thread.
     * Clears the list of IO threads and closes the server socket.
     */
    @Override
    public synchronized void shutdown() {
        Log.d(TAG, "shutdown");
        mStopThread = true;
        final BluetoothServerSocket bluetoothServerSocket = mBluetoothServerSocket;

        if (bluetoothServerSocket != null) {
            try {
                bluetoothServerSocket.close();
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "shutdown: Failed to close the Bluetooth server socket: " + e.getMessage(), e);
            }
        }

        for (BluetoothSocketIoThread thread : mSocketIoThreads) {
            if (thread != null) {
                thread.close(true, true);
            }
        }

        mSocketIoThreads.clear();
    }

    /**
     * Validates the read message, which should contain the identity of the peer, and if OK, we will
     * try to respond with our own identity.
     *
     * @param bytes The array of bytes read.
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        Log.d(TAG, "onBytesRead: Read " + size + " bytes successfully (thread ID: " + threadId + ")");

        PeerProperties peerProperties =
                BluetoothUtils.validateReceivedHandshakeMessage(bytes, size, who.getSocket());

        if (peerProperties != null) {
            Log.i(TAG, "Got valid identity from " + peerProperties.toString());

            // Set the resolved properties to the associated thread
            who.setPeerProperties(peerProperties);

            // Respond to client
            if (!who.write(getHandshakeMessage())) {
                Log.e(TAG, "Failed to respond to thread with ID " + threadId);
                removeThreadFromList(threadId, true);
            }
        } else {
            Log.e(TAG, "Failed to receive valid identity (thread ID: " + threadId + ")");
            removeThreadFromList(threadId, true);
        }
    }

    /**
     * This will get called, if the response to an incoming connection was successful. Thus, we can
     * assume we have initiated a connection.
     *
     * @param bytes The array of bytes written.
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesWritten(byte[] bytes, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        Log.d(TAG, "onBytesWritten: " + size + " bytes successfully written (thread ID: " + threadId + ")");

        // Remove the thread from the list, but do not close the socket associated with it, since
        // it is now the responsibility of the listener to do that.
        boolean threadRemoved = removeThreadFromList(who, false);

        if (threadRemoved) {
            Log.d(TAG, "Handshake thread disposed (thread ID: " + threadId + ")");
        } else {
            Log.e(TAG, "Failed to find the thread from the list (thread ID: " + threadId + ")");
        }
        Log.d(TAG, "onIncomingConnectionConnected socket:" +  who.getSocket() + ", properties: "   + who.getPeerProperties());
        mListener.onIncomingConnectionConnected(who.getSocket(), who.getPeerProperties());
    }

    /**
     * If a BluetoothSocketIoThread instance has succeeded, it has been removed from our list.
     * This, if this callback gets called and we still have the given thread in our list, it means
     * that the reason for the disconnect was an error.
     *
     * @param reason The reason why we got disconnected. Contains an exception message in case of failure.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread who) {
        long threadId = who.getId();
        boolean threadFound = removeThreadFromList(who, true);

        if (threadFound) {
            Log.e(TAG, "onDisconnected (handshake failed) socket:" +  who.getSocket() + ", properties: "   + who.getPeerProperties());
            Log.e(TAG, "Handshake failed (thread ID: " + threadId + ")");
        }
    }

    /**
     * Closes the Bluetooth server socket.
     */
    private synchronized void closeBluetoothServerSocket() {
        final BluetoothServerSocket bluetoothServerSocket = mBluetoothServerSocket;
        mBluetoothServerSocket = null;

        if (bluetoothServerSocket != null) {
            try {
                bluetoothServerSocket.close();
                Log.v(TAG, "closeBluetoothServerSocket: Bluetooth server socket closed");
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "closeBluetoothServerSocket: Failed to close the Bluetooth server socket: " + e.getMessage(), e);
            }
        } else {
            Log.v(TAG, "closeBluetoothServerSocket: No Bluetooth server socket to close");
        }
    }

    /**
     * Removes the given socket IO thread from the list of threads.
     *
     * @param threadId The ID of the thread to remove.
     * @param closeSocketAndStreams If true, will close the socket and streams associated with the thread.
     * @return True, if the thread was removed from the list. False, if not found.
     */
    private synchronized boolean removeThreadFromList(
            final long threadId, boolean closeSocketAndStreams) {

        boolean threadRemoved = false;

        for (BluetoothSocketIoThread thread : mSocketIoThreads) {
            if (thread != null && thread.getId() == threadId) {
                Log.d(TAG, "removeThreadFromList: Removing thread with ID " + threadId);
                mSocketIoThreads.remove(thread);

                if (closeSocketAndStreams) {
                    thread.close(true, true);
                }

                threadRemoved = true;
                break;
            }
        }

        return threadRemoved;
    }

    /**
     * Removes the given socket IO thread from the list of threads.
     *
     * @param threadToRemove The thread to remove.
     * @param closeSocketAndStreams If true, will close the socket and streams associated with the thread.
     * @return True, if the thread was removed from the list. False, if not found.
     */
    private synchronized boolean removeThreadFromList(
            BluetoothSocketIoThread threadToRemove, boolean closeSocketAndStreams) {
        return removeThreadFromList(threadToRemove.getId(), closeSocketAndStreams);
    }
}