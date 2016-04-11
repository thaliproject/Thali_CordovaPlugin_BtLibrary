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
                mSharedPreferencesMap.put(key, value);
                return null;
            }

            @Override
            public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
                mSharedPreferencesMap.put(key, values);
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

        assertThat("The default value is set properly",
                mDiscoveryManagerSettings.getAutomateBluetoothMacAddressResolution(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION)));

        mDiscoveryManagerSettings.setAutomateBluetoothMacAddressResolution(false);

        assertThat("The value of the AutomateBluetoothMacAddressResolution is updated",
                mDiscoveryManagerSettings.getAutomateBluetoothMacAddressResolution(), is(equalTo(false)));
        assertThat((Boolean) mSharedPreferencesMap.get("automate_bluetooth_mac_address_resolution"), is(equalTo(false)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

    }

    @Test
    public void testProvideBluetoothMacAddressTimeout() throws Exception {


        assertThat("Default provide BT MAC address timeout is properly set",
                mDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS)));
        mDiscoveryManagerSettings.setProvideBluetoothMacAddressTimeout(100L);
        assertThat(mDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout(), is(equalTo(100L)));
        assertThat((Long) mSharedPreferencesMap.get("provide_bluetooth_mac_address_timeout"), is(equalTo(100L)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));
        mDiscoveryManagerSettings.setProvideBluetoothMacAddressTimeout(100L);
        assertThat("The timeout should not change",
                mDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout(), is(equalTo(100L)));
        assertThat("Apply count should not be incremented", applyCnt, is(equalTo(1)));

    }

    @Test
    public void testBluetoothMacAddress() throws Exception {
        String btAddr = "01:02:03:04:05:06";
        String btAddr2 = "01:02:03:04:05:07";
        String wrongAddr = "00:01:02:03:04";

        assertThat("Default BT MAC address is properly set",
                mDiscoveryManagerSettings.getBluetoothMacAddress(),
                is(equalTo(null)));

        // set wrong value
        mDiscoveryManagerSettings.setBluetoothMacAddress(wrongAddr);
        assertThat("The address is not updated with wrong value",
                mDiscoveryManagerSettings.getBluetoothMacAddress(), is(equalTo(null)));
        assertThat(mSharedPreferencesMap.get("bluetooth_mac_address"), is(equalTo(null)));
        assertThat("Apply count should be not be incremented", applyCnt, is(equalTo(0)));

        // set proper value
        mDiscoveryManagerSettings.setBluetoothMacAddress(btAddr);
        assertThat("The address is updated with proper value",
                mDiscoveryManagerSettings.getBluetoothMacAddress(), is(equalTo(btAddr)));
        assertThat((String) mSharedPreferencesMap.get("bluetooth_mac_address"), is(equalTo(btAddr)));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(1)));

        // repeat the same value
        mDiscoveryManagerSettings.setBluetoothMacAddress(btAddr);
        assertThat("The address is not updated with the same value",
                mDiscoveryManagerSettings.getBluetoothMacAddress(), is(equalTo(btAddr)));
        assertThat((String) mSharedPreferencesMap.get("bluetooth_mac_address"), is(equalTo(btAddr)));
        assertThat("Apply count should not be incremented", applyCnt, is(equalTo(1)));

        // set proper value
        mDiscoveryManagerSettings.setBluetoothMacAddress(btAddr2);
        assertThat("The address is updated with proper value",
                mDiscoveryManagerSettings.getBluetoothMacAddress(), is(equalTo(btAddr2)));
        assertThat((String) mSharedPreferencesMap.get("bluetooth_mac_address"), is(equalTo(btAddr2)));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(2)));


        mDiscoveryManagerSettings.clearBluetoothMacAddress();
        assertThat("The address is properly cleared",
                mDiscoveryManagerSettings.getBluetoothMacAddress(), is(equalTo(null)));
        assertThat(mSharedPreferencesMap.get("bluetooth_mac_address"), is(equalTo(null)));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(3)));


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