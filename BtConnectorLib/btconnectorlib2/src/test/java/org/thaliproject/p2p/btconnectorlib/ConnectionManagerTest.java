package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

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
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atMost;
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
    BluetoothDevice mBluetoothDevice;

    @Mock
    PeerProperties mMockPeerProperties;

    @Mock
    BluetoothConnector mMockBluetoothConnector;

    @Mock
    ConnectionManager.ConnectionManagerListener mMockManagerListener;


    private static Map<String, Object> mSharedPreferencesMap;
    private static int applyCnt;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() {
        applyCnt = 0;
        MockitoAnnotations.initMocks(this);
        mSharedPreferencesMap = new HashMap<>();
        when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06");
        when(mMockBluetoothManager.getBluetoothAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockSharedPreferences.edit()).thenReturn(new SharedPreferences.Editor() {
            @Override
            public SharedPreferences.Editor putString(String key, String value) {
                return null;
            }

            @Override
            public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
                return null;
            }

            @Override
            public SharedPreferences.Editor putInt(String key, int value) {
                mSharedPreferencesMap.put(key, value);
                return null;
            }

            @Override
            public SharedPreferences.Editor putLong(String key, long value) {

                mSharedPreferencesMap.put(key, value);

                return null;
            }

            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                mSharedPreferencesMap.put(key, value);
                return null;
            }

            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                mSharedPreferencesMap.put(key, value);
                return null;
            }

            @Override
            public SharedPreferences.Editor remove(String key) {
                return null;
            }

            @Override
            public SharedPreferences.Editor clear() {
                return null;
            }

            @Override
            public boolean commit() {
                return false;
            }

            @Override
            public void apply() {
                applyCnt++;

            }
        });

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testConstructorThatTakesContextAndPrefs() throws Exception {
        ConnectionManager cm = new ConnectionManager(
                mMockContext,
                new ConnectionManager.ConnectionManagerListener() {
                    @Override
                    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState state) {
                    }
                    @Override
                    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming,
                                            PeerProperties peerProperties) {
                    }
                    @Override
                    public void onConnectionTimeout(PeerProperties peerProperties) {
                    }
                    @Override
                    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
                    }
                },
                new UUID(1,1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));
        assertThat(cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));
        //assertThat(pp.getName(), is(nullValue()));
    }

    @Test
    public void testGetState() throws Exception {

        ConnectionManager cm = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1,1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));
        assertThat(cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));

        when(mMockPeerProperties.toString()).thenReturn("mocked connection");
        when(mMockPeerProperties.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06 ");
        when(mMockBluetoothManager.getRemoteDevice(anyString())).thenReturn(mBluetoothDevice);
        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        Field field = cm.getClass().getDeclaredField("mBluetoothConnector");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(cm, mMockBluetoothConnector);

        assertThat(cm.connect(mMockPeerProperties), is(true));

        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        assertThat(cm.connect(mMockPeerProperties), is(true));

    }

    @Test
    public void testStartListeningForIncomingConnections() throws Exception {
        final ConnectionManager cm = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1,1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));

        when(mMockPeerProperties.toString()).thenReturn("mocked connection");
        when(mMockPeerProperties.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06 ");
        when(mMockBluetoothManager.getRemoteDevice(anyString())).thenReturn(mBluetoothDevice);
        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        Field field = cm.getClass().getDeclaredField("mBluetoothConnector");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(cm, mMockBluetoothConnector);

        //Bluetooth is not supported
        when(mMockBluetoothManager.bind(isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(false);

        assertThat("should not start when failed to start bluetooth manager", cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is not supported", cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.NOT_STARTED)));

        //Bluetooth is supported and disabled
        when(mMockBluetoothManager.bind(isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(true);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);

        assertThat("should return false when Bluetooth is disabled", cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is disabled", cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED)));

        //Bluetooth is supported and enabled but failed to start bluetooth connector
        when(mMockBluetoothManager.bind(isA(BluetoothManager.BluetoothManagerListener.class))).thenReturn(true);
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothConnector.startListeningForIncomingConnections()).thenReturn(false);

        assertThat("should return false failed to start bluetooth connector", cm.startListeningForIncomingConnections(), is(false));
        assertThat("when Bluetooth is disabled", cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED)));



        //Bluetooth is supported and enabled and started
        when(mMockBluetoothConnector.startListeningForIncomingConnections()).thenAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                cm.onIsServerStartedChanged(true);
                return true;
            }
        });

        assertThat("should return true if running", cm.startListeningForIncomingConnections(), is(true));
        assertThat("should return proper state when running", cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.RUNNING)));

        verify(mMockBluetoothManager, atMost(4))
                .bind(isA(BluetoothManager.BluetoothManagerListener.class));

        //Bluetooth is supported and enabled and already started
        when(mMockBluetoothConnector.startListeningForIncomingConnections()).thenReturn(true);

        assertThat("should return true if running", cm.startListeningForIncomingConnections(), is(true));
        assertThat("should return proper state when running", cm.getState(), is(equalTo(ConnectionManager.ConnectionManagerState.RUNNING)));

        verify(mMockBluetoothManager, atMost(4))
                .bind(isA(BluetoothManager.BluetoothManagerListener.class));

    }

    @Test
    public void testStopListeningForIncomingConnections() throws Exception {

    }

    @Test
    public void testConnect() throws Exception {

        ConnectionManager cm = new ConnectionManager(
                mMockContext,
                mMockManagerListener,
                new UUID(1,1), "testConnection", mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(cm, is(notNullValue()));

        when(mMockPeerProperties.toString()).thenReturn("mocked connection");
        when(mMockPeerProperties.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06 ");
        when(mMockBluetoothManager.getRemoteDevice(anyString())).thenReturn(mBluetoothDevice);
        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        Field field = cm.getClass().getDeclaredField("mBluetoothConnector");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(cm, mMockBluetoothConnector);

        assertThat(cm.connect(mMockPeerProperties), is(true));

        when(mMockBluetoothConnector.connect(isA(BluetoothDevice.class), isA(PeerProperties.class))).thenReturn(true);
        assertThat(cm.connect(mMockPeerProperties), is(true));

    }

    @Test
    public void testCancelConnectionAttempt() throws Exception {

    }

    @Test
    public void testCancelAllConnectionAttempts() throws Exception {

    }

    @Test
    public void testSetPeerName() throws Exception {

    }

    @Test
    public void testDispose() throws Exception {

    }

    @Test
    public void testOnConnectionManagerSettingsChanged() throws Exception {

    }

    @Test
    public void testOnHandshakeRequiredSettingChanged() throws Exception {

    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged() throws Exception {

    }

    @Test
    public void testOnIsServerStartedChanged() throws Exception {

    }

    @Test
    public void testOnConnecting() throws Exception {

    }

    @Test
    public void testOnConnected() throws Exception {

    }

    @Test
    public void testOnConnectionTimeout() throws Exception {

    }

    @Test
    public void testOnConnectionFailed() throws Exception {

    }
}