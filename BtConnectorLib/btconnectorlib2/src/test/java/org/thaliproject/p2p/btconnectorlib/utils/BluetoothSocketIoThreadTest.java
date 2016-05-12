package org.thaliproject.p2p.btconnectorlib.utils;

import android.bluetooth.BluetoothSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BluetoothSocketIoThreadTest {

    @Mock
    BluetoothSocket mMockBluetoothSocket;

    @Mock
    BluetoothSocketIoThread.Listener mMockListener;

    @Mock
    PeerProperties mMockPeerProperties;

    @Mock
    InputStream mMockInputStream;

    @Mock
    OutputStream mMockOutputStream;

    BluetoothSocketIoThread mBluetoothSocketIoThread;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockBluetoothSocket.getInputStream()).thenReturn(mMockInputStream);
        when(mMockBluetoothSocket.getOutputStream()).thenReturn(mMockOutputStream);

        mBluetoothSocketIoThread
                = new BluetoothSocketIoThread(mMockBluetoothSocket, mMockListener);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void bluetoothSocketIoThreadConstructor() throws Exception {
        BluetoothSocketIoThread bluetoothSocketIoThread
                = new BluetoothSocketIoThread(mMockBluetoothSocket, mMockListener);
        assertThat("The bluetoothSocketIoThread is properly created",
                bluetoothSocketIoThread, is(notNullValue()));

        assertThat("The BufferSizeInBytes is properly initialized",
                bluetoothSocketIoThread.getBufferSize(),
                is(BluetoothSocketIoThread.DEFAULT_BUFFER_SIZE_IN_BYTES));
    }

    @Test
    public void testGetSocket() throws Exception {

        assertThat("The socket is properly initialized",
                mBluetoothSocketIoThread.getSocket(), is(mMockBluetoothSocket));
    }

    @Test
    public void testPeerProperties() throws Exception {

        mBluetoothSocketIoThread.setPeerProperties(mMockPeerProperties);

        assertThat("The peerProperties is properly set",
                mBluetoothSocketIoThread.getPeerProperties(), is(mMockPeerProperties));
    }

    @Test
    public void testSetExitThreadAfterRead() throws Exception {

        mBluetoothSocketIoThread.setBufferSize(0);

        assertThat("The buffer size can't be set to 0 and lower",
                mBluetoothSocketIoThread.getBufferSize(),
                is(BluetoothSocketIoThread.DEFAULT_BUFFER_SIZE_IN_BYTES));


        mBluetoothSocketIoThread.setBufferSize(100);

        assertThat("The buffer size is properly set",
                mBluetoothSocketIoThread.getBufferSize(), is(100));
    }

    @Test
    public void testRun() throws Exception {

        mBluetoothSocketIoThread.setBufferSize(1);
        mBluetoothSocketIoThread.setExitThreadAfterRead(true);
        when(mMockInputStream.read(any(byte[].class))).thenReturn(0);

        ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(mBluetoothSocketIoThread);

        Thread.sleep(1000);
        verify(mMockListener, never()).onBytesRead(any(byte[].class),
                anyInt(), any(BluetoothSocketIoThread.class));

        mBluetoothSocketIoThread.setBufferSize(1);
        mBluetoothSocketIoThread.setExitThreadAfterRead(true);
        when(mMockInputStream.read(any(byte[].class))).thenReturn(1);
        service.shutdown();

        while (!service.awaitTermination(1L, TimeUnit.SECONDS)) {
            System.out.println("Not yet. Still waiting for termination");
        }

        service = Executors.newSingleThreadExecutor();
        service.execute(mBluetoothSocketIoThread);

        Thread.sleep(1000);
        verify(mMockListener, atLeastOnce()).onBytesRead(any(byte[].class),
                anyInt(), any(BluetoothSocketIoThread.class));
        service.shutdown();

        while (!service.awaitTermination(1L, TimeUnit.SECONDS)) {
            System.out.println("Not yet. Still waiting for termination");
        }

    }

    @Test
    public void testRunThrowsException() throws Exception {

        mBluetoothSocketIoThread.setBufferSize(1);
        mBluetoothSocketIoThread.setExitThreadAfterRead(true);

        //noinspection unchecked
        when(mMockInputStream.read(any(byte[].class))).thenThrow(IOException.class);

        ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(mBluetoothSocketIoThread);
        Thread.sleep(1000);
        verify(mMockListener, never()).onBytesRead(any(byte[].class),
                anyInt(), any(BluetoothSocketIoThread.class));
        verify(mMockListener, times(1)).onDisconnected(anyString(),
                any(BluetoothSocketIoThread.class));
        service.shutdown();

        while (!service.awaitTermination(1L, TimeUnit.SECONDS)) {
            System.out.println("Not yet. Still waiting for termination");
        }
    }

    @Test
    public void testWrite() throws Exception {
        String someText = "some text";
        assertThat("should return true if written properly",
                mBluetoothSocketIoThread.write(someText.getBytes()), is(true));
        verify(mMockListener, times(1)).onBytesWritten(any(byte[].class), anyInt(),
                any(BluetoothSocketIoThread.class));

        doThrow(IOException.class).when(mMockOutputStream).write(any(byte[].class));

        reset(mMockListener);
        assertThat("should return false on failure",
                mBluetoothSocketIoThread.write(someText.getBytes()), is(false));
        verify(mMockListener, never()).onBytesWritten(any(byte[].class), anyInt(),
                any(BluetoothSocketIoThread.class));

        when(mMockBluetoothSocket.getOutputStream()).thenReturn(null);
        assertThat("should return false when no outputStream",
                mBluetoothSocketIoThread.write(someText.getBytes()), is(false));
        verify(mMockListener, never()).onBytesWritten(any(byte[].class), anyInt(),
                any(BluetoothSocketIoThread.class));
    }

    @Test
    public void testClose() throws Exception {
        Field shuttingDownField = mBluetoothSocketIoThread.getClass().getDeclaredField("mIsShuttingDown");
        shuttingDownField.setAccessible(true);

        mBluetoothSocketIoThread.close(true, true);
        verify(mMockInputStream, times(1)).close();
        verify(mMockOutputStream, times(1)).close();
        verify(mMockBluetoothSocket, times(1)).close();

        assertThat("Should be set to true if closing",
                shuttingDownField.getBoolean(mBluetoothSocketIoThread), is(true));

        reset(mMockInputStream);
        reset(mMockOutputStream);
        reset(mMockBluetoothSocket);

        mBluetoothSocketIoThread.close(false, true);
        verify(mMockOutputStream, never()).close();
        verify(mMockInputStream, never()).close();
        verify(mMockBluetoothSocket, times(1)).close();

        assertThat("Should be set to true if closing",
                shuttingDownField.getBoolean(mBluetoothSocketIoThread), is(true));

        reset(mMockInputStream);
        reset(mMockOutputStream);
        reset(mMockBluetoothSocket);

        mBluetoothSocketIoThread.close(true, false);
        verify(mMockOutputStream, times(1)).close();
        verify(mMockInputStream, times(1)).close();
        verify(mMockBluetoothSocket, never()).close();

        assertThat("Should be set to true if closing",
                shuttingDownField.getBoolean(mBluetoothSocketIoThread), is(true));
    }
}