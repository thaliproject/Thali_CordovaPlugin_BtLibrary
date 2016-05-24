package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothConnectorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
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
    @Mock
    CountDownTimer mMockConnectionTimeoutTimer;
    @Mock
    BluetoothClientThread mMockBluetoothClientThread;
    @Mock
    BluetoothServerThread mMockServerThread;
    @Mock
    Handler mMockHandler;
    BluetoothConnector mBluetoothConnector;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mMockSharedPreferences);
        when(mMockSharedPreferences.edit()).thenReturn(mMockEditor);

        mBluetoothConnector = new BluetoothConnector(mMockContext, mMockListener, mMockBluetoothAdapter,
                new UUID(1, 1), "name", "identity", mMockSharedPreferences);
    }

    @Test
    public void testConstructor() throws Exception {
        String name = "";
        String identity = "";

        BluetoothConnector btc = new BluetoothConnector(mMockContext, mMockListener,
                mMockBluetoothAdapter, new UUID(1, 1), name, identity, mMockSharedPreferences);

        assertThat(btc, is(notNullValue()));
    }

    @Test
    public void testSetIdentityString() throws Exception {
        String identity = "myIdentity";
        mBluetoothConnector.setIdentityString(identity);

        Field identityField = mBluetoothConnector.getClass().getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);

        assertThat("Has a proper identity", (String) identityField.get(mBluetoothConnector),
                is(identity));
    }

    @Test
    public void testSetConnectionTimeout() throws Exception {

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);

        mBluetoothConnector.setConnectionTimeout(
                BluetoothConnector.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS);
        assertThat("No timeout is set If the given value is the same as the current one",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));

        mBluetoothConnector.setConnectionTimeout(-1);
        assertThat("No timeout is set If the given value is negative",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));

        mBluetoothConnector.setConnectionTimeout(0);
        assertThat("No timeout is set If the given value is 0",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));

        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        // No client threads added
        mBluetoothConnector.setConnectionTimeout(1000);

        assertThat("No timeout is set If the client threads is list is empty",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));

        // check if the timer is canceled
        verify(mMockConnectionTimeoutTimer, times(1))
                .cancel();

        reset(mMockConnectionTimeoutTimer);

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        mBluetoothConnector.setConnectionTimeout(1500);

        assertThat("The proper timeout is set",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                instanceOf(CountDownTimer.class));

        // check if the timer is not canceled
        verify(mMockConnectionTimeoutTimer, never())
                .cancel();
    }

    @Test
    public void testSetInsecureRfcommSocketPort() throws Exception {
        Field insecureRfcommSocketPortField = mBluetoothConnector.getClass()
                .getDeclaredField("mInsecureRfcommSocketPort");
        insecureRfcommSocketPortField.setAccessible(true);

        mBluetoothConnector.setInsecureRfcommSocketPort(1500);
        assertThat("The proper insecure RFCOMM port is set",
                (Integer) insecureRfcommSocketPortField.get(mBluetoothConnector),
                is(1500));
    }

    @Test
    public void testSetMaxNumberOfOutgoingConnectionAttemptRetries() throws Exception {
        Field maxNumberOfRetriesField = mBluetoothConnector.getClass()
                .getDeclaredField("mMaxNumberOfOutgoingConnectionAttemptRetries");
        maxNumberOfRetriesField.setAccessible(true);

        mBluetoothConnector.setMaxNumberOfOutgoingConnectionAttemptRetries(1);

        assertThat("The proper maximum number of (outgoing) socket connection is set",
                (Integer) maxNumberOfRetriesField.get(mBluetoothConnector),
                is(1));
    }

    @Test
    public void testSetHandshakeRequired() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field serverThreadAliveField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsServerThreadAlive");
        serverThreadAliveField.setAccessible(true);
        serverThreadAliveField.set(mBluetoothConnector, false);

        Field handshakeRequiredField = mBluetoothConnector.getClass()
                .getDeclaredField("mHandshakeRequired");
        handshakeRequiredField.setAccessible(true);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);

        mBluetoothConnector.setHandshakeRequired(!BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED);

        // check if the listening for incoming connections is not stopped
        verify(mMockServerThread, never())
                .shutdown();

        assertThat("The HandshakeRequired is properly set",
                (Boolean) handshakeRequiredField.get(mBluetoothConnector),
                is(!BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED));

        serverThreadAliveField.set(mBluetoothConnector, true);

        mBluetoothConnector.setHandshakeRequired(BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED);

        assertThat("The HandshakeRequired is properly set",
                (Boolean) handshakeRequiredField.get(mBluetoothConnector),
                is(BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED));

        // check if the listening for incoming connections is stopped
        verify(mMockServerThread, times(1))
                .shutdown();

        assertThat("The mIsStoppingServeris properly set",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(true));

        assertThat("The server thread is set to null",
                serverThreadField.get(mBluetoothConnector),
                is(nullValue()));
    }

    @Test
    public void testStartListeningForIncomingConnections_failure() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field serverThreadAliveField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsServerThreadAlive");
        serverThreadAliveField.setAccessible(true);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);

        serverThreadAliveField.set(mBluetoothConnector, true);
        stoppingServerField.set(mBluetoothConnector, true);

        assertThat("Is true as the connector was already running",
                mBluetoothConnector.startListeningForIncomingConnections(),
                is(true));

        serverThreadAliveField.set(mBluetoothConnector, true);
        stoppingServerField.set(mBluetoothConnector, false);

        assertThat("Is true as the connector was already running",
                mBluetoothConnector.startListeningForIncomingConnections(),
                is(true));

        serverThreadAliveField.set(mBluetoothConnector, false);
        stoppingServerField.set(mBluetoothConnector, true);

        mBluetoothConnector.startListeningForIncomingConnections();

        assertThat("Is false as the connector is not running and stopping the server" +
                        " thread is still ongoing",
                mBluetoothConnector.startListeningForIncomingConnections(),
                is(false));
    }

    @Test
    public void testStartListeningForIncomingConnections_success() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field serverThreadAliveField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsServerThreadAlive");
        serverThreadAliveField.setAccessible(true);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);

        serverThreadAliveField.set(mBluetoothConnector, false);
        stoppingServerField.set(mBluetoothConnector, false);

        assertThat("Is true as the connector was started successfully",
                mBluetoothConnector.startListeningForIncomingConnections(),
                is(true));

        // check if the listening for incoming connections was restarted
        verify(mMockServerThread, times(1))
                .shutdown();

        assertThat("Is true as the new socket listener thread is created",
                (Boolean) serverThreadAliveField.get(mBluetoothConnector),
                is(true));

        verify(mMockListener, times(1))
                .onIsServerStartedChanged(true);

    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);

        stoppingServerField.set(mBluetoothConnector, false);
        mBluetoothConnector.stopListeningForIncomingConnections();

        assertThat("Server thread remains null as it was not started",
                serverThreadField.get(mBluetoothConnector),
                is(nullValue()));

        assertThat("It remains false as the listener was not stopped",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(false));

        // stopping listening for incoming connections
        stoppingServerField.set(mBluetoothConnector, false);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        mBluetoothConnector.stopListeningForIncomingConnections();

        verify(mMockServerThread, times(1))
                .shutdown();

        assertThat("Server thread is null as it was stopped",
                serverThreadField.get(mBluetoothConnector),
                is(nullValue()));

        assertThat("It is true as the listener was stopped",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(true));
    }

    // This one checks also the cancelAllConnectionAttempts
    @Test
    public void testShutdown() throws Exception {

        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);
        stoppingServerField.set(mBluetoothConnector, false);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field shuttingDownField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsShuttingDown");
        shuttingDownField.setAccessible(true);
        shuttingDownField.set(mBluetoothConnector, false);

        mBluetoothConnector.shutdown();

        verify(mMockServerThread, times(1))
                .shutdown();

        assertThat("Server thread is null as it was stopped",
                serverThreadField.get(mBluetoothConnector),
                is(nullValue()));

        assertThat("It is true as the listener was stopped",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(true));

        assertThat("It is empty as all client threads are shut down",
                myClientThreads.isEmpty(),
                is(true));

        // Wait for the other thread
        Thread.sleep(500);
        verify(mMockBluetoothClientThread, times(1))
                .shutdown();
    }

    @Test
    public void testConnect_noDevice() throws Exception {
        // No bluetooth device
        assertThat("Is false as no bluetooth device is provided",
                mBluetoothConnector.connect(null, mMockPeerProperties),
                is(false));
    }

    @Test
    public void testConnect() throws Exception {
        String name = "my device name";
        String address = "my device address";

        Field handshakeRequiredField = mBluetoothConnector.getClass()
                .getDeclaredField("mHandshakeRequired");
        handshakeRequiredField.setAccessible(true);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        when(mMockBluetoothDevice.getName()).thenReturn(name);
        when(mMockBluetoothDevice.getAddress()).thenReturn(address);

        assertThat("Is true as connection is properly started",
                mBluetoothConnector.connect(mMockBluetoothDevice, mMockPeerProperties),
                is(true));

        assertThat("It is not empty as client thread is added",
                myClientThreads.isEmpty(),
                is(false));

        BluetoothClientThread btcThread = myClientThreads.get(0);

        assertThat("Client peer properties is properly set",
                btcThread.getPeerProperties(),
                is(mMockPeerProperties));

        assertThat("Client handshake required is properly set",
                btcThread.getHandshakeRequired(),
                is((Boolean) handshakeRequiredField.get(mBluetoothConnector)));

        verify(mMockListener, times(1)).onConnecting(name, address);
    }

    @Test
    public void testCancelConnectionAttempt_exception() throws Exception {
        thrown.expect(NullPointerException.class);
        mBluetoothConnector.cancelConnectionAttempt(null);
    }

    @Test
    public void testCancelConnectionAttempt() throws Exception {
        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        assertThat("Is false as a client thread associated with the given peer properties not found",
                mBluetoothConnector.cancelConnectionAttempt(mMockPeerProperties),
                is(false));

        when(mMockBluetoothClientThread.getPeerProperties()).thenReturn(mMockPeerProperties);
        when(mMockBluetoothClientThread.getId()).thenReturn(123456789L);

        assertThat("Is true as a client thread associated with the given peer properties was found",
                mBluetoothConnector.cancelConnectionAttempt(mMockPeerProperties),
                is(true));

        // check if the timer was canceled
        verify(mMockConnectionTimeoutTimer, times(1))
                .cancel();

        // Wait for the other thread
        Thread.sleep(500);
        verify(mMockBluetoothClientThread, times(1))
                .shutdown();
    }

    @Test
    public void testOnIncomingConnectionConnected_connected() throws Exception {
        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        when(mMockBluetoothSocket.isConnected()).thenReturn(true);

        mBluetoothConnector.onIncomingConnectionConnected(mMockBluetoothSocket, mMockPeerProperties);
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, times(1)).onConnected(mMockBluetoothSocket, true, mMockPeerProperties);
    }

    @Test
    public void testOnIncomingConnectionConnected_notConnected() throws Exception {
        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        when(mMockBluetoothSocket.isConnected()).thenReturn(false);

        mBluetoothConnector.onIncomingConnectionConnected(mMockBluetoothSocket, mMockPeerProperties);
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, never()).onConnected(mMockBluetoothSocket, true, mMockPeerProperties);
    }

    @Test
    public void testOnIncomingConnectionFailed() throws Exception {
        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        mBluetoothConnector.onIncomingConnectionFailed("Test Error");
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, times(1)).onConnectionFailed(null, "Test Error");
    }

    @Test
    public void testOnServerStopped() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);
        stoppingServerField.set(mBluetoothConnector, false);

        Field serverThreadAliveField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsServerThreadAlive");
        serverThreadAliveField.setAccessible(true);
        serverThreadAliveField.set(mBluetoothConnector, true);

        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        // test not deliberate shutdown
        mBluetoothConnector.onServerStopped();
        verify(mMockHandler, never()).post(captor.capture());
        // This instance is still valid, check if the server is restart
        verify(mMockServerThread, times(1)).shutdown();

        assertThat("The server is restarted",
                serverThreadField.get(mBluetoothConnector),
                is(notNullValue()));

        // test deliberate shutdown
        stoppingServerField.set(mBluetoothConnector, true);

        mBluetoothConnector.onServerStopped();
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);
        verify(mMockListener, times(1)).onIsServerStartedChanged(false);

        assertThat("It is false as the server is stopped",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(false));

        assertThat("It is false as the server is stopped",
                (Boolean) serverThreadAliveField.get(mBluetoothConnector),
                is(false));
    }

    @Test
    public void testOnBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded() throws Exception {
        Field serverThreadField = mBluetoothConnector.getClass()
                .getDeclaredField("mServerThread");
        serverThreadField.setAccessible(true);
        serverThreadField.set(mBluetoothConnector, mMockServerThread);

        Field stoppingServerField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsStoppingServer");
        stoppingServerField.setAccessible(true);
        stoppingServerField.set(mBluetoothConnector, false);

        mBluetoothConnector.onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded(10);

        verify(mMockServerThread, times(1)).shutdown();

        assertThat("The server is stopped",
                serverThreadField.get(mBluetoothConnector),
                is(nullValue()));

        assertThat("The server will not be automatically restarted",
                (Boolean) stoppingServerField.get(mBluetoothConnector),
                is(true));
    }

    @Test
    public void testOnSocketConnected() throws Exception {
        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        Field shuttingDownField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsShuttingDown");
        shuttingDownField.setAccessible(true);
        shuttingDownField.set(mBluetoothConnector, true);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        // handshake required
        when(mMockBluetoothClientThread.getHandshakeRequired()).thenReturn(true);

        mBluetoothConnector.onSocketConnected(mMockBluetoothSocket, mMockPeerProperties,
                mMockBluetoothClientThread);

        verify(mMockHandler, never()).post(captor.capture());

        // handshake required, notify the listener, shutting down in progress
        when(mMockBluetoothClientThread.getHandshakeRequired()).thenReturn(false);
        mBluetoothConnector.onSocketConnected(mMockBluetoothSocket, mMockPeerProperties,
                mMockBluetoothClientThread);

        verify(mMockConnectionTimeoutTimer, times(1)).cancel();
        assertThat("No timeout is set If the client threads is list is empty",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));
        verify(mMockHandler, never()).post(captor.capture());

        // handshake required, notify the listener
        // Add client threads
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        when(mMockBluetoothClientThread.getHandshakeRequired()).thenReturn(false);
        shuttingDownField.set(mBluetoothConnector, false);

        when(mMockBluetoothSocket.isConnected()).thenReturn(false);

        mBluetoothConnector.onSocketConnected(mMockBluetoothSocket, mMockPeerProperties,
                mMockBluetoothClientThread);

        verify(mMockConnectionTimeoutTimer, times(1)).cancel();
        assertThat("No timeout is set If the client threads is list is empty",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, never()).onConnected(mMockBluetoothSocket, false, mMockPeerProperties);
    }

    @Test
    public void testOnSocketConnected_success() throws Exception {
        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        Field shuttingDownField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsShuttingDown");
        shuttingDownField.setAccessible(true);
        shuttingDownField.set(mBluetoothConnector, true);

        when(mMockBluetoothSocket.isConnected()).thenReturn(true);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        // handshake required, notify the listener
        // Add client threads

        when(mMockBluetoothClientThread.getHandshakeRequired()).thenReturn(false);
        shuttingDownField.set(mBluetoothConnector, false);

        mBluetoothConnector.onSocketConnected(mMockBluetoothSocket, mMockPeerProperties,
                mMockBluetoothClientThread);

        verify(mMockConnectionTimeoutTimer, times(1)).cancel();
        assertThat("No timeout is set If the client threads is list is empty",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, times(1)).onConnected(mMockBluetoothSocket, false, mMockPeerProperties);
    }

    @Test
    public void testOnHandshakeSucceeded() throws Exception {
        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        Field shuttingDownField = mBluetoothConnector.getClass()
                .getDeclaredField("mIsShuttingDown");
        shuttingDownField.setAccessible(true);
        shuttingDownField.set(mBluetoothConnector, true);

        when(mMockBluetoothSocket.isConnected()).thenReturn(true);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        // handshake required, notify the listener
        // Add client threads

        when(mMockBluetoothClientThread.getHandshakeRequired()).thenReturn(false);
        shuttingDownField.set(mBluetoothConnector, false);

        mBluetoothConnector.onHandshakeSucceeded(mMockBluetoothSocket, mMockPeerProperties,
                mMockBluetoothClientThread);

        verify(mMockConnectionTimeoutTimer, times(1)).cancel();
        assertThat("No timeout is set If the client threads is list is empty",
                connectionTimeoutTimerField.get(mBluetoothConnector),
                is(nullValue()));
        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, times(1)).onConnected(mMockBluetoothSocket, false, mMockPeerProperties);
    }

    @Test
    public void testOnConnectionFailed() throws Exception {
        Field connectionTimeoutTimerField = mBluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutTimer");
        connectionTimeoutTimerField.setAccessible(true);
        connectionTimeoutTimerField.set(mBluetoothConnector, mMockConnectionTimeoutTimer);

        Field handlerField = mBluetoothConnector.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(mBluetoothConnector, mMockHandler);

        Field clientThreadsField = mBluetoothConnector.getClass().getDeclaredField("mClientThreads");
        clientThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothClientThread> myClientThreads = new CopyOnWriteArrayList<>();

        // With client threads added
        myClientThreads.add(mMockBluetoothClientThread);
        clientThreadsField.set(mBluetoothConnector, myClientThreads);

        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        String msg = "error message";

        mBluetoothConnector.onConnectionFailed(mMockPeerProperties, msg,
                mMockBluetoothClientThread);

        assertThat("It is empty as client thread is removed",
                myClientThreads.isEmpty(),
                is(true));

        verify(mMockHandler, times(1)).post(captor.capture());

        Thread thread = new Thread(captor.getValue());
        thread.start();
        // Wait for the other thread
        Thread.sleep(500);

        verify(mMockListener, times(1)).onConnectionFailed(mMockPeerProperties, msg);
    }
}