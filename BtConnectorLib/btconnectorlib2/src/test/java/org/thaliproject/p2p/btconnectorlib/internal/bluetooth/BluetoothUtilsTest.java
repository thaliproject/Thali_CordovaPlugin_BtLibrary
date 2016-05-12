package org.thaliproject.p2p.btconnectorlib.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class BluetoothUtilsTest {

    @Mock
    BluetoothSocket mMockBluetoothSocket;

    @Mock
    BluetoothDevice mMockBluetoothDevice;

    @Mock
    PeerProperties mMockPeerProperties;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(mMockBluetoothDevice);
    }

    @Test
    public void testIsBluetoothMacAddressUnknown() throws Exception {

        assertThat("The BT MAC address is unknown if null is provided",
                BluetoothUtils.isBluetoothMacAddressUnknown(null), is(true));

        assertThat("The Bt MAC address is unknown if MARSHMALLOW_FAKE_MAC_ADDRESS provided",
                BluetoothUtils.isBluetoothMacAddressUnknown("02:00:00:00:00:00"), is(true));

        assertThat("The Bt MAC address is unknown if BLUETOOTH_MAC_ADDRESS_UNKNOWN provided",
                BluetoothUtils.isBluetoothMacAddressUnknown(
                        PeerProperties.BLUETOOTH_MAC_ADDRESS_UNKNOWN), is(true));

        assertThat("The Bt MAC address is known if proper address provided",
                BluetoothUtils.isBluetoothMacAddressUnknown("01:02:03:04:05:06"), is(false));
    }

    @Test
    public void testIsValidBluetoothMacAddress() throws Exception {
        assertThat("The BT MAC is not valid if null is provided",
                BluetoothUtils.isValidBluetoothMacAddress(null), is(false));

        assertThat("The BT MAC is not valid if empty string is provided",
                BluetoothUtils.isValidBluetoothMacAddress(""), is(false));

        assertThat("The BT MAC is not valid if not hexadecimal digits provided",
                BluetoothUtils.isValidBluetoothMacAddress("ff:gg:hh:ff:gg:hh"), is(false));

        assertThat("The BT MAC is not valid if five groups of two hexadecimal digits used",
                BluetoothUtils.isValidBluetoothMacAddress("00:11:22:33:44"), is(false));

        assertThat("The BT MAC is not valid if seven groups of two hexadecimal digits used",
                BluetoothUtils.isValidBluetoothMacAddress("00:11:22:33:44:55:66"), is(false));

        assertThat("The BT MAC is not valid if six groups of two hexadecimal digits not" +
                        " separated by colons used",
                BluetoothUtils.isValidBluetoothMacAddress("00:11:22:33:44:55:66"), is(false));

        assertThat("The BT MAC is not valid if lower case chars used",
                BluetoothUtils.isValidBluetoothMacAddress("0a:1b:2c:3d:4e:5f"), is(false));

        assertThat("The BT MAC is valid if six colon separated groups of two hexadecimal digits used",
                BluetoothUtils.isValidBluetoothMacAddress("00:11:22:33:44:55"), is(true));

        assertThat("The BT MAC is valid if six colon separated groups of two hexadecimal digits used",
                BluetoothUtils.isValidBluetoothMacAddress("0A:1B:2C:3D:4E:5F"), is(true));

    }

    @Test
    public void testGetBluetoothMacAddressFromSocket() throws Exception {
        String macAddress = "0A:1B:2C:3D:4E:5F";
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);

        assertThat("The BT MAC the peer from the given BT socket instance is proper",
                BluetoothUtils.getBluetoothMacAddressFromSocket(mMockBluetoothSocket), is(macAddress));

        assertThat("The BT MAC the peer from the given BT socket instance is null if not accessible",
                BluetoothUtils.getBluetoothMacAddressFromSocket(null), is(nullValue()));

        when(mMockBluetoothSocket.getRemoteDevice()).thenReturn(null);

        assertThat("The BT MAC the peer from the given BT socket instance is null if not accessible",
                BluetoothUtils.getBluetoothMacAddressFromSocket(null), is(nullValue()));
    }

    @Test
    public void testValidateReceivedHandshakeMessage_SimpleHandshake() throws Exception {

        String macAddress = "0A:1B:2C:3D:4E:5F";
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);

        assertThat("The received object is null if handshake message is empty",
                BluetoothUtils.validateReceivedHandshakeMessage(
                        "".getBytes(), 10, mMockBluetoothSocket),
                is(nullValue()));


        assertThat("The received object is null if handshakeMessageLength is wrong",
                BluetoothUtils.validateReceivedHandshakeMessage(
                        "WrongMessage".getBytes(), 10, mMockBluetoothSocket),
                is(nullValue()));


        assertThat("The received object is null if the message is wrong",
                BluetoothUtils.validateReceivedHandshakeMessage(
                        "WrongMessage".getBytes(), 15, mMockBluetoothSocket),
                is(nullValue()));

        assertThat("The proper object is returned if the message is correct",
                BluetoothUtils.validateReceivedHandshakeMessage(
                        BluetoothUtils.SIMPLE_HANDSHAKE_MESSAGE_AS_BYTE_ARRAY,
                        15, mMockBluetoothSocket).getBluetoothMacAddress(),
                is(macAddress));
    }

    // The test below does not work due to the fact that the class JSONObject
    // is part of the android SDK. That means that is not available for unit testing by default.
    // It has to be tested by instrumented tests
    public void testValidateReceivedHandshakeMessage_LongHandshake() throws Exception {

        String macAddress = "0A:1B:2C:3D:4E:5F";
        when(mMockBluetoothDevice.getAddress()).thenReturn(macAddress);
        when(mMockPeerProperties.isValid()).thenReturn(true);
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("name", "myName");
        jsonObject.put("address", macAddress);

        assertThat("The received object is null if handshakeMessageLength is wrong",
                BluetoothUtils.validateReceivedHandshakeMessage(
                        jsonObject.toString().getBytes(), 20, mMockBluetoothSocket),
                is(notNullValue()));

    }

    @Test
    public void testPreviouslyUsedAlternativeChannelOrPort() throws Exception {
        // get default port
        BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort();
    }

    @Test
    public void testSetNextAlternativeChannelOrPort() throws Exception {
        int MAX_ALTERNATIVE_CHANNEL = 30;
        int tmpChPort = BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort();

        // can't set the number below the limit
        BluetoothUtils.setNextAlternativeChannelOrPort(-1);
        assertThat("It returns previous port if the alternative RFCOMM channel is below 0 or not set",
                BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort(),
                is(tmpChPort));

        tmpChPort = BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort();
        BluetoothUtils.setNextAlternativeChannelOrPort(0);
        assertThat("It returns previous port if the alternative RFCOMM channel is 0",
                BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort(),
                is(tmpChPort));

        BluetoothUtils.setNextAlternativeChannelOrPort(MAX_ALTERNATIVE_CHANNEL -1);
        assertThat("It returns proper alternative RFCOMM channel",
                BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort(),
                is(MAX_ALTERNATIVE_CHANNEL - 2));
        // can't set the number above the limit
        BluetoothUtils.setNextAlternativeChannelOrPort(MAX_ALTERNATIVE_CHANNEL);
        assertThat("It returns proper alternative RFCOMM channel",
                BluetoothUtils.getPreviouslyUsedAlternativeChannelOrPort(),
                is(MAX_ALTERNATIVE_CHANNEL - 2));
    }

    // The tests below does not work due to the fact that the class BluetoothSocket
    // is part of the android SDK. That means that is not available for unit testing by default.
    // It has to be tested by instrumented tests
    public void testCreateBluetoothSocketToServiceRecord() throws Exception {
    }

    public void testCreateBluetoothSocketToServiceRecordWithNextPort() throws Exception {

    }

    public void testCreateBluetoothSocket() throws Exception {

    }

    public void testCreateBluetoothSocketWithNextChannel() throws Exception {

    }
}