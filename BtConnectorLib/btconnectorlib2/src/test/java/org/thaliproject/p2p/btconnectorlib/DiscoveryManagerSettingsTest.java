package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryManagerSettingsTest {

    private static Map<String, Object> mSharedPreferencesMap;
    private static int applyCnt;
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    Context mMockContext;
    @Mock
    SharedPreferences mMockSharedPreferences;
    @Mock
    BluetoothManager mMockBluetoothManager;
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    DiscoveryManager mMockDiscoveryManager;
    @Mock
    DiscoveryManager.DiscoveryManagerListener mMockListener;
    private DiscoveryManagerSettings mDiscoveryManagerSettings;

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
        Field field = dmSettings.getClass().getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(dmSettings, null);
    }

    @Test
    public void testGetInstance() throws Exception {
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        assertThat(dmSettings, is(notNullValue()));
    }

    @Test
    public void testListener() throws Exception {
        DiscoveryManager listener = new DiscoveryManager(mMockContext,
                mMockListener,
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
    public void testDiscoveryModeNull() throws Exception {
        assertThat("Default discovery mode is properly set",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_DISCOVERY_MODE)));

        thrown.expect(NullPointerException.class);
        mDiscoveryManagerSettings.setDiscoveryMode(null);

    }

    @Test
    public void testDiscoveryModeBLE() throws Exception {
        Field field = mDiscoveryManagerSettings.getClass().getDeclaredField("mDiscoveryMode");
        field.setAccessible(true);

        // No listeners registered
        assertThat("Should return true if no listeners are registered",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE),
                is(equalTo(true)));

        // change discovery mode to check if it is updated
        field.set(mDiscoveryManagerSettings, DiscoveryManager.DiscoveryMode.WIFI);
        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // Listeners registered
        assertThat("Should return false if isBleMultipleAdvertisementSupported is false",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE),
                is(equalTo(false)));

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);
        assertThat("Should return true if isBleMultipleAdvertisementSupported is true",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE),
                is(equalTo(true)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onDiscoveryModeChanged(DiscoveryManager.DiscoveryMode.BLE, false);

        assertThat("Should return proper discovery mode",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE)));

        assertThat((Integer) mSharedPreferencesMap.get("discovery_mode"),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE.ordinal())));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(1)));

        // The same mode repeated
        assertThat("Should return true if isBleMultipleAdvertisementSupported is true",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE),
                is(equalTo(true)));

        assertThat("Should return proper discovery mode",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE)));

        assertThat((Integer) mSharedPreferencesMap.get("discovery_mode"),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE.ordinal())));
        assertThat("Apply count should not be incremented when mode repeated", applyCnt, is(equalTo(1)));
    }

    @Test
    public void testDiscoveryModeBLE_AND_WIFI() throws Exception {
        Field field = mDiscoveryManagerSettings.getClass().getDeclaredField("mDiscoveryMode");
        field.setAccessible(true);

        // No listeners registered
        assertThat("Should return true if no listeners are registered",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(true)));

        // change discovery mode to check if it is updated
        field.set(mDiscoveryManagerSettings, DiscoveryManagerSettings.DEFAULT_DISCOVERY_MODE);

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // Listeners registered
        assertThat("Should return false if isBleMultipleAdvertisementSupported and isWifiDirectSupported are false",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(false)));

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        // Listeners registered
        assertThat("Should return false if isBleMultipleAdvertisementSupported is false",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(false)));

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(false);
        when(mMockDiscoveryManager.isWifiDirectSupported()).thenReturn(true);
        assertThat("Should return false if isWifiDirectSupported is false",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(false)));

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);
        when(mMockDiscoveryManager.isWifiDirectSupported()).thenReturn(true);
        assertThat("Should return true if isBleMultipleAdvertisementSupported and isWifiDirectSupported are true",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(true)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onDiscoveryModeChanged(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI, false);

        assertThat("Should return proper discovery mode",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI)));

        assertThat((Integer) mSharedPreferencesMap.get("discovery_mode"),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI.ordinal())));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(2)));

        // The same mode repeated
        assertThat("Should return true if isBleMultipleAdvertisementSupported and isWifiDirectSupported are true",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI),
                is(equalTo(true)));

        assertThat("Should return proper discovery mode",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI)));

        assertThat((Integer) mSharedPreferencesMap.get("discovery_mode"),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI.ordinal())));
        assertThat("Apply count should not be incremented when mode repeated", applyCnt, is(equalTo(2)));
    }

    @Test
    public void testDiscoveryModeForceStart() throws Exception {
        Field field = mDiscoveryManagerSettings.getClass().getDeclaredField("mDiscoveryMode");
        field.setAccessible(true);
        // change discovery mode to check if it is updated

        field.set(mDiscoveryManagerSettings, DiscoveryManager.DiscoveryMode.WIFI);
        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        assertThat("Should return true if isBleMultipleAdvertisementSupported is true",
                mDiscoveryManagerSettings.setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE, true),
                is(equalTo(true)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onDiscoveryModeChanged(DiscoveryManager.DiscoveryMode.BLE, true);

        assertThat("Should return proper discovery mode",
                mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE)));

        assertThat((Integer) mSharedPreferencesMap.get("discovery_mode"),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE.ordinal())));
        assertThat("Apply count should be incremented", applyCnt, is(equalTo(1)));

    }

    @Test
    public void testPeerExpiration() throws Exception {
        // default value
        assertThat("The default peer expiration time is set",
                mDiscoveryManagerSettings.getPeerExpiration(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set peer expiration time
        mDiscoveryManagerSettings.setPeerExpiration(1);
        assertThat("The peer expiration time is set properly",
                mDiscoveryManagerSettings.getPeerExpiration(), is(equalTo(1L)));
        assertThat((Long) mSharedPreferencesMap.get("peer_expiration"),
                is(equalTo(1L)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onPeerExpirationSettingChanged(1L);

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setPeerExpiration(1);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getPeerExpiration(), is(equalTo(1L)));
        assertThat((Long) mSharedPreferencesMap.get("peer_expiration"),
                is(equalTo(1L)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onPeerExpirationSettingChanged(anyLong());

    }

    @Test
    public void testAdvertisementDataType() throws Exception {
        // default value
        assertThat("The default advertisement data type is set",
                mDiscoveryManagerSettings.getAdvertisementDataType(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set the advertisement data type
        mDiscoveryManagerSettings.setAdvertisementDataType(BlePeerDiscoverer.AdvertisementDataType.MANUFACTURER_DATA);
        assertThat("The advertisement data type is set properly",
                mDiscoveryManagerSettings.getAdvertisementDataType(),
                is(equalTo(BlePeerDiscoverer.AdvertisementDataType.MANUFACTURER_DATA)));
        // Not the value stored in the shared property is updated by advertisementDataTypeToInt()
        assertThat((Integer) mSharedPreferencesMap.get("advertisement_data_type"),
                is(equalTo(1)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onAdvertiseScanSettingsChanged();

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setAdvertisementDataType(BlePeerDiscoverer.AdvertisementDataType.MANUFACTURER_DATA);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getAdvertisementDataType(),
                is(equalTo(BlePeerDiscoverer.AdvertisementDataType.MANUFACTURER_DATA)));
        assertThat((Integer) mSharedPreferencesMap.get("advertisement_data_type"),
                is(equalTo(1)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onAdvertiseScanSettingsChanged();
    }

    @Test
    public void testAdvertiseMode() throws Exception {
        // default value
        assertThat("The default Bluetooth LE advertise model set",
                mDiscoveryManagerSettings.getAdvertiseMode(),
                is(equalTo(AdvertiseSettings.ADVERTISE_MODE_BALANCED)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set the Bluetooth LE advertise model
        mDiscoveryManagerSettings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        assertThat("The advertise model is set properly",
                mDiscoveryManagerSettings.getAdvertiseMode(),
                is(equalTo(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)));
        assertThat((Integer) mSharedPreferencesMap.get("advertise_mode"),
                is(equalTo(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onAdvertiseScanSettingsChanged();

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getAdvertiseMode(),
                is(equalTo(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)));
        assertThat((Integer) mSharedPreferencesMap.get("advertise_mode"),
                is(equalTo(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onAdvertiseScanSettingsChanged();
    }

    @Test
    public void testAdvertiseTxPowerLevel() throws Exception {
        // default value
        assertThat("The default Bluetooth LE advertise TX power level is set",
                mDiscoveryManagerSettings.getAdvertiseTxPowerLevel(),
                is(equalTo(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set Bluetooth LE advertise TX power level
        mDiscoveryManagerSettings.setAdvertiseTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        assertThat("The Bluetooth LE advertise TX power level is set properly",
                mDiscoveryManagerSettings.getAdvertiseTxPowerLevel(),
                is(equalTo(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)));
        assertThat((Integer) mSharedPreferencesMap.get("advertise_tx_power_level"),
                is(equalTo(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onAdvertiseScanSettingsChanged();

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setAdvertiseTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getAdvertiseTxPowerLevel(),
                is(equalTo(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)));
        assertThat((Integer) mSharedPreferencesMap.get("advertise_tx_power_level"),
                is(equalTo(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onAdvertiseScanSettingsChanged();
    }

    @Test
    public void testGetScanMode() throws Exception {
        //the Bluetooth LE scan mode
        // default value
        assertThat("The default Bluetooth LE scan mode is set",
                mDiscoveryManagerSettings.getScanMode(),
                is(equalTo(ScanSettings.SCAN_MODE_BALANCED)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set scan mode
        mDiscoveryManagerSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        assertThat("The scan mode is set properly",
                mDiscoveryManagerSettings.getScanMode(), is(equalTo(ScanSettings.SCAN_MODE_LOW_LATENCY)));
        assertThat((Integer) mSharedPreferencesMap.get("scan_mode"),
                is(equalTo(ScanSettings.SCAN_MODE_LOW_LATENCY)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onAdvertiseScanSettingsChanged();

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getScanMode(), is(equalTo(ScanSettings.SCAN_MODE_LOW_LATENCY)));
        assertThat((Integer) mSharedPreferencesMap.get("scan_mode"),
                is(equalTo(ScanSettings.SCAN_MODE_LOW_LATENCY)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onAdvertiseScanSettingsChanged();
    }

    @Test
    public void testScanReportDelay() throws Exception {
        // default value
        assertThat("The default scan report delay is set",
                mDiscoveryManagerSettings.getScanReportDelay(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS)));

        mDiscoveryManagerSettings.addListener(mMockDiscoveryManager);

        // set scan report delay
        mDiscoveryManagerSettings.setScanReportDelay(1L);
        assertThat("The scan report delay is set properly",
                mDiscoveryManagerSettings.getScanReportDelay(), is(equalTo(1L)));
        assertThat((Long) mSharedPreferencesMap.get("scan_report_delay"),
                is(equalTo(1L)));
        assertThat("Apply count is incremented", applyCnt, is(equalTo(1)));

        verify(mMockDiscoveryManager, atLeast(1))
                .onAdvertiseScanSettingsChanged();

        reset(mMockDiscoveryManager);

        // set second time
        mDiscoveryManagerSettings.setScanReportDelay(1L);
        assertThat("Set the same value is not possible",
                mDiscoveryManagerSettings.getScanReportDelay(), is(equalTo(1L)));
        assertThat((Long) mSharedPreferencesMap.get("scan_report_delay"),
                is(equalTo(1L)));
        assertThat("Apply count is not incremented", applyCnt, is(equalTo(1)));
        verify(mMockDiscoveryManager, never())
                .onAdvertiseScanSettingsChanged();
    }

    @Test
    public void testLoad() throws Exception {

        mDiscoveryManagerSettings.load();
        //Bluetooth MAC address automation is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getBoolean(Mockito.eq("automate_bluetooth_mac_address_resolution"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION));

        //maximum duration of "Provide Bluetooth MAC address" is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getLong(Mockito.eq("provide_bluetooth_mac_address_timeout"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS));

        //Bluetooth MAC address of this device is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getString(Mockito.eq("bluetooth_mac_address"), anyString());

        //discovery mode is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getInt(Mockito.eq("discovery_mode"),
                        Mockito.eq(0));


        //peer expiration time in milliseconds is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getLong(Mockito.eq("peer_expiration"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS));


        //Bluetooth LE advertise data type is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getInt(Mockito.eq("advertisement_data_type"),
                        anyInt());

        //Bluetooth LE advertise model is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getInt(Mockito.eq("advertise_mode"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE));

        //Bluetooth LE advertise TX power level is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getInt(Mockito.eq("advertise_tx_power_level"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL));

        //Bluetooth LE scan mode is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getInt(Mockito.eq("scan_mode"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_SCAN_MODE));

        //scan report delay is set
        verify(mMockSharedPreferences, Mockito.times(1))
                .getLong(Mockito.eq("scan_report_delay"),
                        Mockito.eq(DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS));


    }

    @Test
    public void testResetDefaults() throws Exception {

        mDiscoveryManagerSettings.resetDefaults();

        assertThat("Default Bluetooth MAC address automation is set",
                mDiscoveryManagerSettings.getAutomateBluetoothMacAddressResolution(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_AUTOMATE_BLUETOOTH_MAC_ADDRESS_RESOLUTION)));

        assertThat("Default maximum duration of \"Provide Bluetooth MAC address\" is set",
                mDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_PROVIDE_BLUETOOTH_MAC_ADDRESS_TIMEOUT_IN_MILLISECONDS)));

        assertThat("Default Bluetooth MAC address of this device is set",
                mDiscoveryManagerSettings.getBluetoothMacAddress(),
                is(equalTo(null)));

        assertThat("Default discovery mode is set", mDiscoveryManagerSettings.getDiscoveryMode(),
                is(equalTo(DiscoveryManager.DiscoveryMode.BLE)));

        assertThat("Default peer expiration time in milliseconds is set",
                mDiscoveryManagerSettings.getPeerExpiration(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS)));

        assertThat("Default Bluetooth LE advertise model is set",
                mDiscoveryManagerSettings.getAdvertiseMode(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE)));

        assertThat("Default Bluetooth LE advertise TX power level is set",
                mDiscoveryManagerSettings.getAdvertiseTxPowerLevel(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL)));

        assertThat("Default Bluetooth LE scan mode is set", mDiscoveryManagerSettings.getScanMode(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_SCAN_MODE)));

        assertThat("Default scan report delay is set", mDiscoveryManagerSettings.getScanReportDelay(),
                is(equalTo(DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS)));

    }
}