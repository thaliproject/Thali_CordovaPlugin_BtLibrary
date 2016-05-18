package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
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
    @Mock
    PackageManager mMockPackageManager;
    @Mock
    BluetoothDevice mMockBluetoothDevice;

    private BluetoothManager mBluetoothManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

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
        Field mInitializedField = mBluetoothManager.getClass().getDeclaredField("mInitialized");
        mInitializedField.setAccessible(true);

        Field mListenersField = mBluetoothManager.getClass().getDeclaredField("mListeners");
        mListenersField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothManager.BluetoothManagerListener> myListeners
                = new CopyOnWriteArrayList<>();

        mListenersField.set(mBluetoothManager, myListeners);

        // With no listeners added
        mBluetoothManager.bind(mMockBluetoothManagerListener);

        mBluetoothManager.release(mMockBluetoothManagerListener);

        verify(mMockContext, times(1)).unregisterReceiver(
                any(BroadcastReceiver.class));

        assertThat("should be empty as the given listener is removed from the list of listeners",
                myListeners.isEmpty(), is(true));

        mBluetoothManager.release(mMockBluetoothManagerListener);
        reset(mMockContext);
        verify(mMockContext, never()).unregisterReceiver(
                any(BroadcastReceiver.class));

        assertThat("should be false",
                mInitializedField.getBoolean(mBluetoothManager), is(false));
    }

    @Test
    public void testRelease_failure() throws Exception {
        Field mInitializedField = mBluetoothManager.getClass().getDeclaredField("mInitialized");
        mInitializedField.setAccessible(true);

        Field mListenersField = mBluetoothManager.getClass().getDeclaredField("mListeners");
        mListenersField.setAccessible(true);
        CopyOnWriteArrayList<BluetoothManager.BluetoothManagerListener> myListeners
                = new CopyOnWriteArrayList<>();

        mListenersField.set(mBluetoothManager, myListeners);

        mBluetoothManager.bind(mMockBluetoothManagerListener);

        // the exception
        doThrow(IllegalArgumentException.class).when(
                mMockContext).unregisterReceiver(any(BroadcastReceiver.class));

        mBluetoothManager.release(mMockBluetoothManagerListener);

        verify(mMockContext, times(1)).unregisterReceiver(
                any(BroadcastReceiver.class));

        assertThat("should be empty as the given listener is removed from the list of listeners",
                myListeners.isEmpty(), is(true));

        assertThat("should be false",
                mInitializedField.getBoolean(mBluetoothManager), is(false));
    }

    @Test
    public void testIsBluetoothSupported_notSupported() throws Exception {
        Field mBluetoothAdapterField = mBluetoothManager.getClass().getDeclaredField("mBluetoothAdapter");
        mBluetoothAdapterField.setAccessible(true);
        mBluetoothAdapterField.set(mBluetoothManager, null);

        assertThat("should be false as the device doesn't have Bluetooth support",
                mBluetoothManager.isBluetoothSupported(), is(false));
    }

    @Test
    public void testIsBluetoothSupported_supported() throws Exception {
        assertThat("should be false as the device has Bluetooth support",
                mBluetoothManager.isBluetoothSupported(), is(true));
    }

    @Test
    public void testIsBleSupported() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
                .thenReturn(true);

        assertThat("should be true as the device supports BLE",
                mBluetoothManager.isBleSupported(), is(true));

        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
                .thenReturn(false);

        assertThat("should be false as the device doesn't support BLE",
                mBluetoothManager.isBleSupported(), is(false));
    }

    @Test
    public void testIsBluetoothEnabled_notEnabled() throws Exception {
        Field mBluetoothAdapterField = mBluetoothManager.getClass()
                .getDeclaredField("mBluetoothAdapter");
        mBluetoothAdapterField.setAccessible(true);
        mBluetoothAdapterField.set(mBluetoothManager, null);

        assertThat("should be false as the device doesn't have Bluetooth support",
                mBluetoothManager.isBluetoothEnabled(), is(false));
    }

    @Test
    public void testIsBluetoothEnabled() throws Exception {
        when(mMockBluetoothAdapter.isEnabled()).thenReturn(false);

        assertThat("should be false as the device doesn't have Bluetooth enabled",
                mBluetoothManager.isBluetoothEnabled(), is(false));

        when(mMockBluetoothAdapter.isEnabled()).thenReturn(true);

        assertThat("should be true as the device's Bluetooth is enabled",
                mBluetoothManager.isBluetoothEnabled(), is(true));
    }

    @Test
    public void testSetBluetoothEnabled() throws Exception {

        mBluetoothManager.setBluetoothEnabled(true);
        verify(mMockBluetoothAdapter, times(1)).enable();

        mBluetoothManager.setBluetoothEnabled(false);
        verify(mMockBluetoothAdapter, times(1)).disable();
    }

    @Test
    public void testGetBluetoothAdapter() throws Exception {
        assertThat("should be equal to the adapter",
                mBluetoothManager.getBluetoothAdapter(), is(mMockBluetoothAdapter));
    }

    @Test
    public void testGetBluetoothName() throws Exception {
        String myName = "name";
        when(mMockBluetoothAdapter.getName()).thenReturn(myName);
        assertThat("should return proper name",
                mBluetoothManager.getBluetoothName(), is(myName));
        verify(mMockBluetoothAdapter, times(1)).getName();
    }

    @Test
    public void testGetRemoteDevice() throws Exception {
        String myAddress = "address";
        when(mMockBluetoothAdapter.getRemoteDevice(myAddress)).thenReturn(mMockBluetoothDevice);

        assertThat("should properly return the remote device",
                mBluetoothManager.getRemoteDevice(myAddress), is(mMockBluetoothDevice));
    }

    @Test
    public void testGetRemoteDevice_failure() throws Exception {
        String myAddress = "address";
        // the exception .getRemoteDevice
        doThrow(IllegalArgumentException.class).when(
                mMockBluetoothAdapter).getRemoteDevice(myAddress);

        assertThat("should be null as failed to get the remote device",
                mBluetoothManager.getRemoteDevice(myAddress), is(nullValue()));
    }

    @Test
    public void testGetRemoteDevice_failure_noadapter() throws Exception {
        Field mBluetoothAdapterField = mBluetoothManager.getClass()
                .getDeclaredField("mBluetoothAdapter");
        mBluetoothAdapterField.setAccessible(true);
        mBluetoothAdapterField.set(mBluetoothManager, null);

        assertThat("should be null as there is no Bluetooth adapter instance",
                mBluetoothManager.getRemoteDevice("someAddress"), is(nullValue()));
    }
}