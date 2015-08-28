package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.CountDownTimer;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by juksilve on 9.7.2015.
 */
public class BLEValueReader {
    private final BLEValueReader that = this;

    enum State{
        Idle,
        Discovering,
        Reading
    }
    private State myState = State.Idle;

    // 60 second after we saw last peer, we could determine that we have seen all we have around us
    private final CountDownTimer peerDiscoveryTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {}

        public void onFinish() {
            myState = myState.Idle;
            if (connectBack != null) {
                connectBack.gotServicesList(myServiceList);
            }
            myServiceList.clear();
        }
    };

    // single device should not take long to connect, discover services & read char
    // this is used for timeout, cancelling any discovery process for any device that
    // takes longer than 10 seconds
    private final CountDownTimer doNextRoundTimer = new CountDownTimer(10000, 1000) {
        public void onTick(long millisUntilFinished) {}

        public void onFinish() {
            if(myDeviceList.size() > 0){
                doNextRound();
            }
        }
    };

    private final CopyOnWriteArrayList<ServiceItem> myServiceList = new CopyOnWriteArrayList<ServiceItem>();
    private final CopyOnWriteArrayList<BluetoothDevice> myDeviceList = new CopyOnWriteArrayList<BluetoothDevice>();
    private final Context context;
    private final DiscoveryCallback connectBack ;
    private final BluetoothAdapter btAdapter;
    private BluetoothGatt bluetoothGatt = null;

    public BLEValueReader(Context Context, DiscoveryCallback CallBack,BluetoothAdapter adapter) {
        this.context = Context;
        this.connectBack = CallBack;
        this.btAdapter = adapter;
    }

    private void restartFullListTimer(){
        peerDiscoveryTimer.cancel();
        peerDiscoveryTimer.start();
    }

    public void Stop(){
        Disconnect();
        myDeviceList.clear();
        myServiceList.clear();
        peerDiscoveryTimer.cancel();
    }


    private void Disconnect() {
        doNextRoundTimer.cancel();
        BluetoothGatt tmpGat = bluetoothGatt;
        bluetoothGatt = null;
        if(tmpGat != null) {
            tmpGat.disconnect();
            tmpGat.close();
        }
    }

    public void AddDevice(final BluetoothDevice device){
        myDeviceList.add(device);

        //implement timeout timer. and call DiscoverServices
        if(myState == State.Idle){
            //we'll need to start this here, in order to get knowledge on all peers disappearing
            // since if we don't find any peers, AddDevice,doNextRound etc. are never called
            restartFullListTimer();
            doNextRound();
        }
    }

    private void doNextRound() {
        //disconnect any previous connections
        Disconnect();
        myState = State.Idle;
        doNextRoundTimer.start();

        // how do I get first index item from the array in thread safe way
        // any alternative than doing this funny loop
        for(BluetoothDevice device : myDeviceList) {
            if (device != null) {
                if (myDeviceList.indexOf(device) == 0) {
                    // get the first, and add it as last, so next round will do different one
                    // we'll remove the device, once we get results for it.
                    myDeviceList.remove(device);
                    myDeviceList.add(device);
                    //do connection to the selected device
                    Log.i("BLEValueReader", "Connecting to next device : " + device.getAddress());

                    doConnect(device);
                    break;
                }
            }
        }
    }

    private boolean doConnect(final BluetoothDevice device) {
        boolean ret = false;
        if(device != null && bluetoothGatt == null && connectBack != null) {
            Log.i("BLEValueReader", "Connecting to : " + device.getAddress());
            bluetoothGatt = device.connectGatt(that.context, false, gattCallback);
            myState = State.Discovering;
            ret = true;
        }
        return ret;
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        public void onConnectionStateChange(android.bluetooth.BluetoothGatt gatt, int status, int newState) {

            //if we fail to get anything started in here, then we'll do the next round with timeout timer
            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    myState = State.Idle;
                    Log.i("BLEValueReader", "we are disconnected");
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    if (!gatt.discoverServices()) {
                        myState = State.Idle;
                        Log.i("BLEValueReader", "discoverServices return FALSE");
                        return;
                    }
                    Log.i("BLEValueReader", "discoverServices to : " + gatt.getDevice().getAddress());
                    myState = State.Discovering;
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                case BluetoothProfile.STATE_DISCONNECTING:
                default:
                    // we can ignore any other than actual connected/disconnected sate
                    break;
            }
        }

        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            List<BluetoothGattService> services = gatt.getServices();
            if (services != null) {
                for (BluetoothGattService item : services) {
                    outerLoop:
                    if (item != null && item.getUuid().toString().equalsIgnoreCase(BLEBase.SERVICE_UUID_1)) {
                        List<BluetoothGattCharacteristic> charList = item.getCharacteristics();
                        if (charList != null) {
                            for (BluetoothGattCharacteristic charItem : charList) {
                                if (charItem != null && charItem.getUuid().toString().equalsIgnoreCase(BLEBase.CharacteristicsUID1)) {
                                    if(!gatt.readCharacteristic(charItem)){
                                        Log.i("BLEValueReader", "readCharacteristic return FALSE");
                                        myState = State.Idle;
                                        return;
                                    }

                                    Log.i("BLEValueReader", "readCharacteristic to : " + gatt.getDevice().getAddress());
                                    myState = State.Reading;
                                    break outerLoop;
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

                    Log.i("BLEValueReader", "JsonLine: " + jsonString + " -- peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                    ServiceItem tmpSrv = new ServiceItem(peerIdentifier, peerName, peerAddress, "BLE", gatt.getDevice().getAddress(), gatt.getDevice().getName());

                    // we need to save the peer, so we can determine devices that went away with timer.
                    myServiceList.add(tmpSrv);

                    // lets inform that we have found a peer
                    if (connectBack != null) {
                        connectBack.foundService(tmpSrv);
                    }

                    //only fully successful discoveries will reset the full list timer
                    // this is to prevent failing peers to prevent discovery re-start from clean situations
                    restartFullListTimer();

                    //remove the already processed device from the search list
                    for (BluetoothDevice device : myDeviceList) {
                        if (device != null && device.getAddress().equalsIgnoreCase(gatt.getDevice().getAddress())) {
                            myDeviceList.remove(device);
                        }
                    }

                    //lets then move to process next peer we see on the list
                    doNextRound();

                } catch (JSONException e) {
                    Log.i("BLEValueReader", "Desscryptin instance failed , :" + e.toString());
                }
            }
        }
    };
}
