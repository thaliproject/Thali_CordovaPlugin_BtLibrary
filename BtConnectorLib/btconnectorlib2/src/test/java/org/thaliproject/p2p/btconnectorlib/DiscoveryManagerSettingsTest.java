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
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryManagerSettingsTest {

    @Mock
    Context mMockContext;

    @Mock
    SharedPreferences mMockSharedPreferences;

    @Mock
    BluetoothManager mMockBluetoothManager;

    @Mock
    BluetoothAdapter mMockBluetoothAdapter;

    private DiscoveryManagerSettings mDiscoveryManagerSettings;

    private static Map<String, Object> mSharedPreferencesMap;
    private static int applyCnt;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() throws Exception {
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

        // the code below is needed to reset the DiscoveryManagerSettings singleton
        mDiscoveryManagerSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        Field stateField = mDiscoveryManagerSettings.getClass().getDeclaredField("mInstance");
        stateField.setAccessible(true);

        stateField.set(mDiscoveryManagerSettings, null);
        mDiscoveryManagerSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
    }

    @After
    public void tearDown() throws Exception {
        // the code below is needed to reset the DiscoveryManagerSettings singleton
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        Field stateField = dmSettings.getClass().getDeclaredField("mInstance");
        stateField.setAccessible(true);
        stateField.set(dmSettings, null);
    }

    @Test
    public void testGetInstance() throws Exception {
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        assertThat(dmSettings, is(notNullValue()));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testListener() throws Exception {
        DiscoveryManager listener = new DiscoveryManager(mMockContext,
                new DiscoveryManager.DiscoveryManagerListener() {
                    @Override
                    public boolean onPermissionCheckRequired(String permission) {
                        return false;
                    }

                    @Override
                    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState state, boolean isDiscovering, boolean isAdvertising) {

                    }

                    @Override
                    public void onPeerDiscovered(PeerProperties peerProperties) {

                    }

                    @Override
                    public void onPeerUpdated(PeerProperties peerProperties) {

                    }

                    @Override
                    public void onPeerLost(PeerProperties peerProperties) {

                    }

                    @Override
                    public void onProvideBluetoothMacAddressRequest(String requestId) {

                    }

                    @Override
                    public void onPeerReadyToProvideBluetoothMacAddress() {

                    }

                    @Override
                    public void onBluetoothMacAddressResolved(String bluetoothMacAddress) {

                    }
                }
                ,
                new UUID(1, 1), "test Name", mMockBluetoothManager,
                mMockSharedPreferences);

        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

        dmSettings.removeListener(listener);
        dmSettings.addListener(listener);
        thrown.expect(IllegalArgumentException.class);
        dmSettings.addListener(listener);
    }

    @Test
    public void testAutomateBluetoothMacAddressResolution() throws Exception {

        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);

        assertThat("The default value is set properly",
                dmSettings.getAutomateBluetoothMacAddressResolution(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION)));

        dmSettings.setAutomateBluetoothMacAddressResolution(false);

        assertThat("The value of the AutomateBluetoothMacAddressResolution is updated",
                dmSettings.getAutomateBluetoothMacAddressResolution(), is(equalTo(false)));
        assertThat((Boolean) mSharedPreferencesMap.get("automate_bluetooth_mac_address_resolution"), is(equalTo(false)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

    }

    @Test
    public void testGetProvideBluetoothMacAddressTimeout() throws Exception {

    }

    @Test
    public void testSetProvideBluetoothMacAddressTimeout() throws Exception {

    }

    @Test
    public void testGetBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testSetBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testClearBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testGetDiscoveryMode() throws Exception {

    }

    @Test
    public void testSetDiscoveryMode() throws Exception {

    }

    @Test
    public void testSetDiscoveryMode1() throws Exception {

    }

    @Test
    public void testGetPeerExpiration() throws Exception {

    }

    @Test
    public void testSetPeerExpiration() throws Exception {

    }

    @Test
    public void testGetAdvertisementDataType() throws Exception {

    }

    @Test
    public void testSetAdvertisementDataType() throws Exception {

    }

    @Test
    public void testGetAdvertiseMode() throws Exception {

    }

    @Test
    public void testSetAdvertiseMode() throws Exception {

    }

    @Test
    public void testGetAdvertiseTxPowerLevel() throws Exception {

    }

    @Test
    public void testSetAdvertiseTxPowerLevel() throws Exception {

    }

    @Test
    public void testGetScanMode() throws Exception {

    }

    @Test
    public void testSetScanMode() throws Exception {

    }

    @Test
    public void testGetScanReportDelay() throws Exception {

    }

    @Test
    public void testSetScanReportDelay() throws Exception {

    }

    @Test
    public void testLoad() throws Exception {

    }

    @Test
    public void testResetDefaults() throws Exception {

    }
}