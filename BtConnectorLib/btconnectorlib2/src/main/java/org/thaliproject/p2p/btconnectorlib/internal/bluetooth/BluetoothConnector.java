/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thaliproject.p2p.btconnectorlib.ConnectionManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.ThreadUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
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
         * Called when the Bluetooth server thread is started and ready to accept incoming
         * connections or when the thread is stopped.
         *
         * @param isStarted If true, the server thread is started. If false, the server thread is stopped.
         */
        void onIsServerStartedChanged(boolean isStarted);

        /**
         * Called when connecting to a Bluetooth device.
         *
         * @param bluetoothDeviceName    The name of the Bluetooth device connecting to.
         * @param bluetoothDeviceAddress The address of the Bluetooth device connecting to.
         */
        void onConnecting(String bluetoothDeviceName, String bluetoothDeviceAddress);

        /**
         * Called when connected to a Bluetooth device.
         *
         * @param bluetoothSocket The Bluetooth socket.
         * @param isIncoming      True, if the connection was incoming. False, if it was outgoing.
         * @param peerProperties  The properties of the peer connected to.
         */
        void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties);

        /**
         * Called when the connection attempt times out.
         *
         * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
         */
        void onConnectionTimeout(PeerProperties peerProperties);

        /**
         * Called when a connection fails.
         *
         * @param peerProperties The properties of the peer we we're trying to connect to. Note: Can be null.
         * @param errorMessage   The error message. Note: Can be null.
         */
        void onConnectionFailed(PeerProperties peerProperties, String errorMessage);
    }

    private static final String TAG = BluetoothConnector.class.getName();
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = 15000;
    public static final int SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT = BluetoothClientThread.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT = BluetoothClientThread.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT;
    public static final int DEFAULT_MAX_NUMBER_OF_RETRIES = BluetoothClientThread.DEFAULT_MAX_NUMBER_OF_RETRIES;
    public static final boolean DEFAULT_HANDSHAKE_REQUIRED = true;
    private static final long CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = 5000;
    private static final long SERVER_RESTART_DELAY_IN_MILLISECONDS = 2000;

    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothConnectorListener mListener;
    private final UUID mServiceRecordUuid;
    private final String mMyBluetoothName;
    private final Handler mHandler;
    private final Thread.UncaughtExceptionHandler mUncaughtExceptionHandler;
    private String mMyIdentityString = null;
    private BluetoothServerThread mServerThread = null;
    private CopyOnWriteArrayList<BluetoothClientThread> mClientThreads = new CopyOnWriteArrayList<>();
    private CountDownTimer mConnectionTimeoutTimer = null;
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private int mInsecureRfcommSocketPort = SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT;
    private int mMaxNumberOfOutgoingConnectionAttemptRetries = DEFAULT_MAX_NUMBER_OF_RETRIES;
    private boolean mHandshakeRequired = DEFAULT_HANDSHAKE_REQUIRED;
    private boolean mIsServerThreadAlive = false;
    private boolean mIsStoppingServer = false;
    private boolean mIsShuttingDown = false;

    /**
     * Constructor.
     *
     * @param context           The application context.
     * @param listener          The listener.
     * @param bluetoothAdapter  The Bluetooth adapter.
     * @param serviceRecordUuid Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myBluetoothName   Our Bluetooth name.
     * @param myIdentityString  A string containing our identity.
     *                          See AbstractBluetoothConnectivityAgent.createIdentityString()
     */
    public BluetoothConnector(
            Context context, BluetoothConnectorListener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceRecordUuid, String myBluetoothName, String myIdentityString) {
        this(context, listener, bluetoothAdapter, serviceRecordUuid, myBluetoothName,
                myIdentityString, PreferenceManager.getDefaultSharedPreferences(context));
    }

    /**
     * Constructor used in unit tests. It allows to provide initialized
     * shared preferences.
     *
     * @param context           The application context.
     * @param listener          The listener.
     * @param bluetoothAdapter  The Bluetooth adapter.
     * @param serviceRecordUuid Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myBluetoothName   Our Bluetooth name.
     * @param myIdentityString  A string containing our identity.
     *                          See AbstractBluetoothConnectivityAgent.createIdentityString()
     * @param preferences       The shared preferences.
     */
    public BluetoothConnector(
            Context context, BluetoothConnectorListener listener, BluetoothAdapter bluetoothAdapter,
            UUID serviceRecordUuid, String myBluetoothName, String myIdentityString,
            SharedPreferences preferences) {
        if (listener == null || bluetoothAdapter == null || serviceRecordUuid == null) {
            throw new NullPointerException("Listener, Bluetooth adapter instance or service record UUID is null");
        }

        Log.d(TAG, "BluetoothConnector: Bluetooth name: " + myBluetoothName + ", service record UUID: " + serviceRecordUuid.toString());

        mListener = listener;
        mBluetoothAdapter = bluetoothAdapter;
        mServiceRecordUuid = serviceRecordUuid;
        mMyBluetoothName = myBluetoothName;
        mHandler = new Handler(context.getMainLooper());

        ConnectionManagerSettings mConnectionManagerSettings
                = ConnectionManagerSettings.getInstance(context, preferences);
        mConnectionTimeoutInMilliseconds = mConnectionManagerSettings.getConnectionTimeout();
        mInsecureRfcommSocketPort = mConnectionManagerSettings.getInsecureRfcommSocketPortNumber();
        mMaxNumberOfOutgoingConnectionAttemptRetries
                = mConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries();
        mHandshakeRequired = mConnectionManagerSettings.getHandshakeRequired();

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

        setIdentityString(myIdentityString);
    }

    /**
     * Sets the identity string. Note that if the we are currently listening for incoming
     * connections, the server thread will still operate using the previous identity string.
     * The server thread must be restarted to use the new identity string.
     *
     * @param myIdentityString A string containing our identity.
     *                         See AbstractBluetoothConnectivityAgent.createIdentityString()
     */
    public void setIdentityString(String myIdentityString) {
        Log.d(TAG, "setIdentityString: " + myIdentityString);
        mMyIdentityString = myIdentityString;
    }

    /**
     * Sets the connection timeout. If the given value is negative or zero, no timeout is set.
     * The timeout applies only to connections whose handshake hasn't succeeded. After a successful
     * handshake this class no longer manages the connection but the responsibility is that of the
     * listener.
     *
     * @param connectionTimeoutInMilliseconds The connection timeout in milliseconds.
     */
    public void setConnectionTimeout(long connectionTimeoutInMilliseconds) {
        if (mConnectionTimeoutInMilliseconds != connectionTimeoutInMilliseconds) {
            Log.v(TAG, "setConnectionTimeout: "
                    + mConnectionTimeoutInMilliseconds + " -> " + connectionTimeoutInMilliseconds);
            mConnectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;

            if (mConnectionTimeoutInMilliseconds > 0 && mClientThreads.size() > 0) {
                createConnectionTimeoutTimer();
            } else {
                if (mConnectionTimeoutTimer != null) {
                    mConnectionTimeoutTimer.cancel();
                    mConnectionTimeoutTimer = null;
                }
            }
        }
    }

    /**
     * Sets the preferred port to be used by the insecure RFCOMM socket of the client thread.
     *
     * @param insecureRfcommSocketPort The port to use.
     */
    public void setInsecureRfcommSocketPort(int insecureRfcommSocketPort) {
        if (mInsecureRfcommSocketPort != insecureRfcommSocketPort) {
            Log.v(TAG, "setInsecureRfcommSocketPort: " + mInsecureRfcommSocketPort + " -> " + insecureRfcommSocketPort);
            mInsecureRfcommSocketPort = insecureRfcommSocketPort;
        }
    }

    /**
     * Sets the maximum number of (outgoing) socket connection  attempt retries (0 means only one attempt).
     *
     * @param maxNumberOfRetries The maximum number of socket connection attempt retries for outgoing connections.
     */
    public void setMaxNumberOfOutgoingConnectionAttemptRetries(int maxNumberOfRetries) {
        if (mMaxNumberOfOutgoingConnectionAttemptRetries != maxNumberOfRetries) {
            Log.v(TAG, "setMaxNumberOfOutgoingConnectionAttemptRetries: "
                    + mMaxNumberOfOutgoingConnectionAttemptRetries + " -> " + maxNumberOfRetries);
            mMaxNumberOfOutgoingConnectionAttemptRetries = maxNumberOfRetries;
        }
    }

    /**
     * Sets the value indicating whether we require a handshake protocol when establishing a connection or not.
     * Restarts the Bluetooth server thread, if it was running.
     *
     * @param handshakeRequired True, if a handshake protocol should be applied when establishing a connection.
     */
    public void setHandshakeRequired(boolean handshakeRequired) {
        if (mHandshakeRequired != handshakeRequired) {
            Log.v(TAG, "setHandshakeRequired: " + mHandshakeRequired + " -> " + handshakeRequired);
            mHandshakeRequired = handshakeRequired;

            if (mIsServerThreadAlive) {
                stopListeningForIncomingConnections();

                // Stopping the server is asynchronous and may take a short while. Thus, we need to
                // restart it with a small delay.
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        startListeningForIncomingConnections();
                    }
                }, SERVER_RESTART_DELAY_IN_MILLISECONDS);
            }
        }
    }

    /**
     * Starts to listen for incoming connections.
     *
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
                        this, mBluetoothAdapter, mServiceRecordUuid, mMyBluetoothName, mMyIdentityString);
            } catch (NullPointerException e) {
                Log.e(TAG, "Failed to create the socket listener thread: " + e.getMessage(), e);
                mServerThread = null;
            }

            if (mServerThread != null) {
                mServerThread.setUncaughtExceptionHandler(mUncaughtExceptionHandler);
                mServerThread.setHandshakeRequired(mHandshakeRequired);
                mServerThread.start();
                mIsServerThreadAlive = true;
                mListener.onIsServerStartedChanged(true);
            }
        } else {
            if (mIsServerThreadAlive) {
                Log.d(TAG, "startListeningForIncomingConnections: Already started");
            } else {
                Log.e(TAG, "startListeningForIncomingConnections: The process of stopping the server thread is still ongoing, please wait for the process to complete before restarting");
            }
        }

        return mIsServerThreadAlive;
    }

    /**
     * Stops listening for incoming connections.
     */
    public synchronized void stopListeningForIncomingConnections() {
        if (mServerThread != null) {
            Log.i(TAG, "stopListeningForIncomingConnections: Stopping...");
            mIsStoppingServer = true; // So that we don't automatically restart
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
        if (!mIsShuttingDown) {
            Log.i(TAG, "shutdown: Shutting down...");
        }

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
     *
     * @param bluetoothDeviceToConnectTo The Bluetooth device to connect to.
     * @param peerProperties             The properties of the peer to connect to.
     * @return True, if started trying to connect successfully. False otherwise.
     */
    public synchronized boolean connect(
            BluetoothDevice bluetoothDeviceToConnectTo, PeerProperties peerProperties) {

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
                        this, bluetoothDeviceToConnectTo, mServiceRecordUuid, mMyIdentityString);
            } catch (IOException e) {
                errorMessage = "connect: Failed to create a Bluetooth connect thread instance: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
            }

            if (bluetoothClientThread != null) {
                bluetoothClientThread.setUncaughtExceptionHandler(mUncaughtExceptionHandler);
                bluetoothClientThread.setHandshakeRequired(mHandshakeRequired);
                bluetoothClientThread.setPeerProperties(peerProperties);
                bluetoothClientThread.setInsecureRfcommSocketPortNumber(mInsecureRfcommSocketPort);
                bluetoothClientThread.setMaxNumberOfRetries(mMaxNumberOfOutgoingConnectionAttemptRetries);
                mClientThreads.add(bluetoothClientThread);

                if (mConnectionTimeoutTimer == null) {
                    try {
                        createConnectionTimeoutTimer();
                        mConnectionTimeoutTimer.start();
                    } catch (RuntimeException e) {
                        Log.e(TAG, "connect: Failed to create the connection timeout timer: " + e.getMessage(), e);
                    }
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
     *
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
                isCancelling = removeAndShutdownBluetoothClientThread(bluetoothClientThread);
            }
        } else {
            if (peerProperties == null) {
                throw new NullPointerException("The given peer properties instance is null");
            } else {
                Log.e(TAG, "cancelConnectionAttempt: No client threads to cancel");
            }
        }

        return isCancelling;
    }

    /**
     * Shuts down all client threads.
     */
    public synchronized void cancelAllConnectionAttempts() {
        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        final int numberOfClientThreadsToShutdown = mClientThreads.size();

        if (numberOfClientThreadsToShutdown > 0) {
            Log.d(TAG, "cancelAllConnectionAttempts: Shutting down " + numberOfClientThreadsToShutdown + " client threads");

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

    /**
     * Forward the event to the listener.
     *
     * @param bluetoothSocket The Bluetooth socket associated with the incoming connection.
     * @param peerProperties  The peer properties.
     */
    @Override
    public void onIncomingConnectionConnected(
            final BluetoothSocket bluetoothSocket, final PeerProperties peerProperties) {

        Log.i(TAG, "onIncomingConnectionConnected: " + peerProperties.toString());
        boolean posted = mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onIncomingConnectionConnected: bluetoothSocket.isConnected() " + bluetoothSocket.isConnected());
                if (bluetoothSocket.isConnected()) {
                    mListener.onConnected(bluetoothSocket, true, peerProperties);
                } else {
                    onIncomingConnectionFailed("Disconnected");
                }
            }
        });
        Log.i(TAG, "onIncomingConnectionConnected: posted = " + posted);
    }

    /**
     * Forward the event to the listener.
     *
     * @param errorMessage The error message.
     */
    @Override
    public void onIncomingConnectionFailed(String errorMessage) {
        Log.e(TAG, "onIncomingConnectionFailed: " + errorMessage);
        final String tempErrorMessage = errorMessage;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onConnectionFailed(null, tempErrorMessage);
            }
        });
    }

    /**
     * If this wasn't a deliberate shutdown (shutdown of this instance was not called), tries to
     * restart the server thread.
     */
    @Override
    public void onServerStopped() {
        final boolean wasServerExplicitlyStopped = mIsStoppingServer;
        Log.i(TAG, "onServerStopped: Was explicitly stopped: " + wasServerExplicitlyStopped);
        mIsStoppingServer = false;
        mIsServerThreadAlive = false;

        if (wasServerExplicitlyStopped) {
            // Was deliberately stopped
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onIsServerStartedChanged(false);
                }
            });
        } else {
            // Was not stopped deliberately.
            // This instance is still valid, let's try to restart the server.
            Log.i(TAG, "onServerStopped: Restarting the server...");
            startListeningForIncomingConnections();
        }
    }

    @Override
    public void onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded(int limit) {
        Log.e(TAG, "onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded: Failed " + limit + " times");
        stopListeningForIncomingConnections();
    }

    /**
     * Does nothing but logs the event.
     *
     * @param bluetoothSocket The Bluetooth socket associated with the connection.
     * @param peerProperties  The peer properties.
     * @param who             The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onSocketConnected(
            BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who) {
        Log.i(TAG, "onSocketConnected: " + peerProperties.toString() + " (thread ID: " + who.getId() + ")");

        if (!who.getHandshakeRequired()) {
            handleSuccessfulClientThread(who, bluetoothSocket, peerProperties); // Notifies the listener
        }
    }

    /**
     * The connection is now established and validated by handshake protocol.
     *
     * @param bluetoothSocket The Bluetooth socket associated with the connection.
     * @param peerProperties  The peer properties.
     * @param who             The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onHandshakeSucceeded(
            BluetoothSocket bluetoothSocket, PeerProperties peerProperties, BluetoothClientThread who) {
        Log.i(TAG, "onHandshakeSucceeded: " + peerProperties.toString() + " (thread ID: " + who.getId() + ")");
        handleSuccessfulClientThread(who, bluetoothSocket, peerProperties); // Notifies the listener
    }

    /**
     * Forward the event to the listener.
     *
     * @param peerProperties The peer properties.
     * @param errorMessage   The error message.
     * @param who            The Bluetooth client thread instance calling this callback.
     */
    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage, BluetoothClientThread who) {
        Log.e(TAG, "onConnectionFailed: " + errorMessage + " (thread ID: " + who.getId() + ")");
        final String tempErrorMessage = errorMessage;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onConnectionFailed(tempPeerProperties, tempErrorMessage);
            }
        });

        removeAndShutdownBluetoothClientThread(who);
    }

    /**
     * Handles a successful Bluetooth client thread - one that has established a connection.
     * Notifies the listener that we are now fully connected.
     *
     * @param bluetoothClientThread The Bluetooth client thread instance.
     * @param bluetoothSocket       The Bluetooth socket.
     * @param peerProperties        The peer properties.
     */
    private synchronized void handleSuccessfulClientThread(
            final BluetoothClientThread bluetoothClientThread,
            final BluetoothSocket bluetoothSocket, final PeerProperties peerProperties) {
        Log.i(TAG, "handleSuccessfulClientThread: " + peerProperties.toString() + " (thread ID: " + bluetoothClientThread.getId() + ")");

        // Only remove, but do not shutdown the client thread, since that would close the socket too
        mClientThreads.remove(bluetoothClientThread);

        if (mConnectionTimeoutTimer != null && mClientThreads.size() == 0) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer = null;
        }

        if (!mIsShuttingDown) {
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "handleSuccessfulClientThread run. " + ThreadUtils.currentThreadToString());
                    if (bluetoothSocket.isConnected()) {
                        Log.d(TAG, "onConnected run. " + ThreadUtils.currentThreadToString());
                        mListener.onConnected(bluetoothSocket, false, peerProperties);
                    } else {
                        Log.d(TAG, "onConnectionFailed run. " + ThreadUtils.currentThreadToString());
                        onConnectionFailed(peerProperties, "Disconnected", bluetoothClientThread);
                    }
                }
            });
            Log.d(TAG, "Posted: " + posted);
        }
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
                Iterator<BluetoothClientThread> iterator = mClientThreads.iterator();

                while (iterator.hasNext()) {
                    final BluetoothClientThread bluetoothClientThread = iterator.next();
                    long timeStarted = bluetoothClientThread.getTimeStarted();

                    if (timeStarted > 0 && currentTime > timeStarted + mConnectionTimeoutInMilliseconds) {
                        // Got a client thread that needs to be cancelled
                        mClientThreads.remove(bluetoothClientThread);
                        final PeerProperties peerProperties = bluetoothClientThread.getPeerProperties();

                        if (peerProperties != null) {
                            Log.i(TAG, "Connection timeout for peer "
                                    + peerProperties.toString() + " (thread ID: "
                                    + bluetoothClientThread.getId() + ")");
                        } else {
                            Log.i(TAG, "Connection timeout" + " (thread ID: "
                                    + bluetoothClientThread.getId() + ")");
                        }

                        shutdownBluetoothClientThread(bluetoothClientThread); // Try to cancel

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onConnectionTimeout(peerProperties);
                            }
                        });
                    }
                }

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
     * <p>
     * Shutting down is safer to do in its own thread, because closing the socket of the client
     * thread may block (and in worst case freeze the device), if the socket is still trying to
     * connect.
     *
     * @param bluetoothClientThread The Bluetooth client thread instance to shut down and remove.
     * @return True, if the thread was shut down and removed. False otherwise.
     */
    private synchronized boolean removeAndShutdownBluetoothClientThread(final BluetoothClientThread bluetoothClientThread) {
        boolean wasRemovedAndShutdown = false;

        if (bluetoothClientThread != null) {
            if (mClientThreads.size() > 0) {
                for (BluetoothClientThread currentBluetoothClientThread : mClientThreads) {
                    if (currentBluetoothClientThread != null
                            && currentBluetoothClientThread.getId() == bluetoothClientThread.getId()) {
                        Log.i(TAG, "removeAndShutdownBluetoothClientThread: Thread ID: " + bluetoothClientThread.getId());

                        mClientThreads.remove(currentBluetoothClientThread);
                        shutdownBluetoothClientThread(currentBluetoothClientThread);

                        if (mConnectionTimeoutTimer != null && mClientThreads.size() == 0) {
                            mConnectionTimeoutTimer.cancel();
                            mConnectionTimeoutTimer = null;
                        }

                        wasRemovedAndShutdown = true;
                        break;
                    }
                }
            }
        } else {
            throw new NullPointerException("The given Bluetooth client thread instance is null");
        }

        if (!wasRemovedAndShutdown) {
            Log.w(TAG, "removeAndShutdownBluetoothClientThread: Failed to find a thread with ID " + bluetoothClientThread.getId());
        }

        return wasRemovedAndShutdown;
    }

    /**
     * Tries to shutdown the given Bluetooth client thread.
     *
     * @param bluetoothClientThread The Bluetooth client thread to shutdown.
     */
    private synchronized void shutdownBluetoothClientThread(final BluetoothClientThread bluetoothClientThread) {
        if (bluetoothClientThread != null) {
            Log.d(TAG, "shutdownBluetoothClientThread: Thread ID: " + bluetoothClientThread.getId());

            new Thread() {
                @Override
                public void run() {
                    bluetoothClientThread.shutdown();
                }
            }.start();
        }
    }
}
