/* Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License.
 * See license.txt in the project root for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.internal.CommonUtils;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread listening to incoming connections via insecure Bluetooth socket.
 */
class BluetoothSocketListenerThread extends Thread implements BluetoothSocketIoThread.Listener {
    /**
     * Listener interface.
     */
    public interface Listener {
        void onIncomingConnection(BluetoothSocket socket, String peerId, String peerName, String peerAddress);
        void onSocketAcceptFailure(String reason);
    }

    private static final String TAG = BluetoothSocketListenerThread.class.getName();
    private final CopyOnWriteArrayList<BluetoothSocketIoThread> mSocketIoThreads = new CopyOnWriteArrayList<BluetoothSocketIoThread>();
    private final Listener mListener;
    private final BluetoothServerSocket mSocket;
    private final String mInstanceString;
    private boolean mStopThread = false;

    /**
     * Constructor.
     * @param listener
     * @param bluetoothAdapter
     * @param bluetoothUuid
     * @param bluetoothName
     * @param instanceString
     * @throws NullPointerException Thrown, if either the given listener or the Bluetooth adapter instance is null.
     * @throws IOException Thrown, if BluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord fails.
     */
    public BluetoothSocketListenerThread(
            Listener listener, BluetoothAdapter bluetoothAdapter,
            UUID bluetoothUuid, String bluetoothName, String instanceString)
            throws NullPointerException, IOException {
        if (listener == null || bluetoothAdapter == null)
        {
            throw new NullPointerException("Either the listener or the Bluetooth adapter instance is null");
        }

        mListener = listener;
        mInstanceString = instanceString;
        mSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(bluetoothName, bluetoothUuid);
    }

    /**
     * From Thread.
     */
    @Override
    public void run() {
        Log.i(TAG, "Thread started");
        final BluetoothSocketListenerThread thisInstance = this;

        while (!mStopThread) {
            Log.i(TAG, "Waiting to accept incoming connection...");
            BluetoothSocket socket;

            try {
                socket = mSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "Failed to accept socket: " + e.getMessage(), e);
                mListener.onSocketAcceptFailure("Failed to accept socket: " + e.getMessage());
                mStopThread = true;
                socket = null;
            }

            if (socket != null) {
                BluetoothSocketIoThread handshakeThread = null;

                try {
                    handshakeThread = new BluetoothSocketIoThread(socket, this);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create a handshake thread instance: " + e.getMessage(), e);
                }

                if (handshakeThread != null) {
                    mSocketIoThreads.add(handshakeThread);
                    handshakeThread.setDefaultUncaughtExceptionHandler(thisInstance.getUncaughtExceptionHandler());
                    handshakeThread.start();
                    Log.i(TAG, "Got an incoming connection (thread ID: " + handshakeThread.getId() + ")");
                }
            } else {
                Log.e(TAG, "Socket is null");
                mListener.onSocketAcceptFailure("Socket is null");
                mStopThread = true;
            }
        }

        Log.i(TAG, "Thread stopping");
    }

    /**
     *
     */
    public void stopListening() {
        Log.i(TAG, "Stopping...");
        mStopThread = true;

        for (BluetoothSocketIoThread thread : mSocketIoThreads) {
            if (thread != null) {
                thread.closeSocket();
            }
        }

        mSocketIoThreads.clear();

        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the socket: " + e.getMessage(), e);
            }
        }
    }

    /**
     *
     * @param bytes The array of bytes read.
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who) {
        Log.i(TAG, "onBytesRead: Read " + size + "bytes");
        String instanceString = new String(bytes);
        CommonUtils.PeerProperties peerProperties = new CommonUtils.PeerProperties();
        boolean resolvedPropertiesOk = false;
        final long threadId = who.getId();

        if (!instanceString.isEmpty()) {
            try {
                resolvedPropertiesOk =
                        CommonUtils.getPropertiesFromInstanceString(instanceString, peerProperties);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to resolve peer properties: " + e.getMessage(), e);
            }

            if (resolvedPropertiesOk) {
                // Set the resolved properties to the associated thread
                who.setPeerId(peerProperties.peerId);
                who.setPeerName(peerProperties.peerName);
                who.setPeerAddress(peerProperties.bluetoothAddress);

                // Respond by sending our identification
                if (!who.write(mInstanceString.getBytes())) {
                    Log.e(TAG, "Failed to respond to thread with ID " + threadId);
                }
            }
        }

        if (!resolvedPropertiesOk) {
            Log.e(TAG, "Failed to receive valid identification (thread ID: " + threadId + ")");

            for (BluetoothSocketIoThread thread : mSocketIoThreads) {
                if (thread != null && thread.getId() == threadId) {
                    Log.i(TAG, "Removing thread with ID " + threadId + ")");
                    mSocketIoThreads.remove(thread);
                    thread.closeSocket();
                    break;
                }
            }
        }
    }

    /**
     *
     * @param bytes The array of bytes written.
     * @param size The size of the array.
     * @param who The related BluetoothSocketIoThread instance.
     */
    @Override
    public void onBytesWritten(byte[] bytes, int size, BluetoothSocketIoThread who) {
        final long threadId = who.getId();
        Log.i(TAG, "onBytesWritten: " + size + " bytes successfully written (thread ID: " + threadId + ")");

        for (BluetoothSocketIoThread thread : mSocketIoThreads) {
            if (thread != null && thread.getId() == threadId) {
                Log.i(TAG, "Removing thread with ID " + threadId + ")");
                mSocketIoThreads.remove(thread);
                break;
            }
        }

        mListener.onIncomingConnection(
                who.getSocket(), who.getPeerId(), who.getPeerName(), who.getPeerAddress());
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
        boolean threadFound = false;

        for (BluetoothSocketIoThread thread : mSocketIoThreads) {
            if (thread != null && thread.getId() == who.getId()) {
                threadFound = true;
                mSocketIoThreads.remove(thread);
                thread.closeSocket();
                break;
            }
        }

        if (threadFound) {
            Log.e(TAG, "Handshake failed (thread ID: " + threadId + ")");
        } else {
            Log.i(TAG, "Handshake thread disposed (thread ID: " + threadId + ")");
        }
    }
}