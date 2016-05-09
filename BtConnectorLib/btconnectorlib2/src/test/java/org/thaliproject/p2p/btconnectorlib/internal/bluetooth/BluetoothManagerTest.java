package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.ConnectionManagerSettings;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothManagerTest {

    @Mock
    Context mMockContext;

    @Mock
    SharedPreferences mMockSharedPreferences;

    @Mock
    BluetoothAdapter mMockBluetoothAdapter;

    @Mock
    BluetoothManager.BluetoothManagerListener mMockBluetoothManagerListener;

    private BluetoothManager mBluetoothManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

//        when(mMockBluetoothManager.getBluetoothMacAddress()).thenReturn("01:02:03:04:05:06");
//        when(mMockBluetoothManager.getBluetoothAdapter()).thenReturn(mMockBluetoothAdapter);
//        when(mMockSharedPreferences.edit()).thenReturn(mMockEditor);

        mBluetoothManager = BluetoothManager.getInstance(mMockContext,
                mMockBluetoothAdapter, mMockSharedPreferences);

    }

    @After
    public void tearDown() throws Exception {
        // the code below is needed to reset the BluetoothManager singleton
        Field instanceField = mBluetoothManager.getClass().getDeclaredField("mInstance");
        instanceField.setAccessible(true);
        instanceField.set(mBluetoothManager, null);
    }

    @Test
    public void testGetInstance() throws Exception {
        BluetoothManager bluetoothManager = BluetoothManager.getInstance(mMockContext,
                mMockBluetoothAdapter, mMockSharedPreferences);
        assertThat(bluetoothManager, is(notNullValue()));

    }
    @Test
    synchronized public void testBind_failure() throws Exception {

        Field mListenersField = mBluetoothManager.getClass().getDeclaredField("mListeners");
        mListenersField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothManager.BluetoothManagerListener> myListeners
                = new CopyOnWriteArrayList<>();

        mListenersField.set(mBluetoothManager, myListeners);

        // the exception
        doThrow(IllegalArgumentException.class).when(
                mMockContext).registerReceiver(any(BroadcastReceiver.class),
                any(IntentFilter.class));
        assertThat("should be false as it is not bound successfully",
                mBluetoothManager.bind(mMockBluetoothManagerListener), is(false));

        assertThat("should be false as a listener is added",
                myListeners.isEmpty(), is(false));
    }

    @Test
    synchronized public void testBind() throws Exception {

        Field mListenersField = mBluetoothManager.getClass().getDeclaredField("mListeners");
        mListenersField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothManager.BluetoothManagerListener> myListeners
                = new CopyOnWriteArrayList<>();

        mListenersField.set(mBluetoothManager, myListeners);

        // With no listeners added
        mBluetoothManager.bind(mMockBluetoothManagerListener);
        assertThat("should be true as it is bound successfully",
                mBluetoothManager.bind(mMockBluetoothManagerListener), is(true));
        verify(mMockContext, times(1)).registerReceiver(
                any(BroadcastReceiver.class), any(IntentFilter.class));

        assertThat("should be equal as listener is added",
                myListeners.get(0), is(mMockBluetoothManagerListener));

        assertThat("should be true as it is already bound successfully",
                mBluetoothManager.bind(mMockBluetoothManagerListener), is(true));
        reset(mMockContext);
        verify(mMockContext, never()).registerReceiver(
                any(BroadcastReceiver.class), any(IntentFilter.class));
    }

    @Test
    public void testRelease() throws Exception {

    }

    @Test
    public void testIsBluetoothSupported() throws Exception {

    }

    @Test
    public void testIsBleSupported() throws Exception {

    }

    @Test
    public void testIsBleMultipleAdvertisementSupported() throws Exception {

    }

    @Test
    public void testIsBluetoothEnabled() throws Exception {

    }

    @Test
    public void testSetBluetoothEnabled() throws Exception {

    }

    @Test
    public void testGetBluetoothAdapter() throws Exception {

    }

    @Test
    public void testGetBluetoothMacAddress() throws Exception {

    }

    @Test
    public void testGetBluetoothName() throws Exception {

    }

    @Test
    public void testGetRemoteDevice() throws Exception {

    }
}