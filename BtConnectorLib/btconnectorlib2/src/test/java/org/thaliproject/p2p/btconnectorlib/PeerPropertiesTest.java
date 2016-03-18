package org.thaliproject.p2p.btconnectorlib;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PeerPropertiesTest {

    PeerProperties peerProperties;
    String mac = "0:0:0:0:0:0";
    String peerName = "Test peer name";
    String serviceType = "Service type";
    String deviceAddress = "192.168.0.1";
    String deviceName = "Test device name";

    @Before
    public void setUp() throws Exception {

        peerProperties = new PeerProperties(peerName, mac, serviceType,
                deviceAddress, deviceName);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void peerPropertiesConstructor() {
        PeerProperties pp = new PeerProperties();
        assertEquals(null, pp.getBluetoothMacAddress());
        assertEquals(null, pp.getName());
    }

    @Test
    public void peerPropertiesConstructorThatTakesAString() {
        PeerProperties pp = new PeerProperties(mac);
        assertEquals(mac, pp.getBluetoothMacAddress());
        assertEquals(PeerProperties.NO_PEER_NAME_STRING, pp.getName());
    }

    @Test
    public void peerPropertiesConstructorThatTakesTwoStrings() {
        PeerProperties pp = new PeerProperties(peerName, mac);
        assertEquals(mac, pp.getBluetoothMacAddress());
        assertEquals(peerName, pp.getName());
    }

    @Test
    public void peerPropertiesConstructorThatTakesFiveStrings() {
        PeerProperties pp = new PeerProperties(peerName, mac, serviceType,
                deviceAddress, deviceName);
        assertEquals(mac, pp.getBluetoothMacAddress());
        assertEquals(peerName, pp.getName());
        assertEquals(serviceType, pp.getServiceType());
        assertEquals(deviceAddress, pp.getDeviceAddress());
        assertEquals(deviceName, pp.getDeviceName());

    }

    @Test
    public void testGetId() {
        assertEquals(mac, peerProperties.getId());
    }

    @Test
    public void testGetName(){
        assertEquals(peerName, peerProperties.getName());
    }

    @Test
    public void testSetName() {
        String newName = "New Name";
        peerProperties.setName(newName);
        assertEquals(newName, peerProperties.getName());

    }

    @Test
    public void testGetBluetoothMacAddress() {
        assertEquals(mac, peerProperties.getBluetoothMacAddress());
    }

    @Test
    public void testSetBluetoothMacAddress() {
        String newMac = "11:22:33:44:55:66";
        peerProperties.setBluetoothMacAddress(newMac);
        assertEquals(newMac, peerProperties.getBluetoothMacAddress());
    }

    @Test
    public void testGetServiceType() {
        assertEquals(serviceType, peerProperties.getServiceType());
    }

    @Test
    public void testSetServiceType() {
        String newServiceType = "new service type";
        peerProperties.setServiceType(newServiceType);
        assertEquals(newServiceType, peerProperties.getServiceType());
    }

    @Test
    public void testGetDeviceName() {
        assertEquals(deviceName, peerProperties.getDeviceName());
    }

    @Test
    public void testSetDeviceName() {
        String newDeviceName = "new device name";
        peerProperties.setDeviceName(newDeviceName);
        assertEquals(newDeviceName, peerProperties.getDeviceName());
    }

    @Test
    public void testGetDeviceAddress() {
        assertEquals(deviceAddress, peerProperties.getDeviceAddress());
    }

    @Test
    public void testSetDeviceAddress() {
        String newDeviceAddress = "127.0.1";
        peerProperties.setDeviceAddress(newDeviceAddress);
        assertEquals(newDeviceAddress, peerProperties.getDeviceAddress());
    }

    @Test
    public void testCopyFrom() {
        PeerProperties pp = new PeerProperties();
        pp.copyFrom(peerProperties);
        assertEquals(mac, pp.getBluetoothMacAddress());
        assertEquals(peerName, pp.getName());
        assertEquals(serviceType, pp.getServiceType());
        assertEquals(deviceAddress, pp.getDeviceAddress());
        assertEquals(deviceName, pp.getDeviceName());
    }

    @Test
    public void testCopyFromNull() {
        PeerProperties pp = new PeerProperties();
        pp.copyFrom(null);
        assertEquals(null, pp.getBluetoothMacAddress());
        assertEquals(null, pp.getName());
        assertEquals(null, pp.getServiceType());
        assertEquals(null, pp.getDeviceAddress());
        assertEquals(null, pp.getDeviceName());
    }

    @Test
    public void testIsValid() {
        PeerProperties pp = new PeerProperties();
        assertEquals(false, pp.isValid());
        pp.setName("");
        assertEquals(false, pp.isValid());
        pp.setBluetoothMacAddress("");
        assertEquals(false, pp.isValid());
        pp.setName("Test");
        assertEquals(false, pp.isValid());
        pp.setBluetoothMacAddress("00:11:22:33:44:55");
        assertEquals(true, pp.isValid());
        pp.setName("");
        assertEquals(false, pp.isValid());
    }

    @Test
    public void testHasMoreInformation() {
        PeerProperties pp1 = new PeerProperties();
        PeerProperties pp2 = new PeerProperties();
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setName(PeerProperties.NO_PEER_NAME_STRING);
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setName("name1");
        assertEquals(true, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
        pp2.setName("name2");
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setBluetoothMacAddress("00:11:22:33:44:55");
        assertEquals(true, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
        pp2.setBluetoothMacAddress("00:11:22:33:44:66");
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setDeviceName("device1");
        assertEquals(true, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
        pp2.setDeviceName("device2");
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setDeviceAddress("127.0.0.1");
        assertEquals(true, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
        pp2.setDeviceAddress("127.0.0.2");
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));

        pp1.setServiceType("type1");
        assertEquals(true, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
        pp2.setServiceType("type2");
        assertEquals(false, pp1.hasMoreInformation(pp2));
        assertEquals(false, pp2.hasMoreInformation(pp1));
    }

    @Test
    public void testCopyMissingValuesFromOldPeer() throws Exception {

    }

    @Test
    public void testEquals() {
        // If the mac address is the same, objects are equal
        PeerProperties pp1 = new PeerProperties(mac);
        PeerProperties pp2 = new PeerProperties(mac);
        assertTrue(pp1.equals(pp2));
        PeerProperties pp3 = new PeerProperties();
        assertFalse(pp2.equals(pp3));
        pp3.setBluetoothMacAddress("00:00:00:00:00:00");
        assertFalse(pp1.equals(pp3));
        pp3.setBluetoothMacAddress(mac);
        assertTrue(pp1.equals(pp3));

    }

    @Test
    public void testToString() {
        assertEquals("[" + peerName + " " + mac + "]", peerProperties.toString());

    }
}