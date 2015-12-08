/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils.PeerProperties;
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
    private static final long CONNECTION_TIMEOUT_IN_MILLISECONDS = 15000;
    private static final long CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS = CONNECTION_TIMEOUT_IN_MILLISECONDS;

    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothConnectorListener mListener;
    private final UUID mMyBluetoothUuid;
    private final String mMyBluetoothName;
    private final String mMyIdentityString;
    private final Handler mHandler;
    private final CountDownTimer mConnectionTimeoutTimer;
    private final Thread.UncaughtExceptionHandler mUncaughtExceptionHandler;
    private BluetoothServerThread mServerThread = null;
    private BluetoothClientThread mClientThread = null;
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

        mConnectionTimeoutTimer = new CountDownTimer(
                CONNECTION_TIMEOUT_IN_MILLISECONDS, CONNECTION_TIMEOUT_TIMER_INTERVAL_IN_MILLISECONDS) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Not used
            }

            @Override
            public void onFinish() {
                BluetoothClientThread tempClientThread = mClientThread;
                mClientThread = null;

                if (tempClientThread != null) {
                    // Shut down the client thread and notify the listener (me)
                    Log.i(TAG, "Connection timeout");
                    tempClientThread.cancel("Connection timeout");
                }
            }
        };

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
        mConnectionTimeoutTimer.cancel();

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
     * @param myBluetoothUuid Our own Bluetooth UUID.
     * @param peerProperties The properties of the peer to connect to.
     * @return True, if started trying to connect successfully. False otherwise.
     */
    public synchronized boolean connect(
            BluetoothDevice bluetoothDeviceToConnectTo, UUID myBluetoothUuid, PeerProperties peerProperties) {

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
                mClientThread.setRemotePeerProperties(peerProperties);
                mClientThread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
                mConnectionTimeoutTimer.cancel();
                mConnectionTimeoutTimer.start();
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
     * Tries to connect to the given Bluetooth device.
     * Note that even if this method returns successfully, the connection is not yet established,
     * but the connection process is merely initiated.
     * @param bluetoothDeviceToConnectTo The Bluetooth device to connect to.
     * @param myBluetoothUuid Our own Bluetooth UUID.
     * @param peerId The peer ID.
     * @param peerName The peer name.
     * @param peerBluetoothAddress The Bluetooth address of the peer.
     * @return True, if started trying to connect successfully. False otherwise.
     */
    public synchronized boolean connect(
            BluetoothDevice bluetoothDeviceToConnectTo, UUID myBluetoothUuid,
            String peerId, String peerName, String peerBluetoothAddress) {

        PeerProperties peerProperties = new PeerProperties();
        peerProperties.id = peerId;
        peerProperties.name = peerName;
        peerProperties.bluetoothAddress = peerBluetoothAddress;

        return connect(bluetoothDeviceToConnectTo, myBluetoothUuid, peerProperties);
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
        final BluetoothConnector thisInstance = this;
        final BluetoothSocket tempSocket = bluetoothSocket;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tempSocket.isConnected()) {
                    thisInstance.mListener.onConnected(tempSocket, true, tempPeerProperties);
                } else {
                    thisInstance.onIncomingConnectionFailed("Disconnected");
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
        final BluetoothConnector thisInstance = this;
        final String tempReason = reason;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                thisInstance.mListener.onConnectionFailed(tempReason, null);
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
        Log.i(TAG, "onSocketConnected: Authenticating next: " + peerProperties.toString());
        mConnectionTimeoutTimer.cancel();
        mConnectionTimeoutTimer.start();
    }

    /**
     * Forward the event to the listener.
     * @param bluetoothSocket The Bluetooth socket associated with the connection.
     * @param peerProperties The peer properties.
     */
    @Override
    public void onAuthenticated(BluetoothSocket bluetoothSocket, PeerProperties peerProperties) {
        Log.i(TAG, "onAuthenticated: Fully connected: " + peerProperties.toString());
        mConnectionTimeoutTimer.cancel();
        mClientThread = null;

        final BluetoothConnector thisInstance = this;
        final BluetoothSocket tempSocket = bluetoothSocket;
        final PeerProperties tempPeerProperties = peerProperties;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tempSocket.isConnected()) {
                    thisInstance.mListener.onConnected(tempSocket, false, tempPeerProperties);
                } else {
                    thisInstance.onConnectionFailed("Disconnected", tempPeerProperties);
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
        final BluetoothConnector thisInstance = this;
        final String tempReason = reason;
        final PeerProperties tempPeerProperties = peerProperties;

        mConnectionTimeoutTimer.cancel();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                thisInstance.mListener.onConnectionFailed(tempReason, tempPeerProperties);

                BluetoothClientThread tempClientThread = mClientThread;
                mClientThread = null;

                if (tempClientThread != null) {
                    tempClientThread.shutdown();
                }
            }
        });
    }
}
