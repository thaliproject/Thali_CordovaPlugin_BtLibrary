package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConnectionManagerSettingsTest {

    @Mock
    Context mMockContext;

    @Mock
    SharedPreferences mMockSharedPreferences;

    @Mock
    BluetoothManager mMockBluetoothManager;

    @Mock
    BluetoothAdapter mMockBluetoothAdapter;

    private ConnectionManagerSettings mConnectionManagerSettings;

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

        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

    }

    @After
    public void tearDown() throws Exception {
        // the code below is needed to reset the ConnectionManagerSettings singleton
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        Field stateField = cmSettings.getClass().getDeclaredField("mInstance");
        stateField.setAccessible(true);
        stateField.set(cmSettings, null);
    }

    @Test
    public void testGetInstance() throws Exception {
        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        assertThat(cmSettings, is(notNullValue()));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testListener() throws Exception {
        ConnectionManager listener = new ConnectionManager(mMockContext,
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
                }
                ,
                new UUID(1, 1), "test Name", mMockBluetoothManager,
                mMockSharedPreferences);

        ConnectionManagerSettings cmSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

        cmSettings.removeListener(listener);
        cmSettings.addListener(listener);
        thrown.expect(IllegalArgumentException.class);
        cmSettings.addListener(listener);
    }

    @Test
    public void testConnectionTimeout() throws Exception {

        assertThat("Default connection timeout is set", mConnectionManagerSettings.getConnectionTimeout(),
                is(equalTo(ConnectionManagerSettings.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS)));
        mConnectionManagerSettings.setConnectionTimeout(100L);
        assertThat(mConnectionManagerSettings.getConnectionTimeout(), is(equalTo(100L)));
        assertThat((Long) mSharedPreferencesMap.get("connection_timeout"), is(equalTo(100L)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));
        mConnectionManagerSettings.setConnectionTimeout(100L);
        assertThat("The timeout should not change",
                mConnectionManagerSettings.getConnectionTimeout(), is(equalTo(100L)));
        assertThat("Apply count should bit be incremented", applyCnt, is(equalTo(1)));
    }

    @Test
    public void testInsecureRfcommSocketPortNumber() throws Exception {
        // default value
        assertThat("The default Rotating port number is properly set",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(),
                is(equalTo(ConnectionManagerSettings.DEFAULT_ALTERNATIVE_INSECURE_RFCOMM_SOCKET_PORT)));

        // the lower limit value
        assertThat("Set port number -1 is possible ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(-1), is(true));
        assertThat("Port number is properly set",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(-1)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(-1)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        // the recommended value
        assertThat("Set port number 1 is possible ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(1), is(true));
        assertThat("Port number is properly set",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(1)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(1)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(2)));

        // the upper limit value
        assertThat("Set port number 30 is possible ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(30), is(true));
        assertThat("Port number is properly set",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(30)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(30)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(3)));

        // the repeated value
        assertThat("Set the same port number is not possible ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(30), is(false));
        assertThat("Port number is properly set",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(30)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(30)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(3)));

        // the value below the limit
        assertThat("Set port number below the limit value is not possible ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(-2), is(false));
        assertThat("Port number is not changed",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(30)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(30)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(3)));

        // the value above the limit
        assertThat("Set port number above the limit value is not possible  ",
                mConnectionManagerSettings.setInsecureRfcommSocketPortNumber(31), is(false));
        assertThat("Port number is not changed",
                mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(), is(equalTo(30)));
        assertThat((Integer) mSharedPreferencesMap.get("port_number"), is(equalTo(30)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(3)));
    }

    @Test
    public void testMaxNumberOfConnectionAttemptRetries() throws Exception {
        // default value
        assertThat("The default max number of connection attempt retries number is set",
                mConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries(),
                is(equalTo(ConnectionManagerSettings.DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES)));

        // set Max number of connection attempt retries number
        mConnectionManagerSettings.setMaxNumberOfConnectionAttemptRetries(1);
        assertThat("Max number of connection attempt retries number",
                mConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries(), is(equalTo(1)));
        assertThat((Integer) mSharedPreferencesMap.get("max_number_of_connection_attempt_retries"),
                is(equalTo(1)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        // set Max number of connection attempt retries number second time
        mConnectionManagerSettings.setMaxNumberOfConnectionAttemptRetries(1);
        assertThat("Set the same value is not possible",
                mConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries(), is(equalTo(1)));
        assertThat((Integer) mSharedPreferencesMap.get("max_number_of_connection_attempt_retries"),
                is(equalTo(1)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
    }

    @Test
    public void testHandshakeRequired() throws Exception {
        // default value
        assertThat("The default value of require a handshake protocol is set",
                mConnectionManagerSettings.getHandshakeRequired(),
                is(ConnectionManagerSettings.DEFAULT_HANDSHAKE_REQUIRED));

        // set require a handshake protocol to false
        mConnectionManagerSettings.setHandshakeRequired(false);
        assertThat("Require a handshake protocol is properly set (false)",
                mConnectionManagerSettings.getHandshakeRequired(), is(false));
        assertThat((Boolean) mSharedPreferencesMap.get("require_handshake"),
                is(false));

        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        // set require a handshake protocol to true
        mConnectionManagerSettings.setHandshakeRequired(true);

        assertThat("Require a handshake protocol is properly set (true)",
                mConnectionManagerSettings.getHandshakeRequired(), is(true));
        assertThat((Boolean) mSharedPreferencesMap.get("require_handshake"),
                is(true));

        assertThat("Apply count is incremented", applyCnt, is(equalTo(2)));

        // set require a handshake protocol second time
        mConnectionManagerSettings.setHandshakeRequired(true);
        assertThat("Set the same value is not possible",
                mConnectionManagerSettings.getHandshakeRequired(), is(true));
        assertThat((Boolean) mSharedPreferencesMap.get("require_handshake"),
                is(true));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(2)));
    }

    @Test
    public void testLoad() throws Exception {

        mConnectionManagerSettings.load();

        verify(mMockSharedPreferences, atLeast(1))
                .getBoolean(contains("require_handshake"), anyBoolean());
        verify(mMockSharedPreferences, atLeast(1))
                .getLong(contains("connection_timeout"), anyInt());
        verify(mMockSharedPreferences, atLeast(1))
                .getInt(contains("port_number"), anyInt());
        verify(mMockSharedPreferences, atLeast(1))
                .getInt(contains("max_number_of_connection_attempt_retries"), anyInt());
    }

    @Test
    public void testResetDefaults() throws Exception {

        mConnectionManagerSettings.resetDefaults();

        assertThat("Default connection timeout", mConnectionManagerSettings.getConnectionTimeout(),
                is(equalTo(BluetoothConnector.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS)));

        assertThat("Default port number", mConnectionManagerSettings.getInsecureRfcommSocketPortNumber(),
                is(equalTo(BluetoothConnector.SYSTEM_DECIDED_INSECURE_RFCOMM_SOCKET_PORT)));

        assertThat("Default max number of connection attempt retries number",
                mConnectionManagerSettings.getMaxNumberOfConnectionAttemptRetries(),
                is(equalTo(ConnectionManagerSettings.DEFAULT_MAX_NUMBER_OF_CONNECTION_ATTEMPT_RETRIES)));

        assertThat("Require a handshake protocol is properly set to default",
                mConnectionManagerSettings.getHandshakeRequired(),
                is(BluetoothConnector.DEFAULT_HANDSHAKE_REQUIRED));

    }
}