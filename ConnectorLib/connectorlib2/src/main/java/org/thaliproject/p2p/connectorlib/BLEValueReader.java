package org.thaliproject.p2p.connectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.CountDownTimer;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by juksilve on 9.7.2015.
 */
public class BLEValueReader {
    BLEValueReader that = this;

    BluetoothAdapter btAdapter = null;
    BluetoothGatt bluetoothGatt = null;

    enum State{
        Idle,
        Discovering,
        Connecting,
        Reading
    }
    State myState = State.Idle;


    List<ServiceItem> myServiceList = new ArrayList<ServiceItem>();
    List<BluetoothDevice> myDeviceList = new ArrayList<BluetoothDevice>();


    interface BLEConnectCallback {
        public void gotServicesList(List<ServiceItem> list);
        public void foundService(ServiceItem item);
    }

    private Context context = null;
    private BLEConnectCallback connectBack = null;


    // 60 second after we saw last peer, we could determine that we have seen all we have around us
    CountDownTimer peerDiscoveryTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }

        public void onFinish() {
            myState = myState.Idle;
            if (connectBack != null) {
                synchronized (myServiceList) {
                    connectBack.gotServicesList(myServiceList);
                    myServiceList.clear();
                }
            }
        }
    };

    // single device should not take long to connect, discover services & read char
    // this is used for timeout, cancelling any discovery process for any device that
    // takes longer than 10 seconds
    CountDownTimer doNextRoundTimer = new CountDownTimer(10000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }

        public void onFinish() {
            if(myDeviceList.size() > 0){
                doNextRound();
            }
        }
    };

    public BLEValueReader(Context Context, BLEConnectCallback CallBack,BluetoothAdapter adapter) {
        this.context = Context;
        this.connectBack = CallBack;
        this.btAdapter = adapter;
    }

    public synchronized void Start(){
        //lets make sure we are fresh to do actual start
        Stop();
        //we'll need to start this here, in order to get knowledge on all peers disappearing
        // since if we don't find any peers, AddDevice,doNextRound etc. are never called
        restartFullListTimer();
    }

    private void restartFullListTimer(){
        peerDiscoveryTimer.cancel();
        peerDiscoveryTimer.start();
    }

    public synchronized void Stop(){
        disConnect();
        myDeviceList.clear();
        myServiceList.clear();
        peerDiscoveryTimer.cancel();
    }


    private void disConnect() {
        doNextRoundTimer.cancel();
        BluetoothGatt tmpGat = bluetoothGatt;
        bluetoothGatt = null;
        if(tmpGat != null) {
            tmpGat.disconnect();
            tmpGat.close();
        }
    }

    public void AddDevice(final BluetoothDevice device){

        synchronized (myDeviceList){
            myDeviceList.add(device);
        }

        //implement timeout timer. and call DiscoverServices
        if(myState == State.Idle){
            doNextRound();
        }
    }

    private void doNextRound(){
        //disconnect any previous connections
        disConnect();
        myState = State.Idle;
        doNextRoundTimer.start();

        synchronized (myDeviceList){
            if(myDeviceList.size() > 0){

                // get the first, and add it as last, so next round will do different one
                // we'll remove the device, once we get results for it.
                BluetoothDevice device = myDeviceList.get(0);
                myDeviceList.remove(0);
                myDeviceList.add(device);

                //do connection to the selected device
                doConnect(device);
            }else{
                debug_print("BLEConnector","doNextRound has processed all devices now.");
                // done for now, lets wait for new devices added from scan in here
            }
        }
    }

    private boolean doConnect(final BluetoothDevice device) {
        boolean ret = false;
        if(device != null && bluetoothGatt == null && connectBack != null) {
            debug_print("BLEConnector","Connecting to : " + device.getAddress());
            bluetoothGatt = device.connectGatt(that.context, false, gattCallback);
            myState = State.Discovering;
            ret = true;
        }
        return ret;
    }



    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                if(gatt.discoverServices()){
                    debug_print("BLEConnector","discoverServices to : " + gatt.getDevice().getAddress());
                    myState = State.Discovering;
                }else{
                    myState = State.Idle;
                    //do next round with timeout timer
                    debug_print("BLEConnector","discoverServices return FALSE");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                myState = State.Idle;
                //do next round with timeout timer
                debug_print("BLEConnector","we are disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            List<BluetoothGattService> services = gatt.getServices();
            if (services != null) {
                for (BluetoothGattService item : services) {
                    outterloop:
                    if (item != null && item.getUuid().toString().equalsIgnoreCase(BLEBase.SERVICE_UUID_1)) {
                        List<BluetoothGattCharacteristic> charList = item.getCharacteristics();
                        if (charList != null) {
                            for (BluetoothGattCharacteristic charItem : charList) {
                                if (charItem != null && charItem.getUuid().toString().equalsIgnoreCase(BLEBase.CharacteristicsUID1)) {
                                    if(gatt.readCharacteristic(charItem)){
                                        debug_print("BLEConnector","readCharacteristic to : " + gatt.getDevice().getAddress());
                                        myState = State.Reading;
                                    }else{
                                        debug_print("BLEConnector","readCharacteristic return FALSE");
                                        myState = State.Idle;
                                    }
                                    break outterloop;
                                }
                            }
                        }
                    }
                }
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if(characteristic.getValue().length > 0) {
                String jsonString = new String(characteristic.getValue());
                try {
                    JSONObject jObject = new JSONObject(jsonString);

                    String peerIdentifier = jObject.getString(BTConnector.JSON_ID_PEERID);
                    String peerName = jObject.getString(BTConnector.JSON_ID_PEERNAME);
                    String peerAddress = jObject.getString(BTConnector.JSON_ID_BTADRRES);

                    debug_print("BLEConnector","JsonLine: " + jsonString + " -- peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                    ServiceItem tmpSrv = new ServiceItem(peerIdentifier, peerName, peerAddress, "BLE", gatt.getDevice().getAddress(), gatt.getDevice().getName());

                    // we need to save the peer, so we can determine devices that went away with timer.
                    synchronized (myServiceList) {
                        myServiceList.add(tmpSrv);
                    }

                    // lets inform that we have found a peer
                    if(connectBack != null) {
                        connectBack.foundService(tmpSrv);
                    }

                    //only fully successfull discoveries will reset the full list timer
                    // this is to prevent failing peers to prevent discovery re-start from clean situations
                    restartFullListTimer();

                    //remove the already processed device from the search list
                    synchronized (myDeviceList){
                        for(BluetoothDevice device: myDeviceList){
                            if(device != null && device.getAddress().equalsIgnoreCase(gatt.getDevice().getAddress())){
                                myDeviceList.remove(device);
                            }
                        }
                    }

                } catch (Exception e) {
                    debug_print("BLEConnector", "Desscryptin instance failed , :" + e.toString());
                }
            }
        }
    };

    private void debug_print(String who, String what){
        Log.i(who, what);
    }
}
