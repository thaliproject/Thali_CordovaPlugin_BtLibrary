/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.model;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;
import java.io.IOException;
import java.util.Date;

/**
 * Represents a two-way connection to a peer.
 */
public class Connection implements BluetoothSocketIoThread.Listener {
    public interface Listener {
        void onBytesRead(byte[] bytes, int size, BluetoothSocketIoThread who);
        void onBytesWritten(byte[] bytes, int size, BluetoothSocketIoThread who);
        void onDisconnected(String reason, Connection who);
        void onSendDataProgress(float progressInPercentages, float transferSpeed, PeerProperties receivingPeer);
        void onDataSent(float dataSentInMegaBytes, float transferSpeed, PeerProperties receivingPeer);
    }

    private static final String TAG = Connection.class.getName();
    public static final int DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES = 1024 * 10;
    public static final long DEFAULT_DATA_AMOUNT_IN_BYTES = 1024 * 1024;
    private static final int REPORT_PROGRESS_INTERVAL_IN_MILLISECONDS = 1000;
    private static final byte[] PING_PACKAGE = new String("Is there anybody out there?").getBytes();
    private Listener mListener = null;
    private BluetoothSocketIoThread mBluetoothSocketIoThread = null;
    private PeerProperties mPeerProperties = null;
    private String mPeerId = null;
    private boolean mIsIncoming = false;
    private DataSenderHelper mDataSenderHelper = null;
    private long mTimeSinceLastReportedProgress = 0;
    private float mSendDataProgressInPercentages = 0f;
    private float mCurrentDataTransferSpeedInMegaBytesPerSecond = 0f;
    private boolean mIsClosed = false;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothSocket The Bluetooth socket associated with this connection.
     * @param peerProperties The peer properties.
     * @param isIncoming If true, this connection is incoming. If false, this is an outgoing connection.
     * @throws IOException
     * @throws NullPointerException
     */
    public Connection(
            Listener listener,
            BluetoothSocket bluetoothSocket, PeerProperties peerProperties,
            boolean isIncoming)
        throws IOException, NullPointerException {
        mListener = listener;
        mBluetoothSocketIoThread = new BluetoothSocketIoThread(bluetoothSocket, this);
        mBluetoothSocketIoThread.setPeerProperties(peerProperties);

        Settings settings = Settings.getInstance(null);

        if (settings != null) {
            mBluetoothSocketIoThread.setBufferSize(settings.getBufferSize());
        } else {
            mBluetoothSocketIoThread.setBufferSize(DEFAULT_SOCKET_IO_THREAD_BUFFER_SIZE_IN_BYTES);
        }

        mIsIncoming = isIncoming;
        mPeerProperties = peerProperties;
        mPeerId = mPeerProperties.getId();

        mBluetoothSocketIoThread.start();
    }

    public void setBufferSize(int bufferSizeInBytes) {
        mBluetoothSocketIoThread.setBufferSize(bufferSizeInBytes);
    }

    public String getPeerId() {
        return mPeerId;
    }

    public PeerProperties getPeerProperties() {
        return mPeerProperties;
    }

    public boolean getIsIncoming() {
        return mIsIncoming;
    }

    public boolean isSendingData() {
        return (mDataSenderHelper != null);
    }

    public boolean isClosed() {
        return mIsClosed;
    }

    public float getTotalDataAmountCurrentlySendingInMegaBytes() {
        if (mDataSenderHelper != null) {
            return mDataSenderHelper.getTotalDataAmountInMegaBytes();
        }

        return 0f;
    }

    public float getCurrentDataTransferSpeedInMegaBytesPerSecond() {
        return mCurrentDataTransferSpeedInMegaBytesPerSecond;
    }

    /**
     * @return The progress in percentages.
     */
    public float getSendDataProgress() {
        return mSendDataProgressInPercentages;
    }

    /**
     * Tries to send the given bytes to the peer.
     * @param bytes The bytes to send.
     */
    public void send(byte[] bytes) {
        SocketWriterThread thread = new SocketWriterThread();
        thread.write(bytes);
    }

    /**
     * Starts sending large amount of data.
     */
    public void sendData() {
        Settings settings = Settings.getInstance(null);

        if (settings != null) {
            mDataSenderHelper = new DataSenderHelper(settings.getDataAmount());
        } else {
            mDataSenderHelper = new DataSenderHelper(DEFAULT_DATA_AMOUNT_IN_BYTES);
        }

        Log.i(TAG, "sendData: Sending data now...");
        mDataSenderHelper.sendNextChunk();
        mTimeSinceLastReportedProgress = new Date().getTime();
        mSendDataProgressInPercentages = 0f;
        mCurrentDataTransferSpeedInMegaBytesPerSecond = 0f;
    }

    public void ping() {
        if (mDataSenderHelper == null) {
            send(PING_PACKAGE);
        }
    }

    public void disconnect() {
        close(true);
        mListener.onDisconnected("Disconnected by the user", this);
    }

    public void close(boolean closeSocket) {
        if (!mIsClosed) {
            mBluetoothSocketIoThread.close(true, closeSocket);
            Log.d(TAG, "close: Closed");
            mIsClosed = true;
        }
    }

    @Override
    public boolean equals(Object object) {
        Connection otherConnection = null;

        try {
            otherConnection = (Connection)object;
        } catch (Exception e) {
        }

        return (otherConnection != null
            && otherConnection.getPeerId().equals(mPeerId)
            && otherConnection.getIsIncoming() == mIsIncoming);
    }

