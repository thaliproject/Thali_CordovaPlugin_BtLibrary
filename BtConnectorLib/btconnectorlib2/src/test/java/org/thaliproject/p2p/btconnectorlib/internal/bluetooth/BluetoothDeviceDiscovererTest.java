package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothDeviceDiscovererTest {

    @Mock
    Context mMockContext;
    @Mock
    BluetoothDeviceDiscoverer.BluetoothDeviceDiscovererListener mMockListener;
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    PeerProperties mMockPeerProperties;

    BluetoothDeviceDiscoverer mBluetoothDeviceDiscoverer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBluetoothDeviceDiscoverer = new BluetoothDeviceDiscoverer(mMockContext,
                mMockBluetoothAdapter, mMockListener);
    }

    @Test
    public void testConstructor() throws Exception {

        BluetoothDeviceDiscoverer btd = new BluetoothDeviceDiscoverer(mMockContext,
                mMockBluetoothAdapter, mMockListener);

        assertThat(btd, is(notNullValue()));
    }

    @Test
    public void testStartStop_success() throws Exception {
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);

        // start
        assertThat("It returns true as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.start(10000L),
                is(true));

        // check if running
        assertThat("It returns true as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(true));

        // stop
        mBluetoothDeviceDiscoverer.stop();

        verify(mMockBluetoothAdapter, times(1)).cancelDiscovery();
        verify(mMockContext, times(1)).unregisterReceiver(Matchers.any(BroadcastReceiver.class));

        // check if stopped
        assertThat("It returns false as bt discoverer is stopped",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(false));
    }

    @Test
    public void testStartStop_failure() throws Exception {
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(false);

        // start
        assertThat("It returns true as bt adapter is not discovering",
                mBluetoothDeviceDiscoverer.start(10000L),
                is(false));

        // check if running
        assertThat("It returns false as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(false));
        verify(mMockBluetoothAdapter, never()).cancelDiscovery();
        verify(mMockContext, times(1)).unregisterReceiver(Matchers.any(BroadcastReceiver.class));

        // check if stopped
        assertThat("It returns false as bt discoverer is stopped",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(false));
    }

    @Test
    public void testStartStop_failure2() throws Exception {
        doThrow(IllegalArgumentException.class).when(
                mMockContext).registerReceiver(any(BroadcastReceiver.class),
                any(IntentFilter.class));
        // start
        assertThat("It returns false as bt adapter is not discovering",
                mBluetoothDeviceDiscoverer.start(10000L),
                is(false));

        // check if running
        assertThat("It returns false as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(false));
    }

    @Test
    public void testStop_failure() throws Exception {
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);

        // start
        assertThat("It returns true as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.start(10000L),
                is(true));

        // check if running
        assertThat("It returns true as bt adapter is discovering",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(true));

        doThrow(IllegalArgumentException.class).when(
                mMockContext).registerReceiver(any(BroadcastReceiver.class),
                any(IntentFilter.class));

        // stop and throw exception
        mBluetoothDeviceDiscoverer.stop();

        verify(mMockBluetoothAdapter, times(1)).cancelDiscovery();
        verify(mMockContext, times(1)).unregisterReceiver(Matchers.any(BroadcastReceiver.class));

        // check if stopped
        assertThat("It returns false as bt discoverer is stopped",
                mBluetoothDeviceDiscoverer.isRunning(),
                is(false));
    }

}