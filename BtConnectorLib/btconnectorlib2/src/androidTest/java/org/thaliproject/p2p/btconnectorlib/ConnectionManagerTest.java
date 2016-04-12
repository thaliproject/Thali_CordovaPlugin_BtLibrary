package org.thaliproject.p2p.btconnectorlib;

import android.support.test.InstrumentationRegistry;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConnectionManagerTest {

    private class TestConnectionManagerListener implements ConnectionManager.ConnectionManagerListener {

        private String lastCall = "";

        public String getLastCall() {
            return lastCall;
        }

        @Override
        public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState state) {
            lastCall = "onConnectionManagerStateChanged";
        }

        @Override
        public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
            lastCall = "onConnected";
        }

        @Override
        public void onConnectionTimeout(PeerProperties peerProperties) {
            lastCall = "onConnectionTimeout";
        }

        @Override
        public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
            lastCall = "onConnectionFailed";
        }
    }

    private static BluetoothAdapter mBluetoothAdapter = null;
    private ConnectionManager mConnectionManager = null;
    private TestConnectionManagerListener mConnectionManagerListener = null;

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
        assertThat(mBluetoothAdapter.isEnabled(), is(turnOn));
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        toggleBluetooth(true);
    }

    @Before
    public void setUp() throws Exception {
        mConnectionManagerListener = new TestConnectionManagerListener();
        mConnectionManager = new ConnectionManager(InstrumentationRegistry.getContext(),
                                                   mConnectionManagerListener,
                                                   UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        assertEquals(ConnectionManager.ConnectionManagerState.NOT_STARTED, mConnectionManager.getState());

        // check if connection manager is added as listener
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(InstrumentationRegistry.getContext());
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
        Thread.sleep(1);
        assertThat(mConnectionManagerListener.getLastCall(), is("onConnectionManagerStateChanged"));

        // ensure bluetooth is enabled for other tests
        toggleBluetooth(true);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        Thread.sleep(1);
        assertThat(mConnectionManagerListener.getLastCall(), is("onConnectionManagerStateChanged"));
    }

    @Test
    public void testOnConnectionFailed() throws Exception {
        mConnectionManager.onConnectionFailed(null, "DUMMY_MESSAGE");
        Thread.sleep(1);
        assertThat(mConnectionManagerListener.getLastCall(), is("onConnectionFailed"));
    }

    @Test
    public void testOnConnectionTimeout() throws Exception {
        mConnectionManager.onConnectionTimeout(null);
        Thread.sleep(1);
        assertThat(mConnectionManagerListener.getLastCall(), is("onConnectionTimeout"));
    }

    @Test
    public void testOnConnected() throws Exception {
        mConnectionManager.onConnected(null, true, null);
        Thread.sleep(1);
        assertThat(mConnectionManagerListener.getLastCall(), is("onConnected"));
    }
}