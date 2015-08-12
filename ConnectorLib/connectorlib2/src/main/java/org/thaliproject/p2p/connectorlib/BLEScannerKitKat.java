package org.thaliproject.p2p.connectorlib;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by juksilve on 20.4.2015.
 */

/*
Disconnect 0x13 with Nexus 5 having lollipop
https://code.google.com/p/android/issues/detail?id=156730

 */

public class BLEScannerKitKat implements BLEValueReader.BLEConnectCallback {

    BLEScannerKitKat that = this;
    BluetoothAdapter btAdapter = null;



    interface BLEScannerCallback {
        public void gotServicesList(List<ServiceItem> list);
        public void foundService(ServiceItem item);
    }

    private Context context = null;
    private BLEScannerCallback callBack = null;

    public class DeviceListItem {
        private BluetoothDevice mDevice;
        private String mUuid;

        public DeviceListItem(BluetoothDevice device,byte[] scanRecord) {
            this.mDevice =device;
            this.mUuid = BLEBase.getServiceUUID(BLEBase.ParseRecord(scanRecord));
        }

        public BluetoothDevice getDevice(){return mDevice;}
        public String getUUID(){return mUuid;}
    }

    BLEValueReader mBLEValueReader = null;
    private List<DeviceListItem> devlist = new ArrayList<>();

    public BLEScannerKitKat(Context Context, BLEScannerCallback CallBack) {
        this.context = Context;
        this.callBack = CallBack;
        BluetoothManager btManager = (BluetoothManager)this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
    }

    public void Start() {
        Stop();
        mBLEValueReader = new BLEValueReader(this.context,this,btAdapter);
        btAdapter.startLeScan(leScanCallback);
    }

    public void Stop() {
        btAdapter.stopLeScan(leScanCallback);

        BLEValueReader tmp = mBLEValueReader;
        mBLEValueReader = null;
        if(tmp != null){
            tmp.Stop();
        }
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {

        if(mBLEValueReader != null && list != null && (list.size() > 0)) {
            mBLEValueReader.Start();
        }
        clearList();
        callBack.gotServicesList(list);
    }

    @Override
    public void foundService(ServiceItem item) {
        callBack.foundService(item);
    }

    public void clearList() {
        synchronized (devlist) {
            devlist.clear();
        }
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            if(device != null && scanRecord != null) {

                DeviceListItem itemTmp = null;

                synchronized (devlist) {
                    for (DeviceListItem item : devlist) {
                        if (item != null && item.getDevice() != null) {
                            if (item.getDevice().getAddress().equalsIgnoreCase(device.getAddress())) {
                                itemTmp = item;
                            }
                        }
                    }

                    //in here we just care of devices we have not seen earlier.
                    if (itemTmp == null) {
                        itemTmp = new DeviceListItem(device, scanRecord);
                        //see that its our service
                        if (itemTmp.getUUID().equalsIgnoreCase(BLEBase.SERVICE_UUID_1)) {
                            debug_print("SCAN", "added new device : " + device.getAddress());

                            if(mBLEValueReader != null){
                                synchronized (mBLEValueReader){
                                    // starts the full list timer
                                    // we need to let the BLE connector to know that we got new round started
                                    if(devlist.size() == 0){
                                        mBLEValueReader.Start();
                                    }
                                    //Add device will actually start the discovery process
                                    mBLEValueReader.AddDevice(device);
                                }
                            }

                            devlist.add(itemTmp);
                        }
                    }
                }
            }
        }
    };

    private void debug_print(String who, String what){
        Log.i(who, what);
    }
}
