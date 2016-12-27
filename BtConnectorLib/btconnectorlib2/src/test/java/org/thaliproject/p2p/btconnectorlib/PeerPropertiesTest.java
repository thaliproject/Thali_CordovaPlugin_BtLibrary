package org.thaliproject.p2p.btconnectorlib;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class PeerPropertiesTest {

    private static final String TEST_MAC = "00:11:22:33:44:55";
    private static final int TEST_EXTRA_INFO = 12;
    private static final String SERVICE_TYPE = "Service type";
    private static final String DEVICE_ADDRESS = "192.168.0.1";
    private static final String DEVICE_NAME = "Test device name";

    private PeerProperties bluetoothPeerProperties;
    private PeerProperties wifiPeerProperties;
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        bluetoothPeerProperties = new PeerProperties(TEST_MAC, TEST_EXTRA_INFO);
        wifiPeerProperties = new PeerProperties(SERVICE_TYPE, DEVICE_NAME, DEVICE_ADDRESS);
    }

    @Test
    public void peerPropertiesMacAddressConstructor() {
        PeerProperties pp = new PeerProperties(TEST_MAC);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(TEST_MAC)));
    }

    @Test
    public void peerPropertiesMacAndExtraInfoConstructor() {
        PeerProperties pp = new PeerProperties(TEST_MAC, TEST_EXTRA_INFO);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(TEST_MAC)));
        assertThat(pp.getExtraInformation(), is(equalTo(TEST_EXTRA_INFO)));
        pp = new PeerProperties("", TEST_EXTRA_INFO);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)));
        assertThat(pp.getExtraInformation(), is(equalTo(TEST_EXTRA_INFO)));
        thrown.expect(IllegalArgumentException.class);
        new PeerProperties(TEST_MAC, PeerProperties.NO_EXTRA_INFORMATION);
    }

    @Test
    public void peerPropertiesConstructorThatTakesTypeAddressAndName() {
        PeerProperties pp = new PeerProperties(SERVICE_TYPE, DEVICE_NAME, DEVICE_ADDRESS);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)));
        assertThat(pp.getServiceType(), is(equalTo(SERVICE_TYPE)));
        assertThat(pp.getDeviceAddress(), is(equalTo(DEVICE_ADDRESS)));
        assertThat(pp.getDeviceName(), is(equalTo(DEVICE_NAME)));
    }

    @Test
    public void testGetId() {
        assertThat(bluetoothPeerProperties.getId(), is(equalTo(TEST_MAC)));
        assertThat(wifiPeerProperties.getId(), is(equalTo(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)));
    }

    @Test
    public void testGetBluetoothMacAddress() {
        assertThat(bluetoothPeerProperties.getBluetoothMacAddress(), is(equalTo(TEST_MAC)));
        assertThat(wifiPeerProperties.getId(), is(equalTo(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)));
    }

    @Test
    public void testGetServiceType() {
        assertThat(wifiPeerProperties.getServiceType(), is(equalTo(SERVICE_TYPE)));
        assertThat(bluetoothPeerProperties.getServiceType(), is(nullValue()));
    }

    @Test
    public void testGetDeviceName() {
        assertThat(wifiPeerProperties.getDeviceName(), is(equalTo(DEVICE_NAME)));
        assertThat(bluetoothPeerProperties.getDeviceName(), is(nullValue()));
    }

    @Test
    public void testGetDeviceAddress() {
        assertThat(wifiPeerProperties.getDeviceAddress(), is(equalTo(DEVICE_ADDRESS)));
        assertThat(bluetoothPeerProperties.getDeviceAddress(), is(nullValue()));
    }

    @Test
    public void testCopyFrom() {
        PeerProperties pp = new PeerProperties(TEST_MAC);
        pp.copyFrom(bluetoothPeerProperties);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(TEST_MAC)));
        assertThat(pp.getExtraInformation(), is(equalTo(TEST_EXTRA_INFO)));

        pp.copyFrom(wifiPeerProperties);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN)));
        assertThat(pp.getExtraInformation(), is(equalTo(PeerProperties.NO_EXTRA_INFORMATION)));
        assertThat(pp.getDeviceAddress(), is(equalTo(DEVICE_ADDRESS)));
        assertThat(pp.getServiceType(), is(equalTo(SERVICE_TYPE)));
        assertThat(pp.getDeviceName(), is(equalTo(DEVICE_NAME)));
    }

    @Test
    public void testCopyFromNull() {
        PeerProperties pp = new PeerProperties(null);
        pp.copyFrom(null);
        assertThat(pp.getBluetoothMacAddress(), is(PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN));
        assertThat(pp.getServiceType(), is(nullValue()));
        assertThat(pp.getDeviceAddress(), is(nullValue()));
        assertThat(pp.getDeviceName(), is(nullValue()));
    }

    @Test
    public void testIsValid() {
        PeerProperties pp = new PeerProperties(TEST_MAC);
        assertThat(pp.isValid(), is(false));
        pp = new PeerProperties(TEST_MAC, TEST_EXTRA_INFO);
        assertThat(pp.isValid(), is(true));
        thrown.expect(IllegalArgumentException.class);
        new PeerProperties(TEST_MAC, PeerProperties.NO_EXTRA_INFORMATION);
    }

    @Test
    public void testHasMoreInformation() {
        PeerProperties pp1 = new PeerProperties(TEST_MAC);
        PeerProperties pp2 = new PeerProperties(TEST_MAC);
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1 = new PeerProperties(TEST_MAC, TEST_EXTRA_INFO);
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2 = new PeerProperties(TEST_MAC, TEST_EXTRA_INFO);
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1 = new PeerProperties("type1", "deviceName1", "address1");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2 = new PeerProperties("type2", "deviceName2", "address2");
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
    }

    @Test
    public void testCopyMissingValuesFromOldPeer() {
        PeerProperties pp1 = new PeerProperties(TEST_MAC);
        PeerProperties pp2 = new PeerProperties(TEST_MAC);
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1 = new PeerProperties("type1", "deviceName1", "address1");
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(true));
        assertThat(pp1.hasMoreInformation(pp2), is(false));
    }

    @Test
    public void testEquals() {
        // If the mac address is the same, objects are equal
        PeerProperties pp1 = new PeerProperties(TEST_MAC);
        PeerProperties pp2 = new PeerProperties(TEST_MAC);
        assertThat(pp1.equals(pp2), is(true));
        PeerProperties pp3 = new PeerProperties("1:2:3");
        assertThat(pp2.equals(pp3), is(false));
    }

    @Test
    public void testToString() {
        assertThat(bluetoothPeerProperties.toString(), is(equalTo("[" + TEST_MAC + " " + TEST_EXTRA_INFO + "]")));
        assertThat(new PeerProperties(TEST_MAC).toString(), is(equalTo("[" + TEST_MAC + "]")));
    }
}