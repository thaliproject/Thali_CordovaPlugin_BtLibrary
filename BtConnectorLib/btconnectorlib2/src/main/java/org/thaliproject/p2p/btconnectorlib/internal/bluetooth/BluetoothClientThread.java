/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.IOException;
import java.util.Date;
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
         * @param bluetoothSocket The Bluetooth socket associated with the connection.
         * @param peerProperties The peer properties.
         * @param who The Bluetooth client thread instance calling this callback.
         */
        void onSocketConnected(BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who);

        /**
         * Called when successfully connected to and validated (handshake OK) a peer.
         * Note that the responsibility over the Bluetooth socket is transferred to the listener.
         * @param bluetoothSocket The Bluetooth socket associated with the connection.
         * @param peerProperties The peer properties.
         * @param who The Bluetooth client thread instance calling this callback.
         */
        void onHandshakeSucceeded(BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who);

        /**
         * Called when connection attempt fails.
         * @param peerProperties The peer properties.
         * @param errorMessage The error message.
         * @param who The Bluetooth client thread instance calling this callback.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage, BluetoothClientThread who);
    }

    private static final String TAG = BluetoothClientThread.class.getName();
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = -1;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = 1;
    public static final int DEFAULT_MAX_NUMBER_OF_RETRIES = 0;
    private static final int WAIT_BETWEEN_RETRIES_IN_MILLISECONDS = 300;
    private final BluetoothDevice mBluetoothDeviceToConnectTo;
    private Listener mListener = null;
    private BluetoothSocket mBluetoothSocket = null;
    private BluetoothSocketIoThread mHandshakeThread = null;
    private PeerProperties mPeerProperties;
    private int mInsecureRfcommSocketPort = SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfRetries = DEFAULT_MAX_NUMBER_OF_RETRIES;
    private long mTimeStarted = 0;
    private boolean mIsShuttingDown = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothDeviceToConnectTo The Bluetooth device to connect to.
     * @param serviceRecordUuid Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myIdentityString Our identity.
     * @throws NullPointerException Thrown, if either the listener or the Bluetooth device instance is null.
     * @throws IOException Thrown, if BluetoothDevice.createInsecureRfcommSocketToServiceRecord fails.
     */
    public BluetoothClientThread(
            Listener listener, BluetoothDevice bluetoothDeviceToConnectTo,
            UUID serviceRecordUuid, String myIdentityString)
            throws NullPointerException, IOException {
        super(serviceRecordUuid, myIdentityString);

        if (listener == null || bluetoothDeviceToConnectTo == null) {
            throw new NullPointerException("Either the listener or the Bluetooth device instance is null");
        }

        mListener = listener;
        mBluetoothDeviceToConnectTo = bluetoothDeviceToConnectTo;
        mServiceRecordUuid = serviceRecordUuid;
        mMyIdentityString = myIdentityString;
        mPeerProperties = new PeerProperties();
    }

    /**
     * @return The time this thread was started.
     */
    public long getTimeStarted() {
        return mTimeStarted;
    }

    /**
     * From Thread.
     *
     * Tries to connect to the Bluetooth socket. If successful, will create a handshake instance to
     * handle the connecting process.
     */
    @Override
    public void run() {
        Log.i(TAG, "Trying to connect to peer with address "
                + mBluetoothDeviceToConnectTo.getAddress()
                + " (thread ID: " + getId() + ")");

        mTimeStarted = new Date().getTime();
        boolean socketConnectSucceeded = tryToConnect();

        if (mHandshakeRequired && socketConnectSucceeded && !mIsShuttingDown) {
            String errorMessage = "";

            try {
                mHandshakeThread = new BluetoothSocketIoThread(mBluetoothSocket, this);
                mHandshakeThread.setUncaughtExceptionHandler(this.getUncaughtExceptionHandler());
                mHandshakeThread.setExitThreadAfterRead(true);
                mHandshakeThread.start();
                boolean handshakeSucceeded = mHandshakeThread.write(mMyIdentityString.getBytes()); // This does not throw exceptions

                if (handshakeSucceeded) {
                    Log.d(TAG, "Outgoing connection initialized (*handshake* thread ID: "
                            + mHandshakeThread.getId() + ")");
                } else if (!mIsShuttingDown) {
                    Log.e(TAG, "Failed to initiate handshake");
                    close();

                    if (mListener != null) {
                        mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
                    }
                }
            } catch (IOException e) {
                errorMessage = "Construction of a handshake thread failed: " + e.getMessage();
                Log.e(TAG, errorMessage, e);

                if (mListener != null) {
                    mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
                }
            } catch (NullPointerException e) {
                errorMessage = "Unexpected error: " + e.getMessage();
                Log.e(TAG, errorMessage, e);

                if (mListener != null) {
                    mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
                }
            }
        }

        if (mIsShuttingDown) {
            mIsShuttingDown = false;
        }

        Log.i(TAG, "Exiting thread (thread ID: " + getId() + ")");
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket.
     * @param insecureRfcommSocketPort The port to use.
     */
    public void setInsecureRfcommSocketPortNumber(int insecureRfcommSocketPort) {
        Log.i(TAG, "setInsecureRfcommSocketPortNumber: Using port " + insecureRfcommSocketPort);
        mInsecureRfcommSocketPort = insecureRfcommSocketPort;
    }

    /**
     * Sets the maximum number of socket connection attempt retries (0 means only one attempt).
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries.
     */
    public void setMaxNumberOfRetries(int maxNumberOfRetries) {
        Log.i(TAG, "setMaxNumberOfRetries: " + maxNumberOfRetries);
        mMaxNumberOfRetries = maxNumberOfRetries;
    }

    public PeerProperties getPeerProperties() {
        return mPeerProperties;
    }

    /**
     * Stores the given properties to be used when reporting failures.
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
    public synchronized void shutdown() {
        Log.d(TAG, "shutdown (thread ID: " + getId() + ")");
        mIsShuttingDown = true;
        mListener = null;
        close();
    }

    /**
     * Tries to validate the read message, which should contain the identity of the peer. If the
     * identity is valid, notify the user that we have established a connection.
     * @param bytes The array of bytes read.
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        final BluetoothSocket bluetoothSocket = who.getSocket();

        Log.d(TAG, "onBytesRead: Read " + size + " bytes successfully (thread ID: " + threadId + ")");

        PeerProperties peerProperties =
                BluetoothUtils.validateReceivedHandshakeMessage(bytes, bluetoothSocket);

        if (peerProperties != null) {
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
        } else {
            String errorMessage = "Handshake failed - unable to resolve peer properties, perhaps due to invalid identity";
            Log.e(TAG, errorMessage);

            if (mListener != null) {
                mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
            }

            shutdown();
        }
    }

    /**
     * Does nothing, but logs the event.
     * @param buffer
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesWritten(byte[] buffer, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        Log.d(TAG, "onBytesWritten: " + size + " bytes successfully written (thread ID: " + threadId + ")");
    }

    /**
     * If the handshake thread instance is still around, it means we got a connection failure in our
     * hands and we need to notify the listener and shutdown.
     * @param reason The reason why we got disconnected. Contains an exception message in case of failure.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        final PeerProperties peerProperties = who.getPeerProperties();
        Log.i(TAG, "onDisconnected: " + peerProperties.toString() + " (thread ID: " + threadId + ")");

        // If we were successful, the handshake thread instance was set to null
        if (mHandshakeThread != null) {
            if (mListener != null) {
                mListener.onConnectionFailed(peerProperties, "Socket disconnected", this);
            }

            shutdown();
        }
    }

    /**
     * Closes the handshake thread, if one exists, and the Bluetooth socket.
     */
    private synchronized void close() {
        if (mHandshakeThread != null) {
            mHandshakeThread.close(true, false);
            mHandshakeThread = null;
        }

        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close the socket: " + e.getMessage());
            }

            mBluetoothSocket = null;
        }
    }

    /**
     * Creates an insecure Bluetooth socket with the service record UUID and tries to connect.
     * @param port If -1, will use a standard method for socket creation (OS decides).
     *             If 0, will use a rotating port number (see BluetoothUtils.createBluetoothSocketToServiceRecordWithNextPort).
     *             If greater than 0, will use the given port number.
     * @return Null, if successfully connected. An exception in case of a failure.
     */
    private synchronized Exception createSocketAndConnect(final int port) {
        // Make sure the current socket, if one exists, is closed
        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
            }
        }

        boolean socketCreatedSuccessfully = false;
        Exception exception = null;

        try {
            if (port == SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT) {
                // Use the standard method of creating a socket
                mBluetoothSocket = mBluetoothDeviceToConnectTo.createInsecureRfcommSocketToServiceRecord(mServiceRecordUuid);
            } else if (port == 0) {
                // Use a rotating port number
                mBluetoothSocket = BluetoothUtils.createBluetoothSocketToServiceRecordWithNextPort(
                        mBluetoothDeviceToConnectTo, mServiceRecordUuid, false);
            } else {
                // Use the given port number
                mBluetoothSocket = BluetoothUtils.createBluetoothSocketToServiceRecord(
                        mBluetoothDeviceToConnectTo, mServiceRecordUuid, port, false);
            }

            socketCreatedSuccessfully = true;
        } catch (IOException e) {
            exception = e;
        }

        if (socketCreatedSuccessfully) {
            try {
                mBluetoothSocket.connect(); // Blocking call
            } catch (IOException e) {
                exception = e;

                try {
                    mBluetoothSocket.close();
                } catch (IOException e2) {
                }
            }
        }

        return exception;
    }

    /**
     * Tries to establish a socket connection.
     * @return True, if successful. False otherwise.
     */
    private synchronized boolean tryToConnect() {
        boolean socketConnectSucceeded = false;
        String errorMessage = "";
        int socketConnectAttemptNo = 1;

        while (!socketConnectSucceeded && !mIsShuttingDown) {
            Exception socketException = createSocketAndConnect(mInsecureRfcommSocketPort);

            if (socketException == null) {
                if (!mIsShuttingDown) {
                    if (mListener != null) {
                        mListener.onSocketConnected(mBluetoothSocket, mPeerProperties, this);
                    }

                    socketConnectSucceeded = true;

                    // Log the choice of port
                    String logMessage = "Socket connection succeeded";

                    if (mInsecureRfcommSocketPort == 0) {
                        logMessage += " (using port" + BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort() + ")";
                    } else if (mInsecureRfcommSocketPort > 0) {
                        logMessage += " (using port " + mInsecureRfcommSocketPort + ")";
                    } else {
                        logMessage += " (using system decided port)";
                    }

                    logMessage += ", total number of attempts: " + socketConnectAttemptNo
                            + " (thread ID: " + getId() + ")";
                    Log.i(TAG, logMessage);
                } else {
                    // Shutting down probably due to connection timeout
                    Log.i(TAG, "Socket connection succeeded, but we are shutting down (thread ID: " + getId() + ")");
                }
            } else if (mInsecureRfcommSocketPort >= 0 && !mIsShuttingDown) {
                // We were using a custom port, fallback to the standard method of creating a socket
                socketException = createSocketAndConnect(SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT);

                if (socketException == null) {
                    if (!mIsShuttingDown) {
                        if (mListener != null) {
                            mListener.onSocketConnected(mBluetoothSocket, mPeerProperties, this);
                        }

                        socketConnectSucceeded = true;

                        Log.i(TAG, "Socket connection succeeded (using system decided port), total number of attempts: "
                                + socketConnectAttemptNo + " (thread ID: " + getId() + ")");
                    } else {
                        // Shutting down due to probably connection timeout
                        Log.i(TAG, "Socket connection succeeded, but we are shutting down (thread ID: " + getId() + ")");
                    }
                } else {
                    errorMessage = "Failed to connect (tried " + socketConnectAttemptNo + " time(s)): "
                            + socketException.getMessage();
                }
            } else if (!mIsShuttingDown) {
                errorMessage = "Failed to connect (tried " + socketConnectAttemptNo + " time(s)): "
                        + socketException.getMessage();
            }

            if (!socketConnectSucceeded && !mIsShuttingDown) {
                Log.d(TAG, errorMessage + " (thread ID: " + getId() + ")");

                if (socketConnectAttemptNo < mMaxNumberOfRetries + 1) {
                    Log.d(TAG, "Trying to connect again in " + WAIT_BETWEEN_RETRIES_IN_MILLISECONDS
                            + " ms... (thread ID: " + getId() + ")");

                    try {
                        Thread.sleep(WAIT_BETWEEN_RETRIES_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                    }
                } else {
                    Log.d(TAG, "Maximum number of allowed retries (" + mMaxNumberOfRetries
                            + ") reached, giving up... (thread ID: " + getId() + ")");

                    if (mListener != null) {
                        mListener.onConnectionFailed(mPeerProperties, errorMessage, this);
                    }

                    break;
                }
            }

            socketConnectAttemptNo++;
            errorMessage = "";
        } // while (!socketConnectSucceeded && !mIsShuttingDown)

        return socketConnectSucceeded;
    }
}
