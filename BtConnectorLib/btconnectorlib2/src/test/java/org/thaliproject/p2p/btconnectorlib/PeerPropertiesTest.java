package org.thaliproject.p2p.btconnectorlib;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

@SmallTest
public class PeerPropertiesTest {

    private PeerProperties mPeerProperties;
    private String mac = "0:0:0:0:0:0";
    private String peerName = "Test peer name";
    private String serviceType = "Service type";
    private String deviceAddress = "192.168.0.1";
    private String deviceName = "Test device name";

    @Before
    public void setUp() throws Exception {

        mPeerProperties = new PeerProperties(peerName, mac, serviceType,
                deviceAddress, deviceName);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void peerPropertiesConstructor() {
        PeerProperties pp = new PeerProperties();
        assertThat(pp.getBluetoothMacAddress(), is(nullValue()));
        assertThat(pp.getName(), is(nullValue()));
    }

    @Test
    public void peerPropertiesConstructorThatTakesAString() {
        PeerProperties pp = new PeerProperties(mac);
        assertThat(pp.getName(), is(equalTo(PeerProperties.NO_PEER_NAME_STRING)));
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
    }

    @Test
    public void peerPropertiesConstructorThatTakesTwoStrings() {
        PeerProperties pp = new PeerProperties(peerName, mac);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
        assertThat(pp.getName(), is(equalTo(peerName)));
    }

    @Test
    public void peerPropertiesConstructorThatTakesFiveStrings() {
        PeerProperties pp = new PeerProperties(peerName, mac, serviceType,
                deviceAddress, deviceName);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
        assertThat(pp.getName(), is(equalTo(peerName)));
        assertThat(pp.getServiceType(), is(equalTo(serviceType)));
        assertThat(pp.getDeviceAddress(), is(equalTo(deviceAddress)));
        assertThat(pp.getDeviceName(), is(equalTo(deviceName)));
    }

    @Test
    public void testGetId() {
        assertThat(mPeerProperties.getId(), is(equalTo(mac)));
    }

    @Test
    public void testGetName() {
        assertThat(mPeerProperties.getName(), is(equalTo(peerName)));
    }

    @Test
    public void testSetName() {
        String newName = "New Name";
        mPeerProperties.setName(newName);
        assertThat(mPeerProperties.getName(), is(equalTo(newName)));
    }

    @Test
    public void testGetBluetoothMacAddress() {
        assertThat(mPeerProperties.getBluetoothMacAddress(), is(equalTo(mac)));
    }

    @Test
    public void testSetBluetoothMacAddress() {
        String newMac = "11:22:33:44:55:66";
        mPeerProperties.setBluetoothMacAddress(newMac);
        assertThat(mPeerProperties.getBluetoothMacAddress(), is(equalTo(newMac)));
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
        PeerProperties pp = new PeerProperties();
        pp.copyFrom(mPeerProperties);
        assertThat(pp.getBluetoothMacAddress(), is(equalTo(mac)));
        assertThat(pp.getDeviceAddress(), is(equalTo(deviceAddress)));
        assertThat(pp.getName(), is(equalTo(peerName)));
        assertThat(pp.getServiceType(), is(equalTo(serviceType)));
        assertThat(pp.getDeviceName(), is(equalTo(deviceName)));
    }

    @Test
    public void testCopyFromNull() {
        PeerProperties pp = new PeerProperties();
        pp.copyFrom(null);
        assertThat(pp.getBluetoothMacAddress(), is(nullValue()));
        assertThat(pp.getName(), is(nullValue()));
        assertThat(pp.getServiceType(), is(nullValue()));
        assertThat(pp.getDeviceAddress(), is(nullValue()));
        assertThat(pp.getDeviceName(), is(nullValue()));
    }

    @Test
    public void testIsValid() {
        PeerProperties pp = new PeerProperties();
        assertThat(pp.isValid(), is(false));
        pp.setName("");
        assertThat(pp.isValid(), is(false));
        pp.setBluetoothMacAddress("");
        assertThat(pp.isValid(), is(false));
        pp.setName("Test");
        assertThat(pp.isValid(), is(false));
        pp.setBluetoothMacAddress("00:11:22:33:44:55");
        assertThat(pp.isValid(), is(true));
        pp.setName("");
        assertThat(pp.isValid(), is(false));
    }

    @Test
    public void testHasMoreInformation() {
        PeerProperties pp1 = new PeerProperties();
        PeerProperties pp2 = new PeerProperties();
        assertThat(pp1.hasMoreInformation(pp2),is(false));
        assertThat(pp2.hasMoreInformation(pp1),is(false));

        pp1.setName(PeerProperties.NO_PEER_NAME_STRING);
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1.setName("name1");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2.setName("name2");
        assertThat(pp1.hasMoreInformation(pp2), is(false));
        assertThat(pp2.hasMoreInformation(pp1), is(false));

        pp1.setBluetoothMacAddress("00:11:22:33:44:55");
        assertThat(pp1.hasMoreInformation(pp2), is(true));
        assertThat(pp2.hasMoreInformation(pp1), is(false));
        pp2.setBluetoothMacAddress("00:11:22:33:44:66");
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
    public void testCopyMissingValuesFromOldPeer() throws Exception {

    }

    @Test
    public void testEquals() {
        // If the mac address is the same, objects are equal
        PeerProperties pp1 = new PeerProperties(mac);
        PeerProperties pp2 = new PeerProperties(mac);
        assertThat(pp1.equals(pp2), is(true));
        PeerProperties pp3 = new PeerProperties();
        assertThat(pp2.equals(pp3), is(false));
        pp3.setBluetoothMacAddress("00:00:00:00:00:00");
        assertThat(pp1.equals(pp3), is(false));
        pp3.setBluetoothMacAddress(mac);
        assertThat(pp1.equals(pp3), is(true));

    }

    @Test
    public void testToString() {
        assertThat(mPeerProperties.toString(), is(equalTo("[" + peerName + " " + mac + "]")));
    }
}