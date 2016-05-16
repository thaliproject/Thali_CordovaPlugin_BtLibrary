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

import java.lang.reflect.Field;
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
    private final long MAX_TIMEOUT = 20000;
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
    private void checkAllStatesWithTimeout(boolean isRunning,
                                           boolean isBleDiscovering, boolean isBleAdvertising,
                                           boolean isWifiDiscovering, boolean isWifiAdvertising)
            throws java.lang.InterruptedException {
        long currentTimeout = 0;
        while (currentTimeout < MAX_TIMEOUT) {
            Thread.sleep(CHECK_INTERVAL);
            currentTimeout += CHECK_INTERVAL;
            try {
                assertThat(mDiscoveryManager.isRunning(), is(isRunning));
                assertThat(mDiscoveryManager.isBleDiscovering(), is(isBleDiscovering));
                assertThat(mDiscoveryManager.isBleAdvertising(), is(isBleAdvertising));
                assertThat(mDiscoveryManager.isWifiDiscovering(), is(isWifiDiscovering));
                assertThat(mDiscoveryManager.isWifiAdvertising(), is(isWifiAdvertising));
                assertThat(mDiscoveryManager.isDiscovering(), is(isBleDiscovering || isWifiDiscovering));
                assertThat(mDiscoveryManager.isAdvertising(), is(isBleAdvertising || isWifiAdvertising));
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
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartListeningBluetoothEnabledStartDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);
    }

    @Test
    public void testStartListeningWifiDisabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        toggleWifi(false);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartListeningWifiEnabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, false, false, true, false);
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
        checkAllStatesWithTimeout(true, false, false, false, true);
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
        checkAllStatesWithTimeout(true, false, false, true, true);
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
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, false, false, true, false);
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
        checkAllStatesWithTimeout(true, false, false, false, true);
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
        checkAllStatesWithTimeout(true, false, false, true, true);
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
        checkAllStatesWithTimeout(true, true, false, true, false);
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
        checkAllStatesWithTimeout(true, false, true, false, true);
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
        checkAllStatesWithTimeout(true, true, true, true, true);
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
        checkAllStatesWithTimeout(true, true, false, false, false);
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
        checkAllStatesWithTimeout(true, false, true, false, false);
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
        checkAllStatesWithTimeout(true, true, true, false, false);
    }

    @Test
    public void testChangeBluetoothStateDuringDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);
    }

    @Test
    public void testChangeBluetoothStateDuringAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true, false, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true, false, false);
    }

    @Test
    public void testChangeBluetoothStateDuringBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);
    }

    @Test
    public void testChangeWifiStateDuringDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, false);
    }

    @Test
    public void testChangeWifiStateDuringAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, false, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, false, true);
    }

    @Test
    public void testChangeWifiStateDuringBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);
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
        checkAllStatesWithTimeout(true, true, false, true, false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false, true, false);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, false);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false, true, false);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, false, true, false, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true, false, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, false, true);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true, false, true);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, true, true, true, true);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);
        toggleWifi(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true, true, true);
        toggleBluetooth(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);
        toggleBluetooth(true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true, true, true);
        toggleBluetooth(false);
        toggleWifi(false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopBluetoothDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, true, false, false, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopBluetoothAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true, false, false);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, false, true, false, false);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopBluetoothBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        checkAllStatesWithTimeout(true, false, true, false, false);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopWifiDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, false, false, true, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopWifiAdvertising() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, false, true);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, false, false, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testStartStopWifiBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI));
        checkAllStatesWithTimeout(true, false, false, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, true, false, true, false);
        // stopping advertising does nothing
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, true, false, true, false);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, false, true, false, true);
        // stopping discovery does nothing
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, false, true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
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
        checkAllStatesWithTimeout(true, true, true, true, true);
        mDiscoveryManager.stopDiscovery();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI));
        checkAllStatesWithTimeout(true, false, true, false, true);
        mDiscoveryManager.stopAdvertising();
        assertThat(mDiscoveryManager.getState(),
                is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testMultipleStartBluetooth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, false, false, false);

        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, false, true, false, false);

        mDiscoveryManager.start(false, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testMultipleStartWifi() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, false);
        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, false, true);

        mDiscoveryManager.start(false, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testMultipleStartBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true, true, true);

        mDiscoveryManager.start(true, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, false, true, false);
        mDiscoveryManager.start(false, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, false, true, false, true);

        mDiscoveryManager.start(false, false);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.NOT_STARTED);
        checkAllStatesWithTimeout(false, false, false, false, false);
    }

    @Test
    public void testDiscoveryModeChangeBluetooth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleBluetooth(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);
    }

    @Test
    public void testDiscoveryModeChangeWifi() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_SERVICES_TO_BE_ENABLED);
        checkAllStatesWithTimeout(false, false, false, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);
    }

    @Test
    public void testDiscoveryModeChangeBoth() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true, true, true);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE);
        checkAllStatesWithTimeout(true, true, true, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_WIFI);
        checkAllStatesWithTimeout(true, false, false, true, true);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE_AND_WIFI);
        checkAllStatesWithTimeout(true, true, true, true, true);
    }

    @Test
    public void testUnknownBluetoothMacAddress() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        when(mMockDiscoveryManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        mDiscoveryManager.setEmulateMarshmallow(true);
        mDiscoveryManager.setPeerName("TestPeerName");
        toggleBluetooth(true);
        toggleWifi(true);

        DiscoveryManagerSettings dmSettings =
                DiscoveryManagerSettings.getInstance(InstrumentationRegistry.getContext());
        Field blMacAddressField = dmSettings.getClass().getDeclaredField("mBluetoothMacAddress");
        blMacAddressField.setAccessible(true);
        blMacAddressField.set(dmSettings, "");

        mDiscoveryManager.start(true, true);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS);
        checkAllStatesWithTimeout(true, true, false, false, false);

        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE_AND_WIFI);
        checkStateWithTimeout(DiscoveryManager.DiscoveryManagerState.WAITING_FOR_BLUETOOTH_MAC_ADDRESS);
        checkAllStatesWithTimeout(true, true, false, true, true);
    }
}