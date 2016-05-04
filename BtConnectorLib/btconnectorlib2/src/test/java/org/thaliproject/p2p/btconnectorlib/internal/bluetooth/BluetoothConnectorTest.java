package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class BluetoothConnectorTest {
    @Mock
    Context mMockContext;

    @Mock
    BluetoothConnector.BluetoothConnectorListener mMockListener;

    @Mock
    BluetoothManager mMockBluetoothManager;

    @Mock
    BluetoothAdapter mMockBluetoothAdapter;

    @Mock
    SharedPreferences mMockSharedPreferences;

    @Mock
    SharedPreferences.Editor mMockEditor;

    @Mock
    BluetoothSocket mMockBluetoothSocket;

    @Mock
    BluetoothDevice mMockBluetoothDevice;

    @Mock
    PeerProperties mMockPeerProperties;

    BluetoothConnector mBluetoothConnector;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mMockSharedPreferences);
        when(mMockSharedPreferences.edit()).thenReturn(mMockEditor);

        mBluetoothConnector = new BluetoothConnector(mMockContext, mMockListener, mMockBluetoothAdapter,
                new UUID(1,1), "name", "identity", mMockSharedPreferences );

    }

    @Test
    public void testConstructorThatTakesContextAndPrefs() throws Exception {
        String name = "";
        String identity = "";

        BluetoothConnector btc = new BluetoothConnector(mMockContext, mMockListener, mMockBluetoothAdapter,
                new UUID(1,1), name, identity, mMockSharedPreferences );

        assertThat(btc, is(notNullValue()));
    }

    @Test
    public void testSetIdentityString() throws Exception {
        String identity = "myIdentity";
        mBluetoothConnector.setIdentityString(identity);

        Field identityField = mBluetoothConnector.getClass().getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);

        assertThat("Has a proper identity", (String)identityField.get(mBluetoothConnector),
                is(identity));
    }

    @Test
    public void testSetConnectionTimeout() throws Exception {

    }

    @Test
    public void testSetInsecureRfcommSocketPort() throws Exception {

    }

    @Test
    public void testSetMaxNumberOfOutgoingConnectionAttemptRetries() throws Exception {

    }

    @Test
    public void testSetHandshakeRequired() throws Exception {

    }

    @Test
    public void testStartListeningForIncomingConnections() throws Exception {

    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {

    }

    @Test
    public void testShutdown() throws Exception {

    }

    @Test
    public void testConnect() throws Exception {

    }

    @Test
    public void testCancelConnectionAttempt() throws Exception {

    }

    @Test
    public void testCancelAllConnectionAttempts() throws Exception {

    }

    @Test
    public void testOnIncomingConnectionConnected() throws Exception {

    }

    @Test
    public void testOnIncomingConnectionFailed() throws Exception {

    }

    @Test
    public void testOnServerStopped() throws Exception {

    }

    @Test
    public void testOnBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded() throws Exception {

    }

    @Test
    public void testOnSocketConnected() throws Exception {

    }

    @Test
    public void testOnHandshakeSucceeded() throws Exception {

    }

    @Test
    public void testOnConnectionFailed() throws Exception {

    }
}