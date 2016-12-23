package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.internal.BluetoothMacAddressResolutionHelper;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.utils.PeerModel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryManagerTest {

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
    DiscoveryManagerSettings mMockDiscoveryManagerSettings;
    @Mock
    DiscoveryManager.DiscoveryManagerListener mMockManagerListener;
    @Mock
    BluetoothDevice mMockBluetoothDevice;
    @Mock
    WifiDirectManager mMockWifiDirectManager;
    @Mock
    Handler mHandler;
    @Mock
    BluetoothSocket mMockBluetoothSocket;
    @Mock
    BlePeerDiscoverer mMockBlePeerDiscoverer;
    @Mock
    WifiPeerDiscoverer mMockWifiPeerDiscoverer;
    @Mock
    PeerModel mMockPeerModel;
    @Mock
    BluetoothMacAddressResolutionHelper mMockBluetoothMacAddressResolutionHelper;

    DiscoveryManager discoveryManager;

    @SuppressLint("CommitPrefEdits")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06");
        when(mMockBluetoothManager.getBluetoothAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockSharedPreferences.edit()).thenReturn(mMockEditor);

        discoveryManager = new DiscoveryManager(mMockContext,
                mMockManagerListener,
                new UUID(1, 1),
                "test service type",
                mMockBluetoothManager,
                mMockSharedPreferences);
    }

    @After
    public void tearDown() throws Exception {
        discoveryManager.dispose();
        resetSettings();
    }

    private void resetSettings() throws IllegalAccessException, NoSuchFieldException {
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(mMockContext,
                mMockSharedPreferences);
        Field instanceField = dmSettings.getClass().getDeclaredField("mInstance");
        instanceField.setAccessible(true);
        instanceField.set(dmSettings, null);
    }

    @Test
    public void testConstructorThatTakesContextAndPrefs() throws Exception {
        DiscoveryManager dm = new DiscoveryManager(mMockContext,
                mMockManagerListener,
                new UUID(1, 1),
                "test service type",
                mMockBluetoothManager,
                mMockSharedPreferences);

        assertThat(dm, is(notNullValue()));
        assertThat(dm.getState(), is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));
    }

    @Test
    public void testIsBleMultipleAdvertisementSupported() throws Exception {

        //multi advertisement is not supported
        when(mMockBluetoothManager.isBleMultipleAdvertisementSupported())
                .thenReturn(BluetoothManager.FeatureSupportedStatus.NOT_SUPPORTED);

        assertThat("should be false if NOT_SUPPORTED",
                discoveryManager.isBleMultipleAdvertisementSupported(), is(false));

        //not supported ble
        when(mMockBluetoothManager.isBleMultipleAdvertisementSupported())
                .thenReturn(BluetoothManager.FeatureSupportedStatus.NOT_RESOLVED);

        when(mMockBluetoothManager.isBleSupported())
                .thenReturn(false);

        assertThat("should be false if NOT_RESOLVED and BLE not supported",
                discoveryManager.isBleMultipleAdvertisementSupported(), is(false));

        //ble supported
        when(mMockBluetoothManager.isBleMultipleAdvertisementSupported())
                .thenReturn(BluetoothManager.FeatureSupportedStatus.NOT_RESOLVED);

        when(mMockBluetoothManager.isBleSupported())
                .thenReturn(true);

        assertThat("should be true if NOT_RESOLVED but BLE supported",
                discoveryManager.isBleMultipleAdvertisementSupported(), is(true));

        //supported
        when(mMockBluetoothManager.isBleMultipleAdvertisementSupported())
                .thenReturn(BluetoothManager.FeatureSupportedStatus.SUPPORTED);

        assertThat("should be true if SUPPORTED",
                discoveryManager.isBleMultipleAdvertisementSupported(), is(true));

    }

    @Test
    public void testIsWifiDirectSupported() throws Exception {

        Field wifiDirectManagerField = discoveryManager.getClass().getDeclaredField("mWifiDirectManager");
        wifiDirectManagerField.setAccessible(true);
        wifiDirectManagerField.set(discoveryManager, mMockWifiDirectManager);

        //Wi-Fi Direct is supported
        when(mMockWifiDirectManager.isWifiDirectSupported())
                .thenReturn(true);

        assertThat("should be true if if Wi-Fi Direct is supported",
                discoveryManager.isWifiDirectSupported(), is(true));

        //Wi-Fi Direct is not supported
        when(mMockWifiDirectManager.isWifiDirectSupported())
                .thenReturn(false);

        assertThat("should be false if if Wi-Fi Direct is not supported",
                discoveryManager.isWifiDirectSupported(), is(false));


    }

    @Test
    public void testGetState() throws Exception {

        //default state
        assertThat("Has a proper default state", discoveryManager.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);
        assertThat("Has a proper state PROVIDING_BLUETOOTH_MAC_ADDRESS", discoveryManager.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.PROVIDING_BLUETOOTH_MAC_ADDRESS)));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE);
        assertThat("Has a proper state RUNNING_BLE", discoveryManager.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE)));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE_AND_WIFI);
        assertThat("Has a proper state RUNNING_BLE_AND_WIFI", discoveryManager.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI)));

    }

    @Test
    public void testIsRunning() throws Exception {
        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .WAITING_FOR_BLUETOOTH_MAC_ADDRESS);
        assertThat("Should be running when WAITING_FOR_BLUETOOTH_MAC_ADDRESS",
                discoveryManager.isRunning(), is(true));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);
        assertThat("Should be running when PROVIDING_BLUETOOTH_MAC_ADDRESS",
                discoveryManager.isRunning(), is(true));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE);
        assertThat("Should be running when RUNNING_BLE",
                discoveryManager.isRunning(), is(true));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_WIFI);
        assertThat("Should be running when RUNNING_WIFI",
                discoveryManager.isRunning(), is(true));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE_AND_WIFI);
        assertThat("Should be running when RUNNING_BLE_AND_WIFI",
                discoveryManager.isRunning(), is(true));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .NOT_STARTED);
        assertThat("Should not be running when NOT_STARTED",
                discoveryManager.isRunning(), is(false));

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .WAITING_FOR_SERVICES_TO_BE_ENABLED);
        assertThat("Should not be running when WAITING_FOR_SERVICES_TO_BE_ENABLED",
                discoveryManager.isRunning(), is(false));
    }

    @Test
    public void testIsDiscoveringBothNull() throws Exception {

        // both are null
        assertThat("Should not be true when mBlePeerDiscoverer mWifiPeerDiscoverer are null",
                discoveryManager.isDiscovering(), is(false));
        assertThat("Should not be true when mBlePeerDiscoverer is null",
                discoveryManager.isBleDiscovering(), is(false));
        assertThat("Should not be true when mWifiPeerDiscoverer is null",
                discoveryManager.isWifiDiscovering(), is(false));
    }

    @Test
    public void testIsDiscovering() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.SCANNING);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        // BlePeerDiscoverer is null
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when WifiPeerDiscoverer is scanning",
                discoveryManager.isDiscovering(), is(true));
        assertThat("Should be true when WifiPeerDiscoverer is scanning",
                discoveryManager.isWifiDiscovering(), is(true));
        assertThat("Should be false when BlePeerDiscoverer is not scanning",
                discoveryManager.isBleDiscovering(), is(false));

        bleStates = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING);
        wifiStates = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when BlePeerDiscoverer is scanning",
                discoveryManager.isDiscovering(), is(true));
        assertThat("Should be true when BlePeerDiscoverer is scanning",
                discoveryManager.isBleDiscovering(), is(true));
        assertThat("Should be false when BlePeerDiscoverer is not scanning",
                discoveryManager.isWifiDiscovering(), is(false));
    }

    @Test
    public void testIsDiscoveringScanning() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.SCANNING);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when scanning",
                discoveryManager.isDiscovering(), is(true));
        assertThat("Should be true when scanning",
                discoveryManager.isBleDiscovering(), is(true));
        assertThat("Should be true when scanning",
                discoveryManager.isWifiDiscovering(), is(true));
    }

    @Test
    public void testIsDiscoveringWifiNull() throws Exception {

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when at least one is scanning",
                discoveryManager.isDiscovering(), is(true));
        assertThat("Should be true when ble is scanning",
                discoveryManager.isBleDiscovering(), is(true));
        assertThat("Should be false when wifi is not scanning",
                discoveryManager.isWifiDiscovering(), is(false));
    }

    @Test
    public void testIsDiscoveringBleNull() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.SCANNING);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        assertThat("Should be true when at least one is scanning",
                discoveryManager.isDiscovering(), is(true));
        assertThat("Should be true when wifi is scanning",
                discoveryManager.isWifiDiscovering(), is(true));
        assertThat("Should be false when ble is not scanning",
                discoveryManager.isBleDiscovering(), is(false));
    }

    @Test
    public void testIsAdvertisingBothNull() throws Exception {

        // both are null
        assertThat("Should not be true when mBlePeerDiscoverer mWifiPeerDiscoverer are null",
                discoveryManager.isAdvertising(), is(false));
        assertThat("Should not be true when mBlePeerDiscoverer is null",
                discoveryManager.isBleAdvertising(), is(false));
        assertThat("Should not be true when mWifiPeerDiscoverer is null",
                discoveryManager.isWifiAdvertising(), is(false));
    }

    @Test
    public void testIsAdvertising() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.ADVERTISING);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        // BlePeerDiscoverer is null
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when WifiPeerDiscoverer is advertising",
                discoveryManager.isAdvertising(), is(true));
        assertThat("Should be true when WifiPeerDiscoverer is advertising",
                discoveryManager.isWifiAdvertising(), is(true));
        assertThat("Should be false when BlePeerDiscoverer is not advertising",
                discoveryManager.isBleAdvertising(), is(false));

        bleStates = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.ADVERTISING);
        wifiStates = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when BlePeerDiscovererStateSet is advertising",
                discoveryManager.isAdvertising(), is(true));
        assertThat("Should be false when WifiPeerDiscoverer is not advertising",
                discoveryManager.isWifiAdvertising(), is(false));
        assertThat("Should be true when BlePeerDiscoverer is advertising",
                discoveryManager.isBleAdvertising(), is(true));
    }

    @Test
    public void testIsAdvertisingAdvertising() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.ADVERTISING);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.ADVERTISING);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when advertising",
                discoveryManager.isAdvertising(), is(true));
        assertThat("Should be true when WifiPeerDiscoverer is advertising",
                discoveryManager.isWifiAdvertising(), is(true));
        assertThat("Should be true when BlePeerDiscoverer is advertising",
                discoveryManager.isBleAdvertising(), is(true));
    }

    @Test
    public void testIsAdvertisingWifiNull() throws Exception {

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.ADVERTISING);

        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);

        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when at least one is advertising",
                discoveryManager.isAdvertising(), is(true));
        assertThat("Should be false when WifiPeerDiscoverer is not advertising",
                discoveryManager.isWifiAdvertising(), is(false));
        assertThat("Should be true when BlePeerDiscoverer is advertising",
                discoveryManager.isBleAdvertising(), is(true));
    }

    @Test
    public void testIsAdvertisingBleNull() throws Exception {

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.ADVERTISING);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        assertThat("Should be true when at least one is advertising",
                discoveryManager.isAdvertising(), is(true));
        assertThat("Should be true when WifiPeerDiscoverer is advertising",
                discoveryManager.isWifiAdvertising(), is(true));
        assertThat("Should be false when BlePeerDiscoverer is not advertising",
                discoveryManager.isBleAdvertising(), is(false));
    }

    @Test
    public void testGetWifiDirectManager() throws Exception {
        Field field = discoveryManager.getClass().getDeclaredField("mWifiDirectManager");
        field.setAccessible(true);

        field.set(discoveryManager, mMockWifiDirectManager);

        assertThat("Should return proper mWifiDirectManager",
                discoveryManager.getWifiDirectManager(), is(equalTo(mMockWifiDirectManager)));
    }

    @Test
    public void testGetPeerModel() throws Exception {
        Field field = discoveryManager.getClass().getDeclaredField("mPeerModel");
        field.setAccessible(true);

        field.set(discoveryManager, mMockPeerModel);

        assertThat("Should return proper mPeerModel",
                discoveryManager.getPeerModel(), is(equalTo(mMockPeerModel)));
    }

    @Test
    public void testGetBluetoothMacAddressResolutionHelper() throws Exception {
        Field field = discoveryManager.getClass()
                .getDeclaredField("mBluetoothMacAddressResolutionHelper");
        field.setAccessible(true);

        field.set(discoveryManager, mMockBluetoothMacAddressResolutionHelper);

        assertThat("Should return proper mBluetoothMacAddressResolutionHelper",
                discoveryManager.getBluetoothMacAddressResolutionHelper(),
                is(equalTo(mMockBluetoothMacAddressResolutionHelper)));
    }

    @Test
    public void testGetMissingPermission() throws Exception {
        Field field = discoveryManager.getClass().getDeclaredField("mMissingPermission");
        field.setAccessible(true);
        String permission = "testPermission";

        field.set(discoveryManager, permission);

        assertThat("Should return proper mBluetoothMacAddressResolutionHelper",
                discoveryManager.getMissingPermission(), is(equalTo(permission)));
    }

    @Test
    public void testStartNotSupportedDiscoveryMode() throws Exception {
        DiscoveryManager discoveryManagerSpy;

        // set the state to something else than NOT_STARTED
        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);

        // mock BlePeerDiscoverer
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // mock WifiPeerDiscoverer
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode()).thenReturn(null);
        Field mDiscoveryManagerSettingsField = discoveryManager.getClass()
                .getDeclaredField("mSettings");
        mDiscoveryManagerSettingsField.setAccessible(true);
        mDiscoveryManagerSettingsField.set(discoveryManager, mMockDiscoveryManagerSettings);
        discoveryManagerSpy = spy(discoveryManager);

        assertThat("Should not start if not proper DiscoveryMode ",
                discoveryManagerSpy.start(true, true), is(false));

        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));
    }

    @Test
    public void testStartDiscoveryModeBLE() throws Exception {
        DiscoveryManager discoveryManagerSpy;

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);

        // mock BlePeerDiscoverer
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // mock WifiPeerDiscoverer
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        discoveryManagerSpy = spy(discoveryManager);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.BLE);

        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);

        assertThat("Should wait if BT is not enabled ",
                discoveryManagerSpy.start(true, true), is(false));
        assertThat("Should update the state to WAITING_FOR_SERVICES_TO_BE_ENABLED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED)));

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.isBleSupported()).thenReturn(false);
        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);
        assertThat("Should not start if BLE is not supported",
                discoveryManagerSpy.start(true, true), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        //supported BLE but scanner not created
        when(mMockBluetoothManager.isBleSupported()).thenReturn(true);
        assertThat("Should not start if scanner not started",
                discoveryManagerSpy.start(true, true), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // 2.1 discovery mode = DiscoveryMode.BLE, startScanner = false, startAdvertising = false

        //supported BleMultipleAdvertisement but discovery not started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.isBleSupported()).thenReturn(true);
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);

        assertThat("Should not start in BLE multi advertisement when startScanner and startAdvertising are false",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // 2.2 discovery mode = DiscoveryMode.BLE, startScanner = false, startAdvertising = true

        //supported BleMultipleAdvertisement and advertisement started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        mBlePeerDiscovererField.set(discoveryManagerSpy, mMockBlePeerDiscoverer);
        doReturn(true).when(discoveryManagerSpy).isBleDiscovering();
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.isBleSupported()).thenReturn(true);
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);
        when(mMockBlePeerDiscoverer.startAdvertiser()).thenReturn(true);

        assertThat("Should start in BLE multi advertisement when startScanner is false and startAdvertising is true",
                discoveryManagerSpy.start(false, true), is(true));
        assertThat("Should update the state to RUNNING_BLE ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE)));

        // when unknown mac address
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        doReturn(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)
                .when(discoveryManagerSpy).getBluetoothMacAddress();

        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.isBleSupported()).thenReturn(true);
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);
        when(mMockBlePeerDiscoverer.startAdvertiser()).thenReturn(true);

        assertThat("Should start in BLE multi advertisement when startScanner is false and startAdvertising is true",
                discoveryManagerSpy.start(false, true), is(true));
        assertThat("Should update the state to WAITING_FOR_BLUETOOTH_MAC_ADDRESS when BT MAC address unknown ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS)));

        // 2.3 discovery mode = DiscoveryMode.BLE, startScanner = true, startAdvertising = false

        // not supported filtering and scan batching and discovery started
        doReturn(false).when(discoveryManagerSpy).isBleOffloadedScanBatchingSupported();
        doReturn(false).when(discoveryManagerSpy).isBleOffloadedFilteringSupported();
        doReturn("00:01:02:03:04:05")
                .when(discoveryManagerSpy).getBluetoothMacAddress();
        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.isBleSupported()).thenReturn(true);
        doReturn(false).when(discoveryManagerSpy).isBleDiscovering();
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);

        assertThat("Should not start BLE discovery",
                discoveryManagerSpy.start(true, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // supported filtering and scan batching and discovery started
        mBlePeerDiscovererField.set(discoveryManagerSpy, mMockBlePeerDiscoverer);
        when(mMockBlePeerDiscoverer.startScanner()).thenReturn(true);
        doReturn(true).when(discoveryManagerSpy).isBleDiscovering();
        doReturn(true).when(discoveryManagerSpy).isBleOffloadedScanBatchingSupported();
        doReturn(true).when(discoveryManagerSpy).isBleOffloadedFilteringSupported();

        assertThat("Should start BLE discovery",
                discoveryManagerSpy.start(true, false), is(true));
        assertThat("Should update the state to RUNNING_BLE ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE)));
    }

    @Test
    public void testStartDiscoveryModeWIFI() throws Exception {
        DiscoveryManager discoveryManagerSpy;

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);

        // mock BlePeerDiscoverer
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // mock BlePeerDiscoverer
        Field mDiscoveryManagerSettingsField = discoveryManager.getClass()
                .getDeclaredField("mSettings");
        mDiscoveryManagerSettingsField.setAccessible(true);
        mDiscoveryManagerSettingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        // mock WifiDirectManager
        Field mWifiDirectManagerField = discoveryManager.getClass()
                .getDeclaredField("mWifiDirectManager");
        mWifiDirectManagerField.setAccessible(true);
        mWifiDirectManagerField.set(discoveryManager, mMockWifiDirectManager);

        // mock WifiPeerDiscoverer
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);
        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> bleStates
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        discoveryManagerSpy = spy(discoveryManager);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.WIFI);

        when(mMockWifiDirectManager.isWifiEnabled()).thenReturn(false);
        assertThat("Should wait if WIFI is not enabled ",
                discoveryManagerSpy.start(true, true), is(false));
        assertThat("Should update the state to WAITING_FOR_SERVICES_TO_BE_ENABLED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED)));

        when(mMockWifiDirectManager.isWifiEnabled()).thenReturn(true);
        assertThat("Should not start if Identity string is invalid",
                discoveryManagerSpy.start(true, true), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // since verifyIdentityString is using JSON, the successful discovery start
        // need to be tested it in instrumentation tests
    }

    @Test
    public void testStartDiscoveryModeBLE_AND_WIFI() throws Exception {
        // since verifyIdentityString is using JSON, the successful discovery start
        // need to be tested it in instrumentation tests
    }

    @Test
    public void testStopDiscovery() throws Exception {
        // mock mShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);

        // mock BlePeerDiscoverer
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // mock mWifiPeerDiscoverer
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        discoveryManager.stopDiscovery();

        assertThat("Should false when discovery stopped ",
                mShouldBeScanningField.getBoolean(discoveryManager),
                is(false));
        verify(mMockBlePeerDiscoverer, atLeastOnce())
                .stopScanner();
        verify(mMockWifiPeerDiscoverer, atLeastOnce())
                .stopDiscoverer();

    }

    @Test
    public void testStopAdvertising() throws Exception {

        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);

        // mock BlePeerDiscoverer
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // mock mWifiPeerDiscoverer
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        discoveryManager.stopAdvertising();

        assertThat("Should false when advertising stopped ",
                mShouldBeAdvertisingField.getBoolean(discoveryManager),
                is(false));
        verify(mMockBlePeerDiscoverer, atLeastOnce())
                .stopAdvertiser();
        verify(mMockWifiPeerDiscoverer, atLeastOnce())
                .stopAdvertiser();

    }

    @Test
    public void testStop() throws Exception {

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);
        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .PROVIDING_BLUETOOTH_MAC_ADDRESS);

        // mock mWifiDirectManager
        Field wifiDirectManagerField = discoveryManager.getClass()
                .getDeclaredField("mWifiDirectManager");
        wifiDirectManagerField.setAccessible(true);
        wifiDirectManagerField.set(discoveryManager, mMockWifiDirectManager);

        // mock mPeerModel
        Field mPeerModelField = discoveryManager.getClass().getDeclaredField("mPeerModel");
        mPeerModelField.setAccessible(true);
        mPeerModelField.set(discoveryManager, mMockPeerModel);

        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);

        // mock ShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);

        discoveryManager.stop();

        assertThat("Should not scan when stopped ",
                mShouldBeScanningField.getBoolean(discoveryManager),
                is(false));

        assertThat("Should not advertise when stopped ",
                mShouldBeAdvertisingField.getBoolean(discoveryManager),
                is(false));

        assertThat("Should be NOT_STARTED when stopped ",
                discoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

    @Test
    public void testDispose() throws Exception {
        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_WIFI);

        discoveryManager.dispose();

        verify(mMockDiscoveryManagerSettings, atLeastOnce())
                .removeListener(isA(DiscoveryManager.class));

        assertThat("Should be NOT_STARTED when disposed ",
                discoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));

    }

    @Test
    public void testOnWifiP2PStateChanged_NonWifiMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.BLE);

        discoveryManager.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_DISABLED);

        assertThat("The state should not change ",
                discoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
    }

    @Test
    public void testOnWifiP2PStateChanged_WifiMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .NOT_STARTED);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.WIFI);

        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, false);

        // mock ShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, false);

        // WifiPeerDiscoverer not null
        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);
        when(mMockWifiPeerDiscoverer.getState()).thenReturn(wifiStates);
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        DiscoveryManager discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_ENABLED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);
        discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_ENABLED);

        verify(discoveryManagerSpy, atLeastOnce())
                .start(anyBoolean(), anyBoolean());

        reset(discoveryManagerSpy);
        doReturn(true).when(discoveryManagerSpy).isRunning();
        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_DISABLED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        assertThat("The state should change when wifi state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));
    }

    @Test
    public void testOnWifiP2PStateChanged_BLE_AND_WIFIMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .NOT_STARTED);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);


        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, false);

        // mock ShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, false);


        DiscoveryManager discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_ENABLED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);
        discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_ENABLED);

        verify(discoveryManagerSpy, atLeastOnce())
                .start(anyBoolean(), anyBoolean());

        // WifiPeerDiscoverer null
        reset(discoveryManagerSpy);
        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_DISABLED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        assertThat("The state should change when wifi state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // WifiPeerDiscoverer not null
        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);
        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        discoveryManagerSpy = spy(discoveryManager);

        doReturn(true).when(discoveryManagerSpy).isRunning();

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_DISABLED);

        verify(mMockWifiPeerDiscoverer, atLeastOnce()).stopDiscovererAndAdvertiser();

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        assertThat("The state should change when wifi state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // WifiPeerDiscoverer not null and BluetoothEnabled
        discoveryManagerSpy = spy(discoveryManager);

        doReturn(true).when(discoveryManagerSpy).isRunning();
        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        doReturn(true).when(discoveryManagerSpy).isBleDiscovering();

        discoveryManagerSpy.onWifiP2PStateChanged(WifiP2pManager.WIFI_P2P_STATE_DISABLED);

        verify(mMockWifiPeerDiscoverer, atLeastOnce()).stopDiscovererAndAdvertiser();

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());
        assertThat("The state should change when wifi state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
    }

    @Test
    public void testOnWifiPeerDiscovererStateChanged() throws Exception {
        EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet> wifiStates
                = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.SCANNING);

        Field mWifiPeerDiscovererStateSetField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscovererStateSet");
        mWifiPeerDiscovererStateSetField.setAccessible(true);

        Field mWifiPeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mWifiPeerDiscoverer");
        mWifiPeerDiscovererField.setAccessible(true);

        mWifiPeerDiscovererField.set(discoveryManager, mMockWifiPeerDiscoverer);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        Field handlerField = discoveryManager.getClass().getDeclaredField("mHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryManager, mHandler);

        discoveryManager.onWifiPeerDiscovererStateChanged(wifiStates);

        //noinspection unchecked
        assertThat("The states should be updated ",
                (EnumSet<WifiPeerDiscoverer.WifiPeerDiscovererStateSet>) mWifiPeerDiscovererStateSetField.get(discoveryManager),
                is(equalTo(wifiStates)));

        verify(mHandler, atLeastOnce())
                .post(isA(Runnable.class));
    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged_NonBLEMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_WIFI);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.WIFI);

        discoveryManager.onBluetoothAdapterScanModeChanged(BluetoothAdapter.STATE_CONNECTED);

        assertThat("The state should not change ",
                discoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged_BLEMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .NOT_STARTED);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.BLE);


        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, false);

        // mock ShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, false);


        DiscoveryManager discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.STATE_CONNECTED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);
        discoveryManagerSpy = spy(discoveryManager);

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.STATE_CONNECTED);

        verify(discoveryManagerSpy, atLeastOnce())
                .start(anyBoolean(), anyBoolean());

        reset(discoveryManagerSpy);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());


        // WifiPeerDiscoverer not null
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_BLE);

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);

        discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);

        verify(mMockBlePeerDiscoverer, atLeastOnce()).stopScannerAndAdvertiser();

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        assertThat("The state should change when BT state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged__BLE_AND_WIFIMode() throws Exception {

        Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

        Field stateField = discoveryManager.getClass().getDeclaredField("mState");
        stateField.setAccessible(true);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .NOT_STARTED);

        when(mMockDiscoveryManagerSettings.getDiscoveryMode())
                .thenReturn(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);


        // mock mShouldBeAdvertising
        Field mShouldBeAdvertisingField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeAdvertising");
        mShouldBeAdvertisingField.setAccessible(true);
        mShouldBeAdvertisingField.setBoolean(discoveryManager, false);

        // mock ShouldBeScanning
        Field mShouldBeScanningField = discoveryManager.getClass()
                .getDeclaredField("mShouldBeScanning");
        mShouldBeScanningField.setAccessible(true);
        mShouldBeScanningField.setBoolean(discoveryManager, false);


        DiscoveryManager discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.STATE_CONNECTED);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        mShouldBeAdvertisingField.setBoolean(discoveryManager, true);
        mShouldBeScanningField.setBoolean(discoveryManager, true);
        discoveryManagerSpy = spy(discoveryManager);

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.STATE_CONNECTED);

        verify(discoveryManagerSpy, atLeastOnce())
                .start(anyBoolean(), anyBoolean());

        reset(discoveryManagerSpy);

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);
        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());


        // WifiPeerDiscoverer not null
        Field mBlePeerDiscovererField = discoveryManager.getClass()
                .getDeclaredField("mBlePeerDiscoverer");
        mBlePeerDiscovererField.setAccessible(true);
        mBlePeerDiscovererField.set(discoveryManager, mMockBlePeerDiscoverer);

        // set the state to check if it has changed
        stateField.set(discoveryManager, DiscoveryManager.DiscoveryManagerState
                .RUNNING_WIFI);

        discoveryManagerSpy = spy(discoveryManager);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);

        verify(mMockBlePeerDiscoverer, atLeastOnce()).stopScannerAndAdvertiser();

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());

        assertThat("The state should change when BT state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED));

        // mock WifiDirectManager
        Field mWifiDirectManagerField = discoveryManager.getClass()
                .getDeclaredField("mWifiDirectManager");
        mWifiDirectManagerField.setAccessible(true);
        mWifiDirectManagerField.set(discoveryManager, mMockWifiDirectManager);

        // and WiFi Enabled
        discoveryManagerSpy = spy(discoveryManager);
        doReturn(true).when(discoveryManagerSpy)
                .isRunning();
        doReturn(true).when(discoveryManagerSpy)
                .isWifiAdvertising();
        when(mMockWifiDirectManager.isWifiEnabled()).thenReturn(true);

        discoveryManagerSpy.onBluetoothAdapterScanModeChanged(BluetoothAdapter.SCAN_MODE_NONE);

        verify(mMockBlePeerDiscoverer, atLeastOnce()).stopScannerAndAdvertiser();

        verify(discoveryManagerSpy, never())
                .start(anyBoolean(), anyBoolean());
        assertThat("The state should change when BT state changed",
                discoveryManagerSpy.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
    }

    @Test
    public void testUpdateState() throws Exception {

        class UpdateStateChecker {

            DiscoveryManager discoveryManagerSpy;
            Field mShouldBeScanningField;
            Field mShouldBeAdvertisingField;

            UpdateStateChecker() throws Exception {
                Field settingsField = discoveryManager.getClass().getDeclaredField("mSettings");
                settingsField.setAccessible(true);
                settingsField.set(discoveryManager, mMockDiscoveryManagerSettings);

                Field mBluetoothMacAddressResolutionHelperField = discoveryManager.getClass()
                        .getDeclaredField("mBluetoothMacAddressResolutionHelper");
                mBluetoothMacAddressResolutionHelperField.setAccessible(true);
                mBluetoothMacAddressResolutionHelperField.set(discoveryManager, mMockBluetoothMacAddressResolutionHelper);

                Field mWifiDirectManagerField = discoveryManager.getClass().getDeclaredField("mWifiDirectManager");
                mWifiDirectManagerField.setAccessible(true);
                mWifiDirectManagerField.set(discoveryManager, mMockWifiDirectManager);

                mShouldBeScanningField = discoveryManager.getClass().getDeclaredField("mShouldBeScanning");
                mShouldBeScanningField.setAccessible(true);
                mShouldBeAdvertisingField = discoveryManager.getClass().getDeclaredField("mShouldBeAdvertising");
                mShouldBeAdvertisingField.setAccessible(true);

                discoveryManagerSpy = spy(discoveryManager);
            }

            void prepareCheck(DiscoveryManager.DiscoveryMode discoveryMode,
                              boolean shouldDiscover, boolean shouldAdvertise,
                              boolean blEnabled, boolean wifiEnabled,
                              boolean bleDiscovering, boolean bleAdvertising,
                              boolean wifiDiscovering, boolean wifiAdvertising,
                              boolean blMACUnknown, boolean blProvidingMACStarted) throws Exception {

                when(mMockDiscoveryManagerSettings.getDiscoveryMode()).thenReturn(discoveryMode);

                mShouldBeScanningField.setBoolean(discoveryManagerSpy, shouldDiscover);
                mShouldBeAdvertisingField.setBoolean(discoveryManagerSpy, shouldAdvertise);

                when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(blEnabled);
                when(mMockWifiDirectManager.isWifiEnabled()).thenReturn(wifiEnabled);

                doReturn(bleDiscovering).when(discoveryManagerSpy).isBleDiscovering();
                doReturn(bleAdvertising).when(discoveryManagerSpy).isBleAdvertising();
                doReturn(wifiDiscovering).when(discoveryManagerSpy).isWifiDiscovering();
                doReturn(wifiAdvertising).when(discoveryManagerSpy).isWifiAdvertising();

                if (blMACUnknown) {
                    when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("02:00:00:00:00:00");
                } else {
                    when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06");
                }
                when(mMockBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted())
                        .thenReturn(blProvidingMACStarted);
            }

            void check(DiscoveryManager.DiscoveryManagerState expectedState,
                       boolean expectedDiscovering, boolean expectedAdvertising) throws Exception {
                Method updateStateMethod = discoveryManager.getClass().getDeclaredMethod("updateState");
                updateStateMethod.setAccessible(true);
                updateStateMethod.invoke(discoveryManagerSpy);

                Field isDiscoveringField = discoveryManager.getClass().getDeclaredField("mIsDiscovering");
                isDiscoveringField.setAccessible(true);
                boolean isDiscovering = isDiscoveringField.getBoolean(discoveryManagerSpy);
                Field isAdvertisingField = discoveryManager.getClass().getDeclaredField("mIsAdvertising");
                isAdvertisingField.setAccessible(true);
                boolean isAdvertising = isAdvertisingField.getBoolean(discoveryManagerSpy);

                assertThat(discoveryManagerSpy.getState(), is(expectedState));
                assertThat(isDiscovering, is(expectedDiscovering));
                assertThat(isAdvertising, is(expectedAdvertising));
            }
        }

        UpdateStateChecker checker = new UpdateStateChecker();

        // BLE mode
        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, false, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, false, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, false, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, false, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                true, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                true, true);
        checker.check(DiscoveryManager.DiscoveryManagerState.PROVIDING_BLUETOOTH_MAC_ADDRESS, true, true);


        // WIFI mode
        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, true, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, false, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, true, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, false, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, false, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, true, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, false, true);

        // BLE_AND_WIFI mode
        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, true, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, false, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, false, true, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, true, false, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI, false, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                false, false, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, false, false,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED, false, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, false, true, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI, true, false);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, false, false,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, false, true, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                false, true, false, true,
                // blMacUnknown, blProvidingMacStarted
                false, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI, false, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, true, true,
                // blMacUnknown, blProvidingMacStarted
                true, false);
        checker.check(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS, true, true);

        checker.prepareCheck(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI,
                // shouldDiscover, shouldAdvertise, blEnabled, wifiEnabled,
                true, true, true, true,
                // bleDiscovering, bleAdvertising, wifiDiscovering, wifiAdvertising,
                true, true, true, true,
                // blMacUnknown, blProvidingMacStarted
                true, true);
        checker.check(DiscoveryManager.DiscoveryManagerState.PROVIDING_BLUETOOTH_MAC_ADDRESS, true, true);
    }
}