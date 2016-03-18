/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * An abstract base class for BluetoothClientThread and BluetoothServerThread.
 */
abstract class AbstractBluetoothThread extends Thread {
    protected UUID mServiceRecordUuid = null;
    protected String mMyIdentityString = null;
    protected boolean mHandshakeRequired = false;

    /**
     * Constructor.
     *
     * @param serviceRecordUuid Our UUID (service record UUID to lookup RFCOMM channel).
     * @param myIdentityString Our identity (possible name and the Bluetooth MAC address). Used for
     *                         handshake (if required).
     */
    public AbstractBluetoothThread(UUID serviceRecordUuid, String myIdentityString) {
        if (serviceRecordUuid == null) {
            throw new NullPointerException("Service record UUID is null");
        }

        mServiceRecordUuid = serviceRecordUuid;
        mMyIdentityString = myIdentityString;
    }

    public boolean getHandshakeRequired() {
        return mHandshakeRequired;
    }

    public void setHandshakeRequired(boolean handshakeRequired) {
        mHandshakeRequired = handshakeRequired;
    }

    abstract public void shutdown();

    /**
     * Creates a handshake message. Uses the identity string for the message, if the string is
     * non-empty. Otherwise will return a simple, generic handshake message.
     *
     * @return The handshake message as a byte array.
     */
    protected byte[] getHandshakeMessage() {
        return (CommonUtils.isNonEmptyString(mMyIdentityString)
                ? mMyIdentityString.getBytes(StandardCharsets.UTF_8)
                : BluetoothUtils.SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY);
    }
}
