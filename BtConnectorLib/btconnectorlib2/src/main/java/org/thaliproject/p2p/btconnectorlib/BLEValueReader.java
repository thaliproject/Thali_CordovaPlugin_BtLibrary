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
import android.os.Handler;

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

    // 20 second after we saw last peer, we could determine that we have seen all we have around us
    private final CountDownTimer peerDiscoveryTimer = new CountDownTimer(20000, 1000) {
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
                Log.i("BLEValueReader", "doNextRoundTimer timeout" );
                doNextRound();
            }
        }
    };
    private final CopyOnWriteArrayList<ServiceItem> myDevicesSeenList = new CopyOnWriteArrayList<ServiceItem>();

    private final CopyOnWriteArrayList<ServiceItem> myServiceList = new CopyOnWriteArrayList<ServiceItem>();
    private final CopyOnWriteArrayList<BluetoothDevice> myDeviceList = new CopyOnWriteArrayList<BluetoothDevice>();
    private final Context context;
    private final DiscoveryCallback connectBack ;
    private final BluetoothAdapter btAdapter;
    private final Handler mHandler;
    private BluetoothGatt bluetoothGatt = null;

    public BLEValueReader(Context Context, DiscoveryCallback CallBack,BluetoothAdapter adapter) {
        this.context = Context;
        this.connectBack = CallBack;
        this.btAdapter = adapter;
        this.mHandler = new Handler(this.context.getMainLooper());
    }

    private void restartFullListTimer(){
        peerDiscoveryTimer.cancel();
        peerDiscoveryTimer.start();
    }

    public void Stop(){
        Disconnect();
        myDeviceList.clear();
        myServiceList.clear();
        myDevicesSeenList.clear();
        peerDiscoveryTimer.cancel();
    }


    private void Disconnect() {
        doNextRoundTimer.cancel();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                BluetoothGatt tmpGat = that.bluetoothGatt;
                bluetoothGatt = null;
                if (tmpGat != null) {
                  /*disconnect appears to create following error:
                    BluetoothGatt﹕ Unhandled exception in callback
                        java.lang.NullPointerException: Attempt to invoke virtual method 'void android.bluetooth.BluetoothGattCallback.onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)' on a null object reference
                            at android.bluetooth.BluetoothGatt$1.onClientConnectionState(BluetoothGatt.java:181)
                            at android.bluetooth.IBluetoothGattCallback$Stub.onTransact(IBluetoothGattCallback.java:70)
                            at android.os.Binder.execTransact(Binder.java:446)
                    */
                    //  tmpGat.disconnect();
                    tmpGat.close();
                }
            }
        });
    }

    public void AddDevice(final BluetoothDevice device){

        boolean alreadyInTheList = false;
        for(ServiceItem foundOne: myDevicesSeenList){
            if(foundOne != null && foundOne.deviceAddress.equalsIgnoreCase(device.getAddress())) {
                myServiceList.add(foundOne);
                alreadyInTheList = true;
                break;
            }
        }

        if(!alreadyInTheList) {
            myDeviceList.add(device);
        }

        if(myState != State.Idle){
            //we are already running a discovery on peer
            // we'll do this one later
            return;
        }

        //we'll need to start this here, in order to get knowledge on all peers disappearing
        // since if we don't find any peers, AddDevice,doNextRound etc. are never called
        restartFullListTimer();
        doNextRound();
    }

    private void doNextRound() {
        //disconnect any previous connections
        Disconnect();
        myState = State.Idle;
        doNextRoundTimer.start();

        Log.i("BLEValueReader", "Connecting in called context");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(connectBack == null || bluetoothGatt != null ) {
                }

                // how do I get first index item from the array in thread safe way
                // any alternative than doing this funny loop
                for(BluetoothDevice device : that.myDeviceList) {
                    if (device != null) {
                        if (that.myDeviceList.indexOf(device) == 0) {

                            // get the first, and add it as last, so next round will do different one
                            // we'll remove the device, once we get results for it.
                            that.myDeviceList.remove(device);
                            that.myDeviceList.add(device);
                            //do connection to the selected device
                            Log.i("BLEValueReader", "Connecting to next device : " + device.getAddress());;
                            that.bluetoothGatt = device.connectGatt(that.context, false, that.gattCallback);
                            that.myState = State.Discovering;
                            break;
                        }
                    }
                }
            }
        });
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {


        public void onConnectionStateChange(android.bluetooth.BluetoothGatt gatt, int status, int newState) {

            Log.i("BLEValueReader", "onConnectionStateChange status : " + BLEBase.getGATTStatusAsString(status) + ", state: " + BLEBase.getConnectionStateAsString(newState));

            //if we fail to get anything started in here, then we'll do the next round with timeout timer
            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    myState = State.Idle;
                    Log.i("BLEValueReader", "we are disconnected");
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (that.bluetoothGatt == null) {
                                return;
                            }
                            if (!that.bluetoothGatt.discoverServices()) {
                                that.myState = State.Idle;
                                Log.i("BLEValueReader", "discoverServices return FALSE");
                                return;
                            }
                            Log.i("BLEValueReader", "discoverServices to : " + that.bluetoothGatt.getDevice().getAddress());
                            that.myState = State.Discovering;
                        }
                    });
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                case BluetoothProfile.STATE_DISCONNECTING:
                default:
                    // we can ignore any other than actual connected/disconnected sate
                    break;
            }

            Log.i("BLEValueReader", "onConnectionStateChange out");
        }

        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            Log.i("BLEValueReader", "onServicesDiscovered status : " + BLEBase.getGATTStatusAsString(status));

            if (bluetoothGatt == null) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (that.bluetoothGatt == null) {
                        return;
                    }

                    List<BluetoothGattService> services = that.bluetoothGatt.getServices();
                    if (services != null) {
                        for (BluetoothGattService item : services) {
                            outerLoop:
                            if (item != null && item.getUuid().toString().equalsIgnoreCase(BLEBase.SERVICE_UUID_1)) {
                                List<BluetoothGattCharacteristic> charList = item.getCharacteristics();
                                if (charList != null) {
                                    for (BluetoothGattCharacteristic charItem : charList) {
                                        if (charItem != null && charItem.getUuid().toString().equalsIgnoreCase(BLEBase.CharacteristicsUID1)) {
                                            if (!gatt.readCharacteristic(charItem)) {
                                                Log.i("BLEValueReader", "readCharacteristic return FALSE");
                                                myState = State.Idle;
                                                return;
                                            }

                                            Log.i("BLEValueReader", "readCharacteristic to : " + that.bluetoothGatt.getDevice().getAddress());
                                            myState = State.Reading;
                                            break outerLoop;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            final BluetoothGattCharacteristic characteristicTmp = characteristic;
            Log.i("BLEValueReader", "onCharacteristicRead status : " + BLEBase.getGATTStatusAsString(status));

            mHandler.post(new Runnable() {
                @Override
                public void run() {


                    if (that.bluetoothGatt == null) {
                        return;
                    }

                    if (characteristicTmp == null || characteristicTmp.getValue() == null || characteristicTmp.getValue().length <= 0) {
                        return;
                    }

                    String jsonString = new String(characteristicTmp.getValue());
                    try {
                        JSONObject jObject = new JSONObject(jsonString);

                        String peerIdentifier = jObject.getString(BTConnector.JSON_ID_PEERID);
                        String peerName = jObject.getString(BTConnector.JSON_ID_PEERNAME);
                        String peerAddress = jObject.getString(BTConnector.JSON_ID_BTADRRES);

                        Log.i("BLEValueReader", "JsonLine: " + jsonString + " -- peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                        ServiceItem tmpSrv = new ServiceItem(peerIdentifier, peerName, peerAddress, "BLE", that.bluetoothGatt.getDevice().getAddress(), that.bluetoothGatt.getDevice().getName());

                        // we need to save the peer, so we can determine devices that went away with timer.
                        that.myServiceList.add(tmpSrv);

                        //lets cache all peers we find, so we don't need to poll them again
                        boolean alreadyInTheList = false;
                        for (ServiceItem foundOne : that.myDevicesSeenList) {
                            if (foundOne != null && foundOne.deviceAddress.equalsIgnoreCase(tmpSrv.deviceAddress)) {
                                alreadyInTheList = true;
                                break;
                            }
                        }

                        if (!alreadyInTheList) {
                            //see whether we had it there with other BLE address, i.e. it was re-started
                            for (ServiceItem foundOne : that.myDevicesSeenList) {
                                if (foundOne != null && foundOne.peerAddress.equalsIgnoreCase(tmpSrv.peerAddress)) {
                                    that.myDevicesSeenList.remove(foundOne);
                                }
                            }
                            that.myDevicesSeenList.add(tmpSrv);
                        }

                        // lets inform that we have found a peer
                        if (that.connectBack != null) {
                            that.connectBack.foundService(tmpSrv);
                        }

                        //only fully successful discoveries will reset the full list timer
                        // this is to prevent failing peers to prevent discovery re-start from clean situations
                        restartFullListTimer();

                        //remove the already processed device from the search list
                        for (BluetoothDevice device : myDeviceList) {
                            if (device != null && device.getAddress().equalsIgnoreCase(that.bluetoothGatt.getDevice().getAddress())) {
                                that.myDeviceList.remove(device);
                            }
                        }

                        Log.i("BLEValueReader", "processed fully, do next now!");
                        //lets then move to process next peer we see on the list
                        doNextRound();

                    } catch (JSONException e) {
                        Log.i("BLEValueReader", "Desscryptin instance failed , :" + e.toString());
                    }
                }
            });
        }
    };
}
