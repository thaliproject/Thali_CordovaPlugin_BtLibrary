package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
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

    ConnectionManagerSettings mConnectionManagerSettings;

    ConnectionManager mListener;

    static Map<String, Object> mSharedPreferencesMap;
    static int apllyCnt;
    static int commitCnt;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSharedPreferencesMap = new HashMap<> ();
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
                return null;
            }

            @Override
            public SharedPreferences.Editor putLong(String key, long value) {

                mSharedPreferencesMap.put(key, value);

                return null;
            }

            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                return null;
            }

            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
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
                commitCnt++;
                return false;
            }

            @Override
            public void apply() {
                apllyCnt++;

            }
        });

        mListener = new ConnectionManager(mMockContext,
                new ConnectionManager.ConnectionManagerListener() {
                    @Override
                    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState state) {
                    }
                    @Override
                    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
                    }
                    @Override
                    public void onConnectionTimeout(PeerProperties peerProperties) {
                    }
                    @Override
                    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
                    }
                },
                new UUID(1,1), "test Name", mMockBluetoothManager,
                mMockSharedPreferences);

        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

    }

    @Test
    public void testGetInstance() throws Exception {
        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        assertThat(mConnectionManagerSettings, is(notNullValue()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddListener() throws Exception {

        mConnectionManagerSettings.addListener(mListener);
    }

    @Test
    public void testRemoveListener() throws Exception {
        ConnectionManager listener = new ConnectionManager(mMockContext,
                new ConnectionManager.ConnectionManagerListener() {
                    @Override
                    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState state) {

                    }

                    @Override
                    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {

                    }

                    @Override
                    public void onConnectionTimeout(PeerProperties peerProperties) {

                    }

                    @Override
                    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {

                    }
                }
                ,
                new UUID(1,1), "test Name", mMockBluetoothManager,
                mMockSharedPreferences);

        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

        mConnectionManagerSettings.removeListener(listener);
        mConnectionManagerSettings.addListener(listener);
    }

    @Test
    public void testConnectionTimeout() throws Exception {

        assertThat(mConnectionManagerSettings.getConnectionTimeout(), is(equalTo(0L)));
        mConnectionManagerSettings.setConnectionTimeout(100L);
        assertThat(mConnectionManagerSettings.getConnectionTimeout(), is(equalTo(100L)));
        assertThat((Long) mSharedPreferencesMap.get("connection_timeout"), is(equalTo(100L)));
        assertThat("Apply count is incremented", apllyCnt, is(equalTo(2)));
        mConnectionManagerSettings.setConnectionTimeout(100L);
        assertThat("The timeout should not change", mConnectionManagerSettings.getConnectionTimeout(), is(equalTo(100L)));
        assertThat("Apply count should bit be incremented", apllyCnt, is(equalTo(2)));
    }

}