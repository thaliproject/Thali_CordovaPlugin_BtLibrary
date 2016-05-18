package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.utils.BluetoothSocketIoThread;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothServerThreadTest {
    @Mock
    Context mMockContext;
    @Mock
    SharedPreferences mMockSharedPreferences;
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    BluetoothServerThread.Listener mMockListener;
    @Mock
    BluetoothServerSocket mMockBluetoothServerSocket;
    @Mock
    BluetoothSocket mMockBluetoothSocket;
    @Mock
    BluetoothDevice mMockBluetoothDevice;
    @Mock
    BluetoothSocketIoThread mMockBluetoothSocketIoThread;
    @Mock
    InputStream mMockInputStream;
    BluetoothServerThread mBluetoothServerThread;
    private String myIdentityId = "IdentityId";
    private UUID myUUID = new UUID(1, 1);
    private String myServerName = "serverName";
    private Field mStopThreadField;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mBluetoothServerThread = new BluetoothServerThread(mMockListener,
                mMockBluetoothAdapter, myUUID, myServerName, myIdentityId);

        assertThat("The BT server thread is properly instantiated",
                mBluetoothServerThread, is(notNullValue()));

        mStopThreadField = mBluetoothServerThread.getClass()
                .getDeclaredField("mStopThread");
        mStopThreadField.setAccessible(true);
        mStopThreadField.set(mBluetoothServerThread, false);
    }

    @After
    public void tearDown() throws Exception {

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRun_Handshake() throws Exception {
        mBluetoothServerThread.setHandshakeRequired(true);

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);

        when(mMockBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(myServerName, myUUID))
                .thenReturn(mMockBluetoothServerSocket);

        // mock blocking call mBluetoothServerSocket.accept()
        when(mMockBluetoothServerSocket.accept()).thenAnswer(new Answer<BluetoothSocket>() {
            @Override
            public BluetoothSocket answer(InvocationOnMock invocation) throws
                    InterruptedException, IllegalAccessException {
                mStopThreadField.set(mBluetoothServerThread, true);
                return mMockBluetoothSocket;
            }
        });

        when(mMockBluetoothSocket.getInputStream()).thenReturn(mMockInputStream);
        when(mMockInputStream.read()).thenAnswer(new Answer<byte[]>() {
            @Override
            public byte[] answer(InvocationOnMock invocation) throws
                    InterruptedException, IllegalAccessException {
                return "some text".getBytes();
            }
        });

        // Actual test
        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                mBluetoothServerThread.run();
            }
        });
        service.start();
        service.join();

        CopyOnWriteArrayList<BluetoothSocketIoThread> mSocketIoThreads
                = (CopyOnWriteArrayList<BluetoothSocketIoThread>)
                mSocketIoThreadsField.get(mBluetoothServerThread);

        BluetoothSocketIoThread handshakeThread = mSocketIoThreads.get(0);
        assertThat("The handshake thread instance has the proper socket is set",
                handshakeThread.getSocket(),
                is(mMockBluetoothSocket));

        verify(mMockListener, never()).onIncomingConnectionConnected(any(BluetoothSocket.class),
                any(PeerProperties.class));

        verify(mMockListener, times(1)).onServerStopped();
    }

    @Test
    public void testRun_NoHandshake() throws Exception {
        String macAddress = "0A:1B:2C:3D:4E:5F";
        mBluetoothServerThread.setHandshakeRequired(false);

        when(mMockBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(myServerName, myUUID))
                .thenReturn(mMockBluetoothServerSocket);

        // mock blocking call mBluetoothServerSocket.accept()
        when(mMockBluetoothServerSocket.accept()).thenAnswer(new Answer<BluetoothSocket>() {
            @Override
            public BluetoothSocket answer(InvocationOnMock invocation) throws
                    InterruptedException, IllegalAccessException {
                mStopThreadField.set(mBluetoothServerThread, true);
                return mMockBluetoothSocket;
            }
        });

        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);

        // Actual test
        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                mBluetoothServerThread.run();
            }
        });
        service.start();
        service.join();

        final ArgumentCaptor<BluetoothSocket> btSocketCaptor
                = ArgumentCaptor.forClass(BluetoothSocket.class);
        final ArgumentCaptor<PeerProperties> ppCaptor
                = ArgumentCaptor.forClass(PeerProperties.class);

        verify(mMockListener, times(1)).onIncomingConnectionConnected(btSocketCaptor.capture(),
                ppCaptor.capture());

        assertThat("The proper BT address is set",
                ppCaptor.getValue().getBluetoothMacAddress(),
                is(macAddress));

        verify(mMockListener, times(1)).onServerStopped();
    }


    @Test
    public void testRun_FailedInvalidAddress() throws Exception {
        String invalifMacAddress = "IN:VA:LI:D0:AD:DR";
        mBluetoothServerThread.setHandshakeRequired(false);

        when(mMockBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(myServerName, myUUID))
                .thenReturn(mMockBluetoothServerSocket);

        // mock blocking call mBluetoothServerSocket.accept()
        when(mMockBluetoothServerSocket.accept()).thenAnswer(new Answer<BluetoothSocket>() {
            @Override
            public BluetoothSocket answer(InvocationOnMock invocation) throws
                    InterruptedException, IllegalAccessException {
                mStopThreadField.set(mBluetoothServerThread, true);
                return mMockBluetoothSocket;
            }
        });

        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.getAddress()).thenReturn(invalifMacAddress);

        // Actual test
        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                mBluetoothServerThread.run();
            }
        });

        service.start();
        service.join();

        verify(mMockListener, never()).onIncomingConnectionConnected(any(BluetoothSocket.class),
                any(PeerProperties.class));

        verify(mMockListener, times(1)).onIncomingConnectionFailed(anyString());
        verify(mMockListener, times(1)).onServerStopped();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRun_FailedToAcceptSocket() throws Exception {
        mBluetoothServerThread.setHandshakeRequired(false);
        when(mMockBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(myServerName, myUUID))
                .thenReturn(mMockBluetoothServerSocket);

        when(mMockBluetoothServerSocket.accept()).thenThrow(IOException.class);

        // Actual test
        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                mBluetoothServerThread.run();
            }
        });

        service.start();
        service.join();

        verify(mMockListener, times(1)).onIncomingConnectionFailed(anyString());
        verify(mMockListener, times(1)).onServerStopped();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRun_FailedToStartListening() throws Exception {
        Field limitField = mBluetoothServerThread.getClass()
                .getDeclaredField("BLUETOOTH_SERVER_SOCKET_CONSECUTIVE_CREATION_FAILURE_COUNT_LIMIT");
        limitField.setAccessible(true);

        when(mMockBluetoothAdapter
                .listenUsingInsecureRfcommWithServiceRecord(myServerName, myUUID))
                .thenThrow(IOException.class);

        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                mBluetoothServerThread.run();
            }
        });

        service.start();
        service.join();

        verify(mMockListener, times(1))
                .onBluetoothServerSocketConsecutiveCreationFailureCountLimitExceeded(
                        limitField.getInt(mBluetoothServerThread)
                );

        verify(mMockListener, times(1)).onServerStopped();
    }

    @Test
    public void testShutdown() throws Exception {
        Field mBluetoothServerSocketField = mBluetoothServerThread.getClass()
                .getDeclaredField("mBluetoothServerSocket");
        mBluetoothServerSocketField.setAccessible(true);
        mBluetoothServerSocketField.set(mBluetoothServerThread, mMockBluetoothServerSocket);

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);

        mySocketIoThreads.add(mMockBluetoothSocketIoThread);
        mBluetoothServerThread.shutdown();

        verify(mMockBluetoothSocketIoThread, times(1)).close(true, true);
        verify(mMockBluetoothServerSocket, times(1)).close();

        assertThat("The list of IO threads is cleared",
                mySocketIoThreads.isEmpty(), is(true));
    }

    @Test
    public void testShutdown_exception() throws Exception {
        Field mBluetoothServerSocketField = mBluetoothServerThread.getClass()
                .getDeclaredField("mBluetoothServerSocket");
        mBluetoothServerSocketField.setAccessible(true);
        mBluetoothServerSocketField.set(mBluetoothServerThread, mMockBluetoothServerSocket);

        // the exception
        doThrow(IOException.class).when(
                mMockBluetoothServerSocket).close();

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);

        mySocketIoThreads.add(mMockBluetoothSocketIoThread);
        mBluetoothServerThread.shutdown();

        verify(mMockBluetoothSocketIoThread, times(1)).close(true, true);

        assertThat("The list of IO threads is cleared",
                mySocketIoThreads.isEmpty(), is(true));
    }

    @Test
    public void testOnBytesRead() throws Exception {
        String macAddress = "0A:1B:2C:3D:4E:5F";

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);
        mySocketIoThreads.add(mMockBluetoothSocketIoThread);

        when(mMockBluetoothSocketIoThread.getSocket()).thenReturn(mMockBluetoothSocket);
        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);

        // mock response
        when(mMockBluetoothSocketIoThread.write(
                myIdentityId.getBytes(StandardCharsets.UTF_8))).thenReturn(true);

        mBluetoothServerThread.onBytesRead(BluetoothUtils.SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY,
                15, mMockBluetoothSocketIoThread);

        // check if the associated thread is closed
        verify(mMockBluetoothSocketIoThread, never()).close(anyBoolean(), anyBoolean());

        // check if the resolved properties set to the associated thread
        verify(mMockBluetoothSocketIoThread, times(1)).setPeerProperties(any(PeerProperties.class));

        // check if responded with our own identity
        verify(mMockBluetoothSocketIoThread, times(1))
                .write(myIdentityId.getBytes(StandardCharsets.UTF_8));

        assertThat("The list of IO threads is not changed",
                mySocketIoThreads.isEmpty(), is(false));
    }

    @Test
    public void testOnBytesRead_notValidIdentity() throws Exception {

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);
        mySocketIoThreads.add(mMockBluetoothSocketIoThread);

        mBluetoothServerThread.onBytesRead("WrongMessage".getBytes(),
                15, mMockBluetoothSocketIoThread);

        // check if the associated thread is closed
        verify(mMockBluetoothSocketIoThread, times(1)).close(true, true);

        // check if the resolved properties set to the associated thread
        verify(mMockBluetoothSocketIoThread, never()).setPeerProperties(any(PeerProperties.class));

        // check if responded with our own identity
        verify(mMockBluetoothSocketIoThread, never())
                .write(myIdentityId.getBytes(StandardCharsets.UTF_8));

        assertThat("The thread is removed from the list of IO threads",
                mySocketIoThreads.isEmpty(), is(true));
    }

    @Test
    public void testOnBytesRead_notValidResponse() throws Exception {
        String macAddress = "0A:1B:2C:3D:4E:5F";

        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);
        mySocketIoThreads.add(mMockBluetoothSocketIoThread);

        when(mMockBluetoothSocketIoThread.getSocket()).thenReturn(mMockBluetoothSocket);
        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);

        // mock response
        when(mMockBluetoothSocketIoThread.write(
                myIdentityId.getBytes(StandardCharsets.UTF_8))).thenReturn(false);

        mBluetoothServerThread.onBytesRead(BluetoothUtils.SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY,
                15, mMockBluetoothSocketIoThread);

        // check if the associated thread is closed
        verify(mMockBluetoothSocketIoThread, times(1)).close(true, true);

        assertThat("The thread is removed from the list of IO threads",
                mySocketIoThreads.isEmpty(), is(true));
    }

    @Test
    public void testOnBytesWritten() throws Exception {
        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);
        mySocketIoThreads.add(mMockBluetoothSocketIoThread);


        mBluetoothServerThread.onBytesWritten("aText".getBytes(), 15, mMockBluetoothSocketIoThread);

        // check if the associated thread is closed
        verify(mMockBluetoothSocketIoThread, never()).close(anyBoolean(), anyBoolean());

        assertThat("The thread is removed from the list of IO threads",
                mySocketIoThreads.isEmpty(), is(true));
    }

    @Test
    public void testOnDisconnected() throws Exception {
        Field mSocketIoThreadsField = mBluetoothServerThread.getClass()
                .getDeclaredField("mSocketIoThreads");
        mSocketIoThreadsField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothSocketIoThread> mySocketIoThreads
                = new CopyOnWriteArrayList<>();
        mSocketIoThreadsField.set(mBluetoothServerThread, mySocketIoThreads);
        mySocketIoThreads.add(mMockBluetoothSocketIoThread);


        mBluetoothServerThread.onDisconnected("Some reson", mMockBluetoothSocketIoThread);

        // check if the associated thread is closed
        verify(mMockBluetoothSocketIoThread, times(1)).close(true, true);

        assertThat("The thread is removed from the list of IO threads",
                mySocketIoThreads.isEmpty(), is(true));
    }
}