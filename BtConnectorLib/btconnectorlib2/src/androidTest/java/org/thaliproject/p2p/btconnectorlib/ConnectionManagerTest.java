package org.thaliproject.p2p.btconnectorlib;

import java.util.UUID;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.CoreMatchers.is;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ConnectionManagerTest {

    private static BluetoothAdapter mBluetoothAdapter = null;
    private ConnectionManager mConnectionManager = null;
    private Context mContext = null;

    @Mock
    ConnectionManager.ConnectionManagerListener mConnectionManagerListener;

    private static void toggleBluetooth(boolean turnOn) throws Exception {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            fail("No Bluetooth support!");
        }
        if (turnOn && !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        } else if (!turnOn && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
        Thread.sleep(3000);
        if (mBluetoothAdapter.isEnabled() != turnOn) {
            // wait additional 15 seconds
            Thread.sleep(15000);
        }
        assertThat(mBluetoothAdapter.isEnabled(), is(turnOn));
    }

    private void waitForMainLooper() throws java.lang.InterruptedException {
        // In API level 23 we could use MessageQueue.isIdle to check if all is ready
        // For now just use timeout
        Thread.sleep(100);
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        toggleBluetooth(true);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getContext();
        mConnectionManager = new ConnectionManager(mContext,
                                                   mConnectionManagerListener,
                                                   UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.NOT_STARTED));

        // check if connection manager is added as listener
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        try {
            cmSettings.addListener(mConnectionManager);
            fail();
        } catch (java.lang.IllegalArgumentException exc) {
        }
    }

    @Test
    public void testStartListeningBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(false));
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        // ensure bluetooth is enabled for other tests
        toggleBluetooth(true);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);
    }

    @Test
    public void testOnConnectionFailed() throws Exception {
        mConnectionManager.onConnectionFailed(null, "DUMMY_MESSAGE");
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1)).onConnectionFailed(null, "DUMMY_MESSAGE");
    }

    @Test
    public void testOnConnectionTimeout() throws Exception {
        mConnectionManager.onConnectionTimeout(null);
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1)).onConnectionTimeout(null);
    }

    @Test
    public void testOnConnected() throws Exception {
        mConnectionManager.onConnected(null, true, null);
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1)).onConnected(null, true, null);
    }

    @Test
    public void testOnIsServerStartedChanged() throws Exception {
        mConnectionManager.onIsServerStartedChanged(true);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        mConnectionManager.onIsServerStartedChanged(false);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.NOT_STARTED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.NOT_STARTED);
    }

    @Test
    public void testOnIsServerStartedChangedBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        mConnectionManager.startListeningForIncomingConnections();
        mConnectionManager.onIsServerStartedChanged(false);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // ensure bluetooth is enabled for other tests
        toggleBluetooth(true);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeNone() throws Exception {
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        // call again to check that state is not changed
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeConnectable() throws Exception {
        toggleBluetooth(false);
        // just set the state to WAITING_FOR_SERVICES_TO_BE_ENABLED
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        waitForMainLooper();
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        toggleBluetooth(true);

        // now call again with bluetooth enabled
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        // call again in state RUNNING
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

    }

    @Test
    public void testOnHandshakeRequiredSettingChanged() throws Exception {

    }

}