package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;

import java.lang.reflect.Field;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BleAdvertiserTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    AdvertiseData mMockAdvertiseData;
    @Mock
    AdvertiseData mTmpMockAdvertiseData;
    @Mock
    BleAdvertiser.Listener mMockListener;
    @Mock
    AdvertiseSettings.Builder mMockBuilder;
    @Mock
    DiscoveryManagerSettings mMockDiscoveryManagerSettings;
    @Mock
    AdvertiseSettings mMockAdvertiseSettings;
    @Mock
    BluetoothLeAdvertiser mMockBluetoothLeAdvertiser;
    BleAdvertiser mBleAdvertiser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseSettings);

        when(mMockDiscoveryManagerSettings.getAdvertiseTxPowerLevel()).thenReturn(
                DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE);

        when(mMockDiscoveryManagerSettings.getAdvertiseMode()).thenReturn(
                DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL);

        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(
                mMockBluetoothLeAdvertiser);

        mBleAdvertiser = new BleAdvertiser(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                mMockDiscoveryManagerSettings);
    }

    @Test
    public void bleAdvertiserConstructor_noSettings() throws Exception {
        reset(mMockBuilder);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseSettings);

        BleAdvertiser bleAdvertiser
                = new BleAdvertiser(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                null);

        assertThat("The BleAdvertiser is properly created",
                bleAdvertiser, is(notNullValue()));

        verify(mMockBuilder, times(1)).setAdvertiseMode(
                DiscoveryManagerSettings.DEFAULT_ADVERTISE_MODE);

        verify(mMockBuilder, times(1)).setTxPowerLevel(
                DiscoveryManagerSettings.DEFAULT_ADVERTISE_TX_POWER_LEVEL);

        verify(mMockBuilder, times(1)).setTimeout(0);
        verify(mMockBuilder, times(1)).setConnectable(false);
    }

    @Test
    public void bleAdvertiserConstructor_withSettings() throws Exception {

        reset(mMockBuilder);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseSettings);

        when(mMockDiscoveryManagerSettings.getAdvertiseTxPowerLevel()).thenReturn(
                AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

        when(mMockDiscoveryManagerSettings.getAdvertiseMode()).thenReturn(
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);

        BleAdvertiser bleAdvertiser
                = new BleAdvertiser(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                mMockDiscoveryManagerSettings);

        assertThat("The BleAdvertiser is properly created",
                bleAdvertiser, is(notNullValue()));

        verify(mMockBuilder, times(1)).setAdvertiseMode(
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);

        verify(mMockBuilder, times(1)).setTxPowerLevel(
                AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

        verify(mMockBuilder, times(1)).setTimeout(0);
        verify(mMockBuilder, times(1)).setConnectable(false);
    }

    @Test
    public void testSetAdvertiseData_noRestart() throws Exception {
        Field advertiseDataField = mBleAdvertiser.getClass().getDeclaredField("mAdvertiseData");
        advertiseDataField.setAccessible(true);

        mBleAdvertiser.stop(false);
        reset(mMockBluetoothLeAdvertiser);
        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);

        assertThat("The advertise data is properly set",
                (AdvertiseData) advertiseDataField.get(mBleAdvertiser), is(mMockAdvertiseData));

        // should not restart the instance, as it was not running
        verify(mMockBluetoothLeAdvertiser, never()).startAdvertising(Mockito.any(AdvertiseSettings.class),
                Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseCallback.class));

        verify(mMockBluetoothLeAdvertiser, never()).stopAdvertising(Mockito.any(AdvertiseCallback.class));

    }

    @Test
    public void testSetAdvertiseData_restart() throws Exception {
        Field advertiseDataField = mBleAdvertiser.getClass().getDeclaredField("mAdvertiseData");
        advertiseDataField.setAccessible(true);

        // needed to start advertising
        advertiseDataField.set(mBleAdvertiser, mTmpMockAdvertiseData);
        mBleAdvertiser.start();

        reset(mMockBluetoothLeAdvertiser);
        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);

        assertThat("The advertise data is properly set",
                (AdvertiseData) advertiseDataField.get(mBleAdvertiser), is(mMockAdvertiseData));

        // should restart the instance, as it was running
        verify(mMockBluetoothLeAdvertiser, times(1)).startAdvertising(Mockito.any(AdvertiseSettings.class),
                Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseCallback.class));

        verify(mMockBluetoothLeAdvertiser, times(1)).stopAdvertising(Mockito.any(AdvertiseCallback.class));
    }


    @Test
    public void testSetAdvertiseData_exception() throws Exception {
        thrown.expect(NullPointerException.class);
        mBleAdvertiser.setAdvertiseData(null);
    }

    @Test
    public void testSetAdvertiseSettings() throws Exception {
        Field advertiseSettingsField = mBleAdvertiser.getClass().getDeclaredField("mAdvertiseSettings");
        advertiseSettingsField.setAccessible(true);

        mBleAdvertiser.setAdvertiseSettings(mMockAdvertiseSettings);

        assertThat("The advertise settings is properly set",
                (AdvertiseSettings) advertiseSettingsField.get(mBleAdvertiser), is(mMockAdvertiseSettings));
    }

    @Test
    public void testSetAdvertiseSettings_exception() throws Exception {
        thrown.expect(NullPointerException.class);
        mBleAdvertiser.setAdvertiseSettings(null);
    }

    @Test
    public void testStart() throws Exception {
        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        assertThat("It should return true if advertising is properly started",
                mBleAdvertiser.start(), is(true));

        // check if the instance is started
        verify(mMockBluetoothLeAdvertiser, times(1)).startAdvertising(mMockAdvertiseSettings,
                mMockAdvertiseData, null, mBleAdvertiser);

        assertThat("It should return true as the advertiser is either starting or running",
                mBleAdvertiser.isStarted(), is(true));

        // as it has the state STARTING the event shouldn't be called
        verify(mMockListener, never()).onIsAdvertiserStartedChanged(anyBoolean());

        reset(mMockBluetoothLeAdvertiser);

        // repeat start
        assertThat("It should return true if advertising is already started",
                mBleAdvertiser.start(), is(true));

        verify(mMockBluetoothLeAdvertiser, never()).startAdvertising(
                Mockito.any(AdvertiseSettings.class), Mockito.any(AdvertiseData.class),
                Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseCallback.class));
    }

    @Test
    public void testStart_exception() throws Exception {

        doThrow(IllegalArgumentException.class).when(mMockBluetoothLeAdvertiser)
                .startAdvertising(mMockAdvertiseSettings,
                        mMockAdvertiseData, null, mBleAdvertiser);

        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        assertThat("It should return false as the advertising is not started",
                mBleAdvertiser.start(), is(false));

        assertThat("It should remain false as the advertiser is neither starting nor running",
                mBleAdvertiser.isStarted(), is(false));
    }

    @Test
    public void testStart_noAdvertiseData() throws Exception {

        assertThat("It should return false as the advertising is not started",
                mBleAdvertiser.start(), is(false));

        verify(mMockBluetoothLeAdvertiser, never()).startAdvertising(
                Mockito.any(AdvertiseSettings.class), Mockito.any(AdvertiseData.class),
                Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseCallback.class));

        assertThat("It should remain false as the advertiser is neither starting nor running",
                mBleAdvertiser.isStarted(), is(false));
    }

    @Test
    public void testStart_noBTAdvertiser() throws Exception {

        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(null);

        BleAdvertiser bleAdvertiser
                = new BleAdvertiser(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                null);

        assertThat("It should return false as the advertising is not started",
                bleAdvertiser.start(), is(false));

        verify(mMockBluetoothLeAdvertiser, never()).startAdvertising(
                Mockito.any(AdvertiseSettings.class), Mockito.any(AdvertiseData.class),
                Mockito.any(AdvertiseData.class), Mockito.any(AdvertiseCallback.class));

        assertThat("It should remain false as the advertiser is neither starting nor running",
                bleAdvertiser.isStarted(), is(false));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testStop_noBTAdvertiser() throws Exception {

        Field advertiserField = mBleAdvertiser.getClass().getDeclaredField("mBluetoothLeAdvertiser");
        advertiserField.setAccessible(true);
        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        mBleAdvertiser.start();

        // remove Advertiser
        advertiserField.set(mBleAdvertiser, null);

        mBleAdvertiser.stop(true);

        verify(mMockListener, times(1)).onIsAdvertiserStartedChanged(false);

        assertThat("It should be set to false as the advertiser is stopped",
                mBleAdvertiser.isStarted(), is(false));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testStop_notify() throws Exception {

        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        mBleAdvertiser.start();

        mBleAdvertiser.stop(true);

        verify(mMockBluetoothLeAdvertiser, times(1)).stopAdvertising(mBleAdvertiser);
        verify(mMockListener, times(1)).onIsAdvertiserStartedChanged(false);

        assertThat("It should be set to false as the advertiser is stopped",
                mBleAdvertiser.isStarted(), is(false));
    }

    @Test
    public void testStop_dontNotify() throws Exception {

        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        mBleAdvertiser.start();

        mBleAdvertiser.stop(false);

        verify(mMockBluetoothLeAdvertiser, times(1)).stopAdvertising(mBleAdvertiser);
        verify(mMockListener, never()).onIsAdvertiserStartedChanged(anyBoolean());

        assertThat("It should be set to false as the advertiser is stopped",
                mBleAdvertiser.isStarted(), is(false));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testOnStartFailure() throws Exception {
        mBleAdvertiser.setAdvertiseData(mMockAdvertiseData);
        mBleAdvertiser.start();

        // test
        mBleAdvertiser.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED);

        verify(mMockListener, times(1)).onIsAdvertiserStartedChanged(false);
        verify(mMockListener, times(1)).onAdvertiserFailedToStart(
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED);
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testOnStartSuccess() throws Exception {
        mBleAdvertiser.onStartSuccess(mMockAdvertiseSettings);
        verify(mMockListener, times(1)).onIsAdvertiserStartedChanged(true);
    }
}