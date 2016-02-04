/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.ConnectionManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The main Bluetooth connectivity interface managing both incoming and outgoing connections.
 */
public class BluetoothConnector
        implements BluetoothServerThread.Listener, BluetoothClientThread.Listener {
    /**
     * The listener interface.
     */
    public interface BluetoothConnectorListener {
        /**
         * Called when connecting to a Bluetooth device.
         * @param bluetoothDeviceName The name of the Bluetooth device connecting to.
         * @param bluetoothDeviceAddress The address of the Bluetooth device connecting to.
         */
        void onConnecting(String bluetoothDeviceName, String bluetoothDeviceAddress);

        /**
         * Called when connected to a Bluetooth device.
         * @param bluetoothSocket The Bluetooth socket.
         * @param isIncoming True, if the connection was incoming. False, if it was outgoing.
         * @param peerProperties The properties of the peer connected to.
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties);

        /**
         * Called when the connection attempt times out.
         * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
         */
        void onConnectionTimeout(PeerProperties peerProperties);

        /**
         * Called when a connection fails.
         * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
         * @param errorMessage The error message. Note: Can be null.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage);
    }

    private static final String TAG = BluetoothConnector.class.getName();
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = 15000;
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = BluetoothClientThread.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = BluetoothClientThread.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_MAX_NUMBER_OF_RETRIES = BluetoothClientThread.DEFAULT_MAX_NUMBER_OF_RETRIES;
    private static final long CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 5000;

    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothConnectorListener mListener;
    private final UUID mMyBluetoothUuid;
    private final String mMyBluetoothName;
    private final String mMyIdentityString;
    private final Handler mHandler;
    private final BluetoothConnector mBluetoothConnectorInstance;
    private final Thread.UncaughtExceptionHandler mUncaughtExceptionHandler;
    private BluetoothServerThread mServerThread = null;
    private CopyOnWriteArrayList<BluetoothClientThread> mClientThreads = new CopyOnWriteArrayList<>();
    private CountDownTimer mConnectionTimeoutTimer = null;
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mInsecureRfcommSocketPort = SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfOutgoingConnectionAttemptRetries = DEFAULT_MAX_NUMBER_OF_RETRIES;
    private boolean mIsServerThreadAlive = false;
    private boolean mIsStoppingServer = false;
    private boolean mIsShuttingDown = false;

    /**
     * Constructor.
     * @param context The application context.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param myBluetoothUuid The Bluetooth UUID.
     * @param myBluetoothName Our Bluetooth name.
     * @param myIdentityString Our identity.
     */
    public BluetoothConnector(
            Context context, BluetoothConnectorListener listener, BluetoothAdapter bluetoothAdapter,
            UUID myBluetoothUuid, String myBluetoothName, String myIdentityString) {
        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mMyBluetoothUuid = myBluetoothUuid;
        mMyBluetoothName = myBluetoothName;
        mMyIdentityString = myIdentityString;
        mHandler = new Handler(context.getMainLooper());

        ConnectionManagerSettings settings = ConnectionManagerSettings.getInstance(context);
        mConnectionTimeoutInMilliseconds = settings.getConnectionTimeout();
        mInsecureRfcommSocketPort = settings.getInsecureRfcommSocketPortNumber();
        mMaxNumberOfOutgoingConnectionAttemptRetries = settings.getMaxNumberOfConnectionAttemptRetries();

        createConnectionTimeoutTimer();

        mUncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(TAG, "Uncaught exception: " + ex.getMessage(), ex);
                final Throwable tmpException = ex;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        throw new RuntimeException(tmpException);
                    }
                });
            }
        };

        mBluetoothConnectorInstance = this;
    }

    /**
     * Sets the connection timeout. If the given value is negative or zero, no timeout is set.
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        Log.i(TAG, "setConnectionTimeout: " + connectionTimeoutInMilliseconds);
        mConnectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;

        if (mConnectionTimeoutInMilliseconds > 0) {
            createConnectionTimeoutTimer();
        } else {
            if (mConnectionTimeoutTimer != null) {
                mConnectionTimeoutTimer.cancel();
                mConnectionTimeoutTimer = null;
            }
        }
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket of the client thread.
     * @param insecureRfcommSocketPort The port to use.
     */
    public void setInsecureRfcommSocketPort(int insecureRfcommSocketPort) {
        mInsecureRfcommSocketPort = insecureRfcommSocketPort;
    }

    /**
     * Sets the maximum number of (outgoing) socket connection  attempt retries (0 means only one attempt).
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries for outgoing connections.
     */
    public void setMaxNumberOfOutgoingConnectionAttemptRetries(int maxNumberOfRetries) {
        mMaxNumberOfOutgoingConnectionAttemptRetries = maxNumberOfRetries;
    }

    /**
     * Starts to listen for incoming connections.
     * @return True, if the connector was started successfully or was already running. False otherwise.
     */
    public synchronized boolean startListeningForIncomingConnections() {
        if (!mIsServerThreadAlive && !mIsStoppingServer) {
            Log.i(TAG, "startListeningForIncomingConnections: Starting...");

            if (mServerThread != null) {
                mServerThread.shutdown();
                mServerThread = null;
            }

            try {
                mServerThread = new BluetoothServerThread(
                        this, mBluetoothAdapter, mMyBluetoothUuid, mMyBluetoothName, mMyIdentityString);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create the socket listener thread: " + e.getMessage(), e);
                mServerThread = null;
            }

            if (mServerThread != null) {
                mServerThread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
                mServerThread.start();
                mIsServerThreadAlive = true;
            }
        } else {
            Log.w(TAG, "startListeningForIncomingConnections: Already started");
        }

        return mIsServerThreadAlive;
    }

    /**
     * Stops listening for incoming connections.
     */
    public synchronized void stopListeningForIncomingConnections() {
        mIsStoppingServer = true; // So that we don't automatically restart

        if (mServerThread != null) {
            Log.i(TAG, "stopListeningForIncomingConnections: Stopping...");
            mServerThread.shutdown();
            mServerThread = null;
        }
    }

    /**
     * Shuts down all operations.
     * Note that after calling this method, this instance cannot be used anymore and must be
     * disposed of. To start using this class again one must create a new instance.
     */
    public synchronized void shutdown() {
        Log.i(TAG, "Shutting down...");
        mIsShuttingDown = true;

        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        stopListeningForIncomingConnections();
        cancelAllConnectionAttempts();
    }

    /**
     * Tries to connect to the given Bluetooth device.
     * Note that even if this method returns successfully, the connection is not yet established,
     * but the connection process is merely initiated.
     * @param bluetoothDeviceToConnectTo The Bluetooth device to connect to.
     * @param peerProperties The properties of the peer to connect to.
     * @param myBluetoothUuid Our own Bluetooth UUID.
     * @return True, if started trying to connect successfully. False otherwise.
     */
    public synchronized boolean connect(
            BluetoothDevice bluetoothDeviceToConnectTo, PeerProperties peerProperties, UUID myBluetoothUuid) {

        boolean wasSuccessful = false;
        String errorMessage = "";

        if (bluetoothDeviceToConnectTo != null) {
            final String bluetoothDeviceName = bluetoothDeviceToConnectTo.getName();
            final String bluetoothDeviceAddress = bluetoothDeviceToConnectTo.getAddress();

            Log.i(TAG, "connect: Trying to start connecting to " + bluetoothDeviceName
                    + " in address " + bluetoothDeviceAddress);
            BluetoothClientThread bluetoothClientThread = null;

            try {
                bluetoothClientThread = new BluetoothClientThread(
                        this, bluetoothDeviceToConnectTo, myBluetoothUuid, mMyIdentityString);
            } catch (IOException e) {
                errorMessage = "connect: Failed to create a Bluetooth connect thread instance: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
            }

            if (bluetoothClientThread != null) {
                BluetoothClientThread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
                bluetoothClientThread.setPeerProperties(peerProperties);
                bluetoothClientThread.setInsecureRfcommSocketPortNumber(mInsecureRfcommSocketPort);
                bluetoothClientThread.setMaxNumberOfRetries(mMaxNumberOfOutgoingConnectionAttemptRetries);
                mClientThreads.add(bluetoothClientThread);

                if (mConnectionTimeoutTimer == null) {
                    createConnectionTimeoutTimer();
                    mConnectionTimeoutTimer.start();
                }

                bluetoothClientThread.start();

                mListener.onConnecting(bluetoothDeviceName, bluetoothDeviceAddress);
                wasSuccessful = true;

                Log.d(TAG, "connect: Started connecting to " + bluetoothDeviceName
                        + " in address " + bluetoothDeviceAddress);
            } else {
                mListener.onConnectionFailed(peerProperties, errorMessage);
            }
        } else {
            Log.e(TAG, "connect: No bluetooth device!");
        }

        return wasSuccessful;
    }

    /**
     * Cancels a client thread containing the peer with the given properties.
     * @param peerProperties The properties of the peer whose connection attempt to cancel.
     * @return True, if a client thread associated with the given peer properties was found and
     * cancelled. False otherwise.
     */
    public synchronized boolean cancelConnectionAttempt(PeerProperties peerProperties) {
        boolean isCancelling = false;

        if (peerProperties != null && mClientThreads.size() > 0) {
            Log.i(TAG, "cancelConnectionAttempt: " + peerProperties.toString());
            BluetoothClientThread bluetoothClientThread = null;

            for (BluetoothClientThread currentBluetoothSocketThread : mClientThreads) {
                if (currentBluetoothSocketThread != null
                        && currentBluetoothSocketThread.getPeerProperties() != null
                        && currentBluetoothSocketThread.getPeerProperties().equals(peerProperties)) {
                    bluetoothClientThread = currentBluetoothSocketThread;
                    break;
                }
            }

            if (bluetoothClientThread != null) {
                isCancelling = shutdownAndRemoveClientThread(bluetoothClientThread);
            }
        } else {
            if (peerProperties == null) {
                Log.e(TAG, "cancelConnectionAttempt: The given peer properties instance is null");
            } else {
                Log.e(TAG, "cancelConnectionAttempt: No client threads to cancel");
            }
        }

        return isCancelling;
    }

    /**
     * Forward the event to the listener.
     * @param bluetoothSocket The Bluetooth socket associated with the incoming connection.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onIncomingConnectionConnected(
            BluetoothSocket bluetoothSocket, PeerProperties peerProperties) {

        Log.i(TAG, "onIncomingConnectionConnected: " + peerProperties.toString());
        final BluetoothSocket tempSocket = bluetoothSocket;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tempSocket.isConnected()) {
                    mBluetoothConnectorInstance.mListener.onConnected(tempSocket, true, tempPeerProperties);
                } else {
                    mBluetoothConnectorInstance.onIncomingConnectionFailed("Disconnected");
                }
            }
        });
    }

    /**
     * Forward the event to the listener.
     * @param errorMessage The error message.
     */
    @Override
    public void onIncomingConnectionFailed(String errorMessage) {
        Log.e(TAG, "onIncomingConnectionFailed: " + errorMessage);
        final String tempErrorMessage = errorMessage;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothConnectorInstance.mListener.onConnectionFailed(null, tempErrorMessage);
            }
        });
    }

    /**
     * If this wasn't a deliberate shutdown (shutdown of this instance was not called), tries to
     * restart the server thread.
     */
    @Override
    public void onServerStopped() {
        Log.i(TAG, "onServerStopped");
        mIsServerThreadAlive = false;

        if (!mIsStoppingServer) {
            // Was not stopped deliberately.
            // This instance is still valid, let's try to restart the server.
            Log.i(TAG, "onServerStopped: Restarting the server...");
            startListeningForIncomingConnections();
        }

        mIsStoppingServer = false;
    }

    /**
     * Does nothing but logs the event.
     * @param peerProperties The peer properties.
     * @param who The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onSocketConnected(PeerProperties peerProperties, BluetoothClientThread who) {
        Log.i(TAG, "onSocketConnected: " + peerProperties.toString() + " (thread ID: " + who.getId() + ")");
    }

    /**
     * The connection is now established and validated by handshake protocol. Notifies the listener
     * that we are now fully connected.
     * @param bluetoothSocket The Bluetooth socket associated with the connection.
     * @param peerProperties The peer properties.
     * @param who The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onHandshakeSucceeded(BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who) {
        Log.i(TAG, "onHandshakeSucceeded: " + peerProperties.toString() + " (thread ID: " + who.getId() + ")");

        // Only remove, but do not shutdown the client thread, since that would close the socket too
        final BluetoothClientThread bluetoothClientThread = who;
        mClientThreads.remove(bluetoothClientThread);

        if (mConnectionTimeoutTimer != null && mClientThreads.size() == 0) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        if (!mIsShuttingDown) {
            final BluetoothSocket tempSocket = bluetoothSocket;
            final PeerProperties tempPeerProperties = peerProperties;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (tempSocket.isConnected()) {
                        mBluetoothConnectorInstance.mListener.onConnected(tempSocket, false, tempPeerProperties);
                    } else {
                        mBluetoothConnectorInstance.onConnectionFailed(tempPeerProperties, "Disconnected", bluetoothClientThread);
                    }
                }
            });
        }
    }

    /**
     * Forward the event to the listener.
     * @param peerProperties The peer properties.
     * @param errorMessage The error message.
     * @param who The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage, BluetoothClientThread who) {
        Log.e(TAG, "onConnectionFailed: " + errorMessage + " (thread ID: " + who.getId() + ")");
        final String tempErrorMessage = errorMessage;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothConnectorInstance.mListener.onConnectionFailed(tempPeerProperties, tempErrorMessage);
            }
        });

        shutdownAndRemoveClientThread(who);
    }

    /**
     * Constructs the connection timeout timer. If a timer instance already exists, it is cancelled
     * and then recreated.
     */
    private void createConnectionTimeoutTimer() {
        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        mConnectionTimeoutTimer = new CountDownTimer(
                CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS, CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            @Override
            public void onFinish() {
                this.cancel();
                long currentTime = new Date().getTime();
                boolean removed = true;

                while (removed) {
                    removed = false;

                    for (BluetoothClientThread bluetoothClientThread : mClientThreads) {
                        final BluetoothClientThread currentBluetoothClientThread = bluetoothClientThread;

                        if (currentBluetoothClientThread != null) {
                            long startedTime = currentBluetoothClientThread.getTimeStarted();

                            if (startedTime > 0 && currentTime > startedTime + mConnectionTimeoutInMilliseconds) {
                                // Got a client thread that needs to be cancelled
                                final PeerProperties peerProperties = currentBluetoothClientThread.getPeerProperties();

                                if (peerProperties != null) {
                                    Log.i(TAG, "Connection timeout for peer "
                                            + peerProperties.toString() + " (thread ID: "
                                            + currentBluetoothClientThread.getId() + ")");
                                } else {
                                    Log.i(TAG, "Connection timeout" + " (thread ID: "
                                            + currentBluetoothClientThread.getId() + ")");
                                }

                                try {
                                    mClientThreads.remove(currentBluetoothClientThread);
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to removed a timed out thread: " + e.getMessage(), e);
                                }

                                removed = true;

                                new Thread() {
                                    @Override
                                    public void run() {
                                        currentBluetoothClientThread.shutdown(); // Try to cancel
                                    }
                                }.start();

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mBluetoothConnectorInstance.mListener.onConnectionTimeout(peerProperties);
                                    }
                                });

                                break;
                            }
                        }
                    }
                } // while (removed)

                if (mClientThreads.size() > 0) {
                    this.start(); // Restart
                } else {
                    mConnectionTimeoutTimer = null;
                }
            }
        };
    }

    /**
     * Shuts down the given Bluetooth client thread instance and removes it from the list of
     * client threads.
     *
     * Shutting down is safer to do in its own thread, because closing the socket of the client
     * thread may block (and in worst case freeze the device), if the socket is still trying to
     * connect.
     *
     * @param bluetoothClientThread The Bluetooth client thread instance to shut down and remove.
     * @return True, if the thread was shut down and removed. False otherwise.
     */
    private synchronized boolean shutdownAndRemoveClientThread(final BluetoothClientThread bluetoothClientThread) {
        boolean wasShutdownAndRemoved = false;

        if (bluetoothClientThread != null) {
            if (mClientThreads.size() > 0) {
                for (BluetoothClientThread currentBluetoothClientThread : mClientThreads) {
                    if (currentBluetoothClientThread != null
                            && currentBluetoothClientThread.getId() == bluetoothClientThread.getId()) {
                        Log.i(TAG, "shutdownAndRemoveClientThread: Shutting down thread with ID "
                                + bluetoothClientThread.getId());

                        mClientThreads.remove(currentBluetoothClientThread);

                        new Thread() {
                            @Override
                            public void run() {
                                bluetoothClientThread.shutdown();
                            }
                        }.start();

                        if (mConnectionTimeoutTimer != null && mClientThreads.size() == 0) {
                            mConnectionTimeoutTimer.cancel();
                            mConnectionTimeoutTimer = null;
                        }

                        wasShutdownAndRemoved = true;
                        break;
                    }
                }
            }
        } else {
            Log.e(TAG, "shutdownAndRemoveClientThread: The given Bluetooth client thread instance is null");
        }

        if (!wasShutdownAndRemoved) {
            Log.w(TAG, "shutdownAndRemoveClientThread: Failed to find a thread with ID " + bluetoothClientThread.getId());
        }

        return wasShutdownAndRemoved;
    }

    /**
     * Shuts down all client threads.
     */
    private synchronized void cancelAllConnectionAttempts() {
        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        if (mClientThreads.size() > 0) {
            for (BluetoothClientThread bluetoothClientThread : mClientThreads) {
                final BluetoothClientThread finalBluetoothClientThread = bluetoothClientThread;

                if (finalBluetoothClientThread != null) {
                    new Thread() {
                        @Override
                        public void run() {
                            finalBluetoothClientThread.shutdown();
                        }
                    }.start();
                }
            }

            mClientThreads.clear();
        }
    }
}
