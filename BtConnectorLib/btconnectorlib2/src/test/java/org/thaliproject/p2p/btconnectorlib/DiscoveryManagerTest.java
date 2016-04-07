package org.thaliproject.p2p.btconnectorlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.internal.AbstractBluetoothConnectivityAgent;
import org.thaliproject.p2p.btconnectorlib.internal.BluetoothMacAddressResolutionHelper;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothConnector;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;
import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiPeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.utils.PeerModel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    Handler mHander;

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

        bleStates = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING);
        wifiStates = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when BlePeerDiscovererStateSet is scanning",
                discoveryManager.isDiscovering(), is(true));

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
    }

    @Test
    public void testIsAdvertisingBothNull() throws Exception {

        // both are null
        assertThat("Should not be true when mBlePeerDiscoverer mWifiPeerDiscoverer are null",
                discoveryManager.isAdvertising(), is(false));
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

        bleStates = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.ADVERTISING);
        wifiStates = EnumSet.of(WifiPeerDiscoverer.WifiPeerDiscovererStateSet.NOT_STARTED);

        when(mMockWifiPeerDiscoverer.getState())
                .thenReturn(wifiStates);

        when(mMockBlePeerDiscoverer.getState())
                .thenReturn(bleStates);

        assertThat("Should be true when BlePeerDiscovererStateSet is advertising",
                discoveryManager.isAdvertising(), is(true));

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
        discoveryManagerSpy = spy(discoveryManager);

        assertThat("Should not start if not proper DiscoveryMode ",
                discoveryManagerSpy.start(false, false), is(false));

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

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(false);
        assertThat("Should not start if BT is not enabled ",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        assertThat("Should not start if BLE multi advertisement not supported",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        //supported BleMultipleAdvertisement but bleDiscoveryStarted not started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);

        assertThat("Should not start if BLE multi advertisement not supported",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // 2.1 discovery mode = DiscoveryMode.BLE, startScanner = false, startAdvertising = false

        //supported BleMultipleAdvertisement but bleDiscoveryStarted started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);

        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);

        assertThat("Should not start in BLE multi advertisement when startScanner and startAdvertising are false",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // 2.2 discovery mode = DiscoveryMode.BLE, startScanner = true, startAdvertising = false

        //supported BleMultipleAdvertisement and bleDiscoveryStarted started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);
        when(mMockBlePeerDiscoverer.startScanner()).thenReturn(true);

        assertThat("Should start in BLE multi advertisement when startScanner true and startAdvertising are false",
                discoveryManagerSpy.start(true, false), is(true));
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
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);
        when(mMockBlePeerDiscoverer.startScanner()).thenReturn(true);

        assertThat("Should start in BLE multi advertisement when startScanner true and startAdvertising are false",
                discoveryManagerSpy.start(true, false), is(true));
        assertThat("Should update the state to WAITING_FOR_BLUETOOTH_MAC_ADDRESS when BT MAC address unknown ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS)));

        // 2.3 discovery mode = DiscoveryMode.BLE, startScanner = false, startAdvertising = true

        //supported BleMultipleAdvertisement and bleDiscoveryStarted started
        doReturn(true).when(discoveryManagerSpy).isBleMultipleAdvertisementSupported();
        doReturn("00:01:02:03:04:05")
                .when(discoveryManagerSpy).getBluetoothMacAddress();

        doReturn(mMockBlePeerDiscoverer).when(discoveryManagerSpy)
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();

        when(mMockBluetoothManager.isBluetoothEnabled()).thenReturn(true);

        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);

        when(mMockBlePeerDiscoverer.startAdvertiser()).thenReturn(true);

        assertThat("Should not start in BLE multi advertisement when startScanner is false and startAdvertising are true",
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
        when(mMockBluetoothManager.bind(Mockito.any(DiscoveryManager.class))).thenReturn(true);
        when(mMockBlePeerDiscoverer.startAdvertiser()).thenReturn(true);

        assertThat("Should start in BLE multi advertisement when startScanner true and startAdvertising are false",
                discoveryManagerSpy.start(true, false), is(true));
        assertThat("Should update the state to WAITING_FOR_BLUETOOTH_MAC_ADDRESS when BT MAC address unknown ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS)));
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
        assertThat("Should not start if WIFI is not enabled ",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        when(mMockWifiDirectManager.isWifiEnabled()).thenReturn(true);
        assertThat("Should not start if Identity string is invalid",
                discoveryManagerSpy.start(false, false), is(false));
        assertThat("Should update the state to NOT_STARTED ",
                discoveryManagerSpy.getState(),
                is(equalTo(DiscoveryManager.DiscoveryManagerState.NOT_STARTED)));

        // @TODO check how to mock verifyIdentityString and perform the rest of tests

    }

    @Test
    public void testStartDiscoveryModeBLE_AND_WIFI() throws Exception {
        // @TODO check how to mock verifyIdentityString and perform the rest of tests

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

        // mock mWifiDirectManager
        Field wifiDirectManagerField = discoveryManager.getClass().getDeclaredField("mWifiDirectManager");
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
    public void testMakeDeviceDiscoverable() throws Exception {

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
    public void testGetBlePeerDiscovererInstanceAndCheckBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testOnDiscoveryModeChanged() throws Exception {

    }

    @Test
    public void testOnPeerExpirationSettingChanged() throws Exception {

    }

    @Test
    public void testOnAdvertiseSettingsChanged() throws Exception {

    }

    @Test
    public void testOnScanSettingsChanged() throws Exception {

    }

    @Test
    public void testOnBluetoothAdapterScanModeChanged() throws Exception {

    }

    @Test
    public void testOnWifiStateChanged() throws Exception {

    }

    @Test
    public void testOnWifiPeerDiscovererStateChanged() throws Exception {

    }

    @Test
    public void testOnBlePeerDiscovererStateChanged() throws Exception {

    }

    @Test
    public void testOnPeerDiscovered() throws Exception {

    }

    @Test
    public void testOnP2pDeviceListChanged() throws Exception {

    }

    @Test
    public void testOnProvideBluetoothMacAddressRequest() throws Exception {

    }

    @Test
    public void testOnPeerReadyToProvideBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testOnProvideBluetoothMacAddressResult() throws Exception {

    }

    @Test
    public void testOnBluetoothMacAddressResolved() throws Exception {

    }

    @Test
    public void testOnProvideBluetoothMacAddressModeStartedChanged() throws Exception {

    }

    @Test
    public void testOnReceiveBluetoothMacAddressModeStartedChanged() throws Exception {

    }

    @Test
    public void testOnPeerAdded() throws Exception {

    }

    @Test
    public void testOnPeerUpdated() throws Exception {

    }

    @Test
    public void testOnPeerExpiredAndRemoved() throws Exception {

    }
}