// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by juksilve on 11.3.2015.
 */

class BTHandShakeSocketTread extends Thread {

    public static final int MESSAGE_READ         = 0x11;
    public static final int MESSAGE_WRITE        = 0x22;
    public static final int SOCKET_DISCONNECTED  = 0x33;

    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    public BTHandShakeSocketTread(BluetoothSocket socket, Handler handler)  throws IOException {
        Log.i("", "Creating BTHandShakeSocketTread");
        mHandler = handler;
        mmSocket = socket;
        mmInStream = mmSocket.getInputStream();
        mmOutStream = mmSocket.getOutputStream();
    }
    public void run() {
        Log.i("", "BTHandShakeSocketTread started");
        byte[] buffer = new byte[255];
        int bytes;

        try {
            bytes = mmInStream.read(buffer);
            //Log.d(TAG, "ConnectedThread read data: " + bytes + " bytes");
            mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
        } catch (IOException e) {
            Log.i("", "BTHandShakeSocketTread disconnected: " +  e.toString());
            mHandler.obtainMessage(SOCKET_DISCONNECTED, -1, -1, e).sendToTarget();
        }
        Log.i("", "BTHandShakeSocketTread fully stopped");
    }
    /**
     * Write to the connected OutStream.
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) {

        if (mmOutStream == null) {
            return;
        }

        try {
            mmOutStream.write(buffer);
            mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer).sendToTarget();
        } catch (IOException e) {
            // when write fails, the timeout for handshake will clear things out eventually.
            Log.i("", "BTHandShakeSocketTread  write failed: " + e.toString());
        }
    }

    public void CloseSocket() {

        if (mmInStream != null) {
            try {mmInStream.close();} catch (IOException e) {e.printStackTrace();}
        }

        if (mmOutStream != null) {
            try {mmOutStream.close();} catch (IOException e) {e.printStackTrace();}
        }

        if (mmSocket != null) {
            try {mmSocket.close();} catch (IOException e) {e.printStackTrace();}
        }
    }
}
