package org.thaliproject.p2p.btconnectorlib;

import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DiscoveryManagerTest extends AbstractConnectivityManagerTest {

    private DiscoveryManager mDiscoveryManager = null;
    private static DiscoveryManager.DiscoveryMode defaultDiscoveryMode;
    private static boolean defaultBTStatus;
    private static boolean defaultWifiStatus;
    private final long MAX_TIMEOUT = 10000;
    private final long CHECK_INTERVAL = 500;

    private static void setDiscoveryMode(DiscoveryManager.DiscoveryMode mode) {
        DiscoveryManagerSettings settings = DiscoveryManagerSettings
                .getInstance(InstrumentationRegistry.getContext());
        settings.setDiscoveryMode(mode);
    }

    private static DiscoveryManager.DiscoveryMode getDiscoveryMode() {
        DiscoveryManagerSettings settings = DiscoveryManagerSettings
                .getInstance(InstrumentationRegistry.getContext());
        return settings.getDiscoveryMode();
    }

    // helper to check discovery state with defined timeout
    private void checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState expectedState)
            throws java.lang.InterruptedException {
        long currentTimeout = 0;
        while (currentTimeout < MAX_TIMEOUT) {
            Thread.sleep(CHECK_INTERVAL);
            currentTimeout += CHECK_INTERVAL;
            try {
                assertThat(mDiscoveryManager.getState(), is(expectedState));
                break;
            } catch (java.lang.AssertionError assertionError) {
                if (currentTimeout >= MAX_TIMEOUT) {
                    throw assertionError;
                }
            }
        }
    }

    // helper to check discovery manager state, discovering state and advertisement state
    private void checkAllStatesWithTimeout(boolean isRunning, boolean isDiscovering, boolean isAdvertising)
            throws java.lang.InterruptedException {
        long currentTimeout = 0;
        while (currentTimeout < MAX_TIMEOUT) {
            Thread.sleep(CHECK_INTERVAL);
            currentTimeout += CHECK_INTERVAL;
            try {
                assertThat(mDiscoveryManager.isRunning(), is(isRunning));
                assertThat(mDiscoveryManager.isDiscovering(), is(isDiscovering));
                assertThat(mDiscoveryManager.isAdvertising(), is(isAdvertising));
                break;
            } catch (java.lang.AssertionError assertionError) {
                if (currentTimeout >= MAX_TIMEOUT) {
                    throw assertionError;
                }
            }
        }
    }

    @Mock
    DiscoveryManager.DiscoveryManagerListener mMockDiscoveryManagerListener;

    @BeforeClass
    public static void init()  throws Exception {
        // call Looper.prepare in try/catch because it may be already called by other test
        try {
            Looper.prepare();
        } catch (java.lang.RuntimeException ex) {
            // can throw exception if current thread already has a looper
        }
        // save the state
        defaultDiscoveryMode = getDiscoveryMode();
        defaultBTStatus = getBluetoothStatus();
        defaultWifiStatus = getWifiStatus();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        toggleBluetooth(false);
        toggleWifi(false);

        mDiscoveryManager = new DiscoveryManager(InstrumentationRegistry.getContext(),
                mMockDiscoveryManagerListener,
                UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {
        mDiscoveryManager.dispose();
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false);
        mDiscoveryManager = null;
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        // restore the saved state
        setDiscoveryMode(defaultDiscoveryMode);
        toggleWifi(defaultWifiStatus);
        toggleBluetooth(defaultBTStatus);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        assertEquals(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, mDiscoveryManager.getState());

        // check if discovery manager is added as listener
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings
                .getInstance(InstrumentationRegistry.getContext());
        thrown.expect(IllegalArgumentException.class);
        dmSettings.addListener(mDiscoveryManager);
    }

    @Test
    public void testStartBluetoothDisabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(false);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartListeningBluetoothEnabledStartDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testStartListeningWifiDisabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        toggleWifi(false);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartListeningWifiEnabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartListeningWifiEnabledStartDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testStartListeningWifiEnabledStartAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testStartListeningWifiEnabledStartDiscoveryAndAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
    }

    @Test
    public void testStartListeningWifiDisabledBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        toggleWifi(false);
        toggleBluetooth(false);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
    }

    // WIFI enabled, BT disabled
    @Test
    public void testStartListeningWifiEnabledBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(false);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartListeningWifiEnabledStartDiscoveryBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(false);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testStartListeningWifiEnabledStartAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(false);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testStartListeningWifiEnabledStartDiscoveryAndAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(false);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
    }

    // Both BT and WIFI enabled
    @Test
    public void testStartListeningWifiBTEnabledStartDiscoveryBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testStartListeningWifiBTEnabledStartAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testStartListeningWifiBTEnabledStartDiscoveryAndAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true);
    }

    // BT enabled, Wifi disabled
    @Test
    public void testStartListeningBTEnabledStartDiscoveryBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(false);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testStartListeningBTEnabledStartAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(false);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testStartListeningBTEnabledStartDiscoveryAndAdvertisingBW() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(false);
        toggleBluetooth(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        boolean isRunning = mDiscoveryManager.start(true, true);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true);
    }

    @Test
    public void testChangeBluetoothStateDuringDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testChangeBluetoothStateDuringAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testChangeBluetoothStateDuringBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true);
    }

    @Test
    public void testChangeWifiStateDuringDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
    }

    @Test
    public void testChangeWifiStateDuringAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
    }

    @Test
    public void testChangeWifiStateDuringBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
    }

    @Test
    public void testChangeBluetoothWifiStateDuringDiscovering() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testChangeBluetoothWifiStateDuringAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testChangeBluetoothWifiStateDuringBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopBluetoothDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, true, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);;
    }

    @Test
    public void testStartStopBluetoothAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopBluetoothBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopWifiDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, true, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopWifiAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopWifiBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopBluetoothWifiDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, true, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopBluetoothWifiAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    @Test
    public void testStartStopBluetoothWifiBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false);
    }

    // start called multiple times with different args and in different context
    // change mode (advertise/scanner/power) during work
    // ConnectionManager - turn off bluetooth during work
    // move timeout state check to connectionmanager
}