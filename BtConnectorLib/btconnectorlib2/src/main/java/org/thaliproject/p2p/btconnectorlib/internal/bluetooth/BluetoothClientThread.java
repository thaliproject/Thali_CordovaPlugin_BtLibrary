/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;

import java.io.IOException;
import java.util.UUID;

/**
 * Thread for initiating outgoing connections.
 */
class BluetoothClientThread extends AbstractBluetoothThread implements BluetoothSocketIoThread.Listener {
    /**
     * Thread listener.
     */
    public interface Listener {
        /**
         * Called when socket connection with a peer succeeds.
         *
         * @param bluetoothSocket The Bluetooth socket associated with the connection.
         * @param peerProperties  The peer properties.
         * @param who             The Bluetooth client thread instance calling this callback.
         */
        void onSocketConnected(BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who);

        /**
         * Called when successfully connected to and validated (handshake OK) a peer.
         * Note that the responsibility over the Bluetooth socket is transferred to the listener.
         *
         * @param bluetoothSocket The Bluetooth socket associated with the connection.
         * @param peerProperties  The peer properties.
         * @param who             The Bluetooth client thread instance calling this callback.
         */
        void onHandshakeSucceeded(BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who);

        /**
         * Called when connection attempt fails.
         *
         * @param peerProperties The peer properties.
         * @param errorMessage   The error message.
         * @param who            The Bluetooth client thread instance calling this callback.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage, BluetoothClientThread who);
    }

    private static final String TAG = BluetoothClientThread.class.getName();
    static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = -1;
    static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = 1;
    static final int DEFAULT_MAX_NUMBER_OF_RETRIES = 0;
    private static final int WAIT_BETWEEN_RETRIES_IN_MILLISECONDS = 300;
    private final BluetoothDevice mBluetoothDeviceToConnectTo;
    private Listener mListener = null;
    private BluetoothSocket mBluetoothSocket = null;
    private BluetoothSocketIoThread mHandshakeThread = null;
    private PeerProperties mPeerProperties;
    private int mInsecureRfcommSocketPort = SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfRetries = DEFAULT_MAX_NUMBER_OF_RETRIES;
    private long mTimeStarted = 0;
    private volatile boolean mIsShuttingDown = false;

    /**
     * Constructor.
     *
     * @param listener                   The listener.
     * @param bluetoothDeviceToConnectTo The Bluetooth device to connect to.
     * @param serviceRecordUuid          Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myIdentityString           Our identity.
     * @throws NullPointerException Thrown, if either the listener or the Bluetooth device instance is null.
     * @throws IOException          Thrown, if BluetoothDevice.createInsecureRfcommSocketToServiceRecord fails.
     */
    BluetoothClientThread(Listener listener, BluetoothDevice bluetoothDeviceToConnectTo,
                          UUID serviceRecordUuid, String myIdentityString) throws NullPointerException,
            IOException {
        super(serviceRecordUuid, myIdentityString);

        if (listener == null || bluetoothDeviceToConnectTo == null) {
            throw new NullPointerException("Either the listener or the Bluetooth device instance is null");
        }

        mListener = listener;
        mBluetoothDeviceToConnectTo = bluetoothDeviceToConnectTo;
        mServiceRecordUuid = serviceRecordUuid;
        mPeerProperties = new PeerProperties(mBluetoothDeviceToConnectTo.getAddress());
    }

    /**
     * @return The time this thread was started.
     */
    long getTimeStarted() {
        return mTimeStarted;
    }

    /**
     * From Thread.
     * <p>
     * Tries to connect to the Bluetooth socket. If successful, will create a handshake instance to
     * handle the connecting process.
     */
    @Override
    public void run() {
        Log.i(TAG, "Trying to connect to peer with address " + mBluetoothDeviceToConnectTo.getAddress()
                + " (thread ID: " + getId() + ")");
        mTimeStarted = System.currentTimeMillis();
        boolean socketConnectSucceeded = tryToConnect();
        Log.i(TAG, "socket is " + (socketConnectSucceeded ? "connected" : "not connected"));
        final BluetoothSocket bluetoothSocket = mBluetoothSocket;

        if (mHandshakeRequired && socketConnectSucceeded && !mIsShuttingDown) {
            try {
                setUpHandshakeThread(bluetoothSocket);
                doHandshake();
            } catch (IOException e) {
                String errorMessage = "Construction of a handshake thread failed: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
                notifyOnConnectionFailed(errorMessage);
            }
        }

        Log.i(TAG, "Exiting thread (thread ID: " + getId() + ")");
    }

