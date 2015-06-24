package org.thaliproject.p2p.btlib_testapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Message;

import org.thaliproject.p2p.btconnectorlib.BTConnector;
import org.thaliproject.p2p.btconnectorlib.BTConnectorSettings;
import org.thaliproject.p2p.btconnectorlib.ServiceItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by juksilve on 14.5.2015.
 */
public class BtConnectorHelper implements BTConnector.Callback, BTConnector.ConnectSelector {

    jxCallBack jxcore = null;
    Context context = null;

    public interface jxCallBack{
        public void CallJSMethod(String method, String jsonData);
    }

    final String instanceEncryptionPWD = "CHANGEYOURPASSWRODHERE";
    final String serviceTypeIdentifier = "Cordovap2p._tcp";
    final String BtUUID                = "fa87c0d0-afac-11de-8a39-0800200c9a66";
    final String Bt_NAME               = "Thaili_Bluetooth";

    final String STATE_UNAVAILABLE     = "Unavailable";
    final String STATE_AVAILABLE       = "Available";
    final String STATE_CONNECTING      = "Connecting";
    final String STATE_CONNECTINGFAIL  = "ConnectingFailed";
    final String STATE_DISCONNECTED    = "Disconnected";
    final String STATE_CONNECTED       = "Connected";

    List<ServiceItem> lastAvailableList = new ArrayList<ServiceItem>();

    BTConnectorSettings conSettings = null;
    BTConnector mBTConnector = null;
    BtConnectedThread mBTConnectedThread = null;

    String myPeerIdentifier= "";
    String myPeerName = "";

    public BtConnectorHelper(Context Context,jxCallBack callBack) {
        conSettings = new BTConnectorSettings();
        conSettings.SERVICE_TYPE = serviceTypeIdentifier;
        conSettings.MY_UUID = UUID.fromString(BtUUID);
        conSettings.MY_NAME = Bt_NAME;

        this.jxcore = callBack;
        this.context = Context;
    }

    public void Start(String peerIdentifier, String peerName){
        this.myPeerIdentifier= peerIdentifier;
        this.myPeerName = peerName;
        Stop();
        mBTConnector = new BTConnector(context,this,this,conSettings,instanceEncryptionPWD);
        mBTConnector.Start(peerIdentifier,peerName);
    }

    public void Stop(){

        // do we need to send disconnected event when communicationsd are stopped ?
        if (mBTConnectedThread != null) {
      //      String peerId = mBTConnectedThread.GetPeerId();
      //      String peerName = mBTConnectedThread.GetPeerName();
            mBTConnectedThread.Stop();
            mBTConnectedThread = null;
      //      String stateReply = "[" + getStatusItem(peerId, peerName, STATE_DISCONNECTED) + "]";
      //      jxcore.CallJSMethod("peerChanged", stateReply);
        }

        if(mBTConnector != null){
            mBTConnector.Stop();
            mBTConnector = null;
        }
    }

    public String GetDeviceName(){

        String ret= "";
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if(bluetooth != null){
            ret = bluetooth.getName();
        }

        return ret;
    }

    public boolean SetDeviceName(String name){

        boolean  ret= false;
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if(bluetooth != null){
            ret = bluetooth.setName(name);
        }

        return ret;
    }

    public String  MakeGUID() {
        return UUID.randomUUID().toString();
    }


    public void BeginConnectPeer(String toPeerId) {

        ServiceItem selectedDevice = null;
        if (lastAvailableList != null) {
            for (int i = 0; i < lastAvailableList.size(); i++) {
                if (lastAvailableList.get(i).peerId.contentEquals(toPeerId)) {
                    selectedDevice = lastAvailableList.get(i);
                    break;
                }
            }
        }

        String peerId = toPeerId;
        String peerName = "";
        if(selectedDevice != null){
            peerId = selectedDevice.peerId;
            peerName = selectedDevice.peerName;
        }

        if (selectedDevice != null && mBTConnector != null  && mBTConnector.TryConnect(selectedDevice) == BTConnector.TryConnectReturnValues.Connecting) {
            // we are ok, and status-callback will be delivering the events.
            String stateReply = "[" + getStatusItem(peerId,peerName, STATE_CONNECTING) + "]";
            jxcore.CallJSMethod("peerChanged", stateReply);
        } else {
            String stateReply = "[" + getStatusItem(peerId,peerName, STATE_CONNECTINGFAIL) + "]";
            jxcore.CallJSMethod("peerChanged", stateReply);
        }
    }

