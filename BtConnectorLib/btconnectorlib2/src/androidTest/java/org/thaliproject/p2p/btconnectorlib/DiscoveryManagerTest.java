package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DiscoveryManagerTest {


    private static BluetoothAdapter mBluetoothAdapter = null;
    private DiscoveryManager mDiscoveryManager = null;

    @Mock
    DiscoveryManager.DiscoveryManagerListener mMockManagerListener;

    private static void toggleBluetooth(boolean turnOn) throws Exception {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            fail("No Bluetooth support!");
        }
        if (turnOn && !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        } else if (!turnOn && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
        Thread.sleep(3000);
        if (mBluetoothAdapter.isEnabled() != turnOn) {
            // wait additional 7 seconds
            Thread.sleep(7000);
        }
        assertThat(mBluetoothAdapter.isEnabled(), is(turnOn));
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        toggleBluetooth(true);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDiscoveryManager = new DiscoveryManager(InstrumentationRegistry.getContext(),
                mMockManagerListener,
                UUID.randomUUID(), "MOCK_NAME");
    }

    @After
    public void tearDown() throws Exception {

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
        toggleBluetooth(false);

        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        Thread.sleep(1);

        // ensure bluetooth is enabled for other tests
        toggleBluetooth(true);
    }

    @Test
    public void testStartListeningBluetoothEnabled() throws Exception {
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, false);
        assertThat(isRunning, is(false));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.NOT_STARTED));
        Thread.sleep(1);
    }

    @Test
    public void testStartListeningBluetoothEnabledStartDiscovery() throws Exception {
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(true, false);
        assertThat(isRunning, is(true));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        Thread.sleep(1);
        verify(mMockManagerListener, times(1))
                .onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, true, false);

    }

    @Test
    public void testStartListeningBluetoothEnabledStartAdvertising() throws Exception {
        when(mMockManagerListener.onPermissionCheckRequired(anyString())).thenReturn(true);
        boolean isRunning = mDiscoveryManager.start(false, true);
        assertThat(isRunning, is(true));
        assertThat(mDiscoveryManager.getState(), is(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE));
        Thread.sleep(1);
        verify(mMockManagerListener, times(1))
                .onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState.RUNNING_BLE, false, true);

    }

}