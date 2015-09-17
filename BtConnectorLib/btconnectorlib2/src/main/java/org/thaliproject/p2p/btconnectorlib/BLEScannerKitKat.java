package org.thaliproject.p2p.btconnectorlib;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import android.os.Handler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by juksilve on 20.4.2015.
 */

class BLEScannerKitKat implements DiscoveryCallback {

    private final BLEScannerKitKat that = this;
    private final Context context;
    private final DiscoveryCallback mDiscoveryCallback;
    private final BluetoothAdapter mBluetoothAdapter;
    private final CopyOnWriteArrayList<BLEDeviceListItem> mBLEDeviceList = new CopyOnWriteArrayList<>();
    private final Handler mHandler;

    private BLEValueReader mBLEValueReader = null;

    public BLEScannerKitKat(Context Context, DiscoveryCallback CallBack,BluetoothManager Manager) {
        this.context = Context;
        this.mDiscoveryCallback = CallBack;
        this.mBluetoothAdapter = Manager.getAdapter();
        this.mHandler = new Handler(this.context.getMainLooper());
    }

    public void Start() {
        Stop();
        BLEValueReader tmpValueReader = new BLEValueReader(this.context,this,mBluetoothAdapter);
        mBLEValueReader = tmpValueReader;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean retValue = that.mBluetoothAdapter.startLeScan(that.leScanCallback);
                Log.i("SCAN-NER", "start now : " + retValue);
            }
        });
    }

    public void Stop() {
        Log.i("SCAN-NER", "stop now");
        mBluetoothAdapter.stopLeScan(leScanCallback);

        BLEValueReader tmp = mBLEValueReader;
        mBLEValueReader = null;
        if(tmp != null){
            tmp.Stop();
        }
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {
        Log.i("SCAN-NER", "gotServicesList size : " + list.size());
        that.mDiscoveryCallback.gotServicesList(list);
       //we clear our scanner device list, so we can check which devices we are seeing now
        that.mBLEDeviceList.clear();

    }

    @Override
    public void foundService(ServiceItem item) {
        Log.i("SCAN-NER", "foundService : " + item.peerName);
        that.mDiscoveryCallback.foundService(item);
    }

    @Override
    public void StateChanged(State newState) {
        that.mDiscoveryCallback.StateChanged(newState);
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            if (device == null || scanRecord == null) {
                return;
            }
            BLEDeviceListItem itemTmp = null;
            for (BLEDeviceListItem item : mBLEDeviceList) {
                if (item != null && item.getDevice() != null) {
                    if (item.getDevice().getAddress().equalsIgnoreCase(device.getAddress())) {
                        itemTmp = item;
                    }
                }
            }

            //in here we just care of devices we have not seen earlier.
            if (itemTmp != null) {
                //we seen this earlier, so lets not do any further processing
                return;
            }

            itemTmp = new BLEDeviceListItem(device, scanRecord);
            mBLEDeviceList.add(itemTmp);
            if (!itemTmp.getUUID().equalsIgnoreCase(BLEBase.SERVICE_UUID_1)) {
                //its not our service, so we are not interested on it anymore.
                // but to faster ignore it later scan results, we'll add it to the list
                // but we don't give it to the value reader for any further processing
                return;
            }

            //new device we have not seen since last mBLEDeviceList clear, so lets get values from it
            // will also start the reader process if not started earlier
            Log.i("SCAN-NER", "added new device : " + device.getAddress());
            //Add device will actually start the discovery process
            mBLEValueReader.AddDevice(device);
        }
    };
}
