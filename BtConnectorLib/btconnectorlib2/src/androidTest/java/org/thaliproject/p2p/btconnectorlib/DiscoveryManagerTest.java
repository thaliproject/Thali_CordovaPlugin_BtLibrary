package org.thaliproject.p2p.btconnectorlib;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DiscoveryManagerTest extends AbstractConnectivityManagerTest {

    private DiscoveryManager mDiscoveryManager = null;
    private static DiscoveryManager.DiscoveryMode defaultDiscoveryMode;
    private static boolean defaultBTStatus;
    private static boolean defaultWifiStatus;

    private static void setDiscoveryMode(DiscoveryManager.DiscoveryMode mode) {
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(InstrumentationRegistry.getContext());
        settings.setDiscoveryMode(mode);
    }

    private static DiscoveryManager.DiscoveryMode getDiscoveryMode() {
        DiscoveryManagerSettings settings = DiscoveryManagerSettings.getInstance(InstrumentationRegistry.getContext());
        return settings.getDiscoveryMode();
    }

    @Mock
    DiscoveryManager.DiscoveryManagerListener mMockManagerListener;

    @BeforeClass
    public static void init()  throws Exception{
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
                mMockManagerListener,
                UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        setDiscoveryMode(defaultDiscoveryMode);
        toggleWifi(defaultWifiStatus);
        toggleBluetooth(defaultBTStatus);
    }


    @Test
    public void testAfterConstruction() throws Exception {
        // check state
        assertEquals(DiscoveryManager.DiscoveryManagerState.NOT_STARTED, mDiscoveryManager.getState());

        // check if discovery manager is added as listener
        DiscoveryManagerSettings dmSettings = DiscoveryManagerSettings.getInstance(InstrumentationRegistry.getContext());
        try {
            dmSettings.addListener(mDiscoveryManager);
            fail();
        } catch (IllegalArgumentException exc) {
        }
    }

    @Test
    public void testStartBluetoothDisabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(false);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

    @Test
    public void testStartListeningBluetoothEnabledStartDiscovery() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.BLE);
        toggleBluetooth(true);
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        Thread.sleep(1);
        verify(mMockManagerListener, Mockito.times(1))
                .onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, false);
    }

    @Test
    public void testStartListeningWifiDisabled() throws Exception {
        setDiscoveryMode(DiscoveryManager.DiscoveryMode.WIFI);
        toggleWifi(false);
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

    @Test
    public void testStartListeningWifiEnabled() throws Exception {
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

    @Test
    public void testStartListeningWifiEnabledEnabledStartDiscovery() throws Exception {
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        toggleWifi(true);
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
    }

}