package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.le.AdvertiseData;
import android.os.ParcelUuid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PeerAdvertisementFactoryTest {

    @Mock
    AdvertiseData.Builder mMockBuilder;
    @Mock
    ByteArrayOutputStream mMockByteArrayOutputStream;
    @Mock
    DataOutput mMockDataOutput;
    @Mock
    AdvertiseData mMockAdvertiseData;
    @Mock
    BlePeerDiscoveryUtils.ParsedAdvertisement mMockParsedAdvertisement;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreateAdvertiseDataToServiceData() throws Exception {
        int mostSigBits = 1;
        int leastSigBits = 2;

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String correctBluetoothMacAddress = "00:11:22:33:44:55";

        when(mMockBuilder.build()).thenReturn(mMockAdvertiseData);

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToServiceData(
                uuid, beaconAdExtraInformation, correctBluetoothMacAddress, mMockBuilder);

        verify(mMockBuilder, times(1)).setIncludeDeviceName(false);
        verify(mMockBuilder, times(1)).setIncludeTxPowerLevel(false);
        // This MUST NOT be set
        verify(mMockBuilder, never()).addManufacturerData(anyInt(), any(byte[].class));

        ArgumentCaptor<ParcelUuid> parcelUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        ArgumentCaptor<byte[]> serviceDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockBuilder, times(1)).addServiceData(parcelUuidCaptor.capture(), serviceDataCaptor.capture());

        // Verify the service data values
        // first is the beacon ad extra information
        assertEquals(BlePeerDiscoveryUtils.int8ToByte(beaconAdExtraInformation),
                serviceDataCaptor.getValue()[0]);
        // and the mac address
        assertEquals(0, serviceDataCaptor.getValue()[1]);
        assertEquals(17, serviceDataCaptor.getValue()[2]);
        assertEquals(34, serviceDataCaptor.getValue()[3]);
        assertEquals(51, serviceDataCaptor.getValue()[4]);
        assertEquals(68, serviceDataCaptor.getValue()[5]);
        assertEquals(85, serviceDataCaptor.getValue()[6]);

        assertThat("The newly created AdvertiseData instance should be properly returned",
                advertiseData, is(mMockAdvertiseData));
    }

    @Test
    public void testCreateAdvertiseDataToServiceData_wrongMacAddress() throws Exception {
        int mostSigBits = 1;
        int leastSigBits = 2;

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String incorrectBluetoothMacAddress = "Wr:on:gA:dd:re:ss";

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToServiceData(
                uuid, beaconAdExtraInformation, incorrectBluetoothMacAddress, mMockBuilder);

        verify(mMockBuilder, never()).build();

        assertThat("A null is returned in case of an incorrect MAC address",
                advertiseData, is(nullValue()));
    }

    @Test
    public void testCreateAdvertiseDataToServiceData_failedToAddManufacturerData() throws Exception {
        int mostSigBits = 1;
        int leastSigBits = 2;

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String correctBluetoothMacAddress = "00:11:22:33:44:55";

        when(mMockBuilder.build()).thenReturn(mMockAdvertiseData);

        doThrow(IllegalArgumentException.class).when(mMockBuilder)
                .addServiceData(any(ParcelUuid.class), any(byte[].class));

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToServiceData(
                uuid, beaconAdExtraInformation, correctBluetoothMacAddress, mMockBuilder);

        assertThat("A null is returned in case of a failure to add the manufacturer data",
                advertiseData, is(nullValue()));
    }

    @Test
    public void testCreateAdvertiseDataToManufacturerData() throws Exception {
        int manufacturerId = DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int mostSigBits = 1;
        int leastSigBits = 2;
        byte[] testByteArray = "bytes".getBytes();

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String correctBluetoothMacAddress = "00:11:22:33:44:55";

        when(mMockByteArrayOutputStream.toByteArray()).thenReturn(testByteArray);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseData);

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(
                manufacturerId, beaconAdLengthAndType, uuid, beaconAdExtraInformation,
                correctBluetoothMacAddress, mMockBuilder, mMockByteArrayOutputStream, mMockDataOutput);

        // Insert the UUID
        verify(mMockDataOutput, times(1)).writeLong(mostSigBits);
        verify(mMockDataOutput, times(1)).writeLong(leastSigBits);

        // Insert the Bluetooth address
        verify(mMockDataOutput, times(1)).writeByte(0);
        verify(mMockDataOutput, times(1)).writeByte(17);
        verify(mMockDataOutput, times(1)).writeByte(34);
        verify(mMockDataOutput, times(1)).writeByte(51);
        verify(mMockDataOutput, times(1)).writeByte(68);
        verify(mMockDataOutput, times(1)).writeByte(85);

        // check AdvertiseData passed to ManufacturerData
        verify(mMockBuilder, times(1)).addManufacturerData(manufacturerId, testByteArray);

        verify(mMockBuilder, times(1)).setIncludeDeviceName(false);
        verify(mMockBuilder, times(1)).setIncludeTxPowerLevel(false);

        assertThat("The newly created AdvertiseData instance should be properly returned",
                advertiseData, is(mMockAdvertiseData));
    }

    @Test
    public void testCreateAdvertiseDataToManufacturerData_failedToWriteToOutput() throws Exception {
        int manufacturerId = DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int mostSigBits = 1;
        int leastSigBits = 2;
        byte[] testByteArray = "bytes".getBytes();

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String correctBluetoothMacAddress = "00:11:22:33:44:55";

        when(mMockByteArrayOutputStream.toByteArray()).thenReturn(testByteArray);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseData);

        doThrow(IOException.class).when(mMockDataOutput).writeShort(anyByte());

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(
                manufacturerId, beaconAdLengthAndType, uuid, beaconAdExtraInformation,
                correctBluetoothMacAddress, mMockBuilder, mMockByteArrayOutputStream, mMockDataOutput);

        // check AdvertiseData passed to ManufacturerData
        verify(mMockBuilder, never()).addManufacturerData(anyInt(), any(byte[].class));

        verify(mMockBuilder, never()).setIncludeDeviceName(false);
        verify(mMockBuilder, never()).setIncludeTxPowerLevel(false);

        assertThat("A null is returned in case of a failure to write to the output stream",
                advertiseData, is(nullValue()));
    }

    @Test
    public void testCreateAdvertiseDataToManufacturerData_failedToAddManufacturerData() throws Exception {
        int manufacturerId = DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int mostSigBits = 1;
        int leastSigBits = 2;
        byte[] testByteArray = "bytes".getBytes();

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String correctBluetoothMacAddress = "00:11:22:33:44:55";

        when(mMockByteArrayOutputStream.toByteArray()).thenReturn(testByteArray);
        when(mMockBuilder.build()).thenReturn(mMockAdvertiseData);

        doThrow(IllegalArgumentException.class).when(mMockBuilder)
                .addManufacturerData(anyInt(), any(byte[].class));

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(
                manufacturerId, beaconAdLengthAndType, uuid, beaconAdExtraInformation,
                correctBluetoothMacAddress, mMockBuilder, mMockByteArrayOutputStream, mMockDataOutput);

        assertThat("A null is returned in case of a failure to add the manufacturer data",
                advertiseData, is(nullValue()));
    }

    @Test
    public void testCreateAdvertiseDataToManufacturerData_wrongMacAddress() throws Exception {
        int manufacturerId = DiscoveryManagerSettings.DEFAULT_MANUFACTURER_ID;
        int beaconAdLengthAndType = DiscoveryManagerSettings.DEFAULT_BEACON_AD_LENGTH_AND_TYPE;
        int mostSigBits = 1;
        int leastSigBits = 2;

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        int beaconAdExtraInformation = DiscoveryManagerSettings.DEFAULT_BEACON_AD_EXTRA_INFORMATION;
        String incorrectBluetoothMacAddress = "Wr:on:gA:dd:re:ss";

        AdvertiseData advertiseData = PeerAdvertisementFactory.createAdvertiseDataToManufacturerData(
                manufacturerId, beaconAdLengthAndType, uuid, beaconAdExtraInformation,
                incorrectBluetoothMacAddress, mMockBuilder, mMockByteArrayOutputStream, mMockDataOutput);

        verify(mMockBuilder, never()).addManufacturerData(anyInt(), any(byte[].class));
        verify(mMockBuilder, never()).setIncludeDeviceName(false);
        verify(mMockBuilder, never()).setIncludeTxPowerLevel(false);
        verify(mMockBuilder, never()).build();

        assertThat("A null is returned in case of an incorrect MAC address",
                advertiseData, is(nullValue()));
    }

    @Test
    public void testParsedAdvertisementToPeerProperties() throws Exception {
        mMockParsedAdvertisement.bluetoothMacAddress = "00:11:22:33:44:55";
        mMockParsedAdvertisement.extraInformation = 1;
        PeerProperties pp = PeerAdvertisementFactory
                .parsedAdvertisementToPeerProperties(mMockParsedAdvertisement);

        assertThat("The PeerProperties is properly prepared",
                pp, is(notNullValue()));

        assertThat("The extraInformation is properly updated",
                pp.getExtraInformation(), is(mMockParsedAdvertisement.extraInformation));

        assertThat("The bluetoothMacAddress is properly updated",
                pp.getBluetoothMacAddress(), is(mMockParsedAdvertisement.bluetoothMacAddress));
    }

    @Test
    public void testParsedAdvertisementToPeerProperties_noBTAddress() throws Exception {
        PeerProperties pp = PeerAdvertisementFactory
                .parsedAdvertisementToPeerProperties(mMockParsedAdvertisement);

        assertThat("The PeerProperties is null when no BT address provided",
                pp, is(nullValue()));
    }

    @Test
    public void testParsedAdvertisementToPeerProperties_null() throws Exception {
        PeerProperties pp = PeerAdvertisementFactory
                .parsedAdvertisementToPeerProperties(null);

        assertThat("The PeerProperties is null when null parsedAdvertisement provided",
                pp, is(nullValue()));
    }

    @Test
    public void testCreateProvideBluetoothMacAddressUuid() throws Exception {
        UUID serviceUuid = new UUID(1, 2);
        String requestId = "10";
        assertThat("The UUID is properly created", PeerAdvertisementFactory
                        .createProvideBluetoothMacAddressUuid(serviceUuid, requestId),
                is(new UUID(1, 16)));
    }

    @Test
    public void testCreateProvideBluetoothMacAddressUuid_nullServiceUuid() throws Exception {
        assertThat("The UUID is null when null serviceUuid provided",
                PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(null, "10"),
                is(nullValue()));
    }

    @Test
    public void testCreateProvideBluetoothMacAddressUuid_nullRequestIs() throws Exception {
        UUID serviceUuid = new UUID(1, 2);
        assertThat("The UUID is null when null requestId provided",
                PeerAdvertisementFactory.createProvideBluetoothMacAddressUuid(serviceUuid, null),
                is(nullValue()));
    }

    @Test
    public void testGenerateNewProvideBluetoothMacAddressRequestUuid() throws Exception {
        UUID serviceUuid = new UUID(1, 2);
        UUID genUUID = PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid(serviceUuid);
        assertThat("The most significant bits are equal",
                genUUID.getMostSignificantBits(),
                is(serviceUuid.getMostSignificantBits()));

        assertThat("The least significant bits are different",
                genUUID.getLeastSignificantBits(),
                is(not(serviceUuid.getLeastSignificantBits())));

        serviceUuid = new UUID(999999999, 999999999);
        genUUID = PeerAdvertisementFactory.generateNewProvideBluetoothMacAddressRequestUuid(serviceUuid);

        assertThat("The most significant bits are equal",
                genUUID.getMostSignificantBits(),
                is(serviceUuid.getMostSignificantBits()));

        assertThat("The least significant bits are different",
                genUUID.getLeastSignificantBits(),
                is(not(serviceUuid.getLeastSignificantBits())));
    }

    @Test
    public void testParseRequestIdFromUuid() throws Exception {
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 1)),
                is("000000000001"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 2)),
                is("000000000002"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 100)),
                is("000000000064"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 1000)),
                is("0000000003e8"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 10000)),
                is("000000002710"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 1000000)),
                is("0000000f4240"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 100000000)),
                is("000005f5e100"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 999999999)),
                is("00003b9ac9ff"));
        assertThat(PeerAdvertisementFactory.parseRequestIdFromUuid(new UUID(1, 99999999999901L)),
                is("5af3107a3f9d"));

    }
}