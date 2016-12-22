package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectionManagerTest {

    @Mock
    Context mMockContext;
    @Mock
    SharedPreferences mMockSharedPreferences;
    @Mock
    BluetoothManager mMockBluetoothManager;
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    SharedPreferences.Editor mMockEditor;
    @Mock
    BluetoothDevice mBluetoothDevice;
    @Mock
    PeerProperties mMockPeerProperties;
    @Mock
    BluetoothConnector mMockBluetoothConnector;
    @Mock
    ConnectionManagerSettings mMockConnectionManagerSettings;
    @Mock
    ConnectionManager.ConnectionManagerListener mMockManagerListener;
    @Mock
    BluetoothDevice mMockBluetoothDevice;
    @Mock
    Handler mHander;
    @Mock
    BluetoothSocket bluetoothSocket;

    ConnectionManager connectionManager;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06");
        when(mMockBluetoothManager.getBluetoothAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockSharedPreferences.edit()).thenReturn(mMockEditor);

        connectionManager = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1, 1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(connectionManager, is(notNullValue()));

        Field field = connectionManager.getClass().getDeclaredField("mBluetoothConnector");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(connectionManager, mMockBluetoothConnector);
    }

    @After
    public void tearDown() throws Exception {
        connectionManager.dispose();
        resetSettings();
    }

    private void resetSettings() throws NoSuchFieldException, IllegalAccessException {
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        Field stateField = cmSettings.getClass().getDeclaredField("mInstance");
        stateField.setAccessible(true);
        stateField.set(cmSettings, null);
    }

    @Test
    public void testConstructorThatTakesContextAndPrefs() throws Exception {
        ConnectionManager cm = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1, 1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));
        assertThat(cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));
    }

    @Test
    public void testGetState() throws Exception {

        //default state
        assertThat("Has a proper default state", connectionManager.getState(),
                is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));

        Field stateField = connectionManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        assertThat("Has a proper state when waiting for bluetooth", connectionManager.getState(),
                is(equalTo(ConnectionManager.ConnectionManagerState
                        .WAITING_FOR_SERVICES_TO_BE_ENABLED)));
        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.RUNNING);
        assertThat("Has a proper state when running", connectionManager.getState(),
                is(equalTo(ConnectionManager.ConnectionManagerState.RUNNING)));
    }

    @Test
    public void testStartListeningForIncomingConnections() throws Exception {
        final ConnectionManager cm = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1, 1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));

        when(mMockPeerProperties.toString()).thenReturn("mocked connection");
        when(mMockPeerProperties.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06 ");
        when(mMockBluetoothManager.getRemoteDevice(anyString())).thenReturn(mBluetoothDevice);
        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class),
                isA(PeerProperties.class))).thenReturn(true);
        Field field = cm.getClass().getDeclaredField("mBluetoothConnector");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(cm, mMockBluetoothConnector);

        //Bluetooth is not supported
        when(mMockBluetoothManager.bind(
                isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(false);

        assertThat("should not start when failed to start bluetooth manager",
                cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is not supported",
                cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));

        //Bluetooth is supported and disabled
        when(mMockBluetoothManager.bind(
                isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(true);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);

        assertThat("should return false when Bluetooth is disabled",
                cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is disabled", cm.getState(),
                is(equalTo(ConnectionManager.ConnectionManagerState
                        .WAITING_FOR_SERVICES_TO_BE_ENABLED)));

        //Bluetooth is supported and enabled but failed to start bluetooth connector
        when(mMockBluetoothManager.bind(
                isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(true);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothConnector.startListeningForIncomingConnections()).thenReturn(false);

        assertThat("should return false failed to start bluetooth connector",
                cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is disabled",
                cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState
                        .WAITING_FOR_SERVICES_TO_BE_ENABLED)));

        //Bluetooth is supported and enabled and started
        when(mMockBluetoothConnector.startListeningForIncomingConnections())
                .thenAnswer(new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        //simulate onIsServerStartedChanged event
                        cm.onIsServerStartedChanged(true);
                        return true;
                    }
                });

        assertThat("should return true if running",
                cm.startListeningForIncomingConnections(), is(true));
        assertThat("should return proper state when running",
                cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.RUNNING)));

        verify(mMockBluetoothManager, atMost(4))
                .bind(isA(BluetoothManager.BluetoothManagerListener.class));

        //Bluetooth is supported and enabled and already started
        when(mMockBluetoothConnector.startListeningForIncomingConnections()).thenReturn(true);

        assertThat("should return true if running",
                cm.startListeningForIncomingConnections(), is(true));
        assertThat("should return proper state when running",
                cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.RUNNING)));

        verify(mMockBluetoothManager, atMost(4))
                .bind(isA(BluetoothManager.BluetoothManagerListener.class));
    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {

        assertThat(connectionManager, is(notNullValue()));

        connectionManager.stopListeningForIncomingConnections();
        verify(mMockBluetoothConnector, atLeastOnce())
                .stopListeningForIncomingConnections();
        verify(mMockBluetoothManager, atLeastOnce())
                .release(isA(BluetoothManager.BluetoothManagerListener.class));

        //checking if the state is changed manually when waiting for Bluetooth
        Field stateField = connectionManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);
        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState
                .WAITING_FOR_SERVICES_TO_BE_ENABLED);
        connectionManager.stopListeningForIncomingConnections();
        verify(mMockBluetoothConnector, atLeastOnce())
                .stopListeningForIncomingConnections();
        verify(mMockBluetoothManager, atLeastOnce())
                .release(isA(BluetoothManager.BluetoothManagerListener.class));
        assertThat("when waiting for Bluetooth the state is changed manually",
                connectionManager.getState(), is(equalTo(ConnectionManager.ConnectionManagerState
                        .NOT_STARTED)));
    }

    @Test
    public void testConnect() throws Exception {

        when(mMockBluetoothManager.getRemoteDevice(anyString())).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothConnector.connect(
                isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        assertThat("Returns true if connected",
                connectionManager.connect(mMockPeerProperties), is(true));
        when(mMockBluetoothConnector.connect(
                isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(false);
        assertThat("Returns false if cannot connect",
                connectionManager.connect(mMockPeerProperties), is(false));

        assertThat("Returns false if the peer to connect to is null",
                connectionManager.connect(null), is(false));
    }

    @Test
    public void testCancelConnectionAttempt() throws Exception {

        when(mMockBluetoothConnector.cancelConnectionAttempt(
                isA(PeerProperties.class))).thenReturn(true);
        assertThat("Returns true if cancelled successfully",
                connectionManager.cancelConnectionAttempt(mMockPeerProperties), is(true));
        when(mMockBluetoothConnector.cancelConnectionAttempt(
                isA(PeerProperties.class))).thenReturn(false);
        assertThat("Returns false if cancel failed",
                connectionManager.cancelConnectionAttempt(mMockPeerProperties), is(false));
    }

    @Test
    public void testCancelAllConnectionAttempts() throws Exception {

        connectionManager.cancelAllConnectionAttempts();
        verify(mMockBluetoothConnector, atLeastOnce())
                .cancelAllConnectionAttempts();
    }

    @Test
    public void testDispose() throws Exception {

        Field settingsField = connectionManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(connectionManager, mMockConnectionManagerSettings);

        assertThat(connectionManager, is(notNullValue()));

        connectionManager.dispose();
        verify(mMockBluetoothConnector, atLeastOnce())
                .stopListeningForIncomingConnections();
        verify(mMockBluetoothManager, atLeastOnce())
                .release(isA(BluetoothManager.BluetoothManagerListener.class));
        verify(mMockBluetoothConnector, atLeastOnce())
                .shutdown();
        verify(mMockConnectionManagerSettings, atLeastOnce())
                .removeListener(isA(ConnectionManager.class));
    }

    @Test
    public void testOnConnectionManagerSettingsChanged() throws Exception {

        Field settingsField = connectionManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(connectionManager, mMockConnectionManagerSettings);

        when(mMockConnectionManagerSettings.getConnectionTimeout()).thenReturn(100L);
        when(mMockConnectionManagerSettings.getInsecureRfcommSocketPortNumber()).thenReturn(1);
        when(mMockConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries()).thenReturn(10);

        connectionManager.onConnectionManagerSettingsChanged();
        verify(mMockBluetoothConnector, atLeastOnce())
                .setConnectionTimeout(100L);

        verify(mMockBluetoothConnector, atLeastOnce())
                .setInsecureRfcommSocketPort(1);

        verify(mMockBluetoothConnector, atLeastOnce())
                .setMaxNumberOfOutgoingConnectionAttemptRetries(10);
    }

    @Test
    public void testOnHandshakeRequiredSettingChanged() throws Exception {
        Field settingsField = connectionManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(connectionManager, mMockConnectionManagerSettings);

        when(mMockConnectionManagerSettings.getHandshakeRequired()).thenReturn(true);

        connectionManager.onHandshakeRequiredSettingChanged(true);
        verify(mMockBluetoothConnector, atLeastOnce())
                .setHandshakeRequired(true);

        when(mMockConnectionManagerSettings.getHandshakeRequired()).thenReturn(false);

        connectionManager.onHandshakeRequiredSettingChanged(true);
        verify(mMockBluetoothConnector, atLeastOnce())
                .setHandshakeRequired(false);
    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged() throws Exception {

        Field stateField = connectionManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);
        Field shouldBeStartedField = connectionManager.getClass().getDeclaredField("mShouldBeStarted");
        shouldBeStartedField.setAccessible(true);
        shouldBeStartedField.setBoolean(connectionManager, true);

        // connection paused
        int mode = BluetoothAdapter.SCAN_MODE_NONE;
        int state = BluetoothAdapter.STATE_OFF;

        // default state - not started
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        connectionManager.onBluetoothAdapterScanModeChanged(mode);
        verify(mMockBluetoothConnector, times(1)).stopListeningForIncomingConnections();
        assertThat(connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);
        connectionManager.onBluetoothAdapterStateChanged(state);
        assertThat(connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // running
        reset(mMockBluetoothConnector);
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.RUNNING);

        connectionManager.onBluetoothAdapterScanModeChanged(mode);
        // connection paused
        verify(mMockBluetoothConnector, times(1)).stopListeningForIncomingConnections();
        assertThat(connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // waiting for bluetooth
        reset(mMockBluetoothConnector);
        connectionManager.onBluetoothAdapterScanModeChanged(mode);
        // connection paused
        verify(mMockBluetoothConnector, never()).stopListeningForIncomingConnections();
        assertThat(connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // connection enabled
        mode = BluetoothAdapter.SCAN_MODE_CONNECTABLE;
        // state - not started
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.NOT_STARTED);

        ConnectionManager connectionManagerSpy = spy(connectionManager);
        connectionManagerSpy.onBluetoothAdapterScanModeChanged(mode);

        verify(connectionManagerSpy, never()).startListeningForIncomingConnections();

        // state - running, bt not enabled
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.RUNNING);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);
        connectionManagerSpy = spy(connectionManager);
        connectionManagerSpy.onBluetoothAdapterScanModeChanged(mode);

        verify(connectionManagerSpy, never()).startListeningForIncomingConnections();

        // state - running, bt enabled
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.RUNNING);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        connectionManagerSpy = spy(connectionManager);
        connectionManagerSpy.onBluetoothAdapterScanModeChanged(mode);

        verify(connectionManagerSpy, never()).startListeningForIncomingConnections();


        // state - waiting, bt disabled
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);
        connectionManagerSpy = spy(connectionManager);
        connectionManagerSpy.onBluetoothAdapterScanModeChanged(mode);

        verify(connectionManagerSpy, never()).startListeningForIncomingConnections();

        // state - waiting, bt enabled
        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        connectionManagerSpy = spy(connectionManager);
        connectionManagerSpy.onBluetoothAdapterScanModeChanged(mode);

        verify(connectionManagerSpy, times(1)).startListeningForIncomingConnections();
    }

    @Test
    public void testOnIsServerStartedChanged() throws Exception {

        //checking if the state is changed  when waiting for Bluetooth
        Field stateField = connectionManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);
        Field shouldBeStartedField = connectionManager.getClass().getDeclaredField("mShouldBeStarted");
        shouldBeStartedField.setAccessible(true);
        shouldBeStartedField.setBoolean(connectionManager, true);

        connectionManager.onIsServerStartedChanged(true);
        assertThat(connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.RUNNING));

        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        connectionManager.onIsServerStartedChanged(true);
        assertThat("state changed to running if waiting for Bluetooth to be enabled",
                connectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));

        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.NOT_STARTED);
        connectionManager.onIsServerStartedChanged(true);
        assertThat("state changed to started if not started",
                connectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));

        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.RUNNING);
        connectionManager.onIsServerStartedChanged(true);
        assertThat("state remains running if running",
                connectionManager.getState(), is(ConnectionManager.ConnectionManagerState.RUNNING));

        stateField.set(connectionManager,
                ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        connectionManager.onIsServerStartedChanged(false);
        assertThat("state not changed if waiting for Bluetooth to be enabled", connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.RUNNING);
        connectionManager.onIsServerStartedChanged(false);
        assertThat("state changed if running", connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.RUNNING);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        connectionManager.onIsServerStartedChanged(false);
        assertThat("state changed if running", connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));

        stateField.set(connectionManager, ConnectionManager.ConnectionManagerState.NOT_STARTED);
        connectionManager.onIsServerStartedChanged(false);
        assertThat("state still not started if not started", connectionManager.getState(),
                is(ConnectionManager.ConnectionManagerState.NOT_STARTED));
    }

    @Test
    public void testOnConnected() throws Exception {
        Field handlerField = connectionManager.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(connectionManager, mHander);

        connectionManager.onConnected(bluetoothSocket, true, mMockPeerProperties);

        verify(mHander, atLeastOnce())
                .post(isA(Runnable.class));
    }

    @Test
    public void testOnConnectionTimeout() throws Exception {
        Field handlerField = connectionManager.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(connectionManager, mHander);

        connectionManager.onConnectionTimeout(mMockPeerProperties);

        verify(mHander, atLeastOnce())
                .post(isA(Runnable.class));
    }

    @Test
    public void testOnConnectionFailed() throws Exception {
        Field handlerField = connectionManager.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(connectionManager, mHander);

        connectionManager.onConnectionFailed(mMockPeerProperties, "message");

        verify(mHander, atLeastOnce())
                .post(isA(Runnable.class));
    }

    @Test
    public void testUpdateState() throws Exception {

        class UpdateStateChecker {

            Field mShouldBeStartedField;
            Field mIsServerStartedField;
            Method updateStateMethod;

            UpdateStateChecker() throws Exception {
                mShouldBeStartedField = connectionManager.getClass().getDeclaredField("mShouldBeStarted");
                mShouldBeStartedField.setAccessible(true);
                mIsServerStartedField = connectionManager.getClass().getDeclaredField("mIsServerStarted");
                mIsServerStartedField.setAccessible(true);
                updateStateMethod = connectionManager.getClass().getDeclaredMethod("updateState");
                updateStateMethod.setAccessible(true);
            }

            void prepareCheck(boolean bluetoothEnabled, boolean shouldBeStarted, boolean isStarted) throws Exception {
                when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(bluetoothEnabled);
                mShouldBeStartedField.setBoolean(connectionManager, shouldBeStarted);
                mIsServerStartedField.setBoolean(connectionManager, isStarted);
            }

            void check(ConnectionManager.ConnectionManagerState expectedState) throws Exception {
                updateStateMethod.invoke(connectionManager);
                assertThat(connectionManager.getState(), is(expectedState));
            }
        }

        UpdateStateChecker checker = new UpdateStateChecker();

        // bluetooth disabled, not started, not running
        checker.prepareCheck(false, false, false);
        checker.check(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        // bluetooth disabled, started, not running
        checker.prepareCheck(false, true, false);
        checker.check(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);

        // bluetooth enabled, not started, not running
        checker.prepareCheck(true, false, false);
        checker.check(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        // bluetooth enabled, started, not running
        checker.prepareCheck(true, true, false);
        checker.check(ConnectionManager.ConnectionManagerState.NOT_STARTED);

        // bluetooth enabled, started, running
        checker.prepareCheck(true, true, true);
        checker.check(ConnectionManager.ConnectionManagerState.RUNNING);
    }
}