package org.thaliproject.p2p.btconnectorlib.internal;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothDeviceDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BlePeerDiscoverer;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le.BluetoothGattManager;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BluetoothMacAddressResolutionHelperTest {

    @Mock
    Context mMockContext;

    @Mock
    SharedPreferences mMockSharedPreferences;

    @Mock
    BluetoothManager mMockBluetoothManager;

    @Mock
    BluetoothAdapter mMockBluetoothAdapter;

    @Mock
    DiscoveryManager mMockDiscoveryManager;

    @Mock
    BlePeerDiscoverer mMockBlePeerDiscoverer;

    @Mock
    BluetoothDeviceDiscoverer mMockBluetoothDeviceDiscoverer;

    @Mock
    CountDownTimer mMockCountDownTimer;

    @Mock
    BluetoothGattManager mMockBluetoothGattManager;

    @Mock
    DiscoveryManagerSettings mMockDiscoveryManagerSettings;

    @Mock
    BluetoothDevice mMockBluetoothDevice;


    BluetoothMacAddressResolutionHelper mBluetoothMacAddressResolutionHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDiscoveryManager.getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress())
                .thenReturn(mMockBlePeerDiscoverer);

        mBluetoothMacAddressResolutionHelper = new BluetoothMacAddressResolutionHelper(
                mMockContext, mMockBluetoothAdapter, mMockDiscoveryManager, new UUID(1, 1),
                new UUID(1, 1), mMockSharedPreferences);

    }

    @Test
    public void bluetoothMacAddressResolutionHelperConstructor() throws Exception {

        UUID macAddressUUID = new UUID(1, 1);
        BluetoothMacAddressResolutionHelper bmaHelper = new BluetoothMacAddressResolutionHelper(
                mMockContext, mMockBluetoothAdapter, mMockDiscoveryManager, new UUID(1, 1),
                macAddressUUID, mMockSharedPreferences);

        assertThat("The BluetoothMacAddressResolutionHelper is properly created",
                bmaHelper, is(notNullValue()));

        assertThat("The it properly returns MacAddressRequestUuid",
                bmaHelper.getProvideBluetoothMacAddressRequestUuid(), is(macAddressUUID));
    }

    @Test
    public void testGetIsProvideBluetoothMacAddressModeStarted() throws Exception {
        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        field.setAccessible(true);

        // default value
        assertThat("The default value of the \"Provide Bluetooth MAC address\" mode is false",
                mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted(),
                is(false));

        field.set(mBluetoothMacAddressResolutionHelper, true);

        assertThat("The value of the \"Provide Bluetooth MAC address\" mode is properly returned",
                mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted(),
                is(true));
    }

    @Test
    public void testGetIsBluetoothMacAddressGattServerStarted() throws Exception {

        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(true);

        assertThat("Is true if a Bluetooth MAC address request (GATT) service has been added",
                mBluetoothMacAddressResolutionHelper.getIsBluetoothMacAddressGattServerStarted(),
                is(true));

        verify(mMockBluetoothGattManager, times(1)).getIsBluetoothMacAddressRequestServerStarted();

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(false);

        assertThat("Is false if a Bluetooth MAC address request (GATT) service not added",
                mBluetoothMacAddressResolutionHelper.getIsBluetoothMacAddressGattServerStarted(),
                is(false));
    }

    @Test
    public void testGetIsReceiveBluetoothMacAddressModeStarted() throws Exception {
        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsReceiveBluetoothMacAddressModeStarted");
        field.setAccessible(true);

        // default value
        assertThat("The default value of the \"Receive Bluetooth MAC address\" mode is false",
                mBluetoothMacAddressResolutionHelper.getIsReceiveBluetoothMacAddressModeStarted(),
                is(false));

        field.set(mBluetoothMacAddressResolutionHelper, true);

        assertThat("The value of the \"Receive Bluetooth MAC address\" mode is properly returned",
                mBluetoothMacAddressResolutionHelper.getIsReceiveBluetoothMacAddressModeStarted(),
                is(true));
    }

    @Test
    public void testStopAllBluetoothMacAddressResolutionOperations() throws Exception {

        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mReceiveBluetoothMacAddressTimeoutTimer");
        field.setAccessible(true);
        field.set(mBluetoothMacAddressResolutionHelper, mMockCountDownTimer);

        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        mBluetoothMacAddressResolutionHelper.stopAllBluetoothMacAddressResolutionOperations();

        verify(mMockCountDownTimer, times(1)).cancel();

        assertThat("Is set to null if receive bt mac address properly stopped",
                field.get(mBluetoothMacAddressResolutionHelper), is(nullValue()));

        verify(mMockBluetoothGattManager, times(1)).stopBluetoothMacAddressRequestServer();
        verify(mMockBluetoothGattManager, times(1)).clearBluetoothGattClientOperationQueue();
        verify(mMockDiscoveryManager, times(1)).
                onProvideBluetoothMacAddressModeStartedChanged(false);
        verify(mMockBluetoothDeviceDiscoverer, atLeastOnce()).stop();
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_Success() throws Exception {
        String reqId = "requestID";
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, false);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        when(mMockBluetoothDeviceDiscoverer.isRunning()).thenReturn(true);

        // used to instantiate BluetoothDeviceDiscoverer
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);

        doReturn(true).when(mMockBlePeerDiscoverer)
                .startPeerAddressHelperAdvertiser(anyString(),
                        anyString(),
                        anyLong());

        assertThat("is True, if the \"Provide Bluetooth MAC address\" mode was started successfully",
                mBluetoothMacAddressResolutionHelper.startProvideBluetoothMacAddressMode(reqId),
                is(true));

        // check if stopProvideBluetoothMacAddressMode() was properly executed
        verify(mMockDiscoveryManager, times(1)).
                onProvideBluetoothMacAddressModeStartedChanged(true);
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_NotSupported() throws Exception {
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, true);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(false);

        // assertions
        testStartProvideBluetoothMacAddressModeFailures("testId");
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_AlreadyStarted() throws Exception {
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, true);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        // assertions
        testStartProvideBluetoothMacAddressModeFailures("testId");
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_InvalidRequestID() throws Exception {
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, false);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        // assertions
        testStartProvideBluetoothMacAddressModeFailures("");
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_FailedToStartBluetooth() throws Exception {
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, false);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.SCANNING);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        // assertions
        testStartProvideBluetoothMacAddressModeFailures("requestID");
    }

    @Test
    public void testStartProvideBluetoothMacAddressMode_FailedToStartAdvertising() throws Exception {
        String reqId = "requestID";
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldBTDiscoverer = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        fieldBTDiscoverer.setAccessible(true);
        fieldBTDiscoverer.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        Field fieldReqID = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mCurrentProvideBluetoothMacAddressRequestId");
        fieldReqID.setAccessible(true);
        fieldReqID.set(mBluetoothMacAddressResolutionHelper, "dummyReqID");

        Field fieldIsProvideBTModeStarted = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mIsProvideBluetoothMacAddressModeStarted");
        fieldIsProvideBTModeStarted.setAccessible(true);
        fieldIsProvideBTModeStarted.set(mBluetoothMacAddressResolutionHelper, false);

        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        when(mMockBluetoothDeviceDiscoverer.isRunning()).thenReturn(true);

        // used to instantiate BluetoothDeviceDiscoverer
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);

        doReturn(false).when(mMockBlePeerDiscoverer)
                .startPeerAddressHelperAdvertiser(anyString(),
                        anyString(),
                        anyLong());
        // assertions
        testStartProvideBluetoothMacAddressModeFailures(reqId);
    }

    // The method below is used by the testStartProvideBluetoothMacAddressMode in failure cases.
    // It checks also execution of stopProvideBluetoothMacAddressMode(),
    // getCurrentProvideBluetoothMacAddressRequestId() and getCurrentProvideBluetoothMacAddressRequestId()
    private void testStartProvideBluetoothMacAddressModeFailures(String reqId) throws Exception {

        // The test
        assertThat("is False, if the \"Provide Bluetooth MAC address\" mode was not started",
                mBluetoothMacAddressResolutionHelper.startProvideBluetoothMacAddressMode(reqId),
                is(false));

        // check if stopProvideBluetoothMacAddressMode() was properly executed
        verify(mMockDiscoveryManager, times(1)).
                onProvideBluetoothMacAddressModeStartedChanged(false);
        verify(mMockBluetoothGattManager, atLeastOnce()).clearBluetoothGattClientOperationQueue();
        verify(mMockBluetoothDeviceDiscoverer, atLeastOnce()).stop();

        // test getCurrentProvideBluetoothMacAddressRequestId()
        assertThat("The request ID of the current request is set to null",
                mBluetoothMacAddressResolutionHelper.getCurrentProvideBluetoothMacAddressRequestId(),
                is(nullValue()));

        // test getIsProvideBluetoothMacAddressModeStarted()
        assertThat("The \"Provide Bluetooth MAC address\" mode is started is set to false",
                mBluetoothMacAddressResolutionHelper.getIsProvideBluetoothMacAddressModeStarted(),
                is(false));
    }

    // The test below tests if the provideBluetoothMacAddressToDevice is properly executed.
    // To do so, first we have to start the "Provide Bluetooth MAC address"
    @Test
    public void testProvideBluetoothMacAddressToDevice() throws Exception {
        String reqId = "testId";
        Long provideBtAddrTimeout = 1000L;

        Field fieldSettings = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mSettings");
        fieldSettings.setAccessible(true);
        fieldSettings.set(mBluetoothMacAddressResolutionHelper, mMockDiscoveryManagerSettings);

        when(mMockDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout())
                .thenReturn(provideBtAddrTimeout);


        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        // used to instantiate BluetoothDeviceDiscoverer
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);
        when(mMockDiscoveryManager.isBleMultipleAdvertisementSupported()).thenReturn(true);
        when(mMockBlePeerDiscoverer.startPeerAddressHelperAdvertiser(
                reqId, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN, provideBtAddrTimeout
        )).thenReturn(true);

        mBluetoothMacAddressResolutionHelper.startProvideBluetoothMacAddressMode(reqId);

        // actual test
        mBluetoothMacAddressResolutionHelper.provideBluetoothMacAddressToDevice(mMockBluetoothDevice);

        verify(mMockBlePeerDiscoverer, times(1)).startPeerAddressHelperAdvertiser(
                reqId, PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN, provideBtAddrTimeout);
        verify(mMockDiscoveryManager, times(1)).onProvideBluetoothMacAddressModeStartedChanged(true);
        verify(mMockBluetoothGattManager, times(1)).provideBluetoothMacAddressToDevice(
                mMockBluetoothDevice, reqId);
    }

    @Test
    public void testStartReceiveBluetoothMacAddressMode_1() throws Exception {
        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mReceiveBluetoothMacAddressTimeoutTimer");
        field.setAccessible(true);
        field.set(mBluetoothMacAddressResolutionHelper, mMockCountDownTimer);

        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldSettings = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mSettings");
        fieldSettings.setAccessible(true);
        fieldSettings.set(mBluetoothMacAddressResolutionHelper, mMockDiscoveryManagerSettings);

        when(mMockDiscoveryManagerSettings.getAutomateBluetoothMacAddressResolution())
                .thenReturn(true);

        when(mMockDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout())
                .thenReturn(0L);

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(false);

        mBluetoothMacAddressResolutionHelper.startReceiveBluetoothMacAddressMode("1");

        verify(mMockDiscoveryManager, times(1)).makeDeviceDiscoverable(
                DiscoveryManagerSettings.DEFAULT_DEVICE_DISCOVERABLE_DURATION_IN_SECONDS);
        verify(mMockCountDownTimer, times(1)).cancel();
        verify(mMockBluetoothGattManager, times(1)).startBluetoothMacAddressRequestServer("1");

        assertThat("mReceiveBluetoothMacAddressTimeoutTimer countdown timer is properly set",
                field.get(mBluetoothMacAddressResolutionHelper), is(notNullValue()));
    }

    @Test
    public void testStartReceiveBluetoothMacAddressMode_2() throws Exception {
        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mReceiveBluetoothMacAddressTimeoutTimer");
        field.setAccessible(true);
        field.set(mBluetoothMacAddressResolutionHelper, mMockCountDownTimer);

        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        Field fieldSettings = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mSettings");
        fieldSettings.setAccessible(true);
        fieldSettings.set(mBluetoothMacAddressResolutionHelper, mMockDiscoveryManagerSettings);

        when(mMockDiscoveryManagerSettings.getAutomateBluetoothMacAddressResolution())
                .thenReturn(true);

        when(mMockDiscoveryManagerSettings.getProvideBluetoothMacAddressTimeout())
                .thenReturn(4000L);

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(false);

        mBluetoothMacAddressResolutionHelper.startReceiveBluetoothMacAddressMode("1");

        verify(mMockDiscoveryManager, times(1)).makeDeviceDiscoverable(4);
        verify(mMockCountDownTimer, times(1)).cancel();
        verify(mMockBluetoothGattManager, times(1)).startBluetoothMacAddressRequestServer("1");


        assertThat("mReceiveBluetoothMacAddressTimeoutTimer countdown timer is properly set",
                field.get(mBluetoothMacAddressResolutionHelper), is(notNullValue()));
    }

    @Test
    public void testStopReceiveBluetoothMacAddressMode() throws Exception {

        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mReceiveBluetoothMacAddressTimeoutTimer");
        field.setAccessible(true);
        field.set(mBluetoothMacAddressResolutionHelper, mMockCountDownTimer);

        mBluetoothMacAddressResolutionHelper.stopReceiveBluetoothMacAddressMode();

        verify(mMockCountDownTimer, times(1)).cancel();

        assertThat("Is set to null if receive bt mac address properly stopped",
                field.get(mBluetoothMacAddressResolutionHelper), is(nullValue()));
    }

    @Test
    public void testStartBluetoothMacAddressGattServer() throws Exception {
        String rqId = "rqId";
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(true);
        mBluetoothMacAddressResolutionHelper.startBluetoothMacAddressGattServer(rqId);
        verify(mMockBluetoothGattManager, never()).startBluetoothMacAddressRequestServer(anyString());

        when(mMockBluetoothGattManager.getIsBluetoothMacAddressRequestServerStarted())
                .thenReturn(false);

        mBluetoothMacAddressResolutionHelper.startBluetoothMacAddressGattServer(rqId);
        verify(mMockBluetoothGattManager, times(1)).startBluetoothMacAddressRequestServer(rqId);
    }

    @Test
    public void testStartBluetoothDeviceDiscovery_success() throws Exception {

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        // used to instantiate BluetoothDeviceDiscoverer
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);

        assertThat("Returns true if bt discovery is started",
                mBluetoothMacAddressResolutionHelper.startBluetoothDeviceDiscovery(),
                is(equalTo(true)));
        verify(mMockDiscoveryManager, times(1))
                .getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        verify(mMockBlePeerDiscoverer, times(1)).stopScanner();

        // now as the BluetoothDeviceDiscoverer.mBroadcastReceiver is instantiated
        // we can test mBluetoothDeviceDiscoverer.isRunning()
        assertThat("Returns true if bt discovery is started",
                mBluetoothMacAddressResolutionHelper.startBluetoothDeviceDiscovery(),
                is(equalTo(true)));

        verify(mMockDiscoveryManager, times(2)).
                getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        verify(mMockBlePeerDiscoverer, times(2)).stopScanner();
    }

    @Test
    public void testStartBluetoothDeviceDiscovery_failure() throws Exception {

        EnumSet<BlePeerDiscoverer.BlePeerDiscovererStateSet> mStateSet
                = EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED);
        when(mMockBlePeerDiscoverer.getState()).thenReturn(mStateSet);

        // used to instantiate BluetoothDeviceDiscoverer
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(false);

        assertThat("Returns false if bt discovery is not started properly",
                mBluetoothMacAddressResolutionHelper.startBluetoothDeviceDiscovery(),
                is(equalTo(false)));
        verify(mMockDiscoveryManager, times(1)).
                getBlePeerDiscovererInstanceAndCheckBluetoothMacAddress();
        verify(mMockBlePeerDiscoverer, times(1)).stopScanner();
    }

    @Test
    public void testStopBluetoothDeviceDiscovery() throws Exception {

        assertThat("Returns false if bt discovery is null",
                mBluetoothMacAddressResolutionHelper.stopBluetoothDeviceDiscovery(), is(equalTo(false)));

        Field field = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothDeviceDiscoverer");
        field.setAccessible(true);
        field.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothDeviceDiscoverer);

        assertThat("Returns true if bt discovery properly stopped",
                mBluetoothMacAddressResolutionHelper.stopBluetoothDeviceDiscovery(), is(equalTo(true)));

        verify(mMockBluetoothDeviceDiscoverer, times(1)).stop();

        assertThat("Is set to null if bt discovery properly stopped",
                field.get(mBluetoothMacAddressResolutionHelper), is(nullValue()));
    }

    @Test
    public void testOnBluetoothDeviceDiscovered() throws Exception {
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        mBluetoothMacAddressResolutionHelper.onBluetoothDeviceDiscovered(mMockBluetoothDevice);

        verify(mMockBluetoothDevice, times(1)).getAddress();
        verify(mMockBluetoothGattManager, times(1))
                .provideBluetoothMacAddressToDevice((BluetoothDevice) anyObject(), anyString());

    }

    @Test
    public void testOnProvideBluetoothMacAddressResult() throws Exception {
        String rqId = "rqId";
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        mBluetoothMacAddressResolutionHelper.onProvideBluetoothMacAddressResult(rqId, true);

        verify(mMockDiscoveryManager, times(1)).onProvideBluetoothMacAddressResult(rqId, true);
        // called in stopProvideBluetoothMacAddressMode()
        verify(mMockDiscoveryManager, times(1)).onProvideBluetoothMacAddressModeStartedChanged(false);
        verify(mMockBluetoothGattManager, times(1)).clearBluetoothGattClientOperationQueue();
    }

    @Test
    public void testOnBluetoothMacAddressResolved() throws Exception {
        String btMacAddress = "00:01:02:03:04:05:06";
        Field fieldGattManager = mBluetoothMacAddressResolutionHelper.getClass()
                .getDeclaredField("mBluetoothGattManager");
        fieldGattManager.setAccessible(true);
        fieldGattManager.set(mBluetoothMacAddressResolutionHelper, mMockBluetoothGattManager);

        mBluetoothMacAddressResolutionHelper.onBluetoothMacAddressResolved(btMacAddress);
        verify(mMockDiscoveryManager, times(1)).onBluetoothMacAddressResolved(btMacAddress);
        verify(mMockBluetoothGattManager, times(1)).stopBluetoothMacAddressRequestServer();
    }
}