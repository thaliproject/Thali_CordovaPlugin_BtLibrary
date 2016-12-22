package org.thaliproject.p2p.btconnectorlib;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
    private PeerProperties mPeerProperties;
    private String mac = "0:0:0:0:0:0";
    private String serviceType = "Service type";
    private String deviceAddress = "192.168.0.1";
    private String deviceName = "Test device name";

    @Before
    public void setUp() throws Exception {
        mPeerProperties = new PeerProperties(mac, serviceType, deviceAddress, deviceName);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void peerPropertiesConstructorThatTakesAString() {
        PeerProperties pp = new PeerProperties(mac);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
    }

    @Test
    public void peerPropertiesConstructorThatTakesTwoStrings() {
        PeerProperties pp = new PeerProperties(mac);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
    }

    @Test
    public void peerPropertiesConstructorThatTakesFiveStrings() {
        PeerProperties pp = new PeerProperties(mac, serviceType,
                deviceAddress, deviceName);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
        assertThat(pp.getServiceType(), is(equalTo(serviceType)));
        assertThat(pp.getDeviceAddress(), is(equalTo(deviceAddress)));
        assertThat(pp.getDeviceName(), is(equalTo(deviceName)));
    }

    @Test
    public void testGetId() {
        assertThat(mPeerProperties.getId(), is(equalTo(mac)));
    }

    @Test
    public void testGetBluetoothMacAddress() {
        assertThat(mPeerProperties.getBluetoothMacAddress(), is(equalTo(mac)));
    }

    @Test
    public void testGetServiceType() {
        assertThat(mPeerProperties.getServiceType(), is(equalTo(serviceType)));
    }

    @Test
    public void testSetServiceType() {
        String newServiceType = "new service type";
        mPeerProperties.setServiceType(newServiceType);
        assertThat(mPeerProperties.getServiceType(), is(equalTo(newServiceType)));
    }

    @Test
    public void testGetDeviceName() {
        assertThat(mPeerProperties.getDeviceName(), is(equalTo(deviceName)));
    }

    @Test
    public void testSetDeviceName() {
        String newDeviceName = "new device name";
        mPeerProperties.setDeviceName(newDeviceName);
        assertThat(mPeerProperties.getDeviceName(), is(equalTo(newDeviceName)));
    }

    @Test
    public void testGetDeviceAddress() {
        assertThat(mPeerProperties.getDeviceAddress(), is(equalTo(deviceAddress)));
    }

    @Test
    public void testSetDeviceAddress() {
        String newDeviceAddress = "127.0.1";
        mPeerProperties.setDeviceAddress(newDeviceAddress);
        assertThat(mPeerProperties.getDeviceAddress(), is(equalTo(newDeviceAddress)));
    }

    @Test
    public void testCopyFrom() {
        PeerProperties pp = new PeerProperties(TEST_MAC);
        pp.copyFrom(mPeerProperties);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
        assertThat(pp.getDeviceAddress(), is(equalTo(deviceAddress)));
        assertThat(pp.getServiceType(), is(equalTo(serviceType)));
        assertThat(pp.getDeviceName(), is(equalTo(deviceName)));
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
        PeerProperties pp = new PeerProperties(mac);
        assertThat(pp.isValid(), is(false));
        pp = new PeerProperties(mac, 12);
        assertThat(pp.isValid(), is(true));
        pp = new PeerProperties(mac, PeerProperties.NO_EXTRA_INFORMATION);
        assertThat(pp.isValid(), is(false));
    }

    @Test
    public void testHasMoreInformation() {
        PeerProperties pp1 = new PeerProperties(TEST_MAC);
        PeerProperties pp2 = new PeerProperties(TEST_MAC);
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1.setDeviceName("device1");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2.setDeviceName("device2");
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1.setDeviceAddress("127.0.0.1");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2.setDeviceAddress("127.0.0.2");
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));


        pp1.setServiceType("type1");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2.setServiceType("type2");
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
    }

    @Test
    public void testCopyMissingValuesFromOldPeer() {
        PeerProperties pp1 = new PeerProperties(TEST_MAC);
        PeerProperties pp2 = new PeerProperties(TEST_MAC);
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1.setDeviceName("device1");
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(true));
        assertThat(pp1.hasMoreInformation(pp2), is(false));

        pp1.setDeviceAddress("127.0.0.1");
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(true));
        assertThat(pp1.hasMoreInformation(pp2), is(false));

        pp1.setServiceType("type1");
        assertThat(PeerProperties.copyMissingValuesFromOldPeer(pp1, pp2), is(true));
        assertThat(pp1.hasMoreInformation(pp2), is(false));
    }

    @Test
    public void testEquals() {
        // If the mac address is the same, objects are equal
        PeerProperties pp1 = new PeerProperties(mac);
        PeerProperties pp2 = new PeerProperties(mac);
        assertThat(pp1.equals(pp2), is(true));
        PeerProperties pp3 = new PeerProperties(TEST_MAC);
        assertThat(pp2.equals(pp3), is(false));
    }

    @Test
    public void testToString() {
        assertThat(mPeerProperties.toString(), is(equalTo("[" + mac + "]")));
    }
}