    public boolean SendMessage(String message) {
        boolean ret = false;

        if (mBTConnectedThread != null) {
            mBTConnectedThread.write(message.getBytes());
            ret = true;
        }

        return ret;
    }
    @Override
    public void Connected(BluetoothSocket bluetoothSocket, boolean incoming,String peerId,String peerName,String peerAddress) {

        String reply = "[";
        reply = reply + getStatusItem(peerId,peerName, STATE_CONNECTED);

/*        // the current Bluetooth implementation is used for only one p2p connection at any time
        // see if this is needed or not
        if(lastAvailableList != null) {
            for (int ii = 0; ii < lastAvailableList.size(); ii++) {

                if(!lastAvailableList.get(ii).peerId.equals(peerId)) {
                    reply = reply + ",";
                    reply = reply + getStatusItem(lastAvailableList.get(ii), STATE_UNAVAILABLE);
                }
            }
        }
*/
        reply = reply +"]";
        lastAvailableList = null;

        jxcore.CallJSMethod("peerChanged", reply);

        if (mBTConnectedThread != null) {
            mBTConnectedThread.Stop();
            mBTConnectedThread = null;
        }

        mBTConnectedThread = new BtConnectedThread(bluetoothSocket,mHandler,peerId,peerName,peerAddress);
        mBTConnectedThread.start();
    }

    @Override
    public void ConnectionFailed(String peerId, String peerName, String peerAddress) {
        String stateReply = "[" + getStatusItem(peerId,peerName, STATE_CONNECTINGFAIL) + "]";
        jxcore.CallJSMethod("peerChanged", stateReply);
    }

    @Override
    public void StateChanged(BTConnector.State state) {

        // with this version, we don't  use this state information for anything
        switch (state) {
            case Idle:
                break;
            case NotInitialized:
                break;
            case WaitingStateChange:
                break;
            case FindingPeers:
                break;
            case FindingServices:
                break;
            case Connecting:
                break;
            case Connected:
                break;
        };

    }

    @Override
    public ServiceItem CurrentPeersList(List<ServiceItem> serviceItems) {

        String reply = "[";

        Boolean wasPrevouslyAvailable = false;

        if(serviceItems != null) {
            for (int i = 0; i < serviceItems.size(); i++) {

                wasPrevouslyAvailable = false;
                ServiceItem item = serviceItems.get(i);
                if (lastAvailableList != null) {
                    for (int ll = (lastAvailableList.size() - 1); ll >= 0; ll--) {
                        if (item.deviceAddress.equalsIgnoreCase(lastAvailableList.get(ll).deviceAddress)) {
                            wasPrevouslyAvailable = true;
                            lastAvailableList.remove(ll);
                        }
                    }
                }

                if (!wasPrevouslyAvailable) {
                    if (reply.length() > 3) {
                        reply = reply + ",";
                    }
                    reply = reply + getStatusItem(item, STATE_AVAILABLE);
                }
            }
        }
        if(lastAvailableList != null) {
            for (int ii = 0; ii < lastAvailableList.size(); ii++) {
                if (reply.length() > 3) {
                    reply = reply + ",";
                }
                reply = reply + getStatusItem(lastAvailableList.get(ii), STATE_UNAVAILABLE);
            }
        }

        reply = reply +"]";
        lastAvailableList = serviceItems;

        jxcore.CallJSMethod("peerChanged", reply);

        return null;
    }

    @Override
    public void PeerDiscovered(ServiceItem service) {

    }

    /*
        {
            "peerIdentifier": "F50F4805-A2AB-4249-9E2F-4AF7420DF5C7",
            "peerName": "Her Phone",
            "state": "Available"
        }
    */
    private String getStatusItem(ServiceItem item, String state) {
        String reply = "";
        if(item != null) {
            reply = getStatusItem(item.peerId,item.peerName,state);
        }
        return reply;
    }

    private String getStatusItem(String peerId, String peerName, String state) {
        String reply = "";
        reply = "{\"peerIdentifier\":\"" + peerId + "\", " + "\"peerName\":\"" + peerName + "\", " + "\"state\":\"" + state + "\"}";
        return reply;
    }

        // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BtConnectedThread.MESSAGE_WRITE:
                {
                    byte[] writeBuf = (byte[]) msg.obj;// construct a string from the buffer
                    String writeMessage = new String(writeBuf);

                    String reply = "{ \"writeMessage\": \"" + writeMessage + "\"}";
                    jxcore.CallJSMethod("OnMessagingEvent", reply);
                }
                break;
                case BtConnectedThread.MESSAGE_READ:
                {
                    byte[] readBuf = (byte[]) msg.obj;// construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);

                    String reply = "{ \"readMessage\": \"" + readMessage + "\"}";
                    jxcore.CallJSMethod("OnMessagingEvent", reply);
                }
                break;
                case BtConnectedThread.SOCKET_DISCONNEDTED: {

                    if (mBTConnectedThread != null) {
                        String peerId = mBTConnectedThread.GetPeerId();
                        String peerName = mBTConnectedThread.GetPeerName();
                        mBTConnectedThread.Stop();
                        mBTConnectedThread = null;

                        String stateReply = "[" + getStatusItem(peerId, peerName, STATE_DISCONNECTED) + "]";
                        jxcore.CallJSMethod("peerChanged", stateReply);
                    }

                    if(mBTConnector != null) {
                        mBTConnector.Start(myPeerIdentifier,myPeerName);
                    }
                }
                break;
            }
        }
    };
}
