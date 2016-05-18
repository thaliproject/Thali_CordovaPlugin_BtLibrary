package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.util.EnumSet;
import java.util.UUID;

import static java.lang.Long.toHexString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
/*
 Methods stopScanner(), startScanner,  startAdvertiser(), StopAdvertiser(),
 startScannerAndAdvertiser(), stopScannerAndAdvertiser(), startPeerAddressHelperAdvertiser()
 stopPeerAddressHelperAdvertiser() can't be unit tested because are using the android SDK.
 That means that are not available for unit testing by default.
 They have to be tested by instrumented tests
*/
public class BlePeerDiscovererTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    BlePeerDiscoverer.BlePeerDiscoveryListener mMockListener;
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    BleAdvertiser mMockBleAdvertiser;
    @Mock
    BleScanner mMockBleScanner;
    @Mock
    AdvertiseSettings.Builder mMockAdvertiseSettingsBuilder;
    @Mock
    ScanSettings.Builder mMockScanSettingsBuilder;
    @Mock
    AdvertiseSettings mMockAdvertiseSettings;
    @Mock
    ScanSettings mMockScanSettings;

    BlePeerDiscoverer blePeerDiscoverer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        UUID serviceUuid = new UUID(1, 1);
        UUID provideBluetoothMacAddressRequestUuid = new UUID(2, 2);
        String macAddress = "00:11:22:33:44:55";

        blePeerDiscoverer = new BlePeerDiscoverer(mMockListener, mMockBluetoothAdapter,
                serviceUuid, provideBluetoothMacAddressRequestUuid, macAddress,
                DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION,
                DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE,
                mMockBleAdvertiser, mMockBleScanner);
    }

    @Test
    public void testConstructor() throws Exception {
        UUID serviceUuid = new UUID(1, 1);
        UUID provideBluetoothMacAddressRequestUuid = new UUID(2, 2);
        String macAddress = "00:11:22:33:44:55";

        BlePeerDiscoverer bpd = new BlePeerDiscoverer(mMockListener, mMockBluetoothAdapter,
                serviceUuid, provideBluetoothMacAddressRequestUuid, macAddress,
                DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION,
                DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE,
                mMockBleAdvertiser, mMockBleScanner);

        assertThat("The BlePeerDiscoverer is properly created",
                bpd, is(notNullValue()));

        assertThat("The Bluetooth MAC address set to this instance is properly returned",
                bpd.getBluetoothMacAddress(), is(macAddress));

        assertThat("The initial state of this instance properly set and returned",
                bpd.getState(), is(EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED)));
    }

    @Test
    public void testConstructor_MacAddressUnknown() throws Exception {
        UUID serviceUuid = new UUID(1, 1);
        Long leastSigBits = 99999L;
        UUID macAddrRqUuid = new UUID(2, leastSigBits);
        String properRqn = "0000000" + toHexString(macAddrRqUuid.getLeastSignificantBits());
        String macAddress = PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN;

        BlePeerDiscoverer bpd = new BlePeerDiscoverer(mMockListener, mMockBluetoothAdapter,
                serviceUuid, macAddrRqUuid, macAddress,
                DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION,
                DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE,
                mMockBleAdvertiser, mMockBleScanner);

        assertThat("The BlePeerDiscoverer is properly created",
                bpd, is(notNullValue()));

        assertThat("The Bluetooth MAC address set to this instance is properly returned",
                bpd.getBluetoothMacAddress(), is(macAddress));

        assertThat("The initial state of this instance properly set and returned",
                bpd.getState(), is(EnumSet.of(BlePeerDiscoverer.BlePeerDiscovererStateSet.NOT_STARTED)));

        assertThat("The \"provide Bluetooth MAC address\" request ID generated by this instance",
                bpd.getProvideBluetoothMacAddressRequestId(), is(properRqn));

    }

    @Test
    public void testConstructor_exception() throws Exception {
        UUID serviceUuid = new UUID(1, 1);
        UUID provideBluetoothMacAddressRequestUuid = new UUID(2, 2);
        String macAddress = "00:11:22:33:44:55";

        thrown.expect(NullPointerException.class);
        new BlePeerDiscoverer(null, mMockBluetoothAdapter,
                serviceUuid, provideBluetoothMacAddressRequestUuid, macAddress,
                DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE,
                DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION,
                DiscoveryManagerSettings.DEFAULT_ADVERTISEMENT_DATA_TYPE,
                mMockBleAdvertiser, mMockBleScanner);
    }

    @Test
    public void testGenerateNewProvideBluetoothMacAddressRequestUuid() throws Exception {
        UUID serviceUuid = new UUID(123, 456);
        UUID genUUID = BlePeerDiscoverer.generateNewProvideBluetoothMacAddressRequestUuid(serviceUuid);

        assertThat("The most significant bits are equal",
                genUUID.getMostSignificantBits(),
                is(serviceUuid.getMostSignificantBits()));

        assertThat("The least significant bits are different",
                genUUID.getLeastSignificantBits(),
                is(not(serviceUuid.getLeastSignificantBits())));
    }

    @Test
    public void testSetBluetoothMacAddress() throws Exception {
        String validMacAddress = "00:11:22:33:44:55";
        String invalidMacAddress = "I:N:V:A:L:I:D";
        blePeerDiscoverer.setBluetoothMacAddress(validMacAddress);

        assertThat("The Bluetooth MAC address set to this instance is properly returned",
                blePeerDiscoverer.getBluetoothMacAddress(), is(validMacAddress));

        assertThat("The \"provide Bluetooth MAC address\" request ID is null",
                blePeerDiscoverer.getProvideBluetoothMacAddressRequestId(), is(nullValue()));

        blePeerDiscoverer.setBluetoothMacAddress(invalidMacAddress);

        assertThat("The Bluetooth MAC address set to this instance is properly returned",
                blePeerDiscoverer.getBluetoothMacAddress(), is(validMacAddress));
    }

    @Test
    public void testApplySettings() throws Exception {
        int manufacturerId = 77;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int beaconAdExtraInformation = PeerProperties.NO_EXTRA_INFORMATION;
        BlePeerDiscoverer.AdvertisementDataType advertisementDataType = BlePeerDiscoverer.AdvertisementDataType.DO_NOT_CARE;
        int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
        int advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
        int scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY;
        long scanReportDelayInMilliseconds = 10L;

        when(mMockBleAdvertiser.isStarted()).thenReturn(false);
        when(mMockBleScanner.isStarted()).thenReturn(false);
        when(mMockAdvertiseSettingsBuilder.build()).thenReturn(mMockAdvertiseSettings);
        when(mMockScanSettingsBuilder.build()).thenReturn(mMockScanSettings);

        boolean retVal = blePeerDiscoverer.applySettings(manufacturerId, beaconAdLengthAndType,
                beaconAdExtraInformation, advertisementDataType, advertiseMode, advertiseTxPowerLevel,
                scanMode, scanReportDelayInMilliseconds, mMockAdvertiseSettingsBuilder, mMockScanSettingsBuilder);

        verify(mMockAdvertiseSettingsBuilder, times(1)).setAdvertiseMode(advertiseMode);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setTxPowerLevel(advertiseTxPowerLevel);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setTimeout(0);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setConnectable(false);
        verify(mMockBleAdvertiser, times(1)).setAdvertiseSettings(mMockAdvertiseSettings);

        verify(mMockScanSettingsBuilder, times(1)).setScanMode(scanMode);
        verify(mMockScanSettingsBuilder, times(1)).setReportDelay(scanReportDelayInMilliseconds);
        verify(mMockBleScanner, times(1)).setScanSettings(mMockScanSettings);

        assertThat("it returns true as the settings for both the BLE advertiser and the scanner is set",
                retVal, is(true));
    }

    @Test
    public void testApplySettings_FailedApplyScanSettings() throws Exception {
        int manufacturerId = 77;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int beaconAdExtraInformation = PeerProperties.NO_EXTRA_INFORMATION;
        BlePeerDiscoverer.AdvertisementDataType advertisementDataType = BlePeerDiscoverer.AdvertisementDataType.DO_NOT_CARE;
        int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
        int advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
        int scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY;
        long scanReportDelayInMilliseconds = 10L;

        when(mMockAdvertiseSettingsBuilder.build()).thenReturn(mMockAdvertiseSettings);
        when(mMockScanSettingsBuilder.build()).thenReturn(mMockScanSettings);
        when(mMockScanSettingsBuilder.setScanMode(anyInt())).thenThrow(IllegalArgumentException.class);

        boolean retVal = blePeerDiscoverer.applySettings(manufacturerId, beaconAdLengthAndType,
                beaconAdExtraInformation, advertisementDataType, advertiseMode, advertiseTxPowerLevel,
                scanMode, scanReportDelayInMilliseconds, mMockAdvertiseSettingsBuilder, mMockScanSettingsBuilder);

        verify(mMockAdvertiseSettingsBuilder, times(1)).setAdvertiseMode(advertiseMode);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setTxPowerLevel(advertiseTxPowerLevel);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setTimeout(0);
        verify(mMockAdvertiseSettingsBuilder, times(1)).setConnectable(false);
        verify(mMockBleAdvertiser, times(1)).setAdvertiseSettings(mMockAdvertiseSettings);

        verify(mMockScanSettingsBuilder, never()).setReportDelay(anyLong());
        verify(mMockBleScanner, never()).setScanSettings(any(ScanSettings.class));

        assertThat("it returns false as failed to set the settings for the ScanSettings",
                retVal, is(false));
    }

    @Test
    public void testApplySettings_FailedApplyAdvertiserSettings() throws Exception {
        int manufacturerId = 77;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int beaconAdExtraInformation = PeerProperties.NO_EXTRA_INFORMATION;
        BlePeerDiscoverer.AdvertisementDataType advertisementDataType = BlePeerDiscoverer.AdvertisementDataType.DO_NOT_CARE;
        int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
        int advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
        int scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY;
        long scanReportDelayInMilliseconds = 10L;

        when(mMockAdvertiseSettingsBuilder.setAdvertiseMode(anyInt())).thenThrow(IllegalArgumentException.class);
        when(mMockAdvertiseSettingsBuilder.build()).thenReturn(mMockAdvertiseSettings);
        when(mMockScanSettingsBuilder.build()).thenReturn(mMockScanSettings);


        boolean retVal = blePeerDiscoverer.applySettings(manufacturerId, beaconAdLengthAndType,
                beaconAdExtraInformation, advertisementDataType, advertiseMode, advertiseTxPowerLevel,
                scanMode, scanReportDelayInMilliseconds, mMockAdvertiseSettingsBuilder, mMockScanSettingsBuilder);

        verify(mMockAdvertiseSettingsBuilder, never()).setTxPowerLevel(anyInt());
        verify(mMockAdvertiseSettingsBuilder, never()).setTimeout(anyInt());
        verify(mMockAdvertiseSettingsBuilder, never()).setConnectable(anyBoolean());
        verify(mMockBleAdvertiser, never()).setAdvertiseSettings(any(AdvertiseSettings.class));

        verify(mMockScanSettingsBuilder, times(1)).setScanMode(scanMode);
        verify(mMockScanSettingsBuilder, times(1)).setReportDelay(scanReportDelayInMilliseconds);
        verify(mMockBleScanner, times(1)).setScanSettings(mMockScanSettings);

        assertThat("it returns false as failed to set the settings for the BLE advertiser",
                retVal, is(false));
    }

}