    private void setUpHandshakeThread(BluetoothSocket bluetoothSocket) throws IOException {
        mHandshakeThread = new BluetoothSocketIoThread(bluetoothSocket, this);
        mHandshakeThread.setUncaughtExceptionHandler(this.getUncaughtExceptionHandler());
        mHandshakeThread.setExitThreadAfterRead(true);
        mHandshakeThread.setPeerProperties(mPeerProperties);
    }

    private void doHandshake() {
        Log.d(TAG, "Starting handshake");
        mHandshakeThread.start();
        boolean handshakeSucceeded = mHandshakeThread.write(getHandshakeMessage()); // This does not throw exceptions

        if (handshakeSucceeded) {
            Log.d(TAG, "Outgoing connection initialized (*handshake* thread ID: "
                    + mHandshakeThread.getId() + ")");
        } else if (!mIsShuttingDown) {
            String errorMessage = "Failed to initiate handshake";
            Log.e(TAG, errorMessage);
            close();
            notifyOnConnectionFailed(errorMessage);
        }
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket.
     *
     * @param insecureRfcommSocketPort The port to use.
     */
    void setInsecureRfcommSocketPortNumber(int insecureRfcommSocketPort) {
        Log.i(TAG, "setInsecureRfcommSocketPortNumber: Using port " + insecureRfcommSocketPort);
        mInsecureRfcommSocketPort = insecureRfcommSocketPort;
    }

    /**
     * Sets the maximum number of socket connection attempt retries (0 means only one attempt).
     *
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries.
     */
    void setMaxNumberOfRetries(int maxNumberOfRetries) {
        Log.i(TAG, "setMaxNumberOfRetries: " + maxNumberOfRetries);
        mMaxNumberOfRetries = maxNumberOfRetries;
    }

    public PeerProperties getPeerProperties() {
        return mPeerProperties;
    }

    /**
     * Stores the given properties to be used when reporting failures.
     *
     * @param peerProperties The peer properties.
     */
    public void setPeerProperties(PeerProperties peerProperties) {
        mPeerProperties = peerProperties;
    }

    /**
     * Stops the IO thread and closes the socket. This is a graceful shutdown i.e. no error messages
     * are logged by run() nor will the listener be notified (onConnectionFailed), when this method
     * is called.
     */
    @Override
    public void shutdown() {
        Log.d(TAG, "shutdown: Thread ID: " + getId());
        mIsShuttingDown = true;
        close();
    }

    /**
     * Tries to validate the read message, which should contain the identity of the peer. If the
     * identity is valid, notify the user that we have established a connection.
     *
     * @param bytes The array of bytes read.
     * @param size  The size of the array.
     * @param who   The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who) {
        final BluetoothSocket bluetoothSocket = who.getSocket();
        Log.d(TAG, "onBytesRead: Read " + size + " bytes successfully (thread ID: " + who.getId() + ")");
        if (who.getPeerProperties() != null) {
            Log.d(TAG, "onBytesRead: Peer properties = " + who.getPeerProperties().toString());
        }
        PeerProperties peerProperties =
                BluetoothUtils.validateReceivedHandshakeMessage(bytes, size, bluetoothSocket);

        if (peerProperties != null) {
            processSuccessfulHandshake(who, peerProperties, bluetoothSocket);
        } else {
            processFailedHandshake();
        }
    }

    private void processSuccessfulHandshake(BluetoothSocketIoThread who, PeerProperties peerProperties,
                                            BluetoothSocket bluetoothSocket) {
        Log.i(TAG, "Handshake succeeded with " + peerProperties.toString());
        // Set the resolved properties to the associated thread
        who.setPeerProperties(peerProperties);
        if (mListener != null) {
            // On successful handshake, we'll pass the socket for the listener, so it's now
            // the listeners responsibility to close the socket once done. Thus, do not
            // close the socket here. Do not either close the input and output streams,
            // since that will invalidate the socket as well.
            mListener.onHandshakeSucceeded(bluetoothSocket, peerProperties, this);
            mHandshakeThread = null;
        } else {
            // No listener to deal with the socket, shut it down
            shutdown();
        }
    }

    private void processFailedHandshake() {
        String errorMessage = "Handshake failed - unable to resolve peer properties, perhaps due to invalid identity";
        Log.e(TAG, errorMessage);
        notifyOnConnectionFailed(errorMessage);
        shutdown();
    }

    /**
     * Does nothing, but logs the event.
     *
     * @param buffer The array of bytes read.
     * @param size   The size of the array.
     * @param who    The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesWritten(byte[] buffer, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        Log.d(TAG, "onBytesWritten: " + size + " bytes successfully written (thread ID: " + threadId + ")");
        if (who.getPeerProperties() != null) {
            Log.d(TAG, "onBytesWritten: Peer props = " + who.getPeerProperties().toString());
        }
    }

    /**
     * If the handshake thread instance is still around, it means we got a connection failure in our
     * hands and we need to notify the listener and shutdown.
     *
     * @param reason The reason why we got disconnected. Contains an exception message in case of failure.
     * @param who    The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread who) {
        final PeerProperties peerProperties = who.getPeerProperties();
        if (peerProperties != null) {
            Log.i(TAG, "onDisconnected: " + peerProperties.toString() + " (thread ID: " + who.getId() + ")");
        }
        // If we were successful, the handshake thread instance was set to null
        if (mHandshakeThread != null) {
            notifyOnConnectionFailed("Socket disconnected");
            shutdown();
        }
    }

    /**
     * Closes the handshake thread, if one exists, and the Bluetooth socket.
     */
    private void close() {
        Log.d(TAG, "close");
        if (mHandshakeThread != null) {
            mHandshakeThread.close(true, false);
        }
        closeBluetoothSocket();
        finalizeClose();
    }

    /**
     * Since shutdown and close methods are not synchronized (so that they can be executed even when
     * tryToConnect method is still being executed), they cannot set the members to null to avoid
     * NullPointerExceptions. Thus, we do that here.
     */
    private synchronized void finalizeClose() {
        mListener = null;
        mHandshakeThread = null;
        mBluetoothSocket = null;
    }

    /**
     * Tries to establish a socket connection.
     *
     * @return True, if successful. False otherwise.
     */
    private synchronized boolean tryToConnect() {
        boolean socketConnectSucceeded = false;
        int socketConnectAttemptNo = 1;

        Log.d(TAG, "tryToConnect: mIsShuttingDown = " + mIsShuttingDown);
        while (!socketConnectSucceeded && !mIsShuttingDown) {
            socketConnectSucceeded = connect(mInsecureRfcommSocketPort, socketConnectAttemptNo);
            if (!socketConnectSucceeded) {
                if (mInsecureRfcommSocketPort >= 0 && !mIsShuttingDown) {
                    // We were using a custom port, fallback to the standard method of creating a socket
                    socketConnectSucceeded = connect(SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT, socketConnectAttemptNo);
                }
                if (!socketConnectSucceeded && !mIsShuttingDown) {
                    String errorMessage = "Failed to connect (tried " + socketConnectAttemptNo + " time(s)): ";
                    processConnectionFail(errorMessage, socketConnectAttemptNo);
                    if (maxNumberOfRetriesReached(socketConnectAttemptNo)) {
                        break;
                    }
                }
            }
            socketConnectAttemptNo++;
        }
        return socketConnectSucceeded;
    }

    private boolean connect(int portNumber, int socketConnectAttemptNo) {
        boolean socketCreated = createSocketAndConnect(portNumber);
        return socketCreated && processSocketCreatedEvent(portNumber, socketConnectAttemptNo);
    }

    private void processConnectionFail(String errorMessage, int connectAttemptNo) {
        Log.d(TAG, errorMessage + " (thread ID: " + getId() + ")");
        if (!maxNumberOfRetriesReached(connectAttemptNo)) {
            Log.d(TAG, "Trying to connect again in " + WAIT_BETWEEN_RETRIES_IN_MILLISECONDS
                    + " ms... (thread ID: " + getId() + ")");
            waitBeforeRetry();
        } else {
            processMaxNumberOfRetries(errorMessage);
        }
    }

    private boolean maxNumberOfRetriesReached(int currentAttempt) {
        return currentAttempt >= mMaxNumberOfRetries + 1;
    }

    /**
     * Creates an insecure Bluetooth socket with the service record UUID and tries to connect.
     *
     * @param port If -1, will use a standard method for socket creation (OS decides).
     *             If 0, will use a rotating port number (see BluetoothUtils.createBluetoothSocketToServiceRecordWithNextPort).
     *             If greater than 0, will use the given port number.
     * @return Null, if successfully connected. An exception in case of a failure.
     */
    private synchronized boolean createSocketAndConnect(final int port) {
        if (mBluetoothSocket != null) {
            throw new IllegalStateException("Bluetooth socket is already created");
        }
        try {
            mBluetoothSocket = createBluetoothSocket(port);
        } catch (IOException e) {
            Log.e(TAG, "createSocketAndConnect: " + e.getMessage());
            return false;
        }
        return connect();
    }

    private boolean processSocketCreatedEvent(int portNumber, int attemptNumber) {
        if (!mIsShuttingDown && mBluetoothSocket != null) {
            notifyOnConnected(mBluetoothSocket);
            logPortChoice(portNumber, attemptNumber);
            return true;
        } else {
            // Shutting down probably due to connection timeout
            Log.i(TAG, "Socket connection succeeded, but we are shutting down (thread ID: " + getId() + ")");
        }
        return false;
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(WAIT_BETWEEN_RETRIES_IN_MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Waiting between retries was interrupted, " + e.getMessage());
        }
    }

    private void processMaxNumberOfRetries(String errorMessage) {
        Log.d(TAG, "Maximum number of allowed retries (" + mMaxNumberOfRetries
                + ") reached, giving up... (thread ID: " + getId() + ")");
        notifyOnConnectionFailed(errorMessage);
    }

    private BluetoothSocket createBluetoothSocket(int port) throws IOException {
        Log.d(TAG, "createBluetoothSocket " + port);
        switch (port) {
            case SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT: {
                // Use the standard method of creating a socket
                Log.d(TAG, "createBluetoothSocket: SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT");
                return mBluetoothDeviceToConnectTo.createInsecureRfcommSocketToServiceRecord(mServiceRecordUuid);
            }
            case 0: {
                // Use a rotating port number
                Log.d(TAG, "createBluetoothSocket: port == 0");
                return BluetoothUtils.createBluetoothSocketToServiceRecordWithNextPort(
                        mBluetoothDeviceToConnectTo, mServiceRecordUuid, false);
            }
            default: {
                // Use the given port number
                Log.d(TAG, "createBluetoothSocket: given port");
                return BluetoothUtils.createBluetoothSocketToServiceRecord(
                        mBluetoothDeviceToConnectTo, mServiceRecordUuid, port, false);
            }
        }
    }

    private boolean connect() {
        if (mBluetoothSocket != null) {
            try {
                Log.e(TAG, " connect: connecting");
                mBluetoothSocket.connect(); // Blocking call
                Log.i(TAG, "connect: connected. " + BluetoothUtils.portAndTypeToString(mBluetoothSocket));
                return true;
            } catch (IOException e) {
                Log.e(TAG, " connect: " + e.getMessage());
                closeBluetoothSocket();
                return false;
            }
        }
        return false;
    }

    private void logPortChoice(int rfcommSocketPort, int attemptNumber) {
        // Log the choice of port
        String logMessage = "Socket connection succeeded";
        if (rfcommSocketPort == 0) {
            logMessage += " (using port" + BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort() + ")";
        } else if (rfcommSocketPort > 0) {
            logMessage += " (using port " + rfcommSocketPort + ")";
        } else {
            logMessage += " (using system decided port)";
        }
        logMessage += ", total number of attempts: " + attemptNumber + " (thread ID: " + getId() + ")";
        Log.i(TAG, logMessage);
    }

    private void closeBluetoothSocket() {
        if (mBluetoothSocket != null) {
            try {
                Log.d(TAG, "closeBluetoothSocket: Trying to close the bluetooth socket... (thread ID: " + getId() + ")");
                mBluetoothSocket.close();
                mBluetoothSocket = null;
                Log.d(TAG, "closeBluetoothSocket: bluetooth socket closed (thread ID: " + getId() + ")");
            } catch (IOException e) {
                Log.w(TAG, "close: Failed to close the bluetooth socket (thread ID: " + getId() + "): " + e.getMessage());
            }
        }
    }

    private void notifyOnConnected(BluetoothSocket bluetoothSocket) {
        if (mListener != null) {
            mListener.onSocketConnected(bluetoothSocket, mPeerProperties, this);
        }
    }

    private void notifyOnConnectionFailed(String errorMessage) {
        if (mListener != null) {
            mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
        }
    }

}