    @Override
    public String toString() {
        return "[" + mPeerId + " " + mPeerProperties.getBluetoothMacAddress() + " " +
                (mIsIncoming  ? "incoming" : "outgoing") + "]";
    }

    @Override
    public void onBytesRead(byte[] bytes, int i, BluetoothSocketIoThread bluetoothSocketIoThread) {
        mListener.onBytesRead(bytes, i, bluetoothSocketIoThread);
    }

    @Override
    public void onBytesWritten(byte[] bytes, int i, BluetoothSocketIoThread bluetoothSocketIoThread) {
        mListener.onBytesWritten(bytes, i, bluetoothSocketIoThread);

        if (mDataSenderHelper != null) {
            long dataAmountLeft = mDataSenderHelper.getDataAmountLeft();

            if (dataAmountLeft == 0) {
                final DataSenderHelper dataSenderHelper = mDataSenderHelper;
                mDataSenderHelper = null;
                mSendDataProgressInPercentages = 1.0f;
                mCurrentDataTransferSpeedInMegaBytesPerSecond = 0f;

                mListener.onDataSent(
                        dataSenderHelper.getTotalDataAmountInMegaBytes(),
                        dataSenderHelper.calculateFinalTransferSpeed(),
                        mPeerProperties);
            } else {
                mDataSenderHelper.sendNextChunk();

                long currentTime = new Date().getTime();

                if (currentTime >= mTimeSinceLastReportedProgress + REPORT_PROGRESS_INTERVAL_IN_MILLISECONDS) {
                    long totalDataAmount = mDataSenderHelper.getTotalDataAmount();
                    double percentage = (double)(totalDataAmount - dataAmountLeft) / totalDataAmount;
                    mSendDataProgressInPercentages = (float)percentage;
                    mCurrentDataTransferSpeedInMegaBytesPerSecond =
                            mDataSenderHelper.calculateCurrentTransferSpeed(currentTime);

                    mListener.onSendDataProgress(
                            mSendDataProgressInPercentages,
                            mCurrentDataTransferSpeedInMegaBytesPerSecond,
                            mPeerProperties);

                    mTimeSinceLastReportedProgress = currentTime;
                }
            }
        }
    }

    /**
     * Forwards the event to the listener. The Bluetooth socket IO thread will always be the member
     * instance of this class.
     * @param reason The reason for disconnect.
     * @param bluetoothSocketIoThread The Bluetooth socket IO thread associated with the connection.
     */
    @Override
    public void onDisconnected(String reason, BluetoothSocketIoThread bluetoothSocketIoThread) {
        if (mDataSenderHelper != null) {
            mDataSenderHelper = null;
        }

        mListener.onDisconnected(reason, this);
    }

    /**
     * Thread for sending data to a peer using "fire and forget" principle.
     */
    private class SocketWriterThread extends Thread {
        private byte[] mBytesToWrite = null;

        public void write(byte[] bytes) {
            mBytesToWrite = bytes;
            this.start();
        }

        @Override
        public void run() {
            if (!mBluetoothSocketIoThread.write(mBytesToWrite)) {
                Log.e(TAG, "SocketWriterThread: Failed to write " + mBytesToWrite.length + " bytes");
            }
        }
    }

    /**
     * Helper for sending large amounts of data in chunks.
     */
    private class DataSenderHelper {
        private byte[] mDataChunk = null;
        private long mDataAmount = 0;
        private double mDataAmountInMegaBytes = 0;
        private long mDataAmountLeft = 0;
        private long mStartTime = 0;
        private long mEndTime = 0;

        public DataSenderHelper(long dataAmount) {
            mDataAmount = dataAmount;
            mDataAmountLeft = mDataAmount;
            mDataAmountInMegaBytes = (double)mDataAmount / (1024 * 1024);
            Log.i(TAG, "DataSendHelper: Set to send " + mDataAmountInMegaBytes + " MB");
        }

        public long getTotalDataAmount() {
            return mDataAmount;
        }

        public long getDataAmountLeft() {
            return mDataAmountLeft;
        }

        public float getTotalDataAmountInMegaBytes() {
            return (float)mDataAmountInMegaBytes;
        }

        /**
         * @param currentTime The current time.
         * @return The overall transfer speed to this point (current time) in megabytes.
         */
        public float calculateCurrentTransferSpeed(long currentTime) {
            if (currentTime > mStartTime) {
                long secondsElapsed = (currentTime - mStartTime) / 1000;
                double dataSentSoFar = (double)(mDataAmount - mDataAmountLeft);
                return (float)(dataSentSoFar / (1024 * 1024)) / secondsElapsed;
            }

            return 0;
        }

        /**
         * @return The final transfer speed in megabytes.
         */
        public float calculateFinalTransferSpeed() {
            if (mEndTime != 0) {
                long secondsElapsed = (mEndTime - mStartTime) / 1000;
                return (float)mDataAmountInMegaBytes / secondsElapsed;
            }

            return 0;
        }

        public void sendNextChunk() {
            if (mStartTime == 0) {
                mStartTime = new Date().getTime();
            }

            if (mDataAmountLeft > 0) {
                int bufferSize = mBluetoothSocketIoThread.getBufferSize();
                int chunkSize = (mDataAmountLeft < bufferSize) ? (int)mDataAmountLeft : bufferSize;
                mDataChunk = new byte[chunkSize];
                mDataAmountLeft -= chunkSize;
                send(mDataChunk);

                if (mDataAmountLeft == 0) {
                    mEndTime = new Date().getTime();
                }
            }
        }
    }
}
