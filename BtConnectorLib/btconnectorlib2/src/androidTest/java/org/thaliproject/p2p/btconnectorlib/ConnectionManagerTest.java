package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ConnectionManagerTest extends AbstractConnectivityManagerTest {

    private ConnectionManager mConnectionManager = null;
    private Context mContext = null;

    @Mock
    private ConnectionManager.ConnectionManagerListener mConnectionManagerListener;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // call Looper.prepare in try/catch because it may be already called by other test
        try {
            Looper.prepare();
        } catch (java.lang.RuntimeException ex) {
            // can throw exception if current thread already has a looper
        }
    }

    @Before
    public void setUp() throws Exception {
        toggleBluetooth(true);
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getContext();
        mConnectionManager = new ConnectionManager(mContext,
                                                   mConnectionManagerListener,
                                                   UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {
        mConnectionManager.dispose();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));

        // check if connection manager is added as listener
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        thrown.expect(IllegalArgumentException.class);
        cmSettings.addListener(mConnectionManager);

        // check that bluetooth manager is created
        BluetoothManager btManager = BluetoothManager.getInstance(mContext);
        assertThat(mConnectionManager.getBluetoothManager(), is(btManager));
    }

    @Test
    public void testStartListeningBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(false));
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
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
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        mConnectionManager.onIsServerStartedChanged(false);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.NOT_STARTED);
    }

    @Test
    public void testOnIsServerStartedChangedBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        mConnectionManager.startListeningForIncomingConnections();
        mConnectionManager.onIsServerStartedChanged(false);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeNone() throws Exception {
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        // call again to check that state is not changed
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeConnectable() throws Exception {
        toggleBluetooth(false);
        // just set the state to WAITING_FOR_SERVICES_TO_BE_ENABLED
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        waitForMainLooper();
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        toggleBluetooth(true);

        // now call again with bluetooth enabled
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        // call again in state RUNNING
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.RUNNING));
        waitForMainLooper();
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);
    }

    @Test
    public void testOnHandshakeRequiredSettingChanged() throws Exception {
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        boolean currentHandshake = cmSettings.getHandshakeRequired();

        // check the current handshake flag
        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);
        Field handshakeField = bluetoothConnector.getClass().getDeclaredField("mHandshakeRequired");
        handshakeField.setAccessible(true);
        boolean handshake = handshakeField.getBoolean(bluetoothConnector);
        assertThat(handshake, is(currentHandshake));

        // now change it and check if it really changed
        cmSettings.setHandshakeRequired(!currentHandshake); // calls onHandshakeRequiredSettingChanged
        handshake = handshakeField.getBoolean(bluetoothConnector);
        assertThat(handshake, is(!currentHandshake));
    }

    @Test
    public void testOnConnectionManagerSettingsChanged() throws Exception {
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        long currentConnectionTimeout = cmSettings.getConnectionTimeout();
        int currentPortNumber = cmSettings.getInsecureRfcommSocketPortNumber();
        int currentRetries = cmSettings.getMaxNumberOfConnectionAttemptRetries();

        // now change the values and check if they really changed
        cmSettings.setConnectionTimeout(currentConnectionTimeout + 1);
        cmSettings.setInsecureRfcommSocketPortNumber(currentPortNumber + 1);
        cmSettings.setMaxNumberOfConnectionAttemptRetries(currentRetries + 1);

        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);

        Field timeoutField = bluetoothConnector.getClass()
                .getDeclaredField("mConnectionTimeoutInMilliseconds");
        timeoutField.setAccessible(true);
        long timeout = timeoutField.getLong(bluetoothConnector);
        assertThat(timeout, is(currentConnectionTimeout + 1));

        Field portField = bluetoothConnector.getClass().getDeclaredField("mInsecureRfcommSocketPort");
        portField.setAccessible(true);
        int port = portField.getInt(bluetoothConnector);
        assertThat(port, is(currentPortNumber + 1));

        Field retriesField = bluetoothConnector.getClass()
                .getDeclaredField("mMaxNumberOfOutgoingConnectionAttemptRetries");
        retriesField.setAccessible(true);
        int retries = retriesField.getInt(bluetoothConnector);
        assertThat(retries, is(currentRetries + 1));
    }

    @Test
     public void testDispose() throws Exception {
        toggleBluetooth(false);
        mConnectionManager.startListeningForIncomingConnections();
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        mConnectionManager.dispose();
        waitForMainLooper();
        assertThat(mConnectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        cmSettings.addListener(mConnectionManager);  // shouldn't fail as dispose removes listener
        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);
        Field isShutdownField = bluetoothConnector.getClass().getDeclaredField("mIsShuttingDown");
        isShutdownField.setAccessible(true);
        assertThat(isShutdownField.getBoolean(bluetoothConnector), is(true));
    }

    @Test
    public void testSetPeerName() throws Exception {
        String btAddress;
        if (!CommonUtils.isMarshmallowOrHigher()) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            btAddress = btAdapter.getAddress();
        } else {
            btAddress = android.provider.Settings.Secure.getString(
                    mContext.getContentResolver(), "bluetooth_address");
        }
        String expected = "{\"name\":\"TEST_NAME\",\"address\":\"" + btAddress + "\"}";

        mConnectionManager.setPeerName("TEST_NAME");
        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);
        Field identityField = bluetoothConnector.getClass().getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);
        assertThat((String) identityField.get(bluetoothConnector), is(expected));
    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {
        // tested with testDispose
    }

    @Test
    public void testConnect() throws Exception {
        // no target device - connection initialization fails
        assertThat(mConnectionManager.connect(null), is(false));

        // incorrect MAC address of target device - connection initialization fails
        PeerProperties peerProperties =  new PeerProperties("BAD MAC");
        assertThat(mConnectionManager.connect(peerProperties), is(false));

        // correct MAC address of target device - connection initialization starts
        peerProperties =  new PeerProperties("02:00:00:00:00:00");
        assertThat(mConnectionManager.connect(peerProperties), is(true));
    }

    @Test
    public void testCancelConnectionAttempt() throws Exception {
        PeerProperties peerProperties =  new PeerProperties("02:00:00:00:00:00");

        // no connection initialization started - cannot cancel
        assertThat(mConnectionManager.cancelConnectionAttempt(peerProperties), is(false));

        // initialization started - we can cancel
        mConnectionManager.connect(peerProperties);
        assertThat(mConnectionManager.cancelConnectionAttempt(peerProperties), is(true));
    }

    @Test
    public void testSetEmulateMarshmallow() throws Exception {
        mConnectionManager.setEmulateMarshmallow(true);
        Field emulateMarshmallowField = mConnectionManager.getClass().getSuperclass()
                .getDeclaredField("mEmulateMarshmallow");
        emulateMarshmallowField.setAccessible(true);
        assertThat(emulateMarshmallowField.getBoolean(mConnectionManager), is(true));

        mConnectionManager.setEmulateMarshmallow(false);
        assertThat(emulateMarshmallowField.getBoolean(mConnectionManager), is(false));
    }
}