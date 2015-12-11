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
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.io.IOException;
import java.util.UUID;

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
         * Called when a connection fails.
         * @param reason The reason of the failure.
         * @param peerProperties The properties of the peer. Note: Can be null!
         */
        void onConnectionFailed(String reason, PeerProperties peerProperties);
    }

    private static final String TAG = BluetoothConnector.class.getName();
    public static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = 15000;

    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothConnectorListener mListener;
    private final UUID mMyBluetoothUuid;
    private final String mMyBluetoothName;
    private final String mMyIdentityString;
    private final Handler mHandler;
    private final BluetoothConnector mBluetoothConnectorInstance;
    private final Thread.UncaughtExceptionHandler mUncaughtExceptionHandler;
    private BluetoothServerThread mServerThread = null;
    private BluetoothClientThread mClientThread = null;
    private CountDownTimer mConnectionTimeoutTimer;
    private long mConnectionTimeoutInMilliseconds = DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
    private boolean mIsServerThreadAlive = false;
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
     * Starts to listen for incoming connections.
     * @return True, if the connector was started successfully or was already running. False otherwise.
     */
    public synchronized boolean startListeningForIncomingConnections() {
        if (!mIsServerThreadAlive && !mIsShuttingDown) {
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
     * Shuts down all operations.
     * Note that after calling this method, this instance cannot be used anymore and must be
     * disposed of. To start using this class again one must create a new instance.
     */
    public synchronized void shutdown() {
        Log.i(TAG, "Shutting down...");
        mIsShuttingDown = true;

        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
        }

        if (mServerThread != null) {
            mServerThread.shutdown();
            mServerThread = null;
        }

        if (mClientThread != null) {
            mClientThread.shutdown();
            mClientThread = null;
        }

        mIsServerThreadAlive = false;
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

            if (mClientThread != null) {
                mClientThread.shutdown();
                mClientThread = null;
            }

            try {
                mClientThread = new BluetoothClientThread(
                        this, bluetoothDeviceToConnectTo, myBluetoothUuid, mMyIdentityString);
                wasSuccessful = true;
            } catch (IOException e) {
                errorMessage = "connect: Failed to create a Bluetooth connect thread instance: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
            }

            if (wasSuccessful) {
                mClientThread.setPeerProperties(peerProperties);
                mClientThread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);

                if (mConnectionTimeoutTimer != null) {
                    mConnectionTimeoutTimer.cancel();
                    mConnectionTimeoutTimer.start();
                }

                mClientThread.start();

                mListener.onConnecting(bluetoothDeviceName, bluetoothDeviceAddress);
                Log.d(TAG, "connect: Started connecting to " + bluetoothDeviceName
                        + " in address " + bluetoothDeviceAddress);
            } else {
                mListener.onConnectionFailed(errorMessage, peerProperties);
            }
        } else {
            Log.e(TAG, "connect: No bluetooth device!");
        }

        return wasSuccessful;
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
     * @param reason The reason for the failure.
     */
    @Override
    public void onIncomingConnectionFailed(String reason) {
        Log.e(TAG, "onIncomingConnectionFailed: " + reason);
        final String tempReason = reason;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothConnectorInstance.mListener.onConnectionFailed(tempReason, null);
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

        if (!mIsShuttingDown) {
            // This instance is still valid, let's try to restart the server
            Log.i(TAG, "onServerStopped: Restarting the server...");
            startListeningForIncomingConnections();
        }
    }

    /**
     * Restarts the connection timeout timer.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onSocketConnected(PeerProperties peerProperties) {
        Log.i(TAG, "onSocketConnected: " + peerProperties.toString());

        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
            mConnectionTimeoutTimer.start();
        }
    }

    /**
     * The connection is now established and validated by handshake protocol. Notifies the listener
     * that we are now fully connected.
     * @param bluetoothSocket The Bluetooth socket associated with the connection.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onHandshakeSucceeded(BluetoothSocket bluetoothSocket, PeerProperties peerProperties) {
        Log.i(TAG, "onHandshakeSucceeded: " + peerProperties.toString());

        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
        }

        mClientThread = null;

        final BluetoothSocket tempSocket = bluetoothSocket;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tempSocket.isConnected()) {
                    mBluetoothConnectorInstance.mListener.onConnected(tempSocket, false, tempPeerProperties);
                } else {
                    mBluetoothConnectorInstance.onConnectionFailed("Disconnected", tempPeerProperties);
                }
            }
        });
    }

    /**
     * Forward the event to the listener.
     * @param reason The reason for the failure.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onConnectionFailed(String reason, PeerProperties peerProperties) {
        Log.e(TAG, "onConnectionFailed: " + reason);
        final String tempReason = reason;
        final PeerProperties tempPeerProperties = peerProperties;

        if (mConnectionTimeoutTimer != null) {
            mConnectionTimeoutTimer.cancel();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothConnectorInstance.mListener.onConnectionFailed(tempReason, tempPeerProperties);
            }
        });

        final BluetoothClientThread clientThread = mClientThread;
        mClientThread = null;

        if (clientThread != null) {
            new Thread() {
                @Override
                public void run() {
                    clientThread.shutdown();
                }
            }.start();
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
                mConnectionTimeoutInMilliseconds, mConnectionTimeoutInMilliseconds) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            @Override
            public void onFinish() {
                final String connectionFailureReason = "Connection timeout";
                Log.i(TAG, connectionFailureReason);
                this.cancel();
                final BluetoothClientThread clientThread = mClientThread;
                mClientThread = null;

                if (clientThread != null) {
                    new Thread() {
                        @Override
                        public void run() {
                            clientThread.shutdown(); // Try to cancel
                        }
                    }.start();
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothConnectorInstance.mListener.onConnectionFailed(
                                connectionFailureReason, clientThread.getPeerProperties());
                    }
                });
            }
        };
    }
}
