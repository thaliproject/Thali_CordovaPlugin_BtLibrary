package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.CountDownTimer;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.utils.CommonUtils;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(AndroidJUnit4.class)
public class ConnectionManagerTest extends AbstractConnectivityManagerTest {

    private ConnectionManager mConnectionManager = null;
    private Context mContext = null;
    private final long MAX_TIMEOUT = 20000;
    private final long CHECK_INTERVAL = 500;

    // helper to check discovery state with defined timeout
    private void checkStateWithTimeout(ConnectionManager.ConnectionManagerState expectedState)
            throws java.lang.InterruptedException {
        long currentTimeout = 0;
        while (currentTimeout < MAX_TIMEOUT) {
            Thread.sleep(CHECK_INTERVAL);
            currentTimeout += CHECK_INTERVAL;
            try {
                assertThat(mConnectionManager.getState(), is(expectedState));
                break;
            } catch (java.lang.AssertionError assertionError) {
                if (currentTimeout >= MAX_TIMEOUT) {
                    throw assertionError;
                }
            }
        }
    }

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
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
        mConnectionManager = null;
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        // check if connection manager is added as listener
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        thrown.expect(IllegalArgumentException.class);
        cmSettings.addListener(mConnectionManager);

        // check that bluetooth manager is created
        BluetoothManager btManager = BluetoothManager.getInstance(mContext);
        assertThat(mConnectionManager.getBluetoothManager(), is(btManager));
    }

    @Test
    public void testIllegalArgumentsConstruction() throws Exception {
        // connection manager listener may be null - correct call
        new ConnectionManager(mContext, null, UUID.randomUUID(), "DUMMY_NAME");

        // context must be given
        thrown.expect(NullPointerException.class);
        new ConnectionManager(null, mConnectionManagerListener, UUID.randomUUID(), "DUMMY_NAME");

        // UUID must be given
        thrown.expect(NullPointerException.class);
        new ConnectionManager(mContext, mConnectionManagerListener, null, "DUMMY_NAME");
    }

    @Test
    public void testStartListeningBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(false));
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
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
        mConnectionManager.startListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        mConnectionManager.onIsServerStartedChanged(false);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        mConnectionManager.onIsServerStartedChanged(true);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verify(mConnectionManagerListener, times(2))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);
    }

    @Test
    public void testOnIsServerStartedChangedBluetoothDisabled() throws Exception {
        toggleBluetooth(false);

        mConnectionManager.startListeningForIncomingConnections();
        mConnectionManager.onIsServerStartedChanged(false);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeNone() throws Exception {
        toggleBluetooth(false);
        mConnectionManager.startListeningForIncomingConnections();
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        // call again to check that state is not changed
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChangedScanModeConnectable() throws Exception {
        toggleBluetooth(false);
        mConnectionManager.startListeningForIncomingConnections();
        // just set the state to WAITING_FOR_SERVICES_TO_BE_ENABLED
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(
                        ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        toggleBluetooth(true);

        // now call again with bluetooth enabled
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        // call again in state RUNNING
        mConnectionManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
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
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        mConnectionManager.dispose();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
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

        // correct peer name
        mConnectionManager.setPeerName("TEST_NAME");
        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);
        Field identityField = bluetoothConnector.getClass().getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);
        assertThat((String) identityField.get(bluetoothConnector), is(expected));

        // null peer name
        thrown.expect(NullPointerException.class);
        mConnectionManager.setPeerName(null);
    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {
        // tested with testDispose
    }

    @Test
    public void testStartStopListening() throws Exception {
        boolean isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);

        // call start again - no action expected
        isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verifyNoMoreInteractions(mConnectionManagerListener);

        // call stop now
        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
        verify(mConnectionManagerListener, times(1))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        // call stop again
        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
        verifyNoMoreInteractions(mConnectionManagerListener);

        // now start and stop again
        isRunning = mConnectionManager.startListeningForIncomingConnections();
        assertThat(isRunning, is(true));
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);
        verify(mConnectionManagerListener, times(2))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.RUNNING);
        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
        verify(mConnectionManagerListener, times(2))
                .onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState.NOT_STARTED);
    }

    @Test
    public void testConnect() throws Exception {
        // no target device - connection initialization fails
        assertThat(mConnectionManager.connect(null), is(false));

        // incorrect MAC address of target device - connection initialization fails
        PeerProperties peerProperties =  new PeerProperties("BAD MAC");
        assertThat(mConnectionManager.connect(peerProperties), is(false));

        // correct MAC address of target device
        // helper class to test connection with timeout
        class ConnectionThread extends Thread {

            private ConnectionManager testConnectionManager;
            private PeerProperties testPeerProperties;
            private long testTimeout;

            public ConnectionThread(ConnectionManager connectionManager,
                                    PeerProperties peerProperties,
                                    long timeout) {
                testConnectionManager = connectionManager;
                testPeerProperties = peerProperties;
                testTimeout = timeout;
            }

            public void run() {
                Looper.prepare();
                new CountDownTimer(testTimeout + 3000, testTimeout + 3000) {
                    public void onTick(long millisUntilFinished) {
                        // not used
                    }
                    public void onFinish() {
                        Looper.myLooper().quitSafely();
                    }
                }.start();
                // connection initialization starts
                assertThat(testConnectionManager.connect(testPeerProperties), is(true));
                Looper.loop();
            }
        }

        peerProperties = new PeerProperties("02:00:00:00:00:00");
        long timeout = 3000;
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mContext);
        cmSettings.setConnectionTimeout(timeout);
        mConnectionManager = new ConnectionManager(mContext, mConnectionManagerListener,
                                                   UUID.randomUUID(), "MOCK_NAME");
        (new ConnectionThread(
                mConnectionManager,
                peerProperties,
                timeout)).start();

        // let's wait for connection timeout (additional time to ensure no other handler is called)
        Thread.sleep(timeout + 10000);
        verify(mConnectionManagerListener, times(1)).onConnectionTimeout(peerProperties);
        verifyNoMoreInteractions(mConnectionManagerListener);
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

    @Test
    public void testGetBluetoothMacAddress() throws Exception {
        // read device mac address
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(mContext);
        mConnectionManager.setEmulateMarshmallow(false);
        String btAddress;
        if (!CommonUtils.isMarshmallowOrHigher()) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            btAddress = btAdapter.getAddress();
        } else {
            btAddress = android.provider.Settings.Secure.getString(
                    mContext.getContentResolver(), "bluetooth_address");
        }
        assertThat(mConnectionManager.getBluetoothMacAddress(), is(btAddress));
        assertThat(settings.getBluetoothMacAddress(), is(btAddress));

        // now emulate marshmallow (use BT address stored in settings)
        mConnectionManager.setEmulateMarshmallow(true);
        btAddress = "AA:BB:CC:DD:EE:FF";
        settings.setBluetoothMacAddress(btAddress);
        assertThat(mConnectionManager.getBluetoothMacAddress(), is(btAddress));

        // now let's check if returned mac address is verified
        Field macAddressField = settings.getClass()
                .getDeclaredField("mBluetoothMacAddress");
        macAddressField.setAccessible(true);
        macAddressField.set(settings, "WRONG_MAC");
        assertThat(mConnectionManager.getBluetoothMacAddress(), is(nullValue()));
    }

    @Test
    public void testGetPropertiesFromIdentityString() throws Exception {
        PeerProperties peerProperties = new PeerProperties();
        String jsonString = "";

        // wrong JSON
        thrown.expect(org.json.JSONException.class);
        ConnectionManager.getPropertiesFromIdentityString(jsonString, peerProperties);

        // empty name
        jsonString = "{\"name\":\"\",\"address\":\"DUMMY_MAC\"}";
        assertThat(ConnectionManager.getPropertiesFromIdentityString(jsonString, peerProperties), is(false));
        assertThat(peerProperties.getName(), is(""));
        assertThat(peerProperties.getBluetoothMacAddress(), is("DUMMY_MAC"));

        // empty mac address
        jsonString = "{\"name\":\"DUMMY_NAME\",\"address\":\"\"}";
        assertThat(ConnectionManager.getPropertiesFromIdentityString(jsonString, peerProperties), is(false));
        assertThat(peerProperties.getName(), is("DUMMY_NAME"));
        assertThat(peerProperties.getBluetoothMacAddress(), is(""));

        // mac address and name set
        jsonString = "{\"name\":\"DUMMY_NAME\",\"address\":\"DUMMY_MAC\"}";
        assertThat(ConnectionManager.getPropertiesFromIdentityString(jsonString, peerProperties), is(true));
        assertThat(peerProperties.getName(), is("DUMMY_NAME"));
        assertThat(peerProperties.getBluetoothMacAddress(), is("DUMMY_MAC"));
    }

    @Test
    public void testVerifyIdentityString() throws Exception {
        Field identityField = mConnectionManager.getClass().getSuperclass()
                .getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);

        // peer name is empty string
        mConnectionManager.setPeerName("");
        assertThat(identityField.get(mConnectionManager), is(nullValue()));

        // peer name is set to reserved key
        mConnectionManager.setPeerName(PeerProperties.NO_PEER_NAME_STRING);
        assertThat(identityField.get(mConnectionManager), is(nullValue()));

        // peer name is set but bluetooth address is not valid
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(mContext);
        mConnectionManager.setEmulateMarshmallow(true);
        Field macAddressField = settings.getClass()
                .getDeclaredField("mBluetoothMacAddress");
        macAddressField.setAccessible(true);
        macAddressField.set(settings, "WRONG_MAC");
        mConnectionManager.setPeerName("DUMMY_NAME");
        assertThat(identityField.get(mConnectionManager), is(nullValue()));

        // correct peer name and bluetooth address
        macAddressField.set(settings, "AA:BB:CC:DD:EE:FF");
        mConnectionManager.setPeerName("OTHER_DUMMY_NAME");
        assertThat((String) identityField.get(mConnectionManager),
                   is("{\"name\":\"OTHER_DUMMY_NAME\",\"address\":\"AA:BB:CC:DD:EE:FF\"}"));
    }

    @Test
    public void testClearIdentityString() throws Exception {
        Field identityField = mConnectionManager.getClass().getSuperclass()
                .getDeclaredField("mMyIdentityString");
        identityField.setAccessible(true);

        mConnectionManager.setPeerName("DUMMY_NAME");
        assertThat(identityField.get(mConnectionManager), is(notNullValue()));
        mConnectionManager.clearIdentityString();
        assertThat(identityField.get(mConnectionManager), is(nullValue()));
    }

    @Test
    public void testTogglingBluetoothDuringWork() throws Exception {
        mConnectionManager.startListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);

        toggleBluetooth(false);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        toggleBluetooth(true);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);

        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        toggleBluetooth(false);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        mConnectionManager.startListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        toggleBluetooth(true);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);

        toggleBluetooth(false);
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);
    }

    @Test
    public void testTogglingBluetoothDuringStopping() throws Exception {
        Field bluetoothConnectorField = mConnectionManager.getClass()
                .getDeclaredField("mBluetoothConnector");
        bluetoothConnectorField.setAccessible(true);
        BluetoothConnector bluetoothConnector = (BluetoothConnector) bluetoothConnectorField
                .get(mConnectionManager);

        mConnectionManager.startListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.RUNNING);

        BluetoothConnector bluetoothConnectorSpy = spy(bluetoothConnector);
        bluetoothConnectorField.set(mConnectionManager, bluetoothConnectorSpy);
        doAnswer(new Answer<Void>() {
            private boolean called = false;
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                if (!called) {
                    called = true;
                    toggleBluetooth(false);
                    Thread.sleep(10000);
                }
                invocation.callRealMethod();
                return null;
            }
        }).when(bluetoothConnectorSpy).stopListeningForIncomingConnections();

        mConnectionManager.stopListeningForIncomingConnections();
        checkStateWithTimeout(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        bluetoothConnectorField.set(mConnectionManager, bluetoothConnector);
    }
}